package net.runelite.mp

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.runelite.mp.crash.AndroidCrashReporter

class MainActivity : ComponentActivity() {
    companion object {
        lateinit var instance: MainActivity

        fun hideSystemUI() {
            val window = instance.window
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    init {
        instance = this
    }
    private lateinit var swingDialogs: net.runelite.mp.ui.bridge.SwingDialogs
    private lateinit var swingClipboard: net.runelite.mp.ui.bridge.SwingClipboard
    private lateinit var swingDesktop: net.runelite.mp.ui.bridge.SwingDesktop

    // RECORD_AUDIO is asked for the first time a TargetDataLine opens, not at launch: the game
    // never captures, so only a plugin that records ever shows the prompt.
    private val recordPermissionLock = Any()
    private var pendingRecordPermission: java.util.concurrent.CompletableFuture<Boolean>? = null
    private val recordPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> takePendingRecordPermission()?.complete(granted) }
    private val recordPermission = net.runelite.awt.impl.AndroidAudioBridge.RecordPermission { requestRecordPermission() }

    private fun takePendingRecordPermission() = synchronized(recordPermissionLock) {
        pendingRecordPermission.also { pendingRecordPermission = null }
    }

    /** Called on the thread opening the line; blocks it until the user answers the prompt. */
    private fun requestRecordPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return true
        }
        // The prompt's answer arrives on the UI thread, so the UI thread cannot wait for it.
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper() || isDestroyed) return false
        val future = synchronized(recordPermissionLock) {
            pendingRecordPermission ?: java.util.concurrent.CompletableFuture<Boolean>().also { created ->
                pendingRecordPermission = created
                runOnUiThread {
                    if (isDestroyed) takePendingRecordPermission()?.complete(false)
                    else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        return try {
            future.get()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    override fun onDestroy() {
        // A recreated activity installs its own handler in onCreate; only clear ours.
        if (net.runelite.awt.impl.AndroidAudioBridge.getRecordPermission() === recordPermission) {
            net.runelite.awt.impl.AndroidAudioBridge.setRecordPermission(null)
        }
        takePendingRecordPermission()?.complete(false)
        net.runelite.client.plugins.mobile.MobileDisplaySettings.detach()
        if (::swingDialogs.isInitialized) swingDialogs.close()
        if (::swingClipboard.isInitialized) swingClipboard.close()
        if (::swingDesktop.isInitialized) swingDesktop.close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val displayPrefs = getSharedPreferences("mobile-display", MODE_PRIVATE)
        fun applyPortrait(portrait: Boolean) {
            val orientation = if (portrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if (requestedOrientation != orientation) requestedOrientation = orientation
        }
        applyPortrait(displayPrefs.getBoolean("forceVertical", false))
        net.runelite.client.plugins.mobile.MobileDisplaySettings.attach { portrait ->
            runOnUiThread {
                if (!isDestroyed) {
                    displayPrefs.edit().putBoolean("forceVertical", portrait).apply()
                    applyPortrait(portrait)
                }
            }
        }

        // Install before any other init so a crash inside the System.setProperty calls
        // below (or anything else this activity touches) still reaches Crashlytics. The
        // SDK auto-installs its uncaught handler on first getInstance(); this call also
        // stamps device/app context as custom keys.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AndroidCrashReporter.install(applicationContext)
        }

        // RuneLite hard-codes ~/.runelite for caches, logs, config, and the patched-client
        // backup. Map both user.home and the temp dir into our private app storage so all
        // file operations land somewhere we have permission to write.
        val home = filesDir.absolutePath
        System.setProperty("user.home", home)
        System.setProperty("user.dir", home)
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)
        val metrics = resources.displayMetrics
        net.runelite.awt.impl.HeadlessGraphicsConfiguration.updateDisplay(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        window.decorView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (view.width > 0 && view.height > 0)
                net.runelite.awt.impl.HeadlessGraphicsConfiguration.updateDisplay(view.width, view.height, resources.displayMetrics.densityDpi)
        }

        // Fullscreen + edge-to-edge, hide system bars (immersive sticky behavior).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Draw under the camera notch / display cutout instead of leaving a black bar
        // there — the AWT chrome (sidebar, title bar) is empty in that strip otherwise.
        // ALWAYS lets the layout extend into the cutout in any orientation; SHORT_EDGES
        // is the fallback for API 28–29 where ALWAYS doesn't exist.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        // Browse (incl. the RuneLite sign-in redirect), open/edit/print, mail and tray messages.
        swingDesktop = net.runelite.mp.ui.bridge.SwingDesktop(this)
        net.runelite.awt.impl.AndroidAudioBridge.setRecordPermission(recordPermission)
        swingDialogs = net.runelite.mp.ui.bridge.SwingDialogs(this)
        swingClipboard = net.runelite.mp.ui.bridge.SwingClipboard(this)
        setContent { AndroidApp() }
    }

    /**
     * Re-assert immersive mode whenever the window regains focus.
     *
     * From API 30 the framework keeps the bars hidden itself once
     * [WindowInsetsControllerCompat] has a real `WindowInsetsController` to drive. Below
     * that it falls back to the legacy `SYSTEM_UI_FLAG_*` bits, which the platform *clears*
     * when the window loses focus and never puts back — so on Android 8 the status and
     * navigation bars stay on screen after the soft keyboard (or a dialog, or the
     * notification shade) goes away, and the game is left letterboxed under them.
     * Re-applying on focus gain is the documented remedy, and it's a no-op on the versions
     * that don't need it.
     *
     * Safe against the swipe-to-reveal gesture: focus doesn't change when the transient
     * bars appear, so this never fights BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        net.runelite.mp.ui.bridge.ModifierState.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    /**
     * Every key press reaches the game before the view hierarchy — that's what makes a
     * hardware keyboard work in-game — except while something else is genuinely being
     * typed into.
     *
     * Without that exception the Compose UIs lose their editing keys. Ordinary characters
     * survive because IMEs commit those as text rather than as key events, but backspace
     * arrives as `KEYCODE_DEL`, [net.runelite.mp.ui.bridge.KeyDispatch] maps it to
     * `VK_BACK_SPACE`, reports it handled, and the focused text field never sees it. Same
     * for enter and the arrow keys, and for anything typed on a hardware keyboard.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (editorWantsKeys()) {
            return super.dispatchKeyEvent(event)
        }
        if (net.runelite.mp.ui.bridge.KeyDispatch.sendAndroidKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * True when a text editor other than the game's own input bridge holds the IME — i.e.
     * a Compose text field is being edited, so keys belong to it.
     *
     * [net.runelite.mp.ui.bridge.KeyboardInputView] is the game's IME target and is
     * explicitly *not* counted: when it has focus the on-screen keyboard is typing into
     * the game, and those keys have to keep going to the AWT pipeline. `isAcceptingText`
     * covers everything else, and is false when no editor is connected at all — the normal
     * in-game case, hardware keyboard included.
     */
    private fun editorWantsKeys(): Boolean {
        if (currentFocus is net.runelite.mp.ui.bridge.KeyboardInputView) {
            return false
        }
        val imm = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        return imm?.isAcceptingText == true
    }
}
