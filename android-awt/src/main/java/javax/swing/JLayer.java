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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.accessibility.Accessible;
import javax.swing.border.Border;
import javax.swing.plaf.LayerUI;

/**
 * {@code JLayer} is a universal decorator for Swing components: it paints its
 * view through a {@link LayerUI}, which can draw over or around it, and lays
 * the view and a transparent glass pane out over its whole bounds.
 * <p>
 * The shadow Toolkit has no {@code AWTEventListener}s, so the events selected
 * by {@link #setLayerEventMask(long)} are not forwarded to
 * {@link LayerUI#eventDispatched}; the mask is kept and reported, painting,
 * layout, sizing and property changes work as on the desktop.
 *
 * @param <V> the type of {@code JLayer}'s view component
 * @since 1.7
 */
public final class JLayer<V extends Component>
        extends JComponent
        implements Scrollable, PropertyChangeListener, Accessible {

    private static final long serialVersionUID = 1L;

    private V view;
    private JPanel glassPane;
    private long eventMask;
    private transient boolean isPainting;

    /**
     * Creates a new {@code JLayer} object with a {@code null} view component
     * and default {@link LayerUI}.
     */
    public JLayer() {
        this(null);
    }

    /**
     * Creates a new {@code JLayer} object with default {@link LayerUI}.
     *
     * @param view the component to be decorated by this {@code JLayer}
     */
    public JLayer(V view) {
        this(view, new LayerUI<V>());
    }

    /**
     * Creates a new {@code JLayer} object with the specified view component
     * and {@link LayerUI} object.
     *
     * @param view the component to be decorated
     * @param ui the {@link LayerUI} delegate to be used by this {@code JLayer}
     */
    public JLayer(V view, LayerUI<V> ui) {
        setGlassPane(createGlassPane());
        setView(view);
        setUI(ui);
    }

    /**
     * Returns the {@code JLayer}'s view component or {@code null}.
     *
     * @return the {@code JLayer}'s view component or {@code null} if none exists
     */
    public V getView() {
        return view;
    }

    /**
     * Sets the {@code JLayer}'s view component, which can be {@code null}.
     *
     * @param view the view component for this {@code JLayer}
     */
    public void setView(V view) {
        Component oldView = getView();
        if (oldView != null) {
            super.remove(oldView);
        }
        if (view != null) {
            super.add(view, null, getComponentCount());
        }
        this.view = view;
        firePropertyChange("view", oldView, view);
        revalidate();
        repaint();
    }

    /**
     * Sets the {@link LayerUI} which will perform painting and receive input
     * events for this {@code JLayer}.
     *
     * @param ui the {@link LayerUI} for this {@code JLayer}
     */
    public void setUI(LayerUI<? super V> ui) {
        super.setUI(ui);
    }

    /**
     * Returns the {@link LayerUI} for this {@code JLayer}.
     *
     * @return the {@code LayerUI} for this {@code JLayer}
     */
    @SuppressWarnings("unchecked")
    public LayerUI<? super V> getUI() {
        return (LayerUI<? super V>) ui;
    }

    /**
     * Returns the {@code JLayer}'s glassPane component or {@code null}.
     *
     * @return the {@code JLayer}'s glassPane component or {@code null} if none exists
     */
    public JPanel getGlassPane() {
        return glassPane;
    }

    /**
     * Sets the {@code JLayer}'s glassPane component, which can be {@code null}.
     *
     * @param glassPane the glassPane component of this {@code JLayer}
     */
    public void setGlassPane(JPanel glassPane) {
        Component oldGlassPane = getGlassPane();
        boolean isGlassPaneVisible = false;
        if (oldGlassPane != null) {
            isGlassPaneVisible = oldGlassPane.isVisible();
            super.remove(oldGlassPane);
        }
        if (glassPane != null) {
            glassPane.setVisible(isGlassPaneVisible);
            super.add(glassPane, null, 0);
        }
        this.glassPane = glassPane;
        firePropertyChange("glassPane", oldGlassPane, glassPane);
        revalidate();
        repaint();
    }

    /**
     * Called by the constructor methods to create a default {@code glassPane}:
     * a non-opaque panel that only claims the points its children cover.
     *
     * @return the default {@code glassPane}
     */
    public JPanel createGlassPane() {
        return new DefaultLayerGlassPane();
    }

    /**
     * Sets the layout manager for this container. {@code JLayer} lays itself
     * out through its {@link LayerUI}.
     *
     * @param mgr the specified layout manager
     * @throws IllegalArgumentException this method is not supported
     */
    @Override
    public void setLayout(LayoutManager mgr) {
        if (mgr != null) {
            throw new IllegalArgumentException("JLayer.setLayout() not supported");
        }
    }

    /**
     * A non-{@code null} border, or non-zero insets, isn't supported, to prevent
     * the geometry of this component from becoming complex enough to inhibit
     * subclassing of {@code LayerUI} class.
     *
     * @param border the {@code Border} to set
     * @throws IllegalArgumentException this method is not supported
     */
    @Override
    public void setBorder(Border border) {
        if (border != null) {
            throw new IllegalArgumentException("JLayer.setBorder() not supported");
        }
    }

    /**
     * Not supported by {@code JLayer}, use {@link #setView} or
     * {@link #setGlassPane} instead.
     *
     * @throws UnsupportedOperationException this method is not supported
     */
    @Override
    public Component add(Component comp) {
        throw notSupported();
    }

    /** @throws UnsupportedOperationException this method is not supported */
    @Override
    public Component add(Component comp, int index) {
        throw notSupported();
    }

    /** @throws UnsupportedOperationException this method is not supported */
    @Override
    public Component add(String name, Component comp) {
        throw notSupported();
    }

    /** @throws UnsupportedOperationException this method is not supported */
    @Override
    public void add(Component comp, Object constraints) {
        throw notSupported();
    }

    /** @throws UnsupportedOperationException this method is not supported */
    @Override
    public void add(Component comp, Object constraints, int index) {
        throw notSupported();
    }

    private static UnsupportedOperationException notSupported() {
        return new UnsupportedOperationException(
                "Adding components to JLayer is not supported, "
                        + "use setView() or setGlassPane() instead");
    }

    /**
     * Removes the view or the glass pane when {@code comp} is one of them,
     * otherwise the component.
     *
     * @param comp the component to be removed
     */
    @Override
    public void remove(Component comp) {
        if (comp == null) {
            super.remove(comp);
        } else if (comp == getView()) {
            setView(null);
        } else if (comp == getGlassPane()) {
            setGlassPane(null);
        } else {
            super.remove(comp);
        }
    }

    /**
     * Removes the view and the glass pane.
     */
    @Override
    public void removeAll() {
        if (view != null) {
            setView(null);
        }
        if (glassPane != null) {
            setGlassPane(null);
        }
    }

    /**
     * Always returns {@code true}: a repaint of a descendant paints from this
     * layer, so its {@code LayerUI} decorates the whole region.
     *
     * @return true
     */
    protected boolean isPaintingOrigin() {
        return true;
    }

    /**
     * Delegates its functionality to the
     * {@link LayerUI#paintImmediately(int, int, int, int, JLayer)} method,
     * if {@code LayerUI} is set.
     *
     * @param x the x value of the region to be painted
     * @param y the y value of the region to be painted
     * @param w the width of the region to be painted
     * @param h the height of the region to be painted
     */
    public void paintImmediately(int x, int y, int w, int h) {
        LayerUI<? super V> layerUI = getUI();
        if (layerUI != null) {
            layerUI.paintImmediately(x, y, w, h, this);
        } else {
            repaint(x, y, w, h);
        }
    }

    /**
     * Delegates all painting to the {@link LayerUI} object, whose default
     * {@code paint} paints this layer's children back through here.
     *
     * @param g the {@code Graphics} to render to
     */
    @Override
    public void paint(Graphics g) {
        if (!isPainting) {
            isPainting = true;
            try {
                super.paintComponent(g);
            } finally {
                isPainting = false;
            }
        } else {
            super.paint(g);
        }
    }

    /**
     * This method is empty, because all painting is done by
     * {@link #paint(Graphics)} and {@link LayerUI#update(Graphics, JComponent)}.
     */
    @Override
    protected void paintComponent(Graphics g) {
    }

    /**
     * The {@code JLayer} overrides the default implementation of this method
     * (in {@code JComponent}) to return {@code false}: the glass pane overlaps
     * the view.
     *
     * @return false
     */
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /**
     * Forwards a property change of the {@code LayerUI} to
     * {@link LayerUI#applyPropertyChange}.
     *
     * @param evt the property change event
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (getUI() != null) {
            getUI().applyPropertyChange(evt, this);
        }
    }

    /**
     * Sets the bitmask of event types to receive by this {@code JLayer}. See
     * the class comment: the shadow does not forward these events.
     *
     * @param layerEventMask the bitmask of event types to receive
     */
    public void setLayerEventMask(long layerEventMask) {
        long oldEventMask = getLayerEventMask();
        this.eventMask = layerEventMask;
        firePropertyChange("layerEventMask", oldEventMask, layerEventMask);
    }

    /**
     * Returns the bitmap of event mask to receive by this {@code JLayer} and
     * its {@code LayerUI}.
     *
     * @return the bitmask of event types to receive for this {@code JLayer}
     */
    public long getLayerEventMask() {
        return eventMask;
    }

    /**
     * Delegates its functionality to the {@link LayerUI#updateUI(JLayer)} method,
     * if {@code LayerUI} is set.
     */
    @Override
    public void updateUI() {
        if (getUI() != null) {
            getUI().updateUI(this);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        Dimension d = getUI() == null ? null : getUI().getPreferredSize(this);
        return d != null ? d : super.getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        Dimension d = getUI() == null ? null : getUI().getMinimumSize(this);
        return d != null ? d : super.getMinimumSize();
    }

    @Override
    public Dimension getMaximumSize() {
        if (isMaximumSizeSet()) {
            return super.getMaximumSize();
        }
        Dimension d = getUI() == null ? null : getUI().getMaximumSize(this);
        return d != null ? d : super.getMaximumSize();
    }

    /**
     * Returns the preferred size of the viewport for a view component.
     *
     * @return the preferred size of the viewport for a view component
     */
    public Dimension getPreferredScrollableViewportSize() {
        if (getView() instanceof Scrollable) {
            return ((Scrollable) getView()).getPreferredScrollableViewportSize();
        }
        return getPreferredSize();
    }

    /**
     * Returns a scroll increment, which is required for components that display
     * logical rows or columns, to completely expose one block of rows or
     * columns, depending on the value of orientation.
     *
     * @return the "block" increment for scrolling in the specified direction
     */
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (getView() instanceof Scrollable) {
            return ((Scrollable) getView()).getScrollableBlockIncrement(visibleRect, orientation, direction);
        }
        return (orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width;
    }

    /**
     * Returns {@code false} to indicate that the height of the viewport does
     * not determine the height of the layer, unless the preferred height of
     * the layer is smaller than the height of the viewport.
     *
     * @return whether the layer should track the height of the viewport
     */
    public boolean getScrollableTracksViewportHeight() {
        if (getView() instanceof Scrollable) {
            return ((Scrollable) getView()).getScrollableTracksViewportHeight();
        }
        return getParent() instanceof JViewport
                && getParent().getHeight() > getPreferredSize().height;
    }

    /**
     * Returns {@code false} to indicate that the width of the viewport does not
     * determine the width of the layer, unless the preferred width of the layer
     * is smaller than the width of the viewport.
     *
     * @return whether the layer should track the width of the viewport
     */
    public boolean getScrollableTracksViewportWidth() {
        if (getView() instanceof Scrollable) {
            return ((Scrollable) getView()).getScrollableTracksViewportWidth();
        }
        return getParent() instanceof JViewport
                && getParent().getWidth() > getPreferredSize().width;
    }

    /**
     * Returns a scroll increment, which is required for components that display
     * logical rows or columns, to completely expose one new row or column,
     * depending on the value of orientation. Ideally, components should handle
     * a partially exposed row or column by returning the distance required to
     * completely expose the item.
     *
     * @return the "unit" increment for scrolling in the specified direction
     */
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        if (getView() instanceof Scrollable) {
            return ((Scrollable) getView()).getScrollableUnitIncrement(visibleRect, orientation, direction);
        }
        return 1;
    }

    /**
     * Delegates its functionality to the {@link LayerUI#doLayout(JLayer)} method,
     * if {@code LayerUI} is set.
     */
    @Override
    public void doLayout() {
        if (getUI() != null) {
            getUI().doLayout(this);
        }
    }

    /**
     * The default glass pane: non-opaque and, as created, hidden (setGlassPane
     * carries over the visibility of the pane it replaces), so it neither
     * paints nor takes the mouse until a LayerUI shows it.
     */
    private static class DefaultLayerGlassPane extends JPanel {
        private static final long serialVersionUID = 1L;

        DefaultLayerGlassPane() {
            setOpaque(false);
        }
    }
}
