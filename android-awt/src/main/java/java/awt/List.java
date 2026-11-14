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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.EventListener;
import java.util.Vector;
import net.runelite.awt.impl.AwtWidgets;
import net.runelite.awt.impl.ListenerChain;

/**
 * The <code>List</code> component presents the user with a
 * scrolling list of text items. The list can be set up so that
 * the user can choose either one item or multiple items.
 * <p>
 * Clicking on an item that isn't selected selects it; in multiple-selection
 * mode clicking a selected item deselects it. Each such change sends an
 * <code>ItemEvent</code> whose item is the row index as an
 * <code>Integer</code>. Double-clicking an item, or pressing Return while an
 * item is selected, sends an <code>ActionEvent</code> whose command is the
 * item's text. Programmatic selection sends no events, as on the JDK.
 * <p>
 * There is no native peer on Android: the list measures, scrolls and paints
 * itself, and keeps its selection indexes in step with insertions and
 * removals the way the desktop peers do.
 *
 * @author      Sami Shaio
 * @see         java.awt.event.ItemEvent
 * @see         java.awt.event.ItemListener
 * @see         java.awt.event.ActionEvent
 * @see         java.awt.event.ActionListener
 * @since       JDK1.0
 */
public class List extends Component implements ItemSelectable {
    /**
     * A vector created to contain items which will become
     * part of the List Component.
     *
     * @serial
     * @see #addItem(String)
     * @see #getItem(int)
     */
    Vector<String>      items = new Vector<>();

    /**
     * This field will represent the number of visible rows in the
     * <code>List</code> Component.  It is specified only once, and
     * that is when the list component is actually
     * created.  It will never change.
     *
     * @serial
     * @see #getRows()
     */
    int         rows = 0;

    /**
     * <code>multipleMode</code> is a variable that will
     * be set to <code>true</code> if a list component is to be set to
     * multiple selection mode, that is where the user can
     * select more than one item in a list at one time.
     *
     * @serial
     * @see #isMultipleMode()
     * @see #setMultipleMode(boolean)
     */
    boolean     multipleMode = false;

    /**
     * <code>selected</code> is an array that will contain
     * the indices of items that have been selected.
     *
     * @serial
     * @see #getSelectedIndexes()
     * @see #getSelectedIndex()
     */
    int         selected[] = new int[0];

    /**
     * This variable contains the value that will be used
     * when trying to make a particular list item visible.
     *
     * @serial
     * @see #makeVisible(int)
     */
    int         visibleIndex = -1;

    transient ActionListener actionListener;
    transient ItemListener itemListener;

    private static final String base = "list";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
     private static final long serialVersionUID = -3304312411574666869L;

    /**
     * The default number of visible rows is 4.  A list with
     * zero rows is unusable and unsightly.
     */
    final static int    DEFAULT_VISIBLE_ROWS = 4;

    /** Inner margin between the bevel and the rows, the peer's. */
    private static final int MARGIN = 2;

    /** The first row shown; the peer's scroll position. */
    private transient int top;
    /** The row a keyboard or mouse gesture last landed on, the anchor for arrow keys. */
    private transient int lead = -1;

    /**
     * Creates a new scrolling list.
     * By default, there are four visible lines and multiple selections are
     * not allowed.  Note that this is a convenience method for
     * <code>List(0, false)</code>.
     */
    public List() {
        this(0, false);
    }

    /**
     * Creates a new scrolling list initialized with the specified
     * number of visible lines. By default, multiple selections are
     * not allowed.  Note that this is a convenience method for
     * <code>List(rows, false)</code>.
     *
     * @param       rows the number of items to show.
     * @since       JDK1.1
     */
    public List(int rows) {
        this(rows, false);
    }

