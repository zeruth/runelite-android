"""Unit tests for test-hub-plugins.py over recorded logcat and uiautomator fixtures.

Run: python -m unittest discover -s tools-src -p test_hub_plugins_harness.py
"""
import argparse
import importlib.util
import io
import json
import os
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr, redirect_stdout

HERE = os.path.dirname(os.path.abspath(__file__))
FIXTURES = os.path.join(HERE, 'fixtures', 'hub-plugins')
_spec = importlib.util.spec_from_file_location('hub_plugins_harness', os.path.join(HERE, 'test-hub-plugins.py'))
harness = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(harness)


def fixture(name):
    with open(os.path.join(FIXTURES, name), encoding='utf8') as fh:
        return fh.read()


def entries(name, pids=None):
    text = fixture(name)
    if pids is not None:
        text = harness.filter_pids(text, pids)
    return harness.parse_logcat(text)


def kinds(errors):
    return sorted((e['kind'], e['count']) for e in errors)


class LogcatParsing(unittest.TestCase):
    def test_threadtime_fields(self):
        log = entries('install-ok.logcat')
        first = log[0]
        self.assertEqual((first.pid, first.tid, first.level, first.tag), (4321, 4400, 'I', 'RlHubStatus'))
        self.assertEqual(first.message, 'HUB download internal=example-plugin size=123456')
        self.assertTrue(any(e.message.strip().startswith('at com.example.plugin.ExampleOverlay.render') for e in log))

    def test_brief_format_and_headerless_continuation(self):
        log = harness.parse_logcat('E/AndroidRuntime( 4321): java.lang.VerifyError: bad\nwrapped tail\n')
        self.assertEqual([(e.pid, e.tag, e.message) for e in log],
                         [(4321, 'AndroidRuntime', 'java.lang.VerifyError: bad'), (4321, 'AndroidRuntime', 'wrapped tail')])

    def test_filter_pids_drops_other_processes(self):
        everything = entries('install-ok.logcat')
        mine = entries('install-ok.logcat', {4321})
        self.assertTrue(any(e.pid == 1200 for e in everything))
        self.assertFalse(any(e.pid == 1200 for e in mine))
        self.assertEqual(len(everything) - 1, len(mine))


class HubEvents(unittest.TestCase):
    def test_loaded_event_carries_plugin_names(self):
        events = harness.hub_events(entries('install-ok.logcat'))
        self.assertEqual([e['event'] for e in events], ['download', 'converting', 'converted', 'loaded'])
        self.assertEqual(events[0]['size'], '123456')
        self.assertEqual(events[2]['ms'], '4100')
        self.assertEqual(events[3]['plugins'], ['Example Plugin', 'Example Overlay'])
        self.assertEqual(harness.install_outcome(events, 'example-plugin'),
                         ('ok', '', ['Example Plugin', 'Example Overlay'], None))

    def test_start_stage_failure_is_installed_but_not_started(self):
        events = harness.hub_events(entries('start-failed.logcat'))
        install, message, plugins, start = harness.install_outcome(events, 'broken-plugin')
        self.assertEqual((install, message, plugins), ('ok', '', []))
        self.assertTrue(start.startswith('Failed to start: No virtual method getSystemSelection'))
        state, why = harness.start_outcome(entries('start-failed.logcat'), start, {})
        self.assertEqual(state, 'failed')
        self.assertEqual(why, start)

    def test_dex_failure_and_hub_error(self):
        events = harness.hub_events(entries('dex-failed.logcat'))
        install, message, _, _ = harness.install_outcome(events, 'lwjgl-plugin')
        self.assertEqual(install, 'failed')
        self.assertEqual(message, "DEX: Couldn't convert for Android: uses org.lwjgl.opengl.GL11, which is not "
                                  'available on Android')
        self.assertEqual(harness.hub_errors(events), ["Couldn't reach the Plugin Hub (timeout)"])

    def test_no_event_is_pending_and_later_load_wins(self):
        self.assertEqual(harness.install_outcome([], 'x')[0], 'pending')
        events = [{'event': 'failed', 'internal': 'x', 'stage': 'DOWNLOAD', 'message': 'reset'},
                  {'event': 'loaded', 'internal': 'x', 'plugins': ['X']}]
        self.assertEqual(harness.install_outcome(events, 'x'), ('ok', '', ['X'], None))

    def test_start_outcome_from_toggles_and_log_markers(self):
        log = entries('install-ok.logcat')
        self.assertEqual(harness.start_outcome(log, None, {'Example Plugin': True}), ('ok', ''))
        self.assertEqual(harness.start_outcome(log, None, {'Example Plugin': None})[0], 'unknown')
        self.assertEqual(harness.start_outcome(log, None, {'Example Plugin': False})[0], 'failed')
        marked = harness.parse_logcat('11-12 05:19:01.100  1  2 E PluginManager: Unable to start plugin Foo\n')
        self.assertEqual(harness.start_outcome(marked, None, {'Foo': True}),
                         ('failed', 'Unable to start plugin Foo'))


