#!/usr/bin/env python3
"""Hub-wide compatibility scanner: which desktop-only symbols do Plugin Hub jars need?

Downloads the Plugin Hub manifest (the same ManifestFull the client reads through
ExternalPluginClient) and every plugin jar into a resumable cache keyed by jar hash,
then resolves every class/method/field reference of each jar against the host surface
the Android app offers a plugin: android-awt's compiled classes, android.jar for the
compile SDK, the runelite-client and runelite-api jars, the app's own compiled shims
(org.lwjgl / rlawt), the host-provided libraries from android/build.gradle.kts and the
plugin jar itself. Unresolved references land in build/hub-compat/report.json and
report.md, grouped into the gap buckets of issues #62-#72.

Pure Python, no JVM. The manifest signature is not verified (the framing is skipped);
this is a coverage tool, not a trust boundary. Static linkage only: nothing here says
whether a symbol that resolves also behaves.

Usage:
    python tools-src/hub-compat-scan.py --limit 25
    python tools-src/hub-compat-scan.py --plugin quest-helper --plugin tile-packs
    python tools-src/hub-compat-scan.py --offline            # only what is cached
"""
import argparse
import base64
import concurrent.futures
import datetime
import glob
import hashlib
import io
import json
import os
import re
import struct
import sys
import time
import urllib.error
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_HUB_URL = 'https://repo.runelite.net/plugins'
DEFAULT_API_URL = 'https://api.runelite.net'
BOOTSTRAP_URL = 'https://static.runelite.net/bootstrap.json'
USER_AGENT = 'runelite-android hub-compat-scan'

# Host-provided libraries: group, artifact. The first block is what android/build.gradle.kts
# names for plugins; the second is runelite-client's runtime dependencies that the APK ships
# anyway (and R8 keeps for externals). Versions resolve from the gradle cache, newest first
# unless android/build.gradle.kts pins one inline.
DEFAULT_LIBS = [
    ('com.google.guava', 'guava'),
    ('com.google.guava', 'failureaccess'),
    ('com.google.code.gson', 'gson'),
    ('com.squareup.okhttp3', 'okhttp'),
    ('com.squareup.okio', 'okio'),
    ('org.slf4j', 'slf4j-api'),
    ('org.tomlj', 'tomlj'),
    ('org.antlr', 'antlr4-runtime'),
    ('net.runelite', 'flatlaf'),
    ('net.runelite', 'flatlaf-extras'),

    ('com.google.inject', 'guice'),
    ('javax.inject', 'javax.inject'),
    ('aopalliance', 'aopalliance'),
    ('com.google.code.findbugs', 'jsr305'),
    ('org.apache.commons', 'commons-lang3'),
    ('org.apache.commons', 'commons-text'),
    ('net.sf.jopt-simple', 'jopt-simple'),
]

# Invokedynamic bootstraps D8 desugars away; android.jar has no such classes, the dex
# never references them.
DESUGARED_CLASSES = {
    'java/lang/invoke/LambdaMetafactory',
    'java/lang/invoke/StringConcatFactory',
    'java/lang/invoke/ObjectMethods',
    'java/lang/invoke/SwitchBootstraps',
    'java/lang/invoke/ConstantBootstraps',
}
# Java 9 gave the java.nio buffers covariant overrides (ByteBuffer.flip() returning
# ByteBuffer); android.jar only has java.nio.Buffer's, and D8 rewrites the call sites.
_NIO_BUFFER = re.compile(r'^java/nio/(Byte|Char|Short|Int|Long|Float|Double|Mapped)Buffer$')

# ---------------------------------------------------------------------------
# Class files
# ---------------------------------------------------------------------------

_DESC_CLASS = re.compile(r'L([^;]+);')


class ClassInfo(object):
    """What resolution needs from one class file, plus the references it makes."""
    __slots__ = ('name', 'parents', 'fields', 'methods', 'class_refs', 'member_refs')

    def __init__(self, name, parents, fields, methods, class_refs=(), member_refs=()):
        self.name = name
        self.parents = parents
        self.fields = fields
        self.methods = methods
        self.class_refs = set(class_refs)
        self.member_refs = set(member_refs)


def _descriptor_classes(desc):
    return _DESC_CLASS.findall(desc)


def _ref_class(name):
    """Class name of a CONSTANT_Class entry: arrays contribute their element class."""
    if name.startswith('['):
        found = _descriptor_classes(name)
        return found[0] if found else None
    return name


