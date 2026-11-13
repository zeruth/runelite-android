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
`Desktop`, `SystemTray`, colour management (`java.awt.color`, `ColorConvertOp`: LittleCMS is replaced
by `MatrixCMM`, the matrix/TRC approximation that is exact for the standard profiles and throws
`CMMException` for LUT-only ICC profiles). The public class still carries the OpenJDK signatures and header (what the
caller sees is a port); the body dispatches to a class under `net.runelite.awt.impl` that is written
from scratch, is free to use any Android API, and is renamed by R8. The split keeps the shadow
surface mechanical and puts every "how does Android do this" decision in one package.

The two tests for choosing: does `java.awt.peer`/`sun.java2d` show up in the 8u file's imports, and
does the behaviour depend on the display, the audio device or the file system? Yes to either means
reimplementation behind a ported facade. A class that is half and half (`Component`, `Window`,
`BufferedImage`) is ported, with the peer calls routed to a backend interface rather than removed.

### The event dispatch thread

`EventQueue` runs every `invokeLater`/`invokeAndWait` task on one serial thread, `AWT-InvokeLater`,
and `EventQueue.isDispatchThread()` / `SwingUtilities.isEventDispatchThread()` answer true on that
thread only, as on the JDK. The old rule, true on any thread whose name starts with `AWT-Invoke`
(device probes and bridges used it to impersonate the EDT from a thread of their own), is kept behind
`-Dnet.runelite.awt.edtByName=true` (`System.setProperty` before `java.awt.EventQueue` loads). Never
widen the answer beyond those threads: RuneLite's HTTP interceptor rejects blocking calls made on the
EDT, so a thread wrongly reported as the EDT breaks jav_config loading and login.

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

## 10. java.lang shims in the app

Not every JDK 9+ gap is a shadow class. Where the injected game client calls a `java.*` member
that `android.jar` lacks or that misbehaves on ART, and the owning class is one Android already
defines (so it cannot be shadowed, section 4), the call site is rewritten to a static shim.

