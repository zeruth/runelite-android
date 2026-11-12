#!/usr/bin/env python3
"""Emulator smoke harness for Plugin Hub plugins, and the hub status matrix it records.

For each selected hub plugin (the top --limit by install count, or the --plugin list) the
harness drives the app the way a person would, through uiautomator like test-swing-ui.py:
it installs the plugin from the sidebar's External tab, turns it on in the Plugins tab,
opens the navigation panel it adds (when it adds one), takes a screenshot, and reads the
app's logcat for linkage errors (NoSuchMethodError, NoClassDefFoundError,
AbstractMethodError, VerifyError, UnsatisfiedLinkError), UnsupportedOperationException
thrown by the android-awt shadow surface, crashes, and stack traces through the plugin's
own packages. Plugins start at the login screen, so no game login is needed; the launcher
does need one saved account to tap "Launch" on.

Install and start outcomes come from the app's `RlHubStatus` log lines
(PluginInstallTracker: `HUB <event> internal=<name> ...`), not from what the screen shows.

Outputs: build/hub-status/<internalName>.json and .log per plugin, run.json, the
screenshots (docs/hub-status/<internalName>.png by default, so the committed matrix links
resolve), and docs/hub-status.md rendered from every JSON in the output directory.

Emulator only. Unit tests on recorded logcat/uiautomator fixtures:
    python -m unittest discover -s tools-src -p test_hub_plugins_harness.py
"""
import argparse
import datetime
import glob
import importlib.util
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
import zipfile
from collections import namedtuple

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
STATUS_TAG = 'RlHubStatus'

LINKAGE_ERRORS = ('NoSuchMethodError', 'NoClassDefFoundError', 'AbstractMethodError', 'VerifyError',
                  'UnsatisfiedLinkError')
# Packages the android-awt module supplies; an UnsupportedOperationException thrown from
# one of these is a shadow gap, one from java.util is just a plugin bug.
SHADOW_PREFIXES = ('net.runelite.awt.', 'java.awt.', 'javax.swing.', 'javax.sound.', 'javax.imageio.',
                   'java.beans.', 'sun.awt.', 'sun.java2d.', 'sun.font.', 'com.sun.')
# RuneLite's own log lines when startUp throws (PluginManager / ExternalPluginManager).
START_FAILURES = ('Unable to start plugin', 'Error during starting/stopping plugin',
                  'Unable to start or load external plugin')


class HarnessError(Exception):
    pass


# ---------------------------------------------------------------------------
# logcat parsing (pure; covered by the fixture tests)
# ---------------------------------------------------------------------------

LogEntry = namedtuple('LogEntry', 'time pid tid level tag message')

_THREADTIME = re.compile(r'^(\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.*?)\s*: ?(.*)$')
_BRIEF = re.compile(r'^([VDIWEFA])/(.*?)\s*\(\s*(\d+)\): ?(.*)$')


def parse_logcat(text):
    """Entries of a `logcat -v threadtime` (or brief) dump. A line without a header
    continues the entry before it."""
    entries = []
    for line in text.splitlines():
        line = line.rstrip('\r')
        m = _THREADTIME.match(line)
        if m:
            entries.append(LogEntry(m.group(1), int(m.group(2)), int(m.group(3)), m.group(4), m.group(5), m.group(6)))
            continue
        m = _BRIEF.match(line)
        if m:
            pid = int(m.group(3))
            entries.append(LogEntry('', pid, pid, m.group(1), m.group(2), m.group(4)))
            continue
        if not line.strip() or line.startswith('--------- beginning of'):
            continue
        if entries:
            entries.append(entries[-1]._replace(message=line))
    return entries


def filter_pids(text, pids):
    """The lines of a full logcat dump written by [pids]; header-less lines follow the
    line before them. Filtering after the fact keeps the lines of a process that died
    mid-plugin, which `logcat --pid` of the new process would drop."""
    keep = []
    keeping = False
    for line in text.splitlines():
        m = _THREADTIME.match(line) or _BRIEF.match(line)
        if m:
            pid = int(m.group(2) if m.re is _THREADTIME else m.group(3))
            keeping = pid in pids
        if keeping:
            keep.append(line)
    return '\n'.join(keep) + ('\n' if keep else '')


_HUB = re.compile(r'^HUB (\w+) internal=(\S+)(?: (.*))?$')


