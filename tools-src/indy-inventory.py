#!/usr/bin/env python3
"""Inventory the invokedynamic and dynamic-constant bootstraps in class files and jars.

Each kind is ``Owner.method`` of the bootstrap (``LambdaMetafactory.metafactory``,
``SwitchBootstraps.typeSwitch``, ...), refined where the lowering differs: altMetafactory carries
its flags (``[serializable,markers,bridges]``), makeConcatWithConstants ``[constants]`` when it has
bootstrap constants after the recipe. Dynamic constants are ``condy Owner.method`` when loaded by
``ldc`` and ``condy-arg Owner.method`` when they are another bootstrap's static argument (the
EnumDesc labels of a typeSwitch). Counts are constant-pool entries, i.e. distinct call sites per
class.

    python tools-src/indy-inventory.py                    # build/hub-cache jars + the injected client
    python tools-src/indy-inventory.py some.jar dir/ --json out.json

test-runtime-plugin-dex.py imports this module to check its fixtures and D8's output.
"""
import argparse
import collections
import json
import os
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ALT_FLAGS = ((1, 'serializable'), (2, 'markers'), (4, 'bridges'))


def class_bootstraps(data):
    """Counter of bootstrap kinds used by one class file."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise ValueError('not a class file')
    pos = 8
    count = struct.unpack_from('>H', data, pos)[0]
    pos += 2
    pool = [None] * count
    k = 1
    while k < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from('>H', data, pos)[0]
            pool[k] = (1, data[pos + 2:pos + 2 + length].decode('utf8', 'replace'))
            pos += 2 + length
        elif tag == 3:
            pool[k] = (3, struct.unpack_from('>i', data, pos)[0])
            pos += 4
        elif tag == 4:
            pos += 4
        elif tag in (5, 6):
            pos += 8
            k += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[k] = (tag, struct.unpack_from('>H', data, pos)[0])
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[k] = (tag,) + struct.unpack_from('>HH', data, pos)
            pos += 4
        elif tag == 15:
            pool[k] = (15, data[pos], struct.unpack_from('>H', data, pos + 1)[0])
            pos += 3
        else:
            raise ValueError('bad constant tag %d' % tag)
        k += 1

    dynamic = [(i, e) for i, e in enumerate(pool) if e and e[0] in (17, 18)]
    if not dynamic:
        return collections.Counter()

    pos += 6  # access, this, super
    pos += 2 + 2 * struct.unpack_from('>H', data, pos)[0]
    for _ in range(2):  # fields, methods
        members = struct.unpack_from('>H', data, pos)[0]
        pos += 2
        for _ in range(members):
            pos += 6
            attributes = struct.unpack_from('>H', data, pos)[0]
            pos += 2
            for _ in range(attributes):
                pos += 6 + struct.unpack_from('>I', data, pos + 2)[0]
    bootstraps = []
    attributes = struct.unpack_from('>H', data, pos)[0]
    pos += 2
    for _ in range(attributes):
        name, length = struct.unpack_from('>HI', data, pos)
        start = pos + 6
        pos = start + length
        if pool[name][1] != 'BootstrapMethods':
            continue
        p = start + 2
        for _ in range(struct.unpack_from('>H', data, start)[0]):
            handle, argc = struct.unpack_from('>HH', data, p)
            bootstraps.append((handle, struct.unpack_from('>%dH' % argc, data, p + 4) if argc else ()))
            p += 4 + 2 * argc

    def utf(i):
        return pool[i][1]

    nested = {a for _, args in bootstraps for a in args}
    kinds = collections.Counter()
    for index, (tag, bsm_index, _) in dynamic:
        handle, args = bootstraps[bsm_index]
        _, _, ref = pool[handle]
        _, class_index, nat = pool[ref]
        owner = utf(pool[class_index][1])
        method = utf(pool[nat][1])
        kind = owner.rsplit('/', 1)[-1] + '.' + method
        if owner == 'java/lang/invoke/LambdaMetafactory' and method == 'altMetafactory' and len(args) > 3:
            flags = pool[args[3]][1] if pool[args[3]] and pool[args[3]][0] == 3 else 0
            names = [n for bit, n in ALT_FLAGS if flags & bit]
            if names:
                kind += '[' + ','.join(names) + ']'
        elif owner == 'java/lang/invoke/StringConcatFactory' and method == 'makeConcatWithConstants' and len(args) > 1:
            kind += '[constants]'
        if tag == 17:
            kind = ('condy-arg ' if index in nested else 'condy ') + kind
        kinds[kind] += 1
    return kinds


def covered(kinds, want):
    """True when ``want`` is in ``kinds``; ``Owner.method[flag]`` matches any flag combination holding it."""
    if '[' not in want:
        return want in kinds
    base, flag = want[:-1].split('[', 1)
    return any(k.startswith(base + '[') and flag in k[len(base) + 1:-1].split(',') for k in kinds)


def jar_bootstraps(path):
    kinds = collections.Counter()
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if name.endswith('.class') and not name.endswith('module-info.class'):
                kinds.update(class_bootstraps(archive.read(name)))
    return kinds


def inventory(path):
    path = Path(path)
    if path.is_dir():
        kinds = collections.Counter()
        for file in sorted(path.rglob('*.class')):
            kinds.update(class_bootstraps(file.read_bytes()))
        return kinds
    if path.suffix == '.class':
        return class_bootstraps(path.read_bytes())
    return jar_bootstraps(path)


def client_jars():
    """The injected client the app downloads: data/*injected*.jar, else net.runelite:injected-client
    at gradle.properties' project.build.version from the Gradle cache (runelite-client depends on it)."""
    jars = sorted((ROOT / 'data').glob('*injected*.jar'))
    if jars:
        return jars
    version = None
    properties = ROOT / 'gradle.properties'
    if properties.is_file():
        for line in properties.read_text(encoding='utf-8').splitlines():
            if line.startswith('project.build.version='):
                version = line.split('=', 1)[1].strip()
    if not version:
        return []
    cache = Path.home() / '.gradle' / 'caches' / 'modules-2' / 'files-2.1' / 'net.runelite' / 'injected-client' / version
    return sorted(cache.rglob('injected-client-%s.jar' % version))


def default_inputs():
    hub = ROOT / 'build' / 'hub-cache'
    return ([hub] if hub.is_dir() else []) + client_jars()


def expand(inputs):
    """A directory stands for its loose class files (if any) and, one entry each, its jars."""
    result = []
    for path in inputs:
        if path.is_dir():
            if next(path.rglob('*.class'), None) is not None:
                result.append(path)
            result.extend(sorted(path.rglob('*.jar')))
        else:
            result.append(path)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('inputs', nargs='*', type=Path, help='jars, class files or class directories')
    parser.add_argument('--json', type=Path, help='write {kind: {count, jars: [...]}} here')
    args = parser.parse_args(argv)
    inputs = expand(args.inputs or default_inputs())
    if not inputs:
        raise SystemExit('nothing to scan: pass jars, or run hub-compat-scan.py to fill build/hub-cache')
    totals = collections.Counter()
    users = collections.defaultdict(list)
    for path in inputs:
        try:
            kinds = inventory(path)
        except (ValueError, zipfile.BadZipFile, struct.error) as e:
            print('skipped %s: %s' % (path, e), file=sys.stderr)
            continue
        totals.update(kinds)
        for kind in kinds:
            users[kind].append(path.name)
    print('%d inputs' % len(inputs))
    for kind, n in sorted(totals.items(), key=lambda item: (-item[1], item[0])):
        sample = ', '.join(sorted(users[kind])[:5])
        print('%7d  %-60s %4d jars  %s' % (n, kind, len(users[kind]), sample))
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps({k: {'count': totals[k], 'jars': sorted(users[k])} for k in totals}, indent=1))


if __name__ == '__main__':
    main()
