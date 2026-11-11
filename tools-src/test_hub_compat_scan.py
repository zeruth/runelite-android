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


if __name__ == '__main__':
    unittest.main()
