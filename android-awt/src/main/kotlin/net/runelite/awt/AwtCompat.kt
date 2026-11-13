package net.runelite.awt

import android.util.Log

/**
 * Entry point for the AWT compatibility shim. Call [ensureReady] before any AWT class is
 * touched so the native side has a chance to initialise (logger, color management, etc).
 */
object AwtCompat {
    init {
        System.loadLibrary("runelite_awt_native")
    }

    private const val EXPECTED_ABI_VERSION = 1

    @JvmStatic external fun nativeAbiVersion(): Int

    /**
     * Idempotent. Throws if the native library is the wrong ABI version for this Kotlin
     * code — i.e. someone forgot to rebuild one of the two sides.
     */
    @JvmStatic
    fun ensureReady() {
        val abi = nativeAbiVersion()
        check(abi == EXPECTED_ABI_VERSION) {
            "runelite-awt-native ABI $abi, expected $EXPECTED_ABI_VERSION (rebuild required)"
        }
        // AwtUnsupported stays android-free so the host contracts compile it; the
        // java.util.logging default it falls back to is filtered below INFO on Android.
        AwtUnsupported.setSink { member, reason -> Log.d("AwtUnsupported", "$member is a no-op: $reason") }
    }

    /**
     * Fixes environment variable synchronization bugs on older Android ROMs (like Android 8.0/8.1)
     * by injecting the values directly into the native POSIX runtime layer via Rust.
     */
    @JvmStatic external fun setNativeEnv(key: String, value: String): Int
}
