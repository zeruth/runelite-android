#!/usr/bin/env python3
"""Compile the pure-Java part of android-awt on the host JDK and run the host contracts against it.

Discovery replaces the hand-kept source lists of the old drivers:

* every ``android-awt/src/main/java`` source whose code (comments and string literals stripped) names
  nothing from ``android.*`` and nothing Kotlin-only (``net.runelite.awt.AwtNative``/``AwtCompat``) is a
  candidate; a candidate whose shadow references lead, transitively, to an excluded class that the host
  JDK does not have either is excluded too (it could only compile against the Android backend).  A
  reference to an excluded class the host JDK *does* have (``java.awt.Component``, ``EventQueue``) is
  fine: the JDK class fills in, exactly as on the old drivers.  What javac still rejects (a shadow-only
  member of such a JDK-backed class, ``Component.setParent``) is dropped with javac's reason and the
  compile retried until it converges: the result is the *compiled* set;
* at run time the compiled classes are patched over the host JDK with ``--patch-module``, except where
  the mix would blow up away from the contract under test: the JDK's own classes call internals of their
  neighbours (``Component.<clinit>`` calls the package-private ``Toolkit.loadLibraries()``), so a shadow
  class is demoted to compile-only when a JDK class that can still be loaded (reachable through constant
  pool references from the patched classes and the tests) references a member the JDK class declares and
  the shadow (including what it inherits) does not.  The surviving *patched* set is compiled a second
  time against itself so it never leans on a compile-only class;
* each module's classes are compiled with ``--patch-module <module>=<sources>`` (the module is looked up in
  the host ``jrt:/`` image, so new packages land next to their siblings);
* every ``tools-src/Test*.java`` whose first lines carry ``// host-contract`` runs twice, against the plain
  host JDK and with the patch applied; both runs must pass.

Header options (space separated after ``// host-contract:``):

    runs=both|patched    patched-only when the test reaches shadow-only classes (default both)
    args=A,B             program arguments for every run
    patched-args=A,B     program arguments for the patched run only (``shadow`` lets a test guard the
                         checks that assert a documented divergence from the JDK)
    fixture=keystore     a throw-away PKCS12 keystore (password ``test-only``, CN=localhost) is generated
                         and its path appended to the arguments
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

REPO = Path(__file__).resolve().parent.parent
SHADOW = REPO / "android-awt/src/main/java"
TESTS = REPO / "tools-src"
KOTLIN_ONLY = ("net.runelite.awt.AwtNative", "net.runelite.awt.AwtCompat")
# Fallback for packages the host image lacks entirely (new shadow-only packages).
MODULE_BY_PREFIX = (
    ("com.sun.net.httpserver", "jdk.httpserver"),
    ("com.sun.management", "jdk.management"),
    ("java.net.http", "java.net.http"),
    ("java.lang.management", "java.management"),
    ("javax.management", "java.management"),
    ("java.lang", "java.base"),
    ("java.net", "java.base"),
)
STRIP = re.compile(r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)
IMPORT = re.compile(r'^\s*import\s+(static\s+)?([\w.]+?)(\.\*)?\s*;', re.M)
QUALIFIED = re.compile(r'\b[a-z_]\w*(?:\.[a-z_]\w*)+\.[A-Z]\w*')
ANDROID = re.compile(r'\bandroid\.[a-z]\w*\.')
HEADER = re.compile(r'^\s*//\s*host-contract\b:?\s*(.*)$')
JAVAC_ERROR = re.compile(r'^(.*?\.java):\d+: error: (.*)$', re.M)
JAVAP_CLASS = re.compile(r'^(?:(?:public|protected|private|abstract|static|final|strictfp|sealed|non-sealed)\s+)*'
                         r'(?:class|interface|enum|record|@interface)\s+([\w.$]+)(?:<.*?>)?(.*)\{$')
JRT_LOOKUP = r'''
import java.nio.file.*;
import java.util.*;
public class JrtLookup {
    public static void main(String[] args) throws Exception {
        Path modules = FileSystems.getFileSystem(java.net.URI.create("jrt:/")).getPath("/modules");
        List<Path> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modules)) { for (Path p : stream) names.add(p); }
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            String found = "-";
            for (Path module : names) {
                Path path = module.resolve(line);
                // A package query must see classes directly in the directory: java.datatransfer has a
                // java/awt directory that only holds its own sub-package.
                boolean hit = line.endsWith(".class") ? Files.exists(path) : Files.isDirectory(path)
                    && Files.list(path).anyMatch(p -> p.getFileName().toString().endsWith(".class"));
                if (hit) { found = module.getFileName().toString(); break; }
            }
            System.out.println(line + " " + found);
        }
    }
}
'''
# Constant-pool reader: "C <root> <class> <referenced class>" for every class reference and
# "M <root> <class> <owner> <name> <descriptor>" for field/method references whose owner is listed in
# args[0].  args[1..] are the roots: module names in the jrt image or "dir:<path>" class directories.
REF_SCAN = r'''
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
public class RefScan {
    public static void main(String[] args) throws Exception {
        Set<String> owners = new HashSet<>(Files.readAllLines(Paths.get(args[0])));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"), 1 << 16));
        for (int i = 1; i < args.length; i++) {
            Path root = args[i].startsWith("dir:") ? Paths.get(args[i].substring(4))
                : FileSystems.getFileSystem(java.net.URI.create("jrt:/")).getPath("/modules", args[i]);
            try (Stream<Path> stream = Files.walk(root)) {
                for (Path path : (Iterable<Path>) stream::iterator) {
                    String name = path.getFileName().toString();
                    if (name.endsWith(".class") && !name.equals("module-info.class")) scan(path, owners, args[i], out);
                }
            }
        }
        out.flush();
    }
    static void scan(Path path, Set<String> owners, String root, PrintWriter out) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)));
        in.readInt(); in.readUnsignedShort(); in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] utf = new String[count]; int[] classes = new int[count]; int[][] pairs = new int[count][]; int[] tags = new int[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte(); tags[i] = tag;
            switch (tag) {
                case 1: utf[i] = in.readUTF(); break;
                case 3: case 4: in.readInt(); break;
                case 5: case 6: in.readLong(); i++; break;
                case 7: classes[i] = in.readUnsignedShort(); break;
                case 8: case 16: case 19: case 20: in.readUnsignedShort(); break;
                case 9: case 10: case 11: case 12: case 17: case 18: pairs[i] = new int[] {in.readUnsignedShort(), in.readUnsignedShort()}; break;
                case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                default: throw new IOException("constant tag " + tag + " in " + path);
            }
        }
        in.readUnsignedShort();
        String self = utf[classes[in.readUnsignedShort()]];
        in.close();
        for (int i = 1; i < count; i++) {
            if (tags[i] == 7) {
                String referenced = utf[classes[i]].replaceFirst("^\\[+", "");
                if (referenced.startsWith("L")) referenced = referenced.substring(1, referenced.length() - 1);
                if (!referenced.equals(self) && referenced.indexOf('/') >= 0) out.println("C " + root + " " + self + " " + referenced);
            } else if (tags[i] >= 9 && tags[i] <= 11) {
                String owner = utf[classes[pairs[i][0]]];
                if (owners.contains(owner) && !owner.equals(self)) {
                    int[] nameAndType = pairs[pairs[i][1]];
                    out.println("M " + root + " " + self + " " + owner + " " + utf[nameAndType[0]] + " " + utf[nameAndType[1]]);
                }
            }
        }
    }
}
'''


class Source:
    def __init__(self, path):
        self.path = path
        relative = path.relative_to(SHADOW).with_suffix("")
        self.name = ".".join(relative.parts)
        self.internal = "/".join(relative.parts)
        self.package = ".".join(relative.parts[:-1])
        self.simple = relative.parts[-1]
        self.text = STRIP.sub(" ", path.read_text(encoding="utf-8", errors="replace"))
        self.deps = set()
        self.module = None
        self.reason = None          # why it is not compiled on the host
        self.runtime_reason = None  # why a compiled class is not patched over the JDK

    def blocked(self, stage):
        return self.reason if stage == "compile" else (self.reason or self.runtime_reason)

    def block(self, stage, reason):
        if stage == "compile":
            self.reason = reason
        else:
            self.runtime_reason = reason


def tools(java_home):
    home = Path(java_home) if java_home else None
    suffix = ".exe" if os.name == "nt" else ""

    def tool(name):
        found = str(home / "bin" / (name + suffix)) if home else shutil.which(name)
        if not found or not Path(found).exists():
            raise SystemExit(f"{name} not found; pass --java-home <JDK 11+>")
        return found
    return tool


def run(tool, name, arguments, timeout=None):
    started = time.monotonic()
    try:
        result = subprocess.run([tool(name), *map(str, arguments)], cwd=REPO, text=True, encoding="utf-8",
                                errors="replace", stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
        output, code = result.stdout, result.returncode
    except subprocess.TimeoutExpired as expired:
        output, code = (expired.stdout or "") + f"\n[timeout after {timeout}s]", "timeout"
    return code, output, time.monotonic() - started


def argfile(path, items):
    path.write_text("\n".join('"' + str(item).replace("\\", "/") + '"' for item in items) + "\n", encoding="utf-8")
    return "@" + str(path)


def discover():
    sources = {}
    for path in sorted(SHADOW.rglob("*.java")):
        source = Source(path)
        sources[source.name] = source
    by_package = {}
    for source in sources.values():
        by_package.setdefault(source.package, []).append(source.simple)
    simple_names = {package: re.compile(r'\b(?:' + "|".join(map(re.escape, names)) + r')\b')
                    for package, names in by_package.items()}
    for source in sources.values():
        if ANDROID.search(source.text):
            source.reason = "references android.*"
        elif any(name in source.text for name in KOTLIN_ONLY):
            source.reason = "references the Kotlin JNI facade"
        packages = {source.package, "java.lang"}
        for static, name, wildcard in IMPORT.findall(source.text):
            if wildcard:
                packages.add(name)
            elif static:
                source.deps.add(name.rpartition(".")[0])
            else:
                source.deps.add(name)
        for package in packages:
            if package in simple_names:
                for simple in simple_names[package].findall(source.text):
                    source.deps.add(package + "." + simple)
        source.deps.update(QUALIFIED.findall(source.text))
        source.deps = {dep for dep in source.deps if dep in sources and dep != source.name}
    return sources


def assign_modules(sources, tool, build):
    """Map every source to the host module that owns its class or package; backend classes follow their callers."""
    probe = build / "JrtLookup.java"
    probe.write_text(JRT_LOOKUP, encoding="utf-8")
    queries = sorted({source.internal + ".class" for source in sources.values()}
                     | {source.package.replace(".", "/") for source in sources.values()})
    (build / "jrt-queries.txt").write_text("\n".join(queries) + "\n", encoding="utf-8")
    code, output, _ = run(tool, "java", [probe, build / "jrt-queries.txt"])
    if code:
        raise SystemExit("jrt lookup failed:\n" + output)
    owner = dict(line.split() for line in output.splitlines() if line.strip())
    in_jdk = set()
    for source in sources.values():
        module = owner.get(source.internal + ".class", "-")
        if module != "-":
            in_jdk.add(source.name)
        else:
            module = owner.get(source.package.replace(".", "/"), "-")
        if module == "-":
            module = next((m for prefix, m in MODULE_BY_PREFIX if source.name.startswith(prefix + ".")), "java.desktop")
        source.module = module
    # net.runelite.awt.impl may live in one module only (no split packages): the one that uses it most.
    backend = [s for s in sources.values() if s.name.startswith("net.runelite.awt.")]
    votes = {}
    for source in sources.values():
        if source not in backend:
            for dep in source.deps:
                if dep.startswith("net.runelite.awt."):
                    votes[source.module] = votes.get(source.module, 0) + 1
    home = max(votes, key=lambda m: (votes[m], m == "java.desktop")) if votes else "java.desktop"
    # The convention classes (AwtUnsupported, @ShadowStub) are reached from every module; in the
    # backend's module they would make it a dependency of the modules it implements (a cycle the
    # compile order cannot satisfy), so they patch java.base, which every module reads and which
    # compiles first.
    for source in backend:
        source.module = home if source.name.startswith("net.runelite.awt.impl.") else "java.base"
    return in_jdk


def exclude_transitively(sources, in_jdk, stage):
    changed = True
    while changed:
        changed = False
        for source in sources.values():
            if source.blocked(stage):
                continue
            for dep in sorted(source.deps):
                if sources[dep].blocked(stage) and dep not in in_jdk:
                    source.block(stage, f"needs shadow-only {dep} ({sources[dep].blocked(stage)})")
                    changed = True
                    break


def read_flags(modules, members=()):
    # Android has no module graph: a shadow class may reach another shadow module freely
    # (java.awt.datatransfer.Clipboard posts to java.awt.EventQueue), so every patched module reads the
    # others.  javac resolves only the graph rooted at the patched module (java.datatransfer does not
    # require java.desktop), so the read targets are added as roots as well.
    flags = ["--add-modules", ",".join(sorted(modules))] if modules else []
    for module in sorted(modules):
        others = sorted(set(modules) - {module})
        if others and module != "java.base":  # java.base is read by everything and reads nothing
            flags += ["--add-reads", f"{module}={','.join(others)}"]
    # The module's own packages (net.runelite.awt.AwtUnsupported, @ShadowStub, the backend) land in
    # one patched module but are called from every other one, so they are exported to the rest.
    exported = sorted({(m.module, m.package) for m in members if m.name.startswith("net.runelite.") and m.module in modules})
    for module, package in exported:
        others = sorted(set(modules) - {module})
        if others:
            flags += ["--add-exports", f"{module}/{package}={','.join(others)}"]
    return flags


def module_order(groups, sources):
    """Modules whose shadow classes are implemented by other modules' shadow classes compile last."""
    needs = {module: set() for module in groups}
    for module, members in groups.items():
        for member in members:
            for dep in member.deps:
                if sources[dep].module in groups and sources[dep].module != module:
                    needs[module].add(sources[dep].module)
    order = []
    while needs:
        ready = sorted(m for m, n in needs.items() if not n - set(order)) or [sorted(needs)[0]]
        order.append(ready[0])
        needs.pop(ready[0])
    return order


