// host-contract: patched-args=shadow jvm-args=--add-exports=java.desktop/sun.awt=ALL-UNNAMED,--add-opens=java.desktop/java.awt=ALL-UNNAMED
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.LayoutStyle;
import javax.swing.OverlayLayout;
import javax.swing.ScrollPaneConstants;
import javax.swing.ScrollPaneLayout;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ViewportLayout;
import javax.swing.plaf.LayerUI;

/**
 * BoxLayout, OverlayLayout, GroupLayout, SpringLayout and Spring, ScrollPaneLayout and JLayer
 * against the JDK. Every expected value is what the JDK's SizeRequirements / Spring math yields;
 * the run against the plain JDK keeps them honest. Gaps that depend on the look and feel are
 * compared against LayoutStyle itself; elsewhere every component has explicit sizes so no
 * look-and-feel metric reaches the numbers, and the springs are chosen so the stretch strain is
 * exactly 1 and no float rounding is involved.
 */
public class TestLayoutContracts {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    static void bounds(Component c, int x, int y, int w, int h, String what) {
        check(c.getX() == x && c.getY() == y && c.getWidth() == w && c.getHeight() == h,
              what + ": expected " + x + "," + y + " " + w + "x" + h + " got " + c.getBounds());
    }

    static JPanel sized(int minW, int minH, int prefW, int prefH, int maxW, int maxH) {
        JPanel p = new JPanel();
        p.setMinimumSize(new Dimension(minW, minH));
        p.setPreferredSize(new Dimension(prefW, prefH));
        p.setMaximumSize(new Dimension(maxW, maxH));
        p.setAlignmentX(0.5f);
        p.setAlignmentY(0.5f);
        return p;
    }

    static JPanel rigid(int w, int h) { return sized(w, h, w, h, w, h); }

    static JPanel hiddenRigid(int w, int h) {
        JPanel p = rigid(w, h);
        p.setVisible(false);
        return p;
    }

    static void boxLayout() {
        // Y axis, growing: only b has play, so it takes all of it; across, the aligned math
        // centres the rigid child and stretches b to its maximum.
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        BoxLayout box = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(box);
        JPanel a = rigid(20, 20);
        JPanel b = sized(40, 30, 40, 30, 100, 80);
        panel.add(a);
        panel.add(b);
        check(box.preferredLayoutSize(panel).equals(new Dimension(50, 60)), "box preferred " + box.preferredLayoutSize(panel));
        check(box.minimumLayoutSize(panel).equals(new Dimension(50, 60)), "box minimum " + box.minimumLayoutSize(panel));
        check(box.maximumLayoutSize(panel).equals(new Dimension(110, 110)), "box maximum " + box.maximumLayoutSize(panel));
        check(box.getLayoutAlignmentX(panel) == 0.5f, "box alignment x");
        panel.setSize(110, 110);
        panel.doLayout();
        bounds(a, 45, 5, 20, 20, "box rigid child");
        bounds(b, 5, 25, 100, 80, "box stretched child");

        // X axis, compressed below preferred: the shortfall is shared by (pref - min).
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        JPanel c1 = sized(10, 10, 30, 10, 30, 10);
        JPanel c2 = sized(10, 10, 30, 10, 30, 10);
        row.add(c1);
        row.add(c2);
        row.setSize(50, 10);
        row.doLayout();
        bounds(c1, 0, 0, 25, 10, "compressed first");
        bounds(c2, 25, 0, 25, 10, "compressed second");

        // Glue soaks up the slack; the float factor is the JDK's expandedTile formula.
        Box glued = Box.createHorizontalBox();
        JPanel left = rigid(20, 10), right = rigid(20, 10);
        glued.add(left);
        glued.add(Box.createHorizontalGlue());
        glued.add(right);
        glued.setSize(100, 10);
        glued.doLayout();
        int glue = (int) ((60f / (Short.MAX_VALUE - 0)) * Short.MAX_VALUE);
        bounds(left, 0, 0, 20, 10, "glue left");
        check(right.getX() == 20 + glue, "glue right x " + right.getX() + " expected " + (20 + glue));

        // Invisible children take no space.
        JPanel hidden = new JPanel();
        hidden.setLayout(new BoxLayout(hidden, BoxLayout.X_AXIS));
        JPanel h1 = rigid(20, 10), h2 = hiddenRigid(30, 10), h3 = rigid(20, 10);
        hidden.add(h1);
        hidden.add(h2);
        hidden.add(h3);
        check(hidden.getPreferredSize().width == 40, "invisible child preferred " + hidden.getPreferredSize());
        hidden.setSize(40, 10);
        hidden.doLayout();
        check(h3.getX() == 20, "invisible child skipped " + h3.getX());
    }

