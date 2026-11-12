#!/usr/bin/env python3
"""Check externally callable library APIs against the minified APK, not R8's inputs."""
import argparse
import pathlib
import re
import struct
import zipfile


# The host libraries hub plugins link by name (android/build.gradle.kts verifyReleasePluginApi
# passes their jars): R8 must keep every public/protected member, including the guava-jre
# default methods and the Discord binding types DiscordService exposes.
PREFIXES = ("joptsimple/", "com/google/protobuf/", "com/google/common/", "com/google/gson/",
            "okhttp3/", "okio/", "org/slf4j/", "net/runelite/discord/")
# Runtime D8 can introduce these interfaces even when APK code never names them.
RUNTIME_CLASSES = ("j$/util/Collection", "j$/util/concurrent/Flow$Publisher",
                   "j$/util/concurrent/Flow$Subscriber", "j$/util/concurrent/Flow$Subscription")


def class_api(data):
    pos = 8

    def read(fmt):
        nonlocal pos
        value = struct.unpack_from(">" + fmt, data, pos)
        pos += struct.calcsize(">" + fmt)
        return value[0] if len(value) == 1 else value

    pool = [None] * read("H")
    index = 1
    while index < len(pool):
        tag = read("B")
        if tag == 1:
            size = read("H")
            pool[index] = data[pos:pos + size].decode("utf-8", "replace")
            pos += size
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = read("H")
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            index += 1
        elif tag == 15:
            pos += 3
        else:
            raise ValueError("Unknown class constant tag " + str(tag))
        index += 1
    access, owner, _ = read("HHH")
    name = pool[pool[owner]]
    interfaces = read("H")
    pos += 2 * interfaces
    members = {}
    for kind in ("field", "method"):
        for _ in range(read("H")):
            flags, member, descriptor, count = read("HHHH")
            # Public synthetic bridges also form part of the binary API used by
            # callers compiled against erased generic method signatures.
            if flags & 5:
                members[(kind, pool[member], pool[descriptor])] = flags
            for _ in range(count):
                read("H")
                size = read("I")
                pos += size
    return name, access, members


def dex_api(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Not a DEX file")
    u4 = lambda offset: struct.unpack_from("<I", data, offset)[0]
    u2 = lambda offset: struct.unpack_from("<H", data, offset)[0]

    def leb(offset):
        value = shift = 0
        while True:
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value, offset
            shift += 7

    def string(offset):
        _, offset = leb(offset)
        return data[offset:data.index(b"\0", offset)].decode("utf-8", "replace")

    strings = [string(u4(u4(60) + i * 4)) for i in range(u4(56))]
    types = [strings[u4(u4(68) + i * 4)] for i in range(u4(64))]

    def typelist(offset):
        return [types[u2(offset + 4 + i * 2)] for i in range(u4(offset))] if offset else []

    protos = []
    for i in range(u4(72)):
        offset = u4(76) + i * 12
        protos.append("(" + "".join(typelist(u4(offset + 8))) + ")" + types[u4(offset + 4)])
    fields = [("field", strings[u4(u4(84) + i * 8 + 4)], types[u2(u4(84) + i * 8 + 2)]) for i in range(u4(80))]
    methods = [("method", strings[u4(u4(92) + i * 8 + 4)], protos[u2(u4(92) + i * 8 + 2)]) for i in range(u4(88))]
    result = {}
    for i in range(u4(96)):
        offset = u4(100) + i * 32
        name = types[u4(offset)][1:-1]
        if not name.startswith(PREFIXES) and name not in RUNTIME_CLASSES:
            continue
        access = u4(offset + 4)
        pos = u4(offset + 24)
        members = {}
        if pos:
            counts = []
            for _ in range(4):
                count, pos = leb(pos)
                counts.append(count)
            for group, count in enumerate(counts):
                index = 0
                for _ in range(count):
                    diff, pos = leb(pos)
                    index += diff
                    flags, pos = leb(pos)
                    if group >= 2:
                        _, pos = leb(pos)
                    members[(fields if group < 2 else methods)[index]] = flags
        result[name] = access, members
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    parser.add_argument("--library-jar", type=pathlib.Path, action="append", required=True)
    args = parser.parse_args()
    expected = {}
    for jar in args.library_jar:
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                if entry.startswith(PREFIXES) and entry.endswith(".class"):
                    name, access, members = class_api(archive.read(entry))
                    if name in expected and expected[name] != (access, members):
                        raise ValueError("Conflicting input API for " + name)
                    expected[name] = access, members
    for prefix in PREFIXES:
        if not any(name.startswith(prefix) for name in expected):
            raise ValueError("No reference classes found for " + prefix)
    actual = {}
    with zipfile.ZipFile(args.apk) as archive:
        for entry in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", entry):
                actual.update(dex_api(archive.read(entry)))
    failures = ["Missing runtime class " + name for name in RUNTIME_CLASSES if name not in actual]
    member_count = 0
    for name, (access, members) in expected.items():
        if name not in actual:
            failures.append("Missing class " + name)
            continue
        actual_access, actual_members = actual[name]
        if access & 1 and not actual_access & 1:
            failures.append("Class no longer public " + name)
        for member, flags in members.items():
            member_count += 1
            received = actual_members.get(member)
            if received is None:
                failures.append("Missing " + name + "." + member[1] + member[2])
            elif (flags & 8) != (received & 8) or (flags & 1 and not received & 1) or not received & 5:
                failures.append("Incompatible access/static kind " + name + "." + member[1] + member[2])
    if failures:
        print("\n".join(failures[:80]))
        raise SystemExit("FAIL: %d external API mismatches" % len(failures))
    print("PASS: runtime interfaces present; %d library classes and %d public/protected members retained in %s" % (len(expected), member_count, args.apk.name))


if __name__ == "__main__":
    main()
