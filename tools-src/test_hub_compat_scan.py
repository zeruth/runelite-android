"""Unit tests for hub-compat-scan.py over synthetic class files.

Run: python -m unittest discover -s tools-src -p test_hub_compat_scan.py
"""
import importlib.util
import io
import json
import os
import struct
import tempfile
import unittest
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location('hub_compat_scan', os.path.join(HERE, 'hub-compat-scan.py'))
scan = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(scan)


class ClassWriter(object):
    """Minimal class-file assembler: enough constant pool and member tables to exercise
    the parser and resolver without a JDK on the test machine."""

    def __init__(self, name, superclass='java/lang/Object', interfaces=()):
        self.pool = []
        self.index = {}
        self.name = name
        self.superclass = superclass
        self.interfaces = list(interfaces)
        self.fields = []
        self.methods = []
        self.extra = []  # constant pool entries only referenced from code

    def _const(self, key, payload):
        if key not in self.index:
            self.pool.append(payload)
            self.index[key] = len(self.pool)
        return self.index[key]

    def utf8(self, s):
        b = s.encode('utf8')
        return self._const(('utf8', s), struct.pack('>BH', 1, len(b)) + b)

    def klass(self, name):
        return self._const(('class', name), struct.pack('>BH', 7, self.utf8(name)))

    def name_and_type(self, name, desc):
        return self._const(('nt', name, desc), struct.pack('>BHH', 12, self.utf8(name), self.utf8(desc)))

    def member_ref(self, tag, owner, name, desc):
        return self._const((tag, owner, name, desc),
                           struct.pack('>BHH', tag, self.klass(owner), self.name_and_type(name, desc)))

    def field_ref(self, owner, name, desc):
        return self.member_ref(9, owner, name, desc)

    def method_ref(self, owner, name, desc):
        return self.member_ref(10, owner, name, desc)

    def imethod_ref(self, owner, name, desc):
        return self.member_ref(11, owner, name, desc)

    def method_type(self, desc):
        return self._const(('mt', desc), struct.pack('>BH', 16, self.utf8(desc)))

    def long_const(self, v):
        # Two-slot constant: the parser must step over the phantom slot.
        key = ('long', v)
        if key not in self.index:
            self.pool.append(struct.pack('>Bq', 5, v))
            self.index[key] = len(self.pool)
            self.pool.append(None)
        return self.index[key]

    def field(self, name, desc):
        self.fields.append((self.utf8(name), self.utf8(desc)))

    def method(self, name, desc, code_len=0):
        # A Code attribute of arbitrary bytes checks attribute skipping.
        attrs = []
        if code_len:
            attrs.append((self.utf8('Code'), b'\x00' * code_len))
        self.methods.append((self.utf8(name), self.utf8(desc), attrs))

    def build(self):
        this = self.klass(self.name)
        sup = self.klass(self.superclass) if self.superclass else 0
        ifaces = [self.klass(i) for i in self.interfaces]
        out = io.BytesIO()
        out.write(struct.pack('>IHH', 0xCAFEBABE, 0, 52))
        out.write(struct.pack('>H', len(self.pool) + 1))
        for p in self.pool:
            if p is not None:
                out.write(p)
        out.write(struct.pack('>HHH', 0x21, this, sup))
        out.write(struct.pack('>H', len(ifaces)))
        for i in ifaces:
            out.write(struct.pack('>H', i))
        out.write(struct.pack('>H', len(self.fields)))
        for n, d in self.fields:
            out.write(struct.pack('>HHHH', 1, n, d, 0))
        out.write(struct.pack('>H', len(self.methods)))
        for n, d, attrs in self.methods:
            out.write(struct.pack('>HHHH', 1, n, d, len(attrs)))
            for an, body in attrs:
                out.write(struct.pack('>HI', an, len(body)) + body)
        out.write(struct.pack('>H', 0))
        return out.getvalue()


def jar_bytes(classes):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as z:
        z.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\n')
        for data in classes:
            z.writestr(scan.parse_class(data, refs=False).name + '.class', data)
    return buf.getvalue()


def host_classes():
    obj = ClassWriter('java/lang/Object', superclass=None)
    obj.method('<init>', '()V')
    obj.method('toString', '()Ljava/lang/String;')
    obj.method('clone', '()Ljava/lang/Object;')
    comp = ClassWriter('java/awt/Component')
    comp.method('<init>', '()V')
    comp.method('repaint', '()V')
    comp.field('width', 'I')
    cont = ClassWriter('java/awt/Container', superclass='java/awt/Component')
    cont.method('<init>', '()V')
    cont.method('add', '(Ljava/awt/Component;)Ljava/awt/Component;')
    iface = ClassWriter('javax/swing/Scrollable', superclass='java/lang/Object')
    iface.method('getScrollableTracksViewportWidth', '()Z')
    panel = ClassWriter('javax/swing/JPanel', superclass='java/awt/Container', interfaces=['javax/swing/Scrollable'])
    panel.method('<init>', '()V')
    mh = ClassWriter('java/lang/invoke/MethodHandle')
    mh.method('invokeExact', '([Ljava/lang/Object;)Ljava/lang/Object;')
    buf = ClassWriter('java/nio/Buffer')
    buf.method('flip', '()Ljava/nio/Buffer;')
    bbuf = ClassWriter('java/nio/ByteBuffer', superclass='java/nio/Buffer')
    bbuf.method('get', '()B')
    return [obj.build(), comp.build(), cont.build(), iface.build(), panel.build(), mh.build(),
            buf.build(), bbuf.build()]


class SurfaceFixture(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.host_dir = os.path.join(self.tmp.name, 'host')
        for data in host_classes():
            name = scan.parse_class(data, refs=False).name
            path = os.path.join(self.host_dir, name + '.class')
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'wb') as fh:
                fh.write(data)
        self.surface = scan.Surface([self.host_dir])

    def tearDown(self):
        self.surface.close()
        self.tmp.cleanup()


