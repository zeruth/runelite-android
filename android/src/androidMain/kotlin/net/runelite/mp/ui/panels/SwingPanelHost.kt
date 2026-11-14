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
import android.view.VelocityTracker
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
import net.runelite.mp.input.PhysicalMouseView
import net.runelite.mp.ui.bridge.NavBarBridge
import net.runelite.mp.ui.bridge.RuneLiteAccess
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
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

internal class SwingPanelView(context: Context, private val panelKey: String, private val hostedRoot: JComponent? = null) : PhysicalMouseView(context)
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
    private class MousePress(val x: Float, val y: Float, val time: Long, val target: Component, var moved: Boolean = false)
    private val mousePresses = mutableMapOf<Int, MousePress>() // Swing thread
    private val clicks = net.runelite.mp.input.PanelClickSequence() // EDT only
    private var touchActive = false
    private var touchMount = -1L
    private var contextPopup: AlertDialog? = null
    private var inputPopup: AlertDialog? = null
    private var comboPopup: Pair<JComboBox<*>, AlertDialog>? = null // Android thread
    @Volatile private var comboGeneration = 0L
    private var swingPopup: JPopupMenu? = null // AWT thread only
    // A press captured by a drag handler (quest-helper's section grips): it gets
    // PRESSED/DRAGGED/RELEASED like a desktop mouse instead of the gesture scrolling.
    private var gesture: Component? = null // AWT thread only
    private var gestureTool = 0 // AWT thread only
    // The component AWT would consider "entered" for a real mouse pointer.
    private var hovered: Component? = null // AWT thread only
    private var hoverPoint = Point() // AWT thread only, root coordinates
    // The hovered component's effective cursor, resolved on the EDT for the pointer icon.
    @Volatile private var hoverCursor: java.awt.Cursor? = null
    // Latest pointer positions in logical px; moves are coalesced into one queued EDT task.
    @Volatile private var dragX = 0
    @Volatile private var dragY = 0
    private val dragQueued = AtomicBoolean()
    @Volatile private var hoverX = 0
    @Volatile private var hoverY = 0
    private val hoverQueued = AtomicBoolean()
    // Windows of the heavy weight popups (tooltips) PopupFactory's host layer shows.
    private val overlays = mutableListOf<android.widget.PopupWindow>() // Android thread only
    // Root point of the finger while a long press shows a tip, so its overlay goes beside it.
    private var touchTip: Point? = null // AWT thread only
    private val dismissTip = Runnable { overlays.toList().forEach { it.dismiss() }; overlays.clear() }
    private val hoverExit = Runnable {
        val serial = mountSerial
        SwingUtilities.invokeLater { if (serial == mountSerial) hover(null) }
    }
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
    // In-process drag and drop (java.awt.dnd): every host draws the drag image while the
    // pointer is over it, and registers its root's place on screen so a drag can cross
    // into another panel. Origins are logical px, like the events.
    private class DragOverlay(val image: Bitmap?, val x: Int, val y: Int, val offsetX: Int, val offsetY: Int,
                              val accepted: Boolean)
    private var dragOverlay: DragOverlay? = null // Android thread only
    private val dragPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    @Volatile private var originX = 0
    @Volatile private var originY = 0
    private var dragSession: net.runelite.awt.impl.DragDropHost.Session? = null // AWT thread only
    private var dragBitmap: Bitmap? = null // AWT thread only
    private val dragListener = object : net.runelite.awt.impl.DragDropHost.Listener {
        override fun dragUpdated(session: net.runelite.awt.impl.DragDropHost.Session) {
            root?.let { net.runelite.awt.impl.DragDropHost.setRootOrigin(it, originX, originY) }
            if (session !== dragSession) {
                dragSession = session
                dragBitmap = bitmapOf(session.dragImage)
            }
            val overlay = DragOverlay(dragBitmap, session.location.x, session.location.y,
                session.dragImageOffset.x, session.dragImageOffset.y, session.isDropAccepted)
            post { updateOrigin(); dragOverlay = overlay; invalidate() }
        }
        override fun dragEnded(session: net.runelite.awt.impl.DragDropHost.Session) {
            if (session === dragSession) { dragSession = null; dragBitmap = null }
            post { dragOverlay = null; invalidate() }
        }
    }

    private fun bitmapOf(image: java.awt.Image?): Bitmap? {
        val buffered = image as? java.awt.image.BufferedImage ?: return null
        val w = buffered.width; val h = buffered.height
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(buffered.getRGB(0, 0, w, h, null, 0, w), w, h, Bitmap.Config.ARGB_8888)
    }

    /** This view's top-left on screen, in logical px. Android thread. */
    private fun updateOrigin() {
        if (width == 0) return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val scale = logicalWidth.toFloat() / width
        originX = (loc[0] * scale).toInt(); originY = (loc[1] * scale).toInt()
    }

    private fun drawDrag(canvas: Canvas) {
        val o = dragOverlay ?: return
        val s = width.toFloat() / logicalWidth.coerceAtLeast(1)
        val px = (o.x - originX) * s; val py = (o.y - originY) * s
        if (px < 0 || py < 0 || px >= width || py >= height) return
        val image = o.image
        if (image != null) {
            val left = px + o.offsetX * s; val top = py + o.offsetY * s
            dragPaint.alpha = 200
            canvas.drawBitmap(image, null, android.graphics.RectF(left, top, left + image.width * s, top + image.height * s), dragPaint)
        } else {
            // No drag image: desktop shows only the drag cursor, so mark where the drop lands.
            dragPaint.color = if (o.accepted) android.graphics.Color.rgb(220, 138, 0) else android.graphics.Color.GRAY
            dragPaint.alpha = 160
            canvas.drawCircle(px, py, 10 * s, dragPaint)
        }
    }

    /** Cancels a drag that started in [base]'s tree; it can no longer receive its release. EDT. */
    private fun cancelDragFrom(base: Component?) {
        val session = net.runelite.awt.impl.DragDropHost.current() ?: return
        if (base != null && net.runelite.awt.impl.DragDropHost.rootOf(session.sourceComponent) === base) session.cancel()
    }

    private var paintedRoot: JComponent? = null // AWT thread only
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
                val lw = if (hostedRoot != null) 600 else Math.round(BASE_LOGICAL_WIDTH / sidebarScale())
                logicalWidth = lw
                val h = (ph * lw / pw).coerceAtLeast(1)
                SwingUtilities.invokeLater {
                    var bitmap: Bitmap? = null
                    var failure: String? = null
                    var renderedMount = mountSerial
                    try {
                        if (running && attachment == attachmentSerial) {
                            if (hostedRoot != null) {
                                if (root !== hostedRoot) { root = hostedRoot; hostedRoot.setHostShowing(true); mountSerial++ }
                            } else mountPanel(NavBarBridge.panel(panelKey), panelGeneration())
                            renderedMount = mountSerial
                            val component = root
                            if (component == null) failure = "Panel unavailable: $panelKey"
                            else {
                                component.isVisible = true
                                component.setBounds(0, 0, lw, h)
                                val resized = raster?.let { it.width != pw || it.height != ph } ?: true
                                val active = android.os.SystemClock.uptimeMillis() - lastInteraction < 500 || !flinger.isFinished
                                // Taken every tick, painted or not, so the region never outlives the frame it asked for.
                                val repaints = javax.swing.RepaintManager.currentManager(component)
                                // Layout changes arrive here too: Component.invalidate() repaints, and a
                                // child the RepaintManager re-laid out repaints from reshape, so the
                                // region and the root's validity are the whole change signal.
                                val requested = repaints.hostTakeDirtyRegion(component)
                                val dirty = component !== paintedRoot || resized || !component.isValid() ||
                                    requested != null
                                if (!dirty && !active) return@invokeLater
                                if (!component.isValid()) component.validate()
                                // Everything past here repaints the whole raster (fillRect + hostPaint of
                                // the root), which covers what the layout pass asked for; requests raised
                                // during paint stay pending for the next frame.
                                repaints.hostTakeDirtyRegion(component)
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
                                paintedRoot = component
                            }
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("SwingPanelHost", "Cannot render $panelKey", t)
                        failure = "Panel could not render. See logs."
                    }
                    finally { post {
                        busy = false
                        if (running && attachment == attachmentSerial && renderedMount == mountSerial && (bitmap != null || failure != null)) { frame = bitmap; error = failure; invalidate() }
                    } }
                }
            }
            // Frame rate while the panel is being touched or scrolled (a drag at the idle
            // cadence moved in 100 ms steps); idle panels only repaint for their own changes.
            val active = android.os.SystemClock.uptimeMillis() - lastInteraction < 500 || !flinger.isFinished
            postDelayed(this, if (active) 16 else 100)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hosts.add(java.lang.ref.WeakReference(this))
        net.runelite.awt.impl.DragDropHost.addListener(dragListener)
        attachmentSerial++
        running = true
        post(render)
    }

    override fun onDetachedFromWindow() {
        hosts.removeAll { it.get() == null || it.get() === this }
        net.runelite.awt.impl.DragDropHost.removeListener(dragListener)
        dragOverlay = null
        running = false
        attachmentSerial++
        removeCallbacks(render)
        clearAndroidState(mountSerial)
        SwingUtilities.invokeLater {
            cancelDragFrom(root)
            root?.let { java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(it); it.setHostShowing(false) }
            mountPanel(null, -1L)
            root = null
            // Full-res buffers are several MB each; don't park them while the sidebar is shut.
            raster = null
            paintedRoot = null
            bitmaps.fill(null)
        }
        super.onDetachedFromWindow()
    }

    private fun panelGeneration(): Long = 0L

    /** The EDT replaces the entire binding, including closures for the previous tree. */
    private fun mountPanel(next: PluginPanel?, generation: Long) {
        if (next === panel && generation == mountedGeneration) return
        clicks.reset()
        mousePresses.clear()
        swingPopup?.isVisible = false
        swingPopup = null
        gesture = null
        hovered = null
        hoverCursor = null
        panel?.let { SwingUtil.deactivate(it) }
        root?.let { java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusIn(it); it.setHostShowing(false) }
        root = null
        panel = next
        mountedGeneration = generation
        mountSerial++
        diagnosed = false
        root = next?.wrappedPanel
        root?.setHostShowing(true)
        next?.let { SwingUtil.activate(it) }
        val serial = mountSerial
        post { clearAndroidState(serial) }
    }

    private fun clearAndroidState(serial: Long) {
        if (serial != mountSerial) return
        cancelMouse()
        removeCallbacks(longPress)
        removeCallbacks(hoverExit)
        dismissTip.run()
        SwingUtilities.invokeLater { ToolTipManager.sharedInstance().hostHideToolTip() }
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
        (hostedRoot != null && root === hostedRoot || panel === NavBarBridge.panel(panelKey) && mountedGeneration == panelGeneration())

    override fun onDraw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.clipRect(0, 0, width, height)
        paint.color = android.graphics.Color.rgb(30, 30, 30)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        frame?.let { canvas.drawBitmap(it, null, Rect(0, 0, width, height), paint) }
        drawDrag(canvas)
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
        interacted()
        val delta = -event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (delta == 0f) return super.onGenericMotionEvent(event)
        val scale = logicalWidth.toFloat() / width.coerceAtLeast(1)
        val x = (event.x * scale).toInt()
        val y = (event.y * scale).toInt()
        val serial = mountSerial
        SwingUtilities.invokeLater {
            if (isCurrentMount(serial)) {
                val target = scrollPaneAt(x, y, if (delta > 0) 1 else -1)
                if (target != null) {
                    val steps = wheel.add(delta.toDouble(), target)
                    if (steps != 0) scroll(x, y, steps)
                }
            }
        }
        return true
    }

    override fun onMouseInput(id: Int, button: Int, modifiers: Int, x: Float, y: Float,
                              time: Long, device: Int, canceled: Boolean) {
        if (!running || width == 0) return
        val serial = mountSerial
        val scale = logicalWidth.toFloat() / width
        val sx = (x * scale).toInt()
        val sy = (y * scale).toInt()
        val tolerance = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        if (id == MouseEvent.MOUSE_PRESSED) {
            interacted()
            stopFling()
            removeCallbacks(hoverExit)
        }
        SwingUtilities.invokeLater {
            if (!isCurrentMount(serial)) return@invokeLater
            val base = root ?: return@invokeLater
            // A mouse starts a drag on the desktop threshold alone, no long press.
            net.runelite.awt.impl.DragDropHost.setPointer(false, false)
            when (id) {
                MouseEvent.MOUSE_PRESSED -> {
                    val target = mousePresses.values.firstOrNull()?.target ?: sink(base, hit(base, sx, sy))
                    if (button != MouseEvent.BUTTON1) clicks.reset()
                    JPopupMenu.takeShownPopupMenu()?.isVisible = false
                    mousePresses[button] = MousePress(x, y, time, target)
                    hover(target, sx, sy)
                    send(target, id, sx, sy, modifiers, 1, button, button == MouseEvent.BUTTON3)
                }
                MouseEvent.MOUSE_DRAGGED -> {
                    for (press in mousePresses.values) {
                        if (kotlin.math.abs(x - press.x) > tolerance || kotlin.math.abs(y - press.y) > tolerance)
                            press.moved = true
                    }
                    mousePresses.values.firstOrNull { it.moved }?.let { send(it.target, id, sx, sy, modifiers) }
                }
                MouseEvent.MOUSE_RELEASED -> {
                    val press = mousePresses.remove(button) ?: return@invokeLater
                    val clicked = !canceled && !press.moved && kotlin.math.abs(x - press.x) <= tolerance &&
                            kotlin.math.abs(y - press.y) <= tolerance && sink(base, hit(base, sx, sy)) === press.target
                    val mouse = MotionEvent.TOOL_TYPE_MOUSE
                    val count = if (clicked && button == MouseEvent.BUTTON1)
                        clicks.click(press.target, press.time, time, x.toDouble(), y.toDouble(), device, mouse,
                            ViewConfiguration.getDoubleTapTimeout().toLong(), tolerance.toDouble()) else 1
                    send(press.target, id, sx, sy, modifiers, count, button)
                    if (clicked) {
                        send(press.target, MouseEvent.MOUSE_CLICKED, sx, sy, modifiers, count, button)
                        when (button) {
                            MouseEvent.BUTTON1 -> {
                                tap(sx, sy, press.time, time, x, y, device, mouse, tolerance)
                                JPopupMenu.takeShownPopupMenu()?.let { showPopupMenu(it, serial) }
                            }
                            MouseEvent.BUTTON3 -> contextMenu(sx, sy, false)
                        }
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!running) return false
        val scale = logicalWidth.toFloat() / width.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPress)
                stopFling()
                velocity?.recycle(); velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                interacted()
                downX = event.x; downY = event.y; lastY = event.y; dragged = false; longPressed = false; touchActive = true; touchMount = mountSerial
                val tool = event.getToolType(0)
                if (tool != MotionEvent.TOOL_TYPE_MOUSE)
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                parent?.requestDisallowInterceptTouchEvent(true)
                // Android ends hover just before a button press; the pointer hasn't left.
                removeCallbacks(hoverExit)
                val x = (event.x * scale).toInt(); val y = (event.y * scale).toInt()
                dragX = x; dragY = y
                val serial = touchMount
                updateOrigin()
                val ox = originX; val oy = originY
                SwingUtilities.invokeLater {
                    try {
                        // A tap anywhere dismisses a tip a long press showed, as a desktop press does.
                        ToolTipManager.sharedInstance().hostHideToolTip()
                        if (isCurrentMount(serial)) {
                            root?.let { net.runelite.awt.impl.DragDropHost.setRootOrigin(it, ox, oy) }
                            net.runelite.awt.impl.DragDropHost.setPointer(tool != MotionEvent.TOOL_TYPE_MOUSE, false)
                            JPopupMenu.takeShownPopupMenu()?.isVisible = false
                            if (tool != MotionEvent.TOOL_TYPE_MOUSE) hover(null)
                            // A mouse drag never scrolls on desktop, so any drag handler takes it.
                            press(x, y, tool, false)
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("SwingPanelHost", "Panel press failed: $panelKey", t)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) return true
                val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
                if (kotlin.math.abs(event.y - downY) > slop || kotlin.math.abs(event.x - downX) > slop) {
                    if (!dragged) SwingUtilities.invokeLater { clicks.reset() }
                    dragged = true
                    removeCallbacks(longPress)
                }
                if (dragged) {
                    dragX = (event.x * scale).toInt(); dragY = (event.y * scale).toInt()
                    if (dragQueued.compareAndSet(false, true)) {
                        val x = (downX * scale).toInt(); val y = (downY * scale).toInt()
                        val serial = touchMount; val held = longPressed; val tool = event.getToolType(0)
                        SwingUtilities.invokeLater {
                            dragQueued.set(false)
                            try { if (isCurrentMount(serial)) drag(dragX, dragY, x, y, tool, held) } catch (t: Throwable) {
                                android.util.Log.e("SwingPanelHost", "Panel drag failed: $panelKey", t)
                            }
                        }
                    }
                }
                velocity?.addMovement(event)
                // Content follows the finger 1:1 in logical px. This used to send one wheel
                // "step" (3 units, ~48px) per 12px of travel: 4x the finger, in visible jumps.
                val delta = (lastY - event.y) * scale
                lastY = event.y
                if (dragged && !longPressed && delta != 0f) {
                    scrollBy((downX * scale).toInt(), (downY * scale).toInt(), delta, touchMount)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val serial = touchMount
                val currentTouch = touchActive && serial == mountSerial
                velocity?.let { tracker ->
                    tracker.addMovement(event)
                    tracker.computeCurrentVelocity(1000)
                    if (currentTouch && dragged && !longPressed)
                        fling((downX * scale).toInt(), (downY * scale).toInt(), -tracker.yVelocity, serial)
                    tracker.recycle()
                }
                velocity = null
                val held = currentTouch && longPressed && !dragged
                val clicked = currentTouch && !dragged && !longPressed
                touchActive = false
                if (currentTouch) {
                    val menuX = (downX * scale).toInt(); val menuY = (downY * scale).toInt()
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
                        try {
                            if (isCurrentMount(serial)) {
                                // A captured press releases first; a hold then still opens the
                                // context menu, as a right-click after the left button is up.
                                val captured = release(x, y, clicked, pressedAt, releasedAt, px, py, device, tool, tolerance)
                                // Wait for release before giving another window touch focus.
                                if (held) contextMenu(menuX, menuY, tool != MotionEvent.TOOL_TYPE_MOUSE)
                                else if (clicked) {
                                    if (!captured) tap(x, y, pressedAt, releasedAt, px, py, device, tool, tolerance)
                                    JPopupMenu.takeShownPopupMenu()?.let { showPopupMenu(it, serial) }
                                }
                            }
                        } catch (t: Throwable) {
                            android.util.Log.e("SwingPanelHost", "Panel input failed: $panelKey", t)
                        }
                    }
                    if (clicked && !held) performClick()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                velocity?.recycle(); velocity = null
                val x = dragX; val y = dragY; val serial = touchMount
                SwingUtilities.invokeLater {
                    clicks.reset()
                    // AWT has no cancel; a release leaves drag handlers in their resting state.
                    // A java.awt.dnd drag is cancelled first, so the release does not drop it.
                    try { if (serial == mountSerial) { cancelDragFrom(root); release(x, y, false, 0L, 0L, 0f, 0f, 0, 0, 0f) } } catch (t: Throwable) {
                        android.util.Log.e("SwingPanelHost", "Panel input failed: $panelKey", t)
                    }
                }
                removeCallbacks(longPress)
                touchActive = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    /** A real pointer (mouse, hovering stylus) gets desktop ENTERED/EXITED/MOVED tracking. */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        // Finger "hover" is accessibility touch exploration; leave it to the framework.
        if (!running || event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) return super.onHoverEvent(event)
        val scale = logicalWidth.toFloat() / width.coerceAtLeast(1)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                removeCallbacks(hoverExit)
                hoverX = (event.x * scale).toInt(); hoverY = (event.y * scale).toInt()
                if (hoverQueued.compareAndSet(false, true)) {
                    val serial = mountSerial
                    SwingUtilities.invokeLater {
                        hoverQueued.set(false)
                        try { if (isCurrentMount(serial)) hoverAt(hoverX, hoverY) } catch (t: Throwable) {
                            android.util.Log.e("SwingPanelHost", "Panel hover failed: $panelKey", t)
                        }
                    }
                }
                // Tips need nothing more: ToolTipManager listens on the ENTERED/MOVED that
                // hoverAt sends and shows them through the host layer, as on desktop.
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                // The EXITED that hoverExit sends hides any tip. Android also ends hover when a button goes down, right before ACTION_DOWN;
                // only a hover that doesn't turn into a press means the pointer left.
                removeCallbacks(hoverExit)
                postDelayed(hoverExit, ViewConfiguration.getTapTimeout().toLong())
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    /** Send [id] to [target] at a root point, in target coordinates like AWT retargeting. */
    private fun send(target: Component, id: Int, x: Int, y: Int, modifiers: Int = 0, count: Int = 0,
                     button: Int = MouseEvent.NOBUTTON, popup: Boolean = false) {
        val base = root
        val p = if (base == null) Point(x, y) else SwingUtilities.convertPoint(base, x, y, target)
        target.dispatchEvent(MouseEvent(target, id, System.currentTimeMillis(), modifiers, p.x, p.y, count, popup, button))
    }

    /**
     * Where AWT delivers a mouse event that hit [from]: the deepest component that
     * listens for mouse input. Text components (BasicTextUI's caret listener) and
     * components with a tooltip (ToolTipManager's listener) count even with no plugin
     * listener, so a tap on step text doesn't bubble up to a collapsible section.
     */
    private fun sink(base: Component, from: Component): Component {
        var target = from
        while (target !== base && !isMouseSink(target)) target = target.parent ?: break
        return target
    }

    private fun isMouseSink(c: Component): Boolean = c.mouseListeners.isNotEmpty() ||
        c.mouseMotionListeners.isNotEmpty() || c is AbstractButton || c is JTabbedPane ||
        c is JTextComponent || (c is JComponent && (c.toolTipText != null || ToolTipManager.sharedInstance().isRegistered(c)))

    /** Whether a motion listener actually implements mouseDragged (not adapter no-ops). */
    private fun handlesDrag(c: Component): Boolean = c.mouseMotionListeners.any { listener ->
        listener !is ToolTipManager && try {
            val owner = listener.javaClass.getMethod("mouseDragged", MouseEvent::class.java).declaringClass
            owner != MouseAdapter::class.java && owner != MouseMotionAdapter::class.java
        } catch (t: Throwable) { true }
    }

    /**
     * Capture the press when a drag handler is under (x, y): PRESSED now, then the
     * gesture's moves become DRAGGED. Full-width rows (XP tracker boxes, party members,
     * profiles) forward drags to a reorder pane; on a touchscreen dragging those must
     * still scroll, so they only capture with [anySize] (mouse, or after a long press).
     */
    private fun press(x: Int, y: Int, tool: Int, anySize: Boolean): Boolean {
        if (gesture != null) return true
        val base = root ?: return false
        val deepest = hit(base, x, y)
        var node: Component? = deepest
        while (node != null) {
            // Disabled branches and compound controls keep their tap-only handling.
            if (!node.isEnabled || node is JComboBox<*> || node is JSpinner) return false
            if (node === base) break
            node = node.parent
        }
        val target = sink(base, deepest)
        if (!handlesDrag(target)) return false
        if (target is JTextComponent) {
            val local = SwingUtilities.convertPoint(base, x, y, target)
            if (!target.dragEnabled || !anySize ||
                !net.runelite.awt.impl.SwingDragSupport.isDragPossible(target, local)) return false
        }
        if (!anySize && target.width > logicalWidth / 2) return false
        gesture = target
        gestureTool = tool
        if (tool == MotionEvent.TOOL_TYPE_MOUSE) hover(target, x, y) else send(target, MouseEvent.MOUSE_ENTERED, x, y)
        send(target, MouseEvent.MOUSE_PRESSED, x, y, InputEvent.BUTTON1_DOWN_MASK, 1, MouseEvent.BUTTON1)
        base.validate()
        return true
    }

    private fun drag(x: Int, y: Int, downX: Int, downY: Int, tool: Int, held: Boolean) {
        // Before the press below: a held finger may start a drag, a swiping one only scrolls.
        net.runelite.awt.impl.DragDropHost.setPointer(tool != MotionEvent.TOOL_TYPE_MOUSE, held)
        if (gesture == null && !(held && press(downX, downY, tool, true))) return
        val target = gesture ?: return
        send(target, MouseEvent.MOUSE_DRAGGED, x, y, InputEvent.BUTTON1_DOWN_MASK)
        // Desktop lays out between events; reorder handlers read sibling bounds next drag.
        root?.validate()
    }

    /** Ends a captured press, if any; returns whether there was one. */
    private fun release(x: Int, y: Int, clicked: Boolean, pressedAt: Long, releasedAt: Long,
                        px: Float, py: Float, device: Int, tool: Int, tolerance: Float): Boolean {
        val target = gesture ?: return false
        gesture = null
        val count = if (clicked) clicks.click(target, pressedAt, releasedAt, px.toDouble(), py.toDouble(), device, tool,
            ViewConfiguration.getDoubleTapTimeout().toLong(), tolerance.toDouble()) else 1
        send(target, MouseEvent.MOUSE_RELEASED, x, y, 0, count, MouseEvent.BUTTON1)
        if (clicked) {
            send(target, MouseEvent.MOUSE_CLICKED, x, y, 0, count, MouseEvent.BUTTON1)
            if (target is AbstractButton && target.isEnabled) target.doClick()
        }
        if (gestureTool != MotionEvent.TOOL_TYPE_MOUSE) send(target, MouseEvent.MOUSE_EXITED, x, y)
        root?.validate()
        return true
    }

    /** Move the entered component, sending EXITED/ENTERED as AWT does when the pointer crosses. */
    private fun hover(target: Component?, x: Int = hoverPoint.x, y: Int = hoverPoint.y) {
        hoverPoint = Point(x, y)
        if (target == null) hoverCursor = null
        val previous = hovered
        if (previous === target) return
        hovered = target
        if (previous != null) send(previous, MouseEvent.MOUSE_EXITED, x, y)
        if (target != null) send(target, MouseEvent.MOUSE_ENTERED, x, y)
    }

    private fun hoverAt(x: Int, y: Int) {
        // While pressed AWT keeps routing to the press target; DRAGGED covers that.
        if (gesture != null) return
        val base = root ?: return
        val target = sink(base, hit(base, x, y))
        hover(target, x, y)
        send(target, MouseEvent.MOUSE_MOVED, x, y)
        // After MOVED: handlers set the cursor there (split-pane dividers, link labels).
        hoverCursor = effectiveCursor(target)
    }

    /** Component.getCursor as the JDK resolves it: an unset cursor is the nearest ancestor's. */
    private fun effectiveCursor(target: Component?): java.awt.Cursor? {
        var c = target
        while (c != null) {
            c.cursor?.let { return it }
            c = c.parent
        }
        return null
    }

    override fun awtCursor(x: Float, y: Float): java.awt.Cursor? = hoverCursor

    /**
     * Show the tooltip ToolTipManager would for the pointer at (x, y): the deepest
     * component's, up to the one receiving the events, at once (a finger cannot hover).
     * ToolTipManager builds and places it (createToolTip, getToolTipLocation) and
     * dismisses it after its dismiss delay or the next tap; [touch] moves the overlay
     * beside the finger, where the hand does not cover it.
     */
    private fun toolTip(x: Int, y: Int, touch: Boolean): Boolean {
        val base = root ?: return false
        val manager = ToolTipManager.sharedInstance()
        if (!manager.isEnabled) return false
        val deepest = hit(base, x, y)
        val receiver = sink(base, deepest)
        var node: Component? = deepest
        while (node != null) {
            if (node is JComponent) {
                val p = SwingUtilities.convertPoint(base, x, y, node)
                val probe = MouseEvent(node, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, p.x, p.y, 0, false)
                if (!node.getToolTipText(probe).isNullOrBlank()) {
                    touchTip = if (touch) Point(x, y) else null
                    try { return manager.hostShowToolTip(node, p.x, p.y) } finally { touchTip = null }
                }
            }
            if (node === receiver || node === base) break
            node = node.parent
        }
        return false
    }

    /**
     * PopupFactory's heavy weight popup, for a sidebar panel (no root pane) the kind
     * every tooltip is: [contents], already laid out at its preferred size, rasterized
     * at device scale into an overlay window at the AWT screen point (x, y), so it
     * escapes the panel bounds like a desktop popup window. The overlay is a snapshot;
     * ToolTipManager re-shows a tip whose text changes. EDT only.
     */
    private fun showOverlay(contents: Component, x: Int, y: Int): Overlay? {
        val base = root ?: return null
        if (width <= 0) return null
        val panelScale = width.toFloat() / logicalWidth.coerceAtLeast(1)
        val pw = (contents.width.coerceAtLeast(1) * panelScale).toInt().coerceAtLeast(1)
        val ph = (contents.height.coerceAtLeast(1) * panelScale).toInt().coerceAtLeast(1)
        val image = BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            (graphics as? net.runelite.awt.impl.BufferedImageGraphics2D)?.setDeviceScale(panelScale)
            java.awt.Window.hostPaint(graphics, contents)
        } finally { graphics.dispose() }
        val bitmap = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(image.backingArray(), 0, pw, 0, 0, pw, ph)
        val origin = base.locationOnScreen
        val local = Point(x - origin.x, y - origin.y)
        val finger = touchTip
        val overlay = Overlay()
        val serial = mountSerial
        post { if (running && serial == mountSerial) overlay.show(bitmap, local, finger, panelScale) }
        return overlay
    }

    /** One heavy weight popup's window: requested on the EDT, shown and dismissed on the UI thread. */
    private inner class Overlay {
        private var window: android.widget.PopupWindow? = null // Android thread only
        @Volatile private var hidden = false

        fun show(bitmap: Bitmap, local: Point, finger: Point?, panelScale: Float) {
            if (hidden) return
            val origin = IntArray(2).also { getLocationOnScreen(it) }
            val metrics = resources.displayMetrics
            val w = bitmap.width
            val h = bitmap.height
            var left: Int
            var top: Int
            if (finger != null) {
                // Beside the finger, on the side facing the middle of the screen. Above or below
                // it the fingertip and hand cover the tip, and in a sidebar the panel's top edge
                // kept pushing it underneath.
                val gap = 36f * metrics.density
                val fingerX = origin[0] + finger.x * panelScale
                val fingerY = origin[1] + finger.y * panelScale
                left = if (fingerX > metrics.widthPixels / 2f) (fingerX - gap - w).toInt() else (fingerX + gap).toInt()
                top = (fingerY - h / 2f).toInt()
            } else {
                left = (origin[0] + local.x * panelScale).toInt()
                top = (origin[1] + local.y * panelScale).toInt()
            }
            // Desktop keeps a tip on its screen; ToolTipManager leaves that to the host.
            left = left.coerceIn(0, (metrics.widthPixels - w).coerceAtLeast(0))
            top = top.coerceIn(0, (metrics.heightPixels - h).coerceAtLeast(0))
            val view = android.widget.ImageView(context).apply { setImageBitmap(bitmap) }
            val popup = android.widget.PopupWindow(view, w, h, false)
            popup.isTouchable = false
            popup.showAtLocation(this@SwingPanelView, android.view.Gravity.NO_GRAVITY, left, top)
            window = popup
            overlays.add(popup)
        }

        fun dismiss() {
            hidden = true
            post {
                window?.let { it.dismiss(); overlays.remove(it) }
                window = null
            }
        }
    }

    /** Host dialogs in the dark Material style. The activity theme is Theme.Black, so a plain
     *  AlertDialog.Builder(context) rendered like Android 1.6 (gray boxes, square buttons). */
    private fun dialogBuilder() = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)

    /** Edits a Swing text field through a native input: keyboard up immediately, text
     *  selected, and the IME's action key submits like pressing Enter in the field. */
    private fun requestTextInput(serial: Long, control: JTextComponent, search: Boolean, placeholder: String?, multiline: Boolean) {
        val text = control.text
        val start = control.selectionStart
        val end = control.selectionEnd
        post {
            if (running && serial == mountSerial) showTextInput(serial, control, text, start, end, search, placeholder, multiline)
        }
    }

    private fun showTextInput(serial: Long, control: JTextComponent, text: String, start: Int, end: Int, search: Boolean, placeholder: String?, multiline: Boolean) {
        val themed = android.view.ContextThemeWrapper(context, android.R.style.Theme_Material_Dialog_Alert)
        val density = resources.displayMetrics.density
        val editor = EditText(themed).apply {
            setText(text)
            setSelection(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
            hint = placeholder ?: if (search) "Search" else null
            inputType = when {
                control is JPasswordField -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiline -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> android.text.InputType.TYPE_CLASS_TEXT
            }
            if (!multiline) {
                isSingleLine = true
                imeOptions = if (search) android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    else android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }
        }
        val container = android.widget.FrameLayout(themed).apply {
            val pad = (20 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, 0)
            addView(editor)
        }
        lateinit var dialog: AlertDialog
        val apply = {
            val value = editor.text.toString()
            val selectionStart = editor.selectionStart.coerceAtLeast(0)
            val selectionEnd = editor.selectionEnd.coerceAtLeast(0)
            SwingUtilities.invokeLater {
                if (isCurrentMount(serial) && control.isEnabled && control.isEditable) {
                    try {
                        control.text = value
                        if (control is JFormattedTextField) control.commitEdit()
                        control.select(selectionStart.coerceAtMost(value.length), selectionEnd.coerceAtMost(value.length))
                        if (control is JTextField) control.postActionEvent()
                        post { dialog.dismiss() }
                    } catch (_: java.text.ParseException) {
                        post { editor.error = "Enter a valid value" }
                    }
                }
            }
        }
        inputPopup?.dismiss()
        dialog = dialogBuilder()
            .setTitle(if (search) "Search" else "Edit value")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (search) "Search" else "OK", null)
            .create()
        if (!multiline) editor.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || enter) {
                apply(); true
            } else false
        }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        inputPopup = dialog
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { apply() }
        editor.requestFocus()
    }

    private fun hosts(component: Component): Boolean = running &&
        generateSequence(component as Component?) { it.parent }.any { it === root }

    /** Called on the Swing event thread, including programmatic showPopup(). */
    private fun showCombo(control: JComboBox<*>, visible: Boolean) {
        val generation = ++comboGeneration
        if (!visible) {
            post { comboPopup?.takeIf { it.first === control }?.second?.dismiss() }
            return
        }
        val serial = mountSerial
        val items = (0 until control.itemCount).map { control.getItemAt(it) }.toTypedArray()
        val labels: Array<CharSequence> = items.mapIndexed { index, item ->
            val text = control.hostDisplayText(item, index) ?: ""
            if (text.regionMatches(0, "<html>", 0, 6, ignoreCase = true))
                android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY)
            else text
        }.toTypedArray()
        val editable = control.isEditable
        val popupList = (control.ui as? javax.swing.plaf.basic.BasicComboBoxUI)?.hostPopupList()
        val selected = control.selectedIndex
        val normalBg = popupList?.background?.rgb
        val normalFg = popupList?.foreground?.rgb
        val selectedBg = popupList?.selectionBackground?.rgb
        val selectedFg = popupList?.selectionForeground?.rgb
        val rowHeight = popupList?.fixedCellHeight ?: -1
        post {
            if (!running || serial != mountSerial || generation != comboGeneration) return@post
            inputPopup?.dismiss()
            val picker = dialogBuilder().setTitle("Select value")
            val adapter = object : android.widget.ArrayAdapter<CharSequence>(picker.context, android.R.layout.select_dialog_item, labels) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                    val row = super.getView(position, convertView, parent) as android.widget.TextView
                    (if (position == selected) selectedBg else normalBg)?.let(row::setBackgroundColor)
                    (if (position == selected) selectedFg else normalFg)?.let(row::setTextColor)
                    if (rowHeight > 0) row.minHeight = (rowHeight * sidebarScale()).toInt()
                    return row
                }
            }
            picker.setAdapter(adapter) { _, index ->
                SwingUtilities.invokeLater {
                    if (isCurrentMount(serial) && control.isEnabled && generation == comboGeneration)
                        control.selectedItem = items[index]
                }
            }
            if (editable) picker.setNeutralButton("Edit value") { _, _ ->
                SwingUtilities.invokeLater {
                    val field = control.editor?.editorComponent as? JTextComponent
                    if (isCurrentMount(serial) && control.isEnabled && field != null)
                        requestTextInput(serial, field, false, null, false)
                }
            }
            val dialog = picker.create()
            dialog.setOnCancelListener {
                SwingUtilities.invokeLater { if (generation == comboGeneration) control.firePopupMenuCanceled() }
            }
            dialog.setOnDismissListener {
                if (comboPopup?.second === dialog) comboPopup = null
                if (inputPopup === dialog) inputPopup = null
                SwingUtilities.invokeLater { if (generation == comboGeneration) control.hidePopup() }
            }
            comboPopup = control to dialog
            inputPopup = dialog
            dialog.show()
        }
    }

    private var velocity: VelocityTracker? = null // Android thread only
    private val flinger = android.widget.OverScroller(context)
    private var flingLastY = 0 // Android thread only
    // Logical px of drag/fling travel not yet applied; drained by one queued EDT task so a
    // slow frame catches up in one step instead of queueing a task per touch event.
    private val scrollLock = Any()
    private var pendingScroll = 0f
    private var scrollQueued = false
    /** Last touch/scroll time; the render loop runs at frame rate for a while after it. */
    @Volatile private var lastInteraction = 0L

    private fun interacted() { lastInteraction = android.os.SystemClock.uptimeMillis() }

    private fun scrollBy(x: Int, y: Int, logicalDy: Float, serial: Long) {
        interacted()
        synchronized(scrollLock) {
            pendingScroll += logicalDy
            if (scrollQueued) return
            scrollQueued = true
        }
        SwingUtilities.invokeLater {
            val dy: Int
            synchronized(scrollLock) {
                scrollQueued = false
                dy = pendingScroll.toInt()
                pendingScroll -= dy
            }
            // A captured drag owns the gesture; only uncaptured ones scroll.
            if (dy == 0 || !isCurrentMount(serial) || gesture != null) return@invokeLater
            val pane = scrollPaneAt(x, y, if (dy > 0) 1 else -1) ?: return@invokeLater
            pane.viewport?.scrollBy(0, dy)
        }
    }

    private fun fling(x: Int, y: Int, velocityY: Float, serial: Long) {
        val min = ViewConfiguration.get(context).scaledMinimumFlingVelocity
        if (kotlin.math.abs(velocityY) < min) return
        flingLastY = 0
        flinger.fling(0, 0, 0, velocityY.toInt(), 0, 0, Int.MIN_VALUE / 2, Int.MAX_VALUE / 2)
        val scale = logicalWidth.toFloat() / width.coerceAtLeast(1)
        postOnAnimation(object : Runnable {
            override fun run() {
                if (!running || serial != mountSerial || !flinger.computeScrollOffset()) return
                val now = flinger.currY
                scrollBy(x, y, (now - flingLastY) * scale, serial)
                flingLastY = now
                postOnAnimation(this)
            }
        })
    }

    private fun stopFling() {
        flinger.forceFinished(true)
        synchronized(scrollLock) { pendingScroll = 0f }
    }

    private fun scroll(x: Int, y: Int, direction: Int) {
        val target = scrollPaneAt(x, y, direction) ?: return
        target.dispatchEvent(MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
            0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, direction))
    }

    /**
     * The scroll pane under (x, y) that should take a scroll in [direction]: the innermost
     * one that can still move that way, else the innermost one. Desktop Swing hands wheel
     * events from a scroll pane without a scrollbar up to its ancestor; panels that nest a
     * fully-expanded JScrollPane inside the PluginPanel's (quest-helper's assist-level
     * picker) were otherwise stuck, the inner pane eating every scroll it can't perform.
     */
    private fun scrollPaneAt(x: Int, y: Int, direction: Int): JScrollPane? {
        val base = root ?: return null
        var hit: Component? = hit(base, x, y)
        var innermost: JScrollPane? = null
        while (hit != null) {
            if (hit is JScrollPane && hit.isWheelScrollingEnabled) {
                if (innermost == null) innermost = hit
                if (canScroll(hit, direction)) return hit
            }
            if (hit === base) break
            hit = hit.parent
        }
        return innermost
    }

    private fun canScroll(pane: JScrollPane, direction: Int): Boolean {
        val viewport = pane.viewport ?: return false
        val view = viewport.view ?: return false
        val y = viewport.viewPosition.y
        return if (direction > 0) y < view.height - viewport.height else y > 0
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
            if (ancestor is JComboBox<*> || ancestor is JSpinner || ancestor is JColorChooser) { target = ancestor; break }
            if (ancestor === base) break
            ancestor = ancestor.parent
        }
        if (target is JTextComponent || target is JComboBox<*> || target is JSpinner) clicks.reset()
        if (target.isEnabled) net.runelite.mp.ui.bridge.SoftKeyboardController.hostEditing { target.requestFocusInWindow() }
        when (val control = target) {
            is JTextComponent -> if (control.isEnabled && control.isEditable) {
                val search = generateSequence(control as Component?) { it.parent }.take(4)
                    .any { it.javaClass.simpleName == "IconTextField" }
                val placeholder = (control.getClientProperty("JTextField.placeholderText") as? String)
                    ?: (control.parent as? javax.swing.JComponent)?.getClientProperty("JTextField.placeholderText") as? String
                val multiline = control is JTextArea
                requestTextInput(serial, control, search, placeholder, multiline)
                return
            }
            is JComboBox<*> -> {
                control.showPopup()
                return
            }
            is JColorChooser -> {
                val color = JColorChooser.showDialog(control, "Choose color", control.color)
                if (color != null && isCurrentMount(serial) && control.isEnabled) control.color = color
                return
            }
            is javax.swing.JSlider -> {
                val min = control.minimum.toLong()
                val upper = (control.maximum.toLong() - control.extent).coerceAtLeast(min)
                val range = upper - min
                val steps = range.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val value = control.value.toLong().coerceIn(min, upper)
                val inverted = control.inverted
                val tick = if (control.snapToTicks) {
                    control.minorTickSpacing.takeIf { it > 0 } ?: control.majorTickSpacing.coerceAtLeast(0)
                } else 0
                fun progressFor(value: Long): Int {
                    val progress = if (range == 0L) 0 else Math.round((value - min).toDouble() / range * steps).toInt().coerceIn(0, steps)
                    return if (inverted) steps - progress else progress
                }
                fun valueFor(progress: Int): Int {
                    val directed = if (inverted) steps - progress else progress
                    var next = min + if (steps == 0) 0L else Math.round(directed.toDouble() / steps * range)
                    if (tick > 0) next = min + Math.round((next - min).toDouble() / tick) * tick
                    return next.coerceIn(min, upper).toInt()
                }
                post {
                    if (!running || serial != mountSerial) return@post
                    inputPopup?.dismiss()
                    val seek = android.widget.SeekBar(context).apply { max = steps; progress = progressFor(value) }
                    seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onStartTrackingTouch(bar: android.widget.SeekBar) { SwingUtilities.invokeLater { if (isCurrentMount(serial)) control.valueIsAdjusting = true } }
                        override fun onProgressChanged(bar: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                            if (fromUser) SwingUtilities.invokeLater { if (isCurrentMount(serial) && control.isEnabled) control.value = valueFor(progress) }
                        }
                        override fun onStopTrackingTouch(bar: android.widget.SeekBar) { SwingUtilities.invokeLater { if (isCurrentMount(serial)) control.valueIsAdjusting = false } }
                    })
                    inputPopup = dialogBuilder().setTitle("Adjust value").setView(seek).setPositiveButton("Done", null).create().also { dialog ->
                        dialog.setOnDismissListener { SwingUtilities.invokeLater { control.valueIsAdjusting = false } }
                        dialog.show()
                    }
                }
                return
            }
            is JSpinner -> {
                val spinEditor = control.editor
                post {
                    if (!running || serial != mountSerial) return@post
                    inputPopup?.dismiss()
                    inputPopup = dialogBuilder().setTitle("Adjust value").setItems(arrayOf("Decrease", "Increase", "Edit value")) { _, index ->
                        SwingUtilities.invokeLater {
                            if (isCurrentMount(serial) && control.isEnabled) {
                                if (index == 2) {
                                    if (spinEditor is JSpinner.DefaultEditor)
                                        requestTextInput(serial, spinEditor.textField, false, null, false)
                                    else JOptionPane.showMessageDialog(control, spinEditor, "Edit value", JOptionPane.PLAIN_MESSAGE)
                                } else {
                                    val next = if (index == 0) control.previousValue else control.nextValue
                                    if (next != null) control.value = next
                                }
                            }
                        }
                    }.show()
                }
                return
            }
        }
        target = sink(base, target)
        val local = SwingUtilities.convertPoint(base, x, y, target)
        val mouse = tool == MotionEvent.TOOL_TYPE_MOUSE
        if (!mouse) {
            val count = clicks.click(target, pressedAt, releasedAt, px.toDouble(), py.toDouble(), device, tool,
                ViewConfiguration.getDoubleTapTimeout().toLong(), tolerance.toDouble())
            send(target, MouseEvent.MOUSE_ENTERED, x, y)
            for (id in intArrayOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
                send(target, id, x, y, if (id == MouseEvent.MOUSE_PRESSED) InputEvent.BUTTON1_DOWN_MASK else 0,
                    count, MouseEvent.BUTTON1)
            }
        }
        if (target is AbstractButton && target.isEnabled) target.doClick()
        if (target is JTabbedPane) {
            val index = target.indexAtLocation(local.x, local.y)
            // indexAtLocation reports disabled tabs too, as on the JDK; only enabled ones select.
            if (index >= 0 && target.isEnabledAt(index)) target.selectedIndex = index
        }
        if (!mouse) send(target, MouseEvent.MOUSE_EXITED, x, y)
    }

    /**
     * Right-click on the Swing thread, then present its real actions on Android. When
     * nothing offers a menu, a [touch] hold shows the tooltip instead, since a finger
     * can't hover to reveal it.
     */
    private fun contextMenu(x: Int, y: Int, touch: Boolean) {
        clicks.reset()
        if (!running) return
        val serial = mountSerial
        val base = root ?: return
        val deepest = hit(base, x, y)
        var node: Component? = deepest
        var owner: JComponent? = null
        while (node != null) {
            // Disabled components take no clicks, but Swing still shows their tooltips.
            if (!node.isEnabled) { if (touch) toolTip(x, y, true); return }
            if (owner == null && node is JComponent && node.componentPopupMenu != null) owner = node
            if (node === base) break
            node = node.parent
        }
        if (touch) {
            val receiver = sink(base, deepest)
            val recipient = if (isMouseSink(receiver)) receiver else owner ?: deepest
            JPopupMenu.takeShownPopupMenu()
            send(recipient, MouseEvent.MOUSE_ENTERED, x, y)
            for (id in intArrayOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
                send(recipient, id, x, y, if (id == MouseEvent.MOUSE_PRESSED) InputEvent.BUTTON3_DOWN_MASK else 0,
                    1, MouseEvent.BUTTON3, id == MouseEvent.MOUSE_PRESSED)
            }
            send(recipient, MouseEvent.MOUSE_EXITED, x, y)
        }
        val shown = JPopupMenu.takeShownPopupMenu()
        val menu = shown ?: owner?.componentPopupMenu
        if (menu == null) { if (touch) toolTip(x, y, true); return }
        if (shown == null) {
            val at = SwingUtilities.convertPoint(base, x, y, owner)
            menu.show(owner, at.x, at.y)
            JPopupMenu.takeShownPopupMenu()
        }
        if (!showPopupMenu(menu, serial) && touch) toolTip(x, y, true)
    }

    /** Present the actual menu opened by a plugin, for either mouse button or touch. */
    private fun showPopupMenu(menu: JPopupMenu, serial: Long): Boolean {
        if (!isCurrentMount(serial)) { menu.isVisible = false; return false }
        val items = menu.components.filterIsInstance<JMenuItem>().filter { it.isVisible }
        if (items.isEmpty()) { menu.isVisible = false; return false }
        swingPopup = menu
        val labels = items.map { it.text ?: "" }
        val enabled = items.map { it.isEnabled }
        val checked = items.map { it.isSelected }
        val submenus = items.map { it is javax.swing.JMenu }
        val requestedSize = if (menu.isPreferredSizeSet) java.awt.Dimension(menu.preferredSize) else null
        post {
            if (!running || serial != mountSerial) { SwingUtilities.invokeLater { menu.isVisible = false }; return@post }
            contextPopup?.dismiss()
            val panelScale = width.toFloat() / logicalWidth
            val titles = labels.mapIndexed { index, label ->
                (if (checked[index]) "✓ $label" else label) + if (submenus[index]) "  ›" else ""
            }
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
            val popup = dialogBuilder().setAdapter(adapter) { _, index ->
                val item = items.getOrNull(index)
                SwingUtilities.invokeLater {
                    if (isCurrentMount(serial) && item != null && item.isEnabled && item.isVisible) {
                        android.util.Log.i("SwingPanelHost", "Context action $panelKey: ${labels[index]}")
                        item.doClick()
                        if (item is javax.swing.JMenu) showPopupMenu(item.popupMenu, serial)
                    }
                }
            }.create()
            fun resizePopup(size: java.awt.Dimension?) {
                val screen = resources.displayMetrics
                val popupWidth = size?.let { (it.width * panelScale).toInt().coerceIn(1, screen.widthPixels.coerceAtLeast(1)) }
                    ?: width.coerceAtLeast(240)
                val popupHeight = size?.let { (it.height * panelScale).toInt().coerceIn(1, screen.heightPixels.coerceAtLeast(1)) }
                    ?: android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                popup.window?.setLayout(popupWidth, popupHeight)
            }
            val sizeListener = java.beans.PropertyChangeListener { event ->
                if (event.propertyName == "preferredSize" || event.propertyName == "popupSize") {
                    val size = if (event.propertyName == "popupSize") (event.newValue as? java.awt.Dimension)?.let { java.awt.Dimension(it) }
                        else if (menu.isPreferredSizeSet) java.awt.Dimension(menu.preferredSize) else null
                    post { if (running && serial == mountSerial && contextPopup === popup) resizePopup(size) }
                }
            }
            popup.setOnDismissListener {
                SwingUtilities.invokeLater {
                    menu.removePropertyChangeListener(sizeListener)
                    if (swingPopup === menu) {
                        javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath()
                        menu.isVisible = false
                        swingPopup = null
                    }
                }
                if (contextPopup === popup) contextPopup = null
            }
            contextPopup = popup
            popup.show()
            popup.listView.setBackgroundColor(android.graphics.Color.rgb(30, 30, 30))
            resizePopup(requestedSize)
            SwingUtilities.invokeLater {
                if (isCurrentMount(serial) && swingPopup === menu) menu.addPropertyChangeListener(sizeListener)
            }
            android.util.Log.i("SwingPanelHost", "Context menu $panelKey: ${labels.joinToString()}")
        }
        return true
    }

    private fun hit(component: Component, x: Int, y: Int): Component {
        // Keep the root fallback for captured gestures that finish outside the panel.
        return SwingUtilities.getDeepestComponentAt(component, x, y) ?: component
    }


    private companion object {
        val hosts = java.util.concurrent.CopyOnWriteArrayList<java.lang.ref.WeakReference<SwingPanelView>>()
        init {
            PopupFactory.setHostLayer(object : PopupFactory.HostLayer {
                override fun show(owner: Component?, contents: Component, x: Int, y: Int): Any? {
                    val o = owner ?: return null
                    val host = hosts.mapNotNull { it.get() }.lastOrNull { it.hosts(o) } ?: return null
                    return host.showOverlay(contents, x, y)
                }
                override fun hide(handle: Any?) { (handle as? Overlay)?.dismiss() }
            })
            JComboBox.setPopupHandler { combo, visible ->
                SwingUtilities.invokeLater {
                    val host = hosts.mapNotNull { it.get() }.lastOrNull { it.hosts(combo) }
                    if (host != null) host.showCombo(combo, visible)
                    else if (visible) combo.hidePopup()
                }
            }
        }
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