def parse_class(data, refs=True):
    """Parse one class file. Attributes are skipped; references come from the constant
    pool (Class, Fieldref, Methodref, InterfaceMethodref, descriptors) and the class's
    own member descriptors, which is where every linkage the verifier checks lives."""
    pos = [8]

    def u1():
        v = data[pos[0]]
        pos[0] += 1
        return v

    def u2():
        v = struct.unpack_from('>H', data, pos[0])[0]
        pos[0] += 2
        return v

    def u4():
        v = struct.unpack_from('>I', data, pos[0])[0]
        pos[0] += 4
        return v

    n = u2()
    pool = [None] * n
    k = 1
    while k < n:
        t = u1()
        if t == 1:
            l = u2()
            pool[k] = data[pos[0]:pos[0] + l].decode('utf8', 'replace')
            pos[0] += l
        elif t in (7, 8, 16, 19, 20):
            pool[k] = (t, u2())
        elif t in (9, 10, 11, 12, 17, 18):
            pool[k] = (t, u2(), u2())
        elif t in (3, 4):
            pos[0] += 4
        elif t in (5, 6):
            pos[0] += 8
            k += 1
        elif t == 15:
            pool[k] = (t, u1(), u2())
        else:
            raise ValueError('bad constant tag %d' % t)
        k += 1

    def utf(i):
        return pool[i]

    def cls(i):
        return utf(pool[i][1])

    u2()  # access flags
    name = cls(u2())
    s = u2()
    parents = [cls(s)] if s else []
    parents += [cls(u2()) for _ in range(u2())]

    own_descs = []

    def members():
        result = set()
        for _ in range(u2()):
            u2()
            mname = utf(u2())
            desc = utf(u2())
            result.add((mname, desc))
            own_descs.append(desc)
            for _ in range(u2()):
                u2()
                length = u4()  # two statements: `pos[0] += u4()` reads pos before u4 advances it
                pos[0] += length
        return result

    fields = members()
    methods = members()
    info = ClassInfo(name, parents, fields, methods)
    if not refs:
        return info

    class_refs = set()
    member_refs = set()
    descs = list(own_descs)
    for entry in pool:
        if not isinstance(entry, tuple):
            continue
        t = entry[0]
        if t == 7:
            c = _ref_class(utf(entry[1]))
            if c:
                class_refs.add(c)
        elif t in (9, 10, 11):
            owner = utf(pool[entry[1]][1])
            nt = pool[entry[2]]
            member_refs.add((t, owner, utf(nt[1]), utf(nt[2])))
        elif t == 12:
            descs.append(utf(entry[2]))
        elif t == 16:
            descs.append(utf(entry[1]))
    for d in descs:
        class_refs.update(_descriptor_classes(d))
    class_refs.discard(name)
    info.class_refs = class_refs
    info.member_refs = member_refs
    return info


# ---------------------------------------------------------------------------
# Host surface and resolution
# ---------------------------------------------------------------------------

def _is_class_entry(n):
    return n.endswith('.class') and not n.startswith('META-INF/') and not n.endswith('module-info.class')


class Surface(object):
    """Lazy index over directories of .class files and jars; first source wins."""

    def __init__(self, sources):
        self.sources = list(sources)
        self.index = {}
        self._jars = {}
        self._cache = {}
        self._members = {}
        for path in self.sources:
            if os.path.isdir(path):
                for root, _, files in os.walk(path):
                    for f in files:
                        if f.endswith('.class') and f != 'module-info.class':
                            full = os.path.join(root, f)
                            name = os.path.relpath(full, path).replace(os.sep, '/')[:-6]
                            self.index.setdefault(name, (path, full))
            else:
                z = zipfile.ZipFile(path)
                self._jars[path] = z
                for n in z.namelist():
                    if _is_class_entry(n):
                        self.index.setdefault(n[:-6], (path, n))

    def __contains__(self, name):
        return name in self.index

    def get(self, name):
        if name in self._cache:
            return self._cache[name]
        loc = self.index.get(name)
        info = None
        if loc is not None:
            source, key = loc
            if source in self._jars:
                data = self._jars[source].read(key)
            else:
                with open(key, 'rb') as fh:
                    data = fh.read()
            info = parse_class(data, refs=False)
        self._cache[name] = info
        return info

    def has_member(self, owner, name, desc, field):
        key = (owner, name, desc, field)
        hit = self._members.get(key)
        if hit is None:
            hit = _lookup_member(self.get, owner, name, desc, field, set())
            self._members[key] = hit
        return hit

    def close(self):
        for z in self._jars.values():
            z.close()


_POLYMORPHIC = {'java/lang/invoke/MethodHandle', 'java/lang/invoke/VarHandle'}


def _lookup_member(get, owner, name, desc, field, seen):
    if owner in seen:
        return False
    seen.add(owner)
    c = get(owner)
    if c is None:
        return False
    table = c.fields if field else c.methods
    if (name, desc) in table:
        return True
    if owner in _POLYMORPHIC and not field and any(m == name for m, _ in table):
        return True
    if name == '<init>' or name == '<clinit>':
        return False
    for p in c.parents:
        if _lookup_member(get, p, name, desc, field, seen):
            return True
    return False


class Resolver(object):
    """Host surface plus the plugin's own classes. The host is searched first because the
    plugin's DexClassLoader is parent-first: a library the plugin shades is shadowed by
    the host's copy, so a member missing from the host copy is a real gap."""

    def __init__(self, surface, overlay):
        self.surface = surface
        self.overlay = overlay

    def get(self, name):
        info = self.surface.get(name)
        if info is None:
            info = self.overlay.get(name)
        return info

    def has_class(self, name):
        return name in self.surface or name in self.overlay or name in DESUGARED_CLASSES

    def has_member(self, owner, name, desc, field):
        if owner.startswith('['):
            if name == 'clone' and not field:
                return True
            owner = 'java/lang/Object'
        if owner in DESUGARED_CLASSES:
            return True
        if owner in self.surface:
            if self.surface.has_member(owner, name, desc, field):
                return True
            if not field and _NIO_BUFFER.match(owner) and desc.endswith('L%s;' % owner):
                base = desc[:desc.rindex(')') + 1] + 'Ljava/nio/Buffer;'
                return self.surface.has_member(owner, name, base, field)
            return False
        return _lookup_member(self.get, owner, name, desc, field, set())

    def missing_ancestor(self, owner):
        """First class in owner's superclass/interface chain that no source defines, or
        None. A member unresolved on a plugin's own class is attributed to it: the gap is
        the host class the plugin extends, not the plugin's subclass."""
        seen = set()
        stack = [owner]
        while stack:
            n = stack.pop()
            if n in seen:
                continue
            seen.add(n)
            c = self.get(n)
            if c is None:
                return None if n == owner else n
            stack.extend(reversed(c.parents))
        return None


