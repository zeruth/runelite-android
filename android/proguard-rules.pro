# ============================================================================
# runelite-mp — release ProGuard / R8 rules
#
# The runtime architecture here makes name-obfuscation tricky: a patched copy
# of the Jagex OSRS gamepack is dexed into the APK and mixin-injected via
# annotations whose targets are string class names; RuneLite plugins are
# discovered through a Guava classpath scan; configs are Guice-injected; HTTP
# DTOs are Gson-bound. Each of those is a reflection layer that needs its
# names preserved.
#
# Strategy: keep names on everything reflection touches, let R8 obfuscate the
# rest (Compose internals, helper classes, Kotlin stdlib). The android-awt AAR
# ships its own consumer-rules.pro for the JDK shadow surface — we don't
# duplicate those here.
# ============================================================================

# ----------------------------------------------------------------------------
# Stack traces. The Crashlytics gradle plugin auto-uploads mapping.txt to
# Firebase when isMinifyEnabled = true; line numbers must survive for
# deobfuscation to be useful.
# ----------------------------------------------------------------------------
# Keep every class-file attribute. This is broader than strictly necessary but
# catches every Guice/Gson/Reflection-sensitive bit — generic Signature on
# parameters, EnclosingMethod for anon inner classes, all annotation flavours,
# parameter names. R8 has been stripping the Signature attribute off some
# @Inject constructor parameter inside RuneLite's tree, which breaks Guice's
# Key.canonicalizeForKey with a ClassCastException at injector creation. The
# precise culprit is hard to identify; this rule preempts the whole class.
-keepattributes *
-keepparameternames
-renamesourcefileattribute SourceFile

# Annotation interfaces themselves. RuneLite's @ConfigGroup, @Named, @PluginDescriptor
# and similar markers are meta-annotated with @BindingAnnotation / @Qualifier — Guice
# checks for that meta-annotation at injection time. If R8 obfuscates the annotation
# interface, the meta-annotation reference goes with it and Guice throws
# "X is not a binding annotation" at injector creation. Keep all annotation types
# and their members (annotations carry their values as interface methods).
-keep class * extends java.lang.annotation.Annotation { *; }
-keep @java.lang.annotation.Annotation class * { *; }
-keepclassmembers class * extends java.lang.annotation.Annotation { *; }

# Guice's marker annotations that other annotations meta-reference.
-keep class com.google.inject.BindingAnnotation { *; }
-keep class com.google.inject.ScopeAnnotation { *; }
-keep class javax.inject.Qualifier { *; }
-keep class javax.inject.Scope { *; }

# ----------------------------------------------------------------------------
# Jagex obfuscated gamepack. Lives in the default package as 1-3 char tokens
# (`client`, `te`, `tf`, `da`). Mixin injection re-targets specific methods on
# these classes at build time, then reflection from the runtime config picks
# the entry class by name (ClientLoader.java:197-198). Keep everything in the
# default package — these names are already short, so R8 has nothing to win
# by mangling them.
# ----------------------------------------------------------------------------
-keep class !**.* { *; }

# Synthetic helpers from the desugar pipeline land in `net/runelite/mp/util/`
# (IndyConcat) and `runelite/desugar/` (Lambda). The injected jar calls them
# by name; keep both.
-keep class net.runelite.mp.util.** { *; }
-keep class runelite.desugar.** { *; }

# Downloaded hub plugins reach H2 through the parent loader after APK shrinking.
-keep class org.h2.** { *; }
# Optional paths inside the retained engine: JDBC 4.2 SQLType overloads, scripted
# triggers, StAX SQLXML and JTS geometry conversion. Android does not supply these;
# ordinary JDBC/string-backed dictionaries do not use them. Keep this list scoped.
-dontwarn java.sql.SQLType
-dontwarn java.sql.JDBCType
-dontwarn javax.script.**
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.transform.stax.**
-dontwarn org.locationtech.jts.**
# H2's h2.threadDeadlockDetector diagnostics are disabled by default.
-dontwarn java.lang.management.LockInfo
-dontwarn java.lang.management.MonitorInfo
-dontwarn java.lang.management.ThreadInfo

# ----------------------------------------------------------------------------
# RuneLite client + API + cache. Lombok generates equals/hashCode/toString
# that reflect on field names, plugins reach into client internals via
# reflection bridges (RuneLiteAccess.kt), and the injected gamepack calls
# back into net.runelite.api.* via the mixin-targeted hook methods. Keep
# the whole surface; the obfuscation win on this tree is small anyway since
# the names are short.
# ----------------------------------------------------------------------------
-keep class net.runelite.api.** { *; }
-keep class net.runelite.client.** { *; }
-keep class net.runelite.cache.** { *; }
-keep class net.runelite.rs.** { *; }
-keep class net.runelite.http.api.** { *; }

