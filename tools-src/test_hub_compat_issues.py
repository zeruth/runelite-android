"""Unit tests for hub-compat-issues.py over a report built by hub-compat-scan.build_report.

Run: python -m unittest discover -s tools-src -p test_hub_compat_issues.py
"""
import contextlib
import importlib.util
import io
import json
import os
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location('hub_compat_issues', os.path.join(HERE, 'hub-compat-issues.py'))
issues = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(issues)
scan = issues.scan


def fixture_report():
    results = {
        'alpha': {'classes': 3, 'unresolved': [
            ('class', 'java/awt/Robot', '', '', 2),
            ('method', 'java/awt/Component', 'enableInputMethods', '(Z)V', 1),
            ('method!', 'java/awt/LayoutManager3', 'foo', '()V', 1),
        ]},
        'beta': {'classes': 2, 'unresolved': [
            ('method', 'java/awt/Component', 'enableInputMethods', '(Z)V', 4),
            ('class', 'com/google/common/Foo', '', '', 1),
        ]},
    }
    jars = [{'internalName': 'alpha'}, {'internalName': 'beta'}]
    meta = {'generatedAt': 'now', 'hubVersion': '1.0', 'hostSources': [], 'surfaceFingerprint': 'x'}
    return scan.build_report(results, jars, [], {'alpha': 10, 'beta': 5}, meta)


class IssuesTest(unittest.TestCase):

    def test_issue_mapping(self):
        self.assertEqual(issues.ISSUES[62], 'layout/input/painting')
        self.assertEqual(issues.ISSUES[72], 'other libraries')
        self.assertEqual(len(issues.ISSUES), 11)

    def test_unresolved_count_matches_report(self):
        report = fixture_report()
        baseline = {'signatures': {
            'class java/awt/Robot': {'bucket': 'dialogs/windows/desktop', 'status': 'documented', 'note': 'n'},
            'method java/awt/Gone.x()V': {'bucket': 'layout/input/painting', 'status': 'unresolved'},
        }}
        text, counts = issues.checklist(report, baseline, 62)
        self.assertEqual(counts['unresolvedTotal'], report['buckets']['layout/input/painting']['signatures'])
        self.assertEqual(counts['unresolvedTotal'], 2)
        self.assertEqual(counts['resolved'], 1)
        self.assertIn('- [x] `method java/awt/Gone.x()V` — resolved', text)
        self.assertIn('- [ ] `method java/awt/Component.enableInputMethods(Z)V` — 2 plugins, 15 installs', text)

        text, counts = issues.checklist(report, baseline, 64)
        self.assertEqual(counts['documented'], 1)
        self.assertEqual(counts['unresolvedTotal'], 1)
        self.assertIn('documented: n', text)

    def test_check_and_update(self):
        report = fixture_report()
        with tempfile.TemporaryDirectory() as tmp:
            proposed = os.path.join(tmp, 'proposed.json')
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(issues.check(report, {'signatures': {}}, proposed), 1)
            with open(proposed, encoding='utf8') as fh:
                accepted = json.load(fh)
            self.assertEqual(len(accepted['signatures']), len(report['unresolved']))
            accepted['signatures']['class java/awt/Robot']['status'] = 'documented'
            accepted['signatures']['class java/awt/Robot']['note'] = 'kept'
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(issues.check(report, accepted, proposed), 0)
            again = issues.proposed_baseline(report, accepted)
            self.assertEqual(again['signatures']['class java/awt/Robot'],
                             {'bucket': 'dialogs/windows/desktop', 'status': 'documented', 'note': 'kept'})

    def test_partial_scan_warning(self):
        report = fixture_report()
        self.assertIsNotNone(issues.partial_scan_warning(report, {'pluginsScanned': 100}))
        self.assertIsNone(issues.partial_scan_warning(report, {'pluginsScanned': 2}))


if __name__ == '__main__':
    unittest.main()