def scan_jar_classes(classes):
    """classes: iterable of ClassInfo parsed with refs. Returns {ClassInfo.name: ClassInfo}."""
    return dict((c.name, c) for c in classes)


def unresolved_references(surface, classes):
    """Resolve every reference of the given plugin classes. Returns
    {(kind, owner, name, desc): referencing-class-count} where kind is 'class',
    'field' or 'method'; 'ownerMissing' is folded into the kind for members whose
    owner class is absent altogether ('field!' / 'method!')."""
    overlay = scan_jar_classes(classes)
    resolver = Resolver(surface, overlay)
    out = {}
    for c in classes:
        for ref in c.class_refs:
            if not resolver.has_class(ref):
                key = ('class', ref, '', '')
                out[key] = out.get(key, 0) + 1
        for tag, owner, name, desc in c.member_refs:
            field = tag == 9
            if resolver.has_member(owner, name, desc, field):
                continue
            element = _ref_class(owner) if owner.startswith('[') else owner
            missing = element is not None and not resolver.has_class(element)
            if not missing and owner in overlay and owner not in surface:
                ancestor = resolver.missing_ancestor(owner)
                if ancestor is not None:
                    owner, missing = ancestor, True
            kind = ('field' if field else 'method') + ('!' if missing else '')
            key = (kind, owner, name, desc)
            out[key] = out.get(key, 0) + 1
    return out


def read_plugin_jar(path_or_bytes):
    data = path_or_bytes
    if isinstance(path_or_bytes, (bytes, bytearray)):
        data = io.BytesIO(path_or_bytes)
    classes = []
    with zipfile.ZipFile(data) as z:
        for n in z.namelist():
            if _is_class_entry(n):
                try:
                    classes.append(parse_class(z.read(n)))
                except Exception as e:  # a corrupt entry should not sink the plugin
                    sys.stderr.write('warning: %s!%s: %s\n' % (path_or_bytes, n, e))
    return classes


# ---------------------------------------------------------------------------
# Buckets
# ---------------------------------------------------------------------------

BUCKETS = [
    'layout/input/painting',
    'drawing/geometry/fonts/images',
    'dialogs/windows/desktop',
    'widgets/menus/tables',
    'text/HTML',
    'dnd/datatransfer',
    'tooltips/accessibility',
    'audio',
    'lwjgl/native',
    'guava',
    'other libraries',
    # Not in #62-#72, kept apart so they do not pollute those lists.
    'runelite',
    'other jdk',
]

