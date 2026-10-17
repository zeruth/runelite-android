package net.runelite.mp.account

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import net.runelite.client.account.SessionManager
import net.runelite.mp.MainActivity
import net.runelite.mp.ui.bridge.RuneLiteAccess

/** Keeps the localhost OAuth callback reachable while the browser is foreground. */
class RuneLiteLoginService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var deadline = 0L
    private val monitor = object : Runnable {
        override fun run() {
            val manager = RuneLiteAccess.instance(SessionManager::class.java)
            if (manager == null || !manager.isLoginPending) {
                stopSelf()
            } else if (SystemClock.elapsedRealtime() >= deadline) {
                manager.cancelLogin()
                Toast.makeText(this@RuneLiteLoginService, "RuneLite sign-in timed out. Please try again.", Toast.LENGTH_LONG).show()
                stopSelf()
            } else handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, "RuneLite sign-in", NotificationManager.IMPORTANCE_LOW
        ))
        val returnToApp = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1,
            Intent(this, RuneLiteLoginService::class.java).setAction(CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Signing in to RuneLite")
            .setContentText("Finish signing in with Google in your browser.")
            .setContentIntent(returnToApp).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else startForeground(ID, notification)
        handler.removeCallbacks(monitor)
        if (intent?.action == CANCEL) {
            RuneLiteAccess.instance(SessionManager::class.java)?.cancelLogin()
            stopSelf()
            return START_NOT_STICKY
        }
        val uri = intent?.data
        if (uri == null || !isRuneLiteLogin(uri)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Open the browser only after startForeground has removed background restrictions.
        deadline = SystemClock.elapsedRealtime() + 150_000L
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            handler.postDelayed(monitor, 500)
        } catch (_: android.content.ActivityNotFoundException) {
            RuneLiteAccess.instance(SessionManager::class.java)?.cancelLogin()
            Toast.makeText(this, "No web browser is installed", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        RuneLiteAccess.instance(SessionManager::class.java)?.cancelLogin()
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitor)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "runelite-login"
        private const val ID = 7413
        private const val CANCEL = "net.runelite.mp.CANCEL_RUNELITE_LOGIN"
        fun isRuneLiteLogin(uri: Uri): Boolean {
            if (uri.scheme != "https" || uri.host != "accounts.google.com") return false
            val redirect = uri.getQueryParameter("redirect_uri")?.let(Uri::parse) ?: return false
            return redirect.scheme == "https" && redirect.host == "api.runelite.net" && redirect.path == "/oauth/"
        }
    }
}
