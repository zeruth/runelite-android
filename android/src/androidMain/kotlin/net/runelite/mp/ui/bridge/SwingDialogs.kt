package net.runelite.mp.ui.bridge

import android.app.AlertDialog
import android.net.Uri
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
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
    private val windows = mutableMapOf<java.awt.Window, AlertDialog>()
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
        val folders = linkedSetOf<File>() // Android main thread
        @Volatile var result = JFileChooser.CANCEL_OPTION
    }
    private val open = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { finishPick(listOfNotNull(it)) }
    private val openMany = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { finishPick(it) }
    private val save = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { finishPick(listOfNotNull(it)) }

    init {
        java.awt.Window.setVisibilityHandler handler@{ window, visible ->
            // The client frame and the splash are drawn full-screen by the main activity (AppAndroid);
            // presenting them here would put the whole client in a dialog whose Close runs RuneLite's exit.
            if (window is net.runelite.client.ui.ContainableFrame || window is net.runelite.client.ui.SplashScreen) return@handler
            main.post {
                if (!visible || closed) {
                    // Desktop order on hide: focus leaves, then the window deactivates (CLOSED follows from dispose).
                    if (windows.remove(window)?.also { it.dismiss() } != null) javax.swing.SwingUtilities.invokeLater {
                        window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_LOST_FOCUS))
                        window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_DEACTIVATED))
                    }
                } else if (!windows.containsKey(window)) {
                    val root = (window as? javax.swing.RootPaneContainer)?.rootPane
                    if (root != null) {
                        val view = net.runelite.mp.ui.panels.SwingPanelView(activity, "Swing window", root)
                        val title = (window as? java.awt.Dialog)?.title ?: (window as? java.awt.Frame)?.title
                        val dialog = AlertDialog.Builder(activity).setTitle(title).setView(view).setNegativeButton("Close", null).create()
                        // The close button is the desktop title-bar close: WINDOW_CLOSING lets JFrame/JDialog apply
                        // their default close operation and plugin listeners veto or react, as on the JDK.
                        fun requestClose() {
                            javax.swing.SwingUtilities.invokeLater {
                                if (window is java.awt.Frame || window is java.awt.Dialog)
                                    window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING))
                                else window.isVisible = false
                            }
                        }
                        windows[window] = dialog
                        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { requestClose() } }
                        dialog.setOnDismissListener {
                            if (windows[window] === dialog && !closed) {
                                requestClose()
                                javax.swing.SwingUtilities.invokeLater {
                                    main.post { if (window.isVisible && !closed) dialog.show() }
                                }
                            }
                        }
                        dialog.show()
                        // OPENED was fired by setVisible; the window manager's ACTIVATED then GAINED_FOCUS follow.
                        javax.swing.SwingUtilities.invokeLater {
                            if (window.isVisible) {
                                window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_ACTIVATED))
                                window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS))
                            }
                        }
                        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .9).toInt(), (activity.resources.displayMetrics.heightPixels * .85).toInt())
                    }
                }
            }
        }
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
                            when {
                                chooser.fileSelectionMode == JFileChooser.DIRECTORIES_ONLY -> showDirectory(request)
                                saving -> save.launch(chooser.selectedFile?.name ?: "export.properties")
                                chooser.fileSelectionMode == JFileChooser.FILES_AND_DIRECTORIES -> {
                                    confirmation = AlertDialog.Builder(activity).setTitle(chooser.dialogTitle ?: "Select")
                                        .setItems(arrayOf("File", "Folder")) { _, index ->
                                            if (index == 1) showDirectory(request) else launchOpen(request)
                                        }.setNegativeButton("Cancel") { _, _ -> cancelPick(request) }
                                        .setOnCancelListener { cancelPick(request) }.show()
                                }
                                else -> launchOpen(request)
                            }
                        } catch (e: Exception) { fail(e); pending = null; request.done.finish() }
                    }
                }
                try { request.done.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                request.result
            }
        }
        JOptionPane.setInputHandler { message, title, choices, initial ->
            if (Looper.myLooper() == Looper.getMainLooper()) null
            else {
                val done = Completion()
                var result: Any? = null
                main.post {
                    if (closed) done.finish()
                    else {
                        confirmation?.dismiss()
                        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                        content.addView(messageView(message))
                        val text = if (choices == null) EditText(activity).apply {
                            setSingleLine(true); setText(initial?.toString() ?: ""); selectAll()
                            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        } else null
                        val options = if (choices != null) Spinner(activity).apply {
                            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, choices.map { it.toString() })
                            val index = choices.indexOf(initial)
                            if (index >= 0) setSelection(index)
                        } else null
                        content.addView(text ?: options!!)
                        confirmation = AlertDialog.Builder(activity).setTitle(title).setView(content)
                            .setPositiveButton("OK") { _, _ ->
                                result = text?.text?.toString() ?: choices?.getOrNull(options?.selectedItemPosition ?: -1)
                            }.setNegativeButton("Cancel", null).create().also { dialog ->
                                dialog.setOnDismissListener { done.finish() }
                                dialog.show()
                                if (text != null) {
                                    text.setOnEditorActionListener { _, action, event ->
                                        val enter = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                                            event.action == android.view.KeyEvent.ACTION_DOWN
                                        if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || enter) {
                                            result = text.text.toString()
                                            dialog.dismiss()
                                            true
                                        } else false
                                    }
                                    text.requestFocus()
                                    dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                                }
                            }
                    }
                }
                try { done.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                result
            }
        }
        javax.swing.JColorChooser.setDialogHandler { _, title, initial ->
            if (Looper.myLooper() == Looper.getMainLooper()) null
            else {
                val done = Completion()
                var result: java.awt.Color? = null
                main.post {
                    if (closed) done.finish()
                    else {
                        confirmation?.dismiss()
                        val input = android.widget.EditText(activity).apply {
                            setSingleLine(true)
                            setText(String.format("#%06X", initial.rgb and 0xFFFFFF))
                            hint = "#RRGGBB"
                        }
                        val dialog = AlertDialog.Builder(activity).setTitle(title).setView(input)
                            .setPositiveButton("OK", null).setNegativeButton("Cancel", null).create()
                        dialog.setOnShowListener {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                                try {
                                    result = java.awt.Color(android.graphics.Color.parseColor(input.text.toString()))
                                    dialog.dismiss()
                                } catch (_: IllegalArgumentException) { input.error = "Enter a color such as #FF8800" }
                            }
                        }
                        dialog.setOnDismissListener { done.finish() }
                        confirmation = dialog; dialog.show()
                    }
                }
                try { done.await() } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                result
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
                        val builder = AlertDialog.Builder(activity).setTitle(title)
                        val dateSpinner = message as? javax.swing.JSpinner
                        val datePicker = if (dateSpinner?.model is javax.swing.SpinnerDateModel) android.widget.DatePicker(activity).apply {
                            val cal = java.util.Calendar.getInstance().apply { time = dateSpinner.value as java.util.Date }
                            init(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH), null)
                        } else null
                        if (datePicker != null) builder.setView(datePicker) else builder.setView(messageView(message))
                        if (options != null) {
                            builder.setItems(options.map { it.toString() }.toTypedArray()) { _, index -> result = index }
                        } else {
                            val yesNo = optionType == JOptionPane.YES_NO_OPTION || optionType == JOptionPane.YES_NO_CANCEL_OPTION
                            builder.setPositiveButton(if (yesNo) "Yes" else "OK") { _, _ ->
                                if (datePicker != null && dateSpinner != null) {
                                    val cal = java.util.Calendar.getInstance().apply { clear(); set(datePicker.year, datePicker.month, datePicker.dayOfMonth) }
                                    javax.swing.SwingUtilities.invokeAndWait { dateSpinner.value = cal.time }
                                }
                                result = JOptionPane.OK_OPTION
                            }
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

    private fun messageView(message: Any?): View = when (message) {
        is Array<*> -> LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            message.forEach { addView(messageView(it)) }
        }
        is java.awt.Component -> {
            var root: javax.swing.JComponent? = null
            javax.swing.SwingUtilities.invokeAndWait {
                root = message as? javax.swing.JComponent ?: javax.swing.JPanel(java.awt.BorderLayout()).apply { add(message) }
            }
            net.runelite.mp.ui.panels.SwingPanelView(activity, "Dialog", root!!).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    (activity.resources.displayMetrics.heightPixels * .45f).toInt())
            }
        }
        else -> android.widget.TextView(activity).apply {
            text = message?.toString() ?: ""
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
    }

    private fun mimeTypes(chooser: JFileChooser): Array<String> {
        val filter = chooser.fileFilter as? javax.swing.filechooser.FileNameExtensionFilter ?: return arrayOf("*/*")
        val types = filter.extensions.mapNotNull { android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase(java.util.Locale.ROOT)) }.distinct()
        // An unknown extension cannot be represented by MIME filtering; validate the selected filename instead.
        return if (types.size != filter.extensions.size) arrayOf("*/*") else types.toTypedArray()
    }

    private fun launchOpen(request: Pick) {
        if (request.chooser.isMultiSelectionEnabled) openMany.launch(mimeTypes(request.chooser))
        else open.launch(mimeTypes(request.chooser))
    }

    private fun cancelPick(request: Pick) {
        if (pending === request) pending = null
        request.done.finish()
    }

    private fun completePick(request: Pick, files: Array<File>, folder: File? = null) {
        if (closed || pending !== request) { request.done.finish(); return }
        pending = null
        javax.swing.SwingUtilities.invokeLater {
            try {
                if (folder != null) request.chooser.currentDirectory = folder
                request.chooser.selectedFiles = files
                request.result = JFileChooser.APPROVE_OPTION
            } finally { request.done.finish() }
        }
    }

    private fun showDirectory(request: Pick, current: File = request.chooser.currentDirectory ?: activity.filesDir) {
        val folder = current.takeIf { it.isDirectory && it.canRead() } ?: activity.filesDir
        val children = folder.listFiles()?.filter { it.isDirectory && it.canRead() }?.sortedBy { it.name.lowercase() }.orEmpty()
        if (closed || pending !== request) return
        val multiple = request.chooser.isMultiSelectionEnabled
        val entries = listOf("..") + children.map { it.name }
        val builder = AlertDialog.Builder(activity).setTitle(folder.absolutePath)
            .setItems(entries.toTypedArray()) { _, index ->
                showDirectory(request, if (index == 0) folder.parentFile ?: folder else children[index - 1])
            }.setPositiveButton(if (multiple) "Add folder" else "Select folder") { _, _ ->
                if (multiple) { request.folders.add(folder); showDirectory(request, folder) }
                else completePick(request, arrayOf(folder), folder)
            }.setNegativeButton("Cancel") { _, _ -> cancelPick(request) }
            .setOnCancelListener { cancelPick(request) }
        if (multiple) builder.setNeutralButton("Done (${request.folders.size})") { _, _ ->
            completePick(request, request.folders.ifEmpty { setOf(folder) }.toTypedArray(), folder)
        }
        confirmation = builder.show()
    }

    private fun selectedName(uri: Uri, fallback: String): String {
        val name = activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: fallback
        return name.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() && it != "." && it != ".." } ?: fallback
    }

    private fun finishPick(uris: List<Uri>) {
        val request = pending ?: return
        if (uris.isEmpty()) { cancelPick(request); return }
        io.execute {
            try {
                val folder = File(directory, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                val uri = uris[0]
                val file = File(folder, selectedName(uri, request.chooser.selectedFile?.name ?: "selection.properties"))
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
                        if (!closed && pending === request) {
                            watchers.add(observer); observer.startWatching()
                            // If the Swing export fails before opening the file, release the watcher.
                            main.postDelayed({ observer.stopWatching(); watchers.remove(observer) }, 300_000)
                            completePick(request, arrayOf(file))
                        } else {
                            request.done.finish()
                        }
                    }
                } else {
                    val files = uris.mapIndexed { index, selected ->
                        val itemFolder = File(folder, index.toString()).apply { mkdirs() }
                        val item = File(itemFolder, selectedName(selected, "selection.properties"))
                        activity.contentResolver.openInputStream(selected)?.use { input ->
                            item.outputStream().use { input.copyTo(it) }
                        } ?: error("Could not open selected file")
                        require(request.chooser.accept(item)) { "Selected file does not match ${request.chooser.fileFilter?.description}" }
                        item
                    }
                    main.post {
                        completePick(request, files.toTypedArray())
                    }
                }
            } catch (e: Exception) {
                fail(e)
                main.post { cancelPick(request) }
            }
        }
    }

    private fun fail(e: Exception) {
        android.util.Log.e("SwingDialogs", "File operation failed", e)
        main.post { if (!closed) Toast.makeText(activity, "File operation failed: ${e.message}", Toast.LENGTH_LONG).show() }
    }

    fun close() {
        closed = true
        val openWindows = windows.keys.toList()
        javax.swing.SwingUtilities.invokeLater { openWindows.forEach { it.dispose() } }
        windows.values.toList().forEach { it.dismiss() }; windows.clear()
        pending?.done?.finish(); pending = null
        confirmation?.dismiss(); confirmation = null
        watchers.forEach { it.stopWatching() }; watchers.clear()
        // Let already queued transfers finish; reject new dialogs through closed above.
        io.shutdown()
    }
}