    /**
     * Creates a new scrolling list initialized to display the specified
     * number of rows. Note that if zero rows are specified, then
     * the list will be created with a default of four rows.
     * If the value of <code>multipleMode</code> is
     * <code>true</code>, then the user can select multiple items from
     * the list. If it is <code>false</code>, only one item at a time
     * can be selected.
     *
     * @param       rows   the number of items to show.
     * @param       multipleMode   if <code>true</code>,
     *                     then multiple selections are allowed;
     *                     otherwise, only one item can be selected at a time.
     */
    public List(int rows, boolean multipleMode) {
        this.rows = (rows != 0) ? rows : DEFAULT_VISIBLE_ROWS;
        this.multipleMode = multipleMode;
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK
                     | AWTEvent.KEY_EVENT_MASK | AWTEvent.ITEM_EVENT_MASK | AWTEvent.ACTION_EVENT_MASK);
    }

    /**
     * Construct a name for this component.  Called by
     * <code>getName</code> when the name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (List.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Gets the number of items in the list.
     * @return     the number of items in the list
     * @see        #getItem
     * @since      JDK1.1
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
        return items.size();
    }

    /**
     * Gets the item associated with the specified index.
     * @return       an item that is associated with
     *                    the specified index
     * @param        index the position of the item
     * @see          #getItemCount
     */
    public String getItem(int index) {
        return getItemImpl(index);
    }

    // NOTE: This method may be called by privileged threads.
    //       We implement this functionality in a package-private method
    //       to insure that it cannot be overridden by client subclasses.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    final String getItemImpl(int index) {
        return items.elementAt(index);
    }

    /**
     * Gets the items in the list.
     * @return       a string array containing items of the list
     * @see          #select
     * @see          #deselect
     * @see          #isIndexSelected
     * @since        JDK1.1
     */
    public synchronized String[] getItems() {
        String itemCopies[] = new String[items.size()];
        items.copyInto(itemCopies);
        return itemCopies;
    }

    /**
     * Adds the specified item to the end of scrolling list.
     * @param item the item to be added
     * @since JDK1.1
     */
    public void add(String item) {
        addItem(item);
    }

    /**
     * @deprecated      replaced by <code>add(String)</code>.
     */
    @Deprecated
    public void addItem(String item) {
        addItem(item, -1);
    }

    /**
     * Adds the specified item to the the scrolling list
     * at the position indicated by the index.  The index is
     * zero-based.  If the value of the index is less than zero,
     * or if the value of the index is greater than or equal to
     * the number of items in the list, then the item is added
     * to the end of the list.
     * @param       item   the item to be added;
     *              if this parameter is <code>null</code> then the item is
     *              treated as an empty string, <code>""</code>
     * @param       index  the position at which to add the item
     * @since       JDK1.1
     */
    public void add(String item, int index) {
        addItem(item, index);
    }

    /**
     * @deprecated     replaced by <code>add(String, int)</code>.
     */
    @Deprecated
    public synchronized void addItem(String item, int index) {
        if (index < -1 || index >= items.size()) {
            index = -1;
        }

        if (item == null) {
            item = "";
        }

        if (index == -1) {
            items.addElement(item);
        } else {
            items.insertElementAt(item, index);
            // The peer shifts the selection with the rows it moves down.
            for (int i = 0; i < selected.length; i++) {
                if (selected[i] >= index) selected[i]++;
            }
            if (lead >= index) lead++;
        }
        invalidate();
        repaint();
    }

    /**
     * Replaces the item at the specified index in the scrolling list
     * with the new string.
     * @param       newValue   a new string to replace an existing item
     * @param       index      the position of the item to replace
     * @exception ArrayIndexOutOfBoundsException if <code>index</code>
     *          is out of range
     */
    public synchronized void replaceItem(String newValue, int index) {
        remove(index);
        add(newValue, index);
    }

    /**
     * Removes all items from this list.
     * @see #remove
     * @see #delItems
     * @since JDK1.1
     */
    public void removeAll() {
        clear();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>removeAll()</code>.
     */
    @Deprecated
    public synchronized void clear() {
        items = new Vector<>();
        selected = new int[0];
        top = 0;
        lead = -1;
        invalidate();
        repaint();
    }

    /**
     * Removes the first occurrence of an item from the list.
     * If the specified item is selected, and is the only selected
     * item in the list, the list is set to have no selection.
     * @param        item  the item to remove from the list
     * @exception    IllegalArgumentException
     *                     if the item doesn't exist in the list
     * @since        JDK1.1
     */
    public synchronized void remove(String item) {
        int index = items.indexOf(item);
        if (index < 0) {
            throw new IllegalArgumentException("item " + item +
                                               " not found in list");
        } else {
            remove(index);
        }
    }

    /**
     * Removes the item at the specified position
     * from this scrolling list.
     * If the item with the specified position is selected, and is the
     * only selected item in the list, the list is set to have no selection.
     * @param      position   the index of the item to delete
     * @exception    ArrayIndexOutOfBoundsException
     *               if the <code>position</code> is less than 0 or
     *               greater than <code>getItemCount()-1</code>
     * @since      JDK1.1
     */
    public void remove(int position) {
        delItem(position);
    }

    /**
     * @deprecated     replaced by <code>remove(String)</code>
     *                         and <code>remove(int)</code>.
     */
    @Deprecated
    public void delItem(int position) {
        delItems(position, position);
    }

    /**
     * Gets the index of the selected item on the list,
     *
     * @return        the index of the selected item;
     *                if no item is selected, or if multiple items are
     *                selected, <code>-1</code> is returned.
     * @see           #select
     * @see           #deselect
     * @see           #isIndexSelected
     */
    public synchronized int getSelectedIndex() {
        int sel[] = getSelectedIndexes();
        return (sel.length == 1) ? sel[0] : -1;
    }

    /**
     * Gets the selected indexes on the list.
     *
     * @return        an array of the selected indexes on this scrolling list;
     *                if no item is selected, a zero-length array is returned.
     * @see           #select
     * @see           #deselect
     * @see           #isIndexSelected
     */
    public synchronized int[] getSelectedIndexes() {
        return selected.clone();
    }

    /**
     * Gets the selected item on this scrolling list.
     *
     * @return        the selected item on the list;
     *                if no item is selected, or if multiple items are
     *                selected, <code>null</code> is returned.
     * @see           #select
     * @see           #deselect
     * @see           #isIndexSelected
     */
    public synchronized String getSelectedItem() {
        int index = getSelectedIndex();
        return (index < 0) ? null : getItem(index);
    }

    /**
     * Gets the selected items on this scrolling list.
     *
     * @return        an array of the selected items on this scrolling list;
     *                if no item is selected, a zero-length array is returned.
     * @see           #select
     * @see           #deselect
     * @see           #isIndexSelected
     */
    public synchronized String[] getSelectedItems() {
        int sel[] = getSelectedIndexes();
        String str[] = new String[sel.length];
        for (int i = 0 ; i < sel.length ; i++) {
            str[i] = getItem(sel[i]);
        }
        return str;
    }

    /**
     * Gets the selected items on this scrolling list in an array of Objects.
     * @return        an array of <code>Object</code>s representing the
     *                selected items on this scrolling list;
     *                if no item is selected, a zero-length array is returned.
     * @see #getSelectedItems
     * @see ItemSelectable
     */
    public Object[] getSelectedObjects() {
        return getSelectedItems();
    }

    /**
     * Selects the item at the specified index in the scrolling list.
     *<p>
     * Note that passing out of range parameters is invalid,
     * and will result in unspecified behavior.
     *
     * <p>Note that this method should be primarily used to
     * initially select an item in this component.
     * Programmatically calling this method will <i>not</i> trigger
     * an <code>ItemEvent</code>.  The only way to trigger an
     * <code>ItemEvent</code> is by user interaction.
     *
     * @param        index the position of the item to select
     * @see          #getSelectedItem
     * @see          #deselect
     * @see          #isIndexSelected
     */
    public void select(int index) {
        synchronized (this) {
            // The peer ignores rows that do not exist; an out-of-range index never
            // reaches the selection the way it can on an undisplayed JDK list.
            if (index < 0 || index >= items.size()) return;
            boolean alreadySelected = false;

            for (int i = 0 ; i < selected.length ; i++) {
                if (selected[i] == index) {
                    alreadySelected = true;
                    break;
                }
            }

            if (!alreadySelected) {
                if (!multipleMode) {
                    selected = new int[1];
                    selected[0] = index;
                } else {
                    int newsel[] = new int[selected.length + 1];
                    System.arraycopy(selected, 0, newsel, 0,
                                     selected.length);
                    newsel[selected.length] = index;
                    selected = newsel;
                }
            }
            lead = index;
        }
        repaint();
    }

    /**
     * Deselects the item at the specified index.
     * <p>
     * Note that passing out of range parameters is invalid,
     * and will result in unspecified behavior.
     * <p>
     * If the item at the specified index is not selected,
     * then the operation is ignored.
     * @param        index the position of the item to deselect
     * @see          #select
     * @see          #getSelectedItem
     * @see          #isIndexSelected
     */
    public synchronized void deselect(int index) {
        for (int i = 0 ; i < selected.length ; i++) {
            if (selected[i] == index) {
                int newsel[] = new int[selected.length - 1];
                System.arraycopy(selected, 0, newsel, 0, i);
                System.arraycopy(selected, i+1, newsel, i, selected.length - (i+1));
                selected = newsel;
                repaint();
                return;
            }
        }
    }

    /**
     * Determines if the specified item in this scrolling list is
     * selected.
     * @param      index   the item to be checked
     * @return     <code>true</code> if the specified item has been
     *                       selected; <code>false</code> otherwise
     * @see        #select
     * @see        #deselect
     * @since      JDK1.1
     */
    public boolean isIndexSelected(int index) {
        return isSelected(index);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>isIndexSelected(int)</code>.
     */
    @Deprecated
    public boolean isSelected(int index) {
        int sel[] = getSelectedIndexes();
        for (int i = 0 ; i < sel.length ; i++) {
            if (sel[i] == index) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the number of visible lines in this list.  Note that
     * once the <code>List</code> has been created, this number
     * will never change.
     * @return     the number of visible lines in this scrolling list
     */
    public int getRows() {
        return rows;
    }

    /**
     * Determines whether this list allows multiple selections.
     * @return     <code>true</code> if this list allows multiple
     *                 selections; otherwise, <code>false</code>
     * @see        #setMultipleMode
     * @since      JDK1.1
     */
    public boolean isMultipleMode() {
        return allowsMultipleSelections();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>isMultipleMode()</code>.
     */
    @Deprecated
    public boolean allowsMultipleSelections() {
        return multipleMode;
    }

    /**
     * Sets the flag that determines whether this list
     * allows multiple selections.
     * When the selection mode is changed from multiple-selection to
     * single-selection, the selected items change as follows:
     * If a selected item has the location cursor, only that
     * item will remain selected.  If no selected item has the
     * location cursor, all items will be deselected.
     * @param       b   if <code>true</code> then multiple selections
     *                      are allowed; otherwise, only one item from
     *                      the list can be selected at once
     * @see         #isMultipleMode
     * @since       JDK1.1
     */
    public void setMultipleMode(boolean b) {
        setMultipleSelections(b);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>setMultipleMode(boolean)</code>.
     */
    @Deprecated
    public synchronized void setMultipleSelections(boolean b) {
        if (b != multipleMode) {
            multipleMode = b;
            if (!b && selected.length > 1) {
                selected = isSelected(lead) ? new int[] {lead} : new int[0];
                repaint();
            }
        }
    }

    /**
     * Gets the index of the item that was last made visible by
     * the method <code>makeVisible</code>.
     * @return      the index of the item that was last made visible
     * @see         #makeVisible
     */
    public int getVisibleIndex() {
        return visibleIndex;
    }

    /**
     * Makes the item at the specified index visible.
     * @param       index    the position of the item
     * @see         #getVisibleIndex
     */
    public synchronized void makeVisible(int index) {
        visibleIndex = index;
        scrollTo(index);
    }

    /**
     * Gets the preferred dimensions for a list with the specified
     * number of rows.
     * @param      rows    number of rows in the list
     * @return     the preferred dimensions for displaying this scrolling list
     *             given that the specified number of rows must be visible
     * @see        java.awt.Component#getPreferredSize
     * @since      JDK1.1
     */
    public Dimension getPreferredSize(int rows) {
        return preferredSize(rows);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getPreferredSize(int)</code>.
     */
    @Deprecated
    public Dimension preferredSize(int rows) {
        FontMetrics fm = AwtWidgets.metrics(this);
        int widest = 4 * fm.charWidth('W');
        synchronized (this) {
            for (String item : items) widest = Math.max(widest, fm.stringWidth(item));
        }
        return new Dimension(widest + 2 * MARGIN + 8 + AwtWidgets.SCROLLBAR_THICKNESS,
                             Math.max(1, rows) * rowHeight(fm) + 2 * MARGIN + 2);
    }

    /**
     * Gets the preferred size of this scrolling list.
     * @return     the preferred dimensions for displaying this scrolling list
     * @see        java.awt.Component#getPreferredSize
     * @since      JDK1.1
     */
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        return preferredSize(rows > 0 ? rows : DEFAULT_VISIBLE_ROWS);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getPreferredSize()</code>.
     */
    @Deprecated
    public Dimension preferredSize() {
        return getPreferredSize();
    }

    /**
     * Gets the minimum dimensions for a list with the specified
     * number of rows.
     * @param      rows    number of rows in the list
     * @return     the minimum dimensions for displaying this scrolling list
     *             given that the specified number of rows must be visible
     * @see        java.awt.Component#getMinimumSize
     * @since      JDK1.1
     */
    public Dimension getMinimumSize(int rows) {
        return minimumSize(rows);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getMinimumSize(int)</code>.
     */
    @Deprecated
    public Dimension minimumSize(int rows) {
        return preferredSize(rows);
    }

    /**
     * Determines the minimum size of this scrolling list.
     * @return       the minimum dimensions needed
     *                        to display this scrolling list
     * @see          java.awt.Component#getMinimumSize()
     * @since        JDK1.1
     */
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) return super.getMinimumSize();
        return minimumSize(rows > 0 ? rows : DEFAULT_VISIBLE_ROWS);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getMinimumSize()</code>.
     */
    @Deprecated
    public Dimension minimumSize() {
        return getMinimumSize();
    }

    /**
     * Adds the specified item listener to receive item events from
     * this list.  Item events are sent in response to user input, but not
     * in response to calls to <code>select</code> or <code>deselect</code>.
     * If listener <code>l</code> is <code>null</code>,
     * no exception is thrown and no action is performed.
     *
     * @param         l the item listener
     * @since         JDK1.1
     */
    public synchronized void addItemListener(ItemListener l) {
        if (l == null) {
            return;
        }
        itemListener = ListenerChain.add(itemListener, l);
    }

    /**
     * Removes the specified item listener so that it no longer
     * receives item events from this list.
     * If listener <code>l</code> is <code>null</code>,
     * no exception is thrown and no action is performed.
     *
     * @param           l the item listener
     * @since           JDK1.1
     */
    public synchronized void removeItemListener(ItemListener l) {
        if (l == null) {
            return;
        }
        itemListener = ListenerChain.remove(itemListener, l);
    }

    /**
     * Returns an array of all the item listeners
     * registered on this list.
     *
     * @return all of this list's <code>ItemListener</code>s
     *         or an empty array if no item
     *         listeners are currently registered
     * @since 1.4
     */
    public synchronized ItemListener[] getItemListeners() {
        return getListeners(ItemListener.class);
    }

    /**
     * Adds the specified action listener to receive action events from
     * this list. Action events occur when a user double-clicks
     * on a list item or types Enter when the list has the keyboard
     * focus.
     * If listener <code>l</code> is <code>null</code>,
     * no exception is thrown and no action is performed.
     *
     * @param         l the action listener
     * @since         JDK1.1
     */
    public synchronized void addActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        actionListener = ListenerChain.add(actionListener, l);
    }

    /**
     * Removes the specified action listener so that it no longer
     * receives action events from this list.
     * If listener <code>l</code> is <code>null</code>,
     * no exception is thrown and no action is performed.
     *
     * @param           l     the action listener
     * @since           JDK1.1
     */
    public synchronized void removeActionListener(ActionListener l) {
        if (l == null) {
            return;
        }
        actionListener = ListenerChain.remove(actionListener, l);
    }

    /**
     * Returns an array of all the action listeners
     * registered on this list.
     *
     * @return all of this list's <code>ActionListener</code>s
     *         or an empty array if no action
     *         listeners are currently registered
     * @since 1.4
     */
    public synchronized ActionListener[] getActionListeners() {
        return getListeners(ActionListener.class);
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this <code>List</code>.
     *
     * @param listenerType the type of listeners requested
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this list,
     *          or an empty array if no such
     *          listeners have been added
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == ActionListener.class) {
            return ListenerChain.toArray(actionListener, listenerType);
        } else if (listenerType == ItemListener.class) {
            return ListenerChain.toArray(itemListener, listenerType);
        }
        return super.getListeners(listenerType);
    }

    /**
     * Processes events on this scrolling list. If an event is
     * an instance of <code>ItemEvent</code>, it invokes the
     * <code>processItemEvent</code> method. Else, if the
     * event is an instance of <code>ActionEvent</code>,
     * it invokes <code>processActionEvent</code>.
     * If the event is not an item event or an action event,
     * it invokes <code>processEvent</code> on the superclass.
     *
     * @param        e the event
     * @since        JDK1.1
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof ItemEvent) {
            processItemEvent((ItemEvent)e);
            return;
        } else if (e instanceof ActionEvent) {
            processActionEvent((ActionEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes item events occurring on this list by
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

    /**
     * Processes action events occurring on this component
     * by dispatching them to any registered
     * <code>ActionListener</code> objects.
     *
     * @param       e the action event
     * @since       JDK1.1
     */
    protected void processActionEvent(ActionEvent e) {
        ActionListener listener = actionListener;
        if (listener != null) {
            listener.actionPerformed(e);
        }
    }

    /**
     * Returns the parameter string representing the state of this
     * scrolling list. This string is useful for debugging.
     * @return    the parameter string of this scrolling list
     */
    protected String paramString() {
        return super.paramString() + ",selected=" + getSelectedItem();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * Not for public use in the future.
     * This method is expected to be retained only as a package
     * private method.
     */
    @Deprecated
    public synchronized void delItems(int start, int end) {
        for (int i = end; i >= start; i--) {
            items.removeElementAt(i);
        }
        // The peer drops the removed rows from the selection and shifts the rest up.
        int removed = end - start + 1;
        int kept = 0;
        int[] next = new int[selected.length];
        for (int index : selected) {
            if (index < start) next[kept++] = index;
            else if (index > end) next[kept++] = index - removed;
        }
        if (kept != selected.length) {
            int[] trimmed = new int[kept];
            System.arraycopy(next, 0, trimmed, 0, kept);
            selected = trimmed;
        } else {
            selected = next;
        }
        if (lead > end) lead -= removed;
        else if (lead >= start) lead = -1;
        top = Math.max(0, Math.min(top, items.size() - 1));
        invalidate();
        repaint();
    }

    // ---- the peer's part: input and painting ----

    private static int rowHeight(FontMetrics fm) {
        return fm.getHeight() + 2;
    }

    private int visibleRows() {
        int h = getHeight() - 2 * MARGIN - 2;
        int rowHeight = rowHeight(AwtWidgets.metrics(this));
        return h > 0 ? Math.max(1, h / rowHeight) : Math.max(1, rows);
    }

    private boolean scrolls() {
        return items.size() > visibleRows();
    }

    private void scrollTo(int index) {
        int visible = visibleRows();
        if (index < top) top = Math.max(0, index);
        else if (index >= top + visible) top = index - visible + 1;
        top = Math.max(0, Math.min(top, Math.max(0, items.size() - visible)));
        repaint();
    }

    private int rowAt(int y) {
        int row = top + (y - MARGIN - 1) / rowHeight(AwtWidgets.metrics(this));
        return y < MARGIN + 1 || row >= items.size() ? -1 : row;
    }

    protected void processMouseEvent(MouseEvent e) {
        super.processMouseEvent(e);
        if (!isEnabled() || e.getButton() != MouseEvent.BUTTON1) return;
        if (scrolls() && e.getX() >= getWidth() - AwtWidgets.SCROLLBAR_THICKNESS - 1) {
            // The scrollbar: a press above or below the middle pages the rows.
            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                int page = visibleRows();
                synchronized (this) {
                    scrollTo(e.getY() < getHeight() / 2 ? top - page : top + 2 * page - 1);
                }
            }
            return;
        }
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            int row;
            synchronized (this) {
                row = rowAt(e.getY());
            }
            if (row < 0) return;
            requestFocus();
            userToggle(row);
        } else if (e.getID() == MouseEvent.MOUSE_CLICKED && e.getClickCount() == 2) {
            int row;
            synchronized (this) {
                row = rowAt(e.getY());
            }
            if (row >= 0) fireAction(row, e.getWhen(), AwtWidgets.actionModifiers(e));
        }
    }

    protected void processMouseWheelEvent(MouseWheelEvent e) {
        super.processMouseWheelEvent(e);
        synchronized (this) {
            int visible = visibleRows();
            top = Math.max(0, Math.min(top + 3 * e.getWheelRotation(), Math.max(0, items.size() - visible)));
        }
        repaint();
    }

    protected void processKeyEvent(KeyEvent e) {
        super.processKeyEvent(e);
        if (e.isConsumed() || e.getID() != KeyEvent.KEY_PRESSED || !isEnabled() || items.isEmpty()) return;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER: {
                int row = multipleMode ? lead : getSelectedIndex();
                if (row >= 0 && row < items.size()) fireAction(row, e.getWhen(), AwtWidgets.actionModifiers(e));
                break;
            }
            case KeyEvent.VK_UP:
            case KeyEvent.VK_DOWN: {
                int step = e.getKeyCode() == KeyEvent.VK_UP ? -1 : 1;
                int row = Math.max(0, Math.min(items.size() - 1, lead < 0 ? 0 : lead + step));
                if (multipleMode) {
                    lead = row;
                    scrollTo(row);
                } else if (!isIndexSelected(row)) {
                    userToggle(row);
                }
                break;
            }
            case KeyEvent.VK_SPACE:
                if (multipleMode && lead >= 0) userToggle(lead);
                break;
        }
    }

    /** A user selection: what the peer does to the selection, then the ItemEvent it reports. */
    private void userToggle(int row) {
        int change;
        synchronized (this) {
            if (row < 0 || row >= items.size()) return;
            if (isIndexSelected(row)) {
                if (!multipleMode) {
                    lead = row;
                    return;
                }
                deselect(row);
                change = ItemEvent.DESELECTED;
            } else {
                select(row);
                change = ItemEvent.SELECTED;
            }
            lead = row;
            scrollTo(row);
        }
        dispatchEvent(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, Integer.valueOf(row), change));
    }

    private void fireAction(int row, long when, int modifiers) {
        String item;
        synchronized (this) {
            if (row < 0 || row >= items.size()) return;
            item = getItem(row);
        }
        dispatchEvent(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, item, when, modifiers));
    }

    @Override
    public void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        Color well = AwtWidgets.well(this), fg = AwtWidgets.foreground(this);
        g.setColor(well);
        g.fillRect(0, 0, w, h);
        AwtWidgets.bevel(g, well, 0, 0, w, h, false);
        g.setFont(AwtWidgets.font(this));
        FontMetrics fm = g.getFontMetrics();
        int rowHeight = rowHeight(fm);
        boolean scrolls;
        int visible;
        String[] shown;
        int first;
        boolean[] marked;
        synchronized (this) {
            visible = visibleRows();
            scrolls = items.size() > visible;
            first = top;
            int count = Math.max(0, Math.min(visible + 1, items.size() - first));
            shown = new String[count];
            marked = new boolean[count];
            for (int i = 0; i < count; i++) {
                shown[i] = items.elementAt(first + i);
                marked[i] = isIndexSelected(first + i);
            }
        }
        int rowWidth = w - 2 * MARGIN - 2 - (scrolls ? AwtWidgets.SCROLLBAR_THICKNESS : 0);
        Graphics rowsG = g.create(MARGIN + 1, MARGIN + 1, Math.max(0, rowWidth), Math.max(0, h - 2 * MARGIN - 2));
        try {
            for (int i = 0; i < shown.length; i++) {
                int y = i * rowHeight;
                if (marked[i]) {
                    rowsG.setColor(AwtWidgets.shade(well, 0.25f));
                    rowsG.fillRect(0, y, rowWidth, rowHeight);
                }
                rowsG.setColor(fg);
                AwtWidgets.text(rowsG, fm, shown[i], 3, y, rowHeight);
            }
        } finally {
            rowsG.dispose();
        }
        if (scrolls) {
            int sx = w - AwtWidgets.SCROLLBAR_THICKNESS - 1, track = h - 2;
            Color face = AwtWidgets.face(this);
            g.setColor(AwtWidgets.shade(well, 0.05f));
            g.fillRect(sx, 1, AwtWidgets.SCROLLBAR_THICKNESS, track);
            int total = shown.length == 0 ? 1 : Math.max(1, items.size());
            int thumb = Math.max(8, track * visible / total);
            int thumbY = 1 + (int) ((long) (track - thumb) * first / Math.max(1, total - visible));
            g.setColor(face);
            g.fillRect(sx + 1, thumbY, AwtWidgets.SCROLLBAR_THICKNESS - 2, thumb);
            AwtWidgets.bevel(g, face, sx + 1, thumbY, AwtWidgets.SCROLLBAR_THICKNESS - 2, thumb, true);
        }
    }
}
