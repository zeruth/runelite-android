// host-contract
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.Arrays;
import javax.swing.JFrame;

/**
 * Window, Frame and Dialog contracts. Every check is JDK-true: the runner passes
 * -Djava.awt.headless=true, under which the JDK refuses every top-level window, so main turns
 * headless off before the first AWT class reads it (the JDK run opens short-lived native windows).
 * Window events are posted asynchronously by desktop peers, so their order is left to the device.
 */
public class TestWindowContracts {
    private static int checks;
    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(20_000); } catch (InterruptedException e) { return; }
            System.err.println("TestWindowContracts timed out (a modal show did not return)");
            System.exit(2);
        });
        watchdog.setDaemon(true);
        watchdog.start();
        frames();
        extendedState();
        windowState();
        modalOffDispatchThread();
        modalOnDispatchThread();
        System.out.println("PASS " + checks + " window contracts");
        System.exit(0);
    }

    private static void frames() {
        Frame frame = new Frame("contract");
        JFrame swing = new JFrame("swing");
        check(Arrays.asList(Frame.getFrames()).containsAll(Arrays.asList(frame, swing)), "getFrames lists created frames");
        check(Arrays.asList(Window.getWindows()).contains(frame), "getWindows lists a created frame");
        check(Arrays.asList(Window.getOwnerlessWindows()).contains(frame), "frame is ownerless");
        Dialog owned = new Dialog(frame, "owned");
        check(owned.getOwner() == frame && Arrays.asList(frame.getOwnedWindows()).contains(owned), "owned dialog");
        check(!Arrays.asList(Window.getOwnerlessWindows()).contains(owned), "owned dialog is not ownerless");
        check(!frame.isDisplayable(), "not displayable before pack");

        frame.setUndecorated(true);
        frame.pack();
        check(frame.isDisplayable(), "displayable after pack");
        boolean refused = false;
        try { frame.setUndecorated(false); } catch (IllegalComponentStateException expected) { refused = true; }
        check(refused && frame.isUndecorated(), "decorations fixed while displayable");
        owned.pack();
        frame.dispose();
        swing.dispose();
        check(!frame.isDisplayable() && !frame.isShowing(), "dispose releases the frame");
        check(!owned.isDisplayable(), "dispose disposes owned windows");
        // The JDK drops a window from getFrames only once it is garbage collected, not on dispose:
        // a disposed frame stays listed, released (not displayable) until it is shown again.
        check(Arrays.asList(Frame.getFrames()).contains(frame), "disposed frame stays listed until collected");
        frame.setUndecorated(false);
        check(!frame.isUndecorated(), "decorations change again once disposed");
    }

    private static void extendedState() {
        Frame frame = new Frame();
        check(frame.getExtendedState() == Frame.NORMAL && frame.getState() == Frame.NORMAL, "initial state");
        frame.setExtendedState(Frame.MAXIMIZED_BOTH);
        check(frame.getExtendedState() == Frame.MAXIMIZED_BOTH, "maximized round trip");
        frame.setState(Frame.ICONIFIED);
        check(frame.getExtendedState() == (Frame.MAXIMIZED_BOTH | Frame.ICONIFIED) && frame.getState() == Frame.ICONIFIED,
            "iconify keeps maximized bits");
        frame.setState(Frame.NORMAL);
        check(frame.getExtendedState() == Frame.MAXIMIZED_BOTH, "restore keeps maximized bits");
        frame.setExtendedState(Frame.NORMAL);
        check(frame.getExtendedState() == Frame.NORMAL, "normal round trip");

        Rectangle bounds = new Rectangle(1, 2, 300, 400);
        frame.setMaximizedBounds(bounds);
        check(bounds.equals(frame.getMaximizedBounds()), "maximized bounds");
        frame.dispose();
    }

    private static void windowState() {
        Frame frame = new Frame();
        check(frame.getOpacity() == 1.0f && frame.getShape() == null && frame.isOpaque(), "opaque defaults");
        boolean refused = false;
        try { frame.setOpacity(0.5f); } catch (IllegalComponentStateException expected) { refused = true; }
        check(refused, "a decorated frame cannot be translucent");
        frame.setUndecorated(true);
        refused = false;
        try { frame.setOpacity(1.5f); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "opacity range");
        frame.setLocationByPlatform(true);
        check(frame.isLocationByPlatform(), "location by platform");
        frame.setLocation(10, 10);
        check(!frame.isLocationByPlatform(), "setLocation clears location by platform");
        frame.setModalExclusionType(null);
        check(frame.getModalExclusionType() == Dialog.ModalExclusionType.NO_EXCLUDE, "null exclusion type");
        frame.setFocusableWindowState(false);
        check(!frame.getFocusableWindowState(), "focusable window state");
        check(frame.getToolkit() != null, "toolkit");
        frame.dispose();

        Dialog dialog = new Dialog((Frame) null);
        check(!dialog.isModal() && dialog.getModalityType() == Dialog.ModalityType.MODELESS, "modeless default");
        dialog.setModal(true);
        check(dialog.isModal() && dialog.getModalityType() == Dialog.DEFAULT_MODALITY_TYPE, "setModal");
        dialog.setModalityType(null);
        check(!dialog.isModal(), "null modality is modeless");
        refused = false;
        Window plain = new Window((Frame) null);
        try { new Dialog(plain, "bad", Dialog.ModalityType.MODELESS); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "dialog owner must be a frame or dialog");
        plain.dispose();
        dialog.dispose();
    }

    private static void modalOffDispatchThread() throws Exception {
        Dialog dialog = new Dialog((Frame) null, "modal", true);
        Thread closer = new Thread(() -> {
            try {
                while (!dialog.isVisible()) Thread.sleep(5);
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
            EventQueue.invokeLater(() -> dialog.setVisible(false));
        });
        closer.start();
        dialog.setVisible(true);
        check(!dialog.isVisible(), "modal show off the dispatch thread returns once hidden");
        closer.join(5000);
        dialog.dispose();
    }

    private static void modalOnDispatchThread() throws Exception {
        Dialog dialog = new Dialog((Frame) null, "modal", Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] pumped = new boolean[1];
        EventQueue.invokeAndWait(() -> {
            // Queued behind the show: runs only if the modal loop keeps pumping the queue.
            EventQueue.invokeLater(() -> { pumped[0] = true; dialog.dispose(); });
            dialog.setVisible(true);
            check(pumped[0] && !dialog.isVisible() && !dialog.isDisplayable(), "modal dialog returns when closed");
        });
    }
}
