"""Sparse, blobless, depth-1 checkouts of OpenJDK 8u and 17u under third-party/ for porting reference.

Idempotent: re-running advances an existing checkout to the latest -ga tag and re-applies the
sparse set. Records the tags and commits in third-party/JDK-REFERENCE.md.
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
THIRD_PARTY = ROOT / 'third-party'

CHECKOUTS = [
    {
        'name': 'jdk8u',
        'url': 'https://github.com/openjdk/jdk8u',
        # jdk8u472-ga: the update number is the only moving part.
        'tag_pattern': re.compile(r'^jdk8u(\d+)-ga$'),
        'paths': [
            'jdk/src/share/classes/java/awt',
            'jdk/src/share/classes/java/beans',
            'jdk/src/share/classes/java/applet',
            'jdk/src/share/classes/javax/swing',
            'jdk/src/share/classes/javax/imageio',
            'jdk/src/share/classes/javax/sound',
            'jdk/src/share/classes/javax/accessibility',
            'jdk/src/share/classes/javax/print',
            'jdk/src/share/classes/sun/awt',
            'jdk/src/share/classes/sun/java2d',
            'jdk/src/share/classes/sun/font',
            'jdk/src/share/classes/sun/swing',
            'jdk/src/share/classes/com/sun/imageio',
            'jdk/src/share/classes/com/sun/media/sound',
            'jdk/src/share/native/sun/awt',
            'jdk/src/share/native/sun/java2d',
            'jdk/src/share/native/sun/font',
        ],
        'probe': 'jdk/src/share/classes/java/awt/Component.java',
    },
    {
        'name': 'jdk17u',
        'url': 'https://github.com/openjdk/jdk17u',
        # jdk-17.0.17-ga
        'tag_pattern': re.compile(r'^jdk-17\.(\d+)\.(\d+)-ga$'),
        'paths': [
            'src/java.desktop/share/classes',
            'src/java.net.http/share/classes',
        ],
        'probe': 'src/java.desktop/share/classes/java/awt/Taskbar.java',
    },
]

# Windows: the sparse trees stay short, but longpaths costs nothing and spares a surprise.
GIT = ['git', '-c', 'core.longpaths=true', '-c', 'advice.detachedHead=false']


def git(*args, cwd=None, capture=False):
    print('+ git ' + ' '.join(args), flush=True)
    result = subprocess.run(GIT + list(args), cwd=cwd, check=True, text=True,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout if capture else None


def latest_ga_tag(url, pattern):
    listing = git('ls-remote', '--tags', url, capture=True)
    # Annotated tags list twice; the `^{}` line carries the peeled commit, which is what HEAD holds.
    commits = {}
    for line in listing.splitlines():
        sha, _, ref = line.partition('\t')
        tag = ref[len('refs/tags/'):]
        if tag.endswith('^{}'):
            commits[tag[:-3]] = sha
        else:
            commits.setdefault(tag, sha)
    candidates = [(tuple(int(g) for g in m.groups()), tag)
                  for tag, m in ((t, pattern.match(t)) for t in commits) if m]
    if not candidates:
        raise SystemExit(f'no -ga tag found at {url}')
    _, tag = max(candidates)
    return tag, commits[tag]


def ensure_checkout(spec):
    dest = THIRD_PARTY / spec['name']
    tag, remote_commit = latest_ga_tag(spec['url'], spec['tag_pattern'])
    if (dest / '.git').exists():
        head = git('rev-parse', 'HEAD', cwd=dest, capture=True).strip()
        if head != remote_commit:
            git('fetch', '--depth', '1', '--filter=blob:none', 'origin', f'refs/tags/{tag}:refs/tags/{tag}', cwd=dest)
            git('checkout', '--detach', '-q', tag, cwd=dest)
    else:
        dest.parent.mkdir(parents=True, exist_ok=True)
        git('clone', '--filter=blob:none', '--sparse', '--depth', '1', '--branch', tag,
            spec['url'], str(dest))
    # `set` is idempotent in cone mode and heals a checkout whose sparse list drifted.
    git('sparse-checkout', 'set', '--cone', *spec['paths'], cwd=dest)
    commit = git('rev-parse', 'HEAD', cwd=dest, capture=True).strip()
    if commit != remote_commit:
        raise SystemExit(f'{spec["name"]}: HEAD {commit} is not {tag} ({remote_commit})')
    probe = dest / spec['probe']
    if not probe.is_file():
        raise SystemExit(f'{spec["name"]}: expected {probe} after sparse checkout')
    print(f'{spec["name"]}: {tag} @ {commit}')
    return tag, commit


def write_doc(results):
    lines = [
        '# OpenJDK reference checkouts',
        '',
        'Created by `python tools-src/fetch-jdk-reference.py` (or `tools-src/fetch-jdk-reference.ps1`).',
        'Both directories are gitignored; re-run the script to refresh them to the latest `-ga` tag.',
        '',
        '| Directory | Repository | Tag | Commit |',
        '|---|---|---|---|',
    ]
    for spec, (tag, commit) in zip(CHECKOUTS, results):
        lines.append(f'| `third-party/{spec["name"]}` | {spec["url"]} | `{tag}` | `{commit}` |')
    lines += [
        '',
        '## Sparse paths',
        '',
    ]
    for spec in CHECKOUTS:
        lines.append(f'`{spec["name"]}`:')
        lines.append('')
        lines += [f'- `{p}`' for p in spec['paths']]
        lines.append('')
    lines += [
        '## How the checkouts are used',
        '',
        '`jdk8u` is the faithful source for the public API the `android-awt` shadow surface ports:',
        'when a card ports a class, start from the OpenJDK 8u file, keep its public and protected',
        'signatures and Javadoc-visible behaviour, replace `sun.*` peer coupling with the module\'s own',
        '`net.runelite.awt` backend, and keep the OpenJDK license header on the ported file. The native',
        'sources under `jdk/src/share/native` document the behaviour of the C side the Java classes',
        'lean on (Java2D loops, font rasterization). `jdk17u` supplies the JDK 9+ members that RuneLite',
        'and Plugin Hub plugins use on top of the 8u surface (marked `// JDK 9+` in ported files) and',
        '`java.net.http` for reference.',
        '',
        'The checkouts are reference and porting source only. Nothing in them is compiled, dexed,',
        'linked or shipped as-is; only hand-ported files under `android-awt/src` enter the build.',
        '',
        '## License',
        '',
        'OpenJDK is Copyright (c) Oracle and/or its affiliates and contributors, licensed under the',
        'GNU General Public License, version 2, with the Classpath Exception (GPLv2+CE). Each checkout',
        'carries the full text in its `LICENSE` and `ASSEMBLY_EXCEPTION` files. Files ported from these',
        'trees into `android-awt` retain their original copyright and license header and are',
        'distributed under the same terms; the Classpath Exception is what permits linking them with',
        'the independently licensed code of this project.',
        '',
    ]
    (THIRD_PARTY / 'JDK-REFERENCE.md').write_text('\n'.join(lines), encoding='utf-8')


def main():
    results = [ensure_checkout(spec) for spec in CHECKOUTS]
    write_doc(results)
    print(f'recorded in {THIRD_PARTY / "JDK-REFERENCE.md"}')


if __name__ == '__main__':
    sys.exit(main())