def compile_once(groups, sources, tool, root):
    """Compile every module patch; return the class dirs, or the sources javac rejected with its reason."""
    patches, rejected = {}, {}
    reads = read_flags(groups, [member for members in groups.values() for member in members])
    for module in module_order(groups, sources):
        source_dir, class_dir = root / "source" / module, root / "classes" / module
        for folder in (source_dir, class_dir):
            shutil.rmtree(folder, ignore_errors=True)
            folder.mkdir(parents=True)
        files = {}
        for member in groups[module]:
            target = source_dir / member.path.relative_to(SHADOW)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(member.path, target)
            files[target.resolve()] = member
        earlier = [flag for done, done_dir in patches.items() for flag in ("--patch-module", f"{done}={done_dir}")]
        code, output, elapsed = run(tool, "javac", [
            "-encoding", "UTF-8", "-nowarn", "-proc:none", "-Xmaxerrs", "1000", "-Xlint:none",
            *reads, *earlier, "--patch-module", f"{module}={source_dir}", "-d", class_dir,
            argfile(root / f"sources-{module}.txt", files)])
        if code == 0:
            patches[module] = class_dir
            continue
        errors = list(JAVAC_ERROR.finditer(output))
        for index, error in enumerate(errors):
            member = files.get(Path(error.group(1)).resolve())
            if member and member.name not in rejected:
                detail = output[error.end():errors[index + 1].start() if index + 1 < len(errors) else None]
                symbol = re.search(r'symbol:\s*(.*)', detail)
                rejected[member.name] = error.group(2) + (f" ({symbol.group(1).strip()})" if symbol else "")
        if not rejected:
            raise SystemExit(output + f"\nshadow sources of {module} do not compile on the host JDK")
        return None, rejected
    return patches, rejected


