package net.runelite.mp.input

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

internal fun isPhysicalMouse(event: MotionEvent): Boolean {
    if (Build.VERSION.SDK_INT >= 34 && (event.classification == MotionEvent.CLASSIFICATION_PINCH ||
                event.classification == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE)) return false
    return event.isFromSource(InputDevice.SOURCE_MOUSE) ||
            event.pointerCount > 0 && event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
}

/** Native Android button events and ordinary pointer events can describe the same press. */
internal abstract class PhysicalMouseView(context: Context) : View(context) {
    private val buttons = MouseButtonStream(::onMouseInput)

    protected abstract fun onMouseInput(id: Int, button: Int, modifiers: Int, x: Float, y: Float,
                                        time: Long, device: Int, canceled: Boolean)

    private fun route(event: MotionEvent): Boolean {
        // Compose may synthesize CANCEL without the source or button fields from DOWN.
        if (event.actionMasked == MotionEvent.ACTION_CANCEL && buttons.active) {
            buttons.cancel(event.eventTime)
            return true
        }
        if (!isPhysicalMouse(event)) return false
        val action = event.actionMasked
        val changed = if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE)
            event.actionButton else 0
        return buttons.handle(action, event.buttonState, changed, event.x, event.y, event.eventTime, event.deviceId)
    }

    override fun dispatchTouchEvent(event: MotionEvent) = route(event) || super.dispatchTouchEvent(event)
    override fun dispatchGenericMotionEvent(event: MotionEvent) = route(event) || super.dispatchGenericMotionEvent(event)

    protected fun cancelMouse() = buttons.cancel(SystemClock.uptimeMillis())
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelMouse()
    }
    override fun onDetachedFromWindow() {
        cancelMouse()
        super.onDetachedFromWindow()
    }
}

internal class MouseButtonStream(private val emit: (Int, Int, Int, Float, Float, Long, Int, Boolean) -> Unit) {
    private var held = 0
    private var explicit = 0
    private var pending = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var x = 0f
    private var y = 0f
    private var device = -1
    val active get() = pending || held != 0

    fun handle(action: Int, state: Int, changed: Int, px: Float, py: Float, time: Long, deviceId: Int): Boolean {
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_MOVE &&
            action != MotionEvent.ACTION_CANCEL && action != MotionEvent.ACTION_BUTTON_PRESS && action != MotionEvent.ACTION_BUTTON_RELEASE) return false
        if ((action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) && changed and ALL == 0) {
            pending = false
            return false
        }
        if (device != deviceId) cancel(time)
        device = deviceId
        x = px
        y = py
        val reported = state and ALL
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (state == 0 && held == 0) {
                    pending = true
                    downX = x
                    downY = y
                    downTime = time
                } else transition(reported or explicit, time)
            }
            MotionEvent.ACTION_BUTTON_PRESS -> {
                pending = false
                explicit = explicit or (changed and ALL)
                transition(held or reported or explicit, time)
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                pending = false
                explicit = explicit and changed.inv()
                transition((reported or held) and changed.inv(), time)
            }
            MotionEvent.ACTION_MOVE -> {
                if (reported != 0) transition(reported or explicit, time)
                if (held != 0) emit(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, modifiers(), x, y, time, device, false)
            }
            MotionEvent.ACTION_UP -> {
                // Give explicit right/middle events a chance to identify an unlabelled press.
                if (pending) transition(MotionEvent.BUTTON_PRIMARY, downTime, downX, downY)
                explicit = 0
                transition(0, time)
                pending = false
            }
            MotionEvent.ACTION_CANCEL -> cancel(time)
        }
        return true
    }

    fun cancel(time: Long) {
        explicit = 0
        pending = false
        transition(0, time, canceled = true)
    }

    private fun transition(next: Int, time: Long, px: Float = x, py: Float = y, canceled: Boolean = false) {
        if (next == held) return
        pending = false
        for (i in ANDROID.indices) {
            val bit = ANDROID[i]
            if ((next xor held) and bit == 0) continue
            held = held xor bit
            val id = if (held and bit != 0) MouseEvent.MOUSE_PRESSED else MouseEvent.MOUSE_RELEASED
            emit(id, i + 1, modifiers(), px, py, time, device, canceled)
        }
    }

    private fun modifiers(): Int {
        var mask = 0
        for (i in ANDROID.indices) if (held and ANDROID[i] != 0) mask = mask or AWT[i]
        return mask
    }

    private companion object {
        val ANDROID = intArrayOf(MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_TERTIARY, MotionEvent.BUTTON_SECONDARY)
        val AWT = intArrayOf(InputEvent.BUTTON1_DOWN_MASK, InputEvent.BUTTON2_DOWN_MASK, InputEvent.BUTTON3_DOWN_MASK)
        const val ALL = MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY
    }
}
