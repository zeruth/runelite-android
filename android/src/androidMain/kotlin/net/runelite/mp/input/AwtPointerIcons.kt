package net.runelite.mp.input

import android.content.Context
import android.graphics.Bitmap
import android.view.PointerIcon
import java.awt.Cursor
import java.awt.image.BufferedImage
import java.util.WeakHashMap
import net.runelite.awt.impl.ImageCursor

/**
 * Maps an AWT [Cursor] to the [PointerIcon] a mouse or stylus pointer shows: predefined types to the
 * matching system icon, [ImageCursor]s (Toolkit.createCustomCursor) to a bitmap icon at their
 * hotspot, and the drag-and-drop system custom cursors to the copy/grab/alias/no-drop icons.
 */
internal object AwtPointerIcons {
    // Resolved on every hover move; converting the image each time would allocate per event.
    private val images = WeakHashMap<ImageCursor, PointerIcon>() // Android thread only

    fun resolve(context: Context, cursor: Cursor): PointerIcon? {
        if (cursor is ImageCursor) return images.getOrPut(cursor) { bitmapIcon(cursor) ?: system(context, PointerIcon.TYPE_ARROW) }
        val type = when (cursor.type) {
            Cursor.DEFAULT_CURSOR -> PointerIcon.TYPE_ARROW
            Cursor.CROSSHAIR_CURSOR -> PointerIcon.TYPE_CROSSHAIR
            Cursor.TEXT_CURSOR -> PointerIcon.TYPE_TEXT
            Cursor.WAIT_CURSOR -> PointerIcon.TYPE_WAIT
            Cursor.SW_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR -> PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW
            Cursor.NW_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR -> PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
            Cursor.N_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR -> PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW
            Cursor.E_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR -> PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW
            Cursor.HAND_CURSOR -> PointerIcon.TYPE_HAND
            Cursor.MOVE_CURSOR -> PointerIcon.TYPE_ALL_SCROLL
            else -> systemCustom(cursor.name)
        }
        return system(context, type)
    }

    /** Cursor.getSystemCustomCursor names ("CopyDrop32x32", "Invalid32x32", ...). */
    private fun systemCustom(name: String?): Int = when {
        name == null -> PointerIcon.TYPE_ARROW
        name.contains("NoDrop") || name.startsWith("Invalid") -> PointerIcon.TYPE_NO_DROP
        name.startsWith("CopyDrop") -> PointerIcon.TYPE_COPY
        name.startsWith("MoveDrop") -> PointerIcon.TYPE_GRABBING
        name.startsWith("LinkDrop") -> PointerIcon.TYPE_ALIAS
        else -> PointerIcon.TYPE_ARROW
    }

    private fun system(context: Context, type: Int) = PointerIcon.getSystemIcon(context, type)

    private fun bitmapIcon(cursor: ImageCursor): PointerIcon? {
        val image = cursor.image ?: return null
        val width = image.getWidth(null)
        val height = image.getHeight(null)
        if (width <= 0 || height <= 0) return null
        val argb = image as? BufferedImage ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
            val g = it.createGraphics()
            try { g.drawImage(image, 0, 0, null) } finally { g.dispose() }
        }
        val pixels = argb.getRGB(0, 0, width, height, null, 0, width)
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        // PointerIcon rejects a hotspot outside the bitmap; an unloaded image skipped Toolkit's check.
        return PointerIcon.create(bitmap,
            cursor.hotSpotX.coerceIn(0, width - 1).toFloat(), cursor.hotSpotY.coerceIn(0, height - 1).toFloat())
    }
}
