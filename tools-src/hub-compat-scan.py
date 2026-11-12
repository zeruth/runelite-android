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

--jdk-gaps switches to the JDK 9-21 audit: every java.*/javax.* class and member the
injected client, runelite-client and the selected hub jars reference is resolved against
the platform API at minSdk (26) and at the device API (35), the desugar_jdk_libs
configuration the on-device D8 uses, D8's own backports and the app's retargeting shims.
What API 26 lacks lands in build/hub-compat/jdk-gaps.md and the docs/jdk-api-gaps.json
baseline with a disposition: desugar, retarget, already-on-device-API or unsupported.

Usage:
    python tools-src/hub-compat-scan.py --limit 25
    python tools-src/hub-compat-scan.py --plugin quest-helper --plugin tile-packs
    python tools-src/hub-compat-scan.py --offline            # only what is cached
    python tools-src/hub-compat-scan.py --jdk-gaps --limit 200
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
import xml.etree.ElementTree as ET
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
    # Where javac actually puts the record and pattern-switch bootstraps.
    'java/lang/runtime/ObjectMethods',
    'java/lang/runtime/SwitchBootstraps',
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

def http_get(url, timeout=60, retries=6):
    last = None
    for attempt in range(retries):
        delay = 1.5 * (attempt + 1)
        try:
            req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
            with urllib.request.urlopen(req, timeout=timeout) as res:
                return res.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                raise
            if e.code == 429:
                # The hub rate-limits a full fetch; honour Retry-After, else back off harder.
                after = e.headers.get('Retry-After') if e.headers else None
                delay = float(after) if after and after.isdigit() else 5.0 * (attempt + 1)
            last = e
        except (urllib.error.URLError, OSError) as e:
            last = e
        time.sleep(delay)
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


def find_module_jar(module, prefix, root=ROOT):
    libs = glob.glob(os.path.join(root, module, 'build', 'libs', prefix + '-*.jar'))
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


def scan_all(jobs, sources, work, scan_dir, fingerprint, fn=None):
    """work: list of (jar dict, path). Returns {internalName: result}; results are cached
    under scan_dir by jar hash + fingerprint. fn is the per-jar worker (default scan_one,
    which needs the host surface; any other worker gets no initializer)."""
    fn = fn or scan_one
    init = _init_worker if fn is scan_one else None
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
        if init:
            init(sources)
        for jar, path, cached in todo:
            finish(jar, cached, fn(path))
            done += 1
            progress('scan', done, len(todo), started)
    else:
        with concurrent.futures.ProcessPoolExecutor(max_workers=jobs, initializer=init,
                                                    initargs=(sources,) if init else ()) as pool:
            futures = {pool.submit(fn, path): (jar, cached) for jar, path, cached in todo}
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
# JDK 9-21 API gaps (--jdk-gaps)
# ---------------------------------------------------------------------------

DEFAULT_DEVICE_API = 35
DEFAULT_MIN_API = 26

# Worst first: the order of the report sections and of the per-plugin columns.
DISPOSITIONS = ['retarget', 'unsupported', 'already-on-device-API', 'desugar']

# Not the JDK: jsr305 and javax.inject are host libraries, and the main scan checks them.
_JDK_LIBRARY_PREFIXES = ('javax/annotation/', 'javax/inject/')

# D8's own backports (BackportedMethodRewriter), applied for every member whose platform
# API level is above minSdk whether or not core-library desugaring is on. The desugar
# configuration does not list them, so they are tabled here from R8 8.x; a value of None
# covers every member of the class, a name covers every overload, name+descriptor one.
_MATH_BACKPORTS = {'floorDiv(JI)J', 'floorMod(JI)I', 'multiplyExact(JI)J', 'multiplyFull', 'multiplyHigh',
                   'absExact', 'ceilDiv', 'ceilDivExact', 'ceilMod', 'floorDivExact', 'divideExact',
                   'unsignedMultiplyHigh', 'clamp'}