def compile_stage(sources, in_jdk, tool, root, stage):
    """Drop what javac rejects (shadow-only members of JDK-backed classes, host-only name clashes) and retry."""
    started = time.monotonic()
    for _ in range(16):
        groups = {}
        for source in sources.values():
            if not source.blocked(stage):
                groups.setdefault(source.module, []).append(source)
        patches, rejected = compile_once(groups, sources, tool, root)
        if patches:
            print(f"{stage}: {sum(map(len, groups.values()))} classes across {', '.join(sorted(groups))} "
                  f"in {time.monotonic() - started:.1f} s", flush=True)
            return patches
        for name, message in rejected.items():
            sources[name].block(stage, "rejected by the host javac: " + message)
        exclude_transitively(sources, in_jdk, stage)
    raise SystemExit(f"the {stage} compile did not converge")


class Members:
    """Declared members per class from ``javap -p -s``: {class: {(name, descriptor): private?}} plus supertypes."""

    def __init__(self, tool):
        self.tool = tool
        self.declared = {}
        self.parents = {}
        self.natives = set()  # classes declaring native methods: JNI code knows their layout

    def load(self, classes):
        for start in range(0, len(classes), 120):  # javap takes no @argfile; stay under the command-line limit
            code, output, _ = run(self.tool, "javap", ["-p", "-s", *classes[start:start + 120]])
            if code and "class " not in output and "interface " not in output:
                raise SystemExit("javap failed:\n" + output)
            self.parse(output)

    def parse(self, output):
        current, pending = None, None
        for line in output.splitlines():
            header = JAVAP_CLASS.match(line)
            if header:
                current = header.group(1)
                self.declared[current] = {}
                supertypes = re.findall(r'\b(?:extends|implements)\s+([^{]*)', header.group(2))
                self.parents[current] = [p.strip().split("<")[0] for part in supertypes for p in part.split(",") if p.strip()]
                continue
            stripped = line.strip()
            if current and line.startswith("  ") and not line.startswith("    ") and stripped.endswith(";"):
                if stripped.startswith("static {}"):
                    pending = None
                    continue
                if " native " in " " + stripped.split("(")[0] + " ":
                    self.natives.add(current)
                head = stripped.split("(")[0].split()[-1] if "(" in stripped else stripped[:-1].split()[-1]
                name = "<init>" if head in (current, current.rsplit(".", 1)[-1]) else head.rsplit(".", 1)[-1]
                pending = (name, stripped.startswith("private "))
            elif pending and stripped.startswith("descriptor: "):
                self.declared[current][(pending[0], stripped[len("descriptor: "):])] = pending[1]
                pending = None

    def inherited(self, name, resolve, seen=None):
        """All non-private members visible on ``name``; ``resolve`` says which Members holds a supertype."""
        seen = seen or set()
        if name in seen or name == "java.lang.Object":
            return set()
        seen.add(name)
        source = resolve(name)
        found = {member for member, private in source.declared.get(name, {}).items() if not private}
        for parent in source.parents.get(name, []):
            found |= self.inherited(parent, resolve, seen)
        return found


