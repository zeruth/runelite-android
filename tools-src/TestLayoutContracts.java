// host-contract
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.ScrollPaneLayout;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.ViewportLayout;
import javax.swing.plaf.LayerUI;

/**
 * SpringLayout and Spring, ScrollPaneLayout and JLayer against the JDK. Every component has
 * explicit sizes so no look-and-feel metric reaches the numbers; the springs are chosen so the
 * stretch strain is exactly 1 and no float rounding is involved.
 */
public class TestLayoutContracts {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
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

    public static void main(String[] args) {
        springs();
        springLayout();
        scrollPaneLayout();
        defaultLayouts();
        layer();
        System.out.println("TestLayoutContracts: ok");
    }
}
