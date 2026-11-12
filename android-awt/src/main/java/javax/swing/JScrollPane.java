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
package javax.swing;

import java.awt.Component;
import java.awt.Container;
import javax.swing.border.Border;

public class JScrollPane extends JComponent implements ScrollPaneConstants {
    private static final long serialVersionUID = 1L;

    protected JViewport viewport;
    protected JScrollBar verticalScrollBar;
    protected JScrollBar horizontalScrollBar;
    protected int verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED;
    protected int horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED;
    private Border viewportBorder;
    private JViewport columnHeader;
    private JViewport rowHeader;
    private boolean wheelScrollingEnabled=true;
    private final java.util.Map<String,Component> corners=new java.util.HashMap<>();
    private final java.beans.PropertyChangeListener barUIChanged = event -> {
        JScrollBar bar = (JScrollBar) event.getSource();
        if (bar.getUI() != null && bar.getParent() != this) add(bar, barKey(bar));
        else if (bar.getUI() == null && bar.getParent() == this) remove(bar);
        revalidate(); repaint();
    };

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

    /**
     * Swing's ScrollPane scrollbar: its unit/block increments come from a Scrollable view
     * (or the viewport extent for blocks) until set explicitly.
     */
    protected class ScrollBar extends JScrollBar implements javax.swing.plaf.UIResource {
        private static final long serialVersionUID = 1L;
        private boolean unitIncrementSet;
        private boolean blockIncrementSet;

        public ScrollBar(int orientation) { super(orientation); }

        @Override public void setUnitIncrement(int unitIncrement) {
            unitIncrementSet = true;
            super.setUnitIncrement(unitIncrement);
        }

        @Override public int getUnitIncrement(int direction) {
            JViewport vp = getViewport();
            if (!unitIncrementSet && vp != null && vp.getView() instanceof Scrollable) {
                return ((Scrollable) vp.getView()).getScrollableUnitIncrement(vp.getViewRect(), getOrientation(), direction);
            }
            return super.getUnitIncrement(direction);
        }

        @Override public void setBlockIncrement(int blockIncrement) {
            blockIncrementSet = true;
            super.setBlockIncrement(blockIncrement);
        }

        @Override public int getBlockIncrement(int direction) {
            JViewport vp = getViewport();
            if (blockIncrementSet || vp == null) return super.getBlockIncrement(direction);
            if (vp.getView() instanceof Scrollable) {
                return ((Scrollable) vp.getView()).getScrollableBlockIncrement(vp.getViewRect(), getOrientation(), direction);
            }
            return getOrientation() == VERTICAL ? vp.getExtentSize().height : vp.getExtentSize().width;
        }

        /**
         * On the desktop a revalidate is queued on the EDT ahead of the plugin's own
         * invokeLater, so `invokeLater(() -> bar.setValue(saved))` after rebuilding the
         * content sees the new range. Our layout pump runs on its own schedule, so lay the
         * pane out first (it is a validate root in Swing too) or the value clamps to the
         * stale range — quest-helper restores its scroll offset exactly this way.
         */
        @Override public void setValue(int value) {
            if (!JScrollPane.this.isValid() && JScrollPane.this.getWidth() > 0 && JScrollPane.this.getHeight() > 0) {
                try { JScrollPane.this.validate(); } catch (Throwable ignored) {}
            }
            super.setValue(value);
        }
    }

    public JScrollBar createVerticalScrollBar() { return new ScrollBar(JScrollBar.VERTICAL); }
    public JScrollBar createHorizontalScrollBar() { return new ScrollBar(JScrollBar.HORIZONTAL); }
    protected JViewport createViewport() { return new JViewport(); }

    // Viewport -> scrollbars (BasicScrollPaneUI.syncScrollPaneWithViewport) and back.
    // `syncing` stops the model events we raise from steering the viewport again.
    private boolean syncing;
    private final javax.swing.event.ChangeListener viewportListener = event -> {
        JViewport vp = viewport;
        if (vp == null) return;
        if (columnHeader != null) columnHeader.setViewPosition(new java.awt.Point(vp.getViewPosition().x, 0));
        if(rowHeader!=null)rowHeader.setViewPosition(new java.awt.Point(0,vp.getViewPosition().y));
        syncScrollBarsWithViewport();
    };
    private final java.awt.event.AdjustmentListener verticalListener = e -> {
        JViewport vp = viewport;
        if (syncing || vp == null || verticalScrollBar == null) return;
        java.awt.Point p = vp.getViewPosition();
        p.y = verticalScrollBar.getValue();
        vp.setViewPosition(p);
    };
    private final java.awt.event.AdjustmentListener horizontalListener = e -> {
        JViewport vp = viewport;
        if (syncing || vp == null || horizontalScrollBar == null) return;
        java.awt.Point p = vp.getViewPosition();
        p.x = horizontalScrollBar.getValue();
        vp.setViewPosition(p);
    };