def scan_references(tool, build, owners, roots):
    """Class and member references of every class under the given modules / class dirs."""
    probe = build / "RefScan.java"
    probe.write_text(REF_SCAN, encoding="utf-8")
    (build / "ref-owners.txt").write_text("\n".join(sorted(owners)) + "\n", encoding="utf-8")
    code, output, _ = run(tool, "java", [probe, build / "ref-owners.txt", *roots])
    if code:
        raise SystemExit("constant pool scan failed:\n" + output[-4000:])
    jdk_classes, own_classes, members = {}, {}, {}
    for line in output.splitlines():
        parts = line.split()
        from_jdk = not parts[1].startswith("dir:")
        if parts[0] == "C":
            (jdk_classes if from_jdk else own_classes).setdefault(parts[2], set()).add(parts[3])
        elif parts[0] == "M" and from_jdk and len(parts) == 6:
            members.setdefault((parts[3], parts[4], parts[5]), set()).add(parts[2])
    return jdk_classes, own_classes, members


def top(internal):
    return internal.split("$", 1)[0]


def jrt_has(tool, build, internals):
    (build / "jrt-nested.txt").write_text("".join(i + ".class\n" for i in sorted(internals)), encoding="utf-8")
    code, output, _ = run(tool, "java", [build / "JrtLookup.java", build / "jrt-nested.txt"])
    if code:
        raise SystemExit("jrt lookup failed:\n" + output)
    return {line.split()[0][:-len(".class")] for line in output.splitlines() if line.strip() and not line.endswith(" -")}


