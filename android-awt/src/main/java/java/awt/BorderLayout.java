package java.awt;

import java.io.Serializable;
import java.util.IdentityHashMap;
import java.util.Map;

public class BorderLayout implements LayoutManager2, Serializable {
    private static final long serialVersionUID = -8658291919501921765L;

    public static final String NORTH = "North";
    public static final String SOUTH = "South";
    public static final String EAST = "East";
    public static final String WEST = "West";
    public static final String CENTER = "Center";
    public static final String PAGE_START = "First";
    public static final String PAGE_END = "Last";
    public static final String LINE_START = "Before";
    public static final String LINE_END = "After";
    public static final String BEFORE_FIRST_LINE = PAGE_START;
    public static final String AFTER_LAST_LINE = PAGE_END;
    public static final String BEFORE_LINE_BEGINS = LINE_START;
    public static final String AFTER_LINE_ENDS = LINE_END;

    private int hgap;
    private int vgap;

    private Component north, south, east, west, center;
    private final Map<Component, String> positions = new IdentityHashMap<>();

    public BorderLayout() { this(0, 0); }
    public BorderLayout(int hgap, int vgap) { this.hgap = hgap; this.vgap = vgap; }

    public int getHgap() { return hgap; }
    public void setHgap(int hgap) { this.hgap = hgap; }
    public int getVgap() { return vgap; }
    public void setVgap(int vgap) { this.vgap = vgap; }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        addLayoutComponent(comp, name);
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        String name = constraints == null ? CENTER : constraints.toString();
        positions.put(comp, name);
        if (NORTH.equals(name) || PAGE_START.equals(name)) north = comp;
        else if (SOUTH.equals(name) || PAGE_END.equals(name)) south = comp;
        else if (WEST.equals(name) || LINE_START.equals(name)) west = comp;
        else if (EAST.equals(name) || LINE_END.equals(name)) east = comp;
        else center = comp;
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        positions.remove(comp);
        if (comp == north) north = null;
        else if (comp == south) south = null;
        else if (comp == east) east = null;
        else if (comp == west) west = null;
        else if (comp == center) center = null;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        int w = 0, h = 0;
        if (north != null && north.isVisible()) { Dimension d = north.getPreferredSize(); w = Math.max(w, d.width); h += d.height + vgap; }
        if (south != null && south.isVisible()) { Dimension d = south.getPreferredSize(); w = Math.max(w, d.width); h += d.height + vgap; }
        int mid = 0, midH = 0;
        if (west != null && west.isVisible())   { Dimension d = west.getPreferredSize();   mid += d.width + hgap; midH = Math.max(midH, d.height); }
        if (east != null && east.isVisible())   { Dimension d = east.getPreferredSize();   mid += d.width + hgap; midH = Math.max(midH, d.height); }
        if (center != null && center.isVisible()) { Dimension d = center.getPreferredSize(); mid += d.width;        midH = Math.max(midH, d.height); }
        w = Math.max(w, mid);
        h += midH;
        Insets in = parent.getInsets();
        return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
    }

    @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container target) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container target) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}

    @Override
    public void layoutContainer(Container target) {
        Insets ins = target.getInsets();
        int top = ins.top;
        int bottom = target.getHeight() - ins.bottom;
        int left = ins.left;
        int right = target.getWidth() - ins.right;

        if (north != null && north.isVisible()) {
            int h = north.getPreferredSize().height;
            north.setBounds(left, top, right - left, h);
            top += h + vgap;
        }
        if (south != null && south.isVisible()) {
            int h = south.getPreferredSize().height;
            south.setBounds(left, bottom - h, right - left, h);
            bottom -= h + vgap;
        }
        if (east != null && east.isVisible()) {
            int w = east.getPreferredSize().width;
            east.setBounds(right - w, top, w, bottom - top);
            right -= w + hgap;
        }
        if (west != null && west.isVisible()) {
            int w = west.getPreferredSize().width;
            west.setBounds(left, top, w, bottom - top);
            left += w + hgap;
        }
        if (center != null && center.isVisible()) {
            center.setBounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }
}
