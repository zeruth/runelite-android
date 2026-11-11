#!/usr/bin/env python3
"""Copy one OpenJDK class into android-awt/src/main/java and print the porting checklist.

    python tools-src/port-jdk-class.py java.awt.geom.QuadCurve2D [--from jdk8u|jdk17u] [--dry]

The file is copied byte-for-byte (nothing is rewritten); the checklist is the human's work list,
in the order android-awt/PORTING.md section 9 describes it. The reference trees come from
tools-src/fetch-jdk-reference.py.
"""
import argparse
import difflib
import os
import pathlib
import re
import shutil
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
SHADOW_ROOT = ROOT / "android-awt" / "src" / "main" / "java"
REFERENCE_ROOTS = {
    "jdk8u": [ROOT / "third-party" / "jdk8u" / "jdk" / "src" / "share" / "classes"],
    "jdk17u": [
        ROOT / "third-party" / "jdk17u" / "src" / "java.desktop" / "share" / "classes",
        ROOT / "third-party" / "jdk17u" / "src" / "java.net.http" / "share" / "classes",
    ],
}

# Used only when no android.jar can be found: the desktop-only package prefixes android-awt exists
# to supply. Anything else under java.*/javax.* is assumed present on the device.
DESKTOP_ONLY_PREFIXES = (
    "java.awt.", "java.applet.", "java.beans.", "javax.swing.", "javax.imageio.", "javax.sound.",
    "javax.accessibility.", "javax.print.", "sun.", "com.sun.", "jdk.internal.",
)
# JavaBeans classes Android does ship (API 26+); they must not be shadowed.
ANDROID_BEANS = {
    "java.beans.PropertyChangeEvent", "java.beans.PropertyChangeListener",
    "java.beans.PropertyChangeListenerProxy", "java.beans.PropertyChangeSupport",
    "java.beans.IndexedPropertyChangeEvent",
}

JDK9_PLUS_IMPORTS = {
    "java.io.Serial": "14", "java.lang.ProcessHandle": "9", "java.lang.StackWalker": "9",
    "java.lang.invoke.VarHandle": "9", "java.lang.ref.Cleaner": "9",
    "java.util.concurrent.Flow": "9", "java.net.http": "11", "jdk.internal": "9",
}
JDK9_PLUS_PATTERNS = [
    (re.compile(r"@Serial\b"), "@Serial annotation (14)"),
    (re.compile(r"\bvar\s+\w+\s*="), "local 'var' (10)"),
    (re.compile(r"^\s*(public\s+|private\s+|protected\s+|static\s+|final\s+)*record\s+\w+\s*\("), "record (16)"),
    (re.compile(r"\bcase\b[^:]*->"), "switch arrow / expression (14)"),
    (re.compile(r'"""'), "text block (15)"),
    (re.compile(r"\binstanceof\s+[\w.<>\[\]]+\s+\w+\s*[)&|;]"), "pattern instanceof (16)"),
    (re.compile(r"\b(List|Set|Map)\.(of|copyOf)\("), "List/Set/Map.of (9)"),
    (re.compile(r"\.(isBlank|strip|stripLeading|stripTrailing|repeat|lines)\("), "String 11+ method"),
    (re.compile(r"\bObjects\.(requireNonNullElse|requireNonNullElseGet|checkIndex)\("), "Objects 9+ method"),
    (re.compile(r"\bOptional\.(isEmpty|or|stream|ifPresentOrElse)\("), "Optional 9+ method"),
    (re.compile(r"\bMath\.(fma|multiplyHigh|floorMod\(long, ?int)"), "Math 9+ method"),
    (re.compile(r"\.(toArray\(\w+\[\]::new\)|readAllBytes\(|readNBytes\(|transferTo\()"), "9+ collection/stream method"),
    (re.compile(r"\bInputStream\.nullInputStream|\bOutputStream\.nullOutputStream"), "9+ stream factory"),
]

DECL_RE = re.compile(r"^\s*(public|protected)\b")
ANNOTATION_RE = re.compile(r"@\w+(\([^)]*\))?\s*")


def die(message):
    sys.exit("port-jdk-class: " + message)


