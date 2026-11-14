#!/usr/bin/env python3
"""Issue checklists and the CI baseline gate over hub-compat-scan.py's report.json.

Issues #62-#72 track one bucket each (see ISSUES). For an issue this prints a markdown
checklist of every signature of its bucket, from the latest report and the committed
baseline (docs/hub-compat-baseline.json), each marked:

    unresolved  in the report, no note in the baseline          - [ ]
    documented  in the report, the baseline explains the gap    - [ ] ... documented: <note>
    resolved    in the baseline, gone from the report           - [x]

Documented signatures still fail to link, so they count as unresolved; the unresolved
total therefore equals report.json's buckets[<bucket>].signatures.

Usage:
    python tools-src/hub-compat-issues.py --issue 62          # one checklist, paste into #62
    python tools-src/hub-compat-issues.py --all
    python tools-src/hub-compat-issues.py --check             # CI gate: new signatures fail
    python tools-src/hub-compat-issues.py --update-baseline   # accept the report as the baseline
"""
import argparse
import importlib.util
import json
import os
import sys

_spec = importlib.util.spec_from_file_location(
    'hub_compat_scan', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'hub-compat-scan.py'))
scan = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(scan)

ROOT = scan.ROOT
DEFAULT_REPORT = os.path.join(ROOT, 'build', 'hub-compat', 'report.json')
DEFAULT_BASELINE = os.path.join(ROOT, 'docs', 'hub-compat-baseline.json')

# Issue number -> bucket; the scanner's first eleven buckets are #62-#72 in order.
ISSUES = dict((62 + i, b) for i, b in enumerate(scan.BUCKETS[:11]))

UNRESOLVED, DOCUMENTED, RESOLVED = 'unresolved', 'documented', 'resolved'


def entry_key(e):
    return scan.sig_key(e['kind'], e['owner'], e['name'], e['descriptor'])


def load_json(path, what):
    if not os.path.exists(path):
        raise SystemExit('no %s at %s' % (what, path))
    with open(path, encoding='utf8') as fh:
        return json.load(fh)


def load_baseline(path):
    if not os.path.exists(path):
        return {'signatures': {}}
    data = load_json(path, 'baseline')
    data.setdefault('signatures', {})
    return data


def baseline_status(info):
    return DOCUMENTED if info.get('status') == DOCUMENTED else UNRESOLVED


def partial_scan_warning(report, baseline):
    """A --limit or --plugin scan drops signatures without fixing them; say so rather than
    let them read as resolved."""
    want = baseline.get('pluginsScanned')
    got = report.get('pluginsScanned', 0)
    if want and got < want:
        return 'warning: report scanned %d plugins, baseline %d; "resolved" may only mean "not scanned"' % (got, want)
    return None


def checklist(report, baseline, issue, top_plugins=3):
    """(markdown, counts) for one issue."""
    bucket = ISSUES[issue]
    present = dict((entry_key(e), e) for e in report['unresolved'] if e['bucket'] == bucket)
    base = dict((k, v) for k, v in baseline['signatures'].items() if v.get('bucket') == bucket)

    rows = []
    for key, e in present.items():
        info = base.get(key)
        status = baseline_status(info) if info else UNRESOLVED
        rows.append((status, key, e, info))
    for key, info in base.items():
        if key not in present:
            rows.append((RESOLVED, key, None, info))

    counts = {UNRESOLVED: 0, DOCUMENTED: 0, RESOLVED: 0}
    for status, _, _, _ in rows:
        counts[status] += 1
    # Documented gaps still fail to link: they are part of the unresolved total.
    unresolved_total = counts[UNRESOLVED] + counts[DOCUMENTED]
    expected = report['buckets'].get(bucket, {}).get('signatures', 0)
    if unresolved_total != expected:
        raise SystemExit('internal error: %d unresolved in %s but report.json says %d'
                         % (unresolved_total, bucket, expected))

    order = {UNRESOLVED: 0, DOCUMENTED: 1, RESOLVED: 2}
    rows.sort(key=lambda r: (order[r[0]], -(r[2]['installs'] if r[2] else 0), r[1]))

    out = []
    w = out.append
    w('### Hub compatibility: %s' % bucket)
    w('')
    w('From `build/hub-compat/report.json` (%s, hub %s, %d plugins scanned) against `docs/hub-compat-baseline.json`.'
      % (report.get('generatedAt', '?'), report.get('hubVersion', '?'), report.get('pluginsScanned', 0)))
    w('')
    w('**%d unresolved** (%d of them documented), %d resolved.' % (unresolved_total, counts[DOCUMENTED], counts[RESOLVED]))
    w('')
    for status, key, e, info in rows:
        box = '[x]' if status == RESOLVED else '[ ]'
        line = '- %s `%s`' % (box, key)
        if e:
            plugins = ', '.join(e['plugins'][:top_plugins]) + (' …' if len(e['plugins']) > top_plugins else '')
            line += ' — %d plugins, %d installs (%s)' % (e['pluginCount'], e['installs'], plugins)
        if status == RESOLVED:
            line += ' — resolved'
        elif status == DOCUMENTED:
            line += ' — documented: %s' % (info.get('note') or 'see docs/hub-compat.md')
        w(line)
    if not rows:
        w('Nothing in this bucket.')
    w('')
    counts['unresolvedTotal'] = unresolved_total
    return '\n'.join(out), counts