def drop_in_check(sources, in_jdk, patches, tests_dir, loaded, tool, build):
    """Demote compiled classes whose JDK twin has members a live JDK class (or a JNI layout) needs and the shadow lacks.

    ``loaded`` are the classes the plain-JDK runs of the contracts loaded; a JDK class is live when it was
    loaded (or is one reference away from a patched class) and is reachable through constant-pool references
    from the tests or the patched classes without passing through a class the patch replaces."""
    files = {}
    for module, class_dir in patches.items():
        for path in class_dir.rglob("*.class"):
            files[path.relative_to(class_dir).with_suffix("").as_posix()] = path
    by_internal = {s.internal: s for s in sources.values()}
    for internal in list(files):
        if top(internal) not in by_internal:  # a package-private top-level class declared in another source
            package, _, simple = top(internal).rpartition("/")
            declares = re.compile(r'\b(?:class|interface|enum)\s+' + re.escape(simple) + r'\b')
            owner = next((s for s in sources.values() if s.package == package.replace("/", ".") and declares.search(s.text)), None)
            if owner is None:
                raise SystemExit(f"no shadow source declares {internal}")
            by_internal[top(internal)] = owner
    twins = jrt_has(tool, build, files)  # every patched class file the host image also has
    if not twins:
        return
    jdk, shadow = Members(tool), Members(tool)
    jdk.load([t.replace("/", ".") for t in sorted(twins)])
    shadow.load([files[t] for t in sorted(twins)])
    own = {f.replace("/", ".") for f in files}

    def resolve(name):
        if name in own and name not in shadow.declared:
            shadow.load([files[name.replace(".", "/")]])
        elif name not in own and name not in jdk.declared:
            jdk.load([name])
        return shadow if name in own else jdk

    missing = {}
    for internal in twins:
        dotted = internal.replace("/", ".")
        have = shadow.inherited(dotted, resolve)
        missing[internal] = {m: private for m, private in jdk.declared.get(dotted, {}).items() if m not in have}
        # JNI code reaches fields and constructors no constant pool shows: a JDK class with native
        # methods caches its own field IDs (initIDs), and libfontmanager builds 2D geometry itself
        # (GeneralPath's package-private constructor in SunFontManager.initIDs).  Those layouts must match.
        jni_visible = dotted in jdk.natives or internal.startswith(("java/awt/geom/", "java/awt/image/", "java/awt/font/", "java/awt/color/"))
        layout = sorted((n, d) for (n, d), private in missing[internal].items()
                        if not private and not n.startswith("$") and (n == "<init>" or not d.startswith("(")))
        if layout and jni_visible and not by_internal[top(internal)].runtime_reason:
            by_internal[top(internal)].runtime_reason = (f"JNI-visible layout differs from the JDK's "
                                                         f"{dotted}: lacks {layout[0][0]}{layout[0][1]}")
    jdk_refs, own_refs, member_refs = scan_references(
        tool, build, twins, sorted(patches) + [f"dir:{d}" for d in patches.values()] + [f"dir:{tests_dir}"])
    demoted = True
    while demoted:
        demoted = False
        shadowed = {f for f in files if not by_internal[top(f)].runtime_reason}
        roots = set()
        for referrer, referenced in own_refs.items():
            if referrer in shadowed or referrer not in files:  # patched classes and the tests
                roots |= referenced
        allowed = (loaded | roots) - shadowed
        live, frontier = set(), roots & allowed
        while frontier:
            internal = frontier.pop()
            if internal in live:
                continue
            live.add(internal)
            frontier |= jdk_refs.get(internal, set()) & allowed
        for internal in sorted(twins):
            source = by_internal[top(internal)]
            if source.runtime_reason:
                continue
            for (name, descriptor), private in missing[internal].items():
                # Nest mates reach private members too; anyone else only the non-private ones.
                referrers = {r for r in member_refs.get((internal, name, descriptor), set()) & live
                             if not private or top(r) == top(internal)}
                if referrers:
                    source.runtime_reason = (f"the JDK's {sorted(referrers)[0].replace('/', '.')} needs "
                                             f"{internal.replace('/', '.')}.{name}{descriptor}, which the shadow lacks")
                    demoted = True
                    break


