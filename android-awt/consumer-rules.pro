# ============================================================================
# android-awt — consumer ProGuard rules
#
# This module reimplements java.awt.*, javax.swing.*, javax.sound.*, javax.imageio.*,
# sun.* and com.sun.* on top of Android so AWT-using bytecode (the patched OSRS
# gamepack and RuneLite's Swing UI code) can link at dex time.
# ============================================================================

# ----------------------------------------------------------------------------
# JDK shadow surface. Outside code (the gamepack, RuneLite client, plugins) calls
# into these classes by their canonical JDK names. Renaming any of them — even
# private members of public classes — risks breaking a reflection lookup or a
# Class.forName call inside the obfuscated client jar. Keep the entire surface.
# ----------------------------------------------------------------------------
-keep class java.awt.** { *; }
# Android supplies JavaBeans callbacks; keep their shadow declarations compatible.
-keep class java.beans.** { *; }
-keep class javax.swing.** { *; }
-keep class javax.sound.** { *; }
-keep class javax.imageio.** { *; }
-keep class sun.** { *; }
-keep class com.sun.** { *; }

# ----------------------------------------------------------------------------
# JNI bridges. The Rust crate (`runelite-awt-native`) registers its entry points
# by mangled C names — `Java_net_runelite_awt_AwtNative_blit`, etc. The JVM looks
# them up by FQN + method name at first call; if R8 renames the class or any of
# the @JvmStatic external methods, dlsym misses and the AWT software rasterizer
# falls over with UnsatisfiedLinkError on the first paint.
#
# This is the only part of net.runelite.awt that must keep its name. Everything
# else (the impl/ subpackage, the headless graphics device, the audio bridge,
# the font registry, the rasterizer hot path) is reached via direct Java
# references from the shadow surface and gets renamed consistently by R8.
# ----------------------------------------------------------------------------
-keep class net.runelite.awt.AwtNative { *; }
-keep class net.runelite.awt.AwtCompat { *; }

# ----------------------------------------------------------------------------
# rlawt shim. Published with a stable FQN that the injected runelite-client jar
# references by string from its AWTContext factory path. Small surface, kept
# entirely — obfuscating it would force re-keying every release against an
# upstream that doesn't know to follow the rename.
# ----------------------------------------------------------------------------
-keep class net.runelite.rlawt.** { *; }
