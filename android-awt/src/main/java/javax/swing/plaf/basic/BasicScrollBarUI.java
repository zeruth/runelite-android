/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
        installDefaults();
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
    /** The look and feel's thumb bounds ("ScrollBar.minimumThumbSize"/"maximumThumbSize"). */
    protected Dimension minimumThumbSize, maximumThumbSize;

    /**
     * The JDK's: the bar width and thumb bounds from the look and feel, with 16 when
     * "ScrollBar.width" is unset, then the colours.
     */
    protected void installDefaults() {
        scrollBarWidth = UIManager.getInt("ScrollBar.width");
        if (scrollBarWidth <= 0) scrollBarWidth = 16;
        Object min = UIManager.get("ScrollBar.minimumThumbSize");
        Object max = UIManager.get("ScrollBar.maximumThumbSize");
        minimumThumbSize = min instanceof Dimension ? (Dimension) min : null;
        maximumThumbSize = max instanceof Dimension ? (Dimension) max : null;
        configureScrollBarColors();
    }
    protected void configureScrollBarColors() {
        trackColor = UIManager.getColor("ScrollBar.track");
        if (trackColor == null) trackColor = scrollbar.getBackground();
        trackHighlightColor = UIManager.getColor("ScrollBar.trackHighlight");
        if (trackHighlightColor == null) trackHighlightColor = trackColor;
        thumbColor = UIManager.getColor("ScrollBar.thumb");
        if (thumbColor == null) thumbColor = Color.GRAY;
        thumbDarkShadowColor = UIManager.getColor("ScrollBar.thumbDarkShadow");
        if (thumbDarkShadowColor == null) thumbDarkShadowColor = Color.DARK_GRAY;
        thumbHighlightColor = UIManager.getColor("ScrollBar.thumbHighlight");
        if (thumbHighlightColor == null) thumbHighlightColor = Color.LIGHT_GRAY;
        thumbLightShadowColor = UIManager.getColor("ScrollBar.thumbShadow");
        if (thumbLightShadowColor == null) thumbLightShadowColor = Color.LIGHT_GRAY;
    }
    protected JButton createDecreaseButton(int orientation) { return new BasicArrowButton(orientation); }
    protected JButton createIncreaseButton(int orientation) { return new BasicArrowButton(orientation); }
    /**
     * The look and feel's minimum, or 8x8: a thumb a finger can still catch when the
     * content is many pages long.
     */
    protected Dimension getMinimumThumbSize() { return minimumThumbSize != null ? minimumThumbSize : new Dimension(8, 8); }
    protected Dimension getMaximumThumbSize() {
        return maximumThumbSize != null ? maximumThumbSize : new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
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
    /** The JDK's: a scrollbar is as wide as its look and feel says and unbounded along its axis. */
    @Override public Dimension getMaximumSize(JComponent component) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
    /** Whether a mouse gesture (usually the middle button) can place the thumb; not in Basic, as the JDK's. */
    public boolean getSupportsAbsolutePositioning() { return false; }

    /** No part of the track is highlighted. */
    protected static final int NO_HIGHLIGHT = 0;
    /** The track before the thumb is highlighted while it is held. */
    protected static final int DECREASE_HIGHLIGHT = 1;
    /** The track after the thumb is highlighted while it is held. */
    protected static final int INCREASE_HIGHLIGHT = 2;
    /** Which part of the track paintTrack highlights. */
    protected int trackHighlight = NO_HIGHLIGHT;

    /**
     * Moves the thumb and repaints the area it left and entered, as the JDK's; layoutContainer
     * keeps setting the bounds directly, since it also runs from paint.
     */
    protected void setThumbBounds(int x, int y, int width, int height) {
        if (thumbRect.x == x && thumbRect.y == y && thumbRect.width == width && thumbRect.height == height) return;
        int minX = Math.min(x, thumbRect.x), minY = Math.min(y, thumbRect.y);
        int maxX = Math.max(x + width, thumbRect.x + thumbRect.width);
        int maxY = Math.max(y + height, thumbRect.y + thumbRect.height);
        thumbRect.setBounds(x, y, width, height);
        scrollbar.repaint(minX, minY, maxX - minX, maxY - minY);
        setThumbRollover(false);
    }
    /** One block towards {@code direction} (negative: decrease), as a held track does. */
    protected void scrollByBlock(int direction) { step(direction < 0 ? -1 : 1, true); }
    /** One unit towards {@code direction} (negative: decrease), as an arrow button does. */
    protected void scrollByUnit(int direction) { step(direction < 0 ? -1 : 1, false); }

    /**
     * The JDK's paint: the track, then the thumb where it meets the clip. The layout runs
     * first because the host paints scrollbars it never validated.
     */
    @Override public void paint(Graphics g, JComponent component) {
        layoutContainer(scrollbar);
        paintTrack(g, component, getTrackBounds());
        Rectangle thumbBounds = getThumbBounds();
        Rectangle clip = g.getClipBounds();
        if (clip == null || thumbBounds.intersects(clip)) {
            paintThumb(g, component, thumbBounds);
        }
    }
    protected void paintTrack(Graphics g, JComponent component, Rectangle trackBounds) {
        g.setColor(trackColor);
        g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        if (trackHighlight == DECREASE_HIGHLIGHT) {
            paintDecreaseHighlight(g);
        } else if (trackHighlight == INCREASE_HIGHLIGHT) {
            paintIncreaseHighlight(g);
        }
    }
    /** The JDK's raised thumb: a dark outline over the fill, highlight top-left, shadow bottom-right. */
    protected void paintThumb(Graphics g, JComponent component, Rectangle thumbBounds) {
        if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
        int w = thumbBounds.width, h = thumbBounds.height;
        g.translate(thumbBounds.x, thumbBounds.y);
        g.setColor(thumbDarkShadowColor);
        g.drawRect(0, 0, w - 1, h - 1);
        g.setColor(thumbColor);
        g.fillRect(0, 0, w - 1, h - 1);
        g.setColor(thumbHighlightColor);
        g.drawLine(1, 1, 1, h - 2);
        g.drawLine(2, 1, w - 3, 1);
        g.setColor(thumbLightShadowColor);
        g.drawLine(2, h - 2, w - 2, h - 2);
        g.drawLine(w - 2, 1, w - 2, h - 3);
        g.translate(-thumbBounds.x, -thumbBounds.y);
    }
    /** The track between its start and the thumb, in the track highlight colour. */
    protected void paintDecreaseHighlight(Graphics g) {
        Insets insets = scrollbar.getInsets();
        Rectangle thumbR = getThumbBounds();
        g.setColor(trackHighlightColor);
        if (vertical()) {
            int x = insets.left, y = trackRect.y;
            g.fillRect(x, y, scrollbar.getWidth() - (insets.left + insets.right), thumbR.y - y);
        } else {
            int x, w;
            if (scrollbar.getComponentOrientation().isLeftToRight()) {
                x = trackRect.x;
                w = thumbR.x - x;
            } else {
                x = thumbR.x + thumbR.width;
                w = trackRect.x + trackRect.width - x;
            }
            g.fillRect(x, insets.top, w, scrollbar.getHeight() - (insets.top + insets.bottom));
        }
    }
    /** The track between the thumb and its end, in the track highlight colour. */
    protected void paintIncreaseHighlight(Graphics g) {
        Insets insets = scrollbar.getInsets();
        Rectangle thumbR = getThumbBounds();
        g.setColor(trackHighlightColor);
        if (vertical()) {
            int x = insets.left, y = thumbR.y + thumbR.height;
            g.fillRect(x, y, scrollbar.getWidth() - (insets.left + insets.right), trackRect.y + trackRect.height - y);
        } else {
            int x, w;
            if (scrollbar.getComponentOrientation().isLeftToRight()) {
                x = thumbR.x + thumbR.width;
                w = trackRect.x + trackRect.width - x;
            } else {
                x = trackRect.x;
                w = thumbR.x - x;
            }
            g.fillRect(x, insets.top, w, scrollbar.getHeight() - (insets.top + insets.bottom));
        }
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
                int direction = axis(event) < start(thumbRect) ? -1 : 1;
                trackHighlight = direction > 0 ? INCREASE_HIGHLIGHT : DECREASE_HIGHLIGHT;
                scrollByBlock(direction);
                scrollbar.repaint(trackRect.x, trackRect.y, trackRect.width, trackRect.height);
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
            if (trackHighlight != NO_HIGHLIGHT) {
                trackHighlight = NO_HIGHLIGHT;
                scrollbar.repaint(trackRect.x, trackRect.y, trackRect.width, trackRect.height);
            }
        }
        @Override public void mouseMoved(MouseEvent event) { setThumbRollover(thumbRect.contains(event.getPoint())); }
        @Override public void mouseExited(MouseEvent event) { setThumbRollover(false); }
    };
}
