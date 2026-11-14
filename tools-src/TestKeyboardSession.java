import android.content.Context;
import android.view.inputmethod.*;
import android.text.InputType;
import java.lang.reflect.*;

/** Emulator instrumentation regression checks for the game's IME session boundaries. */
public final class TestKeyboardSession {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void run(Context context) throws Exception {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper(), "IME calls run on Android's UI thread");
        java.awt.KeyboardFocusManager manager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
        java.awt.Component previous = manager.getFocusOwner();
        java.awt.EventQueue.invokeAndWait(manager::clearGlobalFocusOwner);
        try { runGameSession(context); }
        finally {
            java.awt.EventQueue.invokeAndWait(() -> { if (previous != null) previous.requestFocusInWindow(); });
        }
    }
    private static void drain() throws Exception { java.awt.EventQueue.invokeAndWait(() -> {}); }
    private static void runGameSession(Context context) throws Exception {
        Class<?> type = Class.forName("net.runelite.mp.ui.bridge.KeyboardInputView");
        Object view = type.getConstructor(Context.class).newInstance(context);
        Method create = type.getMethod("onCreateInputConnection", EditorInfo.class);
        Field open = type.getDeclaredField("isSessionOpen"); open.setAccessible(true);
        check(create.invoke(view, new EditorInfo()) == null, "Closed bridge must not advertise an input connection");
        open.setBoolean(view, true);
        EditorInfo protectedInfo = new EditorInfo();
        InputConnection old = (InputConnection) create.invoke(view, protectedInfo);
        check((protectedInfo.inputType & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, "Login remains protected");
        check(old.setComposingText("hello", 1), "Compose");
        check(old.setComposingText("help", 1), "Replace composition");
        check("help".contentEquals(old.getTextBeforeCursor(20, 0)), "Current composition");
        check(old.performEditorAction(EditorInfo.IME_ACTION_DONE), "Submit closes session");
        check(!open.getBoolean(view), "Session closed on submit");
        check(!old.commitText("help", 1), "Late commit rejected");
        check(!old.setComposingText("old message", 1), "Late composition rejected");
        check(old.getTextBeforeCursor(20, 0).length() == 0, "Closed session exposes no stale text");
        open.setBoolean(view, true);
        InputConnection fresh = (InputConnection) create.invoke(view, new EditorInfo());
        check(fresh.getTextBeforeCursor(20, 0).length() == 0, "Reopen starts clean");
        check(!old.commitText("replayed message", 1), "Reopen cannot reactivate old connection");
        check(fresh.setComposingText("draft", 1), "Draft composition");
        type.getMethod("endSession").invoke(view);
        check(!fresh.deleteSurroundingText(5, 0), "Closed connection cannot erase draft");
        open.setBoolean(view, true);
        InputConnection replacement = (InputConnection) create.invoke(view, new EditorInfo());
        InputConnection newest = (InputConnection) create.invoke(view, new EditorInfo());
        check(!replacement.commitText("stale", 1), "Connection replacement invalidates predecessor");
        newest.closeConnection();
        check(!newest.commitText("stale", 1), "Explicit close rejects input");
        type.getMethod("endSession").invoke(view);
        drain();
        // Observe the actual AWT text events, not only the IME's composing buffer.
        Class<?> managerType = Class.forName("java.awt.KeyboardFocusManager");
        Class<?> dispatcherType = Class.forName("java.awt.KeyEventDispatcher");
        Object manager = managerType.getMethod("getCurrentKeyboardFocusManager").invoke(null);
        StringBuilder typed = new StringBuilder();
        Object dispatcher = Proxy.newProxyInstance(dispatcherType.getClassLoader(), new Class<?>[]{dispatcherType}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("toString")) return "Keyboard test recorder";
            Object event = args[0];
            int id = (Integer) event.getClass().getMethod("getID").invoke(event);
            if (id == 400) {
                char c = (Character) event.getClass().getMethod("getKeyChar").invoke(event);
                if (c == '\b') { if (typed.length() > 0) typed.setLength(typed.length() - 1); }
                else typed.append(c);
            }
            return false;
        });
        managerType.getMethod("addKeyEventDispatcher", dispatcherType).invoke(manager, dispatcher);
        try {
            open.setBoolean(view, true);
            InputConnection input = (InputConnection) create.invoke(view, new EditorInfo());
            input.beginBatchEdit();
            input.setComposingText("helo", 1);
            input.setComposingText("hello", 1);
            input.commitText("hello ", 1);
            input.endBatchEdit();
            drain();
            check("hello ".contentEquals(typed), "Autocorrect replaces composition without duplication");
            input.commitText("voice text", 1);
            drain();
            check("hello voice text".contentEquals(typed), "Whole text commit reaches AWT once");
            input.deleteSurroundingText(4, 0);
            drain();
            check("hello voice ".contentEquals(typed), "IME deletion reaches AWT");
            input.performEditorAction(EditorInfo.IME_ACTION_DONE);
            drain();
            check("hello voice \n".contentEquals(typed), "Done emits one Enter");
            typed.setLength(0);
            open.setBoolean(view, true);
            InputConnection reopened = (InputConnection) create.invoke(view, new EditorInfo());
            input.commitText("old text", 1);
            reopened.setComposingText("n", 1);
            reopened.commitText("new", 1);
            drain();
            check("new".contentEquals(typed), "New session cannot replay previous message");
            type.getMethod("endSession").invoke(view);
            drain();
        } finally {
            managerType.getMethod("removeKeyEventDispatcher", dispatcherType).invoke(manager, dispatcher);
        }
    }
}