class ParseClassTest(unittest.TestCase):
    def test_parses_members_parents_and_references(self):
        w = ClassWriter('com/example/Foo', superclass='javax/swing/JPanel', interfaces=['java/lang/Runnable'])
        w.long_const(7)
        w.field('count', 'I')
        w.field('icon', 'Ljavax/swing/ImageIcon;')
        w.method('<init>', '()V', code_len=9)
        w.method('run', '()V', code_len=3)
        w.method_ref('java/awt/Component', 'repaint', '()V')
        w.field_ref('java/awt/Component', 'width', 'I')
        w.imethod_ref('javax/swing/Scrollable', 'getScrollableTracksViewportWidth', '()Z')
        w.klass('[Ljava/awt/Color;')
        w.klass('[I')
        w.method_type('(Ljava/awt/Font;)V')
        w.name_and_type('paint', '(Ljava/awt/Graphics;)V')
        c = scan.parse_class(w.build())
        self.assertEqual(c.name, 'com/example/Foo')
        self.assertEqual(c.parents, ['javax/swing/JPanel', 'java/lang/Runnable'])
        self.assertEqual(c.fields, {('count', 'I'), ('icon', 'Ljavax/swing/ImageIcon;')})
        self.assertEqual(c.methods, {('<init>', '()V'), ('run', '()V')})
        self.assertIn((10, 'java/awt/Component', 'repaint', '()V'), c.member_refs)
        self.assertIn((9, 'java/awt/Component', 'width', 'I'), c.member_refs)
        self.assertIn((11, 'javax/swing/Scrollable', 'getScrollableTracksViewportWidth', '()Z'), c.member_refs)
        for expected in ('java/awt/Color', 'javax/swing/ImageIcon', 'java/awt/Font', 'java/awt/Graphics',
                         'javax/swing/JPanel', 'java/lang/Runnable'):
            self.assertIn(expected, c.class_refs)
        self.assertNotIn('[I', c.class_refs)
        self.assertNotIn('com/example/Foo', c.class_refs)

    def test_refs_false_skips_reference_collection(self):
        w = ClassWriter('com/example/Bare')
        w.method_ref('java/awt/Component', 'repaint', '()V')
        c = scan.parse_class(w.build(), refs=False)
        self.assertEqual(c.member_refs, set())


