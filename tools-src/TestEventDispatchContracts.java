// host-contract
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Component.dispatchEvent parity: processXxxEvent overrides only see events a listener or an
 * enableEvents mask asked for, AWTEventListeners see every dispatched event of their mask, and
 * Container fires ContainerEvents on add/remove.
 */
public class TestEventDispatchContracts {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static final class Panel extends JPanel {
        final List<Integer> seen = new ArrayList<>();
        @Override protected void processMouseEvent(MouseEvent e) {
            seen.add(e.getID());
            super.processMouseEvent(e);
        }
        void enableMouse() { enableEvents(AWTEvent.MOUSE_EVENT_MASK); }
        void disableMouse() { disableEvents(AWTEvent.MOUSE_EVENT_MASK); }
    }

    private static MouseEvent mouse(Component source, int id) {
        return new MouseEvent(source, id, 0L, 0, 3, 4, 1, false, MouseEvent.BUTTON1);
    }

    public static void main(String[] args) throws Exception {
        Panel panel = new Panel();
        panel.setBounds(0, 0, 50, 50);

        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED));
        check(panel.seen.isEmpty(), "processMouseEvent ran with mouse events disabled: " + panel.seen);

        panel.enableMouse();
        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED));
        check(panel.seen.size() == 1 && panel.seen.get(0) == MouseEvent.MOUSE_PRESSED,
            "processMouseEvent after enableEvents: " + panel.seen);

        panel.disableMouse();
        panel.seen.clear();
        final int[] clicks = {0};
        MouseAdapter listener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { clicks[0]++; }
        };
        panel.addMouseListener(listener);
        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_CLICKED));
        check(panel.seen.size() == 1 && clicks[0] == 1, "a MouseListener enables processMouseEvent: " + panel.seen);
        panel.removeMouseListener(listener);
        panel.seen.clear();

        // Motion events go to processMouseMotionEvent, never processMouseEvent.
        panel.enableMouse();
        panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_MOVED));
        check(panel.seen.isEmpty(), "MOUSE_MOVED reached processMouseEvent");

        // AWTEventListener: sees mouse events of its mask whether or not the target wants them.
        List<AWTEvent> tapped = new ArrayList<>();
        AWTEventListener tap = tapped::add;
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        toolkit.addAWTEventListener(tap, AWTEvent.MOUSE_EVENT_MASK);
        try {
            Panel quiet = new Panel();
            MouseEvent pressed = mouse(quiet, MouseEvent.MOUSE_PRESSED);
            quiet.dispatchEvent(pressed);
            quiet.dispatchEvent(mouse(quiet, MouseEvent.MOUSE_MOVED));
            check(tapped.size() == 1 && tapped.get(0) == pressed, "AWTEventListener with MOUSE_EVENT_MASK saw " + tapped);
            check(quiet.seen.isEmpty(), "the AWTEventListener must not enable processMouseEvent");

            boolean listed = false;
            for (AWTEventListener registered : toolkit.getAWTEventListeners()) {
                if (registered instanceof AWTEventListenerProxy
                        && ((AWTEventListenerProxy) registered).getListener() == tap
                        && ((AWTEventListenerProxy) registered).getEventMask() == AWTEvent.MOUSE_EVENT_MASK) {
                    listed = true;
                }
            }
            check(listed, "getAWTEventListeners lists the proxy with its mask");
            check(toolkit.getAWTEventListeners(AWTEvent.KEY_EVENT_MASK).length == 0
                    || !containsTap(toolkit.getAWTEventListeners(AWTEvent.KEY_EVENT_MASK), tap),
                "getAWTEventListeners(KEY_EVENT_MASK) must not list a mouse-only listener");
        } finally {
            toolkit.removeAWTEventListener(tap);
        }
        check(!containsTap(toolkit.getAWTEventListeners(), tap), "removeAWTEventListener left the listener");
        tapped.clear();
        new Panel().dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED));
        check(tapped.isEmpty(), "removed AWTEventListener still notified");

        // ContainerListener: COMPONENT_ADDED / COMPONENT_REMOVED, synchronously, with the child.
        List<String> container = new ArrayList<>();
        JPanel parent = new JPanel();
        parent.addContainerListener(new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent e) {
                container.add("added:" + e.getChild().getName() + "@" + (e.getContainer() == parent));
            }
            @Override public void componentRemoved(ContainerEvent e) {
                container.add("removed:" + e.getChild().getName() + "@" + (e.getContainer() == parent));
            }
        });
        JLabel a = new JLabel("a"); a.setName("a");
        JLabel b = new JLabel("b"); b.setName("b");
        parent.add(a);
        parent.add(b, BorderLayout.CENTER);
        parent.remove(a);
        parent.removeAll();
        check(container.toString().equals("[added:a@true, added:b@true, removed:a@true, removed:b@true]"),
            "ContainerListener saw " + container);
        check(parent.getContainerListeners().length == 1, "getContainerListeners");

        // invokeAndWait runs the task as the current InvocationEvent.
        final AWTEvent[] current = {null};
        EventQueue.invokeAndWait(() -> current[0] = EventQueue.getCurrentEvent());
        check(current[0] instanceof InvocationEvent, "getCurrentEvent inside invokeAndWait: " + current[0]);

        System.out.println("TestEventDispatchContracts OK");
    }

    private static boolean containsTap(AWTEventListener[] listeners, AWTEventListener tap) {
        for (AWTEventListener registered : listeners) {
            if (registered instanceof AWTEventListenerProxy && ((AWTEventListenerProxy) registered).getListener() == tap) {
                return true;
            }
        }
        return false;
    }
}