**Member retargets** are one table, `JdkRetargets`
(`android/src/androidMain/java/net/runelite/mp/compat/jdk`), applied by one ASM pass,
`net.runelite.mp.compat.JdkRetargeter`, to both the injected client (`DesugarApisTask` in
`ClientTransforms.kt`) and every hub jar (`PluginClassTransform`, ahead of `IndyLowering` and
`PluginDexer`'s D8). A row is (owner, name,
descriptor, dispatch) to a static shim in `net.runelite.mp.compat.jdk`; an instance call becomes a
static call with the receiver first, so the operand stack is unchanged, and method handles
(method references) are retargeted the same way. Types the table relocates (`Runtime$Version` to
`RuntimeVersion`) are renamed in the whole class. Hub jars additionally move `java.util.regex` to
the desktop engine (`net.runelite.mp.compat.regex`, which has the JDK 9 `Matcher` members itself,
so the table's `Matcher` rows apply to the client only) and route the `String` regex methods
through `DesktopRegexStrings`. The rows are the `retarget` disposition of `docs/jdk-api-gaps.json`
plus the JDK 11 `String` members; every other gap there is left to D8 backports and
desugar_jdk_libs. Adding a row: write the shim against JDK 8 (the shims are compiled with
`--release 8` by `tools-src/run-runtime-contracts.py`), add a case to
`tools-src/TestJdkShimContracts.java` comparing it with the JDK member, and bump `FORMAT` in
`PluginDexer` and `TRANSFORM_VERSION` in `ClientPreparer` so cached conversions are redone. The
`jdkRetargetCheck` task in `android/build.gradle.kts` fails the build when android-awt or the app
itself calls a row's member, since nothing retargets their bytecode.

| Row (JDK member) | Shim | Why |
|---|---|---|
| `String.repeat/strip/stripLeading/stripTrailing/isBlank/lines` | `JdkStrings` | JDK 11; the table pins the JDK's semantics whatever D8's backports or libcore do |
| `Files.readString` ×2, `Files.writeString` ×2 | `JdkFiles` | JDK 11; Android has them from API 36.1, desugar_jdk_libs not at all |
| `Runtime.version()` and the type `Runtime$Version` | `JdkRuntime`, `RuntimeVersion` | JDK 9; absent on ART. ART answers feature 8 |
| `ConcurrentHashMap.keySet()KeySetView` | `JdkConcurrent` | android.jar keeps the Java 7 `keySet()Set` descriptor |
| `Matcher.appendReplacement/appendTail(StringBuilder)`, `replaceAll/replaceFirst(Function)`, `results()` | `JdkRegex` | JDK 9, API 34 (client only) |
| `MethodHandles.privateLookupIn` | `JdkInvoke` | JDK 9, API 33; built reflectively |
| `ProcessHandle$Info.command()` (receiver as `Object`) | `JdkProcess` | the command goes into the login packet; cut at a NUL. The `java.lang.ProcessHandle` shadow already answers the NUL-free `/proc/self/exe`, so the row is a guard |

The other rewrites are not member retargets and stay in `ClientTransforms.kt`, client jar only:

| Shim member | Pass (`ClientTransforms.kt`) | Bytecode matched | Rewritten to | Why |
|---|---|---|---|---|
| `IndyConcat.concat(String, Object[], Object[]) String` | `DesugarStringConcatTask` (`ConcatRewriter.visitInvokeDynamicInsn`) | `INVOKEDYNAMIC` with bootstrap `StringConcatFactory.makeConcatWithConstants` | arguments spilled to locals and boxed into `Object[]`, `LDC` recipe, bootstrap constants into `Object[]` (a `ConstantDynamic` over `ConstantBootstraps.invoke` + `H_INVOKESTATIC` becomes the direct static call), then `INVOKESTATIC IndyConcat.concat` | D8 rejects const-dynamic (`UnsupportedFeature(const-dynamic)`), and the client's concat constants are CONDYs; a `toString()` placeholder would leak a NUL into the login packet |
| `JdkStrings.stripNul(String) String` | `DesugarLambdasTask` (first pass collects, `rewriteClass` patches) | every method with descriptor `(Ljava/lang/String;I)V` whose body calls `String.indexOf(I)I` and does `NEW java/lang/IllegalArgumentException` (the client's NUL-terminated string writer, `xi.cq`) | prologue `ALOAD 1; INVOKESTATIC JdkStrings.stripNul; ASTORE 1` at method entry | the writer throws on a NUL; any NUL an Android API slips into the machine-info packet would crash login with "error_game_crash" |
| `UnsafeShim.<constant>` fields | `DesugarLambdasTask` (`LambdaRewriter.visitFieldInsn`) | `GETSTATIC sun/misc/Unsafe.{ADDRESS_SIZE, INVALID_FIELD_OFFSET, ARRAY_*_BASE_OFFSET, ARRAY_*_INDEX_SCALE}` | `GETSTATIC UnsafeShim.<same name and descriptor>` (seeded from a live `Unsafe` in `<clinit>`) | ART's `sun.misc.Unsafe` lacks the static constants |
| `UnsafeShim` off-heap methods | `DesugarLambdasTask` (`LambdaRewriter.visitMethodInsn`) | `INVOKEVIRTUAL sun/misc/Unsafe` `allocateMemory(J)J`, `reallocateMemory(JJ)J`, `freeMemory(J)V`, `setMemory(JJB)V` / `(Ljava/lang/Object;JJB)V`, `copyMemory(JJJ)V` / `(Ljava/lang/Object;JLjava/lang/Object;JJ)V`, `get/put{Byte,Short,Char,Int,Long,Float,Double}(J…)` | arguments spilled to locals, `POP` of the `Unsafe` receiver, arguments reloaded, `INVOKESTATIC UnsafeShim.<same name and descriptor>` | the off-heap members are hidden API on API 30+; the shim emulates off-heap memory with synthetic addresses into `ByteBuffer`s. Heap-array forms stay on ART's `Unsafe` |

`DesugarLambdasTask` itself (every `LambdaMetafactory.metafactory` indy becomes a generated
`runelite/desugar/LambdaN` class, impl methods and their types widened to public) is a language
lowering, not a member retarget, and stays outside the table.

## 11. invokedynamic and dynamic-constant bootstraps

ART at API 26 has `invoke-custom` but none of the JDK bootstrap classes, and no
`const-method-handle` before API 28, so every bootstrap must be gone before the dex reaches the
device: either D8 9.5 (`--min-api 26`) desugars it, or an ASM pass lowers it first. The client jar
goes through `ClientTransforms.kt` before its D8; a hub jar goes through `PluginClassTransform`
(`android/src/androidMain/java/net/runelite/mp/plugins`, `JdkRetargeter` with the desktop regex
relocation, section 10, then `IndyLowering`) before `PluginDexer`'s D8. `python tools-src/indy-inventory.py` lists the kinds
and their call-site counts in `build/hub-cache` (filled by `hub-compat-scan.py`) and the injected
client (`data/`, else `net.runelite:injected-client` at `project.build.version` in the Gradle
cache); a kind it prints that is not a row here needs a row and a fixture.

Inventory of 2026-11-12: all 2927 jars of the 1.13.6 hub manifest plus `injected-client-1.13.6.jar`
(constant-pool entries, i.e. call sites per class, summed):

| Kind | Call sites | Jars | Where |
|---|---|---|---|
| `LambdaMetafactory.metafactory` | 90523 | 2327 | hub (2326 plugins) and the injected client |
| `StringConcatFactory.makeConcatWithConstants` | 57607 | 2163 | hub and the injected client |
| `StringConcatFactory.makeConcatWithConstants[constants]` | 10 | 4 | injected client (3), `inventory-setups-picker`, `irc`, `voiced-dialogue` |
| `condy ConstantBootstraps.invoke` (`ldc`, `H_INVOKESTATIC` target) | 7 | 1 | injected client |
| `condy-arg ConstantBootstraps.invoke` (a `makeConcatWithConstants` constant) | 2 | 1 | injected client |

No hub jar holds `altMetafactory`, `makeConcat`, `ObjectMethods`, `SwitchBootstraps` or any `ldc` of
a dynamic constant: the hub builds at `--release 11` (2926 jars top out at class version 55, one
at 52). The rows past these five are covered ahead
of the plugins that will need them. The runelite-client and runelite-api jars (lambdas and
`makeConcatWithConstants` only) are dexed by AGP's D8, not by either pass here.

| Bootstrap | Emitted by | Client jar | Hub jars | Fixture (`tools-src`) |
|---|---|---|---|---|
| `LambdaMetafactory.metafactory` | lambdas, method references | `DesugarLambdasTask` → `runelite/desugar/LambdaN` (`SyntheticLambdas`); `H_INVOKESPECIAL` impls dispatch `INVOKEVIRTUAL` on the widened method | D8 | `IndyLambdaFixture`; `ClientLambdaFixture` through `TestClientLambdaLowering` |
| `LambdaMetafactory.altMetafactory` (serializable, marker interfaces, bridges) | intersection casts, `Serializable` targets, generic SAM overrides | D8 (not seen in the client) | D8; a serializable lambda calls but does not serialize (no `SerializedLambda` on ART) | `IndyAltLambdaFixture` |
| `StringConcatFactory.makeConcatWithConstants` (with and without bootstrap constants) | `+` on strings, `--release 9`+ | `DesugarStringConcatTask` → `IndyConcat.concat` (section 10) | D8 | `IndyConcatConstantsFixture` |
| `StringConcatFactory.makeConcat` | `-XDstringConcat=indy` | D8 | D8 | `IndyConcatPlainFixture` |
| `ObjectMethods.bootstrap` | records' `equals`/`hashCode`/`toString` | D8 | D8 (record desugaring) | `IndyRecordFixture` |
| `SwitchBootstraps.typeSwitch` (class, `String`, `Integer`, enum labels; `EnumDesc` labels are nested `ConstantBootstraps.invoke` arguments) | pattern `switch`, `--release 21` | D8 | D8 | `IndyTypeSwitchFixture` |
| `SwitchBootstraps.enumSwitch` | pattern `switch` over an enum type | D8 | D8 | `IndyEnumSwitchFixture` |
| `ldc` of a `ConstantBootstraps` dynamic constant (`nullConstant`, `primitiveClass`, `enumConstant`, `getStaticFinal`, `invoke`, `explicitCast`) | obfuscators and bytecode tools, not javac | `DesugarStringConcatTask` → `runelite/desugar/CondyN` holder classes (`invoke` + `H_INVOKESTATIC` only) | `IndyLowering` → a cached `private static synchronized $condy$N` accessor in the same class (resolve-once, own access to private targets) | `IndyCondyFixture`, generated by `TestIndyFixtures condy` |
| `ConstantBootstraps` VarHandle constants (`fieldVarHandle`, `staticFieldVarHandle`, `arrayVarHandle`) | bytecode tools | refused (`DesugarStringConcatTask` names the constant) | refused with the reason (`IndyLowering`); no `VarHandle` at API 26 | none: refusal is the behaviour |
| `ldc` of a dynamic constant with any other static bootstrap (the class's own, JaCoCo's `$jacocoData` shape) | `javac` does not emit one; coverage and bytecode tools do | not present; `DesugarStringConcatTask` stops naming the constant | `IndyLowering` → the same cached accessor, calling the bootstrap with the holder's own `MethodHandles.lookup()` (API 26), the name and the type; D8 9.5 refuses even this shape at every API level ("Unsupported dynamic constant (unsupported constant type)") | `IndySelfCondyFixture`, generated by `TestIndyFixtures selfcondy` |
| Java 21 string templates (`StringTemplate`, preview) | preview-only, removed in JDK 23 | not present | not present (a preview class file cannot load on a release JVM, so hub jars cannot carry it) | none |

`python tools-src/test-runtime-plugin-dex.py` is the proof: each fixture's class files are checked
with the inventory to hold the kinds of its row, run on the host JVM, run again after
`PluginClassTransform` under `-Xverify:all` with identical output, and dexed by D8 9.5 at
min-api 26 into a dex with no call-site and no method-handle items. A dynamic constant `ldc`'d in
an interface is refused by `IndyLowering` with its name: interface fields are final, so there is
no field to cache the resolved value in.

## 12. What finishing the AWT set takes

Measured on 2026-11-12 at `be06cb8f67`, the tip of `awt-staging` (the branch cards land on; `master`
is upstream RuneLite). The set is done when three things hold together:
`:android-awt:awtSurfaceCheck` passes with `surface-baseline.json` empty, `:android-awt:stubReport`
lists only the deliberate stubs below, and `python tools-src/run-awt-contracts.py --verbose` ends in
`Failures: 0, Errors: 0`. On top of that, every card in "Short of JDK 8 parity where the gates
cannot see" below has to be accepted. Each phase's closing card runs all three gates over the
packages it owns, and waits on those cards.

### Surface (`awtSurfaceCheck`)

`surface-baseline.json` lists 21 entries, and 20 of them are on `awt-staging` now, so the gate prints them
under "Baseline entries now present" and the file should be trimmed. `DataFlavor.imageFlavor`,
`Component.contains(II)`, the four `Desktop`/`Taskbar`/`TrayIcon` members, the three `Toolkit`
members, `Frame.setState`, `DragSource.getDragThreshold`, `AffineTransform.rotate(DD)`,
`JDialog.setDefaultLookAndFeelDecorated`, `JFileChooser.setFileHidingEnabled`, the two `JTree`
members and `SwingUtilities.windowForComponent` are declared in their own classes. `JFrame.move`,
`resize` and `reshape` are inherited from `Component`, which declares them. The one gap the gate
sees is `JFrame.setDefaultLookAndFeelDecorated(boolean)` (with its getter
`isDefaultLookAndFeelDecorated()`), which `JDialog` already has and `JFrame` still lacks (#146).

### Short of JDK 8 parity where the gates cannot see

`awtSurfaceCheck` scans only what the injected client, runelite-api and runelite-client reference,
and `stubReport` counts only `AwtUnsupported` calls. So a placeholder class that declares the
referenced members, or a class nothing in those three jars names, passes both gates. These are the
classes short of parity that way, with the open card that owns each:

| Classes | State at `be06cb8f67` | Card |
|---|---|---|
| `GlyphVector` over Android glyphs, `Font.createGlyphVector`/`layoutGlyphVector` | in review | #25 |
| `TextLayout` (measuring stub), `LineBreakMeasurer`, `TextMeasurer` (absent) | short | #26 |
| `JInternalFrame`, `JDesktopPane`, `DesktopManager`, `DefaultDesktopManager` | in review | #56 |
| `BasicInternalFrameUI`, `BasicInternalFrameTitlePane`, `BasicDesktopPaneUI`, `BasicDesktopIconUI` | absent | #143 |
| `GapContent`, `StringContent`, `AbstractDocument`/`DefaultStyledDocument` internals | ported (`AbstractDocument` keeps a `PlainDocument` line map for subclasses that do not declare `getDefaultRootElement`, until `PlainDocument`/`HTMLDocument` are ported; `updateBidi` re-runs over the whole document) | #144 |
| `GlyphView`, `PlainView`, `FieldView`, `WrappedPlainView` and the rest of the view tree | absent | #66 |
| `BasicTextUI` family; `DefaultCaret` (65 lines), `DefaultEditorKit` actions, `JTextArea` (94 lines), `JTextField` painting | absent or placeholder | #67 |
| `JEditorPane` (121 lines), `JFormattedTextField` (88 lines), `JTextPane`, `JPasswordField`, the formatters | placeholder | #68 |
| `javax.swing.text.html` parser, views and `BasicHTML`; `HtmlTextRenderer` to delete | absent | #69 |
| `javax.swing.text.rtf` | absent | #70 |
| `BasicLookAndFeel` defaults tables, `BasicBorders`, `BasicGraphicsUtils`, `BasicIconFactory` | short or absent | #73 |
| painting in nine `J*` classes instead of their Basic delegates | divergent | #142 |
| `javax.swing.plaf.metal` (`MetalLookAndFeel` is a 68-line placeholder) | placeholder | #74 |
| `javax.swing.plaf.synth` | absent | #75 |
| `javax.swing.plaf.nimbus` | absent | #145 |
| FlatLaf painting the sidebar through its own delegates | not wired | #76 |

#73 moved `JButton` painting to `BasicButtonUI` and dropped the shadow's `FlatLaf.styleClass=iconButton`
special case, as on desktop: `SwingUtil.removeButtonDecorations` sets only that style class, and only
FlatLaf's `FlatButtonUI` reads it. So the sidebar's icon buttons (pin star, config gear) lose their
chrome only when `RuneLiteLAF.setup()` in `ClientUI.init` installs FlatLaf. Until #76 wires FlatLaf's
delegates, those buttons paint with the Basic (or Metal) background and border. The person checks this on the
device, and the result is recorded with #76's pixel diffs.

The closing cards #147 (java.awt) and #148 (javax.swing) wait on every card in this table, so they
cannot pass while one of these classes is still short, even though their gates would.

### Stubs (`stubReport`)

There are 21 `AwtUnsupported.fail`/`noop` call sites in the compiled classes, and all of them are
deliberate. In java.awt there are 17: `Robot` 9 (no input injection or screen capture on an unrooted
device), `Desktop` 6 (`open`, `edit`, `print`, `mail` and the two app-event listeners),
`TrayIcon.displayMessage` 1 and the `Paint.createContext` default 1. In java.net.http and
java.lang.management there are 4: `HttpClient.send` with a GET/HEAD body 1, the two `ThreadMXBean`
defaults 2, and the shared `ArtManagement.fail` helper 1. A source grep finds 22, because it also
matches the Javadoc example in `AwtUnsupported` itself, which compiles to nothing.
None is in a path the client or a hub plugin reaches without first checking `isSupported`, so the
ledger is final unless a plugin proves otherwise. The `UnsupportedOperationException`s in
`ColorModel`, `ImageWriteParam`, `ImageReader`/`ImageWriter` and `ServiceRegistry` are the JDK's
own documented throws, not stubs.

### Contract run (`run-awt-contracts.py`): 21 failures, 4 errors over 103 runs, by cause

This is the local run on JDK 21.0.10 with the runner's defaults. CI's run (#140) has 28 failures and
3 errors, 31 runs in all. Every local failing run is also on CI's list. The error counts differ by
one: locally `TestOptionPaneContracts` [patched] is an error (the 180 s timeout), while CI fails
OptionPane in both modes and counts that run as a failure. CI's 3 errors are the same as the other
three local ones: DragAndDrop [patched] and SwingWorker in both modes. CI adds six `[jdk]` runs:
TestAudioSystem (both modes), TestUIManager, TestArea, TestOptionPane and TestWindow. A `[jdk]` run
uses only real JDK classes, so these six point at the CI machine (its JDK build, a headless display,
no audio device) or at the contract, not at the shadow. That is a fifth cause, unconfirmed until it
is reproduced on CI, and #140 owns it with the other four. Matched against CI
by run:

| Cause | Local runs | CI-only runs |
|---|---|---|
| 1. core not patched | 13 | |
| 2. contract wrong on JDK 21 | 8 | |
| 3. contract does not compile | 3 | |
| 4. `Area` donuts | 1 | |
| 5. CI environment | | 6 |
| **Total** | 25 | 6 (31 on CI) |

1. **Core classes are not patched, so a "patched" run is the JDK's core with shadow leaves on top**
   (13 runs, all `[patched]`, one of them an error: `TestUIClassIdContracts`,
   `TestSwingFocusContracts`, `TestOptionPaneContracts` (error, 180 s timeout), `TestWindowContracts`,
   `TestAwtMiscContracts`, `TestAwtWidgetContracts`, `TestFontMetricsContracts`,
   `TestInputMethodContracts`, `TestAccessibilityContracts`, `TestDesktopContracts`,
   `TestImageProducerContracts`, `TestToolkitContracts`, `TestLayoutContracts`; the class names that
   follow are causes, not further runs). `java.awt.Component` reaches `net.runelite.awt.impl.BufferedImageFontMetrics`
   (`android.graphics.Paint`) from `getFontMetrics`, so it is excluded from the host compile, and
   `Container`, `Window`, `JComponent` and `EventQueue` run as the JDK's own. On top of that,
   `Toolkit` is compile-only (the JDK's `KeyboardFocusManager.initPeer` casts it to
   `sun.awt.KeyboardFocusManagerPeerProvider`), `HostToolkit` is rejected by javac (it does not
   implement `mapInputMethodHighlight`), and `TrayIcon` and `MenuComponent` are compile-only
   (JNI-visible field layout). Symptoms: `TestUIClassIdContracts` and `TestSwingFocusContracts`
   [patched] overflow the stack between the shadow `JInternalFrame.hide` and the JDK's
   `Component.show`. `TestOptionPaneContracts` and `TestWindowContracts` hit an NPE in the JDK's
   `EventQueue.removeSourceEvents`, because the patched `InvocationEvent` registers no
   `AWTAccessor`. `TestAwtMiscContracts` (`HeadlessToolkit.loadSystemColors`) and
   `TestAwtWidgetContracts` (`MenuComponent` is headless) fail the same way.
   `TestFontMetricsContracts` and `TestInputMethodContracts` fail because the JDK's
   `FontRenderContext` rejects the shadow `RenderingHints` text-AA value. In
   `TestAccessibilityContracts` the JDK's `JComponent.setUIProperty` rejects `echoChar`.
   `TestDesktopContracts` cannot load `TrayIcon$MessageBridge`, `TestImageProducerContracts` cannot
   load `ProducerImage`, `TestToolkitContracts` cannot load `HostToolkit`, and in
   `TestLayoutContracts` the JDK's `Toolkit` has no `defaultToolkit` field. The fix follows the
   `FontBackend` pattern: `Component.getFontMetrics` reaches the Android metrics through a
   by-name-loaded backend. `HostToolkit` gains `mapInputMethodHighlight`. `Toolkit`, `TrayIcon` and
   `MenuComponent` get the members the drop-in check names in
   `build/compat-tests/awt/compiled-classes.txt`. (The runner strips comments and string literals
   before it matches `android.`, through `STRIP` in `Source.__init__`, so the Javadoc mention of
   `android.graphics.Paint` in `Component.java` is not the cause. The call into
   `BufferedImageFontMetrics` is.)
2. **The contract asserts something the JDK itself does not do** (8 runs: 2 + 2 below on both
   runs, 3 on `[jdk]` only, and `TestSwingDialogContracts` on `[patched]`, which pairs with the
   Chooser one). `TestFontContracts` ×2: the `Font(Map)` constructor keeps POSTURE and SIZE
   null, and `getAttributes` is mutable on JDK 21. `TestListAndScrollContracts` ×2:
   `setSelectedValue(null)` clears the selection. `TestChooserContracts` [jdk] and
   `TestSwingDialogContracts` [patched] fail on "active filter" / "preserve active filter".
   `TestDataTransferContracts` [jdk]: `text/plain; charset` throws
   `StringIndexOutOfBoundsException`, not `IllegalArgumentException`. `TestSwingFocusContracts`
   [jdk]: the JDK's `LayoutComparator` throws `ClassCastException`. The test is wrong in each case.
   It must assert what JDK 21 does, or mark the divergence `patched-args=shadow` (section 8).
3. **The contract no longer compiles against the shadow** (3 runs, all errors: `TestDragAndDropContracts`
   [patched], `TestSwingWorkerContracts` [jdk] and [patched]). `TestDragAndDropContracts` calls
   `JList.setHostShowing`, which is gone; `TestSwingWorkerContracts` subclasses its own `final`
   `Recording`, so neither of its runs compiles.
4. **A real geometry bug** (1 run). `TestAreaContracts` [patched]: `Area` union, subtract,
   intersect and xor of two donuts give a different outline than the JDK's, with stray segments at
   the seam. `Area` is the OpenJDK port over `sun.awt.geom.AreaOp`, `Curve` and `Order0`-`Order3`,
   so the divergence is in those classes, not the `net.runelite.awt.impl` geometry.

Tally: 13 + 8 + 3 + 1 = 25 runs. The 4 errors are `TestOptionPaneContracts` (cause 1) and the
three runs of cause 3; the other 21 are the failures.

Causes 2 and 3 are test-only and can be fixed in parallel. Cause 1 must land before any Swing
contract can be trusted on its patched run. Each phase's closing card on the roadmap (#147 for
java.awt, #148 for javax.swing, #149 for imageio, sound, http and management) holds all three gates
for the classes its phase owns.