def hub_events(entries):
    """PluginInstallTracker's `HUB <event> internal=<name> ...` lines, in order."""
    out = []
    for e in entries:
        if e.tag != STATUS_TAG:
            continue
        m = _HUB.match(e.message.strip())
        if not m:
            continue
        ev = {'event': m.group(1), 'internal': m.group(2)}
        rest = m.group(3) or ''
        if ev['event'] == 'loaded':
            names = rest[len('plugins='):] if rest.startswith('plugins=') else ''
            ev['plugins'] = [n for n in names.split('|') if n]
        elif ev['event'] == 'failed':
            fm = re.match(r'stage=(\S+)(?: message=(.*))?$', rest)
            ev['stage'] = fm.group(1) if fm else ''
            ev['message'] = (fm.group(2) or '') if fm else rest
        else:
            for k, v in re.findall(r'(\w+)=(\S+)', rest):
                ev[k] = v
        out.append(ev)
    return out


def install_outcome(events, internal):
    """(install, message, plugin names, start failure) for one internal name. install is
    'ok', 'failed' or 'pending'; a START-stage failure installed fine but threw in
    startUp. The last event wins, so a retry that loaded supersedes an earlier failure."""
    install, message, plugins, start = 'pending', '', [], None
    for e in events:
        if e['internal'] != internal:
            continue
        if e['event'] == 'loaded':
            install, message, plugins, start = 'ok', '', e.get('plugins', []), None
        elif e['event'] == 'failed':
            if e.get('stage') == 'START':
                install, message, start = 'ok', '', e.get('message') or 'failed to start'
            else:
                install, message = 'failed', '%s: %s' % (e.get('stage'), e.get('message'))
    return install, message, plugins, start


def hub_errors(events):
    """Hub-wide failures (manifest unreachable / bad signature) logged as internal=-."""
    return [e.get('message', '') for e in events if e['event'] == 'failed' and e['internal'] == '-']


def start_outcome(entries, start_failure, enabled):
    """('ok'|'failed'|'unknown', message). [enabled] maps plugin name -> toggle state
    after the harness turned it on, None for a plugin with no toggle in the list."""
    if start_failure:
        return 'failed', start_failure
    for e in entries:
        if any(marker in e.message for marker in START_FAILURES):
            return 'failed', e.message.strip()[:300]
    states = list((enabled or {}).values())
    if not states or any(s is None for s in states):
        return 'unknown', 'no toggle in the Plugins tab'
    if not all(states):
        return 'failed', 'toggle did not stay on: ' + ', '.join(n for n, s in enabled.items() if not s)
    return 'ok', ''


_HEAD = re.compile(r'^\s*(?:(Caused by|Suppressed): )?((?:[A-Za-z_$][\w$]*\.)+[A-Za-z_$][\w$]*)(?:: (.*))?$')
_FRAME = re.compile(r'^\s*at ([\w$.<>-]+)\(')
_MORE = re.compile(r'^\s*\.\.\. \d+ (?:more|common frames omitted)')
_INLINE = re.compile(r'((?:java|javax|dalvik)\.[\w.$]*\.(?:' + '|'.join(LINKAGE_ERRORS) + r'))(?:: (.*))?')


def _throwable_class(name):
    return name.rsplit('.', 1)[-1].endswith(('Exception', 'Error', 'Throwable'))


def throwables(entries):
    """Exception chains in the log, Caused by / Suppressed folded into the throwable they
    belong to. The lines of one log call share pid, tid and tag, which is what keeps two
    threads' traces apart. A linkage error only mentioned inside a message (ART's
    "Rejecting re-init on previously-failed class ...: java.lang.NoClassDefFoundError")
    becomes a frameless chain of its own."""
    blocks = []
    current = None
    context = ''
    key = None
    for e in entries:
        k = (e.pid, e.tid, e.tag)
        if k != key:
            current, key = None, k
        msg = e.message
        fm = _FRAME.match(msg)
        if fm:
            if current is not None:
                current['chain'][-1]['frames'].append(fm.group(1))
            continue
        if current is not None and _MORE.match(msg):
            continue
        hm = _HEAD.match(msg)
        if hm and _throwable_class(hm.group(2)):
            link = {'class': hm.group(2), 'message': (hm.group(3) or '').strip(), 'frames': []}
            if hm.group(1) and current is not None:
                current['chain'].append(link)
            else:
                current = {'chain': [link], 'context': context, 'tag': e.tag, 'inline': False}
                blocks.append(current)
            continue
        current = None
        im = _INLINE.search(msg)
        if im:
            blocks.append({'chain': [{'class': im.group(1), 'message': (im.group(2) or '').strip(), 'frames': []}],
                           'context': msg.strip(), 'tag': e.tag, 'inline': True})
        context = msg.strip()
    return blocks


def _record(kind, link, frame, block):
    return {'kind': kind, 'exception': link['class'], 'message': link['message'][:300], 'frame': frame,
            'context': block['context'][:300]}