def module_flags(patches, sources):
    flags = read_flags(patches, [s for s in sources.values() if not s.blocked("runtime")])
    for module, class_dir in patches.items():
        flags += ["--patch-module", f"{module}={class_dir}"]
    exported = {(s.module, s.package) for s in sources.values() if not s.blocked("runtime") and s.name.startswith("net.runelite.")}
    for module, package in sorted(exported):
        flags += ["--add-exports", f"{module}/{package}=ALL-UNNAMED"]
    return flags


class Contract:
    def __init__(self, path, options):
        self.path = path
        self.name = path.stem
        self.options = dict(option.split("=", 1) for option in options.split() if "=" in option)
        if self.options.get("runs", "both") not in ("both", "patched"):
            raise SystemExit(f"{self.name}: runs= must be both or patched")

    def modes(self):
        return ["jdk", "patched"] if self.options.get("runs", "both") == "both" else ["patched"]

    def arguments(self, mode, fixtures):
        arguments = [a for a in self.options.get("args", "").split(",") if a]
        if mode == "patched":
            arguments += [a for a in self.options.get("patched-args", "").split(",") if a]
        fixture = self.options.get("fixture")
        if fixture:
            arguments.append(str(fixtures[fixture]))
        return arguments


def contracts():
    found = []
    for path in sorted(TESTS.glob("Test*.java")):
        with path.open(encoding="utf-8", errors="replace") as handle:
            for _, line in zip(range(12), handle):
                match = HEADER.match(line)
                if match:
                    found.append(Contract(path, match.group(1)))
                    break
    return found