class Errors(unittest.TestCase):
    def test_install_ok_window(self):
        errors = harness.collect_errors(entries('install-ok.logcat', {4321}), ('com.example.plugin.',))
        self.assertEqual(kinds(errors), [('NoClassDefFoundError', 1), ('UnsupportedOperationException', 1),
                                         ('plugin-trace', 2)])
        by_kind = dict((e['kind'], e) for e in errors)
        uoe = by_kind['UnsupportedOperationException']
        self.assertEqual(uoe['frame'], 'java.awt.Toolkit.getSystemSelection')
        self.assertEqual(uoe['context'], 'Uncaught exception on the EDT')
        trace = by_kind['plugin-trace']
        self.assertEqual((trace['exception'], trace['frame']),
                         ('java.lang.IllegalStateException', 'com.example.plugin.ExampleOverlay.render'))
        ncdfe = by_kind['NoClassDefFoundError']
        self.assertEqual(ncdfe['message'], 'Failed resolution of: Ljavax/sound/midi/MidiSystem;')

    def test_java_util_unsupported_operation_is_not_a_shadow_gap(self):
        errors = harness.collect_errors(entries('install-ok.logcat', {4321}), ())
        self.assertNotIn('java.util.Collections$UnmodifiableCollection.add', [e['frame'] for e in errors])
        # Without the plugin's packages a plain trace is not attributed.
        self.assertEqual(kinds(errors), [('NoClassDefFoundError', 1), ('UnsupportedOperationException', 1)])

    def test_other_processes_count_only_when_not_filtered(self):
        errors = harness.collect_errors(entries('install-ok.logcat'), ())
        self.assertIn(('NoSuchMethodError', 1), kinds(errors))

    def test_linkage_error_deep_in_a_cause_chain(self):
        errors = harness.collect_errors(entries('start-failed.logcat'), ('com.broken.',))
        self.assertEqual(kinds(errors), [('NoSuchMethodError', 1)])
        self.assertEqual(errors[0]['frame'], 'com.broken.BrokenPlugin.startUp')
        self.assertEqual(errors[0]['exception'], 'java.lang.NoSuchMethodError')
        self.assertEqual(errors[0]['context'], 'Unable to start or load external plugin "broken-plugin"')

    def test_crash_and_unsatisfied_link(self):
        errors = harness.collect_errors(entries('dex-failed.logcat'), ('com.lwjgl.plugin.',))
        self.assertEqual(kinds(errors), [('UnsatisfiedLinkError', 1), ('crash', 1)])
        crash = [e for e in errors if e['kind'] == 'crash'][0]
        self.assertIn('FATAL EXCEPTION: main', crash['message'])
        self.assertIn('UnsatisfiedLinkError', crash['message'])

    def test_plugin_packages_from_stub(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = os.path.join(tmp, 'p.jar')
            with zipfile.ZipFile(jar, 'w') as z:
                z.writestr('runelite_plugin.json', json.dumps(
                    {'plugins': ['com.example.plugin.ExamplePlugin', 'com.example.plugin.extra.Extra']}))
            self.assertEqual(harness.plugin_packages(jar), ('com.example.plugin.', 'com.example.plugin.extra.'))
            self.assertEqual(harness.plugin_packages(os.path.join(tmp, 'missing.jar')), ())


class UiQueries(unittest.TestCase):
    def setUp(self):
        self.nodes = harness.parse_nodes(fixture('sidebar.xml'))

    def test_row_buttons(self):
        label, node = harness.find_row_button(self.nodes, 'Quest Helper')
        self.assertEqual((label, node['bounds']), ('Remove', '[2200,170][2310,220]'))
        self.assertEqual(harness.find_row_button(self.nodes, 'Quest Helper Extra')[0], 'Install')
        self.assertEqual(harness.find_row_button(self.nodes, '117 HD')[0], 'Unavailable')
        self.assertIsNone(harness.find_row_button(self.nodes, 'Not There'))

    def test_toggles_and_nav(self):
        self.assertEqual(harness.find_toggle(self.nodes, 'Quest Helper')['checked'], 'true')
        self.assertEqual(harness.find_toggle(self.nodes, 'Example Plugin')['checked'], 'false')
        self.assertIsNone(harness.find_toggle(self.nodes, 'Notes'))
        self.assertEqual(harness.nav_entries(self.nodes), {'Plugins', 'Notes'})
        self.assertEqual([n['bounds'] for n in self.nodes if harness.is_plugins_icon(n)], ['[2330,10][2390,70]'])

    def test_merged_text_and_centers(self):
        launch = harness.find_text(self.nodes, 'Launch')
        self.assertEqual(launch['bounds'], '[100,700][900,760]')
        self.assertEqual(harness.center(launch), (500, 730))

    def test_search_term(self):
        self.assertEqual(harness.search_term('Quest Helper'), 'Quest Helper')
        self.assertEqual(harness.search_term("Zoinkwiz's (Quest) Helper"), 'Zoinkwiz')
        self.assertEqual(harness.search_term('[GIM] Group Iron Tracker'), 'Group Iron Tracker')
        self.assertEqual(harness.search_term('!!!'), '')


class Matrix(unittest.TestCase):
    def test_round_trip_and_render(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, 'build', 'hub-status')
            doc = os.path.join(tmp, 'docs', 'hub-status.md')
            statuses = [
                {'internalName': 'small', 'displayName': 'Small | Pipe', 'version': '1.0', 'installs': 10,
                 'install': 'failed', 'installMessage': 'DEX: uses org.lwjgl', 'start': 'skipped', 'panel': 'skipped',
                 'errors': [], 'screenshot': None, 'harness': None},
                {'internalName': 'quest-helper', 'displayName': 'Quest Helper', 'version': '4.9', 'installs': 120000,
                 'install': 'ok', 'installMessage': '', 'start': 'ok', 'panel': 'opened', 'panelName': 'Quest Helper',
                 'errors': [{'kind': 'NoSuchMethodError', 'exception': 'java.lang.NoSuchMethodError',
                             'message': 'No virtual method x', 'frame': 'com.questhelper.Q.a', 'context': '',
                             'count': 2}],
                 'screenshot': 'docs/hub-status/quest-helper.png', 'harness': None},
            ]
            for s in statuses:
                harness.write_status(out, s)
            with open(os.path.join(out, 'run.json'), 'w', encoding='utf8') as fh:
                json.dump({'hubVersion': '1.12.5'}, fh)
            loaded = harness.load_statuses(out)
            self.assertEqual(sorted(s['internalName'] for s in loaded), ['quest-helper', 'small'])
            harness.render_markdown(loaded, doc, {'hubVersion': '1.12.5', 'device': 'sdk_gphone64'}, root=tmp)
            with open(doc, encoding='utf8') as fh:
                text = fh.read()
        lines = text.splitlines()
        self.assertIn('Hub: 1.12.5 · Device: sdk_gphone64', text)
        self.assertIn('2 plugins: 1 installed, 1 started, 1 with errors.', text)
        rows = [l for l in lines if l.startswith('| ') and not l.startswith('| Plugin')]
        self.assertTrue(rows[0].startswith('| Quest Helper (`quest-helper`) | 4.9 | 120000 | ✓ | ✓ | opened: Quest Helper'))
        self.assertIn('NoSuchMethodError ×2', rows[0])
        self.assertIn('[png](hub-status/quest-helper.png)', rows[0])
        self.assertIn('Small \\| Pipe', rows[1])
        self.assertIn('✕ DEX: uses org.lwjgl', rows[1])
        self.assertIn('- **NoSuchMethodError** ×2: java.lang.NoSuchMethodError: No virtual method x at '
                      '`com.questhelper.Q.a`', text)

    def test_render_only_needs_no_emulator(self):
        with tempfile.TemporaryDirectory() as tmp:
            doc = os.path.join(tmp, 'out.md')
            with redirect_stdout(io.StringIO()):
                self.assertEqual(harness.main(['--render-only', '--out-dir', tmp, '--doc', doc]), 0)
            self.assertTrue(os.path.exists(doc))

    def test_real_devices_are_refused(self):
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            harness.main(['--serial', 'R5CT1234'])


class CommandLine(unittest.TestCase):
    def test_every_option_is_documented(self):
        parser = harness.build_parser()
        for action in parser._actions:
            if isinstance(action, argparse._HelpAction):
                continue
            self.assertTrue(action.help and action.help != argparse.SUPPRESS, action.option_strings)
        text = parser.format_help()
        for action in parser._actions:
            for option in action.option_strings:
                self.assertIn(option, text)


if __name__ == '__main__':
    unittest.main()