D8_BACKPORTS = {
    'java/lang/String': {'repeat', 'isBlank', 'strip', 'stripLeading', 'stripTrailing', 'join'},
    'java/lang/CharSequence': {'compare'},
    'java/lang/Character': {'toString(I)Ljava/lang/String;', 'compare', 'hashCode(C)I'},
    'java/lang/Byte': {'compareUnsigned'},
    'java/lang/Short': {'compareUnsigned'},
    'java/lang/Integer': {'parseInt(Ljava/lang/CharSequence;III)I', 'parseUnsignedInt(Ljava/lang/CharSequence;III)I'},
    'java/lang/Long': {'parseLong(Ljava/lang/CharSequence;III)J', 'parseUnsignedLong(Ljava/lang/CharSequence;III)J'},
    'java/lang/Math': _MATH_BACKPORTS,
    'java/lang/StrictMath': _MATH_BACKPORTS,
    'java/util/Objects': {'requireNonNullElse', 'requireNonNullElseGet', 'checkIndex', 'checkFromToIndex',
                          'checkFromIndexSize'},
    'java/util/List': {'of', 'copyOf'},
    'java/util/Set': {'of', 'copyOf'},
    'java/util/Map': {'of', 'ofEntries', 'entry', 'copyOf'},
    'java/util/Optional': {'or', 'stream', 'ifPresentOrElse', 'orElseThrow()Ljava/lang/Object;', 'isEmpty'},
    'java/util/OptionalInt': {'stream', 'ifPresentOrElse', 'orElseThrow()I', 'isEmpty'},
    'java/util/OptionalLong': {'stream', 'ifPresentOrElse', 'orElseThrow()J', 'isEmpty'},
    'java/util/OptionalDouble': {'stream', 'ifPresentOrElse', 'orElseThrow()D', 'isEmpty'},
    'java/util/stream/Stream': {'ofNullable'},
    # Record desugaring rewrites the supertype to r8.RecordTag below API 34.
    'java/lang/Record': None,
}

# The app's own retargeting shims: (owner or package prefix ending in '/', member name or
# None for all, what rewrites it). StringShim only strips NULs at the client's string
# writer and stands in for no JDK member, so it has no row.
SHIMS = [
    ('java/util/regex/', None, 'DesktopRegexTransformer -> net.runelite.mp.compat.regex (hub jars)'),
    ('java/lang/String', 'splitWithDelimiters', 'DesktopRegexTransformer -> DesktopRegexStrings (hub jars)'),
    ('java/util/regex/Matcher', 'replaceAll', 'DesugarApisTask -> IndyConcat.replaceAllJava8 (client)'),
    ('java/lang/invoke/MethodHandles', 'privateLookupIn', 'DesugarApisTask -> IndyConcat.privateLookupInJava8 (client)'),
    ('java/lang/ProcessHandle$Info', 'command', 'ClientTransforms -> ProcessHandleShim.sanitizedCommand (client)'),
]

# Members no static helper can stand in for on ART, whatever the API level.
UNSUPPORTED = [
    ('java/lang/Thread', 'ofVirtual', 'virtual threads: ART has no continuation scheduler'),
    ('java/lang/Thread', 'startVirtualThread', 'virtual threads: ART has no continuation scheduler'),
    ('java/lang/Thread$Builder$OfVirtual', None, 'virtual threads: ART has no continuation scheduler'),
    ('java/util/concurrent/Executors', 'newVirtualThreadPerTaskExecutor', 'virtual threads: ART has no continuation scheduler'),
    ('java/lang/foreign/', None, 'foreign function and memory API: not in ART'),
    ('java/lang/Module', None, 'JPMS: ART has no module layer'),
    ('java/lang/ModuleLayer', None, 'JPMS: ART has no module layer'),
    ('java/lang/module/', None, 'JPMS: ART has no module layer'),
    ('java/lang/Class', 'getModule', 'JPMS: ART has no module layer'),
    ('java/lang/instrument/', None, 'java agents: not in ART'),
    ('java/lang/ScopedValue', None, 'preview API'),
    ('java/util/concurrent/StructuredTaskScope', None, 'preview API'),
    ('java/lang/invoke/MethodHandles$Lookup', 'defineHiddenClass', 'class-file definition: ART loads dex'),
    ('java/lang/invoke/MethodHandles$Lookup', 'defineClass', 'class-file definition: ART loads dex'),
    ('javax/tools/', None, 'compiler API: not in ART'),
    ('javax/lang/model/', None, 'compiler API: not in ART'),
    ('javax/annotation/processing/', None, 'compiler API: not in ART'),
]


def _owner_matches(owner, pattern):
    return owner == pattern or (pattern.endswith('/') and owner.startswith(pattern))


def _table_lookup(table, owner, name):
    for pattern, member, why in table:
        if _owner_matches(owner, pattern) and (member is None or member == name):
            return why
    return None


def is_jdk_name(name):
    return (name.startswith('java/') or name.startswith('javax/')) and not name.startswith(_JDK_LIBRARY_PREFIXES)


def _alive(since, removed, level):
    return since <= level and (removed is None or removed > level)


def _api_level(v):
    """api-versions.xml levels: '26', or '37.0' / '36.1' for the minor SDK releases."""
    f = float(v)
    return int(f) if f == int(f) else f


def _opt_int(v):
    return _api_level(v) if v else None