def make_fixtures(needed, tool, build):
    fixtures = {}
    if "keystore" in needed:
        keystore = build / "test.p12"
        keystore.unlink(missing_ok=True)
        code, output, _ = run(tool, "keytool", [
            "-genkeypair", "-alias", "test", "-storetype", "PKCS12", "-keystore", keystore, "-storepass", "test-only",
            "-keypass", "test-only", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-keyalg", "RSA", "-validity", "1"])
        if code:
            raise SystemExit("keytool failed:\n" + output)
        fixtures["keystore"] = keystore
    return fixtures


def main():
    parser = argparse.ArgumentParser(description="host contract runner for android-awt")
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"), help="JDK 11+ (default: JAVA_HOME, then PATH)")
    parser.add_argument("--list", action="store_true",
                        help="print contracts, compiled/patched classes and exclusions (runs the plain-JDK pass only)")
    parser.add_argument("--only", action="append", default=[], metavar="TEST", help="run only this contract (repeatable)")
    parser.add_argument("--build-dir", type=Path, default=REPO / "build/compat-tests/awt")
    parser.add_argument("--verbose", action="store_true", help="print the output of passing runs too")
    args = parser.parse_args()
    sys.stdout.reconfigure(errors="replace")
    tool = tools(args.java_home)
    build = args.build_dir
    build.mkdir(parents=True, exist_ok=True)
    print(f"java: {tool('java')}")

    selected = contracts()
    unknown = [only for only in args.only if only not in {c.name for c in selected}]
    if unknown:
        raise SystemExit(f"no host contract named {', '.join(unknown)}; --list shows them")
    if args.only:
        selected = [c for c in selected if c.name in args.only]

    sources = discover()
    in_jdk = assign_modules(sources, tool, build)
    exclude_transitively(sources, in_jdk, "compile")
    compiled = compile_stage(sources, in_jdk, tool, build / "compile", "compile")

    # Tests compile against everything compiled, so a contract may name shadow-only API; the run then
    # shows whether the class it needs survived as a patch.
    tests_dir = build / "tests"
    shutil.rmtree(tests_dir, ignore_errors=True)
    tests_dir.mkdir(parents=True)
    test_errors = {}
    all_contracts = contracts()
    for contract in all_contracts:
        code, output, _ = run(tool, "javac", ["-encoding", "UTF-8", "-nowarn", "-proc:none", *module_flags(compiled, sources),
                                             "-sourcepath", TESTS, "-d", tests_dir, contract.path])
        if code:
            test_errors[contract.name] = output

    # The plain-JDK runs come first: the classes they load tell which JDK classes a patched run can
    # still reach, which decides what may be patched over them.
    fixtures = make_fixtures({c.options.get("fixture") for c in selected}, tool, build)
    results, loaded = [], set()
    logs = build / "class-load"
    shutil.rmtree(logs, ignore_errors=True)
    logs.mkdir()

    def execute(contract, mode, flags):
        log = logs / f"{contract.name}-{mode}.log"
        try:
            log_option = os.path.relpath(log, REPO).replace("\\", "/")  # a drive letter's colon breaks -Xlog parsing
        except ValueError:
            log_option = str(log)
        jvm = ["-Djava.awt.headless=true", f"-Xlog:class+load:file={log_option}:none", *flags, "-cp", tests_dir, contract.name]
        code, output, elapsed = run(tool, "java", jvm + contract.arguments(mode, fixtures), timeout=180)
        status = "pass" if code == 0 else "error" if code == "timeout" else "fail"
        results.append((contract.name, mode, status, elapsed, output))
        if log.exists():
            loaded.update(line.split()[0].replace(".", "/") for line in log.read_text(encoding="utf-8", errors="replace").splitlines()
                          if line.strip() and "source: jrt:/" in line or "source: shared objects file" in line)

    for contract in selected:
        if contract.name in test_errors:
            for mode in contract.modes():
                results.append((contract.name, mode, "error", 0.0, test_errors[contract.name]))
        elif "jdk" in contract.modes():
            execute(contract, "jdk", [])

    patches = compiled
    while True:
        before = sum(1 for s in sources.values() if s.runtime_reason)
        drop_in_check(sources, in_jdk, patches, tests_dir, loaded, tool, build)
        exclude_transitively(sources, in_jdk, "runtime")
        patches = compile_stage(sources, in_jdk, tool, build / "patch", "runtime")
        if sum(1 for s in sources.values() if s.runtime_reason) == before:
            break

    compiled_set = [s for s in sources.values() if not s.reason]
    patched_set = [s for s in compiled_set if not s.runtime_reason]
    (build / "compiled-classes.txt").write_text(
        "".join(f"{s.module} {s.name} {'patched' if not s.runtime_reason else 'compile-only: ' + s.runtime_reason}\n"
                for s in compiled_set), encoding="utf-8")
    print(f"shadow sources: {len(sources)} found, {len(compiled_set)} compiled on the host, "
          f"{len(patched_set)} patched over the JDK, {len(sources) - len(compiled_set)} excluded"
          f" (details: {build / 'compiled-classes.txt'})")
    if args.list:
        print("\ncontracts:")
        for contract in selected:
            options = " ".join(f"{k}={v}" for k, v in contract.options.items())
            print(f"  {contract.name} {options}".rstrip() + (" (does not compile)" if contract.name in test_errors else ""))
        print("\ncompiled and patched:")
        for source in patched_set:
            print(f"  {source.module:14} {source.name}")
        print("\ncompiled only:")
        for source in compiled_set:
            if source.runtime_reason:
                print(f"  {source.module:14} {source.name}: {source.runtime_reason}")
        print("\nexcluded:")
        for source in sources.values():
            if source.reason:
                print(f"  {source.name}: {source.reason}")
        return 0

    flags = module_flags(patches, sources)
    for contract in selected:
        if contract.name not in test_errors:
            execute(contract, "patched", flags)
    results.sort(key=lambda r: (r[0], r[1] != "jdk"))
    for name, mode, status, elapsed, output in results:
        print(f"\nRunning {name} [{mode}]")
        if status != "pass" or args.verbose:
            print(output.rstrip())
        print(f"Tests run: 1, Failures: {int(status == 'fail')}, Errors: {int(status == 'error')}, Skipped: 0, "
              f"Time elapsed: {elapsed:.2f} s" + (f" <<< {status.upper()}" if status != "pass" else ""))
    failed = [(n, m, s) for n, m, s, _, _ in results if s != "pass"]
    print("\nResults:")
    for name, mode, status in failed:
        print(f"  {status.upper()} {name} [{mode}]")
    print(f"\nTests run: {len(results)}, Failures: {sum(s == 'fail' for _, _, s in failed)}, "
          f"Errors: {sum(s == 'error' for _, _, s in failed)}, Skipped: 0")
    print(f"Shadow classes: {len(compiled_set)} compiled on the host, {len(patched_set)} patched over "
          f"{', '.join(sorted(patches))}")
    return 1 if failed or not results else 0


if __name__ == "__main__":
    sys.exit(main())
