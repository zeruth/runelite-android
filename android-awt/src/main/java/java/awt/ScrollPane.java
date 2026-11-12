/*
 * Copyright (c) 1996, 2014, Oracle and/or its affiliates. All rights reserved.
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
package java.awt;

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import net.runelite.awt.impl.AwtWidgets;

/**
 * A container class which implements automatic horizontal and/or
 * vertical scrolling for a single child component.  The display
 * policy for the scrollbars can be set to:
 * <OL>
 * <LI>as needed: scrollbars created and shown only when needed by scrollpane
 * <LI>always: scrollbars created and always shown by the scrollpane
 * <LI>never: scrollbars never created or shown by the scrollpane
 * </OL>
 * <P>
 * The state of the horizontal and vertical scrollbars is represented
 * by two <code>ScrollPaneAdjustable</code> objects (one for each
 * dimension) which implement the <code>Adjustable</code> interface.
 * <P>
 * There is no native peer on Android: the pane positions its child at the
 * negated scroll position, clips it to the viewport and paints its own
 * scrollbars; the initial size is 100x100, as on the JDK.
 *
 * @author      Tom Ball
 * @author      Amy Fowler
 * @author      Tim Prinzing
 */
public class ScrollPane extends Container {

    /**
     * Specifies that horizontal/vertical scrollbar should be shown
     * only when the size of the child exceeds the size of the scrollpane
     * in the horizontal/vertical dimension.
     */
    public static final int SCROLLBARS_AS_NEEDED = 0;

    /**
     * Specifies that horizontal/vertical scrollbars should always be
     * shown regardless of the respective sizes of the scrollpane and child.
     */
    public static final int SCROLLBARS_ALWAYS = 1;

    /**
     * Specifies that horizontal/vertical scrollbars should never be shown
     * regardless of the respective sizes of the scrollpane and child.
     */
    public static final int SCROLLBARS_NEVER = 2;

    /**
     * There are 3 ways in which a scroll bar can be displayed.
     * This integer will represent one of these 3 displays -
     * (SCROLLBARS_ALWAYS, SCROLLBARS_AS_NEEDED, SCROLLBARS_NEVER)
     *
     * @serial
     * @see #getScrollbarDisplayPolicy
     */
    private int scrollbarDisplayPolicy;

    /**
     * An adjustable vertical scrollbar.
     * It is important to note that you must <em>NOT</em> call 3
     * <code>Adjustable</code> methods, namely:
     * <code>setMinimum()</code>, <code>setMaximum()</code>,
     * <code>setVisibleAmount()</code>.
     *
     * @serial
     * @see #getVAdjustable
     */
    private ScrollPaneAdjustable vAdjustable;

    /**
     * An adjustable horizontal scrollbar.
     * It is important to note that you must <em>NOT</em> call 3
     * <code>Adjustable</code> methods, namely:
     * <code>setMinimum()</code>, <code>setMaximum()</code>,
     * <code>setVisibleAmount()</code>.
     *
     * @serial
     * @see #getHAdjustable
     */
    private ScrollPaneAdjustable hAdjustable;

    private static final String base = "scrollpane";
    private static int nameCounter = 0;

    private static final boolean defaultWheelScroll = true;

    /**
     * Indicates whether or not scrolling should take place when a
     * MouseWheelEvent is received.
     *
     * @serial
     * @since 1.4
     */
    private boolean wheelScrollingEnabled = defaultWheelScroll;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = 7956609840827222915L;

    /** The thumb grab offset and orientation of a scrollbar drag in progress. */
    private transient int dragOffset = -1, dragOrientation;

    /**
     * Create a new scrollpane container with a scrollbar display
     * policy of "as needed".
     */
    public ScrollPane() {
        this(SCROLLBARS_AS_NEEDED);
    }