_RULES = [(re.compile(p), b) for p, b in [
    (r'^(org/lwjgl/|com/sun/jna/|com/jogamp/|net/runelite/rlawt/|java/awt/peer/|sun/awt/|sun/java2d/|java/lang/foreign/)', 'lwjgl/native'),
    (r'^com/google/common/', 'guava'),
    (r'^(javax/sound/|com/sun/media/sound/)', 'audio'),
    (r'^(java/awt/dnd/|java/awt/datatransfer/|javax/swing/TransferHandler|javax/swing/DropMode|sun/awt/datatransfer/)', 'dnd/datatransfer'),
    (r'^(javax/accessibility/|javax/swing/ToolTipManager|javax/swing/JToolTip|javax/swing/plaf/.*ToolTip|javax/swing/plaf/basic/BasicToolTipUI)', 'tooltips/accessibility'),
    (r'^(javax/swing/text/|javax/swing/undo/|javax/swing/JText|javax/swing/JEditorPane|javax/swing/JFormattedTextField|javax/swing/JPasswordField|javax/swing/event/(Document|Caret|Undoable|Hyperlink)|javax/swing/plaf/basic/BasicText|javax/swing/plaf/TextUI)', 'text/HTML'),
    (r'^(java/awt/(Window|Frame|Dialog|FileDialog|Desktop|SystemTray|TrayIcon|Taskbar|Toolkit|Robot|MouseInfo|PointerInfo|GraphicsEnvironment|GraphicsDevice|GraphicsConfiguration|DisplayMode|HeadlessException|SplashScreen|PopupMenu|MenuBar|Menu|MenuItem|CheckboxMenuItem|MenuComponent|MenuShortcut|PrintJob)|javax/swing/(JFrame|JDialog|JWindow|JOptionPane|JFileChooser|JColorChooser|JRootPane|JLayeredPane|JDesktopPane|JInternalFrame|RootPaneContainer|WindowConstants|Popup|PopupFactory|JApplet)|javax/swing/colorchooser/|javax/swing/filechooser/|javax/swing/plaf/basic/Basic(OptionPane|FileChooser|ColorChooser|RootPane|InternalFrame|Desktop)|javax/swing/plaf/(OptionPane|FileChooser|ColorChooser|RootPane|InternalFrame|Desktop))', 'dialogs/windows/desktop'),
    (r'^(javax/swing/(JTable|JTree|JList|JComboBox|JSpinner|JMenu|JPopupMenu|JCheckBox|JRadioButton|JButton|AbstractButton|JToggleButton|JSlider|JProgressBar|JTabbedPane|JScroll|JViewport|JSplitPane|JToolBar|JSeparator|JLabel|JPanel|ButtonGroup|ButtonModel|DefaultButtonModel|DefaultListModel|AbstractListModel|ListModel|ComboBoxModel|DefaultComboBoxModel|MutableComboBoxModel|SpinnerModel|SpinnerNumberModel|SpinnerListModel|SpinnerDateModel|AbstractSpinnerModel|BoundedRangeModel|DefaultBoundedRangeModel|ListCellRenderer|DefaultListCellRenderer|ListSelectionModel|DefaultListSelectionModel|ComboBoxEditor|CellEditor|DefaultCellEditor|AbstractCellEditor|CellRendererPane|Renderer|Scrollable|ScrollPaneConstants|ScrollPaneLayout|ViewportLayout|MenuElement|MenuSelectionManager|SingleSelectionModel|DefaultSingleSelectionModel|UIManager|UIDefaults|LookAndFeel|UIClientPropertyKey|SwingConstants|JLayer|JComponent\$)|javax/swing/table/|javax/swing/tree/|javax/swing/event/|javax/swing/border/|javax/swing/plaf/|com/formdev/flatlaf/)', 'widgets/menus/tables'),
    (r'^(java/awt/(Graphics|Color|SystemColor|Font|Image|Shape|Polygon|Rectangle|Point|Dimension|Stroke|BasicStroke|Paint|GradientPaint|LinearGradientPaint|RadialGradientPaint|MultipleGradientPaint|TexturePaint|RenderingHints|Composite|AlphaComposite|Transparency|PaintContext|ImageCapabilities|Insets)|java/awt/font/|java/awt/geom/|java/awt/image/|java/awt/color/|java/awt/print/|javax/imageio/|javax/print/|javax/swing/(ImageIcon|Icon|GrayFilter|DebugGraphics|SizeRequirements)|javax/swing/plaf/(ColorUIResource|FontUIResource|IconUIResource|DimensionUIResource|InsetsUIResource)|com/sun/imageio/|sun/font/|com/sun/image/)', 'drawing/geometry/fonts/images'),
    (r'^(java/awt/|javax/swing/)', 'layout/input/painting'),
    (r'^(net/runelite/|rs117/)', 'runelite'),
    (r'^(javax/annotation/|javax/inject/)', 'other libraries'),  # jsr305 / javax.inject jars, not the JDK
    (r'^(java/|javax/|sun/|com/sun/|jdk/|org/w3c/|org/xml/|org/ietf/|org/omg/)', 'other jdk'),
]]


def classify(owner):
    """Bucket of an unresolved owner (internal name, arrays stripped)."""
    name = _ref_class(owner) if owner.startswith('[') else owner
    if name is None:
        return 'other jdk'
    for rx, bucket in _RULES:
        if rx.match(name):
            return bucket
    return 'other libraries'


# ---------------------------------------------------------------------------
# Hub access
# ---------------------------------------------------------------------------

def http_get(url, timeout=60, retries=3):
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
            with urllib.request.urlopen(req, timeout=timeout) as res:
                return res.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise
            last = e
        except (urllib.error.URLError, OSError) as e:
            last = e
        time.sleep(1.5 * (attempt + 1))
    raise last


def parse_signed_manifest(blob):
    """Hub manifests are [int32 signature length][signature][json]; the signature is
    RSA over the json which we skip rather than reimplement."""
    n = struct.unpack_from('>I', blob, 0)[0]
    return json.loads(blob[4 + n:].decode('utf8'))


def jar_hash(data):
    return base64.urlsafe_b64encode(hashlib.sha256(data).digest()).rstrip(b'=').decode('ascii')


def jar_file_name(jar):
    return '%s_%s.jar' % (jar['internalName'], jar['jarHash'])


def project_version():
    with open(os.path.join(ROOT, 'gradle.properties'), encoding='utf8') as fh:
        for line in fh:
            if line.startswith('project.build.version='):
                return line.split('=', 1)[1].strip()
    return None


def fetch_manifest(hub_url, version, cache_dir, refresh):
    """Manifest for the given version, falling back to the live client version from
    bootstrap.json when this checkout is ahead of what the hub has published."""
    tried = []
    candidates = [version]
    while candidates:
        v = candidates.pop(0)
        tried.append(v)
        cached = os.path.join(cache_dir, 'manifest-%s_full.json' % v)
        if os.path.exists(cached) and not refresh:
            with open(cached, encoding='utf8') as fh:
                return v, json.load(fh)
        try:
            blob = http_get('%s/manifest/%s_full.js' % (hub_url, v))
        except urllib.error.HTTPError as e:
            if e.code == 404 and len(tried) == 1:
                live = json.loads(http_get(BOOTSTRAP_URL).decode('utf8'))['client']['version']
                if live not in tried:
                    sys.stderr.write('hub has no %s manifest, falling back to %s\n' % (v, live))
                    candidates.append(live)
                continue
            raise
        manifest = parse_signed_manifest(blob)
        os.makedirs(cache_dir, exist_ok=True)
        with open(cached, 'w', encoding='utf8') as fh:
            json.dump(manifest, fh)
        return v, manifest
    raise SystemExit('no hub manifest for %s' % ', '.join(tried))