# Mixin annotations specifically — referenced by string-name during injection.
# Annotation classes live in the `:mixins` module under net.runelite.mixins.annotations
# (NOT net.runelite.api.mixins, which doesn't exist). The injected gamepack carries
# these annotations on methods like `client.copyInit()`; R8 sees the references on
# the gamepack side and we have to keep the annotation classes around for it to link.
-keep class net.runelite.mixins.annotations.** { *; }
-keep class net.runelite.mixins.** { *; }
-keep @net.runelite.mixins.annotations.Mixin class * { *; }
-keepclassmembers class * {
    @net.runelite.mixins.annotations.Inject *;
    @net.runelite.mixins.annotations.Replace *;
    @net.runelite.mixins.annotations.MethodHook *;
    @net.runelite.mixins.annotations.FieldHook *;
    @net.runelite.mixins.annotations.Shadow *;
    @net.runelite.mixins.annotations.Copy *;
    @net.runelite.mixins.annotations.When *;
}

# ----------------------------------------------------------------------------
# RuneLite plugin discovery. PluginManager.java:260 scans for @PluginDescriptor;
# every annotated class plus its config interface needs its name kept. Config
# interfaces are proxied at runtime by Guice + a custom ConfigManager so each
# method (= config item) name is the database key.
# ----------------------------------------------------------------------------
-keep @net.runelite.client.plugins.PluginDescriptor class * { *; }
-keep class * extends net.runelite.client.plugins.Plugin { *; }
-keep interface * extends net.runelite.client.config.Config
-keepclassmembers interface * extends net.runelite.client.config.Config {
    public *;
}
-keep class * implements net.runelite.client.config.Config { *; }

# ----------------------------------------------------------------------------
# Guice. Constructor / field / method @Inject, @Provides on modules. cglib-style
# proxy generation may use the class name; keep Module impls intact.
# ----------------------------------------------------------------------------
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers class * {
    @com.google.inject.Inject <init>(...);
}
-keepclassmembers class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
    @com.google.inject.Inject <fields>;
    @com.google.inject.Inject <methods>;
    @com.google.inject.Provides <methods>;
}
-keep class * implements com.google.inject.Module { *; }

# Guice's own internals MUST NOT be renamed or vertical-merged. The library uses
# `(ParameterizedType) someClass.getGenericSuperclass()` casts throughout — when
# R8 merges Guice's anonymous Key / TypeLiteral helpers, the Signature attribute
# is lost and that cast fails with ClassCastException at injector creation. Also
# keep its internal SPI used by binder setup.
-keep class com.google.inject.** { *; }
-keepclassmembers class com.google.inject.** { *; }
-dontwarn com.google.inject.**
-keep class * extends com.google.inject.TypeLiteral
-keep class * extends com.google.inject.AbstractModule { *; }

# Guava is used for plugin classpath scanning, ClassPath reflection, and
# ImmutableMap/List builders that the patched RuneLite client calls into.
# Same generic-signature concern as Guice — keep the whole tree.
-keep class com.google.common.** { *; }
-dontwarn com.google.common.**

# ----------------------------------------------------------------------------
# Gson / JSON. SerializedName fields + transport DTOs. Keep the entire
# PluginHubManifest tree because Gson recurses through nested classes.
# ----------------------------------------------------------------------------
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep the Gson library classes with their ORIGINAL names. External (plugin-hub)
# plugins are downloaded as separate dex jars and reference host classes by their
# original names — they're compiled against the unobfuscated client classpath.
# Without this, R8 renames com.google.gson.* in the APK and every external plugin
# that touches Gson dies at load with
# "NoClassDefFoundError: Failed resolution of: Lcom/google/gson/Gson;"
# (e.g. quest-helper, tile-packs). Same reasoning as the guava/guice keeps above.
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# ----------------------------------------------------------------------------
# Our Compose UI + bridges. RuneLiteAccess et al reflect into runelite-client
# by name; keeping the whole mp.* tree is cheap and avoids surprise breakage.
# ----------------------------------------------------------------------------
-keep class net.runelite.mp.** { *; }

# ----------------------------------------------------------------------------
# 117 HD (android-hd / third-party/rlhd). Only HdPlugin itself matched the plugin
# keeps above, so R8 renamed the rest and moved them out of rs117.hd. ResourcePath
# builds resource paths from clazz.getPackage().getName(); a renamed class in the
# default package has a null Package on ART, so HdPlugin's static init threw and the
# plugin was never registered. Its shaders and JSON resources are also looked up by
# the original package path, Gson binds its DTO fields by name, and configure()
# instantiates singletons reflectively. Keep the whole tree, plus the LWJGL shim
# whose native methods are bound by JNI symbol name (hd_memory.c).
# ----------------------------------------------------------------------------
-keep class rs117.** { *; }
-keep class org.lwjgl.** { *; }

