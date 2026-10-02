package net.runelite.mp

import android.content.Context
import android.util.Log
import net.runelite.mp.crash.AndroidCrashReporter
import java.lang.reflect.Modifier

private const val TAG = "PluginBootstrap"

/**
 * Replicates [PluginManager.loadCorePlugins] on Android. Guava's `ClassPath.from(loader)`
 * relies on URL-based classloader scanning and returns nothing on Android's
 * [dalvik.system.BaseDexClassLoader], so RuneLite ends up with zero registered plugins
 * (and therefore zero toolbar nav buttons).
 *
 * We enumerate classes ourselves by walking each dex file's entries via reflection on
 * `BaseDexClassLoader.pathList.dexElements`, filter to the plugin package, then hand the
 * resulting class list to `PluginManager.loadPlugins` and start them.
 */
object PluginBootstrap {
    /** Every root we scan dex entries under for @PluginDescriptor classes. Comma-namespaced
     *  so we pick up 117HD's {@code rs117.hd} alongside RuneLite's main plugin tree. */
    private val PLUGIN_PACKAGES = listOf(
        "net.runelite.client.plugins",
        "rs117.hd",
    )

    /**
     * Packages that won't load on Android because they depend on desktop-only natives
     * (LWJGL/JOGL/AWTContext). Loading them would fail at first static init when their
     * GL imports can't be resolved — usually caught silently, but Guice still tries
     * to enumerate their @Provides methods and we'd rather skip them up front.
     *
     * The Android GPU renderer lives at net.runelite.client.plugins.gpugles
     * ({@code GpuGlesPlugin}) — it uses Android EGL14/GLES31 instead.
     */
    private val EXCLUDED_PACKAGES = setOf(
        "net.runelite.client.plugins.gpu",
    )

    fun bootstrap(context: Context) {
        val started = System.currentTimeMillis()
        try {
            val injector = getInjector() ?: run {
                Log.e(TAG, "RuneLite.injector is null — RuneLite.start probably failed before reaching plugin loading")
                return
            }

            val pluginManagerClass = Class.forName("net.runelite.client.plugins.PluginManager")
            val getInstance = injector.javaClass.getMethod("getInstance", Class::class.java)
            val pluginManager = getInstance.invoke(injector, pluginManagerClass)
            Log.i(TAG, "got pluginManager: $pluginManager")

            val pluginClasses = enumeratePluginClasses(context)
            Log.i(TAG, "enumerated ${pluginClasses.size} candidate plugin classes")

            val loadPlugins = pluginManagerClass.getMethod(
                "loadPlugins", List::class.java, java.util.function.BiConsumer::class.java
            )
            val plugins = loadPlugins.invoke(pluginManager, pluginClasses, null) as List<*>
            Log.i(TAG, "loaded ${plugins.size} plugins")

            val loadDefaultConfig = pluginManagerClass.getMethod(
                "loadDefaultPluginConfiguration", Collection::class.java
            )
            loadDefaultConfig.invoke(pluginManager, null)
            Log.i(TAG, "loaded default plugin configurations")

            net.runelite.mp.hd.AndroidHdHost.configure(
                pluginManager as net.runelite.client.plugins.PluginManager,
                (injector as com.google.inject.Injector).getInstance(net.runelite.client.config.ConfigManager::class.java)
            )

            val startPlugins = pluginManagerClass.getMethod("startPlugins")
            startPlugins.invoke(pluginManager)
            Log.i(TAG, "started plugins in ${System.currentTimeMillis() - started}ms")

            pinDefaultPluginsIfUnset(injector)

            // Retry external plugin load now that builtins are registered. The first
            // pass ran inside RuneLite.main BEFORE PluginBootstrap, so any plugin-hub
            // plugin that declares an @PluginDependency on a builtin (rasta-hud →
            // XpTrackerPlugin is the canonical case) failed with "Unmet dependency"
            // and never loaded. Re-running loadExternalPlugins picks them up: the
            // already-loaded externals are detected via getPlugins() and skipped, and
            // the previously-failed ones get a second chance with the builtin registry
            // populated. SplashScreen has closed by now so the second pass also
            // applies default config + starts them (same code path as in-session install).
            retryExternalPluginLoad(injector)

            // Hook in the Compose-side listeners for the desktop plugins' "open the
            // sidebar panel for X" callbacks. With HEADLESS_CHROME the Swing sidebar
            // is invisible, so without these the right-click "Lookup", loot-tracker's
            // auto-show, etc. would silently no-op. See HiscoresBridge.installExternalHooks
            // for the wiring.
            try { net.runelite.mp.ui.panels.HiscoresBridge.installExternalHooks() }
            catch (t: Throwable) { Log.w(TAG, "installExternalHooks failed", t) }
        } catch (t: Throwable) {
            Log.e(TAG, "bootstrap failed", t)
            try {
                AndroidCrashReporter.nonFatal(t)
            } catch (crashlyticsError: Throwable) {
                Log.w(TAG, "Failed to report bootstrap exception to Crashlytics", crashlyticsError)
            }
        }
    }

