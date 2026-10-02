package net.runelite.mp.hd

import android.util.Log
import java.awt.Canvas
import javax.swing.SwingUtilities
import net.runelite.client.config.ConfigManager
import net.runelite.client.plugins.PluginManager
import net.runelite.client.plugins.gpugles.GlesHost
import net.runelite.client.plugins.gpugles.GpuGlesPlugin
import rs117.hd.AndroidSupport
import rs117.hd.HdPlugin
import rs117.hd.HdPluginConfig

object AndroidHdHost : AndroidSupport.Host {
    private lateinit var manager: PluginManager
    fun configure(pm: PluginManager, config: ConfigManager) {
        manager = pm
        AndroidSupport.host = this
        val hd = pm.plugins.firstOrNull { it is HdPlugin }
        if (hd == null) {
            Log.e("AndroidHdHost", "117 HD was not registered; keeping the existing renderer")
            return
        }
        // Once-only mobile defaults. Further launches retain the user's settings.
        if (config.getConfiguration("runelite-mp", "hdPrototypeDefaults") == null) {
            val group = HdPluginConfig.CONFIG_GROUP
            mapOf("drawDistance" to "35", "shadowMode" to "OFF", "shadowResolution" to "RES_1024",
                "dynamicLights" to "NONE", "tiledLighting" to "false", "sceneResolutionScale" to "75",
                "legacyRenderer2" to "false").forEach { (key, value) -> config.setConfiguration(group, key, value) }
            config.setConfiguration("runelite-mp", "hdPrototypeDefaults", "1")
        }
        if (config.getConfiguration("runelite-mp", "hdPrototypeDefaults") == "1") {
            mapOf("antiAliasingMode" to "MSAA_2", "lowMemoryMode" to "true",
                "modelCacheSizeMiBv2" to "128", "expandedMapLoadingChunks" to "0")
                .forEach { (key, value) -> config.setConfiguration(HdPluginConfig.CONFIG_GROUP, key, value) }
            config.setConfiguration("runelite-mp", "hdPrototypeDefaults", "2")
        }
        Log.i("AndroidHdHost", "117 HD registered, enabled=${pm.isPluginEnabled(hd)}")
    }
    override fun makeCurrent(): Boolean {
        Canvas.setRenderedByGles(true)
        return GlesHost.get().makeCurrent()
    }
    override fun width() = GlesHost.get().width
    override fun height() = GlesHost.get().height
    override fun contextGeneration() = GlesHost.get().contextGeneration()
    override fun stopped() { Canvas.setRenderedByGles(false) }
    override fun failed() {
        Log.e("AndroidHdHost", "117 HD stopped after a renderer error; restoring GPU (GLES)")
        SwingUtilities.invokeLater {
            manager.plugins.firstOrNull { it is GpuGlesPlugin }?.let {
                manager.setPluginEnabled(it, true)
                manager.startPlugin(it)
            }
        }
    }
}
