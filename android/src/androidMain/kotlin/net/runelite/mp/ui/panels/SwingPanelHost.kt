package net.runelite.mp.ui.panels

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import net.runelite.client.config.ConfigManager
import net.runelite.client.plugins.mobile.MobileConfig
import net.runelite.client.ui.PluginPanel
import net.runelite.client.util.SwingUtil
import net.runelite.mp.ui.bridge.NavBarBridge
import net.runelite.mp.ui.bridge.RuneLiteAccess
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
    key(key) {
        AndroidView(factory = { SwingPanelView(it, key) }, modifier = Modifier.fillMaxSize())
    }
}

internal class SwingPanelView(context: Context, private val panelKey: String) : View(context)
{
    private var panel: PluginPanel? = null // AWT thread only
    private var root: JComponent? = null
    private var mountedGeneration = -1L // AWT thread only
    @Volatile private var mountSerial = 0L
    @Volatile private var attachmentSerial = 0L // Android writes, EDT reads
    private var frame: Bitmap? = null // Android thread only
    // Device-resolution raster + two bitmaps it's copied into, reused across the 10Hz
    // repaint. Ping-pong so the EDT never rewrites the bitmap Android is still drawing.
    private var raster: BufferedImage? = null // AWT thread only
    private val bitmaps = arrayOfNulls<Bitmap>(2) // AWT thread only
    private var backBitmap = 0 // AWT thread only
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    @Volatile private var running = false
    private var busy = false
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragged = false
    private var longPressed = false
    private var secondaryPress = false
    private val clicks = net.runelite.mp.input.PanelClickSequence() // EDT only
    private var touchActive = false
    private var touchMount = -1L
    private var contextPopup: AlertDialog? = null
    private var inputPopup: AlertDialog? = null
    private var swingPopup: JPopupMenu? = null // AWT thread only
    private val longPress = Runnable {
        if (running && touchActive && touchMount == mountSerial && !dragged) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
    private var error: String? = null
    /** Swing layout width in logical px: 240 at 100%, wider (so everything draws smaller
     *  and more fits vertically) as the user lowers Mobile → Sidebar scale. Written by
     *  the UI thread when it schedules a render; touch mapping reads the same value. */
    @Volatile private var logicalWidth = BASE_LOGICAL_WIDTH
    private var diagnosed = false
    private val wheel = net.runelite.mp.input.MouseWheelSteps()
    private val render = object : Runnable {
        override fun run() {
            if (!running) return
            if (!busy && width > 0 && height > 0) {
                busy = true
                val attachment = attachmentSerial
                // Layout stays in logical px (so the panel keeps its size on screen); the
                // raster is the view's real pixel size and the graphics' device scale maps
                // between them, so text and borders are rasterized at native density.
                val pw = width
                val ph = height
                val lw = Math.round(BASE_LOGICAL_WIDTH / sidebarScale())
                logicalWidth = lw
                val h = (ph * lw / pw).coerceAtLeast(1)
                SwingUtilities.invokeLater {
                    var bitmap: Bitmap? = null
                    var failure: String? = null
                    var renderedMount = mountSerial
                    try {
                        if (running && attachment == attachmentSerial) {
                            mountPanel(NavBarBridge.panel(panelKey), panelGeneration())
                            renderedMount = mountSerial
                            val component = root
                            if (component == null) failure = "Panel unavailable: $panelKey"
                            else {
                                component.isVisible = true
                                component.setBounds(0, 0, lw, h)
                                invalidateTree(component)
                                component.validate()
                                if (!diagnosed) {
                                    diagnosed = true
                                    fun describe(c: Component, depth: Int): String =
                                        "${c.javaClass.simpleName} ${c.bounds} visible=${c.isVisible}\n" +
                                            if (c is Container && depth < 5) c.components.joinToString("") { describe(it, depth + 1) } else ""
                                    android.util.Log.i("SwingPanelHost", "$panelKey host=${width}x${height}\n${describe(component, 0)}")
                                }
                                val image = raster?.takeIf { it.width == pw && it.height == ph }
                                    ?: BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB).also { raster = it }
                                val graphics = image.createGraphics()
                                try {
                                    graphics.color = java.awt.Color(30, 30, 30)
                                    graphics.fillRect(0, 0, pw, ph)
                                    (graphics as? net.runelite.awt.impl.BufferedImageGraphics2D)
                                        ?.setDeviceScale(pw.toFloat() / lw)
                                    java.awt.Window.hostPaint(graphics, component)
                                } finally { graphics.dispose() }
                                val target = bitmaps[backBitmap]?.takeIf { it.width == pw && it.height == ph }
                                    ?: Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888).also { bitmaps[backBitmap] = it }
                                target.setPixels(image.backingArray(), 0, pw, 0, 0, pw, ph)
                                backBitmap = backBitmap xor 1
                                bitmap = target
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("SwingPanelHost", "Cannot render $panelKey", t)
                        failure = "Panel could not render. See logs."
                    }
                    post {
                        busy = false
                        if (running && attachment == attachmentSerial && renderedMount == mountSerial) { frame = bitmap; error = failure; invalidate() }
                    }
                }
            }
            postDelayed(this, 100)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachmentSerial++
        running = true
        post(render)
    }

    override fun onDetachedFromWindow() {
        running = false
        attachmentSerial++
        removeCallbacks(render)
        clearAndroidState(mountSerial)
        SwingUtilities.invokeLater {
            mountPanel(null, -1L)
            // Full-res buffers are several MB each; don't park them while the sidebar is shut.
            raster = null
            bitmaps.fill(null)
        }
        super.onDetachedFromWindow()
    }

    private fun panelGeneration(): Long = 0L

    /** The EDT replaces the entire binding, including closures for the previous tree. */
    private fun mountPanel(next: PluginPanel?, generation: Long) {
        if (next === panel && generation == mountedGeneration) return
        clicks.reset()
        swingPopup?.isVisible = false
        swingPopup = null
        panel?.let { SwingUtil.deactivate(it) }
        root = null
        panel = next
        mountedGeneration = generation
        mountSerial++
        diagnosed = false
        root = next?.wrappedPanel
        next?.let { SwingUtil.activate(it) }
        val serial = mountSerial
        post { clearAndroidState(serial) }
    }

    private fun clearAndroidState(serial: Long) {
        if (serial != mountSerial) return
        removeCallbacks(longPress)
        touchActive = false; touchMount = -1L; dragged = false; longPressed = false
        downX = 0f; downY = 0f; lastY = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
        contextPopup?.dismiss(); contextPopup = null
        inputPopup?.dismiss(); inputPopup = null
        frame = null; error = null
        invalidate()
    }

    /** Revalidate on the EDT even when input arrives before the next repaint. */
    private fun isCurrentMount(serial: Long): Boolean = running && serial == mountSerial &&
        panel === NavBarBridge.panel(panelKey) && mountedGeneration == panelGeneration()

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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!running || event.actionMasked != MotionEvent.ACTION_SCROLL) {
            return super.onGenericMotionEvent(event)
        }
        SwingUtilities.invokeLater { clicks.reset() }
        val delta = -event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (delta == 0f) return super.onGenericMotionEvent(event)
        val scale = logicalWidth.toFloat() / width.coerceAtLeast(1)
        val x = (event.x * scale).toInt()
        val y = (event.y * scale).toInt()
        val serial = mountSerial
        SwingUtilities.invokeLater {
            if (isCurrentMount(serial)) {
                val base = root
                var target: Component? = base?.let { hit(it, x, y) }
                while (target != null && target !is JScrollPane && target !== base) target = target.parent
                if (target is JScrollPane) {
                    val steps = wheel.add(delta.toDouble(), target)
                    if (steps != 0) scroll(x, y, steps)
                }
            }
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!running) return false
        val scale = logicalWidth.toFloat() / width.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPress)
                downX = event.x; downY = event.y; lastY = event.y; dragged = false; longPressed = false; touchActive = true; touchMount = mountSerial
                secondaryPress = event.buttonState and MotionEvent.BUTTON_SECONDARY != 0
                if (event.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE)
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) return true
                val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
                if (kotlin.math.abs(event.y - downY) > slop || kotlin.math.abs(event.x - downX) > slop) {
                    if (!dragged) SwingUtilities.invokeLater { clicks.reset() }
                    dragged = true
                    removeCallbacks(longPress)
                }
                val delta = (lastY - event.y) * scale
                if (dragged && !longPressed && !secondaryPress && kotlin.math.abs(delta) >= 12) {
                    val x = (downX * scale).toInt(); val y = (downY * scale).toInt()
                    val serial = touchMount
                    SwingUtilities.invokeLater { if (isCurrentMount(serial)) scroll(x, y, if (delta > 0) 1 else -1) }
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val serial = touchMount
                val currentTouch = touchActive && serial == mountSerial
                val held = currentTouch && (longPressed || secondaryPress) && !dragged
                touchActive = false
                if (held) {
                    // Wait for release before giving another window touch focus.
                    val x = (downX * scale).toInt(); val y = (downY * scale).toInt()
                    SwingUtilities.invokeLater {
                        try { if (isCurrentMount(serial)) contextMenu(x, y) } catch (t: Throwable) {
                            android.util.Log.e("SwingPanelHost", "Panel context menu failed: $panelKey", t)
                        }
                    }
                } else if (currentTouch && !dragged && !longPressed) {
                    val x = (event.x * scale).toInt(); val y = (event.y * scale).toInt()
                    // Capture Android event timestamps/coordinates before queuing work. EDT lag
                    // must not turn a slow pair into a double click or discard a fast pair.
                    val pressedAt = event.downTime
                    val releasedAt = event.eventTime
                    val px = event.x; val py = event.y
                    val device = event.deviceId; val tool = event.getToolType(0)
                    val tolerance = if (tool == MotionEvent.TOOL_TYPE_MOUSE)
                        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                    else ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat()
                    SwingUtilities.invokeLater {
                        try { if (isCurrentMount(serial)) tap(x, y, pressedAt, releasedAt, px, py, device, tool, tolerance) } catch (t: Throwable) {
                            android.util.Log.e("SwingPanelHost", "Panel input failed: $panelKey", t)
                        }
                    }
                    performClick()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                SwingUtilities.invokeLater { clicks.reset() }
                removeCallbacks(longPress)
                touchActive = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun scroll(x: Int, y: Int, direction: Int) {
        val base = root ?: return
        var hit: Component? = hit(base, x, y)
        while (hit != null) {
            if (hit is JScrollPane) {
                hit.dispatchEvent(MouseWheelEvent(hit, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
                    0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, direction))
                return
            }
            if (hit === base) return
            hit = hit.parent
        }
    }

    private fun tap(x: Int, y: Int, pressedAt: Long, releasedAt: Long,
                    px: Float, py: Float, device: Int, tool: Int, tolerance: Float) {
        if (!running) return
        val serial = mountSerial
        val base = root ?: return
        var target: Component = hit(base, x, y)
        // Compound controls own their editor/renderer children.
        var ancestor: Component? = target
        while (ancestor != null) {
            // Swing permits mouse listeners on disabled leaf controls (profile cards
            // use disabled names/arrows to expand). Never invoke their editing/actions.
            if (!ancestor.isEnabled && !(ancestor === target &&
                    (target is JTextComponent || target is AbstractButton) && target.mouseListeners.isNotEmpty())) {
                clicks.reset()
                return
            }
            if (ancestor is JComboBox<*> || ancestor is JSpinner) { target = ancestor; break }
            if (ancestor === base) break
            ancestor = ancestor.parent
        }
        if (target is JTextComponent || target is JComboBox<*> || target is JSpinner) clicks.reset()
        when (val control = target) {
            is JTextComponent -> if (control.isEnabled && control.isEditable) {
                val text = control.text
                post {
                    if (!running || serial != mountSerial) return@post
                    val editor = EditText(context).apply {
                        setText(text)
                        if (control is JPasswordField) inputType = 129
                    }
                    inputPopup?.dismiss()
                    inputPopup = AlertDialog.Builder(context).setTitle("Edit value").setView(editor)
                        .setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ ->
                            val value = editor.text.toString()
                            SwingUtilities.invokeLater {
                                if (isCurrentMount(serial) && control.isEnabled && control.isEditable) {
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
                    if (!running || serial != mountSerial) return@post
                    inputPopup?.dismiss()
                    inputPopup = AlertDialog.Builder(context).setTitle("Select value")
                        .setItems(items.map { it.toString() }.toTypedArray()) { _, index ->
                            SwingUtilities.invokeLater { if (isCurrentMount(serial) && control.isEnabled) control.selectedItem = items[index] }
                        }.show()
                }
                return
            }
            is JSpinner -> {
                post {
                    if (!running || serial != mountSerial) return@post
                    inputPopup?.dismiss()
                    inputPopup = AlertDialog.Builder(context).setTitle("Adjust value").setItems(arrayOf("Decrease", "Increase")) { _, index ->
                        SwingUtilities.invokeLater {
                            if (isCurrentMount(serial) && control.isEnabled) {
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
        val count = clicks.click(target, pressedAt, releasedAt, px.toDouble(), py.toDouble(), device, tool,
            ViewConfiguration.getDoubleTapTimeout().toLong(), tolerance.toDouble())
        // Deliver the first click immediately; only the second gets clickCount=2.
        for (id in intArrayOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            target.dispatchEvent(MouseEvent(target, id, System.currentTimeMillis(),
                if (id == MouseEvent.MOUSE_PRESSED) java.awt.event.InputEvent.BUTTON1_DOWN_MASK else 0,
                localX, localY, count, false, MouseEvent.BUTTON1))
        }
        if (target is AbstractButton && target.isEnabled) target.doClick()
        if (target is JTabbedPane) {
            val index = target.indexAtLocation(localX, localY)
            if (index >= 0) target.selectedIndex = index
        }
    }

    /** Right-click on the Swing thread, then present its real actions on Android. */
    private fun contextMenu(x: Int, y: Int) {
        clicks.reset()
        if (!running) return
        val serial = mountSerial
        val base = root ?: return
        val deepest = hit(base, x, y)
        var node: Component? = deepest
        var owner: JComponent? = null
        var target: Component? = null
        while (node != null) {
            if (!node.isEnabled) return
            if (owner == null && node is JComponent && node.componentPopupMenu != null) owner = node
            if (target == null && node.mouseListeners.isNotEmpty()) target = node
            if (node === base) break
            node = node.parent
        }
        val recipient = target ?: owner ?: deepest
        var localX = x; var localY = y
        node = recipient
        while (node != null && node !== base) { localX -= node.x; localY -= node.y; node = node.parent }
        JPopupMenu.takeShownPopupMenu()
        for (id in intArrayOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            recipient.dispatchEvent(MouseEvent(recipient, id, System.currentTimeMillis(),
                if (id == MouseEvent.MOUSE_PRESSED) java.awt.event.InputEvent.BUTTON3_DOWN_MASK else 0,
                localX, localY, 1, id == MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3))
        }
        val shown = JPopupMenu.takeShownPopupMenu()
        val menu = shown ?: owner?.componentPopupMenu ?: return
        swingPopup = menu
        if (shown == null) {
            menu.show(owner, localX, localY)
            JPopupMenu.takeShownPopupMenu()
        }
        val items = menu.components.filterIsInstance<JMenuItem>().filter { it.isVisible }
        if (items.isEmpty()) { menu.isVisible = false; return }
        val labels = items.map { it.text ?: "" }
        val enabled = items.map { it.isEnabled }
        val checked = items.map { it.isSelected }
        post {
            if (!running || serial != mountSerial) { SwingUtilities.invokeLater { menu.isVisible = false }; return@post }
            contextPopup?.dismiss()
            val panelScale = width.toFloat() / logicalWidth
            val titles = labels.mapIndexed { index, label -> if (checked[index]) "✓ $label" else label }
            val adapter = object : android.widget.ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, titles) {
                override fun areAllItemsEnabled() = enabled.all { it }
                override fun isEnabled(position: Int) = enabled[position]
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                    super.getView(position, convertView, parent).apply {
                        isEnabled = enabled[position]
                        alpha = if (enabled[position]) 1f else 0.45f
                        setBackgroundColor(android.graphics.Color.rgb(30, 30, 30))
                        (this as? android.widget.TextView)?.setTextColor(android.graphics.Color.WHITE)
                        (this as? android.widget.TextView)?.apply {
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 14f * panelScale)
                            minHeight = (36f * panelScale).toInt()
                            setPadding((10f * panelScale).toInt(), 0, (10f * panelScale).toInt(), 0)
                            setSingleLine(true)
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        }
                    }
            }
            val popup = AlertDialog.Builder(context).setAdapter(adapter) { _, index ->
                val item = items.getOrNull(index)
                SwingUtilities.invokeLater {
                    if (isCurrentMount(serial) && item != null && item.isEnabled && item.isVisible) {
                        android.util.Log.i("SwingPanelHost", "Context action $panelKey: ${labels[index]}")
                        item.doClick()
                    }
                }
            }.create()
            popup.setOnDismissListener {
                SwingUtilities.invokeLater { menu.isVisible = false; if (swingPopup === menu) swingPopup = null }
                if (contextPopup === popup) contextPopup = null
            }
            contextPopup = popup
            popup.show()
            popup.listView.setBackgroundColor(android.graphics.Color.rgb(30, 30, 30))
            popup.window?.setLayout(width.coerceAtLeast(240), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            android.util.Log.i("SwingPanelHost", "Context menu $panelKey: ${labels.joinToString()}")
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

    private companion object {
        /** Desktop sidebar width (PluginPanel.PANEL_WIDTH + scrollbar), i.e. 100% scale. */
        const val BASE_LOGICAL_WIDTH = 240

        @Volatile private var configManager: ConfigManager? = null

        /** Mobile → Sidebar scale, read live so a change applies on the next repaint. */
        fun sidebarScale(): Float {
            val cm = configManager
                ?: RuneLiteAccess.instance(ConfigManager::class.java)?.also { configManager = it }
                ?: return 1f
            return try {
                cm.getConfiguration<MobileConfig.SidebarScale?>(
                    MobileConfig.GROUP, MobileConfig.SIDEBAR_SCALE, MobileConfig.SidebarScale::class.java
                )?.scale ?: 1f
            } catch (t: Throwable) {
                1f
            }
        }
    }
}