    static void boxContracts() {
        // Constructing a Box installs its BoxLayout without going through the throwing setLayout.
        Box box = new Box(BoxLayout.Y_AXIS);
        check(box.getLayout() instanceof BoxLayout, "box layout " + box.getLayout());
        boolean threw = false;
        try {
            box.setLayout(new FlowLayout());
        } catch (java.awt.AWTError expected) {
            threw = true;
        }
        check(threw, "Box.setLayout throws AWTError");
        check(box.getLayout() instanceof BoxLayout, "box layout kept");

        // BoxLayout add/remove notifications see the child's parent set to the target.
        JPanel child = rigid(10, 20);
        box.add(child);
        box.add(Box.createVerticalStrut(5));
        check(box.getPreferredSize().equals(new Dimension(10, 25)), "box add preferred " + box.getPreferredSize());
        box.remove(child);
        check(box.getPreferredSize().equals(new Dimension(0, 5)), "box remove preferred " + box.getPreferredSize());
        box.removeAll();
        check(box.getComponentCount() == 0, "box removeAll");

        Component strut = Box.createHorizontalStrut(7);
        check(strut.getMinimumSize().equals(new Dimension(7, 0)), "strut min " + strut.getMinimumSize());
        check(strut.getMaximumSize().equals(new Dimension(7, Short.MAX_VALUE)), "strut max " + strut.getMaximumSize());
        Component glue = Box.createGlue();
        check(glue.getMaximumSize().equals(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE)), "glue max " + glue.getMaximumSize());
        Box.Filler filler = (Box.Filler) Box.createRigidArea(new Dimension(3, 4));
        filler.changeShape(new Dimension(1, 1), new Dimension(2, 2), new Dimension(3, 3));
        check(filler.getPreferredSize().equals(new Dimension(2, 2)), "changeShape preferred " + filler.getPreferredSize());

