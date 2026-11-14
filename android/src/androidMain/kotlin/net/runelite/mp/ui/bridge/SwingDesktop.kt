package net.runelite.mp.ui.bridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.awt.Desktop
import java.awt.TrayIcon
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import net.runelite.mp.MainActivity
import net.runelite.mp.account.RuneLiteLoginService

/** Grants other apps per-intent access to the files [SwingDesktop] opens; see `res/xml/desktop_paths.xml`. */
class DesktopFileProvider : FileProvider()

/**
 * The host side of [java.awt.Desktop] and [java.awt.TrayIcon.displayMessage]: browse opens a
 * Custom Tab, open/edit/print hand a content URI to the app registered for the MIME type, mail
 * goes to the mail app, and tray messages become notifications on a channel per message type.
 */
internal class SwingDesktop(activity: ComponentActivity) : AutoCloseable {
    // Calls arrive on the EDT or client thread and may outlive a recreated activity.
    private val context: Context = activity.applicationContext
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val nextId = AtomicInteger(TRAY_ID_BASE)

    private val desktop = object : Desktop.Bridge {
        override fun browse(uri: URI) {
            val target = Uri.parse(uri.toString())
            // Use the real browser for RuneLite/Google sign-in; credentials stay in the browser.
            if (RuneLiteLoginService.isRuneLiteLogin(target)) {
                context.startForegroundService(Intent(context, RuneLiteLoginService::class.java).setData(target))
                return
            }
            when (target.scheme?.lowercase()) {
                "mailto" -> mail(uri)
                // The Custom Tabs wire protocol: the session extra (even null) asks a supporting
                // browser for a tab over the app instead of a full browser switch.
                "http", "https" -> start(Intent(Intent.ACTION_VIEW, target).putExtras(Bundle().apply {
                    putBinder(EXTRA_CUSTOM_TABS_SESSION, null)
                }), "No web browser is installed")
                else -> start(Intent(Intent.ACTION_VIEW, target), "No application can open $uri")
            }
        }

        override fun open(file: File) = view(file, Intent.ACTION_VIEW, "Open with")

        override fun edit(file: File) = view(file, Intent.ACTION_EDIT, "Edit with")

        // Android has no print-a-file intent; a viewer reaches the system print service.
        override fun print(file: File) = view(file, Intent.ACTION_VIEW, "Print with")

        override fun mail(mailtoURI: URI) =
            start(Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoURI.toString())), "No mail application is installed")
    }

    private val tray = TrayIcon.MessageBridge { caption, text, type ->
        if (!notifications.areNotificationsEnabled()) return@MessageBridge
        val returnToApp = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val (channel, icon) = when (type) {
            TrayIcon.MessageType.ERROR -> CHANNEL_ERROR to android.R.drawable.stat_notify_error
            TrayIcon.MessageType.WARNING -> CHANNEL_WARNING to android.R.drawable.stat_sys_warning
            TrayIcon.MessageType.INFO -> CHANNEL_INFO to android.R.drawable.ic_dialog_info
            else -> CHANNEL_NONE to android.R.drawable.stat_notify_more
        }
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentIntent(returnToApp)
            .setAutoCancel(true)
        if (caption != null) builder.setContentTitle(caption)
        if (text != null) builder.setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
        try {
            notifications.notify(nextId.getAndIncrement(), builder.build())
        } catch (denied: SecurityException) {
            android.util.Log.d("SwingDesktop", "Notification permission is unavailable")
        }
    }

    init {
        notifications.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_ERROR, "Errors", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_WARNING, "Warnings", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_INFO, "Information", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_NONE, "Messages", NotificationManager.IMPORTANCE_LOW),
        ))
        Desktop.setBridge(desktop)
        TrayIcon.setMessageBridge(tray)
    }

    private fun view(file: File, action: String, title: String) {
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.desktop", file)
        } catch (e: IllegalArgumentException) {
            throw IOException("${file.path} is outside the app storage other apps can be granted", e)
        }
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        var grant = Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (action == Intent.ACTION_EDIT) grant = grant or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val intent = Intent(action).setDataAndType(uri, mime).addFlags(grant)
        start(Intent.createChooser(intent, title).addFlags(grant), "No application can open ${file.name}")
    }

    private fun start(intent: Intent, missing: String) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            throw IOException(missing, e)
        }
    }

    override fun close() {
        Desktop.clearBridge(desktop)
        TrayIcon.clearMessageBridge(tray)
    }

    private companion object {
        const val EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"
        const val CHANNEL_ERROR = "awt-tray-error"
        const val CHANNEL_WARNING = "awt-tray-warning"
        const val CHANNEL_INFO = "awt-tray-info"
        const val CHANNEL_NONE = "awt-tray-none"
        // Clear of the login service's foreground notification id.
        const val TRAY_ID_BASE = 0x7A00
    }
}