def fetch_install_counts(api_url, version, cache_dir, refresh):
    cached = os.path.join(cache_dir, 'counts-%s.json' % version)
    if os.path.exists(cached) and not refresh and time.time() - os.path.getmtime(cached) < 86400:
        with open(cached, encoding='utf8') as fh:
            return json.load(fh)
    try:
        counts = json.loads(http_get('%s/runelite-%s/pluginhub' % (api_url, version)).decode('utf8'))
    except Exception as e:
        sys.stderr.write('warning: no install counts (%s)\n' % e)
        if os.path.exists(cached):
            with open(cached, encoding='utf8') as fh:
                return json.load(fh)
        return {}
    os.makedirs(cache_dir, exist_ok=True)
    with open(cached, 'w', encoding='utf8') as fh:
        json.dump(counts, fh)
    return counts


def ensure_jar(hub_url, jar, jar_dir):
    """Download one hub jar unless a cached copy with the manifest's size exists.
    Downloads go to .part and are hash-checked before the rename, so a killed run never
    leaves a half jar under the final name."""
    path = os.path.join(jar_dir, jar_file_name(jar))
    if os.path.exists(path) and os.path.getsize(path) == jar['jarSize']:
        return path, False
    data = http_get('%s/jar/%s' % (hub_url, jar_file_name(jar)), timeout=120)
    if jar_hash(data) != jar['jarHash']:
        raise IOError('hash mismatch for %s' % jar_file_name(jar))
    part = path + '.part'
    with open(part, 'wb') as fh:
        fh.write(data)
    os.replace(part, path)
    return path, True


# ---------------------------------------------------------------------------
# Host surface discovery
# ---------------------------------------------------------------------------

def _version_key(v):
    return tuple(int(x) if x.isdigit() else -1 for x in re.split(r'[.\-]', v))


def _newest(paths, pattern):
    best = None
    for p in paths:
        m = re.search(pattern, os.path.basename(p))
        if m and (best is None or _version_key(m.group(1)) > _version_key(best[0])):
            best = (m.group(1), p)
    return best[1] if best else None


def find_awt_classes():
    for variant in ('release', 'debug'):
        p = os.path.join(ROOT, 'android-awt', 'build', 'intermediates', 'javac', variant,
                         'compile%sJavaWithJavac' % variant.capitalize(), 'classes')
        if os.path.isdir(os.path.join(p, 'java', 'awt')):
            return p
    return None


def find_app_classes():
    for variant in ('release', 'debug'):
        p = os.path.join(ROOT, 'android', 'build', 'intermediates', 'javac', variant,
                         'compile%sJavaWithJavac' % variant.capitalize(), 'classes')
        if os.path.isdir(p):
            return p
    return None


def read_sdk_dir():
    lp = os.path.join(ROOT, 'local.properties')
    if os.path.exists(lp):
        with open(lp, encoding='utf8') as fh:
            for line in fh:
                if line.startswith('sdk.dir='):
                    return line.split('=', 1)[1].strip().replace('\\:', ':').replace('\\\\', '\\')
    return os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')


def read_compile_sdk():
    with open(os.path.join(ROOT, 'android', 'build.gradle.kts'), encoding='utf8') as fh:
        m = re.search(r'compileSdk\s*=\s*(\d+)', fh.read())
    return int(m.group(1)) if m else None


def find_android_jar(sdk_dir=None):
    sdk_dir = sdk_dir or read_sdk_dir()
    level = read_compile_sdk()
    if not sdk_dir or level is None:
        return None
    hits = glob.glob(os.path.join(sdk_dir, 'platforms', 'android-%d*' % level, 'android.jar'))
    return sorted(hits)[-1] if hits else None


def find_module_jar(module, prefix):
    libs = glob.glob(os.path.join(ROOT, module, 'build', 'libs', prefix + '-*.jar'))
    libs = [p for p in libs if not re.search(r'-(sources|javadoc|shaded|runtime)\.jar$', p)]
    want = project_version()
    for p in libs:
        if want and os.path.basename(p) == '%s-%s.jar' % (prefix, want):
            return p
    return _newest(libs, r'^%s-(.+)\.jar$' % re.escape(prefix))


def pinned_lib_versions():
    """Versions android/build.gradle.kts states inline ("group:artifact:version")."""
    pins = {}
    with open(os.path.join(ROOT, 'android', 'build.gradle.kts'), encoding='utf8') as fh:
        for g, a, v in re.findall(r'"([\w.\-]+):([\w.\-]+):([\w.\-]+)"', fh.read()):
            pins[(g, a)] = v
    return pins


def find_gradle_lib(group, artifact, version=None, gradle_home=None):
    gradle_home = gradle_home or os.path.join(os.path.expanduser('~'), '.gradle')
    base = os.path.join(gradle_home, 'caches', 'modules-2', 'files-2.1', group, artifact)
    pattern = '%s-%s.jar' % (artifact, version) if version else '%s-*.jar' % artifact
    hits = [p for p in glob.glob(os.path.join(base, '*', '*', pattern))
            if not re.search(r'-(sources|javadoc)\.jar$', p)]
    if not hits:
        return None
    if version:
        return hits[0]
    return _newest(hits, r'^%s-(.+)\.jar$' % re.escape(artifact))


def default_libs():
    pins = pinned_lib_versions()
    out = []
    for group, artifact in DEFAULT_LIBS:
        p = find_gradle_lib(group, artifact, pins.get((group, artifact))) or find_gradle_lib(group, artifact)
        if p:
            out.append(p)
        else:
            sys.stderr.write('warning: %s:%s not in the gradle cache; pass --lib\n' % (group, artifact))
    return out


