package net.runelite.mp.plugins

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import net.runelite.client.externalplugins.PluginHubHooks

/** Where one hub plugin is in download → verify → convert → load → start. */
internal sealed interface InstallPhase
{
    /** [fraction] is null until the first bytes arrive. */
    data class Downloading(val fraction: Float?) : InstallPhase
    data object Converting : InstallPhase
    data object Loading : InstallPhase
    data class Ready(val pluginNames: List<String>) : InstallPhase
    data class Failed(val stage: PluginHubHooks.Stage, val message: String, val detail: String) : InstallPhase
}

internal data class InstallStatus(
    val internalName: String,
    val displayName: String,
    val phase: InstallPhase,
    /** Downloaded or converted during this session, as opposed to loaded from cache at boot. */
    val fresh: Boolean,
    /** The user tapped Install (or Retry) this session; only these get toasts. */
    val requested: Boolean = false,
    val at: Long = System.currentTimeMillis(),
)

/**
 * Collects [PluginHubHooks] events (and the dexer's own convert start/finish) into Compose
 * state for the hub tab. Hook calls arrive on RuneLite's executor; every state write is
 * posted to the main thread so Compose sees ordered, single-threaded updates.
 */
internal object PluginInstallTracker : PluginHubHooks.Listener
{
    val statuses = mutableStateMapOf<String, InstallStatus>()

    /** A hub-wide problem (manifest unreachable / bad signature), shown above the list. */
    val hubErrors = mutableStateListOf<String>()

    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private val lastPercent = HashMap<String, Int>()

    fun init(context: Context) { appContext = context }

    fun dismiss(internalName: String) = post { statuses.remove(internalName) }
    fun clearHubErrors() = post { hubErrors.clear() }

    /** The user tapped Install / Retry: show it as queued until the manager reports in. */
    fun queued(internalName: String, displayName: String) = post {
        statuses[internalName] = InstallStatus(internalName, displayName, InstallPhase.Downloading(null), fresh = true, requested = true)
    }

    fun converting(internalName: String, displayName: String?)
    {
        status("converting", internalName)
        update(internalName, displayName, fresh = true) { InstallPhase.Converting }
    }

    fun converted(internalName: String, millis: Long)
    {
        status("converted", internalName, "ms=$millis")
        update(internalName, null) { InstallPhase.Loading }
    }

    override fun downloadStarted(internalName: String, displayName: String?, jarSize: Int)
    {
        status("download", internalName, "size=$jarSize")
        lastPercent.remove(internalName)
        update(internalName, displayName, fresh = true) { InstallPhase.Downloading(null) }
    }

    override fun downloadProgress(internalName: String, bytes: Long, jarSize: Int)
    {
        if (jarSize <= 0) return
        val percent = (bytes * 100 / jarSize).toInt().coerceIn(0, 100)
        // CountingInputStream reports per read; a state write per chunk would be pointless.
        if (lastPercent.put(internalName, percent) == percent) return
        update(internalName, null) { InstallPhase.Downloading(percent / 100f) }
    }

    override fun downloadFinished(internalName: String) = update(internalName, null) { InstallPhase.Loading }

    override fun loaded(internalName: String, displayName: String?, pluginNames: MutableList<String>)
    {
        val names = pluginNames.toList()
        status("loaded", internalName, "plugins=" + names.joinToString("|"))
        update(internalName, displayName) { InstallPhase.Ready(names) }
        post {
            val status = statuses[internalName] ?: return@post
            if (!status.requested) return@post
            toast("${status.displayName} installed")
            statuses[internalName] = status.copy(requested = false)
        }
    }

    override fun failed(internalName: String?, displayName: String?, stage: PluginHubHooks.Stage, cause: Throwable?)
    {
        val message = describe(stage, cause)
        val detail = cause?.stackTraceToString().orEmpty()
        status("failed", internalName ?: "-", "stage=$stage message=" + message.replace('\n', ' '))
        if (internalName == null)
        {
            post { if (message !in hubErrors) hubErrors += message }
            return
        }
        update(internalName, displayName) { InstallPhase.Failed(stage, message, detail) }
        post {
            val status = statuses[internalName] ?: return@post
            if (!status.requested) return@post
            toast("${status.displayName}: $message")
            statuses[internalName] = status.copy(requested = false)
        }
    }

    private fun update(internalName: String, displayName: String?, fresh: Boolean = false, phase: () -> InstallPhase) = post {
        val old = statuses[internalName]
        val next = InstallStatus(
            internalName = internalName,
            displayName = displayName ?: old?.displayName ?: internalName,
            phase = phase(),
            fresh = fresh || old?.fresh == true,
            requested = old?.requested == true,
        )
        statuses[internalName] = next
        // A finished install clears itself; failures stay until retried or cleared.
        if (next.phase is InstallPhase.Ready) main.postDelayed({
            val current = statuses[internalName]
            if (current != null && current.phase is InstallPhase.Ready && current.at == next.at) statuses.remove(internalName)
        }, READY_DISMISS_MS)
    }

    private const val READY_DISMISS_MS = 3_000L

    private fun describe(stage: PluginHubHooks.Stage, cause: Throwable?): String
    {
        var root = cause
        while (root?.cause != null && root.cause !== root) root = root.cause
        val why = (cause as? PluginHubHooks.DexException)?.message
            ?: root?.let { it.message?.takeIf(String::isNotBlank) ?: it.javaClass.simpleName }
        return when (stage)
        {
            PluginHubHooks.Stage.MANIFEST -> "Couldn't reach the Plugin Hub" + (why?.let { " ($it)" } ?: "")
            PluginHubHooks.Stage.DOWNLOAD -> "Download failed" + (why?.let { ": $it" } ?: "")
            PluginHubHooks.Stage.VERIFY -> "Download was corrupted (hash mismatch) — try again"
            PluginHubHooks.Stage.DEX -> "Couldn't convert for Android" + (why?.let { ": $it" } ?: "")
            PluginHubHooks.Stage.LOAD -> "Couldn't load" + (why?.let { ": $it" } ?: "")
            PluginHubHooks.Stage.START -> "Failed to start" + (why?.let { ": $it" } ?: "")
        }
    }

    /**
     * One machine-readable logcat line per install transition, `HUB <event> internal=<name> …`
     * under [STATUS_TAG]; tools-src/test-hub-plugins.py keys its status matrix off these, so
     * keep the shape stable (plugins= and message= run to the end of the line).
     */
    private fun status(event: String, internalName: String, extra: String = "")
    {
        Log.i(STATUS_TAG, "HUB $event internal=$internalName" + if (extra.isEmpty()) "" else " $extra")
    }

    private const val STATUS_TAG = "RlHubStatus"

    private fun toast(text: String)
    {
        val ctx = appContext ?: return
        Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
    }

    private fun post(block: () -> Unit)
    {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}