# ----------------------------------------------------------------------------
# Firebase / Crashlytics. The SDK installs ContentProviders and reflects on
# its own classes. Standard recommended config from Firebase docs.
# ----------------------------------------------------------------------------
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ----------------------------------------------------------------------------
# Kotlin / Compose. Most of the Compose machinery handles R8 itself, but
# kotlinx coroutines & serialization need a few attribute keeps.
# ----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class kotlin.Metadata { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ----------------------------------------------------------------------------
# Suppress warnings for desktop-only classes referenced by RuneLite client
# code we ship to Android (paths we never reach, but R8 still flags).
# ----------------------------------------------------------------------------
-dontwarn org.lwjgl.**
-dontwarn org.jogamp.**
-dontwarn com.jogamp.**
-dontwarn jogamp.**
-dontwarn org.fife.**
-dontwarn com.formdev.flatlaf.**
-dontwarn javax.tools.**
-dontwarn java.beans.**
-dontwarn java.awt.color.**
-dontwarn javax.print.**
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.security.**
-dontwarn javax.crypto.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.**
-dontwarn com.google.errorprone.**

# ----------------------------------------------------------------------------
# OkHttp / Okio. Standard recommended -dontwarn for shipping unused codepaths.
# Also KEEP their original names: like Gson above, external plugins are compiled
# against the host classpath and reference okhttp3/okio by original name, so
# renaming them in the APK breaks any plugin that makes HTTP calls.
# ----------------------------------------------------------------------------
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# ----------------------------------------------------------------------------
# Apache Commons (lang3 / text / csv). Bundled transitively via runelite-client
# (NpcUtil, BankSearch, ...), so present in the APK — but external plugins
# reference these by their ORIGINAL names (e.g. quest-helper uses
# org.apache.commons.lang3.tuple.Pair), so R8 renaming them breaks plugin load
# with ClassNotFoundException. Same reasoning as the Gson/OkHttp keeps above.
# ----------------------------------------------------------------------------
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# tomlj + its antlr4 runtime — provided by the host for plugins (resource-packs) that
# shade them; external plugins reference them by original name, so keep names un-renamed.
-keep class org.tomlj.** { *; }
-keep class org.antlr.** { *; }
-dontwarn org.tomlj.**
-dontwarn org.antlr.**

# ----------------------------------------------------------------------------
# Slf4j / Logback. Both ship sources that reference packages we don't bundle.
# ----------------------------------------------------------------------------
-dontwarn org.slf4j.**
-dontwarn ch.qos.logback.**
-keep class ch.qos.logback.** { *; }
-keep class org.slf4j.** { *; }

# ----------------------------------------------------------------------------
# Desktop-only references that the code path never reaches on Android. R8
# would otherwise fail the build because the classes can't be resolved at
# link time. Each entry below is something we deliberately don't ship.
# ----------------------------------------------------------------------------
# JNA platform AWT helpers (DnD, transparent windows, raster utils) — JNA is on
# the classpath via runelite-client's transitive deps, but its platform package
# is a desktop-only Swing toolkit we never reach.
-dontwarn com.sun.jna.platform.**

# JShell — the :jshell module is included for desktop dev tooling but the JDK's
# jdk.jshell.* package isn't present on Android.
-dontwarn jdk.jshell.**
-dontwarn net.runelite.jshell.**

# Quest Helper plugin references a couple of Swing/Path2D classes we don't yet
# shadow in android-awt. The plugin's UI panels won't render fully on Android
# but its event-driven logic still works.
-dontwarn java.awt.dnd.**
-dontwarn java.awt.geom.Path2D**

# Netscape JS bridge — applet-only path, never used on Android.
-dontwarn netscape.javascript.**

# Newer JDK classes referenced by reflection-only paths in the patched client.
-dontwarn java.lang.Runtime$Version
-dontwarn java.lang.reflect.InaccessibleObjectException
-dontwarn java.awt.event.ContainerEvent
-dontwarn java.awt.image.DataBufferByte
-dontwarn java.awt.image.MultiPixelPackedSampleModel
-dontwarn javax.swing.JColorChooser
-dontwarn javax.swing.JTextPane
-dontwarn javax.swing.MenuElement

# macOS-specific full-screen / Dock integration in ClientUI.OSXFullScreenAdapter.
# Never reached on Android, but referenced from class init blocks.
-dontwarn com.apple.eawt.**

# AWT exception classes the AWT shadow surface doesn't declare yet. They're only
# referenced by desktop code paths (TrayIcon, frame opacity) we never reach.
-dontwarn java.awt.AWTException
-dontwarn java.awt.IllegalComponentStateException

# Mixin annotations. The :mixins module is NOT on runelite-mp's runtime classpath
# — it gets consumed at build time by the injector. The gamepack jar carries
# references to these annotations on its bytecode-injected methods, but Android
# never needs to load them at runtime. R8 reports them as "Missing class" since
# nothing on the classpath provides them; suppress the warning.
-dontwarn net.runelite.mixins.annotations.**

# ----------------------------------------------------------------------------
# No optimizer. AGP 9 dropped the conservative proguard-android.txt default, so
# the build uses proguard-android-optimize.txt and switches the optimizer back
# off here. Optimization merges the anonymous TypeLiteral / TypeToken subclasses
# that Guice and Gson read generic supertypes from at runtime; with them merged,
# getGenericSuperclass() returns a plain Class and injector creation fails.
# Name obfuscation and dead-code removal are unaffected.
# ----------------------------------------------------------------------------
-dontoptimize