class ApiVersions(object):
    """platforms/android-N/data/api-versions.xml: every public java.*/javax.* class and
    member with the API level it appeared at (and was removed at). Fields carry no
    descriptor there, so field lookups go by name."""

    def __init__(self, path):
        self.path = path
        self.classes = {}
        with open(path, 'rb') as fh:
            self._parse(fh)

    def _parse(self, fh):
        for _, el in ET.iterparse(fh, events=('end',)):
            if el.tag != 'class':
                continue
            name = el.get('name')
            if name.startswith(('java/', 'javax/')):
                since = _api_level(el.get('since', '1'))
                parents, fields, methods = [], {}, {}
                for ch in el:
                    s = _api_level(ch.get('since', since))
                    r = _opt_int(ch.get('removed'))
                    if ch.tag in ('extends', 'implements'):
                        parents.append((ch.get('name'), s, r))
                    elif ch.tag == 'method':
                        sig = ch.get('name')
                        i = sig.index('(')
                        methods[(sig[:i], sig[i:])] = (s, r)
                    elif ch.tag == 'field':
                        fields[ch.get('name')] = (s, r)
                self.classes[name] = (since, _opt_int(el.get('removed')), parents, fields, methods)
            el.clear()

    def class_since(self, name):
        c = self.classes.get(name)
        return c[0] if c else None

    def member_since(self, owner, name, desc, field, level=1 << 30, seen=None):
        """API level the member became reachable on owner at, or None."""
        seen = seen if seen is not None else set()
        if owner in seen:
            return None
        seen.add(owner)
        c = self.classes.get(owner)
        if c is None or not _alive(c[0], c[1], level):
            return None
        since, _, parents, fields, methods = c
        hit = fields.get(name) if field else methods.get((name, desc))
        if hit is None and not field and owner in _POLYMORPHIC:
            hit = next((v for (m, _d), v in methods.items() if m == name), None)
        if hit is not None and _alive(hit[0], hit[1], level):
            return max(hit[0], since)
        if name in ('<init>', '<clinit>'):
            return None
        for p, s, r in parents:
            if _alive(s, r, level):
                got = self.member_since(p, name, desc, field, level, seen)
                if got is not None:
                    return max(got, s, since)
        return None


class ApiLevelSurface(object):
    """Surface-compatible view of ApiVersions at one API level."""

    def __init__(self, api, level):
        self.api = api
        self.level = level
        self.label = '%s@%d' % (api.path, level)

    def __contains__(self, name):
        c = self.api.classes.get(name)
        return c is not None and _alive(c[0], c[1], self.level)

    def get(self, name):
        if name not in self:
            return None
        _, _, parents, fields, methods = self.api.classes[name]
        lv = self.level
        return ClassInfo(name, [p for p, s, r in parents if _alive(s, r, lv)],
                         set((f, None) for f, (s, r) in fields.items() if _alive(s, r, lv)),
                         set(k for k, (s, r) in methods.items() if _alive(s, r, lv)))

    def has_member(self, owner, name, desc, field):
        return self.api.member_since(owner, name, desc, field, self.level) is not None

    def close(self):
        pass


class FilteredSurface(object):
    """A newer android.jar cut back to an older API level by api-versions.xml. The jar
    decides what links (it sees members Android declares on hidden supertypes, such as
    AbstractStringBuilder.setLength, which api-versions.xml omits); the xml only removes
    what it dates after the level."""

    def __init__(self, base, api, level):
        self.base = base
        self.api = api
        self.level = level

    def __contains__(self, name):
        if name not in self.base:
            return False
        since = self.api.class_since(name)
        return since is None or since <= self.level

    def get(self, name):
        return self.base.get(name) if name in self else None

    def has_member(self, owner, name, desc, field):
        if not self.base.has_member(owner, name, desc, field):
            return False
        since = self.api.member_since(owner, name, desc, field)
        return since is None or since <= self.level

    def close(self):
        pass


_PRIMS = {'void': 'V', 'boolean': 'Z', 'byte': 'B', 'char': 'C', 'short': 'S', 'int': 'I',
          'long': 'J', 'float': 'F', 'double': 'D'}
_MODIFIERS = {'public', 'protected', 'private', 'static', 'final', 'abstract', 'synchronized',
              'native', 'default', 'transient', 'volatile', 'strictfp'}


