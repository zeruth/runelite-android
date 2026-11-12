/*
 * Copyright (c) 1995, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.util.EventListener;
import net.runelite.awt.impl.AwtWidgets;
import net.runelite.awt.impl.ListenerChain;

/**
 * The <code>Scrollbar</code> class embodies a scroll bar, a
 * familiar user-interface object. A scroll bar provides a
 * convenient means for allowing a user to select from a
 * range of values.
 * <p>
 * A press on an arrow sends an <code>AdjustmentEvent</code> of type
 * <code>UNIT_DECREMENT</code>/<code>UNIT_INCREMENT</code>, a press on the
 * track one of type <code>BLOCK_DECREMENT</code>/<code>BLOCK_INCREMENT</code>,
 * and dragging the thumb sends <code>TRACK</code> events with
 * <code>getValueIsAdjusting()</code> true until the release. Calls to
 * <code>setValue</code> and friends send no events, as on the JDK. There is
 * no native peer on Android: the scroll bar paints itself.
 *
 * @author      Sami Shaio
 * @see         java.awt.event.AdjustmentEvent
 * @see         java.awt.event.AdjustmentListener
 * @since       JDK1.0
 */
public class Scrollbar extends Component implements Adjustable {

    /**
     * A constant that indicates a horizontal scroll bar.
     */
    public static final int     HORIZONTAL = 0;

    /**
     * A constant that indicates a vertical scroll bar.
     */
    public static final int     VERTICAL   = 1;

    /**
     * The value of the <code>Scrollbar</code>.
     * @serial
     */
    int value;

    /**
     * The maximum value of the <code>Scrollbar</code>.
     * @serial
     */
    int maximum;

    /**
     * The minimum value of the <code>Scrollbar</code>.
     * @serial
     */
    int minimum;

    /**
     * The size of the <code>Scrollbar</code>'s bubble.
     * @serial
     */
    int visibleAmount;

    /**
     * The <code>Scrollbar</code>'s orientation--being either horizontal
     * or vertical.
     * @serial
     */
    int orientation;

    /**
     * The amount by which the scrollbar value will change when going
     * up or down by a line.
     * @serial
     */
    int lineIncrement = 1;

    /**
     * The amount by which the scrollbar value will change when going
     * up or down by a page.
     * @serial
     */
    int pageIncrement = 10;

    /**
     * The adjusting status of the <code>Scrollbar</code>.
     * True if the value is in the process of changing as a result of
     * actions being taken by the user.
     */
    transient boolean isAdjusting;

    transient AdjustmentListener adjustmentListener;

    private static final String base = "scrollbar";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = 8451667562882310543L;

    /** The thumb grab offset of a drag in progress, or -1. */
    private transient int dragOffset = -1;

    /**
     * Constructs a new vertical scroll bar.
     * The default properties of the scroll bar are listed in
     * the following table: minimum 0, maximum 100, value 0, visible amount
     * 10, unit increment 1, block increment 10.
     */
    public Scrollbar() {
        this(VERTICAL, 0, 10, 0, 100);
    }

    /**
     * Constructs a new scroll bar with the specified orientation.
     * <p>
     * The <code>orientation</code> argument must take one of the two
     * values <code>Scrollbar.HORIZONTAL</code>,
     * or <code>Scrollbar.VERTICAL</code>,
     * indicating a horizontal or vertical scroll bar, respectively.
     *
     * @param       orientation   indicates the orientation of the scroll bar
     * @exception   IllegalArgumentException    when an illegal value for
     *                    the <code>orientation</code> argument is supplied
     */
    public Scrollbar(int orientation) {
        this(orientation, 0, 10, 0, 100);
    }

