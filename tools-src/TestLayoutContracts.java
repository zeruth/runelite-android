// host-contract
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import javax.swing.JPanel;
import javax.swing.LayoutStyle;
import javax.swing.OverlayLayout;
import javax.swing.SwingConstants;

/**
 * BoxLayout, OverlayLayout and GroupLayout geometry. Every expected value is what the JDK's
 * SizeRequirements / Spring math yields; the run against the plain JDK keeps them honest.
 * Gaps that depend on the look and feel are compared against LayoutStyle itself.
 */
public class TestLayoutContracts {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
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

    public static void main(String[] args) {
        boxLayout();
        overlayLayout();
        groupLayout();
        System.out.println("Layout contracts passed");
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
        JPanel h1 = rigid(20, 10), h2 = rigid(30, 10), h3 = rigid(20, 10);
        h2.setVisible(false);
        hidden.add(h1);
        hidden.add(h2);
        hidden.add(h3);
        check(hidden.getPreferredSize().width == 40, "invisible child preferred " + hidden.getPreferredSize());
        hidden.setSize(40, 10);
        hidden.doLayout();
        check(h3.getX() == 20, "invisible child skipped " + h3.getX());
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
        JPanel v1 = rigid(20, 10), v2 = rigid(30, 10), v3 = rigid(20, 10);
        v.setHorizontalGroup(v.createSequentialGroup().addComponent(v1).addComponent(v2).addComponent(v3));
        v.setVerticalGroup(v.createParallelGroup().addComponent(v1).addComponent(v2).addComponent(v3));
        v2.setVisible(false);
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
}