def proposed_baseline(report, baseline):
    """The report as a baseline, keeping the notes of documented entries still present."""
    old = baseline['signatures']
    sigs = {}
    for e in report['unresolved']:
        key = entry_key(e)
        info = {'bucket': e['bucket'], 'status': UNRESOLVED}
        prev = old.get(key)
        if prev and baseline_status(prev) == DOCUMENTED:
            info['status'] = DOCUMENTED
            info['note'] = prev.get('note', '')
        sigs[key] = info
    return {
        'comment': 'Accepted hub-compat-scan signatures; see docs/hub-compat.md. '
                   'Regenerate with tools-src/hub-compat-issues.py --update-baseline.',
        'hubVersion': report.get('hubVersion'),
        'generatedAt': report.get('generatedAt'),
        'pluginsScanned': report.get('pluginsScanned'),
        'signatures': dict(sorted(sigs.items())),
    }


def write_json(path, data):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, 'w', encoding='utf8', newline='\n') as fh:
        json.dump(data, fh, indent=1)
        fh.write('\n')


def check(report, baseline, proposed_path):
    """Exit status 1 when the report has a signature the baseline does not list."""
    write_json(proposed_path, proposed_baseline(report, baseline))
    known = baseline['signatures']
    new = [e for e in report['unresolved'] if entry_key(e) not in known]
    present = set(entry_key(e) for e in report['unresolved'])
    gone = sorted(k for k in known if k not in present)
    if gone:
        print('-- Baseline entries no longer unresolved (shrink with --update-baseline) --')
        for k in gone:
            print('  %s' % k)
    if new:
        print('-- Unresolved and not in the baseline --')
        for e in new:
            print('  [%s] %s — %d plugins, %d installs (%s)' % (
                e['bucket'], entry_key(e), e['pluginCount'], e['installs'], ', '.join(e['plugins'][:5])))
        print('%d new signature(s). Shadow them, or accept them: download baseline-proposed.json from the '
              'hub-compat artifact (or run --update-baseline locally) into docs/hub-compat-baseline.json.'
              % len(new))
        print('proposed baseline: %s' % proposed_path)
        return 1
    print('hub-compat: %d unresolved, all in the baseline (%d entries, %d now resolved)'
          % (len(report['unresolved']), len(known), len(gone)))
    return 0


def build_parser():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--issue', type=int, action='append', choices=sorted(ISSUES), help='issue number (repeatable)')
    ap.add_argument('--all', action='store_true', help='every issue #62-#72')
    ap.add_argument('--check', action='store_true', help='fail when the report has signatures not in the baseline')
    ap.add_argument('--update-baseline', action='store_true', help='write the report as the baseline')
    ap.add_argument('--report', default=DEFAULT_REPORT)
    ap.add_argument('--baseline', default=DEFAULT_BASELINE)
    ap.add_argument('--proposed', help='where --check writes the proposed baseline (default: beside the report)')
    ap.add_argument('--top-plugins', type=int, default=3, help='plugins named per signature')
    return ap


def main(argv=None):
    args = build_parser().parse_args(argv)
    if sys.stdout.encoding and sys.stdout.encoding.lower().replace('-', '') != 'utf8':
        # Windows consoles default to a code page that cannot print the dashes.
        sys.stdout.reconfigure(encoding='utf-8')
    report = load_json(args.report, 'report (run tools-src/hub-compat-scan.py)')
    baseline = load_baseline(args.baseline)
    warning = partial_scan_warning(report, baseline)
    if warning:
        sys.stderr.write(warning + '\n')

    if args.update_baseline:
        data = proposed_baseline(report, baseline)
        write_json(args.baseline, data)
        print('baseline: %d signatures -> %s' % (len(data['signatures']), args.baseline))
        return 0
    if args.check:
        proposed = args.proposed or os.path.join(os.path.dirname(os.path.abspath(args.report)), 'baseline-proposed.json')
        return check(report, baseline, proposed)

    issues = sorted(ISSUES) if args.all else (args.issue or [])
    if not issues:
        build_parser().error('give --issue N, --all, --check or --update-baseline')
    for issue in issues:
        text, counts = checklist(report, baseline, issue, args.top_plugins)
        print('<!-- #%d: %d unresolved, %d documented, %d resolved -->' % (
            issue, counts['unresolvedTotal'], counts[DOCUMENTED], counts[RESOLVED]))
        print(text)
    return 0


if __name__ == '__main__':
    sys.exit(main())