        // JSeparator rejects an orientation other than HORIZONTAL or VERTICAL and is not focusable.
        threw = false;
        try {
            new javax.swing.JSeparator(42);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "JSeparator orientation check");
        check(!new javax.swing.JSeparator().isFocusable(), "JSeparator focusable");
    }

    static void overlayLayout() {
        JPanel panel = new JPanel();
        OverlayLayout overlay = new OverlayLayout(panel);
        panel.setLayout(overlay);
        JPanel a = rigid(20, 20);
        JPanel b = sized(40, 40, 40, 40, 100, 100);
        panel.add(a);
        panel.add(b);
        check(overlay.preferredLayoutSize(panel).equals(new Dimension(40, 40)), "overlay preferred " + overlay.preferredLayoutSize(panel));
        panel.setSize(100, 100);
        panel.doLayout();
        bounds(a, 40, 40, 20, 20, "overlay centred");
        bounds(b, 0, 0, 100, 100, "overlay stretched");
    }

    static void groupLayout() {
        // Sequential with an explicit gap; a resizable component takes the slack.
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        GroupLayout g = new GroupLayout(panel);
        panel.setLayout(g);
        JPanel a = rigid(20, 10);
        JPanel b = sized(10, 20, 30, 20, Short.MAX_VALUE, 20);
        g.setHorizontalGroup(g.createSequentialGroup().addComponent(a).addGap(8).addComponent(b));
        g.setVerticalGroup(g.createParallelGroup(GroupLayout.Alignment.CENTER).addComponent(a).addComponent(b));
        check(panel.getComponentCount() == 2, "group layout adds its components to the host");
        check(g.preferredLayoutSize(panel).equals(new Dimension(68, 30)), "group preferred " + g.preferredLayoutSize(panel));
        check(g.minimumLayoutSize(panel).equals(new Dimension(48, 30)), "group minimum " + g.minimumLayoutSize(panel));
        panel.setSize(110, 50);
        panel.doLayout();
        // The top-level sequential wrapper gives its parallel group min(max(40, pref), max) = 20,
        // so the CENTER group centres inside its own 20px span, not the host's 40.
        bounds(a, 5, 10, 20, 10, "group centred rigid");
        bounds(b, 33, 5, 72, 20, "group resizable");

        // PREFERRED_SIZE overrides pin a resizable component at its preferred size; a
        // trailing parallel group right-aligns inside its own (non-growing) span.
        JPanel pinned = new JPanel();
        GroupLayout p = new GroupLayout(pinned);
        pinned.setLayout(p);
        JPanel x = rigid(20, 10);
        JPanel y = sized(10, 10, 30, 10, Short.MAX_VALUE, 10);
        p.setHorizontalGroup(p.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addComponent(x).addComponent(y, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
        p.setVerticalGroup(p.createSequentialGroup().addComponent(x).addComponent(y));
        pinned.setSize(100, 20);
        pinned.doLayout();
        bounds(x, 10, 0, 20, 10, "trailing rigid");
        bounds(y, 0, 10, 30, 10, "pinned at preferred");

        // linkSize makes both as wide as the widest; the boolean addComponent overload.
        JPanel linked = new JPanel();
        GroupLayout l = new GroupLayout(linked);
        linked.setLayout(l);
        JPanel n = rigid(20, 10), w = rigid(30, 10);
        l.setHorizontalGroup(l.createSequentialGroup().addComponent(false, n).addComponent(w));
        l.setVerticalGroup(l.createParallelGroup().addComponent(n).addComponent(w));
        l.linkSize(SwingConstants.HORIZONTAL, n, w);
        linked.setSize(60, 10);
        linked.doLayout();
        bounds(n, 0, 0, 30, 10, "linked narrow");
        bounds(w, 30, 0, 30, 10, "linked wide");

        // Visibility: hidden components collapse unless told to keep their space.
        JPanel vis = new JPanel();
        GroupLayout v = new GroupLayout(vis);
        vis.setLayout(v);
        JPanel v1 = rigid(20, 10), v2 = hiddenRigid(30, 10), v3 = rigid(20, 10);
        v.setHorizontalGroup(v.createSequentialGroup().addComponent(v1).addComponent(v2).addComponent(v3));
        v.setVerticalGroup(v.createParallelGroup().addComponent(v1).addComponent(v2).addComponent(v3));
        vis.setSize(70, 10);
        vis.doLayout();
        check(v3.getX() == 20, "hidden component collapses " + v3.getX());
        v.setHonorsVisibility(v2, Boolean.FALSE);
        vis.doLayout();
        check(v3.getX() == 50, "honorsVisibility=false keeps the space " + v3.getX());

        // LayoutStyle-driven gaps: whatever the look and feel says, the layout must use it.
        JPanel gaps = new JPanel();
        GroupLayout s = new GroupLayout(gaps);
        gaps.setLayout(s);
        JPanel g1 = rigid(20, 10), g2 = rigid(20, 10);
        s.setHorizontalGroup(s.createSequentialGroup().addContainerGap().addComponent(g1)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED).addComponent(g2));
        s.setVerticalGroup(s.createParallelGroup().addComponent(g1).addComponent(g2));
        gaps.setSize(gaps.getPreferredSize());
        gaps.doLayout();
        LayoutStyle style = LayoutStyle.getInstance();
        int container = style.getContainerGap(g1, SwingConstants.WEST, gaps);
        int related = style.getPreferredGap(g1, g2, LayoutStyle.ComponentPlacement.RELATED, SwingConstants.EAST, gaps);
        check(g1.getX() == container, "container gap " + g1.getX() + " vs " + container);
        check(g2.getX() == container + 20 + related, "preferred gap " + g2.getX() + " vs " + (container + 20 + related));

        JPanel auto = new JPanel();
        GroupLayout ag = new GroupLayout(auto);
        auto.setLayout(ag);
        ag.setAutoCreateGaps(true);
        check(ag.getAutoCreateGaps(), "autoCreateGaps");
        JPanel a1 = rigid(20, 10), a2 = rigid(20, 10);
        ag.setHorizontalGroup(ag.createSequentialGroup().addComponent(a1).addComponent(a2));
        ag.setVerticalGroup(ag.createParallelGroup().addComponent(a1).addComponent(a2));
        auto.setSize(auto.getPreferredSize());
        auto.doLayout();
        int autoGap = style.getPreferredGap(a1, a2, LayoutStyle.ComponentPlacement.RELATED, SwingConstants.EAST, auto);
        check(a1.getX() == 0, "no container gap without autoCreateContainerGaps");
        check(a2.getX() == 20 + autoGap, "auto gap " + a2.getX() + " vs " + (20 + autoGap));
    }

    private static void eq(Object expected, Object actual, String what) {
        check(expected.equals(actual), what + ": expected " + expected + ", got " + actual);
    }

    private static JPanel fixed(int minW, int minH, int w, int h, int maxW, int maxH) {
        JPanel p = new JPanel(null);
        p.setMinimumSize(new Dimension(minW, minH));
        p.setPreferredSize(new Dimension(w, h));
        p.setMaximumSize(new Dimension(maxW, maxH));
        return p;
    }

    private static void springs() {
        Spring sum = Spring.sum(Spring.constant(10), Spring.constant(5, 10, 20));
        eq(15, sum.getMinimumValue(), "sum min");
        eq(20, sum.getPreferredValue(), "sum pref");
        eq(30, sum.getMaximumValue(), "sum max");
        Spring max = Spring.max(Spring.constant(3), Spring.constant(1, 5, 9));
        eq(3, max.getMinimumValue(), "max min");
        eq(5, max.getPreferredValue(), "max pref");
        eq(9, max.getMaximumValue(), "max max");
        Spring minus = Spring.minus(Spring.constant(2, 4, 6));
        eq(-6, minus.getMinimumValue(), "minus min");
        eq(-4, minus.getPreferredValue(), "minus pref");
        eq(-2, minus.getMaximumValue(), "minus max");
        Spring scaled = Spring.scale(Spring.constant(2, 4, 6), 2f);
        eq(4, scaled.getMinimumValue(), "scale min");
        eq(8, scaled.getPreferredValue(), "scale pref");
        eq(12, scaled.getMaximumValue(), "scale max");
        JPanel c = fixed(10, 10, 40, 20, 90, 70);
        Spring width = Spring.width(c);
        eq(10, width.getMinimumValue(), "width min");
        eq(40, width.getPreferredValue(), "width pref");
        eq(90, width.getMaximumValue(), "width max");
        eq(20, Spring.height(c).getPreferredValue(), "height pref");
    }

    /** The SpringLayout class Javadoc / tutorial example: a label and a field side by side. */
    private static void springLayout() {
        JPanel contentPane = new JPanel(null);
        contentPane.setBorder(null);
        SpringLayout layout = new SpringLayout();
        contentPane.setLayout(layout);
        JPanel label = fixed(40, 20, 40, 20, 40, 20);
        JPanel textField = fixed(10, 10, 100, 30, 200, 50);
        contentPane.add(label);
        contentPane.add(textField);
        layout.putConstraint(SpringLayout.WEST, label, 5, SpringLayout.WEST, contentPane);
        layout.putConstraint(SpringLayout.NORTH, label, 5, SpringLayout.NORTH, contentPane);
        layout.putConstraint(SpringLayout.WEST, textField, 5, SpringLayout.EAST, label);
        layout.putConstraint(SpringLayout.NORTH, textField, 5, SpringLayout.NORTH, contentPane);
        layout.putConstraint(SpringLayout.EAST, contentPane, 5, SpringLayout.EAST, textField);
        layout.putConstraint(SpringLayout.SOUTH, contentPane, 5, SpringLayout.SOUTH, textField);

        eq(new Dimension(155, 40), layout.preferredLayoutSize(contentPane), "spring preferred");
        eq(new Dimension(65, 20), layout.minimumLayoutSize(contentPane), "spring minimum");
        eq(new Dimension(255, 60), layout.maximumLayoutSize(contentPane), "spring maximum");

        contentPane.setSize(155, 40);
        layout.layoutContainer(contentPane);
        eq(new Rectangle(5, 5, 40, 20), label.getBounds(), "label at preferred");
        eq(new Rectangle(50, 5, 100, 30), textField.getBounds(), "field at preferred");
        eq(45, layout.getConstraint(SpringLayout.EAST, label).getValue(), "label EAST");
        eq(50, layout.getConstraints(textField).getX().getValue(), "field x");

        contentPane.setSize(255, 60);
        layout.layoutContainer(contentPane);
        eq(new Rectangle(5, 5, 40, 20), label.getBounds(), "rigid label stretched");
        eq(new Rectangle(50, 5, 200, 50), textField.getBounds(), "field stretched");
    }

    private static void scrollPaneLayout() {
        JPanel view = fixed(0, 0, 300, 400, 300, 400);
        JScrollPane sp = new JScrollPane(view,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        sp.setBorder(null);
        sp.setViewportBorder(null);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(12, 12));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(12, 12));
        sp.setColumnHeaderView(fixed(0, 0, 300, 20, 300, 20));
        sp.setRowHeaderView(fixed(0, 0, 30, 400, 30, 400));
        JPanel upperLeft = new JPanel(), upperRight = new JPanel(), lowerLeft = new JPanel(), lowerRight = new JPanel();
        sp.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, upperLeft);
        sp.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, upperRight);
        sp.setCorner(ScrollPaneConstants.LOWER_LEFT_CORNER, lowerLeft);
        sp.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, lowerRight);
        ScrollPaneLayout layout = new ScrollPaneLayout();
        sp.setLayout(layout);
        check(sp.getLayout() == layout, "setLayout installs the ScrollPaneLayout");
        check(layout.getViewport() == sp.getViewport(), "layout synced with the viewport");
        try {
            sp.setLayout(new FlowLayout());
            check(false, "JScrollPane accepted a FlowLayout");
        } catch (ClassCastException expected) {
            // JDK: the layout of a JScrollPane must be a ScrollPaneLayout
        }

        eq(new Dimension(342, 432), layout.preferredLayoutSize(sp), "scroll pane preferred");

        sp.setSize(200, 150);
        layout.layoutContainer(sp);
        eq(new Rectangle(30, 20, 158, 118), sp.getViewport().getBounds(), "viewport");
        eq(new Rectangle(30, 0, 158, 20), sp.getColumnHeader().getBounds(), "column header");
        eq(new Rectangle(0, 20, 30, 118), sp.getRowHeader().getBounds(), "row header");
        eq(new Rectangle(188, 20, 12, 118), sp.getVerticalScrollBar().getBounds(), "vertical bar");
        eq(new Rectangle(30, 138, 158, 12), sp.getHorizontalScrollBar().getBounds(), "horizontal bar");
        check(sp.getVerticalScrollBar().isVisible() && sp.getHorizontalScrollBar().isVisible(), "bars visible");
        eq(new Rectangle(0, 0, 30, 20), upperLeft.getBounds(), "upper left corner");
        eq(new Rectangle(188, 0, 12, 20), upperRight.getBounds(), "upper right corner");
        eq(new Rectangle(0, 138, 30, 12), lowerLeft.getBounds(), "lower left corner");
        eq(new Rectangle(188, 138, 12, 12), lowerRight.getBounds(), "lower right corner");
        eq(new Rectangle(30, 20, 158, 118), sp.getViewportBorderBounds(), "viewport border bounds");
    }

    private static void defaultLayouts() {
        JScrollPane sp = new JScrollPane(new JPanel());
        check(sp.getLayout() instanceof ScrollPaneLayout.UIResource, "JScrollPane default layout: " + sp.getLayout());
        check(((ScrollPaneLayout) sp.getLayout()).getViewport() == sp.getViewport(), "default layout synced");
        check(sp.getViewport().getLayout() instanceof ViewportLayout, "JViewport default layout: " + sp.getViewport().getLayout());

        // A ViewportLayout of one's own is the JDK policy: a non-Scrollable view keeps its
        // preferred size while larger than the viewport and fills it once smaller.
        JViewport vp = new JViewport();
        vp.setLayout(new ViewportLayout());
        JPanel view = fixed(0, 0, 300, 400, 300, 400);
        vp.setView(view);
        vp.setSize(200, 150);
        vp.doLayout();
        eq(new Dimension(300, 400), view.getSize(), "view at preferred size");
        vp.setSize(500, 600);
        vp.doLayout();
        eq(new Dimension(500, 600), view.getSize(), "view fills a larger viewport");
        eq(new Dimension(300, 400), vp.getLayout().preferredLayoutSize(vp), "viewport preferred");
    }

    private static void layer() {
        final int[] viewPaints = {0};
        JPanel view = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                viewPaints[0]++;
                g.setColor(Color.BLUE);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        view.setPreferredSize(new Dimension(20, 20));
        final JComponent[] painted = {null};
        LayerUI<JPanel> ui = new LayerUI<JPanel>() {
            @Override public void paint(Graphics g, JComponent c) {
                painted[0] = c;
                super.paint(g, c);
                g.setColor(Color.RED);
                g.fillRect(0, 0, 5, 5);
            }
        };
        JLayer<JPanel> layer = new JLayer<JPanel>(view, ui);
        check(layer.getView() == view && layer.getUI() == ui, "view and ui");
        check(layer.getGlassPane() != null && !layer.getGlassPane().isVisible(), "hidden glass pane");
        eq(new Dimension(20, 20), layer.getPreferredSize(), "layer preferred from view");
        try {
            layer.setLayout(new FlowLayout());
            check(false, "JLayer accepted a layout");
        } catch (IllegalArgumentException expected) {
            // JDK: JLayer.setLayout() not supported
        }
        try {
            layer.add(new JPanel());
            check(false, "JLayer accepted a child");
        } catch (UnsupportedOperationException expected) {
            // JDK: use setView() or setGlassPane()
        }

        layer.setSize(20, 20);
        layer.doLayout();
        eq(new Rectangle(0, 0, 20, 20), view.getBounds(), "view fills the layer");
        eq(new Rectangle(0, 0, 20, 20), layer.getGlassPane().getBounds(), "glass pane fills the layer");

        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        layer.paint(g);
        g.dispose();
        check(painted[0] == layer, "LayerUI.paint gets the layer");
        eq(1, viewPaints[0], "view painted once");
        eq(0xffff0000, img.getRGB(2, 2), "LayerUI decoration over the view");
        eq(0xff0000ff, img.getRGB(10, 10), "view painted through the LayerUI");

        JPanel holder = new JPanel(null);
        holder.add(layer);
        check(SwingUtilities.getUnwrappedParent(view) == holder, "unwrapped parent skips the layer");
        JViewport vp = new JViewport();
        vp.setView(layer);
        check(SwingUtilities.getUnwrappedView(vp) == view, "unwrapped view skips the layer");
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "shadow".equals(args[0])) {
            installHeadlessFocusManager();
        }
        boxLayout();
        boxContracts();
        overlayLayout();
        groupLayout();
        springs();
        springLayout();
        scrollPaneLayout();
        defaultLayouts();
        layer();
        System.out.println("TestLayoutContracts: ok");
    }

    /**
     * The JDK's Component.hide() and setFocusable(false) ask the keyboard focus manager for the
     * focus owner, and the first ask builds a DefaultKeyboardFocusManager whose peer lookup casts
     * the default toolkit to sun.awt.KeyboardFocusManagerPeerProvider. In the patched run the
     * default toolkit is the shadow HostToolkit, and KeyboardFocusManager itself is not
     * patchable (its JNI layout differs), so the manager is built once here with the JDK's
     * HeadlessToolkit, the toolkit of the plain run whose focus peer is a no-op, standing in
     * as default. Only that construction sees the stand-in; every case after it runs on the
     * shadow toolkit.
     */
    private static void installHeadlessFocusManager() throws ReflectiveOperationException {
        java.lang.reflect.Field field = java.awt.Toolkit.class.getDeclaredField("defaultToolkit");
        field.setAccessible(true);
        java.awt.Toolkit host = java.awt.Toolkit.getDefaultToolkit();
        Object headless = Class.forName("sun.awt.HeadlessToolkit")
            .getConstructor(java.awt.Toolkit.class).newInstance(host);
        field.set(null, headless);
        try {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
        } finally {
            field.set(null, host);
        }
    }
}
