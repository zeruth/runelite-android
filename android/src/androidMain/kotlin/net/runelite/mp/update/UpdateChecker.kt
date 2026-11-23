package net.runelite.mp.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import net.runelite.mp.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-update against GitHub releases, since we don't ship through the Play Store. CI
 * publishes each release as tag `<major>.<minor>.<patch>_<RuneLite version>` with a single
 * `runelite-android_<tag>.apk` asset (see .github/workflows/CI.yml).
 */
internal object UpdateChecker
{
    private const val TAG = "UpdateChecker"
    private const val REPO = "zeruth/runelite-android"
    private const val API = "https://api.github.com/repos/$REPO"
    private const val PREFS = "update_checker"
    private const val CONTRIBUTORS_TTL_MS = 24L * 60 * 60 * 1000

    /** GitHub login → name shown on the splash, for contributors who'd rather not be a handle. */
    private val DISPLAY_NAMES = mapOf(
        "zeruth" to "Zeruth",
        "JayBuckley7" to "Jay",
        "JayBuckleyUpdox" to "Jay", // Jay's alt account
        "torkkotravis-creator" to "Torkko",
        "JaredEzz" to "Boomball_01",
    )

    /** Used until the first successful fetch (eg. first launch offline). */
    private val FALLBACK_CONTRIBUTORS = listOf("zeruth", "JayBuckley7", "torkkotravis-creator")

    /** This build's release tag, in the same format CI tags releases with. */
    val currentTag: String get() = BuildConfig.RELEASE_TAG

    data class Release(val tag: String, val apkName: String, val apkUrl: String, val apkSize: Long)

    /** The latest published release if it is newer than this build, else null. */
    fun newerRelease(): Release?
    {
        return try
        {
            val json = JSONObject(get("$API/releases/latest"))
            val tag = json.getString("tag_name")
            if (compareTags(tag, currentTag) <= 0) return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length())
            {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk"))
                    return Release(tag, name, asset.getString("browser_download_url"), asset.optLong("size", -1))
            }
            Log.w(TAG, "release $tag has no APK asset")
            null
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "release check failed", t)
            null
        }
    }

    /**
     * Repo owner first, then authors of merged PRs by PR count. Upstream RuneLite's own
     * contributors are deliberately left out (credited as "RuneLite" by the caller).
     * Cached for a day; returns the cached/fallback list when offline.
     */
    fun contributors(context: Context): List<String>
    {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cached = prefs.getString("contributors", null)?.split(',')?.filter { it.isNotEmpty() }
        val fresh = System.currentTimeMillis() - prefs.getLong("contributorsAt", 0) < CONTRIBUTORS_TTL_MS
        val logins = if (cached != null && fresh) cached else try
        {
            val owner = JSONObject(get(API)).getJSONObject("owner").getString("login")
            val counts = LinkedHashMap<String, Int>()
            for (page in 1..3)
            {
                val pulls = JSONArray(get("$API/pulls?state=closed&per_page=100&page=$page"))
                for (i in 0 until pulls.length())
                {
                    val pr = pulls.getJSONObject(i)
                    if (pr.isNull("merged_at")) continue
                    val user = pr.optJSONObject("user") ?: continue
                    if (user.optString("type") == "Bot") continue
                    val login = user.getString("login")
                    counts[login] = (counts[login] ?: 0) + 1
                }
                if (pulls.length() < 100) break
            }
            val list = listOf(owner) + counts.entries.sortedByDescending { it.value }.map { it.key }.filter { it != owner }
            prefs.edit().putString("contributors", list.joinToString(",")).putLong("contributorsAt", System.currentTimeMillis()).apply()
            list
        }
        catch (t: Throwable)
        {
            Log.w(TAG, "contributor fetch failed", t)
            cached ?: FALLBACK_CONTRIBUTORS
        }
        // Several accounts can belong to one person; credit them once
        return logins.map { DISPLAY_NAMES[it] ?: it }.distinct()
    }

    /** Downloads [release] into the cache, reporting progress in 0..1 (or -1 when the size is unknown). */
    fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File
    {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, release.apkName)
        val conn = open(release.apkUrl)
        try
        {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReport = 0L
                    while (true)
                    {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (read - lastReport > 256 * 1024)
                        {
                            lastReport = read
                            onProgress(if (total > 0) read.toFloat() / total else -1f)
                        }
                    }
                }
            }
        }
        finally { conn.disconnect() }
        onProgress(1f)
        check(signedLikeUs(context, target)) { "Downloaded update isn't signed with this app's key" }
        return target
    }

    /** Hands the APK to the system installer; Android asks for "install unknown apps" the first time. */
    fun install(context: Context, apk: File)
    {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Installs [apk] through a PackageInstaller session, for watches where there's no room
     * for the update card. On Android 12+ the session asks not to need the user, which the
     * system honours once this app is the installer of record (ie. after the first update
     * it installed itself); until then [UpdateInstallReceiver] launches the confirm screen.
     * The result arrives at that receiver; on success the system replaces this process.
     */
    fun installSession(context: Context, apk: File)
    {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable so the installer can fill in the status extras.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val callback = PendingIntent.getBroadcast(context, id, Intent(context, UpdateInstallReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    /** The installer would reject a mismatch with a vague "App not installed"; catch it with a clear message. */
    private fun signedLikeUs(context: Context, apk: File): Boolean
    {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        val pm = context.packageManager
        val ours = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
        val theirs = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo ?: return false
        val ourCerts = ours?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        return theirs.apkContentsSigners.map { it.toCharsString() }.toSet() == ourCerts
    }

    /** Tags compare by app version, then RuneLite version, numerically per segment. */
    internal fun compareTags(a: String, b: String): Int
    {
        fun parts(tag: String) = tag.split('_', '.', '-').map { it.toIntOrNull() ?: 0 }
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size))
        {
            val c = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
            if (c != 0) return c
        }
        return 0
    }

    private fun get(url: String): String
    {
        val conn = open(url)
        try
        {
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode} for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        }
        finally { conn.disconnect() }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "runelite-android/${BuildConfig.RELEASE_TAG}")
        }
}