def classify(block, packages=()):
    """Error records one chain yields: each linkage error in it, an
    UnsupportedOperationException thrown by the shadow surface, or, failing both, a
    'plugin-trace' when any frame runs through the plugin's own [packages]."""
    found = []
    for link in block['chain']:
        simple = link['class'].rsplit('.', 1)[-1]
        top = link['frames'][0] if link['frames'] else ''
        if simple in LINKAGE_ERRORS:
            found.append(_record(simple, link, top, block))
        elif simple == 'UnsupportedOperationException' and (
                top.startswith(SHADOW_PREFIXES) or any(f.startswith('net.runelite.awt.') for f in link['frames'])):
            found.append(_record(simple, link, top, block))
    if not found and packages:
        own = [f for link in block['chain'] for f in link['frames'] if f.startswith(tuple(packages))]
        if own:
            found.append(_record('plugin-trace', block['chain'][-1], own[0], block))
    return found


def collect_errors(entries, packages=()):
    """Deduplicated error records of a plugin's log window, each with its count."""
    seen = {}

    def add(rec):
        k = (rec['kind'], rec['exception'], rec['message'], rec['frame'])
        if k in seen:
            seen[k]['count'] += 1
        else:
            rec['count'] = 1
            seen[k] = rec

    for block in throwables(entries):
        for rec in classify(block, packages):
            add(rec)
    for i, e in enumerate(entries):
        if e.tag == 'AndroidRuntime' and e.message.startswith('FATAL EXCEPTION'):
            nxt = [x.message for x in entries[i + 1:i + 4] if x.tag == 'AndroidRuntime']
            add({'kind': 'crash', 'exception': '', 'message': ' / '.join([e.message] + nxt)[:300], 'frame': '',
                 'context': ''})
    return list(seen.values())


def plugin_packages(jar_path):
    """'pkg.' prefixes of the plugin classes a hub jar declares in runelite_plugin.json."""
    try:
        with zipfile.ZipFile(jar_path) as z:
            stub = json.loads(z.read('runelite_plugin.json').decode('utf8'))
    except (OSError, KeyError, ValueError, zipfile.BadZipFile):
        return ()
    return tuple(sorted(set(c.rsplit('.', 1)[0] + '.' for c in stub.get('plugins') or [] if '.' in c)))


def search_term(name):
    """The longest alphanumeric run of a display name. The hub and plugin searches match
    substrings, and this is what `adb shell input text` can type without shell quoting."""
    runs = re.findall(r'[A-Za-z0-9]+(?: [A-Za-z0-9]+)*', name)
    return max(runs, key=len) if runs else ''


# ---------------------------------------------------------------------------
# uiautomator node queries (pure)
# ---------------------------------------------------------------------------

def parse_nodes(xml_text):
    return [dict(n.attrib) for n in ET.fromstring(xml_text).iter('node')]


def bounds(n):
    x1, y1, x2, y2 = map(int, re.findall(r'-?\d+', n.get('bounds', '[0,0][0,0]')))
    return x1, y1, x2, y2


def center(n):
    x1, y1, x2, y2 = bounds(n)
    return (x1 + x2) // 2, (y1 + y2) // 2


def has_text(n, text):
    """Compose merges a clickable's child texts into one node, joined with ', '."""
    t = n.get('text', '')
    return t == text or text in [p.strip() for p in t.split(',')]


def find_text(nodes, text):
    return next((n for n in nodes if has_text(n, text)), None)


def find_row_button(nodes, display_name, labels=('Install', 'Remove', 'Unavailable')):
    """(label, node) of the action button on the hub row titled [display_name]: the
    button right of a matching title, nearest to it vertically. The activity card above
    the list repeats the name but has no Install/Remove buttons."""
    names = [n for n in nodes if n.get('text') == display_name]
    buttons = [n for n in nodes if n.get('text') in labels]
    best = None
    for name in names:
        nx1, _, _, _ = bounds(name)
        _, ny = center(name)
        for b in buttons:
            bx, by = center(b)
            if bx < nx1:
                continue
            d = abs(by - ny)
            if best is None or d < best[0]:
                best = (d, b)
    return (best[1].get('text'), best[1]) if best else None


def find_toggle(nodes, plugin_name):
    """The Plugins-tab switch for [plugin_name] (Sidebar.kt gives it the plugin's name as
    its content description and a checked state)."""
    return next((n for n in nodes if n.get('checkable') == 'true' and n.get('content-desc') == plugin_name), None)


def nav_entries(nodes):
    """Tooltips of the icon strip's navigation buttons: clickable nodes with a content
    description, less the plugin switches. Buttons whose icon failed to convert show a
    letter instead and are not seen."""
    return set(n.get('content-desc') for n in nodes
               if n.get('clickable') == 'true' and n.get('checkable') != 'true' and n.get('content-desc'))


def is_plugins_icon(n):
    return n.get('clickable') == 'true' and n.get('content-desc') == 'Plugins'