class DesugarConfig(object):
    """What a desugar_jdk_libs configuration (machine format 200, or the human format
    1xx with dotted names) supplies to a program compiled at min_api: whole rewritten types
    and retargeted or amended members. Only common_flags and program_flags apply to the
    program; library_flags are for compiling the library itself."""

    _MEMBER_MAPS = ('static_retarget', 'non_emulated_virtual_retarget', 'emulated_virtual_retarget',
                    'emulated_virtual_retarget_through_emulated_interface', 'covariant_retarget',
                    'retarget_method', 'retarget_method_with_emulated_dispatch', 'covariant_retarget_method')
    _FIELD_MAPS = ('static_field_retarget', 'retarget_static_field')

    def __init__(self, data, min_api):
        self.identifier = data.get('identifier') or 'desugar_jdk_libs'
        self.min_api = min_api
        self.prefixes = dict((v, k) for k, v in (data.get('package_map') or {}).items())
        self.types = {}
        self.members = {}
        short = self.identifier.split(':')[-1]
        for group in ('common_flags', 'program_flags'):
            for flag in data.get(group) or []:
                lo = flag.get('api_level_greater_or_equal', 0)
                hi = flag.get('api_level_below_or_equal', 1 << 30)
                if not (lo <= min_api <= hi):
                    continue
                for key in ('rewrite_type', 'rewrite_prefix'):
                    for t in flag.get(key) or {}:
                        self.types.setdefault(self.type_name(t), 'desugar_jdk_libs %s %s' % (short, key))
                for key in self._MEMBER_MAPS:
                    for sig in flag.get(key) or {}:
                        self._add(self.parse_method(sig), key, short)
                for key in self._FIELD_MAPS:
                    for sig in flag.get(key) or {}:
                        self._add(self.parse_field(sig), key, short)
                for sig in flag.get('amend_library_method') or []:
                    self._add(self.parse_method(sig), 'amend_library_method', short)
                for sig in flag.get('amend_library_field') or []:
                    self._add(self.parse_field(sig), 'amend_library_field', short)
                for spec in (flag.get('emulated_interface') or {}).values():
                    if isinstance(spec, list) and len(spec) > 1 and isinstance(spec[1], dict):
                        for sig in spec[1]:
                            self._add(self.parse_method(sig), 'emulated_interface', short)

    def _add(self, key, how, short):
        if key is not None:
            self.members.setdefault(key, 'desugar_jdk_libs %s %s' % (short, how))

    def type_name(self, t):
        t = t.strip()
        m = re.match(r'^([A-Za-z])\$(.+)$', t)
        if m and m.group(1) + '$' in self.prefixes:
            return self.prefixes[m.group(1) + '$'].replace('.', '/') + '/' + m.group(2)
        return t.replace('.', '/')

    def type_desc(self, t):
        t = t.strip()
        dims = 0
        while t.endswith('[]'):
            t, dims = t[:-2], dims + 1
        return '[' * dims + (_PRIMS.get(t) or 'L%s;' % self.type_name(t))

    @staticmethod
    def _strip_modifiers(sig):
        words = sig.strip().split(' ')
        while words and words[0] in _MODIFIERS:
            words.pop(0)
        return ' '.join(words)

    def parse_method(self, sig):
        """'ret owner#name(p1, p2)' -> (owner, name, descriptor)."""
        try:
            rest = self._strip_modifiers(sig)
            i = rest.index('(')
            ret, ref = rest[:i].split(' ', 1)
            owner, name = ref.strip().rsplit('#', 1)
            params = [p for p in rest[i + 1:rest.rindex(')')].split(',') if p.strip()]
            return (self.type_name(owner), name,
                    '(%s)%s' % (''.join(self.type_desc(p) for p in params), self.type_desc(ret)))
        except ValueError:
            sys.stderr.write('warning: unparsed desugar signature %r\n' % sig)
            return None

    def parse_field(self, sig):
        """'type owner#name' -> (owner, name, None): field lookups go by name."""
        try:
            _, ref = self._strip_modifiers(sig).split(' ', 1)
            owner, name = ref.strip().rsplit('#', 1)
            return (self.type_name(owner), name, None)
        except ValueError:
            sys.stderr.write('warning: unparsed desugar signature %r\n' % sig)
            return None

    def covers(self, kind, owner, name, desc, ancestors):
        """How the configuration supplies the reference, or None. ancestors: owner and its
        supertypes, so an inherited default (ArrayList.toArray(IntFunction)) matches the
        interface the configuration names."""
        for a in ancestors:
            if a in self.types:
                return self.types[a]
            if kind == 'class':
                return None
            key = (a, name, None) if kind == 'field' else (a, name, desc)
            if key in self.members:
                return self.members[key]
        return None


def d8_backport(kind, owner, name, desc):
    if owner not in D8_BACKPORTS:
        return None
    names = D8_BACKPORTS[owner]
    if names is None:
        return 'D8 backport'
    if kind == 'method' and (name in names or name + desc in names):
        return 'D8 backport'
    return None


def read_shadow_owners(path=None):
    """android-awt's shadowPackages / shadowClasses (as internal-name prefixes): the
    surface the main scan audits, left out of the JDK gap list."""
    path = path or os.path.join(ROOT, 'android-awt', 'build.gradle.kts')
    if not os.path.exists(path):
        return []
    with open(path, encoding='utf8') as fh:
        text = fh.read()
    out = []
    for var, suffixes in (('shadowPackages', ('/',)), ('shadowClasses', ('', '$'))):
        m = re.search(r'val\s+%s\s*=\s*listOf\((.*?)\)' % var, text, re.S)
        if m:
            for name in re.findall(r'"([\w.$]+)"', m.group(1)):
                for s in suffixes:
                    out.append((name.replace('.', '/'), s))
    return out


