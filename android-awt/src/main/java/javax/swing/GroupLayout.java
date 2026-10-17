package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.util.ArrayList;
import java.util.List;

/** Two-axis group layout used by the original RuneLite panels. */
public class GroupLayout implements LayoutManager2 {
    public enum Alignment { LEADING, TRAILING, CENTER, BASELINE }
    public static final int DEFAULT_SIZE = -1, PREFERRED_SIZE = -2;
    private static final int LIMIT = 32767;
    private final Container host;
    private Group horizontal, vertical;
    private boolean autoCreateGaps, autoCreateContainerGaps;
    private final List<Component[]> horizontalLinks = new ArrayList<>(), verticalLinks = new ArrayList<>();

    public GroupLayout(Container host) { this.host = host; }
    private static int dim(Dimension d, int axis) { return Math.max(0, axis == 0 ? d.width : d.height); }
    private static int sum(int a, int b) { return Math.min(LIMIT, a + b); }
    private abstract class Spring {
        Alignment alignment;
        abstract int size(int axis, int kind);
        abstract void layout(int axis, int origin, int size);
    }
    private class ComponentSpring extends Spring {
        final Component component;
        final int min, pref, max;
        ComponentSpring(Component c, int min, int pref, int max) {
            component = c; this.min = min; this.pref = pref; this.max = max;
            if (c.getParent() != host) host.add(c);
        }
        private int preferred(int axis) {
            int result = pref >= 0 ? pref : dim(component.getPreferredSize(), axis);
            for (Component[] link : axis == 0 ? horizontalLinks : verticalLinks) {
                boolean member = false;
                for (Component c : link) if (c == component) member = true;
                if (member) for (Component c : link) result = Math.max(result, dim(c.getPreferredSize(), axis));
            }
            return result;
        }
        int size(int axis, int kind) {
            if (!component.isVisible()) return 0;
            int p = preferred(axis);
            int value = kind == 0 ? min : kind == 1 ? pref : max;
            if (value >= 0) return Math.min(LIMIT, value);
            if (value == PREFERRED_SIZE || kind == 1) return p;
            if (kind == 0) return Math.min(p, dim(component.getMinimumSize(), axis));
            // Shadow controls have no L&F delegate supplying their maximum sizes.
            if ((component instanceof AbstractButton || component instanceof JLabel) && !component.isMaximumSizeSet()) return p;
            if (component instanceof javax.swing.text.JTextComponent && axis == 1 && !component.isMaximumSizeSet()) return p;
            return Math.max(p, Math.min(LIMIT, dim(component.getMaximumSize(), axis)));
        }
        void layout(int axis, int origin, int length) {
            if (axis == 0) component.setBounds(origin, component.getY(), length, component.getHeight());
            else component.setBounds(component.getX(), origin, component.getWidth(), length);
        }
    }
    private class Gap extends Spring {
        final int min, pref, max;
        Gap(int min, int pref, int max) { this.min = Math.max(0, min); this.pref = Math.max(0, pref); this.max = Math.max(this.pref, max); }
        int size(int axis, int kind) { return kind == 0 ? min : kind == 1 ? pref : max; }
        void layout(int axis, int origin, int length) {}
    }
    public abstract class Group extends Spring {
        final List<Spring> children = new ArrayList<>();
        public Group addGroup(Group group) { children.add(group); return this; }
        public Group addComponent(Component c) { return addComponent(c, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE); }
        public Group addComponent(Component c, int min, int pref, int max) { children.add(new ComponentSpring(c, min, pref, max)); return this; }
        public Group addGap(int size) { return addGap(size, size, size); }
        public Group addGap(int min, int pref, int max) { children.add(new Gap(min, pref, max)); return this; }
    }
    public class ParallelGroup extends Group {
        final Alignment defaultAlignment;
        final boolean resizable;
        ParallelGroup(Alignment alignment, boolean resizable) { defaultAlignment = alignment; this.resizable = resizable; }
        public ParallelGroup addGroup(Alignment alignment, Group group) { group.alignment = alignment; super.addGroup(group); return this; }
        public ParallelGroup addComponent(Alignment a, Component c) { return addComponent(c, a); }
        public ParallelGroup addComponent(Alignment a, Component c, int min, int pref, int max) { return addComponent(c, a, min, pref, max); }
        public ParallelGroup addComponent(Component c, Alignment a) { return addComponent(c, a, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE); }
        public ParallelGroup addComponent(Component c, Alignment a, int min, int pref, int max) {
            ComponentSpring spring = new ComponentSpring(c, min, pref, max); spring.alignment = a; children.add(spring); return this;
        }
        @Override public ParallelGroup addComponent(Component c) { super.addComponent(c); return this; }
        @Override public ParallelGroup addComponent(Component c, int min, int pref, int max) { super.addComponent(c, min, pref, max); return this; }
        @Override public ParallelGroup addGroup(Group g) { super.addGroup(g); return this; }
        @Override public ParallelGroup addGap(int size) { super.addGap(size); return this; }
        @Override public ParallelGroup addGap(int min, int pref, int max) { super.addGap(min, pref, max); return this; }
        int size(int axis, int kind) {
            int result = 0;
            for (Spring s : children) result = Math.max(result, s.size(axis, resizable ? kind : 1));
            return result;
        }
        void layout(int axis, int origin, int length) {
            for (Spring s : children) {
                int n = Math.min(length, s.size(axis, 2));
                Alignment a = s.alignment == null ? defaultAlignment : s.alignment;
                int offset = a == Alignment.TRAILING ? length - n : a == Alignment.CENTER ? (length - n) / 2 : 0;
                s.layout(axis, origin + offset, n);
            }
        }
    }
    public class SequentialGroup extends Group {
        public SequentialGroup addContainerGap() { return addGap(12); }
        public SequentialGroup addContainerGap(int pref, int max) { return addGap(pref, pref, max); }
        public SequentialGroup addPreferredGap(LayoutStyle.ComponentPlacement type) { return addGap(type == LayoutStyle.ComponentPlacement.UNRELATED ? 12 : 6); }
        public SequentialGroup addPreferredGap(LayoutStyle.ComponentPlacement type, int pref, int max) { return addGap(pref, pref, max); }
        @Override public SequentialGroup addComponent(Component c) { super.addComponent(c); return this; }
        @Override public SequentialGroup addComponent(Component c, int min, int pref, int max) { super.addComponent(c, min, pref, max); return this; }
        @Override public SequentialGroup addGroup(Group g) { super.addGroup(g); return this; }
        @Override public SequentialGroup addGap(int size) { super.addGap(size); return this; }
        @Override public SequentialGroup addGap(int min, int pref, int max) { super.addGap(min, pref, max); return this; }
        int size(int axis, int kind) {
            int result = 0;
            for (Spring s : children) result = sum(result, s.size(axis, kind));
            return result;
        }
        void layout(int axis, int origin, int length) {
            int[] sizes = new int[children.size()];
            int total = 0;
            for (int i = 0; i < sizes.length; i++) { sizes[i] = children.get(i).size(axis, 1); total += sizes[i]; }
            int remaining = length - total;
            while (remaining != 0) {
                boolean grow = remaining > 0;
                int count = 0;
                for (int i = 0; i < sizes.length; i++) {
                    int bound = children.get(i).size(axis, grow ? 2 : 0);
                    if (grow ? sizes[i] < bound : sizes[i] > bound) count++;
                }
                if (count == 0) break;
                int share = Math.max(1, Math.abs(remaining) / count);
                for (int i = 0; i < sizes.length && remaining != 0; i++) {
                    int bound = children.get(i).size(axis, grow ? 2 : 0);
                    int room = grow ? bound - sizes[i] : sizes[i] - bound;
                    int delta = Math.min(Math.min(share, Math.abs(remaining)), Math.max(0, room));
                    sizes[i] += grow ? delta : -delta;
                    remaining += grow ? -delta : delta;
                }
            }
            for (int i = 0; i < sizes.length; i++) { children.get(i).layout(axis, origin, sizes[i]); origin += sizes[i]; }
        }
    }
    public void setAutoCreateGaps(boolean b) { autoCreateGaps = b; }
    public boolean getAutoCreateGaps() { return autoCreateGaps; }
    public void setAutoCreateContainerGaps(boolean b) { autoCreateContainerGaps = b; }
    public boolean getAutoCreateContainerGaps() { return autoCreateContainerGaps; }
    public void setHorizontalGroup(Group g) { horizontal = g; }
    public void setVerticalGroup(Group g) { vertical = g; }
    public ParallelGroup createParallelGroup() { return createParallelGroup(Alignment.LEADING); }
    public ParallelGroup createParallelGroup(Alignment a) { return createParallelGroup(a, true); }
    public ParallelGroup createParallelGroup(Alignment a, boolean resizable) { return new ParallelGroup(a, resizable); }
    public SequentialGroup createSequentialGroup() { return new SequentialGroup(); }
    public void linkSize(Component... components) { linkSize(0, components); linkSize(1, components); }
    public void linkSize(int axis, Component... components) { (axis == 0 ? horizontalLinks : verticalLinks).add(components.clone()); }
    @Override public void addLayoutComponent(String name, Component c) {}
    @Override public void addLayoutComponent(Component c, Object constraints) {}
    @Override public void removeLayoutComponent(Component c) {}
    private Dimension measure(Container parent, int kind) {
        Insets i = parent.getInsets(); int gap = autoCreateContainerGaps ? 24 : 0;
        return new Dimension((horizontal == null ? 0 : horizontal.size(0, kind)) + i.left + i.right + gap,
            (vertical == null ? 0 : vertical.size(1, kind)) + i.top + i.bottom + gap);
    }
    @Override public Dimension preferredLayoutSize(Container p) { return measure(p, 1); }
    @Override public Dimension minimumLayoutSize(Container p) { return measure(p, 0); }
    @Override public Dimension maximumLayoutSize(Container p) { return measure(p, 2); }
    @Override public float getLayoutAlignmentX(Container p) { return .5f; }
    @Override public float getLayoutAlignmentY(Container p) { return .5f; }
    @Override public void invalidateLayout(Container p) {}
    @Override public void layoutContainer(Container p) {
        Insets i = p.getInsets(); int gap = autoCreateContainerGaps ? 12 : 0;
        if (horizontal != null) horizontal.layout(0, i.left + gap, Math.max(0, p.getWidth() - i.left - i.right - gap * 2));
        if (vertical != null) vertical.layout(1, i.top + gap, Math.max(0, p.getHeight() - i.top - i.bottom - gap * 2));
    }
}