# ---------------------------------------------------------------------------
# status files and the matrix (pure)
# ---------------------------------------------------------------------------

def root_relative(path, root=ROOT):
    """Paths under the checkout are stored relative to it, so the JSON doesn't carry the
    person's home directory into the committed matrix."""
    try:
        rel = os.path.relpath(os.path.abspath(path), os.path.abspath(root))
    except ValueError:  # another drive on Windows
        return path
    return path if rel.startswith('..') else rel.replace(os.sep, '/')


def write_status(out_dir, status):
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, status['internalName'] + '.json')
    with open(path, 'w', encoding='utf8') as fh:
        json.dump(status, fh, indent=2, sort_keys=True)
    return path


def load_statuses(out_dir):
    out = []
    for path in sorted(glob.glob(os.path.join(out_dir, '*.json'))):
        if os.path.basename(path) == 'run.json':
            continue
        with open(path, encoding='utf8') as fh:
            out.append(json.load(fh))
    return out


def _cell(s, limit=90):
    s = ' '.join(str(s or '').split())
    if len(s) > limit:
        s = s[:limit - 1] + '…'
    return s.replace('|', '\\|')


_MARK = {'ok': '✓', 'failed': '✕', 'timeout': 'timeout', 'pending': 'timeout', 'unavailable': 'unavailable',
         'skipped': '—', 'unknown': '?', 'not-run': '—'}


def _outcome(state, message):
    mark = _MARK.get(state, state or '—')
    return _cell(mark + (' ' + message if message and state != 'ok' else ''))


def _link(path, doc_dir, root):
    if not path:
        return '—'
    target = path if os.path.isabs(path) else os.path.join(root, path)
    try:
        rel = os.path.relpath(target, doc_dir).replace(os.sep, '/')
    except ValueError:
        rel = target.replace(os.sep, '/')
    return '[png](%s)' % rel


def render_markdown(statuses, doc_path, meta=None, root=ROOT):
    """docs/hub-status.md: one row per plugin, most-installed first, then each plugin's
    distinct errors."""
    meta = meta or {}
    rows = sorted(statuses, key=lambda s: (-(s.get('installs') or 0), s.get('internalName', '')))
    doc_dir = os.path.dirname(os.path.abspath(doc_path))
    installed = sum(1 for s in rows if s.get('install') == 'ok')
    started = sum(1 for s in rows if s.get('start') == 'ok')
    with_errors = sum(1 for s in rows if s.get('errors'))
    lines = [
        '# Plugin Hub status on Android',
        '',
        'Generated by `tools-src/test-hub-plugins.py` on an emulator; re-run it rather than editing this file.',
        '',
    ]
    facts = [('Hub', meta.get('hubVersion')), ('Device', meta.get('device')), ('Run', meta.get('started'))]
    facts = ['%s: %s' % (k, v) for k, v in facts if v]
    if facts:
        lines += [' · '.join(facts), '']
    lines += [
        '%d plugins: %d installed, %d started, %d with errors.' % (len(rows), installed, started, with_errors),
        '',
        '| Plugin | Version | Installs | Install | Start | Panel | Errors | Screenshot |',
        '|---|---|---:|---|---|---|---|---|',
    ]
    for s in rows:
        kinds = {}
        for e in s.get('errors') or []:
            kinds[e['kind']] = kinds.get(e['kind'], 0) + e.get('count', 1)
        errors = ', '.join('%s ×%d' % (k, n) for k, n in sorted(kinds.items())) or '—'
        if s.get('harness'):
            errors += ' (harness: %s)' % s['harness']
        panel = s.get('panel') or '—'
        if s.get('panelName'):
            panel += ': ' + s['panelName']
        lines.append('| %s | %s | %s | %s | %s | %s | %s | %s |' % (
            _cell('%s (`%s`)' % (s.get('displayName') or s['internalName'], s['internalName']), 120),
            _cell(s.get('version') or '—'),
            s['installs'] if s.get('installs') is not None else '—',
            _outcome(s.get('install'), s.get('installMessage')),
            _outcome(s.get('start'), s.get('startMessage')),
            _cell(panel),
            _cell(errors, 160),
            _link(s.get('screenshot'), doc_dir, root),
        ))
    detailed = [s for s in rows if s.get('errors')]
    if detailed:
        lines += ['', '## Errors', '']
        for s in detailed:
            lines.append('### %s' % (s.get('displayName') or s['internalName']))
            lines.append('')
            for e in s['errors']:
                where = ' at `%s`' % e['frame'] if e.get('frame') else ''
                what = e['exception'] + (': ' + e['message'] if e.get('message') else '') if e.get('exception') \
                    else e.get('message', '')
                lines.append('- **%s** ×%d: %s%s' % (e['kind'], e.get('count', 1), _cell(what, 240), where))
            lines.append('')
    os.makedirs(doc_dir, exist_ok=True)
    with open(doc_path, 'w', encoding='utf8', newline='\n') as fh:
        fh.write('\n'.join(lines).rstrip('\n') + '\n')
    return doc_path