def _shadowed(owner, shadow):
    for base, suffix in shadow:
        if suffix == '' and owner == base:
            return True
        if suffix and owner.startswith(base + suffix):
            return True
    return False


class JdkGapClassifier(object):
    """Disposition of one java.*/javax.* reference. floor and device are Surface-like
    (Surface over an android.jar or ApiLevelSurface); api, optional, supplies the API level
    a member arrived at."""

    def __init__(self, floor, device, desugar, api=None, shadow=()):
        self.floor = Resolver(floor, {})
        self.device = Resolver(device, {})
        self.desugar = desugar
        self.api = api
        self.shadow = list(shadow)
        self._cache = {}

    def ancestors(self, owner):
        out, stack, seen = [], [owner], set()
        while stack:
            n = stack.pop(0)
            if n in seen:
                continue
            seen.add(n)
            out.append(n)
            c = self.device.surface.get(n) or self.floor.surface.get(n)
            if c is not None:
                stack.extend(c.parents)
        return out

    def android_since(self, kind, owner, name, desc):
        if self.api is None:
            return None
        if kind == 'class':
            return self.api.class_since(owner)
        return self.api.member_since(owner, name, desc, kind == 'field')

    def classify(self, kind, owner, name, desc):
        """None when the floor API has it (or it is not a JDK gap this mode reports),
        else {'disposition', 'via', 'androidSince'}."""
        key = (kind, owner, name, desc)
        if key in self._cache:
            return self._cache[key]
        result = self._classify(kind, owner, name, desc)
        self._cache[key] = result
        return result

    def _present(self, resolver, kind, owner, name, desc):
        if kind == 'class':
            return resolver.has_class(owner)
        return resolver.has_member(owner, name, desc, kind == 'field')

    def _classify(self, kind, owner, name, desc):
        if not is_jdk_name(owner) or self._present(self.floor, kind, owner, name, desc):
            return None
        shim = _table_lookup(SHIMS, owner, name)
        if shim is None and _shadowed(owner, self.shadow):
            return None
        since = self.android_since(kind, owner, name, desc)

        def done(disposition, via):
            return {'disposition': disposition, 'via': via, 'androidSince': since}

        via = self.desugar.covers(kind, owner, name, desc, self.ancestors(owner)) if self.desugar else None
        via = via or d8_backport(kind, owner, name, desc)
        if via:
            return done('desugar', via)
        if shim:
            return done('retarget', shim)
        if self._present(self.device, kind, owner, name, desc):
            return done('already-on-device-API', 'android API %s' % (since if since is not None else '<= %d' % self.device_level()))
        why = _table_lookup(UNSUPPORTED, owner, name)
        if why:
            return done('unsupported', why)
        return done('retarget', '')

    def device_level(self):
        return getattr(self.device.surface, 'level', DEFAULT_DEVICE_API)


def jdk_references(classes):
    """{(kind, owner, name, desc): referencing-class-count} over java.*/javax.* owners.
    Array owners resolve on Object, which API 1 has, so they are dropped."""
    out = {}
    for c in classes:
        for ref in c.class_refs:
            if is_jdk_name(ref):
                key = ('class', ref, '', '')
                out[key] = out.get(key, 0) + 1
        for tag, owner, name, desc in c.member_refs:
            if is_jdk_name(owner):
                key = ('field' if tag == 9 else 'method', owner, name, desc)
                out[key] = out.get(key, 0) + 1
    return out


def collect_jdk_refs(jar_path):
    """Per-jar worker of --jdk-gaps: the jar's JDK references, JSON-able."""
    try:
        classes = read_plugin_jar(jar_path)
        refs = jdk_references(classes)
        return {'classes': len(classes), 'refs': [[k[0], k[1], k[2], k[3], n] for k, n in refs.items()]}
    except Exception as e:
        return {'classes': 0, 'refs': [], 'error': '%s: %s' % (type(e).__name__, e)}