    private void syncScrollBarsWithViewport() {
        JViewport vp = viewport;
        if (vp == null || syncing) return;
        syncing = true;
        try {
            java.awt.Dimension extent = vp.getExtentSize();
            java.awt.Dimension viewSize = vp.getViewSize();
            java.awt.Point pos = vp.getViewPosition();
            if (verticalScrollBar != null) {
                int ext = extent.height, max = viewSize.height;
                verticalScrollBar.setValues(Math.max(0, Math.min(pos.y, max - ext)), ext, 0, max);
            }
            if (horizontalScrollBar != null) {
                int ext = extent.width, max = viewSize.width;
                horizontalScrollBar.setValues(Math.max(0, Math.min(pos.x, max - ext)), ext, 0, max);
            }
        } finally {
            syncing = false;
        }
    }

    private void init() {
        // As in the JDK the layout comes first, so the viewport, bars and corners
        // added below reach it through addLayoutComponent.
        setLayout(new HostLayout());
        setOpaque(true);
        setViewport(createViewport());
        setVerticalScrollBar(createVerticalScrollBar());
        setHorizontalScrollBar(createHorizontalScrollBar());
        // Real Swing installs a MouseWheelListener that maps wheel rotations into
        // viewport scroll offsets. Mirror that here so dispatching a MOUSE_WHEEL
        // event on the scrollpane (or anywhere in its subtree, via the walk-up in
        // Compose's input bridge) actually scrolls the content.
        addMouseWheelListener(e -> {
            if (e == null || viewport == null || !wheelScrollingEnabled) return;
            // Use the view's preferred row height (≈ a JLabel line) as the unit, or the
            // scrollbar's increment when the pane asks for more (RuneLite sets 16+).
            int unit = Math.max(16, getFont() == null ? 16 : getFontMetrics(getFont()).getHeight());
            if (verticalScrollBar != null) {
                try { unit = Math.max(unit, verticalScrollBar.getUnitIncrement(e.getWheelRotation() < 0 ? -1 : 1)); }
                catch (Throwable ignored) {}
            }
            int amount = e.getWheelRotation() * unit * 3;
            viewport.scrollBy(0, amount);
        });
    }

    /**
     * Sets the layout manager, which must be a {@code ScrollPaneLayout} (or null);
     * it is synced with this pane's viewport, scrollbars, headers and corners.
     * <p>
     * A new pane starts, as in the JDK, with a {@code ScrollPaneLayout.UIResource}.
     * While a scrollbar has no UI delegate that default layout leaves scrolling
     * gestures and indicators to the native host and only reserves room for the
     * bars that do carry one; once both bars have a delegate it is the JDK layout.
     * A plain {@code ScrollPaneLayout} set here lays the pane out exactly as on
     * the desktop.
     *
     * @param layout the {@code ScrollPaneLayout}, or null
     * @throws ClassCastException if {@code layout} is not a {@code ScrollPaneLayout}
     */
    @Override
    public void setLayout(java.awt.LayoutManager layout) {
        if (layout instanceof ScrollPaneLayout) {
            super.setLayout(layout);
            ((ScrollPaneLayout) layout).syncWithScrollPane(this);
        } else if (layout == null) {
            super.setLayout(layout);
        } else {
            String s = "layout of JScrollPane must be a ScrollPaneLayout";
            throw new ClassCastException(s);
        }
    }

