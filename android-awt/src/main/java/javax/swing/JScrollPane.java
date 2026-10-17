package javax.swing;

import java.awt.Component;
import javax.swing.border.Border;

public class JScrollPane extends JComponent implements ScrollPaneConstants {
    private static final long serialVersionUID = 1L;

    protected JViewport viewport = new JViewport();
    protected JScrollBar verticalScrollBar = new JScrollBar(JScrollBar.VERTICAL);
    protected JScrollBar horizontalScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
    protected int verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED;
    protected int horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED;
    private Border viewportBorder;
    private JViewport columnHeader;

    public JScrollPane() { init(); }
    public JScrollPane(Component view) { init(); setViewportView(view); }
    public JScrollPane(int vsbPolicy, int hsbPolicy) {
        this.verticalScrollBarPolicy = vsbPolicy;
        this.horizontalScrollBarPolicy = hsbPolicy;
        init();
    }
    public JScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
        this(vsbPolicy, hsbPolicy);
        setViewportView(view);
    }

    private void init() {
        setOpaque(true);
        super.add(viewport);
        viewport.addChangeListener(event -> {
            if (columnHeader != null) columnHeader.setViewPosition(new java.awt.Point(viewport.getViewPosition().x, 0));
        });
        // Real Swing installs a MouseWheelListener that maps wheel rotations into
        // viewport scroll offsets. Mirror that here so dispatching a MOUSE_WHEEL
        // event on the scrollpane (or anywhere in its subtree, via the walk-up in
        // Compose's input bridge) actually scrolls the content.
        addMouseWheelListener(e -> {
            if (e == null || viewport == null) return;
            // Use the view's preferred row height (≈ a JLabel line) as the unit.
            int unit = Math.max(16, getFont() == null ? 16 : getFontMetrics(getFont()).getHeight());
            int amount = e.getWheelRotation() * unit * 3;
            viewport.scrollBy(0, amount);
        });
    }

    @Override
    public void doLayout() {
        if (viewport != null && getWidth() > 0 && getHeight() > 0) {
            int headerHeight = columnHeader == null || columnHeader.getView() == null ? 0
                : Math.min(getHeight(), columnHeader.getView().getPreferredSize().height);
            if (columnHeader != null) columnHeader.setBounds(0, 0, getWidth(), headerHeight);
            viewport.setBounds(0, headerHeight, getWidth(), Math.max(0, getHeight() - headerHeight));
        }
    }

    public JViewport getViewport() { return viewport; }
    public void setViewport(JViewport viewport) { this.viewport = viewport; }
    public void setViewportView(Component view) {
        viewport.setView(view);
        if (view instanceof JTable) {
            JViewport header = new JViewport();
            header.setView(((JTable) view).getTableHeader());
            setColumnHeader(header);
        } else setColumnHeader(null);
    }
    public JScrollBar getVerticalScrollBar() { return verticalScrollBar; }
    public void setVerticalScrollBar(JScrollBar sb) { this.verticalScrollBar = sb; }
    public JScrollBar getHorizontalScrollBar() { return horizontalScrollBar; }
    public void setHorizontalScrollBar(JScrollBar sb) { this.horizontalScrollBar = sb; }
    public int getVerticalScrollBarPolicy() { return verticalScrollBarPolicy; }
    public void setVerticalScrollBarPolicy(int policy) { this.verticalScrollBarPolicy = policy; }
    public int getHorizontalScrollBarPolicy() { return horizontalScrollBarPolicy; }
    public void setHorizontalScrollBarPolicy(int policy) { this.horizontalScrollBarPolicy = policy; }
    public Border getViewportBorder() { return viewportBorder; }
    public void setViewportBorder(Border viewportBorder) { this.viewportBorder = viewportBorder; }
    public JViewport getRowHeader() { return null; }
    public void setRowHeader(JViewport rowHeader) {}
    public JViewport getColumnHeader() { return columnHeader; }
    public void setColumnHeader(JViewport header) {
        if (columnHeader != null) remove(columnHeader);
        columnHeader = header;
        if (header != null) add(header);
        revalidate(); repaint();
    }
    public Component getCorner(String key) { return null; }
    public void setCorner(String key, Component corner) {}
    public void setWheelScrollingEnabled(boolean enable) {}
    public boolean isWheelScrollingEnabled() { return true; }
}