def build_jdk_gaps(results, classifier, counts, meta, top_plugins=None):
    """results: {source name: collect_jdk_refs result}. Returns the baseline structure:
    every reference the floor API lacks, with its disposition and the sources using it."""
    entries = {}
    plugins = []
    for source, result in results.items():
        installs = counts.get(source, 0)
        per = dict((d, 0) for d in DISPOSITIONS)
        for kind, owner, name, desc, n in result['refs']:
            got = classifier.classify(kind, owner, name, desc)
            if got is None:
                continue
            per[got['disposition']] += 1
            key = sig_key(kind, owner, name, desc)
            e = entries.get(key)
            if e is None:
                e = entries[key] = {
                    'signature': key,
                    'kind': kind,
                    'owner': owner,
                    'name': name,
                    'descriptor': desc,
                    'disposition': got['disposition'],
                    'via': got['via'],
                    'androidSince': got['androidSince'],
                    'pluginCount': 0,
                    'referenceCount': 0,
                    'installs': 0,
                    'plugins': [],
                }
            e['plugins'].append(source)
            e['pluginCount'] += 1
            e['referenceCount'] += n
            e['installs'] += installs
        plugins.append({
            'name': source,
            'installs': installs,
            'classes': result.get('classes', 0),
            'gaps': sum(per.values()),
            'dispositions': per,
            'error': result.get('error'),
        })
    for e in entries.values():
        e['plugins'].sort(key=lambda p: (-counts.get(p, 0), p))
        if top_plugins:
            e['plugins'] = e['plugins'][:top_plugins]
    order = dict((d, i) for i, d in enumerate(DISPOSITIONS))
    gaps = sorted(entries.values(), key=lambda e: (order[e['disposition']], -e['pluginCount'], -e['installs'],
                                                     e['owner'], e['name'], e['descriptor']))
    plugins.sort(key=lambda p: (not p['name'].startswith('('), -p['installs'], p['name']))
    summary = {}
    for d in DISPOSITIONS:
        hits = [e for e in gaps if e['disposition'] == d]
        summary[d] = {
            'signatures': len(hits),
            'plugins': len(set(p for e in hits for p in e['plugins'])),
            'references': sum(e['referenceCount'] for e in hits),
        }
    out = {
        '_comment': ('JDK 9-21 java.*/javax.* references the floor API lacks, from '
                     'tools-src/hub-compat-scan.py --jdk-gaps. disposition: desugar = D8 backport or '
                     'desugar_jdk_libs supplies it at minSdk; retarget = an app shim already rewrites it '
                     '(via names it) or the dexer needs a retargeting entry (via empty); '
                     'already-on-device-API = the device API has it, older devices do not; '
                     'unsupported = nothing on ART can stand in for it.'),
    }
    out.update(meta)
    out.update({
        'sourcesScanned': len(plugins),
        'dispositions': summary,
        'gaps': gaps,
        'plugins': plugins,
    })
    return out


def render_jdk_gaps_markdown(report, top_plugins=5, max_rows=400):
    out = []
    w = out.append
    w('# JDK 9-21 API gaps')
    w('')
    w('Generated %s; minSdk %d, device API %d; %d sources scanned (injected client, runelite-client, '
      'hub manifest %s).' % (report['generatedAt'], report['minApi'], report['deviceApi'],
                             report['sourcesScanned'], report['hubVersion']))
    w('')
    w('Resolved against:')
    w('')
    for label, value in sorted(report['resolvedAgainst'].items()):
        w('- %s: `%s`' % (label, value))
    w('')
    w('Every java.*/javax.* class, method and field the floor API lacks is listed with its '
      'disposition. Owners under android-awt\'s shadow packages are left to the main scan. '
      '`androidSince` is the API level the platform added the member at, if ever.')
    w('')
    w('| disposition | signatures | sources | references |')
    w('|---|---:|---:|---:|')
    for d in DISPOSITIONS:
        s = report['dispositions'][d]
        w('| %s | %d | %d | %d |' % (d, s['signatures'], s['plugins'], s['references']))
    w('')
    for d in DISPOSITIONS:
        rows = [e for e in report['gaps'] if e['disposition'] == d]
        if not rows:
            continue
        w('## %s' % d)
        w('')
        w('| member | kind | androidSince | via | sources | refs | installs | top sources |')
        w('|---|---|---:|---|---:|---:|---:|---|')
        for e in rows[:max_rows]:
            member = e['owner'] if e['kind'] == 'class' else '%s.%s%s' % (e['owner'], e['name'], e['descriptor'])
            w('| `%s` | %s | %s | %s | %d | %d | %d | %s |' % (
                _md_escape(member), e['kind'], '' if e['androidSince'] is None else e['androidSince'],
                _md_escape(e['via']), e['pluginCount'], e['referenceCount'], e['installs'],
                ', '.join('`%s`' % p for p in e['plugins'][:top_plugins])))
        if len(rows) > max_rows:
            w('| … %d more | | | | | | | |' % (len(rows) - max_rows))
        w('')
    w('## Sources')
    w('')
    w('Client jars first, then hub plugins by install count. Columns count distinct gap signatures.')
    w('')
    w('| source | installs | classes | gaps | ' + ' | '.join(DISPOSITIONS) + ' |')
    w('|---|---:|---:|---:|' + '---:|' * len(DISPOSITIONS))
    for p in report['plugins']:
        name = p['name'] + (' (scan failed)' if p.get('error') else '')
        w('| %s | %d | %d | %d | %s |' % (name, p['installs'], p['classes'], p['gaps'],
                                         ' | '.join(str(p['dispositions'][d]) for d in DISPOSITIONS)))
    w('')
    return '\n'.join(out)