    /**
     * Returns the bounds of the viewport's border, the viewport's bounds grown by
     * the viewport border insets.
     *
     * @return a {@code Rectangle} object specifying the viewport border
     */
    public java.awt.Rectangle getViewportBorderBounds() {
        java.awt.Rectangle borderR = new java.awt.Rectangle(getSize());
        java.awt.Insets insets = getInsets();
        borderR.x = insets.left;
        borderR.y = insets.top;
        borderR.width -= insets.left + insets.right;
        borderR.height -= insets.top + insets.bottom;
        if (columnHeader != null && columnHeader.isVisible()) {
            int colHeadHeight = columnHeader.getHeight();
            borderR.y += colHeadHeight;
            borderR.height -= colHeadHeight;
        }
        if (rowHeader != null && rowHeader.isVisible()) {
            int rowHeadWidth = rowHeader.getWidth();
            borderR.x += rowHeadWidth;
            borderR.width -= rowHeadWidth;
        }
        if (verticalScrollBar != null && verticalScrollBar.isVisible()) {
            borderR.width -= verticalScrollBar.getWidth();
        }
        if (horizontalScrollBar != null && horizontalScrollBar.isVisible()) {
            borderR.height -= horizontalScrollBar.getHeight();
        }
        return borderR;
    }

    /**
     * The default layout: a real ScrollPaneLayout (plugins cast {@code getLayout()} to
     * one), whose placement is the host's while a scrollbar has no UI delegate. A
     * delegate-less bar is never a child here and draws nothing, so the JDK placement
     * would reserve an empty strip for it beside the native scroll indicator.
     */
    private static final class HostLayout extends ScrollPaneLayout.UIResource {
        private static final long serialVersionUID = 1L;

        private static boolean host(Container parent) {
            if (!(parent instanceof JScrollPane)) return false;
            JScrollPane sp = (JScrollPane) parent;
            return sp.verticalScrollBar != null && sp.verticalScrollBar.getUI() == null
                || sp.horizontalScrollBar != null && sp.horizontalScrollBar.getUI() == null;
        }

        @Override public void layoutContainer(Container parent) {
            if (host(parent)) ((JScrollPane) parent).layoutHost();
            else super.layoutContainer(parent);
        }

        @Override public java.awt.Dimension preferredLayoutSize(Container parent) {
            return host(parent) ? ((JScrollPane) parent).hostPreferredSize() : super.preferredLayoutSize(parent);
        }

        @Override public java.awt.Dimension minimumLayoutSize(Container parent) {
            return host(parent) ? ((JScrollPane) parent).hostMinimumSize() : super.minimumLayoutSize(parent);
        }
    }

    private void layoutHost() {
        if(viewport==null)return;
        java.awt.Insets in=getInsets();int width=Math.max(0,getWidth()-in.left-in.right),height=Math.max(0,getHeight()-in.top-in.bottom);
        int headerHeight=columnHeader==null||columnHeader.getView()==null?0:Math.min(height,columnHeader.getView().getPreferredSize().height);
        int headerWidth=rowHeader==null||rowHeader.getView()==null?0:Math.min(width,rowHeader.getView().getPreferredSize().width);
        // Native hosts supply the scrolling gesture/indicator. Reserve explicit corner
        // slots without adding a second scrollbar over the default native viewport.
        int right=cornerExtent(true),bottom=cornerExtent(false);
        if(verticalScrollBarPolicy==VERTICAL_SCROLLBAR_NEVER)right=0;
        if(horizontalScrollBarPolicy==HORIZONTAL_SCROLLBAR_NEVER)bottom=0;
        boolean vCustom = verticalScrollBar != null && verticalScrollBar.getUI() != null;
        boolean hCustom = horizontalScrollBar != null && horizontalScrollBar.getUI() != null;
        boolean showV = false, showH = false;
        if (vCustom || hCustom) {
            Component view = viewport.getView();
            java.awt.Dimension content = view == null ? new java.awt.Dimension() : view.getPreferredSize();
            boolean scrollY = !(view instanceof Scrollable) || !((Scrollable) view).getScrollableTracksViewportHeight();
            boolean scrollX = !(view instanceof Scrollable) || !((Scrollable) view).getScrollableTracksViewportWidth();
            // Reserving one bar can make the opposite axis overflow.
            for (int pass = 0; pass < 2; pass++) {
                showV = vCustom && verticalScrollBarPolicy != VERTICAL_SCROLLBAR_NEVER &&
                    (verticalScrollBarPolicy == VERTICAL_SCROLLBAR_ALWAYS || scrollY && content.height > height - headerHeight - bottom);
                if (showV) right = Math.max(right, verticalScrollBar.getPreferredSize().width);
                showH = hCustom && horizontalScrollBarPolicy != HORIZONTAL_SCROLLBAR_NEVER &&
                    (horizontalScrollBarPolicy == HORIZONTAL_SCROLLBAR_ALWAYS || scrollX && content.width > width - headerWidth - right);
                if (showH) bottom = Math.max(bottom, horizontalScrollBar.getPreferredSize().height);
            }
        }
        right=Math.min(right,Math.max(0,width-headerWidth));bottom=Math.min(bottom,Math.max(0,height-headerHeight));
        int viewWidth=Math.max(0,width-headerWidth-right),viewHeight=Math.max(0,height-headerHeight-bottom);
        if(columnHeader!=null)columnHeader.setBounds(in.left+headerWidth,in.top,viewWidth,headerHeight);
        if(rowHeader!=null)rowHeader.setBounds(in.left,in.top+headerHeight,headerWidth,viewHeight);
        viewport.setBounds(in.left+headerWidth,in.top+headerHeight,viewWidth,viewHeight);
        if (vCustom) {
            verticalScrollBar.setVisible(showV);
            if (showV) verticalScrollBar.setBounds(in.left + width - right, in.top + headerHeight, right, viewHeight);
        }
        if (hCustom) {
            horizontalScrollBar.setVisible(showH);
            if (showH) horizontalScrollBar.setBounds(in.left + headerWidth, in.top + height - bottom, viewWidth, bottom);
        }
        layoutCorner(UPPER_LEFT_CORNER,in.left,in.top,headerWidth,headerHeight);
        layoutCorner(UPPER_RIGHT_CORNER,in.left+width-right,in.top,right,headerHeight);
        layoutCorner(LOWER_LEFT_CORNER,in.left,in.top+height-bottom,headerWidth,bottom);
        layoutCorner(LOWER_RIGHT_CORNER,in.left+width-right,in.top+height-bottom,right,bottom);
    }
    private int cornerExtent(boolean vertical){
        Component first=corners.get(vertical?UPPER_RIGHT_CORNER:LOWER_LEFT_CORNER),second=corners.get(LOWER_RIGHT_CORNER);
        if(first==null&&second==null)return 0;
        int result=16;
        for(Component component:new Component[]{first,second})if(component!=null){java.awt.Dimension size=component.getPreferredSize();result=Math.max(result,vertical?size.width:size.height);}
        return result;
    }
    private void layoutCorner(String key,int x,int y,int width,int height){Component component=corners.get(key);if(component!=null)component.setBounds(x,y,width,height);}