    /**
     * Create a new scrollpane container.
     * @param scrollbarDisplayPolicy policy for when scrollbars should be shown
     * @throws IllegalArgumentException if the specified scrollbar
     *     display policy is invalid
     */
    public ScrollPane(int scrollbarDisplayPolicy) {
        this.width = 100;
        this.height = 100;
        switch (scrollbarDisplayPolicy) {
            case SCROLLBARS_NEVER:
            case SCROLLBARS_AS_NEEDED:
            case SCROLLBARS_ALWAYS:
                this.scrollbarDisplayPolicy = scrollbarDisplayPolicy;
                break;
            default:
                throw new IllegalArgumentException("illegal scrollbar display policy");
        }

        vAdjustable = new ScrollPaneAdjustable(this, new PeerFixer(this),
                                               Adjustable.VERTICAL);
        hAdjustable = new ScrollPaneAdjustable(this, new PeerFixer(this),
                                               Adjustable.HORIZONTAL);
        setWheelScrollingEnabled(defaultWheelScroll);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                     | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    /**
     * Construct a name for this component.  Called by getName() when the
     * name is null.
     */
    String constructComponentName() {
        synchronized (ScrollPane.class) {
            return base + nameCounter++;
        }
    }

    /**
     * A scroll pane holds one child: adding another replaces it, and only
     * position 0 exists, as the JDK's addImpl enforces.
     */
    private void replaceChild(Component comp, int index) {
        if (index > 0) {
            throw new IllegalArgumentException("position greater than 0");
        }
        if (getComponentCount() > 0 && getComponent(0) != comp) {
            remove(0);
        }
    }

    @Override
    public Component add(Component comp, int index) {
        if (comp == null) return null;
        synchronized (getTreeLock()) {
            replaceChild(comp, index);
            return super.add(comp, index);
        }
    }

    @Override
    public void add(Component comp, Object constraints) {
        if (comp == null) return;
        synchronized (getTreeLock()) {
            replaceChild(comp, -1);
            super.add(comp, constraints);
        }
    }

    @Override
    public void add(Component comp, Object constraints, int index) {
        synchronized (getTreeLock()) {
            if (comp != null) replaceChild(comp, index);
            super.add(comp, constraints, index);
        }
    }

    /**
     * Returns the display policy for the scrollbars.
     * @return the display policy for the scrollbars
     */
    public int getScrollbarDisplayPolicy() {
        return scrollbarDisplayPolicy;
    }

    /**
     * Returns the current size of the scroll pane's view port.
     * @return the size of the view port in pixels
     */
    public Dimension getViewportSize() {
        Insets i = getInsets();
        return new Dimension(width - i.right - i.left,
                             height - i.top - i.bottom);
    }

    /**
     * Returns the height that would be occupied by a horizontal
     * scrollbar, which is independent of whether it is currently
     * displayed by the scroll pane or not.
     * @return the height of a horizontal scrollbar in pixels
     */
    public int getHScrollbarHeight() {
        return scrollbarDisplayPolicy != SCROLLBARS_NEVER ? AwtWidgets.SCROLLBAR_THICKNESS : 0;
    }

    /**
     * Returns the width that would be occupied by a vertical
     * scrollbar, which is independent of whether it is currently
     * displayed by the scroll pane or not.
     * @return the width of a vertical scrollbar in pixels
     */
    public int getVScrollbarWidth() {
        return scrollbarDisplayPolicy != SCROLLBARS_NEVER ? AwtWidgets.SCROLLBAR_THICKNESS : 0;
    }

    /**
     * Returns the <code>ScrollPaneAdjustable</code> object which
     * represents the state of the vertical scrollbar.
     * The declared return type of this method is
     * <code>Adjustable</code> to maintain backward compatibility.
     * @see java.awt.ScrollPaneAdjustable
     */
    public Adjustable getVAdjustable() {
        return vAdjustable;
    }

    /**
     * Returns the <code>ScrollPaneAdjustable</code> object which
     * represents the state of the horizontal scrollbar.
     * The declared return type of this method is
     * <code>Adjustable</code> to maintain backward compatibility.
     * @see java.awt.ScrollPaneAdjustable
     */
    public Adjustable getHAdjustable() {
        return hAdjustable;
    }

    /**
     * Scrolls to the specified position within the child component.
     * A call to this method is only valid if the scroll pane contains
     * a child.  Specifying a position outside of the legal scrolling bounds
     * of the child will scroll to the closest legal position.
     * Legal bounds are defined to be the rectangle:
     * x = 0, y = 0, width = (child width - view port width),
     * height = (child height - view port height).
     *
     * @param x the x position to scroll to
     * @param y the y position to scroll to
     * @throws NullPointerException if the scrollpane does not contain
     *     a child
     */
    public void setScrollPosition(int x, int y) {
        synchronized (getTreeLock()) {
            if (getComponentCount()==0) {
                throw new NullPointerException("child is null");
            }
            hAdjustable.setValue(x);
            vAdjustable.setValue(y);
        }
    }

    /**
     * Scrolls to the specified position within the child component.
     *
     * @param p the Point representing the position to scroll to
     * @throws NullPointerException if {@code p} is {@code null}
     */
    public void setScrollPosition(Point p) {
        setScrollPosition(p.x, p.y);
    }

    /**
     * Returns the current x,y position within the child which is displayed
     * at the 0,0 location of the scrolled panel's view port.
     * @return the coordinate position for the current scroll position
     * @throws NullPointerException if the scrollpane does not contain
     *     a child
     */
    public Point getScrollPosition() {
        synchronized (getTreeLock()) {
            if (getComponentCount()==0) {
                throw new NullPointerException("child is null");
            }
            return new Point(hAdjustable.getValue(), vAdjustable.getValue());
        }
    }

    /**
     * Sets the layout manager for this container.  This method is
     * overridden to prevent the layout mgr from being set.
     * @param mgr the specified layout manager
     */
    public final void setLayout(LayoutManager mgr) {
        throw new AWTError("ScrollPane controls layout");
    }

    /**
     * The scrollbar space currently taken: a bar is shown when the policy says
     * always, or as needed when the child's preferred size overflows the pane.
     */
    @Override
    public Insets getInsets() {
        boolean[] bars = barsShown();
        return new Insets(0, 0, bars[0] ? getHScrollbarHeight() : 0, bars[1] ? getVScrollbarWidth() : 0);
    }

    /** {horizontal shown, vertical shown}, by the rule of the JDK's calculateChildSize. */
    private boolean[] barsShown() {
        switch (scrollbarDisplayPolicy) {
            case SCROLLBARS_ALWAYS:
                return new boolean[] {true, true};
            case SCROLLBARS_NEVER:
                return new boolean[] {false, false};
            default:
                if (getComponentCount() == 0) return new boolean[] {false, false};
                Dimension childSize = getComponent(0).getPreferredSize();
                return new boolean[] {childSize.width > width, childSize.height > height};
        }
    }

    /*
     * Internal function to calculate the size of the child.
     */
    private Dimension calculateChildSize() {
        //
        // calculate the view size, accounting for border but not scrollbars
        // - don't use right/bottom insets since they vary depending
        //   on whether or not scrollbars were displayed on last resize
        //
        Dimension childSize = new Dimension(getComponent(0).getPreferredSize());
        boolean[] bars = barsShown();
        int viewWidth = width - (bars[1] ? getVScrollbarWidth() : 0);
        int viewHeight = height - (bars[0] ? getHScrollbarHeight() : 0);

        //
        // if child is smaller than view, size it up
        //
        if (childSize.width < viewWidth) {
            childSize.width = viewWidth;
        }
        if (childSize.height < viewHeight) {
            childSize.height = viewHeight;
        }

        return childSize;
    }

    /**
     * Lays out this container by resizing its child to its preferred size.
     * If the new preferred size of the child causes the current scroll
     * position to be invalid, the scroll position is set to the closest
     * valid position.
     *
     * @see Component#validate
     */
    public void doLayout() {
        layout();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>doLayout()</code>.
     */
    @Deprecated
    public void layout() {
        if (getComponentCount()==0) {
            return;
        }
        Component c = getComponent(0);
        Dimension cs = calculateChildSize();
        Dimension vs = getViewportSize();

        hAdjustable.setSpan(0, cs.width, vs.width);
        vAdjustable.setSpan(0, cs.height, vs.height);
        c.setBounds(-hAdjustable.getValue(), -vAdjustable.getValue(), cs.width, cs.height);
    }

    /** What the peer did on an adjustment: move the child to the new scroll position. */
    void scrollChild() {
        synchronized (getTreeLock()) {
            if (getComponentCount() == 0) return;
            getComponent(0).setLocation(-hAdjustable.getValue(), -vAdjustable.getValue());
        }
        repaint();
    }

    /**
     * Prints the component in this scroll pane.
     * @param g the specified Graphics window
     * @see Component#print
     * @see Component#printAll
     */
    public void printComponents(Graphics g) {
        if (getComponentCount()==0) {
            return;
        }
        Component c = getComponent(0);
        Point p = c.getLocation();
        Dimension vs = getViewportSize();
        Graphics cg = g.create();
        try {
            cg.clipRect(0, 0, vs.width, vs.height);
            cg.translate(p.x, p.y);
            c.paint(cg);
        } finally {
            cg.dispose();
        }
    }

    /** The child is clipped to the viewport; the scrollbars are drawn in the insets. */
    @Override
    public void paint(Graphics g) {
        Dimension vs = getViewportSize();
        Graphics viewport = g.create(0, 0, Math.max(0, vs.width), Math.max(0, vs.height));
        try {
            super.paint(viewport);
        } finally {
            viewport.dispose();
        }
        boolean[] bars = barsShown();
        if (bars[0]) paintBar(g, hAdjustable, 0, vs.height, vs.width, getHScrollbarHeight());
        if (bars[1]) paintBar(g, vAdjustable, vs.width, 0, getVScrollbarWidth(), vs.height);
        if (bars[0] && bars[1]) {
            g.setColor(AwtWidgets.well(this));
            g.fillRect(vs.width, vs.height, getVScrollbarWidth(), getHScrollbarHeight());
        }
    }

    private void paintBar(Graphics g, ScrollPaneAdjustable bar, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        Color well = AwtWidgets.well(this), face = AwtWidgets.face(this);
        g.setColor(well);
        g.fillRect(x, y, w, h);
        boolean vertical = bar.getOrientation() == Adjustable.VERTICAL;
        int[] thumb = thumb(bar, vertical ? h : w);
        if (vertical) {
            g.setColor(face);
            g.fillRect(x + 1, y + thumb[0], w - 2, thumb[1]);
            AwtWidgets.bevel(g, face, x + 1, y + thumb[0], w - 2, thumb[1], true);
        } else {
            g.setColor(face);
            g.fillRect(x + thumb[0], y + 1, thumb[1], h - 2);
            AwtWidgets.bevel(g, face, x + thumb[0], y + 1, thumb[1], h - 2, true);
        }
    }

    /** {start, length} of the thumb along a bar track of {@code track} pixels. */
    private static int[] thumb(ScrollPaneAdjustable bar, int track) {
        int range = Math.max(1, bar.getMaximum() - bar.getMinimum());
        int size = Math.min(track, Math.max(8, (int) ((long) track * bar.getVisibleAmount() / range)));
        int travel = range - bar.getVisibleAmount();
        int start = travel <= 0 ? 0 : (int) ((long) (track - size) * bar.getValue() / travel);
        return new int[] {start, size};
    }

    /** The bar under (x, y), or null when the point is in the viewport. */
    private ScrollPaneAdjustable barAt(int x, int y) {
        Dimension vs = getViewportSize();
        if (x >= vs.width && y < vs.height) return vAdjustable;
        if (y >= vs.height && x < vs.width) return hAdjustable;
        return null;
    }

    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (e.getButton() != MouseEvent.BUTTON1 || !isEnabled()) return;
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            ScrollPaneAdjustable bar = barAt(e.getX(), e.getY());
            if (bar == null) return;
            boolean vertical = bar.getOrientation() == Adjustable.VERTICAL;
            Dimension vs = getViewportSize();
            int at = vertical ? e.getY() : e.getX();
            int[] thumb = thumb(bar, vertical ? vs.height : vs.width);
            if (at < thumb[0]) {
                bar.userAdjust(bar.getValue() - bar.getBlockIncrement(), AdjustmentEvent.BLOCK_DECREMENT);
            } else if (at >= thumb[0] + thumb[1]) {
                bar.userAdjust(bar.getValue() + bar.getBlockIncrement(), AdjustmentEvent.BLOCK_INCREMENT);
            } else {
                dragOffset = at - thumb[0];
                dragOrientation = bar.getOrientation();
                bar.setValueIsAdjusting(true);
            }
        } else if (e.getID() == MouseEvent.MOUSE_RELEASED && dragOffset >= 0) {
            dragOffset = -1;
            (dragOrientation == Adjustable.VERTICAL ? vAdjustable : hAdjustable).setValueIsAdjusting(false);
        }
    }

    protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
        if (e.getID() != MouseEvent.MOUSE_DRAGGED || dragOffset < 0) return;
        boolean vertical = dragOrientation == Adjustable.VERTICAL;
        ScrollPaneAdjustable bar = vertical ? vAdjustable : hAdjustable;
        Dimension vs = getViewportSize();
        int track = vertical ? vs.height : vs.width;
        int[] thumb = thumb(bar, track);
        int span = Math.max(1, track - thumb[1]);
        long travel = (long) bar.getMaximum() - bar.getVisibleAmount();
        int at = vertical ? e.getY() : e.getX();
        bar.userAdjust((int) (travel * (at - dragOffset) / span), AdjustmentEvent.TRACK);
    }

    /**
     * Process mouse wheel events that are delivered to this
     * <code>ScrollPane</code> by scrolling an appropriate amount.
     * <p>Note that if the event parameter is <code>null</code>
     * the behavior is unspecified and may result in an
     * exception.
     *
     * @param e  the mouse wheel event
     * @since 1.4
     */
    protected void processMouseWheelEvent(MouseWheelEvent e) {
        if (isWheelScrollingEnabled() && getComponentCount() > 0) {
            // ScrollPaneWheelScroller: the vertical bar when it can move, else the horizontal.
            ScrollPaneAdjustable bar = vAdjustable.getVisibleAmount() < vAdjustable.getMaximum()
                ? vAdjustable : hAdjustable;
            int units = e.getUnitsToScroll();
            if (units != 0) {
                bar.userAdjust(bar.getValue() + units * bar.getUnitIncrement(),
                               units < 0 ? AdjustmentEvent.UNIT_DECREMENT : AdjustmentEvent.UNIT_INCREMENT);
            }
            e.consume();
        }
        super.processMouseWheelEvent(e);
    }

    /**
     * Enables/disables scrolling in response to movement of the mouse wheel.
     * Wheel scrolling is enabled by default.
     *
     * @param handleWheel   <code>true</code> if scrolling should be done
     *                      automatically for a MouseWheelEvent,
     *                      <code>false</code> otherwise.
     * @see #isWheelScrollingEnabled
     * @see java.awt.event.MouseWheelEvent
     * @see java.awt.event.MouseWheelListener
     * @since 1.4
     */
    public void setWheelScrollingEnabled(boolean handleWheel) {
        wheelScrollingEnabled = handleWheel;
    }

    /**
     * Indicates whether or not scrolling will take place in response to
     * the mouse wheel.  Wheel scrolling is enabled by default.
     *
     * @return {@code true} if the wheel scrolls this pane
     * @see #setWheelScrollingEnabled(boolean)
     * @since 1.4
     */
    public boolean isWheelScrollingEnabled() {
        return wheelScrollingEnabled;
    }

    /**
     * Returns a string representing the state of this
     * <code>ScrollPane</code>. This
     * method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return    the parameter string of this scroll pane
     */
    public String paramString() {
        String sdpStr;
        switch (scrollbarDisplayPolicy) {
            case SCROLLBARS_AS_NEEDED:
                sdpStr = "as-needed";
                break;
            case SCROLLBARS_ALWAYS:
                sdpStr = "always";
                break;
            case SCROLLBARS_NEVER:
                sdpStr = "never";
                break;
            default:
                sdpStr = "invalid display policy";
        }
        Point p = (getComponentCount()>0)? getScrollPosition() : new Point(0,0);
        Insets i = getInsets();
        return super.paramString()+",ScrollPosition=("+p.x+","+p.y+")"+
            ",Insets=("+i.top+","+i.left+","+i.bottom+","+i.right+")"+
            ",ScrollbarDisplayPolicy="+sdpStr+
        ",wheelScrollingEnabled="+isWheelScrollingEnabled();
    }

    /**
     * The scroll position follows the adjustables: whichever way a value changes,
     * programmatically or by a user gesture, the child moves with it.
     */
    class PeerFixer implements AdjustmentListener, java.io.Serializable {
        private static final long serialVersionUID = 1043664721353696630L;

        PeerFixer(ScrollPane scroller) {
            this.scroller = scroller;
        }

        /**
         * Invoked when the value of the adjustable has changed.
         */
        public void adjustmentValueChanged(AdjustmentEvent e) {
            scroller.scrollChild();
        }

        private ScrollPane scroller;
    }
}