# ---------------------------------------------------------------------------
# the device
# ---------------------------------------------------------------------------

def wait(function, timeout, what):
    deadline = time.monotonic() + timeout
    while True:
        value = function()
        if value:
            return value
        if time.monotonic() >= deadline:
            raise HarnessError('timed out after %ds waiting for %s' % (timeout, what))
        time.sleep(.5)


class Device(object):
    def __init__(self, adb, serial, package):
        self.adb, self.serial, self.package = adb, serial, package

    def run(self, *args, timeout=60, check=True):
        return subprocess.run([self.adb, '-s', self.serial] + list(args), check=check, capture_output=True,
                              text=True, encoding='utf-8', errors='replace', timeout=timeout).stdout

    def shell(self, *args, **kw):
        return self.run('shell', *args, **kw)

    def nodes(self):
        try:
            if 'dumped to' not in self.shell('uiautomator', 'dump', '/sdcard/hub-harness-ui.xml', timeout=30):
                return []
            return parse_nodes(self.shell('cat', '/sdcard/hub-harness-ui.xml', timeout=30))
        except (subprocess.SubprocessError, ET.ParseError):
            # Dumps fail while the UI is mid-animation; callers poll.
            return []

    def tap(self, n):
        x, y = center(n)
        self.shell('input', 'tap', str(x), str(y))

    def type_into(self, field, term):
        self.tap(field)
        # ADB can inject keys before the newly focused IME connection is ready.
        time.sleep(1)
        self.shell('input', 'keycombination', '113', '29')  # Ctrl+A
        time.sleep(.2)
        self.shell('input', 'keyevent', 'KEYCODE_DEL')
        if term:
            self.shell('input', 'text', term.replace(' ', '%s'))
        time.sleep(1)

    def pid(self):
        try:
            out = self.shell('pidof', self.package, timeout=15).split()
        except subprocess.SubprocessError:
            return None
        return int(out[0]) if out else None

    def logcat(self, pids):
        return filter_pids(self.run('logcat', '-d', '-v', 'threadtime', timeout=120), set(p for p in pids if p))

    def screenshot(self, path):
        data = subprocess.run([self.adb, '-s', self.serial, 'exec-out', 'screencap', '-p'], check=True,
                              capture_output=True, timeout=60).stdout
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        with open(path, 'wb') as fh:
            fh.write(data)


def launch(dev, args):
    dev.shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    command = ['am', 'start', '-W'] + ([] if args.no_restart else ['-S']) + ['-n', args.package + '/' + args.activity]
    out = dev.shell(*command, timeout=180)
    # Slow emulator launches can outlast ActivityManager's own wait window.
    if 'Status: ok' not in out and 'Status: timeout' not in out:
        raise HarnessError('launch failed: ' + out.strip())
    tapped = []

    def ready():
        nodes = dev.nodes()
        if any(is_plugins_icon(n) for n in nodes):
            return True
        if not tapped:
            button = find_text(nodes, args.launch_text)
            if button is not None:
                dev.tap(button)
                tapped.append(button)
        return False

    wait(ready, args.boot_timeout, 'the client\'s Plugins icon (the launcher needs a saved account to tap "%s" on)'
         % args.launch_text)


def open_tab(dev, label):
    """Show the sidebar tab [label]; the Plugins icon opens the sidebar when another panel
    (or none) is showing, and closes it when the sidebar is already up, hence the check."""
    def step():
        nodes = dev.nodes()
        tab = next((n for n in nodes if n.get('text') == label and n.get('clickable') == 'true'), None)
        if tab is not None:
            dev.tap(tab)
            return True
        icon = next((n for n in nodes if is_plugins_icon(n)), None)
        if icon is not None:
            dev.tap(icon)
            time.sleep(1)
        return False

    wait(step, 30, 'the %s tab' % label)


def search(dev, term):
    field = wait(lambda: next((n for n in dev.nodes() if n.get('class') == 'android.widget.EditText'), None),
                 30, 'the search field')
    dev.type_into(field, term)


def hub_row(dev, name, timeout):
    open_tab(dev, 'External')
    search(dev, search_term(name))
    # The first open of the tab downloads the manifest ("Loading manifest…").
    return wait(lambda: find_row_button(dev.nodes(), name), timeout, 'the hub row for "%s"' % name)


