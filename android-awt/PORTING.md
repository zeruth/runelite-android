# Porting JDK classes into android-awt

`android-awt` is a shadow of the desktop JDK surface that Android lacks (`java.awt`, `javax.swing`,
`javax.imageio`, `javax.sound`, `java.beans`, `java.applet`, `sun.*`, `com.sun.*`). Every class in
`src/main/java/{java,javax,sun,com/sun}` is either a port of the OpenJDK file of the same name or an
Android-backed reimplementation of it. This document is the rulebook a port follows; a roadmap card
that ports a class cites it, and `tools-src/port-jdk-class.py` turns its rules into a checklist for
one class.

## 1. Where a port starts

- Run `python tools-src/fetch-jdk-reference.py` once; it creates `third-party/jdk8u` (the faithful
  8u source) and `third-party/jdk17u` (the 9+ members) and records tag and commit in
  `third-party/JDK-REFERENCE.md`. Both trees are gitignored reference, never compiled or shipped.
- Start from the OpenJDK 8u file: `third-party/jdk8u/jdk/src/share/classes/<package path>/<Class>.java`.
  The 17u file (`third-party/jdk17u/src/java.desktop/share/classes/...`) is consulted only for
  members RuneLite or a hub plugin needs that 8u does not have.
- `python tools-src/port-jdk-class.py <fqcn>` copies the 8u file into `src/main/java` verbatim (it
  refuses to overwrite an existing shadow file without `--force`) and prints the checklist of
  section 9. `--from jdk17u` takes the 17u file instead; `--dry` prints the checklist without writing.

## 2. What a port keeps

