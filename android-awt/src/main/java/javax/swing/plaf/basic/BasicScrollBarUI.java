package javax.swing.plaf.basic;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ScrollBarUI;

/** Pluggable scrollbar for panels that supply their own track, thumb or buttons. */
public class BasicScrollBarUI extends ScrollBarUI implements LayoutManager, SwingConstants {
    protected JScrollBar scrollbar;
    protected JButton incrButton, decrButton;
    protected Rectangle trackRect = new Rectangle(), thumbRect = new Rectangle();
    protected Color trackColor, trackHighlightColor, thumbColor, thumbDarkShadowColor, thumbHighlightColor, thumbLightShadowColor;
    protected int scrollBarWidth = 16;
    protected boolean isDragging;
    private boolean rollover;
    private int dragOffset;
    private LayoutManager previousLayout;
    private final AdjustmentListener adjusted = event -> scrollbar.repaint();
    private final ActionListener decrement = event -> step(-1, false);
    private final ActionListener increment = event -> step(1, false);

    public static ComponentUI createUI(JComponent component) { return new BasicScrollBarUI(); }
    @Override public void installUI(JComponent component) {
        scrollbar = (JScrollBar) component;
        previousLayout = scrollbar.getLayout();
        scrollbar.setLayout(this);
        configureScrollBarColors();
        decrButton = createDecreaseButton(vertical() ? NORTH : WEST);
        incrButton = createIncreaseButton(vertical() ? SOUTH : EAST);
        scrollbar.add(decrButton);
        scrollbar.add(incrButton);
        installListeners();
    }
    protected void installListeners() {
        decrButton.addActionListener(decrement);
        incrButton.addActionListener(increment);
        scrollbar.addMouseListener(trackMouse);
        scrollbar.addMouseMotionListener(trackMouse);
        scrollbar.addAdjustmentListener(adjusted);
    }
    @Override public void uninstallUI(JComponent component) {
        uninstallListeners();
        scrollbar.remove(decrButton);
        scrollbar.remove(incrButton);
        if (scrollbar.getLayout() == this) scrollbar.setLayout(previousLayout);
        if (isDragging) scrollbar.setValueIsAdjusting(false);
        isDragging = rollover = false;
        scrollbar = null;
        incrButton = decrButton = null;
    }
    protected void uninstallListeners() {
        scrollbar.removeAdjustmentListener(adjusted);
        scrollbar.removeMouseListener(trackMouse);
        scrollbar.removeMouseMotionListener(trackMouse);
        decrButton.removeActionListener(decrement);
        incrButton.removeActionListener(increment);
    }
    protected void configureScrollBarColors() {
        trackColor = scrollbar.getBackground();
        trackHighlightColor = trackColor;
        thumbColor = Color.GRAY;
        thumbDarkShadowColor = Color.DARK_GRAY;
        thumbHighlightColor = thumbLightShadowColor = Color.LIGHT_GRAY;
    }
    protected JButton createDecreaseButton(int orientation) { return new BasicArrowButton(orientation); }
    protected JButton createIncreaseButton(int orientation) { return new BasicArrowButton(orientation); }
    protected Dimension getMinimumThumbSize() { return new Dimension(8, 8); }
    protected Dimension getMaximumThumbSize() { return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
    protected Rectangle getThumbBounds() { return thumbRect; }
    protected Rectangle getTrackBounds() { return trackRect; }
    public boolean isThumbRollover() { return rollover; }
    protected void setThumbRollover(boolean value) {
        if (rollover != value) { rollover = value; scrollbar.repaint(); }
    }
    private boolean vertical() { return scrollbar.getOrientation() == VERTICAL; }
    private int axis(Dimension size) { return vertical() ? size.height : size.width; }
    private int axis(MouseEvent event) { return vertical() ? event.getY() : event.getX(); }
    private int start(Rectangle bounds) { return vertical() ? bounds.y : bounds.x; }
    private int length(Rectangle bounds) { return vertical() ? bounds.height : bounds.width; }
    private void step(int direction, boolean block) {
        if (!scrollbar.isEnabled()) return;
        int delta = block ? scrollbar.getBlockIncrement(direction) : scrollbar.getUnitIncrement(direction);
        scrollbar.setValue(scrollbar.getValue() + direction * delta);
    }
    @Override public Dimension getPreferredSize(JComponent component) {
        return vertical() ? new Dimension(scrollBarWidth, 48) : new Dimension(48, scrollBarWidth);
    }
    @Override public Dimension preferredLayoutSize(Container parent) { return getPreferredSize(scrollbar); }
    @Override public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }
    @Override public void addLayoutComponent(String name, Component child) {}
    @Override public void removeLayoutComponent(Component child) {}
    @Override public void layoutContainer(Container parent) {
        Insets in = scrollbar.getInsets();
        int w = Math.max(0, scrollbar.getWidth() - in.left - in.right);
        int h = Math.max(0, scrollbar.getHeight() - in.top - in.bottom);
        int size = vertical() ? h : w;
        int dec = Math.min(size / 2, axis(decrButton.getPreferredSize()));
        int inc = Math.min(size / 2, axis(incrButton.getPreferredSize()));
        if (vertical()) {
            decrButton.setBounds(in.left, in.top, w, dec);
            incrButton.setBounds(in.left, in.top + h - inc, w, inc);
            trackRect.setBounds(in.left, in.top + dec, w, h - dec - inc);
        } else {
            decrButton.setBounds(in.left, in.top, dec, h);
            incrButton.setBounds(in.left + w - inc, in.top, inc, h);
            trackRect.setBounds(in.left + dec, in.top, w - dec - inc, h);
        }
        updateThumb();
    }
    private void updateThumb() {
        double range = (double) scrollbar.getMaximum() - scrollbar.getMinimum();
        int extent = scrollbar.getVisibleAmount(), size = length(trackRect);
        if (size <= 0 || range <= extent) { thumbRect.setBounds(0, 0, 0, 0); return; }
        int thumb = (int) Math.round(size * extent / range);
        thumb = Math.min(size, Math.min(axis(getMaximumThumbSize()), Math.max(axis(getMinimumThumbSize()), thumb)));
        int offset = (int) Math.round((size - thumb) * ((double) scrollbar.getValue() - scrollbar.getMinimum()) / (range - extent));
        if (vertical()) thumbRect.setBounds(trackRect.x, trackRect.y + offset, trackRect.width, thumb);
        else thumbRect.setBounds(trackRect.x + offset, trackRect.y, thumb, trackRect.height);
    }
    @Override public void paint(Graphics g, JComponent component) {
        layoutContainer(scrollbar);
        paintTrack(g, component, trackRect);
        if (!thumbRect.isEmpty()) paintThumb(g, component, thumbRect);
    }
    protected void paintTrack(Graphics g, JComponent component, Rectangle bounds) {
        g.setColor(trackColor); g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
    protected void paintThumb(Graphics g, JComponent component, Rectangle bounds) {
        g.setColor(thumbColor); g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
    private final MouseAdapter trackMouse = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent event) {
            if (!scrollbar.isEnabled() || event.getButton() != MouseEvent.BUTTON1) return;
            layoutContainer(scrollbar);
            if (thumbRect.contains(event.getPoint())) {
                isDragging = true;
                dragOffset = axis(event) - start(thumbRect);
                scrollbar.setValueIsAdjusting(true);
            } else if (trackRect.contains(event.getPoint()) && !thumbRect.isEmpty()) {
                step(axis(event) < start(thumbRect) ? -1 : 1, true);
            }
        }
        @Override public void mouseDragged(MouseEvent event) {
            if (!isDragging) return;
            int travel = length(trackRect) - length(thumbRect);
            if (travel <= 0) return;
            int offset = Math.max(0, Math.min(travel, axis(event) - start(trackRect) - dragOffset));
            double range = (double) scrollbar.getMaximum() - scrollbar.getVisibleAmount() - scrollbar.getMinimum();
            scrollbar.setValue((int) Math.round(scrollbar.getMinimum() + range * offset / travel));
        }
        @Override public void mouseReleased(MouseEvent event) {
            if (isDragging) { isDragging = false; scrollbar.setValueIsAdjusting(false); }
        }
        @Override public void mouseMoved(MouseEvent event) { setThumbRollover(thumbRect.contains(event.getPoint())); }
        @Override public void mouseExited(MouseEvent event) { setThumbRollover(false); }
    };
}