    /**
     * Constructs a new scroll bar with the specified orientation,
     * initial value, visible amount, and minimum and maximum values.
     * <p>
     * If the specified maximum value is less than the minimum value, it
     * is changed to be the same as the minimum value. If the initial
     * value is lower than the minimum value, it is changed to be the
     * minimum value; if it is greater than the maximum value, it is
     * changed to be the maximum value.
     *
     * @param     orientation   indicates the orientation of the scroll bar.
     * @param     value     the initial value of the scroll bar
     * @param     visible   the visible amount of the scroll bar, typically
     *                      represented by the size of the bubble
     * @param     minimum   the minimum value of the scroll bar
     * @param     maximum   the maximum value of the scroll bar
     * @exception IllegalArgumentException    when an illegal value for
     *                    the <code>orientation</code> argument is supplied
     * @see #setValues
     */
    public Scrollbar(int orientation, int value, int visible, int minimum,
        int maximum) {
        switch (orientation) {
          case HORIZONTAL:
          case VERTICAL:
            this.orientation = orientation;
            break;
          default:
            throw new IllegalArgumentException("illegal scrollbar orientation");
        }
        setValues(value, visible, minimum, maximum);
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                     | AWTEvent.MOUSE_WHEEL_EVENT_MASK | AWTEvent.ADJUSTMENT_EVENT_MASK);
    }

    /**
     * Constructs a name for this component.  Called by <code>getName</code>
     * when the name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (Scrollbar.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Returns the orientation of this scroll bar.
     *
     * @return    the orientation of this scroll bar, either
     *               <code>Scrollbar.HORIZONTAL</code> or
     *               <code>Scrollbar.VERTICAL</code>
     * @see       java.awt.Scrollbar#setOrientation
     */
    public int getOrientation() {
        return orientation;
    }

    /**
     * Sets the orientation for this scroll bar.
     *
     * @param orientation  the orientation of this scroll bar, either
     *               <code>Scrollbar.HORIZONTAL</code> or
     *               <code>Scrollbar.VERTICAL</code>
     * @see       java.awt.Scrollbar#getOrientation
     * @exception   IllegalArgumentException  if the value supplied
     *                   for <code>orientation</code> is not a
     *                   legal value
     * @since     JDK1.1
     */
    public void setOrientation(int orientation) {
        synchronized (getTreeLock()) {
            if (orientation == this.orientation) {
                return;
            }
            switch (orientation) {
                case HORIZONTAL:
                case VERTICAL:
                    this.orientation = orientation;
                    break;
                default:
                    throw new IllegalArgumentException("illegal scrollbar orientation");
            }
        }
        invalidate();
        repaint();
    }

    /**
     * Gets the current value of this scroll bar.
     *
     * @return      the current value of this scroll bar
     * @see         java.awt.Scrollbar#getMinimum
     * @see         java.awt.Scrollbar#getMaximum
     */
    public int getValue() {
        return value;
    }

    /**
     * Sets the value of this scroll bar to the specified value.
     * <p>
     * If the value supplied is less than the current <code>minimum</code>
     * or greater than the current <code>maximum - visibleAmount</code>,
     * then either <code>minimum</code> or <code>maximum - visibleAmount</code>
     * is substituted, as appropriate.
     * <p>
     * Calling this method does not fire an
     * <code>AdjustmentEvent</code>.
     *
     * @param       newValue   the new value of the scroll bar
     * @see         java.awt.Scrollbar#setValues
     */
    public void setValue(int newValue) {
        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(newValue, visibleAmount, minimum, maximum);
    }

    /**
     * Gets the minimum value of this scroll bar.
     *
     * @return      the minimum value of this scroll bar
     */
    public int getMinimum() {
        return minimum;
    }

    /**
     * Sets the minimum value of this scroll bar.
     * <p>
     * When <code>setMinimum</code> is called, the minimum value
     * is changed, and other values (including the maximum, the
     * visible amount, and the current scroll bar value)
     * are changed to be consistent with the new minimum.
     *
     * @param       newMinimum   the new minimum value for this scroll bar
     * @since       JDK1.1
     */
    public void setMinimum(int newMinimum) {
        // No checks are necessary in this method since minimum is
        // the first variable checked in the setValues function.

        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(value, visibleAmount, newMinimum, maximum);
    }

    /**
     * Gets the maximum value of this scroll bar.
     *
     * @return      the maximum value of this scroll bar
     */
    public int getMaximum() {
        return maximum;
    }

    /**
     * Sets the maximum value of this scroll bar.
     * <p>
     * When <code>setMaximum</code> is called, the maximum value
     * is changed, and other values (including the minimum, the
     * visible amount, and the current scroll bar value)
     * are changed to be consistent with the new maximum.
     *
     * @param       newMaximum   the new maximum value
     *                     for this scroll bar
     * @since       JDK1.1
     */
    public void setMaximum(int newMaximum) {
        // minimum is checked first in setValues, so we need to
        // enforce minimum and maximum checks here.
        if (newMaximum == Integer.MIN_VALUE) {
            newMaximum = Integer.MIN_VALUE + 1;
        }

        if (minimum >= newMaximum) {
            minimum = newMaximum - 1;
        }

        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(value, visibleAmount, minimum, newMaximum);
    }

    /**
     * Gets the visible amount of this scroll bar.
     *
     * @return      the visible amount of this scroll bar
     * @since       JDK1.1
     */
    public int getVisibleAmount() {
        return getVisible();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getVisibleAmount()</code>.
     */
    @Deprecated
    public int getVisible() {
        return visibleAmount;
    }

    /**
     * Sets the visible amount of this scroll bar.
     * <p>
     * If the visible amount is less than one or greater than the
     * current <code>maximum - minimum</code>, then either one or
     * <code>maximum - minimum</code> is substituted, as appropriate.
     *
     * @param       newAmount the new visible amount
     * @since       JDK1.1
     */
    public void setVisibleAmount(int newAmount) {
        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(value, newAmount, minimum, maximum);
    }

    /**
     * Sets the unit increment for this scroll bar.
     * <p>
     * The unit increment is the value that is added or subtracted
     * when the user activates the unit increment area of the
     * scroll bar, generally through a mouse or keyboard gesture
     * that the scroll bar receives as an adjustment event.
     * The unit increment must be greater than zero.
     * Attepts to set the unit increment to a value lower than 1
     * will result in a value of 1 being set.
     *
     * @param        v  the amount by which to increment or decrement
     *                         the scroll bar's value
     * @since        JDK1.1
     */
    public void setUnitIncrement(int v) {
        setLineIncrement(v);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>setUnitIncrement(int)</code>.
     */
    @Deprecated
    public synchronized void setLineIncrement(int v) {
        int tmp = (v < 1) ? 1 : v;

        if (lineIncrement == tmp) {
            return;
        }
        lineIncrement = tmp;
    }

    /**
     * Gets the unit increment for this scrollbar.
     *
     * @return      the unit increment of this scroll bar
     * @since       JDK1.1
     */
    public int getUnitIncrement() {
        return getLineIncrement();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getUnitIncrement()</code>.
     */
    @Deprecated
    public int getLineIncrement() {
        return lineIncrement;
    }

    /**
     * Sets the block increment for this scroll bar.
     * <p>
     * The block increment is the value that is added or subtracted
     * when the user activates the block increment area of the
     * scroll bar. The block increment must be greater than zero.
     * Attepts to set the block increment to a value lower than 1
     * will result in a value of 1 being set.
     *
     * @param        v  the amount by which to increment or decrement
     *                         the scroll bar's value
     * @since        JDK1.1
     */
    public void setBlockIncrement(int v) {
        setPageIncrement(v);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>setBlockIncrement()</code>.
     */
    @Deprecated
    public synchronized void setPageIncrement(int v) {
        int tmp = (v < 1) ? 1 : v;

        if (pageIncrement == tmp) {
            return;
        }
        pageIncrement = tmp;
    }

    /**
     * Gets the block increment of this scroll bar.
     *
     * @return      the block increment of this scroll bar
     * @since       JDK1.1
     */
    public int getBlockIncrement() {
        return getPageIncrement();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getBlockIncrement()</code>.
     */
    @Deprecated
    public int getPageIncrement() {
        return pageIncrement;
    }

    /**
     * Sets the values of four properties for this scroll bar:
     * <code>value</code>, <code>visibleAmount</code>,
     * <code>minimum</code>, and <code>maximum</code>.
     * If the values supplied for these properties are inconsistent
     * or incorrect, they will be changed to ensure consistency.
     * <p>
     * This method simultaneously and synchronously sets the values
     * of four scroll bar properties, assuring that the values of
     * these properties are mutually consistent. It enforces the
     * following constraints:
     * <code>maximum</code> must be greater than <code>minimum</code>,
     * <code>maximum - minimum</code> must not be greater
     *     than <code>Integer.MAX_VALUE</code>,
     * <code>visibleAmount</code> must be greater than zero.
     * <code>visibleAmount</code> must not be greater than
     *     <code>maximum - minimum</code>,
     * <code>value</code> must not be less than <code>minimum</code>,
     * and <code>value</code> must not be greater than
     *     <code>maximum - visibleAmount</code>
     * <p>
     * Calling this method does not fire an
     * <code>AdjustmentEvent</code>.
     *
     * @param      value is the position in the current window
     * @param      visible is the visible amount of the scroll bar
     * @param      minimum is the minimum value of the scroll bar
     * @param      maximum is the maximum value of the scroll bar
     */
    public void setValues(int value, int visible, int minimum, int maximum) {
        synchronized (this) {
            if (minimum == Integer.MAX_VALUE) {
                minimum = Integer.MAX_VALUE - 1;
            }
            if (maximum <= minimum) {
                maximum = minimum + 1;
            }

            long maxMinusMin = (long) maximum - (long) minimum;
            if (maxMinusMin > Integer.MAX_VALUE) {
                maxMinusMin = Integer.MAX_VALUE;
                maximum = minimum + (int) maxMinusMin;
            }
            if (visible > (int) maxMinusMin) {
                visible = (int) maxMinusMin;
            }
            if (visible < 1) {
                visible = 1;
            }

            if (value < minimum) {
                value = minimum;
            }
            if (value > maximum - visible) {
                value = maximum - visible;
            }

            this.value = value;
            this.visibleAmount = visible;
            this.minimum = minimum;
            this.maximum = maximum;
        }
        repaint();
    }

    /**
     * Returns true if the value is in the process of changing as a
     * result of actions being taken by the user.
     *
     * @return the value of the <code>valueIsAdjusting</code> property
     * @see #setValueIsAdjusting
     * @since 1.4
     */
    public boolean getValueIsAdjusting() {
        return isAdjusting;
    }

    /**
     * Sets the <code>valueIsAdjusting</code> property.
     *
     * @param b new adjustment-in-progress status
     * @see #getValueIsAdjusting
     * @since 1.4
     */
    public void setValueIsAdjusting(boolean b) {
        synchronized (this) {
            isAdjusting = b;
        }
    }

    /**
     * Adds the specified adjustment listener to receive instances of
     * <code>AdjustmentEvent</code> from this scroll bar.
     * If l is <code>null</code>, no exception is thrown and no
     * action is performed.
     *
     * @param        l the adjustment listener
     * @since        JDK1.1
     */
    public synchronized void addAdjustmentListener(AdjustmentListener l) {
        if (l == null) {
            return;
        }
        adjustmentListener = ListenerChain.add(adjustmentListener, l);
    }

    /**
     * Removes the specified adjustment listener so that it no longer
     * receives instances of <code>AdjustmentEvent</code> from this scroll bar.
     * If l is <code>null</code>, no exception is thrown and no action
     * is performed.
     *
     * @param           l    the adjustment listener
     * @since           JDK1.1
     */
    public synchronized void removeAdjustmentListener(AdjustmentListener l) {
        if (l == null) {
            return;
        }
        adjustmentListener = ListenerChain.remove(adjustmentListener, l);
    }

    /**
     * Returns an array of all the adjustment listeners
     * registered on this scrollbar.
     *
     * @return all of this scrollbar's <code>AdjustmentListener</code>s
     *         or an empty array if no adjustment
     *         listeners are currently registered
     * @since 1.4
     */
    public synchronized AdjustmentListener[] getAdjustmentListeners() {
        return getListeners(AdjustmentListener.class);
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this <code>Scrollbar</code>.
     *
     * @param listenerType the type of listeners requested
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this component,
     *          or an empty array if no such listeners have been added
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == AdjustmentListener.class) {
            return ListenerChain.toArray(adjustmentListener, listenerType);
        }
        return super.getListeners(listenerType);
    }

    /**
     * Processes events on this scroll bar. If the event is an
     * instance of <code>AdjustmentEvent</code>, it invokes the
     * <code>processAdjustmentEvent</code> method.
     * Otherwise, it invokes its superclass's
     * <code>processEvent</code> method.
     *
     * @param        e the event
     * @since        JDK1.1
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof AdjustmentEvent) {
            processAdjustmentEvent((AdjustmentEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes adjustment events occurring on this
     * scrollbar by dispatching them to any registered
     * <code>AdjustmentListener</code> objects.
     *
     * @param        e the adjustment event
     * @since        JDK1.1
     */
    protected void processAdjustmentEvent(AdjustmentEvent e) {
        AdjustmentListener listener = adjustmentListener;
        if (listener != null) {
            listener.adjustmentValueChanged(e);
        }
    }

    /**
     * Returns a string representing the state of this <code>Scrollbar</code>.
     * This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return      the parameter string of this scroll bar
     */
    protected String paramString() {
        return super.paramString() +
            ",val=" + value +
            ",vis=" + visibleAmount +
            ",min=" + minimum +
            ",max=" + maximum +
            ((orientation == VERTICAL) ? ",vert" : ",horz") +
            ",isAdjusting=" + isAdjusting;
    }

    // ---- the peer's part: input and painting ----

    private int length() {
        return orientation == VERTICAL ? getHeight() : getWidth();
    }

    private int thickness() {
        return orientation == VERTICAL ? getWidth() : getHeight();
    }

    /** The arrow cells are square, shrunk when the bar is too short to hold two. */
    private int arrowSize() {
        return Math.max(0, Math.min(thickness(), length() / 2));
    }

    /** {thumb start, thumb length} along the bar. */
    private int[] thumb() {
        int arrow = arrowSize();
        int track = Math.max(0, length() - 2 * arrow);
        int range = Math.max(1, maximum - minimum);
        int size = Math.min(track, Math.max(8, (int) ((long) track * visibleAmount / range)));
        int travel = range - visibleAmount;
        int start = arrow + (travel <= 0 ? 0 : (int) ((long) (track - size) * (value - minimum) / travel));
        return new int[] {start, size};
    }

    /** A user adjustment: the value moves and the listeners hear of it with its type. */
    private void userAdjust(int newValue, int type) {
        int old;
        synchronized (this) {
            old = value;
            setValues(newValue, visibleAmount, minimum, maximum);
        }
        if (value != old || type == AdjustmentEvent.TRACK) {
            dispatchEvent(new AdjustmentEvent(this, AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                                              type, value, isAdjusting));
        }
    }

    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1) return;
        int at = orientation == VERTICAL ? e.getY() : e.getX();
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            int arrow = arrowSize();
            int[] thumb = thumb();
            if (at < arrow) {
                userAdjust(value - lineIncrement, AdjustmentEvent.UNIT_DECREMENT);
            } else if (at >= length() - arrow) {
                userAdjust(value + lineIncrement, AdjustmentEvent.UNIT_INCREMENT);
            } else if (at < thumb[0]) {
                userAdjust(value - pageIncrement, AdjustmentEvent.BLOCK_DECREMENT);
            } else if (at >= thumb[0] + thumb[1]) {
                userAdjust(value + pageIncrement, AdjustmentEvent.BLOCK_INCREMENT);
            } else {
                dragOffset = at - thumb[0];
                setValueIsAdjusting(true);
            }
        } else if (e.getID() == MouseEvent.MOUSE_RELEASED && dragOffset >= 0) {
            dragOffset = -1;
            setValueIsAdjusting(false);
            userAdjust(value, AdjustmentEvent.TRACK);
        }
    }

    protected void processMouseMotionEvent(MouseEvent e) {
        super.processMouseMotionEvent(e);
        if (e.getID() != MouseEvent.MOUSE_DRAGGED || dragOffset < 0) return;
        int at = orientation == VERTICAL ? e.getY() : e.getX();
        int arrow = arrowSize();
        int[] thumb = thumb();
        int span = Math.max(1, length() - 2 * arrow - thumb[1]);
        long travel = (long) maximum - minimum - visibleAmount;
        int target = minimum + (int) (travel * (at - dragOffset - arrow) / span);
        userAdjust(target, AdjustmentEvent.TRACK);
    }

    protected void processMouseWheelEvent(MouseWheelEvent e) {
        super.processMouseWheelEvent(e);
        if (!isEnabled() || e.getWheelRotation() == 0) return;
        int type = e.getWheelRotation() < 0 ? AdjustmentEvent.UNIT_DECREMENT : AdjustmentEvent.UNIT_INCREMENT;
        userAdjust(value + e.getWheelRotation() * lineIncrement, type);
    }

    /** The peer's size: the system scrollbar thickness across, a default length along. */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        int t = AwtWidgets.SCROLLBAR_THICKNESS;
        return orientation == VERTICAL ? new Dimension(t, 50) : new Dimension(50, t);
    }

    @Override
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        Color well = AwtWidgets.well(this), face = AwtWidgets.face(this);
        g.setColor(well);
        g.fillRect(0, 0, w, h);
        int arrow = arrowSize(), t = thickness(), len = length();
        int[] thumb = thumb();
        boolean vertical = orientation == VERTICAL;
        for (int end = 0; end < 2; end++) {
            int along = end == 0 ? 0 : len - arrow;
            int x = vertical ? 0 : along, y = vertical ? along : 0;
            g.setColor(face);
            g.fillRect(x, y, vertical ? t : arrow, vertical ? arrow : t);
            AwtWidgets.bevel(g, face, x, y, vertical ? t : arrow, vertical ? arrow : t, true);
            g.setColor(AwtWidgets.foreground(this));
            int size = Math.min(t, arrow);
            AwtWidgets.arrow(g, x + ((vertical ? t : arrow) - size) / 2, y + ((vertical ? arrow : t) - size) / 2,
                             size, vertical ? (end == 0 ? 0 : 1) : (end == 0 ? 2 : 3));
        }
        if (thumb[1] <= 0) return;
        int x = vertical ? 1 : thumb[0], y = vertical ? thumb[0] : 1;
        int tw = vertical ? t - 2 : thumb[1], th = vertical ? thumb[1] : t - 2;
        g.setColor(face);
        g.fillRect(x, y, tw, th);
        AwtWidgets.bevel(g, face, x, y, tw, th, true);
    }
}
