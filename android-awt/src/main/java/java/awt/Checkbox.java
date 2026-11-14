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

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventListener;
import net.runelite.awt.impl.AwtWidgets;
import net.runelite.awt.impl.ListenerChain;

/**
 * A check box is a graphical component that can be in either an
 * "on" (<code>true</code>) or "off" (<code>false</code>) state.
 * Clicking on a check box changes its state from
 * "on" to "off," or from "off" to "on."
 * <p>
 * Several check boxes can be grouped together under the control
 * of a single object, using the <code>CheckboxGroup</code> class;
 * at most one button in a group can be in the "on" state, and a
 * grouped check box is drawn as a radio button.
 * <p>
 * A user click sends an <code>ItemEvent</code> whose item is the label;
 * programmatic state changes send none, as on the JDK. There is no
 * native peer on Android: the check box measures and paints itself.
 *
 * @author      Sami Shaio
 * @see         java.awt.GridLayout
 * @see         java.awt.CheckboxGroup
 * @since       JDK1.0
 */
public class Checkbox extends Component implements ItemSelectable {

    /** The size of the box (or radio circle) the peers draw. */
    private static final int BOX = 13;
    /** The gap between the box and the label. */
    private static final int GAP = 4;

    /**
     * The label of the Checkbox.
     * @serial
     */
    String label;

    /**
     * The state of the <code>Checkbox</code>.
     * @serial
     */
    boolean state;

    /**
     * The check box group.
     * @serial
     */
    CheckboxGroup group;

    transient ItemListener itemListener;

    private static final String base = "checkbox";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = 7270714317450821763L;

    /**
     * Creates a check box with an empty string for its label.
     * The state of this check box is set to "off," and it is not
     * part of any check box group.
     */
    public Checkbox() {
        this("", false, null);
    }

    /**
     * Creates a check box with the specified label.  The state
     * of this check box is set to "off," and it is not part of
     * any check box group.
     *
     * @param     label   a string label for this check box,
     *                        or <code>null</code> for no label.
     */
    public Checkbox(String label) {
        this(label, false, null);
    }

    /**
     * Creates a check box with the specified label
     * and sets the specified state.
     * This check box is not part of any check box group.
     *
     * @param     label   a string label for this check box,
     *                        or <code>null</code> for no label
     * @param     state    the initial state of this check box
     */
    public Checkbox(String label, boolean state) {
        this(label, state, null);
    }

