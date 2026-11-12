/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Serializable;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ViewportUI;

/**
 * The "viewport" or "porthole" through which you see the underlying
 * information. When you scroll, what moves is the viewport. It is like
 * peering through a camera's viewfinder. Moving the viewfinder upwards
 * brings new things into view at the top of the picture and loses
 * things that were at the bottom.
 * <p>
 * As in the JDK the view position is the negated location of the view,
 * so a view moved directly by its owner is reflected here.
 * <p>
 * Android: the host rasterizes every frame, so there is no window blit;
 * the scroll mode is kept and reported, and every mode repaints the view
 * from its new position (the JDK's {@code SIMPLE_SCROLL_MODE} behaviour).
 *
 * @author Hans Muller
 * @author Philip Milne
 */
public class JViewport extends JComponent implements javax.accessibility.Accessible {

    private static final long serialVersionUID = 1L;

    /**
     * @see #getUIClassID
     */
    private static final String uiClassID = "ViewportUI";

    /**
     * True when the viewport dimensions have been determined.
     * The default is false.
     */
    protected boolean isViewSizeSet = false;

    /**
     * The last <code>viewPosition</code> that we've painted, so we know how
     * much of the backing store image is valid.
     */
    protected Point lastPaintPosition = null;

    /**
     * True when this viewport is maintaining an offscreen image of its
     * contents, so that some scrolling can take place using fast "bit-blit"
     * operations instead of by accessing the view object to construct the
     * display.  The default is <code>false</code>.
     *
     * @deprecated As of Java 2 platform v1.3
     * @see #setScrollMode
     */
    @Deprecated
    protected boolean backingStore = false;

    /** The view image used for a backing store. */
    transient protected Image backingStoreImage = null;

    /**
     * The <code>scrollUnderway</code> flag is used for components like
     * <code>JList</code>.  When the downarrow key is pressed on a
     * <code>JList</code> and the selected
     * cell is the last in the list, the <code>scrollpane</code> autoscrolls.
     * Here, the old selected cell needs repainting and so we need
     * a flag to make the viewport do the optimized painting
     * only when there is an explicit call to
     * <code>setViewPosition(Point)</code>.
     * When <code>setBounds</code> is called through other routes,
     * the flag is off and the view repaints normally.  Another approach
     * would be to remove this from the <code>JViewport</code>
     * class and have the <code>JList</code> manage this case by using
     * <code>setBackingStoreEnabled</code>.  The default is
     * <code>false</code>.
     */
    protected boolean scrollUnderway = false;

    /**
     * Use <code>graphics.copyArea</code> to implement scrolling.
     * This is the fastest for most applications.
     *
     * @see #setScrollMode
     * @since 1.3
     */
    public static final int BLIT_SCROLL_MODE = 1;

    /**
     * Draws viewport contents into an offscreen image.
     * This was previously the default mode for <code>JTable</code>.
     * This mode may offer advantages over "blit mode"
     * in some cases, but it requires a large chunk of extra RAM.
     *
     * @see #setScrollMode
     * @since 1.3
     */
    public static final int BACKINGSTORE_SCROLL_MODE = 2;

    /**
     * This mode uses the very simplest method of scrolling:
     * it repaints the contents of the scrollpane each time it is scrolled.
     * This was the default behavior in Swing 1.0 and Swing 1.1.
     * Either of the other two options will provide better performance
     * in most cases.
     *
     * @see #setScrollMode
     * @since 1.3
     */
    public static final int SIMPLE_SCROLL_MODE = 0;

    private int scrollMode = BLIT_SCROLL_MODE;

    /** The listener on the view's size; created per view by {@link #createViewListener}. */
    private transient ViewListener viewListener = null;

    /** Shared by every ChangeEvent this viewport fires. */
    private transient ChangeEvent changeEvent = null;

    /** The first view set fires nothing, as in the JDK; later ones do. */
    private boolean hasHadValidView;

    /** A view change fires on the next reshape even when the size is unchanged. */
    private boolean viewChanged;

