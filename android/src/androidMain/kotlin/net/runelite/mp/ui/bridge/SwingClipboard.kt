package net.runelite.mp.ui.bridge

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Carries the Swing system clipboard over Android's: text and HTML as ClipData text, an image as a PNG
 * and a file list as content URIs, both served by the app's FileProvider out of `cache/clipboard`.
 * Flavours Android cannot carry stay in-process (the shadow Clipboard keeps the Transferable while the
 * host still holds what it exported).
 */
internal class SwingClipboard(private val activity: ComponentActivity) : AutoCloseable {
    private val platform = activity.getSystemService(ClipboardManager::class.java)
    private val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { clipboard.refreshFromBridge() }
    private val resolver = activity.contentResolver
    // Shares the update provider: its paths file also exposes cache/clipboard, and a second
    // FileProvider entry would need its own subclass.
    private val authority = "${activity.packageName}.updates"
    private val root = File(activity.cacheDir, "clipboard")
    private var generation = 0

    private val bridge = object : Clipboard.Bridge {
        override fun readText(): String? {
            val clip = primary() ?: return null
            return if (clip.itemCount == 0) null else clip.getItemAt(0).text?.toString()
        }

        override fun writeText(text: String) {
            platform.setPrimaryClip(ClipData.newPlainText(LABEL, text))
        }

        override fun readStamp(): Any? = primary()?.let(::stamp)

        override fun readContents(): Transferable? = primary()?.let(::importClip)

        override fun writeContents(contents: Transferable): Any? = exportClip(contents)
    }

    init {
        clipboard.setBridge(bridge)
        platform.addPrimaryClipChangedListener(listener)
    }

    override fun close() {
        platform.removePrimaryClipChangedListener(listener)
        clipboard.clearBridge(bridge)
    }

    private fun primary(): ClipData? {
        // Android 10+ hands background apps an empty clip; report it as unavailable so the
        // in-process contents survive instead of being cleared.
        if (!activity.hasWindowFocus()) throw IllegalStateException("Clipboard is unavailable while unfocused")
        return platform.primaryClip
    }

    /** What identifies a clip across a write and a later read: item payloads, not the description. */
    private fun stamp(clip: ClipData): List<List<String?>> = (0 until clip.itemCount).map { index ->
        val item = clip.getItemAt(index)
        listOf(item.text?.toString(), item.htmlText, item.uri?.toString())
    }

    private fun importClip(clip: ClipData): Transferable? {
        if (clip.itemCount == 0) return null
        val first = clip.getItemAt(0)
        val text = first.text?.toString()
        val html = first.htmlText
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            .filter { it.scheme == "content" || it.scheme == "file" }
        val flavors = LinkedHashMap<DataFlavor, () -> Any>()
        if (html != null) {
            for (flavor in HTML_FLAVORS) flavors[flavor] = { html }
        }
        if (text != null) {
            flavors[DataFlavor.stringFlavor] = { text }
        } else if (html != null) {
            flavors[DataFlavor.stringFlavor] = { Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString() }
        }
        if (uris.isNotEmpty()) {
            val type = resolver.getType(uris[0]) ?: clip.description.getMimeType(0)
            if (uris.size == 1 && type != null && type.startsWith("image/")) {
                flavors[DataFlavor.imageFlavor] = { importImage(uris[0]) }
            }
            flavors[DataFlavor.javaFileListFlavor] = { importFiles(uris) }
        }
        return if (flavors.isEmpty()) null else HostTransferable(flavors)
    }

