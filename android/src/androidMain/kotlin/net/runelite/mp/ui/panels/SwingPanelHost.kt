package net.runelite.mp.ui.panels

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.runelite.client.ui.PluginPanel
import net.runelite.client.util.SwingUtil
import net.runelite.mp.ui.bridge.NavBarBridge
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.text.JTextComponent

/** Hosts the plugin's existing Swing tree; no plugin-specific model or actions. */
@Composable
internal fun PluginPanelHost(key: String)
{
    var androidView by remember(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (PanelRegistry.hasPanel(key)) {
            Text(
                if (androidView) "Android view · switch to Original" else "Original · switch to Android view",
                color = net.runelite.mp.ui.RlPalette.TextPrimary,
                modifier = Modifier.fillMaxWidth().clickable { androidView = !androidView }.padding(8.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (androidView) PanelRegistry.render(key)
            else key(key) {
                AndroidView(factory = { SwingPanelView(it, key) }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

internal class SwingPanelView(context: Context, private val panelKey: String) : View(context)
{
    private var panel: PluginPanel? = null // AWT thread only
    private var root: JComponent? = null
    private var frame: Bitmap? = null // Android thread only
    private val paint = Paint()
    @Volatile private var running = false
    private var busy = false
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragged = false
    private var error: String? = null
    private val logicalWidth = 240
    private val render = object : Runnable {
        override fun run() {
            if (!running) return
            if (!busy && width > 0 && height > 0) {
                busy = true
                val h = (height * logicalWidth / width).coerceAtLeast(1)
                SwingUtilities.invokeLater {
                    var bitmap: Bitmap? = null
                    var failure: String? = null
                    try {
                        if (running) {
                            if (panel == null) {
                                panel = NavBarBridge.panel(panelKey)
                                root = panel?.wrappedPanel
                                panel?.let { SwingUtil.activate(it) }
                            }
                            val component = root
                            if (component == null) failure = "Panel unavailable: $panelKey"
                            else {
                                component.isVisible = true
                                component.setBounds(0, 0, logicalWidth, h)
                                invalidateTree(component)
                                component.validate()
                                val image = BufferedImage(logicalWidth, h, BufferedImage.TYPE_INT_ARGB)
                                val graphics = image.createGraphics()
                                try {
                                    graphics.color = java.awt.Color(30, 30, 30)
                                    graphics.fillRect(0, 0, logicalWidth, h)
                                    java.awt.Window.hostPaint(graphics, component)
                                } finally { graphics.dispose() }
                                val pixels = image.getRGB(0, 0, logicalWidth, h, null, 0, logicalWidth)
                                bitmap = Bitmap.createBitmap(pixels, logicalWidth, h, Bitmap.Config.ARGB_8888)
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("SwingPanelHost", "Cannot render $panelKey", t)
                        failure = "Panel could not render. See logs."
                    }
                    post {
                        busy = false
                        if (running) { frame = bitmap; error = failure; invalidate() }
                    }
                }
            }
            postDelayed(this, 100)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        post(render)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(render)
        SwingUtilities.invokeLater {
            panel?.let { SwingUtil.deactivate(it) }
            panel = null
            root = null
        }
        frame = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipRect(0, 0, width, height)
        paint.color = android.graphics.Color.rgb(30, 30, 30)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        frame?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), paint) }
        error?.let {
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 14 * resources.displayMetrics.scaledDensity
            canvas.drawText(it, 8f, paint.textSize + 8f, paint)
        }
        canvas.restoreToCount(saved)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scale = logicalWidth.toFloat() / width.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; lastY = event.y; dragged = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (kotlin.math.abs(event.y - downY) > 8 * resources.displayMetrics.density) dragged = true
                val delta = (lastY - event.y) * scale
                if (dragged && kotlin.math.abs(delta) >= 12) {
                    val x = (downX * scale).toInt(); val y = (downY * scale).toInt()
                    SwingUtilities.invokeLater { scroll(x, y, if (delta > 0) 1 else -1) }
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged) {
                    val x = (event.x * scale).toInt(); val y = (event.y * scale).toInt()
                    SwingUtilities.invokeLater {
                        try { tap(x, y) } catch (t: Throwable) {
                            android.util.Log.e("SwingPanelHost", "Panel input failed: $panelKey", t)
                        }
                    }
                    performClick()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun scroll(x: Int, y: Int, direction: Int) {
        val base = root ?: return
        var hit: Component? = hit(base, x, y)
        while (hit != null) {
            if (hit is JScrollPane) {
                hit.dispatchMouseEvent(MouseWheelEvent(hit, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                    0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, direction))
                return
            }
            if (hit === base) return
            hit = hit.parent
        }
    }

    private fun tap(x: Int, y: Int) {
        if (!running) return
        val base = root ?: return
        var target: Component = hit(base, x, y)
        // Compound controls own their editor/renderer children.
        var ancestor: Component? = target
        while (ancestor != null) {
            if (!ancestor.isEnabled) return
            if (ancestor is JComboBox<*> || ancestor is JSpinner) { target = ancestor; break }
            if (ancestor === base) break
            ancestor = ancestor.parent
        }
        when (val control = target) {
            is JTextComponent -> if (control.isEditable) {
                val text = control.text
                post {
                    if (!running) return@post
                    val editor = EditText(context).apply {
                        setText(text)
                        if (control is JPasswordField) inputType = 129
                    }
                    AlertDialog.Builder(context).setTitle("Edit value").setView(editor)
                        .setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ ->
                            val value = editor.text.toString()
                            SwingUtilities.invokeLater {
                                if (running && control.isEnabled && control.isEditable) {
                                    control.text = value
                                    if (control is JTextField) control.postActionEvent()
                                }
                            }
                        }.show()
                }
                return
            }
            is JComboBox<*> -> {
                val items = (0 until control.itemCount).map { control.getItemAt(it) }.toTypedArray()
                post {
                    if (!running) return@post
                    AlertDialog.Builder(context).setTitle("Select value")
                        .setItems(items.map { it.toString() }.toTypedArray()) { _, index ->
                            SwingUtilities.invokeLater { if (running && control.isEnabled) control.selectedItem = items[index] }
                        }.show()
                }
                return
            }
            is JSpinner -> {
                post {
                    if (!running) return@post
                    AlertDialog.Builder(context).setTitle("Adjust value").setItems(arrayOf("Decrease", "Increase")) { _, index ->
                        SwingUtilities.invokeLater {
                            if (running && control.isEnabled) {
                                val next = if (index == 0) control.previousValue else control.nextValue
                                if (next != null) control.value = next
                            }
                        }
                    }.show()
                }
                return
            }
        }
        while (target !== base && target.mouseListeners.isEmpty() && target !is AbstractButton && target !is JTabbedPane)
            target = target.parent ?: break
        var localX = x; var localY = y
        var node: Component? = target
        while (node != null && node !== base) { localX -= node.x; localY -= node.y; node = node.parent }
        for (id in intArrayOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            target.dispatchMouseEvent(MouseEvent(target, id, System.currentTimeMillis(), 0, localX, localY, 1, false, MouseEvent.BUTTON1))
        }
        if (target is AbstractButton && target.isEnabled) target.doClick()
        if (target is JTabbedPane) {
            val index = target.indexAtLocation(localX, localY)
            if (index >= 0) target.selectedIndex = index
        }
    }

    private fun hit(component: Component, x: Int, y: Int): Component {
        if (component is Container) for (child in component.components) {
            if (child.isVisible && x >= child.x && y >= child.y && x < child.x + child.width && y < child.y + child.height)
                return hit(child, x - child.x, y - child.y)
        }
        return component
    }

    private fun invalidateTree(component: Component) {
        component.invalidate()
        if (component is Container) component.components.forEach { invalidateTree(it) }
    }
}