    /** Creates a <code>JViewport</code>. */
    public JViewport() {
        super();
        setLayout(createLayoutManager());
        // The JDK also makes the viewport opaque and installs its ViewportUI here. Without
        // a delegate an opaque viewport fills nothing, and installing the look and feel's
        // ViewportUI behind every sidebar scroll pane would repaint the panels' backgrounds;
        // updateUI() installs it on request.
        setInheritsPopupMenu(true);
    }

    /**
     * Returns the L&amp;F object that renders this component.
     *
     * @return a <code>ViewportUI</code> object, or null when none is installed
     * @since 1.3
     */
    public ViewportUI getUI() {
        return (ViewportUI) ui;
    }

    /**
     * Sets the L&amp;F object that renders this component.
     *
     * @param ui  the <code>ViewportUI</code> L&amp;F object
     * @since 1.3
     */
    public void setUI(ViewportUI ui) {
        super.setUI(ui);
    }

    /**
     * Returns a string that specifies the name of the L&amp;F class
     * that renders this component.
     *
     * @return the string "ViewportUI"
     */
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Sets the <code>JViewport</code>'s one lightweight child,
     * which can be <code>null</code>.
     * (Since there is only one child which occupies the entire viewport,
     * the <code>constraints</code> and <code>index</code>
     * arguments are ignored.)
     *
     * @param child       the lightweight <code>child</code> of the viewport
     * @param constraints the <code>constraints</code> to be respected
     * @param index       the index
     * @see #setView
     */
    protected void addImpl(Component child, Object constraints, int index) {
        setView(child);
    }

    /**
     * Removes the <code>Viewport</code>s one lightweight child.
     *
     * @see #setView
     */
    public void remove(Component child) {
        child.removeComponentListener(viewListener);
        super.remove(child);
    }

    /**
     * Scrolls the view so that <code>Rectangle</code>
     * within the view becomes visible.
     * <p>
     * This attempts to validate the view before scrolling if the
     * view is currently not valid - <code>isValid</code> returns false.
     * To avoid excessive validation when the containment hierarchy is
     * being created this will not validate if one of the ancestors does not
     * have a peer, or there is no validate root ancestor, or one of the
     * ancestors is not a <code>Window</code> or <code>Applet</code>.
     * <p>
     * Note that this method will not scroll outside of the
     * valid viewport; for example, if <code>contentRect</code> is larger
     * than the viewport, scrolling will be confined to the viewport's
     * bounds.
     *
     * @param contentRect the <code>Rectangle</code> to display
     * @see JComponent#isValidateRoot
     * @see java.awt.Component#isValid
     */
    public void scrollRectToVisible(Rectangle contentRect) {
        Component view = getView();

        if (view == null) {
            return;
        } else {
            if (!view.isValid()) {
                // If the view is not valid, validate. scrollRectToVisible
                // may fail if the view is not valid first, contentRect
                // could be bigger than invalid size.
                validateView();
            }
            int dx, dy;

            dx = positionAdjustment(getWidth(), contentRect.width, contentRect.x);
            dy = positionAdjustment(getHeight(), contentRect.height, contentRect.y);

            if (dx != 0 || dy != 0) {
                Point viewPosition = getViewPosition();
                Dimension viewSize = view.getSize();
                int startX = viewPosition.x;
                int startY = viewPosition.y;
                Dimension extent = getExtentSize();

                viewPosition.x -= dx;
                viewPosition.y -= dy;
                // Only constrain the location if the view is valid. If the
                // the view isn't valid, it typically indicates the view
                // isn't visible yet and most likely has a bogus size as will
                // we, and therefore we shouldn't constrain the scrolling
                if (view.isValid()) {
                    Container parent = getParent();
                    // The JDK reads the parent's orientation unconditionally; a viewport
                    // with no parent is laid out left to right.
                    if (parent == null || parent.getComponentOrientation().isLeftToRight()) {
                        if (viewPosition.x + extent.width > viewSize.width) {
                            viewPosition.x = Math.max(0, viewSize.width - extent.width);
                        } else if (viewPosition.x < 0) {
                            viewPosition.x = 0;
                        }
                    } else {
                        if (extent.width > viewSize.width) {
                            viewPosition.x = viewSize.width - extent.width;
                        } else {
                            viewPosition.x = Math.max(0, Math.min(viewSize.width - extent.width, viewPosition.x));
                        }
                    }
                    if (viewPosition.y + extent.height > viewSize.height) {
                        viewPosition.y = Math.max(0, viewSize.height - extent.height);
                    } else if (viewPosition.y < 0) {
                        viewPosition.y = 0;
                    }
                }
                if (viewPosition.x != startX || viewPosition.y != startY) {
                    setViewPosition(viewPosition);
                    // As the JDK: a programmatic scroll may be followed by other
                    // repaints, so the next paint must not take the blit shortcut.
                    scrollUnderway = false;
                }
            }
        }
    }