class ResolutionTest(SurfaceFixture):
    def test_inherited_members_resolve_through_classes_and_interfaces(self):
        self.assertTrue(self.surface.has_member('javax/swing/JPanel', 'repaint', '()V', False))
        self.assertTrue(self.surface.has_member('javax/swing/JPanel', 'width', 'I', True))
        self.assertTrue(self.surface.has_member('javax/swing/JPanel', 'getScrollableTracksViewportWidth', '()Z', False))
        self.assertTrue(self.surface.has_member('javax/swing/JPanel', 'toString', '()Ljava/lang/String;', False))

    def test_constructors_and_wrong_descriptors_do_not_resolve(self):
        self.assertFalse(self.surface.has_member('javax/swing/JPanel', '<init>', '(Z)V', False))
        self.assertFalse(self.surface.has_member('java/awt/Container', 'add', '(Ljava/lang/String;)V', False))
        self.assertFalse(self.surface.has_member('java/awt/Window', 'pack', '()V', False))

    def test_arrays_and_polymorphic_signatures(self):
        resolver = scan.Resolver(self.surface, {})
        self.assertTrue(resolver.has_member('[I', 'clone', '()Ljava/lang/Object;', False))
        self.assertTrue(resolver.has_member('[Ljava/awt/Color;', 'toString', '()Ljava/lang/String;', False))
        self.assertTrue(resolver.has_member('java/lang/invoke/MethodHandle', 'invokeExact', '(II)Ljava/awt/Color;', False))
        self.assertFalse(resolver.has_member('java/lang/invoke/MethodHandle', 'bindTo', '(Ljava/lang/Object;)V', False))

    def test_unresolved_references_report_plugin_gaps_only(self):
        base = ClassWriter('com/example/Base', superclass='javax/swing/JPanel')
        base.method('<init>', '()V')
        base.method('helper', '()V')
        plugin = ClassWriter('com/example/Plugin', superclass='com/example/Base')
        plugin.method('<init>', '()V')
        plugin.method_ref('com/example/Base', 'helper', '()V')          # own class: resolves
        plugin.method_ref('com/example/Plugin', 'repaint', '()V')       # inherited via host: resolves
        plugin.method_ref('java/awt/Component', 'setBounds', '(IIII)V')  # host class, missing member
        plugin.field_ref('java/awt/Window', 'x', 'I')                   # missing class
        plugin.method_ref('javax/swing/JPanel', '<init>', '(Ljava/awt/LayoutManager;)V')  # missing ctor
        plugin.klass('javax/swing/JOptionPane')                          # missing class
        plugin.field('model', 'Ljavax/swing/table/TableModel;')          # missing class in own descriptor
        classes = [scan.parse_class(base.build()), scan.parse_class(plugin.build())]
        got = scan.unresolved_references(self.surface, classes)
        self.assertEqual(got, {
            ('method', 'java/awt/Component', 'setBounds', '(IIII)V'): 1,
            ('field!', 'java/awt/Window', 'x', 'I'): 1,
            ('method', 'javax/swing/JPanel', '<init>', '(Ljava/awt/LayoutManager;)V'): 1,
            ('class', 'java/awt/LayoutManager', '', ''): 1,
            ('class', 'javax/swing/JOptionPane', '', ''): 1,
            ('class', 'javax/swing/table/TableModel', '', ''): 1,
            ('class', 'java/awt/Window', '', ''): 1,
        })

    def test_desugared_bootstraps_and_nio_covariant_returns_resolve(self):
        resolver = scan.Resolver(self.surface, {})
        self.assertTrue(resolver.has_class('java/lang/invoke/LambdaMetafactory'))
        self.assertTrue(resolver.has_member('java/lang/invoke/StringConcatFactory', 'makeConcatWithConstants', '()V', False))
        self.assertTrue(resolver.has_member('java/nio/ByteBuffer', 'flip', '()Ljava/nio/ByteBuffer;', False))
        self.assertTrue(resolver.has_member('java/nio/ByteBuffer', 'flip', '()Ljava/nio/Buffer;', False))
        self.assertFalse(resolver.has_member('java/nio/ByteBuffer', 'slice', '()Ljava/nio/ByteBuffer;', False))
        self.assertFalse(resolver.has_member('java/nio/ByteBuffer', 'flip', '(I)Ljava/nio/ByteBuffer;', False))

    def test_member_on_plugin_subclass_is_attributed_to_missing_ancestor(self):
        ui = ClassWriter('com/example/MyComboBoxUI', superclass='javax/swing/plaf/basic/BasicComboBoxUI')
        ui.method('<init>', '()V')
        user = ClassWriter('com/example/Panel')
        user.method_ref('com/example/MyComboBoxUI', 'installUI', '(Ljavax/swing/JComponent;)V')
        user.method_ref('com/example/MyComboBoxUI', '<init>', '()V')
        classes = [scan.parse_class(ui.build()), scan.parse_class(user.build())]
        got = scan.unresolved_references(self.surface, classes)
        self.assertEqual(got, {
            ('method!', 'javax/swing/plaf/basic/BasicComboBoxUI', 'installUI', '(Ljavax/swing/JComponent;)V'): 1,
            ('class', 'javax/swing/plaf/basic/BasicComboBoxUI', '', ''): 1,
            ('class', 'javax/swing/JComponent', '', ''): 1,
        })

    def test_host_copy_shadows_plugin_shaded_library(self):
        # The plugin bundles its own java/awt/Container with an extra method; the host's
        # copy wins (parent-first class loading), so the extra method is a gap.
        shaded = ClassWriter('java/awt/Container', superclass='java/awt/Component')
        shaded.method('removeAll', '()V')
        plugin = ClassWriter('com/example/P')
        plugin.method_ref('java/awt/Container', 'removeAll', '()V')
        classes = [scan.parse_class(shaded.build()), scan.parse_class(plugin.build())]
        got = scan.unresolved_references(self.surface, classes)
        self.assertEqual(set(got), {('method', 'java/awt/Container', 'removeAll', '()V')})

    def test_shaded_copy_of_host_class_is_not_scanned(self):
        # The host's java/awt/Container loads instead, so what the shaded copy calls never links.
        shaded = ClassWriter('java/awt/Container', superclass='java/awt/Component')
        shaded.method_ref('java/awt/Component', 'invalidateTree', '()V')
        shaded.klass('java/awt/peer/ContainerPeer')
        got = scan.unresolved_references(self.surface, [scan.parse_class(shaded.build())])
        self.assertEqual(got, {})

    def test_synthetic_accessors_of_shaded_copy_are_intra_jar(self):
        # A nested class only the plugin's shaded library has calls accessors that its own
        # copy of the host-provided outer class defines.
        outer = ClassWriter('java/awt/Container', superclass='java/awt/Component')
        outer.method('access$000', '(Ljava/awt/Container;)I')
        outer.method('<init>', '(Ljava/awt/Container$1;)V')
        outer.method('validateUnconditionally', '()V')
        inner = ClassWriter('java/awt/Container$Helper')
        inner.method_ref('java/awt/Container', 'access$000', '(Ljava/awt/Container;)I')
        inner.method_ref('java/awt/Container', '<init>', '(Ljava/awt/Container$1;)V')
        inner.method_ref('java/awt/Container', 'validateUnconditionally', '()V')
        inner.method_ref('java/awt/Container', 'access$100', '()V')  # not in the shaded copy either
        tag = ClassWriter('java/awt/Container$1')
        other = ClassWriter('com/example/User')
        other.method_ref('java/awt/Container', '<init>', '(Ljava/awt/Container$1;)V')  # not a nestmate
        classes = [scan.parse_class(w.build()) for w in (outer, inner, tag, other)]
        got = scan.unresolved_references(self.surface, classes)
        self.assertEqual(got, {
            ('method~', 'java/awt/Container', 'access$000', '(Ljava/awt/Container;)I'): 1,
            ('method~', 'java/awt/Container', '<init>', '(Ljava/awt/Container$1;)V'): 1,
            ('method', 'java/awt/Container', 'validateUnconditionally', '()V'): 1,
            ('method', 'java/awt/Container', 'access$100', '()V'): 1,
            ('method', 'java/awt/Container', '<init>', '(Ljava/awt/Container$1;)V'): 1,
        })
        report = scan.build_report({'p': {'classes': 3, 'unresolved': [list(k) + [n] for k, n in got.items()]}},
                                   [], [], {}, {})
        intra = [e for e in report['unresolved'] if e['bucket'] == 'intra-jar']
        self.assertEqual(sorted(e['name'] for e in intra), ['<init>', 'access$000'])
        self.assertTrue(all(e['kind'] == 'method' for e in intra))
        self.assertEqual(report['buckets']['intra-jar'], {'signatures': 2, 'plugins': 1})

    def test_jar_source_and_plugin_jar_reader(self):
        data = jar_bytes(host_classes())
        path = os.path.join(self.tmp.name, 'host.jar')
        with open(path, 'wb') as fh:
            fh.write(data)
        s = scan.Surface([path])
        try:
            self.assertIn('javax/swing/JPanel', s)
            self.assertTrue(s.has_member('javax/swing/JPanel', 'repaint', '()V', False))
        finally:
            s.close()
        classes = scan.read_plugin_jar(data)
        self.assertEqual(sorted(c.name for c in classes)[0], 'java/awt/Component')