    /**
     * Constructs a Checkbox with the specified label, set to the
     * specified state, and in the specified check box group.
     *
     * @param     label   a string label for this check box,
     *                        or <code>null</code> for no label.
     * @param     state   the initial state of this check box.
     * @param     group   a check box group for this check box,
     *                           or <code>null</code> for no group.
     * @since     JDK1.1
     */
    public Checkbox(String label, boolean state, CheckboxGroup group) {
        this.label = label;
        this.state = state;
        this.group = group;
        if (state && (group != null)) {
            group.setSelectedCheckbox(this);
        }
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK | AWTEvent.ITEM_EVENT_MASK);
    }

    /**
     * Creates a check box with the specified label, in the specified
     * check box group, and set to the specified state.
     *
     * @param     label   a string label for this check box,
     *                        or <code>null</code> for no label.
     * @param     group   a check box group for this check box,
     *                           or <code>null</code> for no group.
     * @param     state   the initial state of this check box.
     * @since     JDK1.1
     */
    public Checkbox(String label, CheckboxGroup group, boolean state) {
        this(label, state, group);
    }

    /**
     * Constructs a name for this component.  Called by
     * <code>getName</code> when the name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (Checkbox.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Gets the label of this check box.
     *
     * @return   the label of this check box, or <code>null</code>
     *                  if this check box has no label.
     * @see      #setLabel(String)
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets this check box's label to be the string argument.
     *
     * @param    label   a string to set as the new label, or
     *                        <code>null</code> for no label.
     * @see      #getLabel
     */
    public void setLabel(String label) {
        boolean testvalid = false;

        synchronized (this) {
            if (label != this.label && (this.label == null ||
                                        !this.label.equals(label))) {
                this.label = label;
                testvalid = true;
            }
        }

        // This could change the preferred size of the Component.
        if (testvalid) {
            invalidate();
        }
    }

    /**
     * Determines whether this check box is in the "on" or "off" state.
     * The boolean value <code>true</code> indicates the "on" state,
     * and <code>false</code> indicates the "off" state.
     *
     * @return    the state of this check box, as a boolean value
     * @see       #setState
     */
    public boolean getState() {
        return state;
    }

    /*
     * Helper function for setState and CheckboxGroup.setSelectedCheckbox
     * Should remain package-private.
     */
    void setStateInternal(boolean state) {
        this.state = state;
        repaint();
    }

    /**
     * Sets the state of this check box to the specified state.
     * The boolean value <code>true</code> indicates the "on" state,
     * and <code>false</code> indicates the "off" state.
     *
     * <p>Note that this method should be primarily used to
     * initialize the state of the checkbox.  Programmatically
     * setting the state of the checkbox will <i>not</i> trigger
     * an <code>ItemEvent</code>.
     *
     * @param     state   the boolean state of the check box
     * @see       #getState
     */
    public void setState(boolean state) {
        /* Cannot hold check box lock when calling group.setSelectedCheckbox. */
        CheckboxGroup group = this.group;
        if (group != null) {
            if (state) {
                group.setSelectedCheckbox(this);
            } else if (group.getSelectedCheckbox() == this) {
                state = true;
            }
        }
        setStateInternal(state);
    }

    /**
     * Returns an array (length 1) containing the checkbox
     * label or null if the checkbox is not selected.
     * @see ItemSelectable
     */
    public Object[] getSelectedObjects() {
        if (state) {
            Object[] items = new Object[1];
            items[0] = label;
            return items;
        }
        return null;
    }

    /**
     * Determines this check box's group.
     * @return     this check box's group, or <code>null</code>
     *               if the check box is not part of a check box group.
     * @see        #setCheckboxGroup(CheckboxGroup)
     */
    public CheckboxGroup getCheckboxGroup() {
        return group;
    }

    /**
     * Sets this check box's group to the specified check box group.
     * If this check box is already in a different check box group,
     * it is first taken out of that group.
     * <p>
     * If the state of this check box is <code>true</code> and the new
     * group already has a check box selected, this check box's state
     * is changed to <code>false</code>.  If the state of this check
     * box is <code>true</code> and the new group has no check box
     * selected, this check box becomes the selected checkbox for
     * the new group and its state is <code>true</code>.
     *
     * @param     g   the new check box group, or <code>null</code>
     *                to remove this check box from any check box group
     * @see       #getCheckboxGroup
     */
    public void setCheckboxGroup(CheckboxGroup g) {
        CheckboxGroup oldGroup;
        boolean oldState;

        /* Do nothing if this check box has already belonged
         * to the check box group g.
         */
        if (this.group == g) {
            return;
        }

        synchronized (this) {
            oldGroup = this.group;
            oldState = getState();

            this.group = g;
            if (this.group != null && getState()) {
                if (this.group.getSelectedCheckbox() != null) {
                    setState(false);
                } else {
                    this.group.setSelectedCheckbox(this);
                }
            }
        }

        /* Locking check box below could cause deadlock with
         * CheckboxGroup's setSelectedCheckbox method.
         *
         * Fix for 4726853 by kdm@sparc.spb.su
         * Here we should check if this check box was selected
         * in the previous group and set selected check box to
         * null for that group if so.
         */
        if (oldGroup != null && oldState) {
            oldGroup.setSelectedCheckbox(null);
        }
        repaint();
    }

    /**
     * Adds the specified item listener to receive item events from
     * this check box.  Item events are sent to listeners in response
     * to user input, but not in response to calls to setState().
     * If l is null, no exception is thrown and no action is performed.
     *
     * @param         l    the item listener
     * @see           #removeItemListener
     * @see           #getItemListeners
     * @since         JDK1.1
     */
    public synchronized void addItemListener(ItemListener l) {
        if (l == null) {
            return;
        }
        itemListener = ListenerChain.add(itemListener, l);
    }

    /**
     * Removes the specified item listener so that the item listener
     * no longer receives item events from this check box.
     * If l is null, no exception is thrown and no action is performed.
     *
     * @param         l    the item listener
     * @see           #addItemListener
     * @see           #getItemListeners
     * @since         JDK1.1
     */
    public synchronized void removeItemListener(ItemListener l) {
        if (l == null) {
            return;
        }
        itemListener = ListenerChain.remove(itemListener, l);
    }

    /**
     * Returns an array of all the item listeners
     * registered on this checkbox.
     *
     * @return all of this checkbox's <code>ItemListener</code>s
     *         or an empty array if no item
     *         listeners are currently registered
     * @since 1.4
     */
    public synchronized ItemListener[] getItemListeners() {
        return getListeners(ItemListener.class);
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this <code>Checkbox</code>.
     *
     * @param listenerType the type of listeners requested
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this checkbox,
     *          or an empty array if no such
     *          listeners have been added
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == ItemListener.class) {
            return ListenerChain.toArray(itemListener, listenerType);
        }
        return super.getListeners(listenerType);
    }

    /**
     * Processes events on this check box.
     * If the event is an instance of <code>ItemEvent</code>,
     * this method invokes the <code>processItemEvent</code> method.
     * Otherwise, it calls its superclass's <code>processEvent</code> method.
     *
     * @param         e the event
     * @since         JDK1.1
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof ItemEvent) {
            processItemEvent((ItemEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes item events occurring on this check box by
     * dispatching them to any registered
     * <code>ItemListener</code> objects.
     *
     * @param       e the item event
     * @since       JDK1.1
     */
    protected void processItemEvent(ItemEvent e) {
        ItemListener listener = itemListener;
        if (listener != null) {
            listener.itemStateChanged(e);
        }
    }

    /** The peer's part: a primary click inside toggles the state and reports it. */
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (e.getID() == MouseEvent.MOUSE_RELEASED && e.getButton() == MouseEvent.BUTTON1
                && e.getX() >= 0 && e.getY() >= 0 && e.getX() < getWidth() && e.getY() < getHeight()) {
            toggle();
        }
    }

    protected void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
        if (!e.isConsumed() && e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_SPACE) {
            toggle();
        }
    }

    /** A selected radio button stays selected when clicked again, and reports nothing. */
    private void toggle() {
        if (!isEnabled()) return;
        boolean next = !state;
        if (group != null && !next) return;
        setState(next);
        dispatchEvent(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, label,
                                    next ? ItemEvent.SELECTED : ItemEvent.DESELECTED));
    }

    /** The peer's size: the box, a gap and the label, with the line height. */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = AwtWidgets.metrics(this);
        int text = label == null || label.isEmpty() ? 0 : GAP + fm.stringWidth(label);
        return new Dimension(BOX + text + 4, Math.max(BOX, fm.getHeight()) + 4);
    }

    @Override
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        int h = getHeight();
        int top = (h - BOX) / 2;
        Color well = AwtWidgets.well(this), fg = AwtWidgets.foreground(this);
        if (group != null) {
            g.setColor(well);
            g.fillOval(2, top, BOX, BOX);
            g.setColor(AwtWidgets.shade(well, 0.3f));
            g.drawOval(2, top, BOX - 1, BOX - 1);
            if (state) {
                g.setColor(fg);
                g.fillOval(2 + 3, top + 3, BOX - 6, BOX - 6);
            }
        } else {
            g.setColor(well);
            g.fillRect(2, top, BOX, BOX);
            AwtWidgets.bevel(g, well, 2, top, BOX, BOX, false);
            if (state) {
                g.setColor(fg);
                int x0 = 2 + 3, y0 = top + BOX / 2;
                for (int i = 0; i < 2; i++) {
                    g.drawLine(x0, y0 + i, x0 + 2, y0 + 2 + i);
                    g.drawLine(x0 + 2, y0 + 2 + i, x0 + 7, y0 - 3 + i);
                }
            }
        }
        if (label == null || label.isEmpty()) return;
        g.setFont(AwtWidgets.font(this));
        g.setColor(fg);
        AwtWidgets.text(g, g.getFontMetrics(), label, 2 + BOX + GAP, 0, h);
    }

    /**
     * Returns a string representing the state of this <code>Checkbox</code>.
     * This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return    the parameter string of this check box
     */
    protected String paramString() {
        String str = super.paramString();
        String label = this.label;
        if (label != null) {
            str += ",label=" + label;
        }
        return str + ",state=" + state;
    }
}