    /**
     * Ascends the <code>Viewport</code>'s parents stopping when
     * a component is found that returns
     * <code>true</code> to <code>isValidateRoot</code>.
     * If all the <code>Component</code>'s  parents are visible,
     * <code>validate</code> will then be invoked on it. The
     * <code>RepaintManager</code> is then invoked with
     * <code>removeInvalidComponent</code>. This
     * is the synchronous version of a <code>revalidate</code>.
     */
    private void validateView() {
        Container validateRoot = null;
        for (Container c = this; c != null; c = c.getParent()) {
            if (c instanceof CellRendererPane) {
                return;
            }
            if (c.isValidateRoot()) {
                validateRoot = c;
                break;
            }
        }
        if (validateRoot == null) {
            return;
        }
        validateRoot.validate();
        RepaintManager rm = RepaintManager.currentManager(this);
        if (rm != null && validateRoot instanceof JComponent) {
            rm.removeInvalidComponent((JComponent) validateRoot);
        }
    }

    /*  Used by the scrollRectToVisible method to determine the
     *  proper direction and amount to move by. The integer variables are named
     *  width, but this method is applicable to height also. The code assumes that
     *  parentWidth/childWidth are positive and childAt can be negative.
     */
    private int positionAdjustment(int parentWidth, int childWidth, int childAt) {

        //   +-----+
        //   | --- |     No Change
        //   +-----+
        if (childAt >= 0 && childWidth + childAt <= parentWidth) {
            return 0;
        }

        //   +-----+
        //  ---------   No Change
        //   +-----+
        if (childAt <= 0 && childWidth + childAt >= parentWidth) {
            return 0;
        }

        //   +-----+          +-----+
        //   |   ----    ->   | ----|
        //   +-----+          +-----+
        if (childAt > 0 && childWidth <= parentWidth) {
            return -childAt + parentWidth - childWidth;
        }

        //   +-----+             +-----+
        //   |  --------  ->     |--------
        //   +-----+             +-----+
        if (childAt >= 0 && childWidth >= parentWidth) {
            return -childAt;
        }

        //   +-----+          +-----+
        // ----    |     ->   |---- |
        //   +-----+          +-----+
        if (childAt <= 0 && childWidth <= parentWidth) {
            return -childAt;
        }

        //   +-----+             +-----+
        //-------- |      ->   --------|
        //   +-----+             +-----+
        if (childAt < 0 && childWidth >= parentWidth) {
            return -childAt + parentWidth - childWidth;
        }

        return 0;
    }

    /**
     * The viewport "scrolls" its child (called the "view") by the
     * normal parent/child clipping (typically the view is moved in
     * the opposite direction of the scroll).  A non-<code>null</code> border,
     * or non-zero insets, isn't supported, to prevent the geometry
     * of this component from becoming complex enough to inhibit
     * subclassing.  To create a <code>JViewport</code> with a border,
     * add it to a <code>JPanel</code> that has a border.
     * <p>Note:  If <code>border</code> is non-<code>null</code>, this
     * method will throw an exception as borders are not supported on
     * a <code>JViewPort</code>.
     *
     * @param border the <code>Border</code> to set
     * @exception IllegalArgumentException this method is not implemented
     */
    public final void setBorder(Border border) {
        if (border != null) {
            throw new IllegalArgumentException("JViewport.setBorder() not supported");
        }
    }