class ClassifyTest(unittest.TestCase):
    def test_buckets(self):
        cases = {
            'java/awt/Component': 'layout/input/painting',
            'java/awt/event/MouseAdapter': 'layout/input/painting',
            'javax/swing/SwingUtilities': 'layout/input/painting',
            'java/awt/Graphics2D': 'drawing/geometry/fonts/images',
            'java/awt/geom/Area': 'drawing/geometry/fonts/images',
            'java/awt/image/BufferedImage': 'drawing/geometry/fonts/images',
            'javax/imageio/ImageIO': 'drawing/geometry/fonts/images',
            '[Ljava/awt/Color;': 'drawing/geometry/fonts/images',
            'java/awt/Desktop': 'dialogs/windows/desktop',
            'javax/swing/JOptionPane': 'dialogs/windows/desktop',
            'javax/swing/JFileChooser': 'dialogs/windows/desktop',
            'javax/swing/JTable': 'widgets/menus/tables',
            'javax/swing/table/DefaultTableModel': 'widgets/menus/tables',
            'javax/swing/JMenuItem': 'widgets/menus/tables',
            'javax/swing/plaf/basic/BasicComboBoxUI': 'widgets/menus/tables',
            'com/formdev/flatlaf/FlatClientProperties': 'widgets/menus/tables',
            'javax/swing/text/html/HTMLEditorKit': 'text/HTML',
            'javax/swing/JTextField': 'text/HTML',
            'javax/swing/event/DocumentListener': 'text/HTML',
            'java/awt/datatransfer/StringSelection': 'dnd/datatransfer',
            'java/awt/dnd/DropTarget': 'dnd/datatransfer',
            'javax/swing/TransferHandler': 'dnd/datatransfer',
            'javax/swing/ToolTipManager': 'tooltips/accessibility',
            'javax/accessibility/AccessibleContext': 'tooltips/accessibility',
            'javax/sound/sampled/AudioSystem': 'audio',
            'javax/sound/midi/MidiSystem': 'audio',
            'org/lwjgl/opengl/GL43C': 'lwjgl/native',
            'com/sun/jna/Native': 'lwjgl/native',
            'com/google/common/collect/ImmutableList': 'guava',
            'org/apache/commons/lang3/StringUtils': 'other libraries',
            'okhttp3/OkHttpClient': 'other libraries',
            'javax/annotation/Nullable': 'other libraries',
            'javax/inject/Inject': 'other libraries',
            'net/runelite/client/ui/PluginPanel': 'runelite',
            'java/beans/PropertyChangeSupport': 'other jdk',
            'java/lang/ProcessHandle': 'other jdk',
        }
        for owner, bucket in cases.items():
            self.assertEqual(scan.classify(owner), bucket, owner)


class HubFormatTest(unittest.TestCase):
    def test_signed_manifest_framing(self):
        body = json.dumps({'jars': [{'internalName': 'a', 'jarHash': 'h', 'jarSize': 1}], 'display': []}).encode()
        sig = b'\x01\x02\x03'
        blob = struct.pack('>I', len(sig)) + sig + body
        self.assertEqual(scan.parse_signed_manifest(blob)['jars'][0]['internalName'], 'a')

    def test_jar_hash_is_urlsafe_base64_sha256_without_padding(self):
        # Mirrors PluginHubManifest.HASH_ENCODER (Base64.getUrlEncoder().withoutPadding()).
        self.assertEqual(scan.jar_hash(b''), '47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU')
        self.assertEqual(scan.jar_file_name({'internalName': 'p', 'jarHash': 'H'}), 'p_H.jar')

    def test_ensure_jar_reuses_cached_copy_without_network(self):
        with tempfile.TemporaryDirectory() as tmp:
            data = b'cached-jar-bytes'
            jar = {'internalName': 'p', 'jarHash': scan.jar_hash(data), 'jarSize': len(data)}
            with open(os.path.join(tmp, scan.jar_file_name(jar)), 'wb') as fh:
                fh.write(data)
            path, fresh = scan.ensure_jar('http://invalid.invalid', jar, tmp)
            self.assertFalse(fresh)
            self.assertTrue(path.endswith(scan.jar_file_name(jar)))


class CatalogTest(unittest.TestCase):
    def test_catalog_versions_inline_and_by_ref(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, 'libs.versions.toml')
            with open(path, 'w', encoding='utf8') as fh:
                fh.write('[versions]\nslf4j = "1.7.25" # pinned\n\n[libraries]\n'
                         'guava = "com.google.guava:guava:23.2-jre"\n'
                         'slf4j-api = { module = "org.slf4j:slf4j-api", version.ref = "slf4j" }\n'
                         'bom = { module = "com.google.firebase:firebase-bom" }\n'
                         '[plugins]\nx = { id = "a.b", version = "1" }\n')
            self.assertEqual(scan.catalog_lib_versions(path), {
                ('com.google.guava', 'guava'): '23.2-jre',
                ('org.slf4j', 'slf4j-api'): '1.7.25',
            })


