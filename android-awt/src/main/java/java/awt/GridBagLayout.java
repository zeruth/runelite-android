package java.awt;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GridBagLayout implements LayoutManager2, Serializable {
    private static final long serialVersionUID = 8838754796412211005L;

    private final Map<Component, GridBagConstraints> constraints = new HashMap<>();

    public GridBagLayout() {}

    public void setConstraints(Component comp, GridBagConstraints constraints) {
        this.constraints.put(comp, (GridBagConstraints) constraints.clone());
    }

    public GridBagConstraints getConstraints(Component comp) {
        GridBagConstraints c = constraints.get(comp);
        return c == null ? new GridBagConstraints() : (GridBagConstraints) c.clone();
    }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void addLayoutComponent(Component comp, Object cn) {
        if (cn instanceof GridBagConstraints) setConstraints(comp, (GridBagConstraints) cn);
    }
    @Override public void removeLayoutComponent(Component comp) { constraints.remove(comp); }
    @Override public Dimension preferredLayoutSize(Container parent) {
        Grid grid = measure(parent); Insets in = parent.getInsets();
        return new Dimension(sum(grid.widths) + in.left + in.right, sum(grid.heights) + in.top + in.bottom);
    }
    @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
    @Override public Dimension maximumLayoutSize(Container target) { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    @Override public float getLayoutAlignmentX(Container target) { return 0.5f; }
    @Override public float getLayoutAlignmentY(Container target) { return 0.5f; }
    @Override public void invalidateLayout(Container target) {}
    @Override public void layoutContainer(Container target) {
        Grid grid = measure(target); Insets outer = target.getInsets();
        distribute(grid.widths, grid.wx, target.getWidth() - outer.left - outer.right - sum(grid.widths));
        distribute(grid.heights, grid.wy, target.getHeight() - outer.top - outer.bottom - sum(grid.heights));
        int originX = outer.left + Math.max(0, target.getWidth() - outer.left - outer.right - sum(grid.widths)) / 2;
        int originY = outer.top + Math.max(0, target.getHeight() - outer.top - outer.bottom - sum(grid.heights)) / 2;
        for (Cell cell : grid.cells) {
            GridBagConstraints c = cell.c; Insets in = c.insets;
            int x = originX + range(grid.widths, 0, cell.x) + in.left;
            int y = originY + range(grid.heights, 0, cell.y) + in.top;
            int width = Math.max(0, range(grid.widths, cell.x, cell.w) - in.left - in.right);
            int height = Math.max(0, range(grid.heights, cell.y, cell.h) - in.top - in.bottom);
            Dimension pref = cell.component.getPreferredSize();
            int w = c.fill == GridBagConstraints.BOTH || c.fill == GridBagConstraints.HORIZONTAL ? width : Math.min(width, pref.width + c.ipadx);
            int h = c.fill == GridBagConstraints.BOTH || c.fill == GridBagConstraints.VERTICAL ? height : Math.min(height, pref.height + c.ipady);
            boolean left = c.anchor == GridBagConstraints.WEST || c.anchor == GridBagConstraints.NORTHWEST || c.anchor == GridBagConstraints.SOUTHWEST || c.anchor == GridBagConstraints.LINE_START || c.anchor == GridBagConstraints.FIRST_LINE_START || c.anchor == GridBagConstraints.LAST_LINE_START;
            boolean right = c.anchor == GridBagConstraints.EAST || c.anchor == GridBagConstraints.NORTHEAST || c.anchor == GridBagConstraints.SOUTHEAST || c.anchor == GridBagConstraints.LINE_END || c.anchor == GridBagConstraints.FIRST_LINE_END || c.anchor == GridBagConstraints.LAST_LINE_END;
            boolean top = c.anchor == GridBagConstraints.NORTH || c.anchor == GridBagConstraints.NORTHWEST || c.anchor == GridBagConstraints.NORTHEAST || c.anchor == GridBagConstraints.PAGE_START || c.anchor == GridBagConstraints.FIRST_LINE_START || c.anchor == GridBagConstraints.FIRST_LINE_END;
            boolean bottom = c.anchor == GridBagConstraints.SOUTH || c.anchor == GridBagConstraints.SOUTHWEST || c.anchor == GridBagConstraints.SOUTHEAST || c.anchor == GridBagConstraints.PAGE_END || c.anchor == GridBagConstraints.LAST_LINE_START || c.anchor == GridBagConstraints.LAST_LINE_END;
            cell.component.setBounds(x + (left ? 0 : right ? width - w : (width - w) / 2), y + (top ? 0 : bottom ? height - h : (height - h) / 2), w, h);
        }
    }

    private static final class Cell {
        Component component; GridBagConstraints c; int x, y, w, h;
    }
    private static final class Grid {
        java.util.List<Cell> cells = new java.util.ArrayList<>();
        int[] widths, heights; double[] wx, wy;
    }
    private Grid measure(Container parent) {
        Grid grid = new Grid(); int columns = 1, rows = 1, nextX = 0, nextY = 0;
        for (Component component : parent.getComponents()) {
            if (!component.isVisible()) continue;
            Cell cell = new Cell(); cell.component = component; cell.c = getConstraints(component);
            cell.x = cell.c.gridx < 0 ? nextX : cell.c.gridx;
            cell.y = cell.c.gridy < 0 ? nextY : cell.c.gridy;
            cell.w = Math.max(1, cell.c.gridwidth); cell.h = Math.max(1, cell.c.gridheight);
            columns = Math.max(columns, cell.x + cell.w); rows = Math.max(rows, cell.y + cell.h);
            if (cell.c.gridwidth == GridBagConstraints.REMAINDER) { nextX = 0; nextY = cell.y + cell.h; }
            else { nextX = cell.x + cell.w; nextY = cell.y; }
            grid.cells.add(cell);
        }
        grid.widths = new int[columns]; grid.heights = new int[rows]; grid.wx = new double[columns]; grid.wy = new double[rows];
        for (Cell cell : grid.cells) {
            if (cell.c.gridwidth == GridBagConstraints.REMAINDER) cell.w = columns - cell.x;
            if (cell.c.gridheight == GridBagConstraints.REMAINDER) cell.h = rows - cell.y;
            Dimension pref = cell.component.getPreferredSize(); Insets in = cell.c.insets;
            grow(grid.widths, cell.x, cell.w, pref.width + cell.c.ipadx + in.left + in.right);
            grow(grid.heights, cell.y, cell.h, pref.height + cell.c.ipady + in.top + in.bottom);
            for (int i = cell.x; i < cell.x + cell.w; i++) grid.wx[i] = Math.max(grid.wx[i], cell.c.weightx / cell.w);
            for (int i = cell.y; i < cell.y + cell.h; i++) grid.wy[i] = Math.max(grid.wy[i], cell.c.weighty / cell.h);
        }
        return grid;
    }
    private static int range(int[] values, int start, int count) { int result = 0; for (int i = start; i < start + count; i++) result += values[i]; return result; }
    private static int sum(int[] values) { return range(values, 0, values.length); }
    private static void grow(int[] values, int start, int count, int wanted) {
        int extra = wanted - range(values, start, count);
        if (extra > 0) for (int i = 0; i < count; i++) { int add = extra / (count - i); values[start + i] += add; extra -= add; }
    }
    private static void distribute(int[] sizes, double[] weights, int extra) {
        double total = 0; for (double weight : weights) total += weight;
        if (total <= 0) return;
        for (int i = 0; i < sizes.length; i++) if (weights[i] > 0) {
            int add = (int) Math.round(extra * weights[i] / total);
            sizes[i] = Math.max(0, sizes[i] + add); extra -= add; total -= weights[i];
        }
    }
}