    /**
     * Returns the insets (border) dimensions as (0,0,0,0), since borders
     * are not supported on a <code>JViewport</code>.
     *
     * @return a <code>Rectangle</code> of zero dimension and zero origin
     * @see #setBorder
     */
    public final Insets getInsets() {
        return new Insets(0, 0, 0, 0);
    }

    /**
     * Returns an <code>Insets</code> object containing this
     * <code>JViewport</code>s inset values.  The passed-in
     * <code>Insets</code> object will be reinitialized, and
     * all existing values within this object are overwritten.
     *
     * @param insets the <code>Insets</code> object which can be reused
     * @return this viewports inset values
     * @see #getInsets
     */
    public final Insets getInsets(Insets insets) {
        insets.left = insets.top = insets.right = insets.bottom = 0;
        return insets;
    }

    /**
     * The <code>JViewport</code> overrides the default implementation of
     * this method (in <code>JComponent</code>) to return false.
     * This ensures
     * that the drawing machinery will call the <code>Viewport</code>'s
     * <code>paint</code>
     * implementation rather than messaging the <code>JViewport</code>'s
     * children directly.
     *
     * @return false
     */
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /**
     * Returns true if scroll mode is a {@code BACKINGSTORE_SCROLL_MODE} to cause
     * painting to originate from {@code JViewport}, or one of its
     * ancestors. Otherwise returns {@code false}.
     *
     * @return true if if scroll mode is a {@code BACKINGSTORE_SCROLL_MODE}.
     */
    protected boolean isPaintingOrigin() {
        return scrollMode == BACKINGSTORE_SCROLL_MODE;
    }

    /**
     * Sets the bounds of this viewport.  If the viewport's width
     * or height has changed, fire a <code>StateChanged</code> event.
     *
     * @param x left edge of the origin
     * @param y top edge of the origin
     * @param w width in pixels
     * @param h height in pixels
     *
     * @see JComponent#reshape(int, int, int, int)
     */
    @SuppressWarnings("deprecation")
    public void reshape(int x, int y, int w, int h) {
        boolean sizeChanged = (getWidth() != w) || (getHeight() != h);
        if (sizeChanged) {
            backingStoreImage = null;
        }
        super.reshape(x, y, w, h);
        if (sizeChanged || viewChanged) {
            viewChanged = false;
            fireStateChanged();
        }
    }

    /**
     * Used to control the method of scrolling the viewport contents.
     * You may want to change this mode to get maximum performance for your
     * use case.
     *
     * @param mode one of the following values:
     * <ul>
     * <li> JViewport.BLIT_SCROLL_MODE
     * <li> JViewport.BACKINGSTORE_SCROLL_MODE
     * <li> JViewport.SIMPLE_SCROLL_MODE
     * </ul>
     *
     * @see #BLIT_SCROLL_MODE
     * @see #BACKINGSTORE_SCROLL_MODE
     * @see #SIMPLE_SCROLL_MODE
     * @since 1.3
     */
    @SuppressWarnings("deprecation")
    public void setScrollMode(int mode) {
        scrollMode = mode;
        backingStore = mode == BACKINGSTORE_SCROLL_MODE;
    }

    /**
     * Returns the current scrolling mode.
     *
     * @return the <code>scrollMode</code> property
     * @see #setScrollMode
     * @since 1.3
     */
    public int getScrollMode() {
        return scrollMode;
    }

    /**
     * Returns <code>true</code> if this viewport is maintaining
     * an offscreen image of its contents.
     *
     * @return <code>true</code> if <code>scrollMode</code> is
     *    <code>BACKINGSTORE_SCROLL_MODE</code>
     *
     * @deprecated As of Java 2 platform v1.3, replaced by
     *             <code>getScrollMode()</code>.
     */
    @Deprecated
    public boolean isBackingStoreEnabled() {
        return scrollMode == BACKINGSTORE_SCROLL_MODE;
    }