def surface_fingerprint(sources):
    """Changes whenever the host surface or this script does, so cached per-plugin scans
    are reused only while their answers would be identical."""
    h = hashlib.sha1()
    with open(os.path.abspath(__file__), 'rb') as fh:
        h.update(fh.read())
    for s in sources:
        h.update(s.encode('utf8'))
        if os.path.isdir(s):
            for root, dirs, files in os.walk(s):
                dirs.sort()
                for f in sorted(files):
                    st = os.stat(os.path.join(root, f))
                    h.update(('%s%d%d' % (f, st.st_size, int(st.st_mtime))).encode('utf8'))
        else:
            st = os.stat(s)
            h.update(('%d%d' % (st.st_size, int(st.st_mtime))).encode('utf8'))
    return h.hexdigest()[:12]


# ---------------------------------------------------------------------------
# Workers
# ---------------------------------------------------------------------------

_SURFACE = None


def _init_worker(sources):
    global _SURFACE
    _SURFACE = Surface(sources)


def scan_one(jar_path):
    """Scan one plugin jar in a worker; returns a JSON-able dict."""
    try:
        classes = read_plugin_jar(jar_path)
        unresolved = unresolved_references(_SURFACE, classes)
        return {
            'classes': len(classes),
            'unresolved': [[k[0], k[1], k[2], k[3], n] for k, n in unresolved.items()],
        }
    except Exception as e:
        return {'classes': 0, 'unresolved': [], 'error': '%s: %s' % (type(e).__name__, e)}


def scan_all(jobs, sources, work, scan_dir, fingerprint):
    """work: list of (jar dict, path). Returns {internalName: result}; results are cached
    under scan_dir by jar hash + surface fingerprint."""
    results = {}
    todo = []
    for jar, path in work:
        cached = os.path.join(scan_dir, '%s_%s-%s.json' % (jar['internalName'], jar['jarHash'], fingerprint))
        if os.path.exists(cached):
            with open(cached, encoding='utf8') as fh:
                results[jar['internalName']] = json.load(fh)
        else:
            todo.append((jar, path, cached))
    if not todo:
        return results
    os.makedirs(scan_dir, exist_ok=True)
    started = time.time()
    done = 0

    def finish(jar, cached, result):
        with open(cached, 'w', encoding='utf8') as fh:
            json.dump(result, fh)
        results[jar['internalName']] = result

    if jobs <= 1:
        _init_worker(sources)
        for jar, path, cached in todo:
            finish(jar, cached, scan_one(path))
            done += 1
            progress('scan', done, len(todo), started)
    else:
        with concurrent.futures.ProcessPoolExecutor(max_workers=jobs, initializer=_init_worker,
                                                    initargs=(sources,)) as pool:
            futures = {pool.submit(scan_one, path): (jar, cached) for jar, path, cached in todo}
            for fut in concurrent.futures.as_completed(futures):
                jar, cached = futures[fut]
                finish(jar, cached, fut.result())
                done += 1
                progress('scan', done, len(todo), started)
    return results


def progress(label, done, total, started):
    if done == total or done % 10 == 0:
        elapsed = time.time() - started
        sys.stderr.write('%s %d/%d (%.0fs)\n' % (label, done, total, elapsed))


# ---------------------------------------------------------------------------
# Reports
# ---------------------------------------------------------------------------

def sig_key(kind, owner, name, desc):
    return kind.rstrip('!') + ' ' + owner + ('' if kind == 'class' else '.' + name + desc)


def build_report(results, manifest_jars, display, counts, meta):
    """Aggregate per-plugin results into the JSON report structure."""
    by_name = dict((d['internalName'], d) for d in display)
    jars = dict((j['internalName'], j) for j in manifest_jars)
    signatures = {}
    plugins = []
    failed = []
    for internal, result in results.items():
        installs = counts.get(internal, 0)
        if result.get('error'):
            failed.append({'internalName': internal, 'error': result['error']})
        per_bucket = {}
        for kind, owner, name, desc, n in result['unresolved']:
            bucket = classify(owner)
            per_bucket[bucket] = per_bucket.get(bucket, 0) + 1
            key = sig_key(kind, owner, name, desc)
            entry = signatures.get(key)
            if entry is None:
                entry = signatures[key] = {
                    'kind': kind.rstrip('!'),
                    'owner': owner,
                    'name': name,
                    'descriptor': desc,
                    'ownerMissing': kind == 'class' or kind.endswith('!'),
                    'bucket': bucket,
                    'plugins': [],
                    'pluginCount': 0,
                    'referenceCount': 0,
                    'installs': 0,
                }
            entry['plugins'].append(internal)
            entry['pluginCount'] += 1
            entry['referenceCount'] += n
            entry['installs'] += installs
        d = by_name.get(internal, {})
        plugins.append({
            'internalName': internal,
            'displayName': d.get('displayName') or jars.get(internal, {}).get('displayName') or internal,
            'version': d.get('version'),
            'installs': installs,
            'classes': result['classes'],
            'unresolved': len(result['unresolved']),
            'buckets': per_bucket,
            'error': result.get('error'),
        })
    for e in signatures.values():
        e['plugins'].sort(key=lambda p: (-counts.get(p, 0), p))
    ordered = sorted(signatures.values(), key=lambda e: (-e['installs'], -e['pluginCount'], e['owner'], e['name'], e['descriptor']))
    plugins.sort(key=lambda p: (-p['installs'], p['internalName']))
    buckets = {}
    for b in BUCKETS:
        entries = [e for e in ordered if e['bucket'] == b]
        buckets[b] = {
            'signatures': len(entries),
            'plugins': len(set(p for e in entries for p in e['plugins'])),
        }
    report = dict(meta)
    report.update({
        'pluginsScanned': len(plugins),
        'pluginsFailed': failed,
        'buckets': buckets,
        'unresolved': ordered,
        'plugins': plugins,
    })
    return report


