#!/usr/bin/env python3
"""Check that every Java source under android-awt/src/main/java carries a license header.

Two headers are accepted (see android-awt/LICENSE):

* the OpenJDK header, on files ported from jdk8u/jdk17u: an Oracle copyright line and the
  "Classpath" exception designation sentence, which is what makes the file GPLv2+CPE;
* the project header, on files original to this project: the BSD 2-Clause notice below.

`--fix` prepends the project header to files that have neither. It never touches a file that
already starts with a comment, so a ported file whose OpenJDK header was mangled is reported
rather than silently relabelled as original work.

Exit status 0 when every file passes, 1 otherwise.
"""
import argparse
import os
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
SOURCE_ROOT = os.path.join(ROOT, "android-awt", "src", "main", "java")

OPENJDK_MARKERS = (
    "Oracle and/or its affiliates",
    'subject to the "Classpath" exception',
)

PROJECT_HEADER = """\
/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
"""

# The lines that identify the project header; the year may advance, so match around it.
PROJECT_MARKERS = (
    ", Tyler Bochard",
    "BSD 2-Clause License",
)

HEAD_BYTES = 4096


def leading_comment(text):
    """Return the first block comment of the file, or '' when the file does not start with one."""
    stripped = text.lstrip("﻿ \t\r\n")
    if not stripped.startswith("/*"):
        return ""
    end = stripped.find("*/")
    return stripped if end < 0 else stripped[: end + 2]


def classify(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        head = f.read(HEAD_BYTES)
    comment = leading_comment(head)
    if comment and all(m in comment for m in OPENJDK_MARKERS):
        return "openjdk"
    if comment and all(m in comment for m in PROJECT_MARKERS):
        return "project"
    return "comment" if comment else "none"


def java_files():
    for dirpath, dirnames, filenames in os.walk(SOURCE_ROOT):
        dirnames.sort()
        for name in sorted(filenames):
            if name.endswith(".java"):
                yield os.path.join(dirpath, name)


def add_project_header(path):
    with open(path, "r", encoding="utf-8", newline="") as f:
        text = f.read()
    bom = text.startswith("﻿")
    if bom:
        text = text[1:]
    newline = "\r\n" if "\r\n" in text else "\n"
    header = PROJECT_HEADER.replace("\n", newline)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(("﻿" if bom else "") + header + text.lstrip("\r\n"))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--fix", action="store_true",
                        help="prepend the project header to files that have no header at all")
    parser.add_argument("--quiet", action="store_true", help="print only the failures")
    args = parser.parse_args(argv)

    if not os.path.isdir(SOURCE_ROOT):
        print("missing source root: " + SOURCE_ROOT, file=sys.stderr)
        return 2

    counts = {"openjdk": 0, "project": 0, "comment": 0, "none": 0}
    failures = []
    fixed = 0
    for path in java_files():
        kind = classify(path)
        if kind == "none" and args.fix:
            add_project_header(path)
            fixed += 1
            kind = classify(path)
        counts[kind] += 1
        if kind in ("comment", "none"):
            rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
            reason = ("starts with a comment that is neither header" if kind == "comment"
                      else "no license header")
            failures.append(rel + ": " + reason)

    total = sum(counts.values())
    if not args.quiet:
        print("%d files: %d OpenJDK (GPLv2+CPE), %d project (BSD-2)" % (
            total, counts["openjdk"], counts["project"]))
        if fixed:
            print("%d files given the project header" % fixed)
    if failures:
        for line in failures:
            print(line)
        print("%d of %d files lack a license header" % (len(failures), total))
        return 1
    if not args.quiet:
        print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