    /**
     * If true if this viewport will maintain an offscreen
     * image of its contents.  The image is used to reduce the cost
     * of successive one dimensional scrolls.  On Android the image is
     * never allocated: the host repaints the whole view each frame.
     *
     * @param enabled if true, maintain an offscreen backing store
     *
     * @deprecated As of Java 2 platform v1.3, replaced by
     *             <code>setScrollMode()</code>.
     */
    @Deprecated
    public void setBackingStoreEnabled(boolean enabled) {
        if (enabled) {
            setScrollMode(BACKINGSTORE_SCROLL_MODE);
        } else {
            setScrollMode(BLIT_SCROLL_MODE);
        }
    }

    /**
     * Returns the <code>JViewport</code>'s one child or <code>null</code>.
     *
     * @return the viewports child, or <code>null</code> if none exists
     *
     * @see #setView
     */
    public Component getView() {
        return (getComponentCount() > 0) ? getComponent(0) : null;
    }

    /**
     * Sets the <code>JViewport</code>'s one lightweight child
     * (<code>view</code>), which can be <code>null</code>.
     *
     * @param view the viewport's new lightweight child
     *
     * @see #getView
     */
    public void setView(Component view) {

        /* Remove the viewport's existing children, if any.
         * Note that removeAll() isn't used here because it
         * doesn't call remove() (which JViewport overrides).
         */
        int n = getComponentCount();
        for (int i = n - 1; i >= 0; i--) {
            remove(getComponent(i));
        }

        isViewSizeSet = false;

        if (view != null) {
            super.addImpl(view, null, -1);
            viewListener = createViewListener();
            view.addComponentListener(viewListener);
        }

        if (hasHadValidView) {
            // Only fire a change if a view has been installed.
            fireStateChanged();
        } else if (view != null) {
            hasHadValidView = true;
        }

        viewChanged = true;

        revalidate();
        repaint();
    }

    /**
     * If the view's size hasn't been explicitly set, return the
     * preferred size, otherwise return the view's current size.
     * If there is no view, return 0,0.
     *
     * @return a <code>Dimension</code> object specifying the size of the view
     */
    public Dimension getViewSize() {
        Component view = getView();

        if (view == null) {
            return new Dimension(0, 0);
        } else if (isViewSizeSet) {
            return view.getSize();
        } else {
            return view.getPreferredSize();
        }
    }

    /**
     * Sets the size of the view.  A state changed event will be fired.
     *
     * @param newSize a <code>Dimension</code> object specifying the new
     *          size of the view
     */
    public void setViewSize(Dimension newSize) {
        Component view = getView();
        if (view != null) {
            Dimension oldSize = view.getSize();
            if (!newSize.equals(oldSize)) {
                // scrollUnderway will be true if this is invoked as the
                // result of a validate and setViewPosition was previously
                // invoked.
                scrollUnderway = false;
                view.setSize(newSize);
                isViewSizeSet = true;
                fireStateChanged();
            }
        }
    }

    /**
     * Returns the view coordinates that appear in the upper left
     * hand corner of the viewport, or 0,0 if there's no view.
     *
     * @return a <code>Point</code> object giving the upper left coordinates
     */
    public Point getViewPosition() {
        Component view = getView();
        if (view != null) {
            Point p = view.getLocation();
            p.x = -p.x;
            p.y = -p.y;
            return p;
        } else {
            return new Point(0, 0);
        }
    }

    /**
     * Sets the view coordinates that appear in the upper left
     * hand corner of the viewport, does nothing if there's no view.
     * <p>
     * As in the JDK the position is not clamped to the view; an unchanged
     * position fires nothing, which JScrollPane's scrollbar sync relies on
     * to settle when it echoes a position back.
     *
     * @param p  a <code>Point</code> object giving the upper left coordinates
     */
    public void setViewPosition(Point p) {
        Component view = getView();
        if (view == null) {
            return;
        }

        int oldX = view.getX(), oldY = view.getY();
        int newX = -p.x;
        int newY = -p.y;

        if ((oldX != newX) || (oldY != newY)) {
            // The viewport clips its children to its own bounds (see Window.hostPaint),
            // so the view at negative coordinates slides under it without painting
            // outside. The JDK also revalidates here so ViewportLayout re-clamps; the
            // host layout re-clamps on its own pass, and relaying the scroll pane on
            // every touch-scroll step would re-run the sidebar layout per frame.
            scrollUnderway = true;
            view.setLocation(newX, newY);
            fireStateChanged();
        }
    }

