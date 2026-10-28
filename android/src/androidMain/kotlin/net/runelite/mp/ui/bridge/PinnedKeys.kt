package net.runelite.mp.ui.bridge

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import net.runelite.client.plugins.mobile.MobileConfig

/** Publishes Mobile settings to the toolbar on the Android UI thread. */
object PinnedKeys {
    data class Settings(val keys: List<String> = listOf("Shift", "Alt"), val atBottom: Boolean = false)
    val settings = mutableStateOf(Settings())
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun update(config: MobileConfig) {
        val keys = buildList {
            if (config.pinControl()) add("Ctrl")
            if (config.pinShift()) add("Shift")
            if (config.pinAlt()) add("Alt")
            if (config.pinSpace()) add("Space")
            if (config.pin0()) add("0")
            if (config.pin1()) add("1")
            if (config.pin2()) add("2")
            if (config.pin3()) add("3")
            if (config.pin4()) add("4")
            if (config.pin5()) add("5")
            if (config.pin6()) add("6")
            if (config.pin7()) add("7")
            if (config.pin8()) add("8")
            if (config.pin9()) add("9")
        }
        val next = Settings(keys, config.pinKeysAtBottom())
        handler.post {
            if ("Ctrl" !in keys && ModifierState.controlActive.value) ModifierState.toggleControl()
            if ("Shift" !in keys && ModifierState.shiftActive.value) ModifierState.toggleShift()
            if ("Alt" !in keys && ModifierState.altActive.value) ModifierState.toggleAlt()
            settings.value = next
        }
    }
}