def main_checkout_root():
    """The primary checkout when ROOT is a git worktree (build outputs and data/ live
    there, not in the worktree), else None."""
    git = os.path.join(ROOT, '.git')
    if not os.path.isfile(git):
        return None
    with open(git, encoding='utf8') as fh:
        line = fh.read().strip()
    if not line.startswith('gitdir:'):
        return None
    gitdir = os.path.normpath(os.path.join(ROOT, line[len('gitdir:'):].strip()))
    if os.path.basename(os.path.dirname(gitdir)) != 'worktrees':
        return None
    main = os.path.dirname(os.path.dirname(os.path.dirname(gitdir)))
    return main if os.path.isdir(main) else None


def search_roots():
    roots = [ROOT]
    main = main_checkout_root()
    if main and os.path.normcase(main) != os.path.normcase(ROOT):
        roots.append(main)
    return roots


def _gradle_value(pattern, default=None):
    with open(os.path.join(ROOT, 'android', 'build.gradle.kts'), encoding='utf8') as fh:
        m = re.search(pattern, fh.read())
    return m.group(1) if m else default


def find_injected_jar():
    for root in search_roots():
        hits = glob.glob(os.path.join(root, 'data', 'runelite-*-injected-*-clean.jar'))
        if hits:
            return max(hits, key=os.path.getmtime)
    return None


def find_client_jar():
    for root in search_roots():
        p = find_module_jar('runelite-client', 'client', root)
        if p:
            return p
    return None


def find_platform_jar(sdk_dir, level):
    if not sdk_dir:
        return None
    for d in ('android-%d' % level, 'android-%d.0' % level):
        p = os.path.join(sdk_dir, 'platforms', d, 'android.jar')
        if os.path.exists(p):
            return p
    return None


def find_api_versions(sdk_dir):
    if not sdk_dir:
        return None
    best = None
    for p in glob.glob(os.path.join(sdk_dir, 'platforms', 'android-*', 'data', 'api-versions.xml')):
        m = re.search(r'android-(\d+)(?:\.(\d+))?', p)
        key = (int(m.group(1)), int(m.group(2) or 0)) if m else (0, 0)
        if best is None or key > best[0]:
            best = (key, p)
    return best[1] if best else None


def load_desugar_config(path=None):
    """(parsed desugar.json, where it came from). The runtime-desugar asset is the exact
    file the on-device D8 is configured with; the gradle cache copy of the same version
    stands in when the app has not been built."""
    candidates = [path] if path else [
        os.path.join(r, 'android', 'build', 'generated', 'runtime-desugar-assets', 'runtime-dex', 'desugar.json')
        for r in search_roots()]
    for c in candidates:
        if c and os.path.exists(c):
            with open(c, encoding='utf8') as fh:
                return json.load(fh), c
    if path:
        raise SystemExit('missing desugar configuration: %s' % path)
    version = _gradle_value(r'val\s+coreLibraryVersion\s*=\s*"([^"]+)"')
    jar = find_gradle_lib('com.android.tools', 'desugar_jdk_libs_configuration', version) if version else None
    if not jar:
        raise SystemExit('no desugar_jdk_libs configuration: build :android:prepareRuntimeDesugarAssets or pass --desugar-json')
    with zipfile.ZipFile(jar) as z:
        return json.loads(z.read('META-INF/desugar/d8/desugar.json').decode('utf8')), jar + '!META-INF/desugar/d8/desugar.json'


def script_fingerprint():
    h = hashlib.sha1()
    with open(os.path.abspath(__file__), 'rb') as fh:
        h.update(fh.read())
    return 'jdk' + h.hexdigest()[:10]