    /**
     * Returns a rectangle whose origin is <code>getViewPosition</code>
     * and size is <code>getExtentSize</code>.
     * This is the visible part of the view, in view coordinates.
     *
     * @return a <code>Rectangle</code> giving the visible part of
     *          the view using view coordinates.
     */
    public Rectangle getViewRect() {
        return new Rectangle(getViewPosition(), getExtentSize());
    }

    /**
     * Returns the size of the visible part of the view in view coordinates.
     *
     * @return a <code>Dimension</code> object giving the size of the view
     */
    public Dimension getExtentSize() {
        return getSize();
    }

    /**
     * Converts a size in pixel coordinates to view coordinates.
     * Subclasses of viewport that support "logical coordinates"
     * will override this method.
     *
     * @param size  a <code>Dimension</code> object using pixel coordinates
     * @return a <code>Dimension</code> object converted to view coordinates
     */
    public Dimension toViewCoordinates(Dimension size) {
        return new Dimension(size);
    }

    /**
     * Converts a point in pixel coordinates to view coordinates.
     * Subclasses of viewport that support "logical coordinates"
     * will override this method.
     *
     * @param p  a <code>Point</code> object using pixel coordinates
     * @return a <code>Point</code> object converted to view coordinates
     */
    public Point toViewCoordinates(Point p) {
        return new Point(p.x, p.y);
    }

    /**
     * Sets the size of the visible part of the view using view coordinates.
     *
     * @param newExtent  a <code>Dimension</code> object specifying
     *          the size of the view
     */
    public void setExtentSize(Dimension newExtent) {
        Dimension oldExtent = getExtentSize();
        if (!newExtent.equals(oldExtent)) {
            setSize(newExtent);
            fireStateChanged();
        }
    }

    /**
     * A listener for the view.
     * <p>
     * <strong>Warning:</strong>
     * Serialized objects of this class will not be compatible with
     * future Swing releases.
     */
    protected class ViewListener extends ComponentAdapter implements Serializable {
        private static final long serialVersionUID = 1L;

        public void componentResized(ComponentEvent e) {
            fireStateChanged();
            revalidate();
        }
    }

    /**
     * Creates a listener for the view.
     *
     * @return a <code>ViewListener</code>
     */
    protected ViewListener createViewListener() {
        return new ViewListener();
    }

    /**
     * Subclassers can override this to install a different
     * layout manager (or <code>null</code>) in the constructor.  Returns
     * the <code>LayoutManager</code> to install on the <code>JViewport</code>.
     *
     * @return a <code>LayoutManager</code>
     */
    protected java.awt.LayoutManager createLayoutManager() {
        return ViewportLayout.SHARED_INSTANCE;
    }

    /**
     * Adds a <code>ChangeListener</code> to the list that is
     * notified each time the view's
     * size, position, or the viewport's extent size has changed.
     *
     * @param l the <code>ChangeListener</code> to add
     * @see #removeChangeListener
     * @see #setViewPosition
     * @see #setViewSize
     * @see #setExtentSize
     */
    public void addChangeListener(ChangeListener l) {
        listenerList.add(ChangeListener.class, l);
    }

    /**
     * Removes a <code>ChangeListener</code> from the list that's notified each
     * time the views size, position, or the viewports extent size
     * has changed.
     *
     * @param l the <code>ChangeListener</code> to remove
     * @see #addChangeListener
     */
    public void removeChangeListener(ChangeListener l) {
        listenerList.remove(ChangeListener.class, l);
    }