def install_from_hub(dev, name, timeout):
    """'requested', 'installed-before' or 'unavailable'."""
    label, button = hub_row(dev, name, timeout)
    if label == 'Unavailable':
        return 'unavailable'
    if label == 'Remove':
        return 'installed-before'
    dev.tap(button)
    title = 'Install %s?' % name

    def confirm():
        nodes = dev.nodes()
        if find_text(nodes, title) is None:
            return False
        candidates = [n for n in nodes if n.get('text') == 'Install']
        dialog = [n for n in candidates if n.get('bounds') != button.get('bounds')] or candidates
        if not dialog:
            return False
        dev.tap(dialog[-1])
        return True

    wait(confirm, 20, 'the install confirmation')
    return 'requested'


def remove_from_hub(dev, name):
    label, button = hub_row(dev, name, 60)
    if label != 'Remove':
        return label == 'Install'
    dev.tap(button)
    try:
        wait(lambda: (find_row_button(dev.nodes(), name) or ('', None))[0] == 'Install', 60, 'the removal')
        return True
    except HarnessError:
        return False


def enable_plugins(dev, names, settle):
    """Turn each plugin on in the Plugins tab; name -> toggle state afterwards, None when
    the plugin has no toggle there (hidden, or it didn't register)."""
    states = {}
    for name in names:
        open_tab(dev, 'Plugins')
        search(dev, search_term(name))
        try:
            toggle = wait(lambda: find_toggle(dev.nodes(), name), 15, 'the toggle for "%s"' % name)
        except HarnessError:
            states[name] = None
            continue
        if toggle.get('checked') != 'true':
            dev.tap(toggle)
            time.sleep(settle)
            toggle = find_toggle(dev.nodes(), name)
        states[name] = toggle is not None and toggle.get('checked') == 'true'
    return states


def run_plugin(dev, args, item):
    internal, name = item['internalName'], item['displayName']
    status = {
        'internalName': internal, 'displayName': name, 'version': item.get('version'),
        'installs': item.get('installs'), 'install': 'not-run', 'installMessage': '', 'plugins': [],
        'start': 'not-run', 'startMessage': '', 'enabled': {}, 'panel': 'not-run', 'panelName': '',
        'errors': [], 'screenshot': None, 'log': None, 'harness': None, 'removed': None,
        'at': datetime.datetime.now().isoformat(timespec='seconds'),
    }
    started = time.monotonic()
    dev.run('logcat', '-c', check=False)
    pids = [dev.pid()]
    shot = os.path.join(args.screenshot_dir, internal + '.png')
    start_failure = None
    try:
        nav_before = nav_entries(dev.nodes())
        state = install_from_hub(dev, name, args.manifest_timeout)
        if state == 'unavailable':
            status['install'] = 'unavailable'
            status['start'] = status['panel'] = 'skipped'
            return status
        if state == 'requested':
            def settled():
                pid = dev.pid()
                if pid is None:
                    raise HarnessError('the app process died while installing')
                return install_outcome(hub_events(parse_logcat(dev.logcat(pids + [pid]))), internal)[0] != 'pending'
            try:
                wait(settled, args.install_timeout, 'RlHubStatus loaded/failed for ' + internal)
            except HarnessError as e:
                if 'process died' in str(e):
                    raise
            install, message, plugins, start_failure = install_outcome(
                hub_events(parse_logcat(dev.logcat(pids + [dev.pid()]))), internal)
            status['install'] = 'timeout' if install == 'pending' else install
            status['installMessage'] = message
        else:
            # Installed by an earlier run; nothing reloads, so the names are a guess.
            status['install'], status['installMessage'] = 'ok', 'installed before this run'
            plugins = [name]
        status['plugins'] = plugins
        if status['install'] != 'ok':
            status['start'] = status['panel'] = 'skipped'
            return status
        if not start_failure:
            status['enabled'] = enable_plugins(dev, plugins or [name], args.settle)
        added = sorted(nav_entries(dev.nodes()) - nav_before)
        if added:
            target = next((n for n in dev.nodes() if n.get('clickable') == 'true' and
                           n.get('content-desc') == added[0]), None)
            if target is not None:
                dev.tap(target)
                time.sleep(args.settle)
                status['panel'], status['panelName'] = 'opened', added[0]
            else:
                status['panel'] = 'none'
        else:
            status['panel'] = 'none'
        dev.screenshot(shot)
        status['screenshot'] = root_relative(shot)
    except (HarnessError, subprocess.SubprocessError) as e:
        status['harness'] = str(e).splitlines()[0][:300] if str(e) else type(e).__name__
    finally:
        pids.append(dev.pid())
        text = dev.logcat(pids)
        log = os.path.join(args.out_dir, internal + '.log')
        os.makedirs(args.out_dir, exist_ok=True)
        with open(log, 'w', encoding='utf8') as fh:
            fh.write(text)
        status['log'] = root_relative(log)
        entries = parse_logcat(text)
        status['errors'] = collect_errors(entries, item.get('packages') or ())
        if status['install'] == 'ok':
            status['start'], status['startMessage'] = start_outcome(entries, start_failure, status['enabled'])
        if not args.keep_installed and status['install'] in ('ok', 'failed', 'timeout') and dev.pid():
            try:
                status['removed'] = remove_from_hub(dev, name)
            except (HarnessError, subprocess.SubprocessError):
                status['removed'] = False
        status['seconds'] = round(time.monotonic() - started, 1)
    return status


