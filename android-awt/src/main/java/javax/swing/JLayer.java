/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javax.swing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.accessibility.Accessible;
import javax.swing.border.Border;
import javax.swing.plaf.LayerUI;

/**
 * {@code JLayer} is a universal decorator for Swing components
 * which enables you to implement various advanced painting effects as well as
 * receive notifications of all {@code AWTEvent}s generated within its borders.
 * <p>
 * {@code JLayer} delegates the handling of painting and input events to a
 * {@link javax.swing.plaf.LayerUI} object, which performs the actual decoration.
 * <p>
 * The custom painting implemented in the {@code LayerUI} and events notification
 * work for the JLayer itself and all its subcomponents.
 *
 * @param <V> the type of {@code JLayer}'s view component
 *
 * @see #JLayer(Component)
 * @see #setView(Component)
 * @see #getView()
 * @see javax.swing.plaf.LayerUI
 * @see #JLayer(Component, LayerUI)
 * @see #setUI(javax.swing.plaf.LayerUI)
 * @see #getUI()
 * @since 1.7
 *
 * @author Alexander Potochkin
 */
public final class JLayer<V extends Component>
        extends JComponent
        implements Scrollable, PropertyChangeListener, Accessible {
    private V view;
    // this field is necessary because JComponent.ui is transient
    // when layerUI is serializable
    private LayerUI<? super V> layerUI;
    private JPanel glassPane;
    private long eventMask;
    private transient boolean isPainting;
    private transient boolean isPaintingImmediately;
    private transient boolean isUpdatingImage;

    /**
     * Creates a new {@code JLayer} object with a {@code null} view component
     * and default {@link javax.swing.plaf.LayerUI}.
     *
     * @see #setView
     * @see #setUI
     */
    public JLayer() {
        this(null);
    }

    /**
     * Creates a new {@code JLayer} object
     * with default {@link javax.swing.plaf.LayerUI}.
     *
     * @param view the component to be decorated by this {@code JLayer}
     *
     * @see #setUI
     */
    public JLayer(V view) {
        this(view, new LayerUI<V>());
    }

    /**
     * Creates a new {@code JLayer} object with the specified view component
     * and {@link javax.swing.plaf.LayerUI} object.
     *
     * @param view the component to be decorated
     * @param ui the {@link javax.swing.plaf.LayerUI} delegate
     * to be used by this {@code JLayer}
     */
    public JLayer(V view, LayerUI<V> ui) {
        setGlassPane(createGlassPane());
        setView(view);
        setUI(ui);
    }

    /**
     * Returns the {@code JLayer}'s view component or {@code null}.
     *
     * @return the {@code JLayer}'s view component
     *         or {@code null} if none exists
     *
     * @see #setView(Component)
     */
    public V getView() {
        return view;
    }

    /**
     * Sets the {@code JLayer}'s view component, which can be {@code null}.
     *
     * @param view the view component for this {@code JLayer}
     *
     * @see #getView()
     */
    public void setView(V view) {
        Component oldView = getView();
        if (oldView != null) {
            super.remove(oldView);
        }
        if (view != null) {
            super.addImpl(view, null, getComponentCount());
        }
        this.view = view;
        firePropertyChange("view", oldView, view);
        revalidate();
        repaint();
    }

    /**
     * Sets the {@link javax.swing.plaf.LayerUI} which will perform painting
     * and receive input events for this {@code JLayer}.
     *
     * @param ui the {@link javax.swing.plaf.LayerUI} for this {@code JLayer}
     */
    public void setUI(LayerUI<? super V> ui) {
        this.layerUI = ui;
        super.setUI(ui);
    }

    /**
     * Returns the {@link javax.swing.plaf.LayerUI} for this {@code JLayer}.
     *
     * @return the {@code LayerUI} for this {@code JLayer}
     */
    public LayerUI<? super V> getUI() {
        return layerUI;
    }

    /**
     * Returns the {@code JLayer}'s glassPane component or {@code null}.
     *
     * @return the {@code JLayer}'s glassPane component
     *         or {@code null} if none exists
     *
     * @see #setGlassPane(JPanel)
     */
    public JPanel getGlassPane() {
        return glassPane;
    }

    /**
     * Sets the {@code JLayer}'s glassPane component, which can be {@code null}.
     *
     * @param glassPane the glassPane component of this {@code JLayer}
     *
     * @see #getGlassPane()
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
            super.addImpl(glassPane, null, 0);
        }
        this.glassPane = glassPane;
        firePropertyChange("glassPane", oldGlassPane, glassPane);
        revalidate();
        repaint();
    }

    /**
     * Called by the constructor methods to create a default {@code glassPane}.
     * By default this method creates a new JPanel with visibility set to true
     * and opacity set to false.
     *
     * @return the default {@code glassPane}
     */
    public JPanel createGlassPane() {
        return new DefaultLayerGlassPane();
    }

    /**
     * Sets the layout manager for this container.  This method is
     * overridden to prevent the layout manager from being set.
     * <p>Note:  If {@code mgr} is non-{@code null}, this
     * method will throw an exception as layout managers are not supported on
     * a {@code JLayer}.
     *
     * @param mgr the specified layout manager
     * @exception IllegalArgumentException this method is not supported
     */
    public void setLayout(LayoutManager mgr) {
        if (mgr != null) {
            throw new IllegalArgumentException("JLayer.setLayout() not supported");
        }
    }

    /**
     * A non-{@code null} border, or non-zero insets, isn't supported, to prevent the geometry
     * of this component from becoming complex enough to inhibit
     * subclassing of {@code LayerUI} class.  To create a {@code JLayer} with a border,
     * add it to a {@code JPanel} that has a border.
     * <p>Note:  If {@code border} is non-{@code null}, this
     * method will throw an exception as borders are not supported on
     * a {@code JLayer}.
     *
     * @param border the {@code Border} to set
     * @exception IllegalArgumentException this method is not supported
     */
    public void setBorder(Border border) {
        if (border != null) {
            throw new IllegalArgumentException("JLayer.setBorder() not supported");
        }
    }

    /**
     * This method is not supported by {@code JLayer}
     * and always throws {@code UnsupportedOperationException}
     *
     * @throws UnsupportedOperationException this method is not supported
     *
     * @see #setView(Component)
     * @see #setGlassPane(JPanel)
     */
    protected void addImpl(Component comp, Object constraints, int index) {
        throw new UnsupportedOperationException(
                "Adding components to JLayer is not supported, " +
                        "use setView() or setGlassPane() instead");
    }

    /**
     * {@inheritDoc}
     */
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
     * {@inheritDoc}
     */
    public void removeAll() {
        if (view != null) {
            setView(null);
        }
        if (glassPane != null) {
            setGlassPane(null);
        }
    }

    /**
     * Always returns {@code true} to cause painting to originate from {@code JLayer},
     * or one of its ancestors.
     *
     * @return true
     * @see JComponent#isPaintingOrigin()
     */
    protected boolean isPaintingOrigin() {
        return true;
    }

    /**
     * Delegates its functionality to the
     * {@link javax.swing.plaf.LayerUI#paintImmediately(int, int, int, int, JLayer)} method,
     * if {@code LayerUI} is set.
     *
     * @param x  the x value of the region to be painted
     * @param y  the y value of the region to be painted
     * @param w  the width of the region to be painted
     * @param h  the height of the region to be painted
     */
    public void paintImmediately(int x, int y, int w, int h) {
        if (!isPaintingImmediately && getUI() != null) {
            isPaintingImmediately = true;
            try {
                getUI().paintImmediately(x, y, w, h, this);
            } finally {
                isPaintingImmediately = false;
            }
        } else {
            super.paintImmediately(x, y, w, h);
        }
    }

    /**
     * Delegates its functionality to the
     * {@link javax.swing.plaf.LayerUI#imageUpdate(java.awt.Image, int, int, int, int, int, JLayer)} method,
     * if the {@code LayerUI} is set.
     *
     * @param     img   the image being observed
     * @param     infoflags   see {@code imageUpdate} for more information
     * @param     x   the <i>x</i> coordinate
     * @param     y   the <i>y</i> coordinate
     * @param     w   the width
     * @param     h   the height
     * @return    {@code false} if the infoflags indicate that the
     *            image is completely loaded; {@code true} otherwise.
     * @since 1.8
     */
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
        // LayerUI.imageUpdate calls back into this method; the flag sends that call to the
        // inherited implementation, as paintImmediately does.
        if (!isUpdatingImage && getUI() != null) {
            isUpdatingImage = true;
            try {
                return getUI().imageUpdate(img, infoflags, x, y, w, h, this);
            } finally {
                isUpdatingImage = false;
            }
        }
        return super.imageUpdate(img, infoflags, x, y, w, h);
    }

    /**
     * Delegates all painting to the {@link javax.swing.plaf.LayerUI} object.
     *
     * @param g the {@code Graphics} to render to
     */
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
     * {@link #paint(Graphics)} and
     * {@link javax.swing.plaf.LayerUI#update(Graphics, JComponent)} methods
     */
    protected void paintComponent(Graphics g) {
    }

    /**
     * The {@code JLayer} overrides the default implementation of
     * this method (in {@code JComponent}) to return {@code false}.
     * This ensures
     * that the drawing machinery will call the {@code JLayer}'s
     * {@code paint}
     * implementation rather than messaging the {@code JLayer}'s
     * children directly.
     *
     * @return false
     */
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if (getUI() != null) {
            getUI().applyPropertyChange(evt, this);
        }
    }

    /**
     * Enables the events from JLayer and <b>all its descendants</b>
     * defined by the specified event mask parameter
     * to be delivered to the
     * {@link LayerUI#eventDispatched(java.awt.AWTEvent, JLayer)} method.
     * <p>
     * Events are delivered provided that {@code LayerUI} is set
     * for this {@code JLayer} and the {@code JLayer}
     * is displayable.
     * <p>
     * On Android the mask is stored and the bound property fires, but no event reaches
     * {@code eventDispatched}: the JDK routes them through
     * {@code Toolkit.addAWTEventListener}, and the shadow toolkit has no global event
     * listener chain to hook (the sidebar host dispatches input per component).
     *
     * @param layerEventMask the bitmask of event types to receive
     *
     * @see #getLayerEventMask()
     * @see LayerUI#eventDispatched(java.awt.AWTEvent, JLayer)
     * @see Component#isDisplayable()
     */
    public void setLayerEventMask(long layerEventMask) {
        long oldEventMask = getLayerEventMask();
        this.eventMask = layerEventMask;
        firePropertyChange("layerEventMask", Long.valueOf(oldEventMask), Long.valueOf(layerEventMask));
    }

    /**
     * Returns the bitmap of event mask to receive by this {@code JLayer}
     * and its {@code LayerUI}.
     * <p>
     * It means that {@link javax.swing.plaf.LayerUI#eventDispatched(java.awt.AWTEvent, JLayer)} method
     * will only receive events that match the event mask.
     * <p>
     * By default {@code JLayer} receives no events.
     *
     * @return the bitmask of event types to receive for this {@code JLayer}
     */
    public long getLayerEventMask() {
        return eventMask;
    }

    /**
     * Delegates its functionality to the {@link javax.swing.plaf.LayerUI#updateUI(JLayer)} method,
     * if {@code LayerUI} is set.
     */
    public void updateUI() {
        if (getUI() != null) {
            getUI().updateUI(this);
        }
    }

    /**
     * Returns the preferred size of the viewport for a view component.
     * <p>
     * If the view component of this layer implements {@link Scrollable}, this method delegates its
     * implementation to the view component.
     *
     * @return the preferred size of the viewport for a view component
     *
     * @see Scrollable
     */
    public Dimension getPreferredScrollableViewportSize() {
        if (getView() instanceof Scrollable) {
            return ((Scrollable)getView()).getPreferredScrollableViewportSize();
        }
        return getPreferredSize();
    }

    /**
     * Returns a scroll increment, which is required for components
     * that display logical rows or columns in order to completely expose
     * one block of rows or columns, depending on the value of orientation.
     * <p>
     * If the view component of this layer implements {@link Scrollable}, this method delegates its
     * implementation to the view component.
     *
     * @return the "block" increment for scrolling in the specified direction
     *
     * @see Scrollable
     */
    public int getScrollableBlockIncrement(Rectangle visibleRect,
                                           int orientation, int direction) {
        if (getView() instanceof Scrollable) {
            return ((Scrollable)getView()).getScrollableBlockIncrement(visibleRect,
                    orientation, direction);
        }
        return (orientation == SwingConstants.VERTICAL) ? visibleRect.height :
                visibleRect.width;
    }

    /**
     * Returns {@code false} to indicate that the height of the viewport does not
     * determine the height of the layer, unless the preferred height
     * of the layer is smaller than the height of the viewport.
     * <p>
     * If the view component of this layer implements {@link Scrollable}, this method delegates its
     * implementation to the view component.
     *
     * @return whether the layer should track the height of the viewport
     *
     * @see Scrollable
     */
    public boolean getScrollableTracksViewportHeight() {
        if (getView() instanceof Scrollable) {
            return ((Scrollable)getView()).getScrollableTracksViewportHeight();
        }
        return false;
    }

    /**
     * Returns {@code false} to indicate that the width of the viewport does not
     * determine the width of the layer, unless the preferred width
     * of the layer is smaller than the width of the viewport.
     * <p>
     * If the view component of this layer implements {@link Scrollable}, this method delegates its
     * implementation to the view component.
     *
     * @return whether the layer should track the width of the viewport
     *
     * @see Scrollable
     */
    public boolean getScrollableTracksViewportWidth() {
        if (getView() instanceof Scrollable) {
            return ((Scrollable)getView()).getScrollableTracksViewportWidth();
        }
        return false;
    }

    /**
     * Returns a scroll increment, which is required for components
     * that display logical rows or columns in order to completely expose
     * one new row or column, depending on the value of orientation.
     * Ideally, components should handle a partially exposed row or column
     * by returning the distance required to completely expose the item.
     * <p>
     * Scrolling containers, like {@code JScrollPane}, will use this method
     * each time the user requests a unit scroll.
     * <p>
     * If the view component of this layer implements {@link Scrollable}, this method delegates its
     * implementation to the view component.
     *
     * @return The "unit" increment for scrolling in the specified direction.
     *         This value should always be positive.
     *
     * @see Scrollable
     */
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation,
                                          int direction) {
        if (getView() instanceof Scrollable) {
            return ((Scrollable) getView()).getScrollableUnitIncrement(
                    visibleRect, orientation, direction);
        }
        return 1;
    }

    /**
     * Delegates its functionality to the {@link javax.swing.plaf.LayerUI#doLayout(JLayer)} method,
     * if {@code LayerUI} is set.
     */
    public void doLayout() {
        if (getUI() != null) {
            getUI().doLayout(this);
        }
    }

    /**
     * The default glassPane for the {@link javax.swing.JLayer}.
     * It is a subclass of {@code JPanel} which is non opaque by default.
     */
    private static class DefaultLayerGlassPane extends JPanel {
        /**
         * Creates a new {@link DefaultLayerGlassPane}
         */
        public DefaultLayerGlassPane() {
            setOpaque(false);
        }

        /**
         * First, implementation of this method iterates through
         * glassPane's child components and returns {@code true}
         * if any of them is visible and contains passed x,y point.
         * After that it checks if no mouseListeners is attached to this component
         * and no mouse cursor is set, then it returns {@code false},
         * otherwise calls the super implementation of this method.
         *
         * @param x the <i>x</i> coordinate of the point
         * @param y the <i>y</i> coordinate of the point
         * @return true if this component logically contains x,y
         */
        public boolean contains(int x, int y) {
            for (int i = 0; i < getComponentCount(); i++) {
                Component c = getComponent(i);
                if (c.isVisible() && c.contains(x - c.getX(), y - c.getY())) {
                    return true;
                }
            }
            if (getMouseListeners().length == 0
                    && getMouseMotionListeners().length == 0
                    && getMouseWheelListeners().length == 0) {
                return false;
            }
            return super.contains(x, y);
        }
    }
}