def _md_escape(s):
    return s.replace('|', '\\|')


def render_markdown(report, top_plugins=5, max_per_owner=60):
    out = []
    w = out.append
    w('# Plugin Hub compatibility scan')
    w('')
    w('Generated %s against hub manifest %s; %d plugins scanned (%d failed).' % (
        report['generatedAt'], report['hubVersion'], report['pluginsScanned'], len(report['pluginsFailed'])))
    w('')
    w('Host surface:')
    w('')
    for s in report['hostSources']:
        w('- `%s`' % s)
    w('')
    w('An unresolved signature is a class, field or method a plugin references that none of the host sources (nor the plugin jar itself) defines. `installs` sums the install counts of the plugins referencing it; a signature marked *(class missing)* has no owner class at all.')
    w('')
    w('## Buckets')
    w('')
    w('| bucket | unresolved signatures | plugins |')
    w('|---|---:|---:|')
    for b in BUCKETS:
        info = report['buckets'][b]
        w('| %s | %d | %d |' % (b, info['signatures'], info['plugins']))
    w('')
    for b in BUCKETS:
        entries = [e for e in report['unresolved'] if e['bucket'] == b]
        if not entries:
            continue
        w('## %s' % b)
        w('')
        owners = {}
        for e in entries:
            owners.setdefault(e['owner'], []).append(e)
        for owner, items in sorted(owners.items(), key=lambda kv: (-max(i['installs'] for i in kv[1]), kv[0])):
            class_entry = [i for i in items if i['kind'] == 'class']
            members = [i for i in items if i['kind'] != 'class']
            note = ' *(class missing)*' if class_entry or any(i['ownerMissing'] for i in members) else ''
            head = class_entry[0] if class_entry else None
            stats = (' — %d plugins, installs %d' % (head['pluginCount'], head['installs'])) if head else ''
            w('### `%s`%s%s' % (owner, note, stats))
            w('')
            if head:
                w('Referenced by: %s' % ', '.join('`%s`' % p for p in head['plugins'][:top_plugins])
                  + (' …' if len(head['plugins']) > top_plugins else ''))
                w('')
            if members:
                w('| member | kind | plugins | refs | installs | top plugins |')
                w('|---|---|---:|---:|---:|---|')
                for i in members[:max_per_owner]:
                    w('| `%s%s` | %s | %d | %d | %d | %s |' % (
                        _md_escape(i['name']), _md_escape(i['descriptor']), i['kind'], i['pluginCount'],
                        i['referenceCount'], i['installs'],
                        ', '.join('`%s`' % p for p in i['plugins'][:top_plugins])))
                if len(members) > max_per_owner:
                    w('| … %d more | | | | | |' % (len(members) - max_per_owner))
                w('')
    w('## Plugins')
    w('')
    w('Sorted by install count. Bucket columns count unresolved signatures of that plugin.')
    w('')
    short = {b: b.split('/')[0] for b in BUCKETS}
    w('| plugin | installs | classes | unresolved | ' + ' | '.join(short[b] for b in BUCKETS) + ' |')
    w('|---|---:|---:|---:|' + '---:|' * len(BUCKETS))
    for p in report['plugins']:
        cells = [str(p['buckets'].get(b, 0)) for b in BUCKETS]
        name = p['internalName'] + (' (scan failed)' if p.get('error') else '')
        w('| %s | %d | %d | %d | %s |' % (name, p['installs'], p['classes'], p['unresolved'], ' | '.join(cells)))
    w('')
    return '\n'.join(out)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def build_parser():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--limit', type=int, help='scan only the N most-installed plugins')
    ap.add_argument('--plugin', action='append', help='scan only these internal names (repeatable)')
    ap.add_argument('--hub-url', default=DEFAULT_HUB_URL)
    ap.add_argument('--hub-version', help='manifest version (default: gradle.properties project.build.version)')
    ap.add_argument('--api-url', default=DEFAULT_API_URL, help='api base for install counts')
    ap.add_argument('--cache-dir', default=os.path.join(ROOT, 'build', 'hub-cache'))
    ap.add_argument('--out-dir', default=os.path.join(ROOT, 'build', 'hub-compat'))
    ap.add_argument('--jobs', type=int, default=max(1, min(8, os.cpu_count() or 1)))
    ap.add_argument('--offline', action='store_true', help='use cached manifest and jars only')
    ap.add_argument('--refresh', action='store_true', help='re-download manifest and install counts')
    ap.add_argument('--awt-classes', help='android-awt javac output (default: build/intermediates)')
    ap.add_argument('--app-classes', help='android app javac output with the lwjgl/rlawt shims')
    ap.add_argument('--android-jar', help='android.jar (default: sdk.dir + compileSdk)')
    ap.add_argument('--sdk-dir')
    ap.add_argument('--client-jar', help='runelite-client jar (default: newest in runelite-client/build/libs)')
    ap.add_argument('--api-jar', help='runelite-api jar (default: newest in runelite-api/build/libs)')
    ap.add_argument('--lib', action='append', default=[], help='extra host jar or class directory (repeatable)')
    ap.add_argument('--no-default-libs', action='store_true', help='skip the gradle-cache library lookup')
    return ap


