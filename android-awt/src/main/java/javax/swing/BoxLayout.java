package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.io.Serializable;

public class BoxLayout implements LayoutManager2, Serializable {
    private static final long serialVersionUID = 1L;

    public static final int X_AXIS = 0;
    public static final int Y_AXIS = 1;
    public static final int LINE_AXIS = 2;
    public static final int PAGE_AXIS = 3;

    private final int axis;
    private final Container target;

    public BoxLayout(Container target, int axis) {
        if (axis != X_AXIS && axis != Y_AXIS && axis != LINE_AXIS && axis != PAGE_AXIS) {
            throw new IllegalArgumentException("Invalid axis: " + axis);
        }
        this.target = target; this.axis = axis;
    }

    public final int getAxis() { return axis; }
    public final Container getTarget() { return target; }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void addLayoutComponent(Component comp, Object constraints) {}
    @Override public void removeLayoutComponent(Component comp) {}

    private boolean isXAxis() { return axis == X_AXIS || axis == LINE_AXIS; }

    @Override
    public Dimension preferredLayoutSize(Container t) {
        int w = 0, h = 0;
        for (Component c : t.getComponents()) {
            if (!c.isVisible()) continue;
            Dimension d = c.getPreferredSize();
            if (isXAxis()) { w += d.width; h = Math.max(h, d.height); }
            else           { h += d.height; w = Math.max(w, d.width); }
        }
        Insets ins = t.getInsets();
        return new Dimension(w + ins.left + ins.right, h + ins.top + ins.bottom);
    }

    @Override public Dimension minimumLayoutSize(Container target) { return preferredLayoutSize(target); }
    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container target) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container target) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}

    @Override
    public void layoutContainer(Container t) {
        Insets ins = t.getInsets();
        int x = ins.left, y = ins.top;
        int innerH = t.getHeight() - ins.top - ins.bottom;
        int innerW = t.getWidth() - ins.left - ins.right;
        if (isXAxis()) {
            for (Component c : t.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                int w = d.width;
                c.setBounds(x, y, w, innerH);
                x += w;
            }
        } else {
            for (Component c : t.getComponents()) {
                if (!c.isVisible()) continue;
                Dimension d = c.getPreferredSize();
                int h = d.height;
                c.setBounds(x, y, innerW, h);
                y += h;
            }
        }
    }
}