def jdk_gaps_main(args):
    sdk_dir = args.sdk_dir or read_sdk_dir()
    min_api = args.min_api or int(_gradle_value(r'minSdk\s*=\s*(\d+)', DEFAULT_MIN_API))
    device_api = args.device_api
    api_path = args.api_versions or find_api_versions(sdk_dir)
    api = ApiVersions(api_path) if api_path and os.path.exists(api_path) else None

    def level_surface(override, level):
        """(surface, label for the log, label for the committed baseline)."""
        jar = override or find_platform_jar(sdk_dir, level)
        if jar:
            return (Surface([jar]), os.path.abspath(jar),
                    '%s/android.jar' % os.path.basename(os.path.dirname(os.path.abspath(jar))))
        if api is None:
            raise SystemExit('no android.jar for API %d and no api-versions.xml (pass --floor-jar/--device-jar or --api-versions)' % level)
        short = '%s/data/api-versions.xml' % os.path.basename(os.path.dirname(os.path.dirname(os.path.abspath(api_path))))
        return ApiLevelSurface(api, level), '%s at API %d' % (api_path, level), '%s at API %d' % (short, level)

    floor, floor_label, floor_short = level_surface(args.floor_jar, min_api)
    device, device_label, device_short = level_surface(args.device_jar, device_api)
    data, desugar_label = load_desugar_config(args.desugar_json)
    desugar = DesugarConfig(data, min_api)
    classifier = JdkGapClassifier(floor, device, desugar, api, read_shadow_owners())
    for label, value in (('floor', floor_label), ('device', device_label), ('desugar', desugar_label)):
        sys.stderr.write('%s: %s\n' % (label, value))

    results = {}
    for label, path in (('(injected client)', args.injected_jar or find_injected_jar()),
                        ('(runelite-client)', args.client_jar or find_client_jar())):
        if path and os.path.exists(path):
            sys.stderr.write('client: %s\n' % path)
            results[label] = collect_jdk_refs(path)
        else:
            sys.stderr.write('warning: %s jar not found, skipping (pass --injected-jar/--client-jar)\n' % label)

    version, jars, display, counts, work = fetch_hub(args)
    hub = scan_all(args.jobs, [], work, os.path.join(args.cache_dir, 'scan'), script_fingerprint(), fn=collect_jdk_refs)
    for name, result in hub.items():
        results[name] = result

    meta = {
        'generatedAt': datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
        'hubVersion': version,
        'minApi': min_api,
        'deviceApi': device_api,
        'resolvedAgainst': {
            'floor': floor_short,
            'device': device_short,
            'desugar': desugar.identifier,
            'shims': 'DesktopRegexTransformer, DesugarApisTask (IndyConcat), ProcessHandleShim; StringShim covers no JDK member',
        },
    }
    report = build_jdk_gaps(results, classifier, counts, meta)
    for s in (floor, device):
        s.close()

    os.makedirs(args.out_dir, exist_ok=True)
    md_path = os.path.join(args.out_dir, 'jdk-gaps.md')
    with open(md_path, 'w', encoding='utf8') as fh:
        fh.write(render_jdk_gaps_markdown(report))
    os.makedirs(os.path.dirname(os.path.abspath(args.baseline)), exist_ok=True)
    with open(args.baseline, 'w', encoding='utf8', newline='\n') as fh:
        json.dump(report, fh, indent=1, sort_keys=False)
        fh.write('\n')
    print('%d sources, %d JDK gap signatures -> %s, %s' % (report['sourcesScanned'], len(report['gaps']), md_path, args.baseline))
    for d in DISPOSITIONS:
        s = report['dispositions'][d]
        print('  %-24s %6d signatures  %5d sources' % (d, s['signatures'], s['plugins']))
    return 0


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
    jdk = ap.add_argument_group('--jdk-gaps')
    jdk.add_argument('--jdk-gaps', action='store_true', help='audit JDK 9-21 java.*/javax.* members instead of the host surface')
    jdk.add_argument('--min-api', type=int, help='floor API level (default: minSdk of android/build.gradle.kts)')
    jdk.add_argument('--device-api', type=int, default=DEFAULT_DEVICE_API, help='device API level (default %(default)s)')
    jdk.add_argument('--api-versions', help='api-versions.xml (default: newest sdk platform that ships one)')
    jdk.add_argument('--floor-jar', help='android.jar of the floor API (default: platforms/android-<min-api>, else api-versions.xml)')
    jdk.add_argument('--device-jar', help='android.jar of the device API (default: platforms/android-<device-api>, else api-versions.xml)')
    jdk.add_argument('--desugar-json', help='desugar_jdk_libs configuration (default: runtime-desugar assets, else the gradle cache)')
    jdk.add_argument('--injected-jar', help='injected client jar (default: newest data/runelite-*-injected-*-clean.jar)')
    jdk.add_argument('--baseline', default=os.path.join(ROOT, 'docs', 'jdk-api-gaps.json'), help='baseline JSON to write')
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


def fetch_hub(args):
    """Manifest, install counts and the selected jars (downloaded unless --offline).
    Returns (version, jars, display, counts, work) with work = [(jar dict, path)]."""
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
    return version, jars, display, counts, work


def main(argv=None):
    args = build_parser().parse_args(argv)
    if args.jdk_gaps:
        return jdk_gaps_main(args)
    sources = resolve_sources(args)
    for s in sources:
        sys.stderr.write('host: %s\n' % s)
    version, jars, display, counts, work = fetch_hub(args)

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