# ---------------------------------------------------------------------------
# plugin selection
# ---------------------------------------------------------------------------

def load_scanner():
    spec = importlib.util.spec_from_file_location('hub_compat_scan', os.path.join(HERE, 'hub-compat-scan.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def select_plugins(scan, args):
    """(hub version, items) for the run: --plugin in the order given, else the top --limit
    by install count. Items carry the display name the UI shows and the plugin packages
    from the jar's runelite_plugin.json (for attributing plain stack traces)."""
    jar_dir = os.path.join(args.cache_dir, 'jar')
    if args.offline:
        cached = sorted(glob.glob(os.path.join(args.cache_dir, 'manifest-*_full.json')), key=os.path.getmtime)
        if not cached:
            raise SystemExit('--offline but no cached manifest under %s' % args.cache_dir)
        version = re.search(r'manifest-(.+)_full\.json$', os.path.basename(cached[-1])).group(1)
        with open(cached[-1], encoding='utf8') as fh:
            manifest = json.load(fh)
        counts_file = os.path.join(args.cache_dir, 'counts-%s.json' % version)
        counts = {}
        if os.path.exists(counts_file):
            with open(counts_file, encoding='utf8') as fh:
                counts = json.load(fh)
    else:
        version, manifest = scan.fetch_manifest(args.hub_url, args.hub_version or scan.project_version(),
                                                args.cache_dir, args.refresh)
        counts = scan.fetch_install_counts(args.api_url, version, args.cache_dir, args.refresh)
    display = dict((d['internalName'], d) for d in manifest.get('display') or [])
    jars = dict((j['internalName'], j) for j in manifest.get('jars') or [])
    if args.plugin:
        names = list(args.plugin)
    else:
        names = sorted(jars, key=lambda n: (-counts.get(n, 0), n))[:args.limit]
    items = []
    for n in names:
        if n not in jars:
            sys.stderr.write('warning: %s is not in the hub %s manifest\n' % (n, version))
            continue
        d = display.get(n, {})
        packages = ()
        path = os.path.join(jar_dir, scan.jar_file_name(jars[n]))
        try:
            if not args.offline:
                os.makedirs(jar_dir, exist_ok=True)
                path = scan.ensure_jar(args.hub_url, jars[n], jar_dir)[0]
            packages = plugin_packages(path)
        except Exception as e:
            sys.stderr.write('warning: no jar for %s (%s); plain traces will not be attributed\n' % (n, e))
        items.append({'internalName': n, 'displayName': d.get('displayName') or n, 'version': d.get('version'),
                      'installs': counts.get(n), 'packages': list(packages)})
    return version, items


# ---------------------------------------------------------------------------
# command line
# ---------------------------------------------------------------------------

def build_parser():
    p = argparse.ArgumentParser(
        prog='test-hub-plugins.py',
        description='Install, enable and screenshot Plugin Hub plugins on an emulator, collect their logcat '
                    'errors, and render the status matrix (docs/hub-status.md).',
        epilog='The launcher needs one saved account to tap Launch on; plugins are exercised at the login '
               'screen. Unit tests: python -m unittest discover -s tools-src -p test_hub_plugins_harness.py')
    p.add_argument('--adb', default='adb', help='adb executable (default: adb on PATH)')
    p.add_argument('--serial', help='emulator serial, emulator-NNNN (required unless --render-only); '
                                    'real devices are refused')
    p.add_argument('--apk', help='APK to install first with adb install -r (never uninstalls: that would wipe '
                                 'saved accounts); default: use what is installed')
    p.add_argument('--package', default='net.runelite.mp', help='application id (default: %(default)s)')
    p.add_argument('--activity', default='.MainActivity', help='launch activity (default: %(default)s)')
    p.add_argument('--launch-text', default='Launch',
                   help='launcher button tapped to start the client (default: %(default)s)')
    p.add_argument('--no-restart', action='store_true',
                   help='attach to a running app instead of force-stopping it before launch')
    p.add_argument('--limit', type=int, default=25,
                   help='number of most-installed plugins to run when --plugin is not given (default: %(default)s)')
    p.add_argument('--plugin', action='append', metavar='INTERNAL_NAME',
                   help='run this hub plugin (internal name, repeatable; overrides --limit, keeps the order)')
    p.add_argument('--hub-url', default='https://repo.runelite.net/plugins',
                   help='plugin hub base URL (default: %(default)s)')
    p.add_argument('--hub-version', help='hub manifest version (default: project.build.version in gradle.properties)')
    p.add_argument('--api-url', default='https://api.runelite.net',
                   help='API base for install counts (default: %(default)s)')
    p.add_argument('--cache-dir', default=os.path.join(ROOT, 'build', 'hub-cache'),
                   help='manifest/count/jar cache shared with hub-compat-scan.py (default: build/hub-cache)')
    p.add_argument('--offline', action='store_true', help='use the cached manifest, counts and jars only')
    p.add_argument('--refresh', action='store_true', help='re-download the manifest and install counts')
    p.add_argument('--out-dir', default=os.path.join(ROOT, 'build', 'hub-status'),
                   help='per-plugin <internalName>.json/.log and run.json (default: build/hub-status)')
    p.add_argument('--doc', default=os.path.join(ROOT, 'docs', 'hub-status.md'),
                   help='rendered status matrix (default: docs/hub-status.md)')
    p.add_argument('--screenshot-dir', default=os.path.join(ROOT, 'docs', 'hub-status'),
                   help='where <internalName>.png screenshots go (default: docs/hub-status, next to the matrix)')
    p.add_argument('--boot-timeout', type=int, default=900,
                   help='seconds to wait for the client after launch; the first boot prepares the client '
                        '(default: %(default)s)')
    p.add_argument('--manifest-timeout', type=int, default=180,
                   help='seconds to wait for a plugin\'s row in the External tab (default: %(default)s)')
    p.add_argument('--install-timeout', type=int, default=300,
                   help='seconds to wait for download + on-device dex + load of one plugin (default: %(default)s)')
    p.add_argument('--settle', type=float, default=6,
                   help='seconds to let startUp and a panel run before reading state (default: %(default)s)')
    p.add_argument('--keep-installed', action='store_true',
                   help='leave each plugin installed instead of removing it after its run')
    p.add_argument('--skip-existing', action='store_true',
                   help='skip plugins that already have a JSON in --out-dir (resume an interrupted run)')
    p.add_argument('--render-only', action='store_true',
                   help='only re-render --doc from the JSON files in --out-dir; needs no emulator')
    return p


def _read_meta(out_dir):
    path = os.path.join(out_dir, 'run.json')
    if not os.path.exists(path):
        return {}
    with open(path, encoding='utf8') as fh:
        return json.load(fh)


def main(argv=None):
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.render_only:
        doc = render_markdown(load_statuses(args.out_dir), args.doc, _read_meta(args.out_dir))
        print('rendered', doc)
        return 0
    if not args.serial or not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('--serial emulator-NNNN is required; this harness only targets an emulator')

    version, items = select_plugins(load_scanner(), args)
    if not items:
        raise SystemExit('no plugins selected')
    dev = Device(args.adb, args.serial, args.package)
    if args.apk:
        print('installing', args.apk, flush=True)
        dev.run('install', '-r', args.apk, timeout=900)
    dev.run('logcat', '-G', '16M', check=False)
    launch(dev, args)
    model = dev.shell('getprop', 'ro.product.model', check=False).strip()
    release = dev.shell('getprop', 'ro.build.version.release', check=False).strip()
    abi = dev.shell('getprop', 'ro.product.cpu.abi', check=False).strip()
    meta = {'hubVersion': version, 'device': '%s, Android %s, %s' % (model, release, abi),
            'started': datetime.datetime.now().isoformat(timespec='seconds'), 'serial': args.serial,
            'plugins': [i['internalName'] for i in items]}
    os.makedirs(args.out_dir, exist_ok=True)
    with open(os.path.join(args.out_dir, 'run.json'), 'w', encoding='utf8') as fh:
        json.dump(meta, fh, indent=2)

    for i, item in enumerate(items, 1):
        if args.skip_existing and os.path.exists(os.path.join(args.out_dir, item['internalName'] + '.json')):
            continue
        if dev.pid() is None:
            print('app is not running; relaunching', flush=True)
            launch(dev, args)
        status = run_plugin(dev, args, item)
        write_status(args.out_dir, status)
        render_markdown(load_statuses(args.out_dir), args.doc, meta)
        print('[%d/%d] %-32s install=%s start=%s panel=%s errors=%d%s (%.0fs)' % (
            i, len(items), item['internalName'], status['install'], status['start'], status['panel'],
            sum(e['count'] for e in status['errors']),
            ' harness: ' + status['harness'] if status['harness'] else '', status.get('seconds', 0)), flush=True)
    print('rendered', args.doc)
    return 0


if __name__ == '__main__':
    sys.exit(main())