    private fun importImage(uri: Uri): BufferedImage =
        (resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")).use { ImageIO.read(it) }
            ?: throw IOException("Undecodable image at $uri")

    /** Files of another app come as content URIs; javaFileListFlavor needs Files, so they are copied in. */
    private fun importFiles(uris: List<Uri>): List<File> {
        val dir = fresh("in")
        return uris.mapIndexed { index, uri ->
            if (uri.scheme == "file") return@mapIndexed File(requireNotNull(uri.path) { "file URI without a path: $uri" })
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "file"
            // One directory per item keeps two same-named files apart and the name path-free.
            val target = File(File(dir, index.toString()).apply { mkdirs() }, File(name).name)
            (resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")).use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target
        }
    }

    private fun exportClip(contents: Transferable): Any? {
        val text = contents.string(DataFlavor.stringFlavor)
        val html = contents.transferDataFlavors.firstOrNull {
            it.primaryType == "text" && it.subType == "html" && it.representationClass == String::class.java
        }?.let { contents.string(it) }
        val uris = when {
            contents.isDataFlavorSupported(DataFlavor.imageFlavor) ->
                listOf(exportImage(contents.getTransferData(DataFlavor.imageFlavor) as Image))
            contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ->
                exportFiles((contents.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>())
            else -> emptyList()
        }
        if (text == null && html == null && uris.isEmpty()) return null
        val mimeTypes = LinkedHashSet<String>()
        when {
            html != null -> mimeTypes += ClipDescription.MIMETYPE_TEXT_HTML
            text != null -> mimeTypes += ClipDescription.MIMETYPE_TEXT_PLAIN
        }
        uris.forEach { mimeTypes += resolver.getType(it) ?: "application/octet-stream" }
        // An HTML item needs a plain-text twin for apps that only paste text.
        val plain = text ?: html?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
        val clip = ClipData(ClipDescription(LABEL, mimeTypes.toTypedArray()), ClipData.Item(plain, html, null, uris.firstOrNull()))
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        platform.setPrimaryClip(clip)
        return stamp(clip)
    }

    private fun exportImage(image: Image): Uri {
        val buffered = image as? BufferedImage ?: BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB).also {
            val graphics = it.createGraphics()
            graphics.drawImage(image, 0, 0, null)
            graphics.dispose()
        }
        val file = File(fresh("out"), "image.png")
        if (!ImageIO.write(buffered, "png", file)) throw IOException("No PNG writer for the clipboard image")
        return FileProvider.getUriForFile(activity, authority, file)
    }

    /** The provider only serves cache/clipboard, so exported files are copied there rather than exposing their roots. */
    private fun exportFiles(files: List<File>): List<Uri> {
        val dir = fresh("out")
        return files.filter { it.isFile }.mapIndexed { index, file ->
            val target = File(File(dir, index.toString()).apply { mkdirs() }, file.name)
            file.copyTo(target, overwrite = true)
            FileProvider.getUriForFile(activity, authority, target)
        }
    }

    /**
     * A new directory per transfer: the URI (and so the stamp) of every export differs, and the
     * previous transfer of the same direction is deleted, since the host no longer holds it.
     */
    @Synchronized
    private fun fresh(direction: String): File {
        val parent = File(root, direction)
        parent.deleteRecursively()
        return File(parent, (++generation).toString() + "-" + System.currentTimeMillis()).apply {
            if (!mkdirs()) throw IOException("Cannot create $this")
        }
    }

    private fun Transferable.string(flavor: DataFlavor): String? =
        if (isDataFlavorSupported(flavor)) getTransferData(flavor) as? String else null

    /** Host clip contents, decoded on first request (images and file copies are not free). */
    private class HostTransferable(private val loaders: Map<DataFlavor, () -> Any>) : Transferable {
        private val loaded = HashMap<DataFlavor, Any>()

        override fun getTransferDataFlavors(): Array<DataFlavor> = loaders.keys.toTypedArray()

        override fun isDataFlavorSupported(flavor: DataFlavor?): Boolean = flavor != null && loaders.containsKey(flavor)

        @Synchronized
        @Throws(UnsupportedFlavorException::class, IOException::class)
        override fun getTransferData(flavor: DataFlavor?): Any {
            if (flavor == null) throw UnsupportedFlavorException(null)
            val loader = loaders[flavor] ?: throw UnsupportedFlavorException(flavor)
            return loaded.getOrPut(flavor) {
                // A revoked URI grant surfaces as SecurityException; callers only expect IOException.
                try { loader() } catch (denied: SecurityException) { throw IOException(denied) }
            }
        }
    }

    private companion object {
        const val LABEL = "RuneLite"
        val HTML_FLAVORS = listOf(DataFlavor.allHtmlFlavor, DataFlavor.fragmentHtmlFlavor, DataFlavor.selectionHtmlFlavor)
    }
}