    /** ScrollPaneLayout's sizing: insets + the viewport's preferred size + the column header. */
    private java.awt.Dimension hostPreferredSize() {
        java.awt.Insets in = getInsets();
        java.awt.Dimension d = viewport == null ? new java.awt.Dimension() : viewport.getPreferredSize();
        int w = d.width, h = d.height;
        if (columnHeader != null && columnHeader.getView() != null) {
            java.awt.Dimension hd = columnHeader.getView().getPreferredSize();
            h += hd.height;
            w = Math.max(w, hd.width);
        }
        if(rowHeader!=null&&rowHeader.getView()!=null){java.awt.Dimension rd=rowHeader.getView().getPreferredSize();w+=rd.width;h=Math.max(h,rd.height);}
        if(verticalScrollBarPolicy!=VERTICAL_SCROLLBAR_NEVER)w+=cornerExtent(true);
        if(horizontalScrollBarPolicy!=HORIZONTAL_SCROLLBAR_NEVER)h+=cornerExtent(false);
        return new java.awt.Dimension(w + in.left + in.right, h + in.top + in.bottom);
    }

    private java.awt.Dimension hostMinimumSize() {
        java.awt.Insets in = getInsets();
        java.awt.Dimension d = viewport == null ? new java.awt.Dimension() : viewport.getMinimumSize();
        return new java.awt.Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
    }

