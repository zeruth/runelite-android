"""Report missing AWT/Swing symbols in one plugin JAR; this does not validate runtime behavior.

Single-jar front end over hub-compat-scan.py's parser and resolver, limited to java.awt
and javax.swing owners. For the whole Plugin Hub use hub-compat-scan.py.

Usage: python tools-src/check-swing-linkage.py PLUGIN.jar --classpath AWT.jar --classpath ANDROID.jar
"""
import argparse
import importlib.util
import json
import os
import pathlib

_spec = importlib.util.spec_from_file_location(
    'hub_compat_scan', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'hub-compat-scan.py'))
scan = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(scan)

PREFIXES = ('java/awt/', 'javax/swing/')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('plugin')
    parser.add_argument('--classpath', action='append', required=True, help='jar or class directory (repeatable)')
    parser.add_argument('--output')
    args = parser.parse_args()

    surface = scan.Surface(args.classpath)
    classes = scan.read_plugin_jar(args.plugin)
    refs = set()
    for c in classes:
        refs.update((o, n, d) for _, o, n, d in c.member_refs if o.startswith(PREFIXES))
    missing = set()
    missing_classes = set()
    for kind, owner, name, desc, _ in ((k[0], k[1], k[2], k[3], n)
                                       for k, n in scan.unresolved_references(surface, classes).items()):
        if not owner.startswith(PREFIXES):
            continue
        if kind == 'class':
            missing_classes.add(owner)
        else:
            missing.add((owner, name, desc))
    result = {
        'pluginClassesScanned': len(classes),
        'uniqueJavaMemberReferences': len(refs),
        'missingClasses': sorted(missing_classes),
        'missingMembers': sorted(missing),
    }
    text = json.dumps(result, indent=2)
    if args.output:
        pathlib.Path(args.output).write_text(text, encoding='utf8')
    print(text)


if __name__ == '__main__':
    main()
