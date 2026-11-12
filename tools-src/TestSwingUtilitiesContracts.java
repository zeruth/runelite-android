// host-contract
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * SwingUtilities against the desktop JDK and the shadow. The layoutCompoundLabel expectations are
 * the JDK's layoutCompoundLabelImpl worked through for a fixed-advance font (7px per char, 14px
 * high), a 16x16 icon and a 4px gap; text never starts with 'W', the one char the JDK measures a
 * side bearing for.
 */
public final class TestSwingUtilitiesContracts implements SwingConstants {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    private static void checkEquals(String name, Object expected, Object actual) {
        boolean same = expected == null ? actual == null : expected.equals(actual);
        check(name + (same ? "" : " expected " + expected + " got " + actual), same);
    }

    private static final class FixedMetrics extends FontMetrics {
        FixedMetrics() { super(new Font(Font.DIALOG, Font.PLAIN, 12)); }
        @Override public int charWidth(char ch) { return 7; }
        @Override public int charWidth(int codePoint) { return 7; }
        @Override public int stringWidth(String str) { return 7 * str.length(); }
        @Override public int getHeight() { return 14; }
    }

    private static final Icon ICON = new Icon() {
        public void paintIcon(Component c, Graphics g, int x, int y) { }
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
    };

    private static String rect(Rectangle r) {
        return r.x + "," + r.y + "," + r.width + "x" + r.height;
    }

    private static void label(String name, boolean icon, int vAlign, int hAlign, int vPos, int hPos, int viewWidth,
                              String text, String textR, String iconR) {
        FontMetrics fm = new FixedMetrics();
        Rectangle view = new Rectangle(10, 20, viewWidth, 40);
        Rectangle iconBounds = new Rectangle();
        Rectangle textBounds = new Rectangle();
        String clipped = SwingUtilities.layoutCompoundLabel(fm, "Hello world", icon ? ICON : null,
            vAlign, hAlign, vPos, hPos, view, iconBounds, textBounds, 4);
        checkEquals("layoutCompoundLabel " + name + " text", text, clipped);
        checkEquals("layoutCompoundLabel " + name + " textR", textR, rect(textBounds));
        checkEquals("layoutCompoundLabel " + name + " iconR", iconR, rect(iconBounds));
    }

    private static JPanel panel(int x, int y, int w, int h) {
        JPanel p = new JPanel(null);
        p.setBounds(x, y, w, h);
        return p;
    }