def resolve_sources(args):
    sources = []

    def add(label, path, required=True):
        if path and os.path.exists(path):
            sources.append(os.path.abspath(path))
        elif required:
            raise SystemExit('missing host source: %s (%s)' % (label, path or 'not found'))
        else:
            sys.stderr.write('warning: %s not found, skipping\n' % label)

    add('android-awt classes (run :android-awt:compileDebugJavaWithJavac)', args.awt_classes or find_awt_classes())
    add('android.jar', args.android_jar or find_android_jar(args.sdk_dir))
    add('runelite-client jar', args.client_jar or find_module_jar('runelite-client', 'client'))
    add('runelite-api jar', args.api_jar or find_module_jar('runelite-api', 'runelite-api'))
    add('android app classes', args.app_classes or find_app_classes(), required=False)
    libs = list(args.lib)
    if not args.no_default_libs:
        libs += default_libs()
    for lib in libs:
        add(lib, lib)
    return sources


def main(argv=None):
    args = build_parser().parse_args(argv)
    sources = resolve_sources(args)
    for s in sources:
        sys.stderr.write('host: %s\n' % s)

    version = args.hub_version or project_version()
    jar_dir = os.path.join(args.cache_dir, 'jar')
    os.makedirs(jar_dir, exist_ok=True)
    if args.offline:
        cached = sorted(glob.glob(os.path.join(args.cache_dir, 'manifest-*_full.json')), key=os.path.getmtime)
        if not cached:
            raise SystemExit('--offline but no cached manifest under %s' % args.cache_dir)
        version = re.search(r'manifest-(.+)_full\.json$', os.path.basename(cached[-1])).group(1)
        with open(cached[-1], encoding='utf8') as fh:
            manifest = json.load(fh)
        counts = fetch_install_counts(args.api_url, version, args.cache_dir, False) if os.path.exists(
            os.path.join(args.cache_dir, 'counts-%s.json' % version)) else {}
    else:
        version, manifest = fetch_manifest(args.hub_url, version, args.cache_dir, args.refresh)
        counts = fetch_install_counts(args.api_url, version, args.cache_dir, args.refresh)

    jars = manifest.get('jars') or []
    display = manifest.get('display') or []
    if args.plugin:
        wanted = set(args.plugin)
        jars = [j for j in jars if j['internalName'] in wanted]
        missing = wanted - set(j['internalName'] for j in jars)
        if missing:
            sys.stderr.write('warning: not in manifest: %s\n' % ', '.join(sorted(missing)))
    jars.sort(key=lambda j: (-counts.get(j['internalName'], 0), j['internalName']))
    if args.limit:
        jars = jars[:args.limit]
    sys.stderr.write('hub %s: %d plugins selected of %d\n' % (version, len(jars), len(manifest.get('jars') or [])))

    work = []
    started = time.time()
    if args.offline:
        for j in jars:
            p = os.path.join(jar_dir, jar_file_name(j))
            if os.path.exists(p):
                work.append((j, p))
        sys.stderr.write('offline: %d of %d jars cached\n' % (len(work), len(jars)))
    else:
        downloaded = 0
        with concurrent.futures.ThreadPoolExecutor(max_workers=min(8, args.jobs * 2)) as pool:
            futures = {pool.submit(ensure_jar, args.hub_url, j, jar_dir): j for j in jars}
            for i, fut in enumerate(concurrent.futures.as_completed(futures), 1):
                j = futures[fut]
                try:
                    path, fresh = fut.result()
                    work.append((j, path))
                    downloaded += fresh
                except Exception as e:
                    sys.stderr.write('warning: %s: %s\n' % (jar_file_name(j), e))
                progress('fetch', i, len(jars), started)
        sys.stderr.write('%d jars downloaded, %d from cache\n' % (downloaded, len(work) - downloaded))

    fingerprint = surface_fingerprint(sources)
    results = scan_all(args.jobs, sources, work, os.path.join(args.cache_dir, 'scan'), fingerprint)

    meta = {
        'generatedAt': datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
        'hubUrl': args.hub_url,
        'hubVersion': version,
        'hostSources': sources,
        'surfaceFingerprint': fingerprint,
    }
    report = build_report(results, jars, display, counts, meta)
    os.makedirs(args.out_dir, exist_ok=True)
    json_path = os.path.join(args.out_dir, 'report.json')
    md_path = os.path.join(args.out_dir, 'report.md')
    with open(json_path, 'w', encoding='utf8') as fh:
        json.dump(report, fh, indent=1)
    with open(md_path, 'w', encoding='utf8') as fh:
        fh.write(render_markdown(report))
    print('%d plugins, %d unresolved signatures -> %s, %s' % (
        report['pluginsScanned'], len(report['unresolved']), json_path, md_path))
    for b in BUCKETS:
        info = report['buckets'][b]
        if info['signatures']:
            print('  %-32s %6d signatures  %5d plugins' % (b, info['signatures'], info['plugins']))
    return 0


if __name__ == '__main__':
    sys.exit(main())
