package net.runelite.mp.ui.bridge

import android.content.Context
import android.text.InputType
import android.text.Selection
import android.text.method.TextKeyListener
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.mutableStateOf
import java.lang.ref.WeakReference

object SoftKeyboardController {
    val isKeyboardVisible = mutableStateOf(false)
    private var attachedViewRef: WeakReference<KeyboardInputView>? = null

    var isPasswordProtectedInput = true
        private set

    fun updatePasswordMode(restricted: Boolean) {
        val view = attachedViewRef?.get()
        if (view == null) {
            isPasswordProtectedInput = restricted
            return
        }
        view.post {
            if (isPasswordProtectedInput != restricted) {
                isPasswordProtectedInput = restricted
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                view.resetConnection()
                if (view.isSessionOpen) imm?.restartInput(view)
            }
        }
    }

    fun registerView(view: KeyboardInputView) {
        attachedViewRef = WeakReference(view)
        installFocusBridge()
    }

    // --- AWT focus -> IME ---------------------------------------------------------------

    private var focusBridgeInstalled = false
    /** The text component the bridge opened the IME for; the IconStrip toggle and chat sessions leave it null. */
    @Volatile private var openedFor: WeakReference<java.awt.Component>? = null
    @Volatile private var suppressFocusBridge = false

    /**
     * Opens the IME when AWT focus lands on an enabled, editable text component inside a
     * Window (a Swing dialog or frame), and closes it when focus leaves one the bridge opened
     * for. Sidebar panels are mounted without a Window and keep their overlay editor.
     */
    private fun installFocusBridge() {
        if (focusBridgeInstalled) return
        focusBridgeInstalled = true
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner",
            java.beans.PropertyChangeListener { event ->
                // A focus change fires twice (old -> null, null -> new); deciding on the UI thread
                // from the settled owner keeps a move between two fields from flashing the IME.
                if (suppressFocusBridge || event.newValue == null && openedFor == null) return@PropertyChangeListener
                attachedViewRef?.get()?.let { view -> view.post { reconcileFocus(view) } }
            })
    }

    /**
     * Runs a focus request from SwingPanelHost's tap path: its own overlay editor takes the
     * text, so the request does not open the IME, and a session the bridge opened is closed.
     */
    fun <T> hostEditing(request: () -> T): T {
        if (openedFor != null) {
            openedFor = null
            hide()
        }
        suppressFocusBridge = true
        try {
            return request()
        } finally {
            suppressFocusBridge = false
        }
    }

    private fun reconcileFocus(view: KeyboardInputView) {
        val owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val text = owner as? javax.swing.text.JTextComponent
        if (text != null && text.isEnabled && text.isEditable && javax.swing.SwingUtilities.getWindowAncestor(text) != null) {
            if (openedFor?.get() === text && view.isSessionOpen) return
            openedFor = WeakReference(text)
            updatePasswordMode(text is javax.swing.JPasswordField)
            view.openSession()
        } else if (openedFor != null) {
            openedFor = null
            view.endSession()
        }
    }

    fun unregisterView(view: KeyboardInputView) {
        if (attachedViewRef?.get() === view) {
            attachedViewRef = null
        }
    }

    fun show() {
        attachedViewRef?.get()?.let { view -> view.post { view.openSession() } }
    }

    fun hide() {
        attachedViewRef?.get()?.let { view -> view.post { view.endSession() } }
    }

    fun toggle() {
        if (isKeyboardVisible.value) {
            hide()
        } else {
            show()
        }
    }
}

class KeyboardInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var isSessionOpen = false
        private set
    private var connectionGeneration = 0
    private var imeWasVisible = false
    private var inputConnection: InputConnection? = null

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun resetConnection() {
        connectionGeneration++
        inputConnection?.closeConnection()
        inputConnection = null
    }

    fun openSession() {
        if (isSessionOpen || !isAttachedToWindow || !hasWindowFocus()) return
        resetConnection()
        isSessionOpen = true
        isFocusableInTouchMode = true
        isFocusable = true
        if (!requestFocus()) { endSession(); return }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
        imm?.showSoftInput(this, 0)
    }

    fun endSession() {
        if (!isSessionOpen) return
        isSessionOpen = false
        resetConnection()
        imeWasVisible = false
        SoftKeyboardController.isKeyboardVisible.value = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
        isFocusable = false
        isFocusableInTouchMode = false
        post { net.runelite.mp.MainActivity.hideSystemUI() }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val visible = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
            .isVisible(WindowInsetsCompat.Type.ime())
        if (isSessionOpen) {
            SoftKeyboardController.isKeyboardVisible.value = visible
            if (imeWasVisible && !visible) endSession()
            else imeWasVisible = visible
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) endSession()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SoftKeyboardController.registerView(this)
    }

    override fun onDetachedFromWindow() {
        endSession()
        super.onDetachedFromWindow()
        SoftKeyboardController.unregisterView(this)
    }

    override fun onCheckIsTextEditor(): Boolean = isSessionOpen

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!isSessionOpen) return null
        resetConnection()
        val generation = connectionGeneration
        outAttrs.inputType = if (SoftKeyboardController.isPasswordProtectedInput) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {
            private var closed = false
            private fun active() = !closed && isSessionOpen && generation == connectionGeneration

            override fun closeConnection() {
                closed = true
                super.closeConnection()
                editable?.apply { clear(); clearSpans() }
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                return edit { super.setComposingText(text ?: "", newCursorPosition) }
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!active()) return false
                val newText = text?.toString() ?: ""
                val accepted = edit { super.commitText(newText, newCursorPosition) }
                if (newText.contains('\n') || newText.contains('\r')) {
                    endSession()
                }
                return accepted
            }

            override fun finishComposingText(): Boolean {
                if (!active()) return false
                return super.finishComposingText()
            }

            override fun setComposingRegion(start: Int, end: Int): Boolean {
                if (!active()) return false
                return super.setComposingRegion(start, end)
            }

            override fun setSelection(start: Int, end: Int): Boolean {
                if (!active()) return false
                val accepted = super.setSelection(start, end)
                if (accepted) updateSelection()
                return accepted
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (!active()) return false
                val knownBefore = selectionStart()
                return edit((beforeLength - knownBefore).coerceAtLeast(0)) {
                    super.deleteSurroundingText(beforeLength, afterLength)
                }
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (!active()) return false
                val knownBefore = editable?.let { Character.codePointCount(it, 0, selectionStart()) } ?: 0
                return edit((beforeLength - knownBefore).coerceAtLeast(0)) {
                    super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                }
            }

            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
                if (!active() || n <= 0) return ""
                return super.getTextBeforeCursor(n, flags) ?: ""
            }

            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
                if (!active() || n <= 0) return ""
                return super.getTextAfterCursor(n, flags) ?: ""
            }

            override fun getSelectedText(flags: Int): CharSequence? {
                if (!active()) return null
                return super.getSelectedText(flags)
            }

            override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
                if (!active()) return false
                // Numeric IMEs can send key events instead of commitText. Keep Android's
                // editor state in sync without emitting those characters a second time.
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    super.finishComposingText()
                    editable?.let { TextKeyListener.getInstance().onKeyDown(this@KeyboardInputView, it, event.keyCode, event) }
                } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                    editable?.let { TextKeyListener.getInstance().onKeyUp(this@KeyboardInputView, it, event.keyCode, event) }
                }
                val res = KeyDispatch.sendAndroidKeyEvent(event)
                updateSelection()
                if (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP) {
                    endSession()
                }
                return res
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                if (!active()) return false
                super.finishComposingText()
                when (actionCode) {
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_UNSPECIFIED -> {
                        KeyDispatch.sendEnter()
                        endSession()
                        return true
                    }
                }
                return super.performEditorAction(actionCode)
            }

            private fun selectionStart(): Int = editable?.let {
                minOf(Selection.getSelectionStart(it), Selection.getSelectionEnd(it)).coerceAtLeast(0)
            } ?: 0

            /** Android owns committed text, cursor and composing spans in one Editable.
             * Forward only the resulting difference through the existing key-event bridge. */
            private fun edit(unknownBackspaces: Int = 0, operation: () -> Boolean): Boolean {
                if (!active()) return false
                val before = editable?.toString() ?: ""
                val accepted = operation()
                if (!accepted) return false
                val after = editable?.toString() ?: ""
                var commonPrefixLen = 0
                val minLen = minOf(before.length, after.length)
                while (unknownBackspaces == 0 && commonPrefixLen < minLen && before[commonPrefixLen] == after[commonPrefixLen]) {
                    commonPrefixLen++
                }
                // The game may contain text from before this keyboard session. Preserve
                // the existing ability to backspace into that untracked prefix.
                repeat(before.length - commonPrefixLen + unknownBackspaces) { KeyDispatch.sendBackspace() }
                KeyDispatch.sendString(after.substring(commonPrefixLen))
                updateSelection()
                return true
            }

            private fun updateSelection() {
                if (!active() || !isAttachedToWindow) return
                val buffer = editable ?: return
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.updateSelection(this@KeyboardInputView,
                    Selection.getSelectionStart(buffer), Selection.getSelectionEnd(buffer),
                    BaseInputConnection.getComposingSpanStart(buffer), BaseInputConnection.getComposingSpanEnd(buffer))
            }
        }.also { inputConnection = it }
    }
}