- The OpenJDK license header, unchanged. Ported files are GPLv2 + Classpath Exception; the header
  is what says so. Files written from scratch (Android backends under `net.runelite.awt.impl`, stubs
  that share nothing but a name) carry the project's BSD 2-Clause header instead; `python
  tools-src/check-license-headers.py` fails on a file that has neither, and `--fix` stamps the project
  header on files that have no header at all. The two headers, and what each means for the APK,
  are described in `android-awt/LICENSE`.
- Every `public` and `protected` signature of the original, including constants, nested classes,
  `serialVersionUID` and `Serializable`/`Cloneable` declarations. Hub plugins are compiled against
  the real JDK; a missing or narrowed member is a `NoSuchMethodError` on a phone, found by whoever
  installs that plugin.
- The Javadoc-visible behaviour: return values, exceptions, null handling, event ordering, the
  pixel-exact geometry of `java.awt.geom`. When the desktop behaviour is "wrong" and the game depends
  on it, desktop wins (`createImage` returns `TYPE_INT_RGB`, `SRC_OVER` promotes to `SRC` for opaque
  sources, the audio line's `flush`/`drain` are no-ops); read `net/runelite/awt/impl` before touching
  a pipeline and look for a note explaining the deviation before "fixing" it.
- The Javadoc itself, trimmed where it only describes peer plumbing that no longer exists. Comments
  added by the port say why, not what.
- OpenJDK style: 4-space indents, K&R braces. Checkstyle is disabled for this module for that reason.

## 3. What a port replaces

- `sun.*` peer coupling. OpenJDK's `java.awt` classes delegate to `sun.awt`, `sun.java2d`,
  `sun.font` peers and to native code; the shadow delegates to `net.runelite.awt.impl` instead
  (`BufferedImageGraphics2D` for the raster pipeline, `AwtFontRegistry` for fonts,
  `AndroidClip`/`PooledSourceDataLine` for audio, `Headless*` for the graphics device, `Stroker`/
  `Contours` for geometry, the ported `sun.awt.geom` for `Area`). A `sun.*` class is ported itself only when outside code
  references it by name (the surface report and the hub scanner say which); otherwise its logic moves
  into the backend or into the class that used it.
- Native methods. There is no `libawt`; a `native` method either becomes pure Java, or becomes a call
  into `AwtNative` (section 7) when it is a per-pixel loop worth running in Rust.
- `java.security.AccessController`/`doPrivileged` wrappers, `sun.misc.Unsafe`, `SharedSecrets`,
  `ReflectUtil` and friends: drop the wrapper, keep the body.
- `java.beans` introspection used only for `PropertyChangeSupport`: Android has `java.beans.
  PropertyChangeSupport`; use it, do not shadow it (section 4, second bullet).

## 4. What a port must never do

- Use Java 9+ language features or JDK APIs: no `var`, `record`, switch expressions, text blocks,
  `List.of`, `String.isBlank/strip/repeat`, `java.io.Serial`, `Objects.requireNonNullElse`,
  `Optional.isEmpty`. The module compiles with `sourceCompatibility = targetCompatibility = 1.8`,
  `jvmTarget 1.8` and `options.release` cleared, and the same sources compile on a desktop JDK with
  `--patch-module java.desktop=` for the host tests. Never bump these in `build.gradle.kts`. The
  17u file is a source of members, not of syntax: strip `@Serial`, rewrite `switch ->`, replace
  `List.of` with `Arrays.asList`/`Collections.unmodifiableList`.
- Define a class `android.jar` already owns. `java.beans.PropertyChangeSupport`, `java.util.*`,
  `java.nio.*`, `javax.xml.*`, `java.util.concurrent.Flow`, `java.lang.ProcessHandle` on API 26+
  ... whatever exists on the device wins at class loading, so a shadow copy is dead code at best
  and a bootclasspath duplicate-class failure at worst. `:android-awt:shadowOwnershipCheck` diffs
  the compiled shadow tree against the compile SDK's `android.jar` and fails on any overlap; it
  also prints what `android.jar` owns in each desktop package the shadow tree populates. Today
  that is, in `java.beans`: `PropertyChangeEvent`, `PropertyChangeListener`,
  `PropertyChangeListenerProxy`, `PropertyChangeSupport`, `IndexedPropertyChangeEvent` (a
  `java.beans` card ports everything else in the package, `Introspector`, `PropertyDescriptor`,
  `ConstructorProperties`, ..., and uses those five from Android); in `java.awt.font`:
  `TextAttribute`, `NumericShaper`. The surface report and gate count these as present.
  `port-jdk-class.py` checks each import against the SDK's `android.jar`; a class that is not in
  it and not already in the shadow tree is a dependency the card has to port or cut.
- Reference anything outside: Android-available JDK classes, other shadow classes, `android.*`,
  and `net.runelite.awt.*`. Lint runs with `error InvalidPackage` and `checkDependencies = true`.
- Rename, relocate or drop a shadow class or member. `consumer-rules.pro` keeps `java.awt.**`,
  `java.beans.**`, `javax.swing.**`, `javax.sound.**`, `javax.imageio.**`, `sun.**`, `com.sun.**`
  by name; a new shadow package outside those prefixes (for example `javax.print`, `javax.
  accessibility`) needs its own `-keep class <pkg>.** { *; }` line there in the same change.
- Leave a silent no-op in a path a plugin can hit. A member either works, or throws
  `UnsupportedOperationException("Foo.bar")` naming itself, or is a documented deliberate no-op with
  the reason in a comment (headless `Toolkit.beep()`, `SourceDataLine.drain()`).

## 5. JDK 9+ members

RuneLite and hub plugins are built against JDK 11+ and use a handful of members that 8u does not
have (`Component.requestFocus(FocusEvent.Cause)`, `Taskbar`, `Desktop.moveToTrash`, `FocusEvent.
Cause`). Each such member is added on top of the 8u port, marked where it is declared:

```java
// JDK 9+ (9): jdk17u src/java.desktop/share/classes/java/awt/Component.java
public void requestFocus(FocusEvent.Cause cause) {
```

The parenthesised number is the `@since` of the member in the 17u file; the path says which file it
was taken from. A whole class that first appeared after 8 (`java.awt.Taskbar`, `java.awt.desktop.*`)
gets the same comment once, under the license header. `port-jdk-class.py --from jdk17u` lists every
`@since 9` or later member of the 17u file with its line number so the marks can be placed before
the file compiles.

## 6. Faithful port or Android-backed reimplementation

Port faithfully when the class is pure Java once the peer is gone: geometry (`java.awt.geom`),
models and events (`javax.swing.*Model`, `javax.swing.event`, `java.awt.event`), text
(`javax.swing.text`, `javax.swing.undo`), `AlphaComposite`, `Color`, `Font` metadata,
`BufferedImage` plumbing, `javax.imageio` metadata, `javax.sound.sampled` data classes. The host
contract tests can then run the same `Test*.java` against the real JDK and the shadow, and a diff is a
bug by definition.

Reimplement over an Android backend when the OpenJDK file is a thin shell over native code or a
peer tree the module does not have: `Graphics2D` (raster loops in Rust), `Font`/`FontMetrics`
rendering (Android `Paint`/`Typeface` through `AwtFontRegistry`), `Toolkit`, `GraphicsEnvironment`,
`Robot`, `Clipboard`, `SourceDataLine`/`Clip` (`AudioTrack`), `ImageIO` codecs (`BitmapFactory`),
`Desktop`, `SystemTray`. The public class still carries the OpenJDK signatures and header (what the
caller sees is a port); the body dispatches to a class under `net.runelite.awt.impl` that is written
from scratch, is free to use any Android API, and is renamed by R8. The split keeps the shadow
surface mechanical and puts every "how does Android do this" decision in one package.

The two tests for choosing: does `java.awt.peer`/`sun.java2d` show up in the 8u file's imports, and
does the behaviour depend on the display, the audio device or the file system? Yes to either means
reimplementation behind a ported facade. A class that is half and half (`Component`, `Window`,
`BufferedImage`) is ported, with the peer calls routed to a backend interface rather than removed.

## 7. Native entry points

A per-pixel loop that is too slow in Java goes into the Rust crate `android-awt/rust`
(`runelite-awt-native`). Adding one is a single change touching both sides, because the merge task
in `build.gradle.kts` reads `external fun` names out of `AwtNative.kt` and fails the build when the
packaged `.so` lacks the `Java_net_runelite_awt_AwtNative_<name>` symbol:

1. Declare `@JvmStatic external fun <name>(...)` in `src/main/kotlin/net/runelite/awt/AwtNative.kt`.
   Pixel buffers are `IntArray` in `0xAARRGGBB`; pass image width/height, device-space clip rects as
   `(x0, y0, x1, y1)` half-open, and the `AlphaComposite` rule as `Int` (constants in `lib.rs` mirror
   `java.awt.AlphaComposite`).
2. Implement `#[no_mangle] pub extern "system" fn Java_net_runelite_awt_AwtNative_<name>` in
   `rust/src/lib.rs`, with the loop in the matching module (`fill.rs`, `line.rs`, `poly.rs`,
   `blit.rs`). The Rust side pins arrays with `GetPrimitiveArrayCritical` for one call: no JNI calls,
   no allocation that can throw, no escape of the pointer while pinned.
3. Add a host `#[test]` in the module for the loop over a plain `Vec<i32>`; `cargo test` in
   `android-awt/rust` is the check. Bump `NATIVE_ABI_VERSION` only when an existing signature changes.
4. Call it from the backend class in `net.runelite.awt.impl`, never from the shadow class directly.

The Java-side fallbacks in `BufferedImageGraphics2D` stay; they are what the host contract tests run.

## 8. How a card proves itself

- `:android-awt:compileDebugJavaWithJavac` compiles; `:android-awt:assembleDebug
  :android-awt:lintAnalyzeDebug` is the full module check (lint catches `android.jar` collisions and
  forbidden packages).
- `:android-awt:awtSurfaceReport` no longer lists the class (and `-Pmembers=true -PclassFilter=<Class>`
  lists no missing members).
- `:android-awt:shadowOwnershipCheck` still reports `duplicates: 0` (the new class is not one
  `android.jar` already defines).
- A behavioural contract goes in `tools-src/Test<Area>Contracts.java`: a `main` that exercises the
  ported behaviour with plain assertions, with `// host-contract` as its first line. `python
  tools-src/run-awt-contracts.py` (`--only Test<Area>Contracts` while iterating, `--list` to see what
  it compiles, `--java-home` for a JDK other than `JAVA_HOME`) runs it twice, on the plain host JDK and
  with the shadow patched over it, and both runs must pass, so a contract can only assert what the
  real JDK also does. There is no source list to maintain: the runner compiles every shadow source
  that reaches nothing from `android.*` or the Kotlin JNI facade (transitively, where the host JDK has
  no class to fall back on), drops what javac rejects against the JDK's own classes, and patches every
  compiled class whose JDK twin can be replaced without a JDK class that is still loaded calling a
  member the shadow lacks; `--list` names each compile-only class with the member and caller that
  demoted it, which is also the cheapest parity hint for the next card. Header options when a test
  needs them: `runs=patched` when it reaches shadow-only classes, `patched-args=shadow` to guard a
  check that asserts a documented divergence from the JDK, `fixture=keystore` for a throw-away TLS
  keystore. The old drivers (`test-swing-contracts.py`, `test-swing-dialog-contracts.py`,
  `run-runtime-contracts.py`) are wrappers over the runner. Headless-only behaviour (`AudioTrack`,
  clipboard, focus) is not provable on the host and is stated as device-verified in the report.
- `cargo test` in `android-awt/rust` when the change touched the crate.
- Anything that needs an APK on a phone is verified by the person; the report says so.

## 9. Checklist printed by `port-jdk-class.py`

For `python tools-src/port-jdk-class.py java.awt.geom.QuadCurve2D [--from jdk17u] [--dry]`:

1. the source file, its tag/commit from `JDK-REFERENCE.md`, and the destination path;
2. every import that `android.jar` lacks, each marked `shadowed` (already in `src/main/java`) or
   `MISSING` (a dependency to port or cut);
3. every `sun.*` / `com.sun.*` reference with line numbers (the peer coupling of section 3);
4. every `native` method (section 7, or pure Java);
5. every `@since 9+` member (17u sources) and every Java 9+ language or API use the regexes
   recognise (section 4 and 5);
6. the existing shadow file it would replace, with a unified diff of the public/protected API of
   the two files, so a port never silently drops a member the stub already had.

The script copies the file byte-for-byte; every change after that is a human edit that the
checklist accounts for.