    /**
     * Returns an array of all the <code>ChangeListener</code>s added
     * to this JViewport with addChangeListener().
     *
     * @return all of the <code>ChangeListener</code>s added or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(ChangeListener.class);
    }

    /**
     * Notifies all <code>ChangeListeners</code> when the views
     * size, position, or the viewports extent size has changed.
     * Listeners are notified last registered first, as in the JDK.
     *
     * @see #addChangeListener
     * @see #removeChangeListener
     * @see EventListenerList
     */
    protected void fireStateChanged() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ChangeListener.class) {
                if (changeEvent == null) {
                    changeEvent = new ChangeEvent(this);
                }
                ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }

    /**
     * Returns a string representation of this <code>JViewport</code>.
     *
     * @return  a string representation of this <code>JViewport</code>
     */
    protected String paramString() {
        String isViewSizeSetString = (isViewSizeSet ? "true" : "false");
        String lastPaintPositionString = (lastPaintPosition != null
                ? lastPaintPosition.toString() : "");
        String scrollUnderwayString = (scrollUnderway ? "true" : "false");

        return super.paramString()
                + ",isViewSizeSet=" + isViewSizeSetString
                + ",lastPaintPosition=" + lastPaintPositionString
                + ",scrollUnderway=" + scrollUnderwayString;
    }

    // Android host additions -------------------------------------------------

    /**
     * Scrolls by (dx, dy) in view coordinates, clamped to the view's bounds; the
     * panel host's touch drag and fling, and the scroll pane's wheel handler.
     */
    public void scrollBy(int dx, int dy) {
        Point p = getViewPosition();
        setViewPosition(new Point(clampX(p.x + dx), clampY(p.y + dy)));
    }

    private int clampX(int x) {
        Component view = getView();
        if (view == null) return 0;
        int max = Math.max(0, view.getWidth() - getWidth());
        return Math.max(0, Math.min(x, max));
    }

    private int clampY(int y) {
        Component view = getView();
        if (view == null) return 0;
        int max = Math.max(0, view.getHeight() - getHeight());
        return Math.max(0, Math.min(y, max));
    }

    /**
     * The host sizing the shared {@link ViewportLayout} applies: the view tracks the
     * viewport width unless it is a Scrollable that says otherwise, so sidebar panels
     * wrap instead of scrolling sideways.
     */
    void layoutHostView() {
        Component view = getView();
        if (view != null && getWidth() > 0 && getHeight() > 0) {
            // Size the view to its preferred height (or at least viewport height) so
            // scrollable content can grow; horizontal stays clamped to viewport.
            Dimension pref = view.getPreferredSize();
            int h = Math.max(getHeight(), pref.height);
            boolean tracksWidth = !(view instanceof Scrollable) || ((Scrollable) view).getScrollableTracksViewportWidth();
            boolean tracksHeight = view instanceof Scrollable && ((Scrollable) view).getScrollableTracksViewportHeight();
            boolean wideTable = view instanceof JTable || view instanceof javax.swing.table.JTableHeader;
            view.setSize(tracksWidth && !wideTable ? getWidth() : Math.max(getWidth(), pref.width),
                tracksHeight ? getHeight() : h);
            isViewSizeSet = true;
            // Re-clamp in case the view shrunk. Always fires: the extent or view size
            // may have changed even when the position didn't, and the scroll pane's
            // scrollbars track all three.
            Point p = getViewPosition();
            view.setLocation(-clampX(p.x), -clampY(p.y));
            fireStateChanged();
        }
    }

    // Accessibility -----------------------------------------------------------

    /**
     * Gets the AccessibleContext associated with this JViewport.
     * For viewports, the AccessibleContext takes the form of an
     * AccessibleJViewport.
     *
     * @return an AccessibleJViewport that serves as the
     *         AccessibleContext of this JViewport
     */
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJViewport();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>JViewport</code> class.  It provides an implementation of the
     * Java Accessibility API appropriate to viewport user-interface elements.
     */
    protected class AccessibleJViewport extends AccessibleJComponent {
        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of
         * the object
         */
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.VIEWPORT;
        }
    }
}
