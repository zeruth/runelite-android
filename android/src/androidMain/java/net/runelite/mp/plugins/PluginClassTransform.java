package net.runelite.mp.plugins;

import java.util.Set;

/**
 * Every bytecode pass a hub jar entry goes through before D8, in order. PluginDexer and the
 * host fixture check (tools-src/test-runtime-plugin-dex.py) both call this, so the fixtures
 * prove the transform the device runs.
 */
public final class PluginClassTransform {
    private PluginClassTransform() {}

    /** @throws IllegalArgumentException with the reason when the entry cannot run on Android */
    public static byte[] rewrite(String entry, byte[] bytes, Set<String> jarClasses) {
        return IndyLowering.rewrite(entry, DesktopRegexTransformer.rewrite(entry, bytes, jarClasses));
    }
}
