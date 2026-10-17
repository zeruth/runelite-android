package net.runelite.mp.ui.bridge

import android.app.AlertDialog
import android.net.Uri
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.swing.JFileChooser
import javax.swing.JOptionPane

/** Android dialogs for synchronous Swing callers. Only the calling Swing thread waits. */
// This host is ComponentActivity, not FragmentActivity; no Fragment dependency is used.
@android.annotation.SuppressLint("InvalidFragmentVersionForActivityResult")
internal class SwingDialogs(private val activity: ComponentActivity) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val directory = File(activity.cacheDir, "swing-files").apply { mkdirs() }
    private var pending: Pick? = null // Android main thread
    private var confirmation: AlertDialog? = null
    private val watchers = mutableSetOf<FileObserver>()
    private var closed = false
    private class Completion {
        private val latch = CountDownLatch(1)
        private val loop = if (java.awt.EventQueue.isDispatchThread())
            java.awt.Toolkit.getDefaultToolkit().systemEventQueue.createSecondaryLoop() else null
        fun finish() {
            latch.countDown()
            // Queue exit on the EDT: it cannot race ahead of enter on this thread.
            loop?.let { java.awt.EventQueue.invokeLater { it.exit() } }
        }
        fun await() {
            if (loop == null) latch.await()
            else if (latch.count > 0) loop.enter()
        }
    }
    private class Pick(val chooser: JFileChooser, val save: Boolean) {
        val done = Completion()
        @Volatile var result = JFileChooser.CANCEL_OPTION
    }
    private val open = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { finishPick(it) }
    private val save = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { finishPick(it) }

    init {
        // Imported files must outlive showOpenDialog: Swing consumers may read asynchronously.
        directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000 }
            ?.forEach { it.deleteRecursively() }
        JFileChooser.setDialogHandler { chooser, saving ->
            if (Looper.myLooper() == Looper.getMainLooper()) JFileChooser.CANCEL_OPTION
            else {
                val request = Pick(chooser, saving)
                main.post {
                    if (closed || pending != null) request.done.finish()
                    else {
                        pending = request
                        try {
                            if (saving) save.launch(chooser.selectedFile?.name ?: "export.properties")
                            else open.launch(arrayOf("*/*"))
                        } catch (e: Exception) { fail(e); pending = null; request.done.finish() }
                    }
                }
                try { request.done.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                request.result
            }
        }
        JOptionPane.setDialogHandler { message, title, optionType, options ->
            if (Looper.myLooper() == Looper.getMainLooper()) JOptionPane.CLOSED_OPTION
            else {
                val done = Completion()
                var result = JOptionPane.CLOSED_OPTION
                main.post {
                    if (closed) done.finish()
                    else {
                        confirmation?.dismiss()
                        val builder = AlertDialog.Builder(activity).setTitle(title).setMessage(message)
                        if (options != null) {
                            builder.setItems(options.map { it.toString() }.toTypedArray()) { _, index -> result = index }
                        } else {
                            val yesNo = optionType == JOptionPane.YES_NO_OPTION || optionType == JOptionPane.YES_NO_CANCEL_OPTION
                            builder.setPositiveButton(if (yesNo) "Yes" else "OK") { _, _ -> result = JOptionPane.OK_OPTION }
                            if (yesNo) builder.setNegativeButton("No") { _, _ -> result = JOptionPane.NO_OPTION }
                            if (optionType == JOptionPane.OK_CANCEL_OPTION)
                                builder.setNegativeButton("Cancel") { _, _ -> result = JOptionPane.CANCEL_OPTION }
                            if (optionType == JOptionPane.YES_NO_CANCEL_OPTION)
                                builder.setNeutralButton("Cancel") { _, _ -> result = JOptionPane.CANCEL_OPTION }
                        }
                        confirmation = builder.create().also { dialog ->
                            dialog.setOnDismissListener { done.finish() }
                            dialog.show()
                        }
                    }
                }
                try { done.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                result
            }
        }
    }

    private fun finishPick(uri: Uri?) {
        val request = pending ?: return
        if (uri == null) { pending = null; request.done.finish(); return }
        io.execute {
            try {
                val folder = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                val file = File(folder, "selection.properties")
                if (request.save) {
                    // Files.copy may replace the destination. Observe the directory, not its inode.
                    val observer = object : FileObserver(folder.absolutePath, CLOSE_WRITE or MOVED_TO) {
                        private val sent = java.util.concurrent.atomic.AtomicBoolean()
                        override fun onEvent(event: Int, path: String?) {
                            if (path != file.name || !sent.compareAndSet(false, true)) return
                            stopWatching()
                            io.execute {
                                try {
                                    activity.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                                        file.inputStream().use { it.copyTo(output) }
                                    } ?: error("Could not open export destination")
                                    file.delete(); folder.delete()
                                    main.post { Toast.makeText(activity, "File exported", Toast.LENGTH_SHORT).show() }
                                } catch (e: Exception) { fail(e) }
                                finally { main.post { watchers.remove(this) } }
                            }
                        }
                    }
                    main.post {
                        if (!closed) {
                            watchers.add(observer); observer.startWatching()
                            request.chooser.selectedFile = file
                            request.result = JFileChooser.APPROVE_OPTION
                            // If the Swing export fails before opening the file, release the watcher.
                            main.postDelayed({ observer.stopWatching(); watchers.remove(observer) }, 300_000)
                        }
                        pending = null; request.done.finish()
                    }
                } else {
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    } ?: error("Could not open selected file")
                    main.post {
                        if (!closed) { request.chooser.selectedFile = file; request.result = JFileChooser.APPROVE_OPTION }
                        pending = null; request.done.finish()
                    }
                }
            } catch (e: Exception) {
                fail(e)
                main.post { pending = null; request.done.finish() }
            }
        }
    }

    private fun fail(e: Exception) {
        android.util.Log.e("SwingDialogs", "File operation failed", e)
        main.post { if (!closed) Toast.makeText(activity, "File operation failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    fun close() {
        closed = true
        pending?.done?.finish(); pending = null
        confirmation?.dismiss(); confirmation = null
        watchers.forEach { it.stopWatching() }; watchers.clear()
        // Let already queued transfers finish; reject new dialogs through closed above.
        io.shutdown()
    }
}