    public JViewport getViewport() { return viewport; }
    public void setViewport(JViewport viewport) {
        JViewport old = this.viewport;
        if (old == viewport) return;
        if (old != null) {
            old.removeChangeListener(viewportListener);
            remove(old);
        }
        this.viewport = viewport;
        if (viewport != null) {
            add(viewport, VIEWPORT, 0);
            viewport.addChangeListener(viewportListener);
        }
        syncScrollBarsWithViewport();
        revalidate();
    }
    public void setViewportView(Component view) {
        if (viewport == null) setViewport(createViewport());
        viewport.setView(view);
        if (view instanceof JTable) {
            JViewport header = new JViewport();
            header.setView(((JTable) view).getTableHeader());
            setColumnHeader(header);
        } else setColumnHeader(null);
    }
    public JScrollBar getVerticalScrollBar() { return verticalScrollBar; }
    private void detachBar(JScrollBar bar) {
        if (bar == null) return;
        bar.removePropertyChangeListener("UI", barUIChanged);
        if (bar.getParent() == this) remove(bar);
        // A bar without a UI is never a child, but a synced ScrollPaneLayout still holds it.
        else if (getLayout() != null) getLayout().removeLayoutComponent(bar);
    }
    private void attachBar(JScrollBar bar) {
        if (bar == null) return;
        bar.addPropertyChangeListener("UI", barUIChanged);
        if (bar.getUI() != null) add(bar, barKey(bar));
    }
    private String barKey(JScrollBar bar) {
        return bar == verticalScrollBar ? VERTICAL_SCROLLBAR : HORIZONTAL_SCROLLBAR;
    }
    public void setVerticalScrollBar(JScrollBar sb) {
        if (verticalScrollBar == sb) return;
        if (verticalScrollBar != null) verticalScrollBar.removeAdjustmentListener(verticalListener);
        detachBar(verticalScrollBar);
        verticalScrollBar = sb;
        attachBar(sb);
        if (sb != null) sb.addAdjustmentListener(verticalListener);
        syncScrollBarsWithViewport();
    }
    public JScrollBar getHorizontalScrollBar() { return horizontalScrollBar; }
    public void setHorizontalScrollBar(JScrollBar sb) {
        if (horizontalScrollBar == sb) return;
        if (horizontalScrollBar != null) horizontalScrollBar.removeAdjustmentListener(horizontalListener);
        detachBar(horizontalScrollBar);
        horizontalScrollBar = sb;
        attachBar(sb);
        if (sb != null) sb.addAdjustmentListener(horizontalListener);
        syncScrollBarsWithViewport();
    }
    public int getVerticalScrollBarPolicy() { return verticalScrollBarPolicy; }
    public void setVerticalScrollBarPolicy(int policy) { this.verticalScrollBarPolicy = policy; }
    public int getHorizontalScrollBarPolicy() { return horizontalScrollBarPolicy; }
    public void setHorizontalScrollBarPolicy(int policy) { this.horizontalScrollBarPolicy = policy; }
    public Border getViewportBorder() { return viewportBorder; }
    public void setViewportBorder(Border viewportBorder) { this.viewportBorder = viewportBorder; }
    public JViewport getRowHeader() { return rowHeader; }
    public void setRowHeader(JViewport header) { JViewport old=rowHeader;if(old!=null)remove(old);rowHeader=header;if(header!=null)add(header,ROW_HEADER);firePropertyChange("rowHeader",old,header);revalidate();repaint(); }
    public void setRowHeaderView(Component view) { if(rowHeader==null)setRowHeader(new JViewport());rowHeader.setView(view);revalidate(); }
    public void setColumnHeaderView(Component view) {
        if (columnHeader == null) setColumnHeader(new JViewport());
        columnHeader.setView(view); revalidate(); repaint();
    }
    public JViewport getColumnHeader() { return columnHeader; }
    public void setColumnHeader(JViewport header) {
        if (columnHeader != null) remove(columnHeader);
        columnHeader = header;
        if (header != null) add(header, COLUMN_HEADER);
        revalidate(); repaint();
    }
    private String cornerKey(String key) { if(UPPER_LEADING_CORNER.equals(key))return UPPER_LEFT_CORNER;if(UPPER_TRAILING_CORNER.equals(key))return UPPER_RIGHT_CORNER;if(LOWER_LEADING_CORNER.equals(key))return LOWER_LEFT_CORNER;if(LOWER_TRAILING_CORNER.equals(key))return LOWER_RIGHT_CORNER;return key; }
    public Component getCorner(String key) { return corners.get(cornerKey(key)); }
    public void setCorner(String key,Component corner) { key=cornerKey(key);if(!UPPER_LEFT_CORNER.equals(key)&&!UPPER_RIGHT_CORNER.equals(key)&&!LOWER_LEFT_CORNER.equals(key)&&!LOWER_RIGHT_CORNER.equals(key))throw new IllegalArgumentException("invalid corner");Component old=corners.remove(key);if(old!=null)remove(old);if(corner!=null){corners.put(key,corner);add(corner,key);}firePropertyChange(key,old,corner);revalidate();repaint(); }
    public void setWheelScrollingEnabled(boolean enable) { boolean old=wheelScrollingEnabled;wheelScrollingEnabled=enable;firePropertyChange("wheelScrollingEnabled",old,enable); }
    public boolean isWheelScrollingEnabled() { return wheelScrollingEnabled; }
}
