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
import java.awt.event.MouseEvent;
import java.util.EventListener;
import java.util.Vector;
import net.runelite.awt.impl.AwtWidgets;
import net.runelite.awt.impl.ListenerChain;

/**
 * The <code>Choice</code> class presents a pop-up menu of choices.
 * The current choice is displayed as the title of the menu.
 * <p>
 * A press opens the list in a {@link javax.swing.JPopupMenu} (the module's
 * popup host stands in for the native drop-down); choosing an item other than
 * the current one selects it and sends an <code>ItemEvent</code> whose item is
 * the item's string. Programmatic selection sends none, as on the JDK.
 *
 * @author      Sami Shaio
 * @author      Arthur van Hoff
 * @since       JDK1.0
 */
public class Choice extends Component implements ItemSelectable {
    /**
     * The items for the <code>Choice</code>.
     * This can be a <code>null</code> value.
     * @serial
     */
    Vector<String> pItems;

    /**
     * The index of the current choice for this <code>Choice</code>
     * or -1 if nothing is selected.
     * @serial
     */
    int selectedIndex = -1;

    transient ItemListener itemListener;

    private static final String base = "choice";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = -4075310674757313071L;

    /** Width of the drop-down arrow cell. */
    private static final int ARROW = 16;

    /**
     * Creates a new choice menu. The menu initially has no items in it.
     * <p>
     * By default, the first item added to the choice menu becomes the
     * selected item, until a different selection is made by the user
     * by calling one of the <code>select</code> methods.
     */
    public Choice() {
        pItems = new Vector<>();
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.ITEM_EVENT_MASK);
    }

    /**
     * Constructs a name for this component.  Called by
     * <code>getName</code> when the name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (Choice.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Returns the number of items in this <code>Choice</code> menu.
     * @return the number of items in this <code>Choice</code> menu
     * @see     #getItem
     * @since   JDK1.1
     */
    public int getItemCount() {
        return countItems();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getItemCount()</code>.
     */
    @Deprecated
    public int countItems() {
        return pItems.size();
    }

    /**
     * Gets the string at the specified index in this
     * <code>Choice</code> menu.
     * @param      index the index at which to begin
     * @see        #getItemCount
     */
    public String getItem(int index) {
        return getItemImpl(index);
    }

    /*
     * This is called by the native code, so client code can't
     * be called on the toolkit thread.
     */
    final String getItemImpl(int index) {
        return pItems.elementAt(index);
    }

    /**
     * Adds an item to this <code>Choice</code> menu.
     * @param      item    the item to be added
     * @exception  NullPointerException   if the item's value is
     *                  <code>null</code>
     * @since      JDK1.1
     */
    public void add(String item) {
        addItem(item);
    }

    /**
     * Obsolete as of Java 2 platform v1.1.  Please use the
     * <code>add</code> method instead.
     * <p>
     * Adds an item to this <code>Choice</code> menu.
     * @param item the item to be added
     * @exception NullPointerException if the item's value is equal to
     *          <code>null</code>
     */
    public void addItem(String item) {
        synchronized (this) {
            insertNoInvalidate(item, pItems.size());
        }
        invalidate();
    }

    /**
     * Inserts an item to this <code>Choice</code>,
     * but does not invalidate the <code>Choice</code>.
     * Client methods must provide their own synchronization before
     * invoking this method.
     * @param item the item to be added
     * @param index the new item position
     * @exception NullPointerException if the item's value is equal to
     *          <code>null</code>
     */
    private void insertNoInvalidate(String item, int index) {
        if (item == null) {
            throw new
                NullPointerException("cannot add null item to Choice");
        }
        pItems.insertElementAt(item, index);
        // no selection or selection shifted up
        if (selectedIndex < 0 || selectedIndex >= index) {
            select(0);
        }
    }

    /**
     * Inserts the item into this choice at the specified position.
     * Existing items at an index greater than or equal to
     * <code>index</code> are shifted up by one to accommodate
     * the new item.  If <code>index</code> is greater than or
     * equal to the number of items in this choice,
     * <code>item</code> is added to the end of this choice.
     * <p>
     * If the item is the first one being added to the choice,
     * then the item becomes selected.  Otherwise, if the
     * selected item was one of the items shifted, the first
     * item in the choice becomes the selected item.  If the
     * selected item was no among those shifted, it remains
     * the selected item.
     * @param item the non-<code>null</code> item to be inserted
     * @param index the position at which the item should be inserted
     * @exception IllegalArgumentException if index is less than 0
     */
    public void insert(String item, int index) {
        synchronized (this) {
            if (index < 0) {
                throw new IllegalArgumentException("index less than zero.");
            }
            /* if the index greater than item count, add item to the end */
            index = Math.min(index, pItems.size());

            insertNoInvalidate(item, index);
        }
        invalidate();
    }

    /**
     * Removes the first occurrence of <code>item</code>
     * from the <code>Choice</code> menu.  If the item
     * being removed is the currently selected item,
     * then the first item in the choice becomes the
     * selected item.  Otherwise, the currently selected
     * item remains selected (and the selected index is
     * updated accordingly).
     * @param      item  the item to remove from this <code>Choice</code> menu
     * @exception  IllegalArgumentException  if the item doesn't
     *                     exist in the choice menu
     * @since      JDK1.1
     */
    public void remove(String item) {
        synchronized (this) {
            int index = pItems.indexOf(item);
            if (index < 0) {
                throw new IllegalArgumentException("item " + item +
                                                   " not found in choice");
            } else {
                removeNoInvalidate(index);
            }
        }
        invalidate();
    }

    /**
     * Removes an item from the choice menu
     * at the specified position.  If the item
     * being removed is the currently selected item,
     * then the first item in the choice becomes the
     * selected item.  Otherwise, the currently selected
     * item remains selected (and the selected index is
     * updated accordingly).
     * @param      position the position of the item
     * @throws IndexOutOfBoundsException if the specified
     *          position is out of bounds
     * @since      JDK1.1
     */
    public void remove(int position) {
        synchronized (this) {
            removeNoInvalidate(position);
        }
        invalidate();
    }

    /**
     * Removes an item from the <code>Choice</code> at the
     * specified position, but does not invalidate the <code>Choice</code>.
     * Client methods must provide their
     * own synchronization before invoking this method.
     * @param      position   the position of the item
     */
    private void removeNoInvalidate(int position) {
        pItems.removeElementAt(position);
        /* Adjust selectedIndex if selected item was removed. */
        if (pItems.size() == 0) {
            selectedIndex = -1;
        } else if (selectedIndex == position) {
            select(0);
        } else if (selectedIndex > position) {
            select(selectedIndex-1);
        }
        repaint();
    }

    /**
     * Removes all items from the choice menu.
     * @see       #remove
     * @since     JDK1.1
     */
    public void removeAll() {
        synchronized (this) {
            pItems.removeAllElements();
            selectedIndex = -1;
        }
        invalidate();
        repaint();
    }

    /**
     * Gets a representation of the current choice as a string.
     * @return    a string representation of the currently
     *                     selected item in this choice menu
     * @see       #getSelectedIndex
     */
    public synchronized String getSelectedItem() {
        return (selectedIndex >= 0) ? getItem(selectedIndex) : null;
    }

    /**
     * Returns an array (length 1) containing the currently selected
     * item.  If this choice has no items, returns <code>null</code>.
     * @see ItemSelectable
     */
    public synchronized Object[] getSelectedObjects() {
        if (selectedIndex >= 0) {
            Object[] items = new Object[1];
            items[0] = getItem(selectedIndex);
            return items;
        }
        return null;
    }

    /**
     * Returns the index of the currently selected item.
     * If nothing is selected, returns -1.
     *
     * @return the index of the currently selected item, or -1 if nothing
     *  is currently selected
     * @see #getSelectedItem
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Sets the selected item in this <code>Choice</code> menu to be the
     * item at the specified position.
     *
     * <p>Note that this method should be primarily used to
     * initially select an item in this component.
     * Programmatically calling this method will <i>not</i> trigger
     * an <code>ItemEvent</code>.  The only way to trigger an
     * <code>ItemEvent</code> is by user interaction.
     *
     * @param      pos      the position of the selected item
     * @exception  IllegalArgumentException if the specified
     *                            position is greater than the
     *                            number of items or less than zero
     * @see        #getSelectedItem
     * @see        #getSelectedIndex
     */
    public synchronized void select(int pos) {
        if ((pos >= pItems.size()) || (pos < 0)) {
            throw new IllegalArgumentException("illegal Choice item position: " + pos);
        }
        if (pItems.size() > 0) {
            selectedIndex = pos;
            repaint();
        }
    }

    /**
     * Sets the selected item in this <code>Choice</code> menu
     * to be the item whose name is equal to the specified string.
     * If more than one item matches (is equal to) the specified string,
     * the one with the smallest index is selected.
     *
     * <p>Note that this method should be primarily used to
     * initially select an item in this component.
     * Programmatically calling this method will <i>not</i> trigger
     * an <code>ItemEvent</code>.  The only way to trigger an
     * <code>ItemEvent</code> is by user interaction.
     *
     * @param       str     the specified string
     * @see         #getSelectedItem
     * @see         #getSelectedIndex
     */
    public synchronized void select(String str) {
        int index = pItems.indexOf(str);
        if (index >= 0) {
            select(index);
        }
    }

    /**
     * Adds the specified item listener to receive item events from
     * this <code>Choice</code> menu.  Item events are sent in response
     * to user input, but not in response to calls to <code>select</code>.
     * If l is <code>null</code>, no exception is thrown and no action
     * is performed.
     * @param         l    the item listener
     * @since         JDK1.1
     */
    public synchronized void addItemListener(ItemListener l) {
        if (l == null) {
           return;
        }
        itemListener = ListenerChain.add(itemListener, l);
    }

    /**
     * Removes the specified item listener so that it no longer receives
     * item events from this <code>Choice</code> menu.
     * If l is <code>null</code>, no exception is thrown and no
     * action is performed.
     * @param         l    the item listener
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
     * registered on this choice.
     *
     * @return all of this choice's <code>ItemListener</code>s
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
     * upon this <code>Choice</code>.
     *
     * @param listenerType the type of listeners requested
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this choice,
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
     * Processes events on this choice. If the event is an
     * instance of <code>ItemEvent</code>, it invokes the
     * <code>processItemEvent</code> method. Otherwise, it calls its
     * superclass's <code>processEvent</code> method.
     *
     * @param      e the event
     * @since      JDK1.1
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof ItemEvent) {
            processItemEvent((ItemEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes item events occurring on this <code>Choice</code>
     * menu by dispatching them to any registered
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

    /** The peer's part: a primary press drops the item list down under the choice. */
    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (e.getID() != MouseEvent.MOUSE_PRESSED || e.getButton() != MouseEvent.BUTTON1
                || !isEnabled() || pItems.isEmpty()) {
            return;
        }
        javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        String[] items;
        synchronized (this) {
            items = pItems.toArray(new String[0]);
        }
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(items[i]);
            item.setFont(AwtWidgets.font(this));
            item.addActionListener(event -> userSelect(index, event.getWhen()));
            popup.add(item);
        }
        popup.show(this, 0, getHeight());
    }

    /** A user choice: selects the item and reports it when it differs from the current one. */
    void userSelect(int index, long when) {
        String item;
        synchronized (this) {
            if (index < 0 || index >= pItems.size() || index == selectedIndex) return;
            select(index);
            item = getItem(index);
        }
        dispatchEvent(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, item, ItemEvent.SELECTED));
    }

    /** The peer's size: the widest item, padding and the arrow cell, by the line height. */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = AwtWidgets.metrics(this);
        int widest = 0;
        synchronized (this) {
            for (String item : pItems) widest = Math.max(widest, fm.stringWidth(item));
        }
        return new Dimension(widest + 12 + ARROW, fm.getHeight() + 8);
    }

    @Override
    public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredSize();
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        Color face = AwtWidgets.face(this);
        g.setColor(face);
        g.fillRect(0, 0, w, h);
        AwtWidgets.bevel(g, face, 0, 0, w, h, true);
        Color fg = AwtWidgets.foreground(this);
        g.setColor(fg);
        AwtWidgets.arrow(g, w - ARROW - 2, (h - ARROW) / 2, ARROW, 1);
        String item = getSelectedItem();
        if (item == null) return;
        Graphics text = g.create(0, 0, Math.max(0, w - ARROW - 4), h);
        try {
            text.setFont(AwtWidgets.font(this));
            text.setColor(fg);
            AwtWidgets.text(text, text.getFontMetrics(), item, 6, 0, h);
        } finally {
            text.dispose();
        }
    }

    /**
     * Returns a string representing the state of this <code>Choice</code>
     * menu. This method is intended to be used only for debugging purposes,
     * and the content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return    the parameter string of this <code>Choice</code> menu
     */
    protected String paramString() {
        return super.paramString() + ",current=" + getSelectedItem();
    }
}