    /** Plugins we want pinned (starred at the top of the Compose plugin list) on first
     *  launch. IDs are full class names — that's what
     *  [net.runelite.mp.ui.bridge.FavoritesBridge] persists in its set. The Compose UI
     *  reads back via the same FQN. */
    private val DEFAULT_PINNED_PLUGINS = listOf(
        "net.runelite.client.plugins.gpugles.GpuGlesPlugin",
        "net.runelite.client.plugins.stretchedmode.StretchedModePlugin",
    )

    /**
     * Seed the Compose-side favorites set on first launch so GPU (GLES) and Stretched
     * Mode appear starred at the top of the plugin list. The set lives under config
     * group `runelite-mp` / key `favorites` (see [FavoritesBridge]) — NOT the desktop
     * RuneLite `runelite/pinnedPlugins` key (the Swing list reads that one, but the
     * Compose UI on Android doesn't). Skip if the user has already set a value: they
     * may have un-favorited the defaults and we don't want to undo that every launch.
     */
    private fun pinDefaultPluginsIfUnset(injector: Any) {
        try {
            val configManagerClass = Class.forName("net.runelite.client.config.ConfigManager")
            val getInstance = injector.javaClass.getMethod("getInstance", Class::class.java)
            val configManager = getInstance.invoke(injector, configManagerClass)
            val getConfig = configManagerClass.getMethod(
                "getConfiguration", String::class.java, String::class.java
            )
            val current = getConfig.invoke(configManager, "runelite-mp", "favorites") as String?
            if (!current.isNullOrEmpty()) {
                Log.i(TAG, "favorites already set ($current); leaving alone")
                return
            }
            val setConfig = configManagerClass.getMethod(
                "setConfiguration", String::class.java, String::class.java, String::class.java
            )
            val value = DEFAULT_PINNED_PLUGINS.sorted().joinToString(",")
            setConfig.invoke(configManager, "runelite-mp", "favorites", value)
            Log.i(TAG, "seeded default favorites=$value")
        } catch (t: Throwable) {
            Log.w(TAG, "could not seed default favorites", t)
        }
    }

    private fun retryExternalPluginLoad(injector: Any) {
        try {
            val epmClass = Class.forName("net.runelite.client.externalplugins.ExternalPluginManager")
            val getInstance = injector.javaClass.getMethod("getInstance", Class::class.java)
            val epm = getInstance.invoke(injector, epmClass)
            val loadExternal = epmClass.getMethod("loadExternalPlugins")
            loadExternal.invoke(epm)
            Log.i(TAG, "retried loadExternalPlugins() after builtin registration")
        } catch (t: Throwable) {
            Log.w(TAG, "retry of loadExternalPlugins() failed", t)
        }
    }

    private fun getInjector(): Any? {
        val runeLiteClass = Class.forName("net.runelite.client.RuneLite")
        val injectorField = runeLiteClass.getDeclaredField("injector")
        injectorField.isAccessible = true
        return injectorField.get(null)
    }

    /**
     * Walk every dex file in the current classloader's pathList and load every class whose
     * name starts with [PLUGIN_PACKAGE]. We don't filter for @PluginDescriptor here — that's
     * PluginManager.loadPlugins' job, and we'd rather over-include than miss a plugin.
     */
    private fun enumeratePluginClasses(context: Context): List<Class<*>> {
        val classLoader = PluginBootstrap::class.java.classLoader
            ?: return emptyList()

        val names = mutableSetOf<String>()
        try {
            val baseDexClass = Class.forName("dalvik.system.BaseDexClassLoader")
            val pathListField = baseDexClass.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathList = pathListField.get(classLoader) ?: return emptyList()

            val dexElementsField = pathList.javaClass.getDeclaredField("dexElements")
            dexElementsField.isAccessible = true
            val dexElements = dexElementsField.get(pathList) as Array<*>

            for (element in dexElements) {
                if (element == null) continue
                val dexFileField = element.javaClass.getDeclaredField("dexFile")
                dexFileField.isAccessible = true
                val dexFile = dexFileField.get(element) ?: continue

                @Suppress("UNCHECKED_CAST")
                val entries = dexFile.javaClass.getMethod("entries")
                    .invoke(dexFile) as java.util.Enumeration<String>
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement()
                    if (PLUGIN_PACKAGES.none { p -> name.startsWith("$p.") }) continue
                    if (EXCLUDED_PACKAGES.any { excl -> name.startsWith("$excl.") || name == excl }) continue
                    names.add(name)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "DEX enumeration failed", t)
            return emptyList()
        }

        val classes = ArrayList<Class<*>>(names.size)
        for (name in names) {
            try {
                val cls = Class.forName(name, false, classLoader)
                // PluginManager only acts on concrete Plugin subclasses with @PluginDescriptor,
                // but it filters internally — we just pass everything that loads.
                if (!Modifier.isAbstract(cls.modifiers) && !cls.isInterface) {
                    classes.add(cls)
                }
            } catch (t: Throwable) {
                // Skip classes whose static init fails — they'd never have worked anyway.
            }
        }
        return classes
    }
}
