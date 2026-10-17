package javax.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.event.ChangeListener;

public class JViewport extends JComponent {
    private static final long serialVersionUID = 1L;

    public static final int BLIT_SCROLL_MODE = 1;
    public static final int BACKINGSTORE_SCROLL_MODE = 2;
    public static final int SIMPLE_SCROLL_MODE = 3;

    private Component view;
    private final java.util.List<ChangeListener> changeListeners = new java.util.ArrayList<>();
    private int scrollMode = BLIT_SCROLL_MODE;
    private final Point viewPosition = new Point();

    public JViewport() {}

    public Component getView() { return view; }
    public void setView(Component view) {
        if (this.view != null) super.remove(this.view);
        this.view = view;
        if (view != null) super.add(view);
        if (getWidth() > 0 && getHeight() > 0) doLayout();
    }
    public Dimension getViewSize() { return view == null ? new Dimension() : view.getSize(); }
    public void setViewSize(Dimension newSize) {}
    public Point getViewPosition() { return new Point(viewPosition); }
    public void setViewPosition(Point p) {
        if (p == null) return;
        viewPosition.setLocation(clampX(p.x), clampY(p.y));
        applyScroll();
    }
    public Rectangle getViewRect() {
        return new Rectangle(viewPosition.x, viewPosition.y, getWidth(), getHeight());
    }
    public Dimension getExtentSize() { return getSize(); }

    /** Scroll by (dx, dy) in view-coords, clamped to the view's bounds. */
    public void scrollBy(int dx, int dy) {
        viewPosition.setLocation(clampX(viewPosition.x + dx), clampY(viewPosition.y + dy));
        applyScroll();
    }

    private int clampX(int x) {
        if (view == null) return 0;
        int max = Math.max(0, view.getWidth() - getWidth());
        return Math.max(0, Math.min(x, max));
    }
    private int clampY(int y) {
        if (view == null) return 0;
        int max = Math.max(0, view.getHeight() - getHeight());
        return Math.max(0, Math.min(y, max));
    }

    private void applyScroll() {
        if (view == null) return;
        // The viewport clips its children to its own bounds (see Window.hostPaint),
        // so positioning the view at negative coords slides the content under the
        // viewport without painting outside.
        view.setLocation(-viewPosition.x, -viewPosition.y);
        javax.swing.event.ChangeEvent event = new javax.swing.event.ChangeEvent(this);
        for (ChangeListener listener : new java.util.ArrayList<>(changeListeners)) listener.stateChanged(event);
    }

    @Override
    public void doLayout() {
        if (view != null && getWidth() > 0 && getHeight() > 0) {
            // Size the view to its preferred height (or at least viewport height) so
            // scrollable content can grow; horizontal stays clamped to viewport.
            Dimension pref = view.getPreferredSize();
            int h = Math.max(getHeight(), pref.height);
            boolean wideTable = view instanceof JTable || view instanceof javax.swing.table.JTableHeader;
            view.setSize(wideTable ? Math.max(getWidth(), pref.width) : getWidth(), h);
            // Re-clamp + re-apply scroll in case the view shrunk.
            viewPosition.setLocation(clampX(viewPosition.x), clampY(viewPosition.y));
            applyScroll();
        }
    }
    public void setExtentSize(Dimension newExtent) {}
    public int getScrollMode() { return scrollMode; }
    public void setScrollMode(int mode) { this.scrollMode = mode; }
    public void addChangeListener(ChangeListener l) { if (l != null) changeListeners.add(l); }
    public void removeChangeListener(ChangeListener l) { changeListeners.remove(l); }
    public void scrollRectToVisible(Rectangle contentRect) {}
}