class ReportTest(unittest.TestCase):
    def test_build_report_and_markdown(self):
        results = {
            'big': {'classes': 10, 'unresolved': [
                ['method', 'java/awt/Component', 'setBounds', '(IIII)V', 3],
                ['class', 'javax/swing/JOptionPane', '', '', 1],
                ['method!', 'javax/swing/JOptionPane', 'showMessageDialog', '(Ljava/awt/Component;Ljava/lang/Object;)V', 1],
            ]},
            'small': {'classes': 2, 'unresolved': [
                ['method', 'java/awt/Component', 'setBounds', '(IIII)V', 1],
                ['method', 'com/google/common/collect/Lists', 'newArrayList', '()Ljava/util/ArrayList;', 1],
            ]},
            'broken': {'classes': 0, 'unresolved': [], 'error': 'BadZipFile: boom'},
        }
        jars = [{'internalName': n, 'displayName': n.title(), 'jarHash': 'h', 'jarSize': 1} for n in results]
        display = [{'internalName': 'big', 'displayName': 'Big Plugin', 'version': '1.2'}]
        counts = {'big': 1000, 'small': 10}
        meta = {'generatedAt': 'now', 'hubUrl': 'u', 'hubVersion': '1.0', 'hostSources': ['x.jar'], 'surfaceFingerprint': 'f'}
        report = scan.build_report(results, jars, display, counts, meta)
        self.assertEqual(report['pluginsScanned'], 3)
        self.assertEqual([p['internalName'] for p in report['plugins']], ['big', 'small', 'broken'])
        self.assertEqual(report['plugins'][0]['displayName'], 'Big Plugin')
        self.assertEqual(report['pluginsFailed'][0]['internalName'], 'broken')
        top = report['unresolved'][0]
        self.assertEqual((top['owner'], top['name']), ('java/awt/Component', 'setBounds'))
        self.assertEqual((top['pluginCount'], top['referenceCount'], top['installs']), (2, 4, 1010))
        self.assertEqual(top['plugins'], ['big', 'small'])
        self.assertFalse(top['ownerMissing'])
        dialog = [e for e in report['unresolved'] if e['name'] == 'showMessageDialog'][0]
        self.assertTrue(dialog['ownerMissing'])
        self.assertEqual(dialog['bucket'], 'dialogs/windows/desktop')
        self.assertEqual(report['buckets']['guava'], {'signatures': 1, 'plugins': 1})
        self.assertEqual(report['buckets']['layout/input/painting'], {'signatures': 1, 'plugins': 2})
        self.assertEqual(report['plugins'][0]['buckets'], {'layout/input/painting': 1, 'dialogs/windows/desktop': 2})
        json.dumps(report)

        md = scan.render_markdown(report)
        self.assertIn('## layout/input/painting', md)
        self.assertIn('### `javax/swing/JOptionPane` *(class missing)*', md)
        self.assertIn('| `setBounds(IIII)V` | method | 2 | 4 | 1010 | `big`, `small` |', md)
        self.assertIn('| big | 1000 | 10 | 3 |', md)
        self.assertIn('broken (scan failed)', md)
        self.assertNotIn('## audio', md)  # empty buckets get no section, only a table row


# A slice of platforms/android-N/data/api-versions.xml: members carry the API level the
# platform added them at, and inherit their class's when they carry none.
API_VERSIONS_XML = '''<?xml version="1.0" encoding="utf-8"?>
<api version="3">
	<class name="java/lang/Object" since="1">
		<method name="&lt;init>()V"/>
		<method name="toString()Ljava/lang/String;"/>
	</class>
	<class name="java/lang/String" since="1">
		<extends name="java/lang/Object"/>
		<method name="length()I"/>
		<method name="isEmpty()Z" since="9"/>
		<method name="repeat(I)Ljava/lang/String;" since="33"/>
		<method name="formatted([Ljava/lang/Object;)Ljava/lang/String;" since="34"/>
		<field name="CASE_INSENSITIVE_ORDER"/>
	</class>
	<class name="java/lang/Thread" since="1">
		<extends name="java/lang/Object"/>
		<method name="start()V"/>
	</class>
	<class name="java/util/Collection" since="1">
		<extends name="java/lang/Object"/>
		<method name="size()I"/>
		<method name="stream()Ljava/util/stream/Stream;" since="24"/>
	</class>
	<class name="java/util/List" since="1">
		<extends name="java/lang/Object"/>
		<implements name="java/util/Collection"/>
		<method name="of([Ljava/lang/Object;)Ljava/util/List;" since="30"/>
	</class>
	<class name="java/util/ArrayList" since="1">
		<extends name="java/lang/Object"/>
		<implements name="java/util/List"/>
		<method name="&lt;init>()V"/>
	</class>
	<class name="java/util/HexFormat" since="34">
		<extends name="java/lang/Object"/>
		<method name="of()Ljava/util/HexFormat;"/>
	</class>
	<class name="java/util/stream/Stream" since="24">
		<extends name="java/lang/Object"/>
		<method name="toList()Ljava/util/List;" since="34"/>
	</class>
	<class name="java/util/stream/Collectors" since="24">
		<extends name="java/lang/Object"/>
		<method name="toUnmodifiableList()Ljava/util/stream/Collector;" since="33"/>
	</class>
	<class name="java/util/regex/Pattern" since="1">
		<extends name="java/lang/Object"/>
		<method name="asMatchPredicate()Ljava/util/function/Predicate;" since="33"/>
	</class>
	<class name="java/util/Comparator" since="1">
		<extends name="java/lang/Object"/>
	</class>
	<class name="java/util/Optional" since="24">
		<extends name="java/lang/Object"/>
	</class>
	<class name="java/util/function/IntFunction" since="24">
		<extends name="java/lang/Object"/>
	</class>
	<class name="java/util/function/Predicate" since="24">
		<extends name="java/lang/Object"/>
	</class>
	<class name="java/util/stream/Collector" since="24">
		<extends name="java/lang/Object"/>
	</class>
	<class name="java/util/Gone" since="1" removed="20">
		<extends name="java/lang/Object"/>
	</class>
</api>
'''

# Machine-format (200) desugar_jdk_libs configuration: types are package-map prefixed.
DESUGAR_JSON = {
    'identifier': 'com.tools.android:desugar_jdk_libs_configuration:2.1.4',
    'configuration_format_version': 200,
    'package_map': {'java.lang': 'a$', 'java.util': 'b$', 'java.util.function': 'c$', 'java.util.stream': 'f$',
                    'java.util.concurrent': 'h$'},
    'common_flags': [
        {'api_level_below_or_equal': 37, 'amend_library_method': ['public a$Object[] b$Collection#toArray(c$IntFunction)']},
        {'api_level_below_or_equal': 32, 'api_level_greater_or_equal': 24,
         'static_retarget': {'f$Collector f$Collectors#toUnmodifiableList()': 'f$Collector f$DesugarCollectors#toUnmodifiableList()'}},
        {'api_level_below_or_equal': 29, 'rewrite_type': {'h$Flow': 'i$Flow'}},
        # Below the floor: must not apply at minSdk 26.
        {'api_level_below_or_equal': 23, 'rewrite_type': {'b$Optional': 'k$Optional'}},
    ],
    'program_flags': [],
    'library_flags': [{'api_level_below_or_equal': 37, 'rewrite_type': {'b$Library': 'k$Library'}}],
}