def find_source(fqcn, origin):
    relative = pathlib.Path(*fqcn.split(".")).with_suffix(".java")
    roots = REFERENCE_ROOTS[origin]
    for root in roots:
        candidate = root / relative
        if candidate.is_file():
            return candidate, relative
    if not any(root.is_dir() for root in roots):
        die("%s is not checked out; run python tools-src/fetch-jdk-reference.py" % origin)
    die("%s not found under %s (nested classes live in their outer class's file)"
        % (fqcn, " or ".join(str(r.relative_to(ROOT)) for r in roots)))


def reference_revision(origin):
    notes = ROOT / "third-party" / "JDK-REFERENCE.md"
    if not notes.is_file():
        return "(third-party/JDK-REFERENCE.md missing)"
    for line in notes.read_text(encoding="utf-8").splitlines():
        if ("third-party/%s`" % origin) in line:
            cells = [c.strip().strip("`") for c in line.strip("|").split("|")]
            if len(cells) >= 4:
                return "%s @ %s" % (cells[2], cells[3][:12])
    return "(not recorded)"


def find_android_jar():
    candidates = []
    local = ROOT / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                candidates.append(line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\"))
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            candidates.append(os.environ[key])
    for sdk in candidates:
        platforms = pathlib.Path(sdk) / "platforms"
        if not platforms.is_dir():
            continue
        best = None
        for d in platforms.iterdir():
            m = re.fullmatch(r"android-(\d+)", d.name)
            if m and (d / "android.jar").is_file():
                level = int(m.group(1))
                if best is None or level > best[0]:
                    best = (level, d / "android.jar")
        if best:
            return best[1]
    return None


class AndroidSurface:
    """Answers 'does the device have this class' from android.jar, or from the prefix list."""

    def __init__(self):
        self.jar = find_android_jar()
        self.entries = None
        if self.jar:
            with zipfile.ZipFile(self.jar) as z:
                self.entries = {n for n in z.namelist() if n.endswith(".class")}

    def describe(self):
        if self.jar:
            return str(self.jar)
        return "no android.jar found (sdk.dir/ANDROID_HOME); falling back to desktop-only package prefixes"

    def has(self, fqcn):
        if self.entries is not None:
            parts = fqcn.split(".")
            # java.awt.geom.Point2D.Double -> java/awt/geom/Point2D$Double.class, trying each split.
            for i in range(len(parts), 0, -1):
                name = "/".join(parts[:i]) + ("$" + "$".join(parts[i:]) if i < len(parts) else "") + ".class"
                if name in self.entries:
                    return True
            return False
        if fqcn in ANDROID_BEANS:
            return True
        return not fqcn.startswith(DESKTOP_ONLY_PREFIXES)


def shadow_has(fqcn):
    parts = fqcn.split(".")
    for i in range(len(parts), 0, -1):
        if (SHADOW_ROOT.joinpath(*parts[:i]).with_suffix(".java")).is_file():
            return True
    return False


def strip_comments(lines):
    """Yield (lineno, code) with // and /* */ comments blanked, keeping line numbers aligned."""
    in_block = False
    for number, line in enumerate(lines, 1):
        out = []
        i = 0
        in_string = False
        while i < len(line):
            ch = line[i]
            two = line[i:i + 2]
            if in_block:
                if two == "*/":
                    in_block = False
                    i += 2
                    continue
                i += 1
                continue
            if in_string:
                out.append(ch)
                if ch == "\\":
                    out.append(line[i + 1:i + 2])
                    i += 2
                    continue
                if ch == '"':
                    in_string = False
                i += 1
                continue
            if two == "//":
                break
            if two == "/*":
                in_block = True
                i += 2
                continue
            if ch == '"':
                in_string = True
            out.append(ch)
            i += 1
        yield number, "".join(out)


def imports_of(lines):
    found = []
    for number, line in enumerate(lines, 1):
        m = re.match(r"\s*import\s+(static\s+)?([\w.]+)(\.\*)?\s*;", line)
        if not m:
            continue
        name = m.group(2)
        if m.group(1):
            name = name.rsplit(".", 1)[0]  # static import names a member; the class is its prefix
        found.append((number, name, bool(m.group(3))))
    return found


def declarations(lines):
    """Public/protected declaration lines joined across continuations, as (lineno, text)."""
    result = []
    code = list(strip_comments(lines))
    i = 0
    while i < len(code):
        number, text = code[i]
        if DECL_RE.match(ANNOTATION_RE.sub("", text)):
            joined = text.strip()
            while not re.search(r"[{;}]\s*$", joined) and i + 1 < len(code):
                i += 1
                joined += " " + code[i][1].strip()
            result.append((number, joined))
        i += 1
    return result


def normalize_signatures(text):
    """One declaration line -> the API signatures it declares, with bodies, initialisers, throws
    and implementation-only modifiers removed so a stub's one-liner and OpenJDK's Javadoc'd
    method compare equal when they declare the same member."""
    text = ANNOTATION_RE.sub("", text)
    text = text.split("{", 1)[0]
    text = re.sub(r"\s*=.*$", "", text)
    text = re.sub(r"\bthrows\b.*$", "", text)
    text = re.sub(r"\b(abstract|final|synchronized|strictfp|native|transient|volatile)\s+", "", text)
    text = re.sub(r"\s+", " ", text).strip().rstrip(";").strip()
    text = re.sub(r"\s*([(),<>\[\]])\s*", r"\1", text)
    if not text:
        return []
    if "(" not in text and "<" not in text and "," in text:
        head, _, names = text.rpartition(" ")  # `public double x1,y1` -> one entry per field
        return ["%s %s" % (head, n) for n in names.split(",")]
    return [text]


def public_api(lines):
    """Sorted so that a member moved between files (nested class order, grouping) is not a diff."""
    seen = set()
    for _, text in declarations(lines):
        seen.update(normalize_signatures(text))
    return sorted(seen)


def since_members(lines):
    """Members whose preceding Javadoc says @since 9 or later: (lineno, version, declaration)."""
    result = []
    pending = None
    in_doc = False
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith("/**"):
            in_doc = True
            pending = None
        if in_doc:
            m = re.search(r"@since\s+(\d+)(?:\.(\d+))?", stripped)
            if m:
                major = int(m.group(1))
                if major == 1 and m.group(2):
                    major = int(m.group(2))
                if major >= 9:
                    pending = str(major)
            if "*/" in stripped:
                in_doc = False
            continue
        if pending is None:
            continue
        if not stripped or stripped.startswith("@"):
            continue
        result.append((number, pending, stripped.rstrip("{").strip()))
        pending = None
    return result


def java9_uses(lines, imports):
    uses = []
    for number, name, _ in imports:
        for prefix, version in JDK9_PLUS_IMPORTS.items():
            if name == prefix or name.startswith(prefix + "."):
                uses.append((number, "import %s (%s)" % (name, version)))
    for number, text in strip_comments(lines):
        for pattern, label in JDK9_PLUS_PATTERNS:
            if pattern.search(text):
                uses.append((number, "%s: %s" % (label, text.strip())))
    return uses


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("fqcn", help="fully qualified class name, e.g. java.awt.geom.QuadCurve2D")
    parser.add_argument("--from", dest="origin", choices=sorted(REFERENCE_ROOTS), default="jdk8u",
                        help="reference tree to copy from (default jdk8u)")
    parser.add_argument("--dry", action="store_true", help="print the checklist, write nothing")
    parser.add_argument("--force", action="store_true", help="overwrite an existing shadow file")
    parser.add_argument("--out", type=pathlib.Path, metavar="DIR",
                        help="copy under DIR instead of android-awt/src/main/java (the checklist still "
                             "compares against the real shadow tree)")
    args = parser.parse_args()

    source, relative = find_source(args.fqcn, args.origin)
    text = source.read_text(encoding="utf-8")
    lines = text.splitlines()
    dest_root = args.out if args.out else SHADOW_ROOT
    dest = dest_root / relative
    existing = SHADOW_ROOT / relative
    android = AndroidSurface()
    imports = imports_of(lines)

    print("== port-jdk-class: %s ==" % args.fqcn)
    print("source:      %s" % source.relative_to(ROOT))
    print("reference:   %s %s" % (args.origin, reference_revision(args.origin)))
    print("destination: %s%s" % (dest.relative_to(ROOT) if dest.is_relative_to(ROOT) else dest,
                                 "  (exists)" if existing.is_file() else ""))
    print("android.jar: %s" % android.describe())
    if not text.lstrip().startswith("/*"):
        print("WARNING: no license header at the top of the source; the port must keep one")

    print()
    print("-- 1. imports android.jar lacks (shadowed = already in android-awt, MISSING = port or cut)")
    count = 0
    for number, name, wildcard in imports:
        if wildcard:
            print("  L%-5d %s.*  (wildcard: check each simple name used)" % (number, name))
            count += 1
            continue
        if android.has(name):
            continue
        count += 1
        print("  L%-5d %-55s %s" % (number, name, "shadowed" if shadow_has(name) else "MISSING"))
    print("  (none)" if count == 0 else "  %d import(s) to resolve; same-package references are implicit and not listed" % count)

    print()
    print("-- 2. sun.* / com.sun.* references (peer coupling to route to net.runelite.awt.impl)")
    count = 0
    for number, code in strip_comments(lines):
        for m in re.finditer(r"\b((?:com\.)?sun\.[\w.]*\w)", code):
            count += 1
            print("  L%-5d %-45s %s" % (number, m.group(1), code.strip()[:90]))
    print("  (none)" if count == 0 else "  %d reference(s)" % count)

    print()
    print("-- 3. native methods (pure Java, or AwtNative.kt + rust/src in one change)")
    count = 0
    for number, code in strip_comments(lines):
        if re.search(r"\bnative\b", code) and not re.search(r"\bnative\s*\(", code):
            count += 1
            print("  L%-5d %s" % (number, code.strip()[:100]))
    print("  (none)" if count == 0 else "  %d native method(s)" % count)

    print()
    print("-- 4. @since 9+ members (mark each `// JDK 9+ (<ver>): %s <path>`)" % args.origin)
    members = since_members(lines)
    if args.origin != "jdk17u" and not members:
        print("  (8u source: none by construction; use --from jdk17u to see the members 17u adds)")
    for number, version, decl in members:
        print("  L%-5d @since %-3s %s" % (number, version, decl[:95]))
    if args.origin == "jdk17u" and not members:
        print("  (none)")
    uses = java9_uses(lines, imports)
    print("-- 4b. Java 9+ language/API uses to rewrite (module compiles as Java 8)")
    for number, what in uses:
        print("  L%-5d %s" % (number, what[:110]))
    print("  (none)" if not uses else "  %d use(s)" % len(uses))

    print()
    print("-- 5. existing shadow file")
    if existing.is_file():
        old_lines = existing.read_text(encoding="utf-8").splitlines()
        old_api = public_api(old_lines)
        new_api = public_api(lines)
        print("  would replace %s (%d lines, %d public/protected members) with the %d-line source "
              "(%d members)" % (existing.relative_to(ROOT), len(old_lines), len(old_api), len(lines), len(new_api)))
        diff = list(difflib.unified_diff(old_api, new_api, fromfile="shadow:" + str(relative),
                                         tofile=args.origin + ":" + str(relative), lineterm="", n=0))
        if diff:
            print("  public API diff (- only in current shadow, + only in OpenJDK source):")
            for line in diff:
                print("    " + line)
        else:
            print("  public API identical")
    else:
        print("  none: %s is new to the shadow tree" % relative)
        print("  consumer-rules.pro keeps java.awt/java.beans/javax.swing/javax.sound/javax.imageio/sun/com.sun;"
              " another package needs a -keep line")

    print()
    if args.dry:
        print("dry run: nothing written")
        return
    if existing.is_file() and not args.out and not args.force:
        die("%s exists; re-run with --force to overwrite it, or --dry to only see the checklist"
            % existing.relative_to(ROOT))
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)
    print("copied verbatim to %s; it will not compile until the checklist above is worked through"
          % (dest.relative_to(ROOT) if dest.is_relative_to(ROOT) else dest))


if __name__ == "__main__":
    main()