    private static final class Marker extends JPanel {
        Marker() { super(null); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        label("center/center text right", true, CENTER, CENTER, CENTER, RIGHT, 100, "Hello world", "32,33,77x14", "12,32,16x16");
        label("top/left text right", true, TOP, LEFT, CENTER, RIGHT, 100, "Hello world", "30,21,77x14", "10,20,16x16");
        label("bottom/right text left", true, BOTTOM, RIGHT, CENTER, LEFT, 100, "Hello world", "13,45,77x14", "94,44,16x16");
        label("center/center text below", true, CENTER, CENTER, BOTTOM, CENTER, 100, "Hello world", "22,43,77x14", "52,23,16x16");
        label("top/left text above", true, TOP, LEFT, TOP, CENTER, 100, "Hello world", "10,20,77x14", "40,38,16x16");
        label("center/right text top-right", true, CENTER, RIGHT, TOP, RIGHT, 100, "Hello world", "33,32,77x14", "13,32,16x16");
        label("bottom/left text bottom-left", true, BOTTOM, LEFT, BOTTOM, LEFT, 100, "Hello world", "10,46,77x14", "91,44,16x16");
        label("clipped center/left text right", true, CENTER, LEFT, CENTER, RIGHT, 50, "H...", "30,33,28x14", "10,32,16x16");
        label("clipped top/right text left", true, TOP, RIGHT, CENTER, LEFT, 50, "H...", "12,21,28x14", "44,20,16x16");
        label("clipped text below", true, CENTER, CENTER, BOTTOM, CENTER, 50, "Hell...", "11,43,49x14", "27,23,16x16");
        label("clipped to ellipsis only", true, CENTER, CENTER, CENTER, RIGHT, 30, "...", "25,33,21x14", "5,32,16x16");
        label("clipped without icon", false, CENTER, LEFT, CENTER, RIGHT, 40, "He...", "10,33,35x14", "10,40,0x0");
        checkEquals("computeStringWidth", 21, SwingUtilities.computeStringWidth(new FixedMetrics(), "abc"));

        // root(100,100) > a(10,20) > b(5,7); root > c(30,40)
        JPanel root = panel(100, 100, 300, 300);
        Marker a = new Marker();
        a.setBounds(10, 20, 200, 200);
        a.setName("alpha");
        JPanel b = panel(5, 7, 100, 100);
        JPanel c = panel(30, 40, 100, 100);
        root.add(a);
        root.add(c);
        a.add(b);

        Point converted = SwingUtilities.convertPoint(b, new Point(1, 2), c);
        checkEquals("convertPoint across nested panels", new Point(-14, -11), converted);
        checkEquals("convertPoint int overload", new Point(6, 9), SwingUtilities.convertPoint(c, -14, -11, a));
        checkEquals("convertPoint to ancestor", new Point(16, 29), SwingUtilities.convertPoint(b, new Point(1, 2), root));
        checkEquals("convertPoint both null returns argument", new Point(3, 4), SwingUtilities.convertPoint(null, new Point(3, 4), null));
        checkEquals("convertRectangle keeps size", new Rectangle(-14, -11, 3, 4),
            SwingUtilities.convertRectangle(b, new Rectangle(1, 2, 3, 4), c));

        MouseEvent press = new MouseEvent(b, MouseEvent.MOUSE_PRESSED, 0L, InputEvent.BUTTON3_DOWN_MASK, 1, 2, 1, false, MouseEvent.BUTTON3);
        MouseEvent moved = SwingUtilities.convertMouseEvent(b, press, c);
        check("convertMouseEvent retargets source", moved.getSource() == c);
        checkEquals("convertMouseEvent point", new Point(-14, -11), moved.getPoint());
        check("convertMouseEvent keeps id, button and click count",
            moved.getID() == MouseEvent.MOUSE_PRESSED && moved.getButton() == MouseEvent.BUTTON3 && moved.getClickCount() == 1);

        check("isRightMouseButton on button 3 press", SwingUtilities.isRightMouseButton(press));
        check("isLeftMouseButton false on button 3 press", !SwingUtilities.isLeftMouseButton(press));
        MouseEvent drag = new MouseEvent(b, MouseEvent.MOUSE_DRAGGED, 0L, InputEvent.BUTTON1_DOWN_MASK, 1, 2, 0, false, MouseEvent.NOBUTTON);
        check("isLeftMouseButton from the down mask while dragging", SwingUtilities.isLeftMouseButton(drag));
        MouseEvent release = new MouseEvent(b, MouseEvent.MOUSE_RELEASED, 0L, 0, 1, 2, 1, false, MouseEvent.BUTTON2);
        check("isMiddleMouseButton from the released button", SwingUtilities.isMiddleMouseButton(release)
            && !SwingUtilities.isLeftMouseButton(release) && !SwingUtilities.isRightMouseButton(release));

        check("getAncestorOfClass", SwingUtilities.getAncestorOfClass(Marker.class, b) == a);
        check("getAncestorOfClass misses", SwingUtilities.getAncestorOfClass(Marker.class, c) == null);
        check("getAncestorNamed", SwingUtilities.getAncestorNamed("alpha", b) == a);
        check("isDescendingFrom", SwingUtilities.isDescendingFrom(b, root) && SwingUtilities.isDescendingFrom(b, b)
            && !SwingUtilities.isDescendingFrom(c, a));
        check("getDeepestComponentAt finds the nested panel", SwingUtilities.getDeepestComponentAt(root, 16, 28) == b);
        check("getDeepestComponentAt outside is null", SwingUtilities.getDeepestComponentAt(root, 400, 400) == null);
        check("getWindowAncestor without a window", SwingUtilities.getWindowAncestor(b) == null);
        checkEquals("getLocalBounds", new Rectangle(0, 0, 100, 100), SwingUtilities.getLocalBounds(b));

        check("isRectangleContainingRectangle", SwingUtilities.isRectangleContainingRectangle(
            new Rectangle(0, 0, 10, 10), new Rectangle(2, 2, 8, 8))
            && !SwingUtilities.isRectangleContainingRectangle(new Rectangle(0, 0, 10, 10), new Rectangle(2, 2, 9, 8)));
        checkEquals("computeIntersection", new Rectangle(5, 5, 5, 5),
            SwingUtilities.computeIntersection(0, 0, 10, 10, new Rectangle(5, 5, 10, 10)));
        checkEquals("computeIntersection disjoint is empty", new Rectangle(0, 0, 0, 0),
            SwingUtilities.computeIntersection(0, 0, 10, 10, new Rectangle(20, 20, 5, 5)));
        checkEquals("computeUnion", new Rectangle(0, 0, 15, 15),
            SwingUtilities.computeUnion(0, 0, 10, 10, new Rectangle(5, 5, 10, 10)));
        Rectangle hole = new Rectangle(2, 2, 4, 4);
        Rectangle[] difference = SwingUtilities.computeDifference(new Rectangle(0, 0, 10, 10), hole);
        int area = 0;
        boolean disjoint = true;
        for (Rectangle r : difference) {
            area += r.width * r.height;
            disjoint &= !r.intersects(hole);
        }
        check("computeDifference covers A minus B in 4 pieces", difference.length == 4 && area == 84 && disjoint);
        check("computeDifference with no overlap is empty",
            SwingUtilities.computeDifference(new Rectangle(0, 0, 5, 5), new Rectangle(10, 10, 5, 5)).length == 0);

        check("isEventDispatchThread false on main", !SwingUtilities.isEventDispatchThread());
        final AtomicBoolean onEdt = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> onEdt.set(SwingUtilities.isEventDispatchThread()));
        check("isEventDispatchThread true inside invokeAndWait", onEdt.get());
        Throwable cause = null;
        try {
            SwingUtilities.invokeAndWait(() -> { throw new IllegalStateException("boom"); });
        } catch (InvocationTargetException e) {
            cause = e.getCause();
        }
        check("invokeAndWait wraps the task's exception", cause instanceof IllegalStateException);
        final AtomicBoolean later = new AtomicBoolean();
        SwingUtilities.invokeLater(() -> later.set(SwingUtilities.isEventDispatchThread()));
        SwingUtilities.invokeAndWait(() -> { });
        check("invokeLater runs on the EDT before a later invokeAndWait returns", later.get());

        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) System.exit(1);
        System.exit(0);
    }
}