SHADOW_GRADLE = '''
val shadowPackages = listOf(
    "java.awt", "java.net.http",
    "javax.swing",
)
val shadowClasses = listOf("java.lang.ProcessHandle")
'''


def jdk_fixture_plugin():
    """One class referencing a member of every disposition (and some that must drop out)."""
    w = ClassWriter('com/example/JdkUser')
    w.method('<init>', '()V')
    w.method_ref('java/lang/String', 'length', '()I')                                       # floor: resolved
    w.method_ref('java/lang/String', 'isEmpty', '()Z')                                      # floor: resolved
    w.field_ref('java/lang/String', 'CASE_INSENSITIVE_ORDER', 'Ljava/util/Comparator;')     # floor: resolved
    w.method_ref('java/lang/String', 'repeat', '(I)Ljava/lang/String;')                    # D8 backport
    w.imethod_ref('java/util/List', 'of', '([Ljava/lang/Object;)Ljava/util/List;')         # D8 backport
    w.method_ref('java/util/stream/Collectors', 'toUnmodifiableList', '()Ljava/util/stream/Collector;')  # config
    w.method_ref('java/util/ArrayList', 'toArray', '(Ljava/util/function/IntFunction;)[Ljava/lang/Object;')  # inherited amend
    w.klass('java/util/concurrent/Flow')                                                    # rewrite_type
    w.imethod_ref('java/util/stream/Stream', 'toList', '()Ljava/util/List;')               # API 34
    w.method_ref('java/lang/String', 'formatted', '([Ljava/lang/Object;)Ljava/lang/String;')  # API 34
    w.method_ref('java/util/HexFormat', 'of', '()Ljava/util/HexFormat;')                    # class + member, API 34
    w.method_ref('java/lang/Thread', 'ofVirtual', '()Ljava/lang/Thread$Builder$OfVirtual;')  # unsupported
    w.method_ref('java/lang/Thread', 'onSpinWait', '()V')                                   # retarget, no shim
    w.method_ref('java/util/regex/Pattern', 'asMatchPredicate', '()Ljava/util/function/Predicate;')  # shim
    w.method_ref('java/awt/Color', 'brighter', '()Ljava/awt/Color;')                        # shadow: main scan's
    w.method_ref('java/net/http/HttpClient', 'newHttpClient', '()Ljava/net/http/HttpClient;')  # shadow
    w.klass('javax/annotation/Nullable')                                                    # library, not JDK
    w.klass('com/google/common/collect/ImmutableList')                                      # not JDK
    return w.build()


class JdkGapsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.xml = os.path.join(self.tmp.name, 'api-versions.xml')
        with open(self.xml, 'w', encoding='utf8') as fh:
            fh.write(API_VERSIONS_XML)
        gradle = os.path.join(self.tmp.name, 'build.gradle.kts')
        with open(gradle, 'w', encoding='utf8') as fh:
            fh.write(SHADOW_GRADLE)
        self.api = scan.ApiVersions(self.xml)
        self.shadow = scan.read_shadow_owners(gradle)
        self.classifier = scan.JdkGapClassifier(scan.ApiLevelSurface(self.api, 26), scan.ApiLevelSurface(self.api, 35),
                                                scan.DesugarConfig(DESUGAR_JSON, 26), self.api, self.shadow)

    def tearDown(self):
        self.tmp.cleanup()

    def test_api_versions_levels(self):
        floor = scan.ApiLevelSurface(self.api, 26)
        device = scan.ApiLevelSurface(self.api, 35)
        self.assertTrue(floor.has_member('java/lang/String', 'isEmpty', '()Z', False))
        self.assertFalse(floor.has_member('java/lang/String', 'formatted', '([Ljava/lang/Object;)Ljava/lang/String;', False))
        self.assertTrue(device.has_member('java/lang/String', 'formatted', '([Ljava/lang/Object;)Ljava/lang/String;', False))
        self.assertTrue(floor.has_member('java/util/ArrayList', 'stream', '()Ljava/util/stream/Stream;', False))
        self.assertTrue(floor.has_member('java/lang/String', 'CASE_INSENSITIVE_ORDER', 'Ljava/util/Comparator;', True))
        self.assertNotIn('java/util/HexFormat', floor)
        self.assertIn('java/util/HexFormat', device)
        self.assertNotIn('java/util/Gone', floor)
        self.assertIn('java/util/Gone', scan.ApiLevelSurface(self.api, 19))
        self.assertEqual(self.api.member_since('java/util/ArrayList', 'stream', '()Ljava/util/stream/Stream;', False), 24)

    def test_desugar_signatures_decode(self):
        d = scan.DesugarConfig(DESUGAR_JSON, 26)
        self.assertEqual(d.parse_method('public a$Object[] b$Collection#toArray(c$IntFunction)'),
                         ('java/util/Collection', 'toArray', '(Ljava/util/function/IntFunction;)[Ljava/lang/Object;'))
        self.assertEqual(d.parse_method('f$Stream f$Stream#iterate(a$Object, c$Predicate, c$UnaryOperator)'),
                         ('java/util/stream/Stream', 'iterate',
                          '(Ljava/lang/Object;Ljava/util/function/Predicate;Ljava/util/function/UnaryOperator;)Ljava/util/stream/Stream;'))
        self.assertEqual(d.parse_method('long java.time.Duration#dividedBy(java.time.Duration, int[])'),
                         ('java/time/Duration', 'dividedBy', '(Ljava/time/Duration;[I)J'))
        self.assertEqual(d.parse_field('public static d$LocalDate d$LocalDate#EPOCH'), ('d$LocalDate', 'EPOCH', None))
        self.assertIn('java/util/concurrent/Flow', d.types)
        self.assertNotIn('java/util/Optional', d.types)   # flag for API <= 23
        self.assertNotIn('java/util/Library', d.types)    # library_flags are not the program's

    def test_shadow_owners(self):
        self.assertTrue(scan._shadowed('java/awt/geom/Area', self.shadow))
        self.assertTrue(scan._shadowed('java/lang/ProcessHandle$Info', self.shadow))
        self.assertTrue(scan._shadowed('java/lang/ProcessHandle', self.shadow))
        self.assertFalse(scan._shadowed('java/lang/ProcessHandleX', self.shadow))
        self.assertFalse(scan._shadowed('java/awtx/Foo', self.shadow))

    def test_dispositions_of_fixture_members(self):
        c = self.classifier
        expect = {
            ('method', 'java/lang/String', 'repeat', '(I)Ljava/lang/String;'): ('desugar', 'D8 backport'),
            ('method', 'java/util/List', 'of', '([Ljava/lang/Object;)Ljava/util/List;'): ('desugar', 'D8 backport'),
            ('method', 'java/util/stream/Collectors', 'toUnmodifiableList', '()Ljava/util/stream/Collector;'):
                ('desugar', 'desugar_jdk_libs 2.1.4 static_retarget'),
            ('method', 'java/util/ArrayList', 'toArray', '(Ljava/util/function/IntFunction;)[Ljava/lang/Object;'):
                ('desugar', 'desugar_jdk_libs 2.1.4 amend_library_method'),
            ('class', 'java/util/concurrent/Flow', '', ''): ('desugar', 'desugar_jdk_libs 2.1.4 rewrite_type'),
            ('method', 'java/util/stream/Stream', 'toList', '()Ljava/util/List;'): ('already-on-device-API', 'android API 34'),
            ('class', 'java/util/HexFormat', '', ''): ('already-on-device-API', 'android API 34'),
            ('method', 'java/lang/Thread', 'onSpinWait', '()V'): ('retarget', ''),
            ('method', 'java/util/regex/Pattern', 'asMatchPredicate', '()Ljava/util/function/Predicate;'):
                ('retarget', 'DesktopRegexTransformer -> net.runelite.mp.compat.regex (hub jars)'),
        }
        for key, (disposition, via) in expect.items():
            got = c.classify(*key)
            self.assertIsNotNone(got, key)
            self.assertEqual((got['disposition'], got['via']), (disposition, via), key)
        got = c.classify('method', 'java/lang/Thread', 'ofVirtual', '()Ljava/lang/Thread$Builder$OfVirtual;')
        self.assertEqual(got['disposition'], 'unsupported')
        self.assertEqual(c.classify('method', 'java/lang/String', 'formatted', '([Ljava/lang/Object;)Ljava/lang/String;')['androidSince'], 34)
        for gone in (('method', 'java/lang/String', 'isEmpty', '()Z'),
                     ('method', 'java/awt/Color', 'brighter', '()Ljava/awt/Color;'),
                     ('class', 'java/lang/invoke/LambdaMetafactory', '', ''),
                     ('class', 'com/google/common/collect/ImmutableList', '', '')):
            self.assertIsNone(c.classify(*gone), gone)

    def test_floor_cut_from_jar_sees_hidden_supertypes(self):
        # api-versions.xml lists no hidden supertype; android.jar does, so what StringBuilder
        # inherits from AbstractStringBuilder links at the floor and is no gap.
        obj = ClassWriter('java/lang/Object', superclass=None)
        obj.method('<init>', '()V')
        asb = ClassWriter('java/lang/AbstractStringBuilder')
        asb.method('setLength', '(I)V')
        sb = ClassWriter('java/lang/StringBuilder', superclass='java/lang/AbstractStringBuilder')
        sb.method('<init>', '()V')
        s = ClassWriter('java/lang/String')
        s.method('length', '()I')
        s.method('repeat', '(I)Ljava/lang/String;')
        path = os.path.join(self.tmp.name, 'android.jar')
        with open(path, 'wb') as fh:
            fh.write(jar_bytes([obj.build(), asb.build(), sb.build(), s.build()]))
        floor = scan.FilteredSurface(scan.Surface([path]), self.api, 26)
        try:
            self.assertFalse(scan.ApiLevelSurface(self.api, 26).has_member('java/lang/StringBuilder', 'setLength', '(I)V', False))
            self.assertTrue(floor.has_member('java/lang/StringBuilder', 'setLength', '(I)V', False))
            self.assertTrue(floor.has_member('java/lang/String', 'length', '()I', False))
            self.assertFalse(floor.has_member('java/lang/String', 'repeat', '(I)Ljava/lang/String;', False))
            c = scan.JdkGapClassifier(floor, scan.ApiLevelSurface(self.api, 35), scan.DesugarConfig(DESUGAR_JSON, 26),
                                      self.api, self.shadow)
            self.assertIsNone(c.classify('method', 'java/lang/StringBuilder', 'setLength', '(I)V'))
            self.assertEqual(c.classify('method', 'java/lang/String', 'repeat', '(I)Ljava/lang/String;')['disposition'], 'desugar')
        finally:
            floor.close()

    def test_member_since_takes_the_earliest_inherited_path(self):
        xml = os.path.join(self.tmp.name, 'sequenced.xml')
        with open(xml, 'w', encoding='utf8') as fh:
            fh.write('''<?xml version="1.0" encoding="utf-8"?>
<api version="3">
	<class name="java/util/Collection" since="1"><method name="add(Ljava/lang/Object;)Z"/></class>
	<class name="java/util/SequencedCollection" since="35"><implements name="java/util/Collection"/></class>
	<class name="java/util/Set" since="1"><implements name="java/util/Collection"/></class>
	<class name="java/util/SortedSet" since="1">
		<implements name="java/util/SequencedCollection" since="35"/>
		<implements name="java/util/Set"/>
	</class>
</api>
''')
        api = scan.ApiVersions(xml)
        self.assertEqual(api.member_since('java/util/SortedSet', 'add', '(Ljava/lang/Object;)Z', False), 1)

    def test_unshimmed_retargets_name_their_planned_helper(self):
        got = self.classifier.classify('method', 'java/lang/Runtime', 'version', '()Ljava/lang/Runtime$Version;')
        self.assertEqual(got['disposition'], 'retarget')
        self.assertTrue(got['via'].startswith('planned (#91)'), got)
        got = self.classifier.classify('method', 'java/nio/file/Files', 'readString', '(Ljava/nio/file/Path;)Ljava/lang/String;')
        self.assertTrue(got['via'].startswith('planned (#91)'), got)

    def test_report_baseline_and_markdown(self):
        refs = scan.jdk_references([scan.parse_class(jdk_fixture_plugin())])
        self.assertNotIn(('class', 'javax/annotation/Nullable', '', ''), refs)
        self.assertNotIn(('class', 'com/google/common/collect/ImmutableList', '', ''), refs)
        results = {
            'big': {'classes': 1, 'refs': [[k[0], k[1], k[2], k[3], n] for k, n in refs.items()]},
            '(injected client)': {'classes': 1, 'refs': [['method', 'java/lang/String', 'repeat', '(I)Ljava/lang/String;', 4]]},
        }
        meta = {'generatedAt': 'now', 'hubVersion': '1.0', 'minApi': 26, 'deviceApi': 35,
                'resolvedAgainst': {'floor': 'x', 'device': 'y', 'desugar': 'z', 'shims': 's'}}
        report = scan.build_jdk_gaps(results, self.classifier, {'big': 500}, meta)
        json.dumps(report)
        by_sig = dict((e['signature'], e) for e in report['gaps'])
        repeat = by_sig['method java/lang/String.repeat(I)Ljava/lang/String;']
        self.assertEqual((repeat['pluginCount'], repeat['referenceCount'], repeat['installs']), (2, 5, 500))
        self.assertEqual(repeat['plugins'], ['big', '(injected client)'])
        self.assertEqual(repeat['androidSince'], 33)
        self.assertNotIn('class java/awt/Color', by_sig)
        self.assertNotIn('method java/net/http/HttpClient.newHttpClient()Ljava/net/http/HttpClient;', by_sig)
        self.assertEqual(report['gaps'][0]['disposition'], 'retarget')
        self.assertEqual([p['name'] for p in report['plugins']], ['(injected client)', 'big'])
        big = report['plugins'][1]['dispositions']
        self.assertEqual(big['desugar'], 5)                 # repeat, List.of, toUnmodifiableList, toArray, Flow
        self.assertEqual(big['already-on-device-API'], 4)   # toList, formatted, HexFormat class + HexFormat.of
        self.assertEqual(big['unsupported'], 2)             # Thread.ofVirtual and its return type
        self.assertEqual(big['retarget'], 2)                # onSpinWait, asMatchPredicate
        self.assertEqual(report['dispositions']['unsupported']['signatures'], 2)

        md = scan.render_jdk_gaps_markdown(report)
        self.assertIn('| retarget |', md)
        self.assertIn('## already-on-device-API', md)
        self.assertIn('`java/util/stream/Stream.toList()Ljava/util/List;` | method | 34 | android API 34 |', md)
        self.assertIn('| (injected client) | 0 | 1 | 1 | 0 | 0 | 0 | 1 |', md)

    def test_main_offline_writes_both_outputs(self):
        cache = os.path.join(self.tmp.name, 'cache')
        jar = jar_bytes([jdk_fixture_plugin()])
        entry = {'internalName': 'big', 'jarHash': scan.jar_hash(jar), 'jarSize': len(jar), 'displayName': 'Big'}
        os.makedirs(os.path.join(cache, 'jar'))
        with open(os.path.join(cache, 'jar', scan.jar_file_name(entry)), 'wb') as fh:
            fh.write(jar)
        with open(os.path.join(cache, 'manifest-1.0_full.json'), 'w', encoding='utf8') as fh:
            json.dump({'jars': [entry], 'display': []}, fh)
        desugar = os.path.join(self.tmp.name, 'desugar.json')
        with open(desugar, 'w', encoding='utf8') as fh:
            json.dump(DESUGAR_JSON, fh)
        client = os.path.join(self.tmp.name, 'client.jar')
        with open(client, 'wb') as fh:
            fh.write(jar)
        out = os.path.join(self.tmp.name, 'out')
        baseline = os.path.join(self.tmp.name, 'docs', 'jdk-api-gaps.json')
        rc = scan.main(['--jdk-gaps', '--offline', '--jobs', '1', '--cache-dir', cache, '--out-dir', out,
                        '--baseline', baseline, '--api-versions', self.xml, '--desugar-json', desugar,
                        '--sdk-dir', os.path.join(self.tmp.name, 'no-sdk'), '--min-api', '26', '--device-api', '35',
                        '--injected-jar', client, '--client-jar', client])
        self.assertEqual(rc, 0)
        self.assertTrue(os.path.exists(os.path.join(out, 'jdk-gaps.md')))
        with open(baseline, encoding='utf8') as fh:
            report = json.load(fh)
        self.assertEqual(report['sourcesScanned'], 3)
        self.assertEqual(report['resolvedAgainst']['floor'], '%s/data/api-versions.xml at API 26'
                         % os.path.basename(os.path.dirname(self.tmp.name)))
        for e in report['gaps']:
            self.assertIn(e['disposition'], scan.DISPOSITIONS)


if __name__ == '__main__':
    unittest.main()
