// host-contract
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Method;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * Component/Container hit-testing, geometry and orientation: the same assertions run on the
 * real JDK and on the shadow. Nothing here is shown, so layouts run through doLayout (the JDK's
 * validate needs a peer); getMousePosition is the one documented divergence: the headless JDK
 * throws, the shadow answers from the last host pointer event.
 */
public final class TestComponentGeometryContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    private static JPanel panel(int x, int y, int w, int h) {
        JPanel p = new JPanel(null);
        p.setBounds(x, y, w, h);
        return p;
    }

    private static void layoutTree(Component c) {
        if (c instanceof Container) {
            Container container = (Container) c;
            container.doLayout();
            for (Component child : container.getComponents()) layoutTree(child);
        }
    }

    private static Method shadowMethod(String name, Class<?>... types) {
        try {
            return Component.class.getMethod(name, types);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        // root: 300x200, insets (10, 20, 10, 20); inner fills the BorderLayout centre.
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(10, 20, 10, 20));
        root.setBounds(0, 0, 300, 200);
        JPanel inner = new JPanel(null);
        root.add(inner, BorderLayout.CENTER);
        JPanel a = panel(10, 10, 50, 50);
        JPanel aa = panel(5, 5, 10, 10);
        a.add(aa);
        JPanel b = panel(40, 40, 50, 50);
        JPanel hidden = panel(100, 10, 50, 50);
        hidden.setVisible(false);
        inner.add(a);
        inner.add(b);
        inner.add(hidden);
        b.setEnabled(false);
        layoutTree(root);

        Insets in = root.getInsets();
        check("getInsets follows the border", in.top == 10 && in.left == 20 && in.bottom == 10 && in.right == 20);
        check("BorderLayout places the centre inside the insets",
            inner.getX() == 20 && inner.getY() == 10 && inner.getWidth() == 260 && inner.getHeight() == 180);
        check("countComponents equals getComponentCount", inner.countComponents() == 3);

        check("contains: inside", root.contains(0, 0) && root.contains(299, 199) && root.contains(new Point(150, 100)));
        check("contains: edges are exclusive", !root.contains(300, 0) && !root.contains(0, 200) && !root.contains(-1, 5));

        check("getComponentAt in the border answers the container", root.getComponentAt(5, 5) == root);
        check("getComponentAt answers the immediate child only", root.getComponentAt(37, 27) == inner);
        check("getComponentAt outside is null", root.getComponentAt(-1, -1) == null && root.getComponentAt(new Point(300, 10)) == null);
        check("getComponentAt does not skip a hidden child", inner.getComponentAt(110, 20) == hidden);
        check("getComponentAt: earlier child is on top", inner.getComponentAt(45, 45) == a);
        check("getComponentAt on a leaf answers itself", aa.getComponentAt(1, 1) == aa && aa.getComponentAt(10, 1) == null);

        check("findComponentAt descends to the deepest child", root.findComponentAt(37, 27) == aa);
        check("findComponentAt: overlap goes to the earlier child", root.findComponentAt(65, 55) == a);
        check("findComponentAt finds a disabled child", root.findComponentAt(new Point(105, 95)) == b);
        check("findComponentAt skips a hidden child", root.findComponentAt(130, 30) == inner);
        check("findComponentAt outside is null", root.findComponentAt(400, 0) == null);
        root.setVisible(false);
        check("findComponentAt on a hidden container is null", root.findComponentAt(37, 27) == null);
        root.setVisible(true);

        check("isAncestorOf: grandchild", root.isAncestorOf(aa) && inner.isAncestorOf(aa));
        check("isAncestorOf: not a sibling or self", !a.isAncestorOf(b) && !root.isAncestorOf(root) && !root.isAncestorOf(null));
        check("getComponentZOrder", inner.getComponentZOrder(b) == 1 && inner.getComponentZOrder(aa) == -1);
        inner.setComponentZOrder(b, 0);
        check("setComponentZOrder brings a child to the top",
            inner.getComponent(0) == b && root.findComponentAt(65, 55) == b);
        inner.setComponentZOrder(b, 1);
        boolean threw = false;
        try {
            inner.setComponentZOrder(b, 3);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("setComponentZOrder rejects the count for a current child", threw);
        threw = false;
        try {
            aa.add(root);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("adding an ancestor to its descendant throws", threw);

        final int[] reshaped = {0};
        JPanel watcher = new JPanel() {
            @Override
            @SuppressWarnings("deprecation")
            public void reshape(int x, int y, int w, int h) {
                reshaped[0]++;
                super.reshape(x, y, w, h);
            }
        };
        watcher.setBounds(1, 2, 3, 4);
        watcher.setLocation(5, 6);
        watcher.setSize(7, 8);
        check("setBounds, setLocation and setSize go through reshape",
            reshaped[0] == 3 && watcher.getX() == 5 && watcher.getY() == 6 && watcher.getWidth() == 7 && watcher.getHeight() == 8);

        MouseListener listener = new MouseAdapter() { };
        aa.addMouseListener(listener);
        MouseListener[] listeners = aa.getListeners(MouseListener.class);
        check("getListeners returns the registered listener", listeners.length == 1 && listeners[0] == listener);
        check("getListeners of an unused type is empty", aa.getListeners(java.awt.event.KeyListener.class).length == 0);
        aa.removeMouseListener(listener);

        threw = false;
        try {
            a.getBaseline(-1, 10);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check("getBaseline rejects a negative size", threw);
        check("a plain panel has no baseline", a.getBaseline(50, 50) < 0
            && a.getBaselineResizeBehavior() == Component.BaselineResizeBehavior.OTHER);

        // Orientation: applyComponentOrientation reaches every descendant and flips FlowLayout.
        JPanel flow = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 5));
        flow.setBounds(0, 0, 200, 40);
        JPanel first = new JPanel();
        first.setPreferredSize(new java.awt.Dimension(30, 20));
        JPanel second = new JPanel();
        second.setPreferredSize(new java.awt.Dimension(30, 20));
        JPanel nested = new JPanel();
        second.add(nested);
        flow.add(first);
        flow.add(second);
        flow.doLayout();
        check("LEADING left-to-right", first.getX() == 5 && second.getX() == 40 && first.getY() == 5);
        flow.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        check("applyComponentOrientation reaches descendants",
            flow.getComponentOrientation() == ComponentOrientation.RIGHT_TO_LEFT
                && nested.getComponentOrientation() == ComponentOrientation.RIGHT_TO_LEFT);
        flow.doLayout();
        check("LEADING right-to-left mirrors the row", first.getX() == 165 && second.getX() == 130 && first.getY() == 5);
        ((FlowLayout) flow.getLayout()).setAlignment(FlowLayout.LEFT);
        flow.doLayout();
        check("LEFT right-to-left still packs to the left", first.getX() == 40 && second.getX() == 5);
        first.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        check("setComponentOrientation is not recursive",
            first.getComponentOrientation().isLeftToRight() && !nested.getComponentOrientation().isLeftToRight());

        Method setHostShowing = shadowMethod("setHostShowing", boolean.class);
        if (setHostShowing == null) {
            threw = false;
            try {
                root.getMousePosition();
            } catch (HeadlessException e) {
                threw = true;
            }
            check("headless JDK: getMousePosition throws HeadlessException", threw);
        } else {
            // Shadow: mount the tree as the Android host does and feed it a host pointer event
            // at screen (37, 27), which is aa's (2, 2).
            setHostShowing.invoke(root, true);
            check("no pointer event yet: nothing is under the pointer", b.getMousePosition() == null);
            aa.dispatchEvent(new MouseEvent(aa, MouseEvent.MOUSE_MOVED, 0L, 0, 2, 2, 37, 27, 0, false, MouseEvent.NOBUTTON));
            Point p = aa.getMousePosition();
            check("getMousePosition in the component under the pointer", p != null && p.x == 2 && p.y == 2);
            p = a.getMousePosition();
            check("getMousePosition of a container counts its children", p != null && p.x == 7 && p.y == 7);
            check("getMousePosition(false) excludes children", a.getMousePosition(false) == null);
            p = root.getMousePosition(true);
            check("getMousePosition(true) at the root", p != null && p.x == 37 && p.y == 27);
            check("getMousePosition of a sibling is null", b.getMousePosition() == null);
            Point screen = java.awt.MouseInfo.getPointerInfo().getLocation();
            check("PointerInfo follows the last host pointer event", screen.x == 37 && screen.y == 27);
            check("getLocationOnScreen sums the origins", aa.getLocationOnScreen().equals(new Point(35, 25)));
            setHostShowing.invoke(root, false);
            check("a hidden tree is not under the pointer", aa.getMousePosition() == null);
        }

        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
