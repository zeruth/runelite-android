/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;

/**
 * This is a basic implementation of the <code>ComboPopup</code> interface.
 *
 * This class represents the ui for the popup portion of the combo box.
 * <p>
 * All event handling is handled by listener classes created with the
 * <code>createxxxListener()</code> methods and internal classes.
 * You can change the behavior of this class by overriding the
 * <code>createxxxListener()</code> methods and supplying your own
 * event listeners or subclassing from the ones supplied in this class.
 * <p>
 * Android: the panel host draws the open picker from {@link #getList()} and
 * scrolls it by touch, so {@link #show()} and {@link #hide()} toggle the
 * combo's hosted popup state instead of mounting this menu on a popup layer.
 * The list, its listeners and the sizing hooks behave as in the JDK.
 *
 * @author Tom Santos
 * @author Mark Davidson
 */
public class BasicComboPopup extends JPopupMenu implements ComboPopup {
    private static final long serialVersionUID = 1L;

    // An empty ListModel, this is used when the UI changes to allow
    // the JList to be gc'ed.
    private static class EmptyListModelClass implements ListModel<Object>, Serializable {
        private static final long serialVersionUID = 1L;
        public int getSize() { return 0; }
        public Object getElementAt(int index) { return null; }
        public void addListDataListener(ListDataListener l) {}
        public void removeListDataListener(ListDataListener l) {}
    }

    static final ListModel<Object> EmptyListModel = new EmptyListModelClass();

    private static Border LIST_BORDER = new LineBorder(Color.BLACK, 1);

    /** The combo box this popup belongs to. */
    protected JComboBox<Object> comboBox;
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the accessor methods instead.
     *
     * @see #getList
     * @see #createList
     */
    protected JList<Object> list;
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the create method instead
     *
     * @see #createScroller
     */
    protected JScrollPane scroller;

    /**
     * As of Java 2 platform v1.4 this previously undocumented field is no
     * longer used.
     */
    protected boolean valueIsAdjusting = false;

    // Listeners that are required by the ComboPopup interface

    /** Implementation of all the listener classes. */
    private Handler handler;

    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the accessor or create methods instead.
     *
     * @see #getMouseMotionListener
     * @see #createMouseMotionListener
     */
    protected MouseMotionListener mouseMotionListener;
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the accessor or create methods instead.
     *
     * @see #getMouseListener
     * @see #createMouseListener
     */
    protected MouseListener mouseListener;
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the accessor or create methods instead.
     *
     * @see #getKeyListener
     * @see #createKeyListener
     */
    protected KeyListener keyListener;
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the create method instead.
     *
     * @see #createListSelectionListener
     */
    protected ListSelectionListener listSelectionListener;

    // Listeners that are attached to the list
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the create method instead.
     *
     * @see #createListMouseListener
     */
    protected MouseListener listMouseListener;
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the create method instead
     *
     * @see #createListMouseMotionListener
     */
    protected MouseMotionListener listMouseMotionListener;

    // Added to the combo box for bound properties
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the create method instead
     *
     * @see #createPropertyChangeListener
     */
    protected PropertyChangeListener propertyChangeListener;

    // Added to the combo box model
    /**
     * As of 1.4, this field is no longer used.
     */
    protected ListDataListener listDataListener;

    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Use the create method instead
     *
     * @see #createItemListener
     */
    protected ItemListener itemListener;

    /**
     * This protected field is implementation specific. Do not access directly
     * or override.
     */
    protected Timer autoscrollTimer;
    protected boolean hasEntered = false;
    protected boolean isAutoScrolling = false;
    protected int scrollDirection = SCROLL_UP;

    protected static final int SCROLL_UP = 0;
    protected static final int SCROLL_DOWN = 1;

    //========================================
    // begin ComboPopup method implementations
    //

    /**
     * Implementation of ComboPopup.show(): selects the combo's current item in
     * the list, then opens the hosted picker.
     */
    public void show() {
        setListSelection(comboBox.getSelectedIndex());
        setVisible(true);
        comboBox.hostSetPopupVisible(true);
    }

    /**
     * Implementation of ComboPopup.hide().
     */
    public void hide() {
        setVisible(false);
        comboBox.hostSetPopupVisible(false);
        comboBox.repaint();
    }

    /**
     * Implementation of ComboPopup.getList().
     */
    public JList<Object> getList() {
        return list;
    }

    /**
     * Implementation of ComboPopup.getMouseListener().
     *
     * @return a <code>MouseListener</code> or null
     * @see ComboPopup#getMouseListener
     */
    public MouseListener getMouseListener() {
        if (mouseListener == null) {
            mouseListener = createMouseListener();
        }
        return mouseListener;
    }

    /**
     * Implementation of ComboPopup.getMouseMotionListener().
     *
     * @return a <code>MouseMotionListener</code> or null
     * @see ComboPopup#getMouseMotionListener
     */
    public MouseMotionListener getMouseMotionListener() {
        if (mouseMotionListener == null) {
            mouseMotionListener = createMouseMotionListener();
        }
        return mouseMotionListener;
    }

    /**
     * Implementation of ComboPopup.getKeyListener().
     *
     * @return a <code>KeyListener</code> or null
     * @see ComboPopup#getKeyListener
     */
    public KeyListener getKeyListener() {
        if (keyListener == null) {
            keyListener = createKeyListener();
        }
        return keyListener;
    }

    /**
     * Called when the UI is uninstalling.  Since this popup isn't in the component
     * tree, it won't get it's uninstallUI() called.  It removes the listeners that
     * were added in addComboBoxListeners().
     */
    public void uninstallingUI() {
        if (isVisible()) {
            hide();
        }
        if (propertyChangeListener != null) {
            comboBox.removePropertyChangeListener(propertyChangeListener);
        }
        if (itemListener != null) {
            comboBox.removeItemListener(itemListener);
        }
        uninstallComboBoxModelListeners(comboBox.getModel());
        uninstallKeyboardActions();
        uninstallListListeners();
        stopAutoScrolling();
        // We do this, otherwise the listener the ui installs on
        // the model (the combobox model in this case) will keep a
        // reference to the list, causing the list (and us) to never get gced.
        list.setModel(EmptyListModel);
    }

    //
    // end ComboPopup method implementations
    //======================================

    /**
     * Removes the listeners from the combo box model
     *
     * @param model The combo box model to install listeners
     * @see #installComboBoxModelListeners
     */
    protected void uninstallComboBoxModelListeners(ComboBoxModel<?> model) {
        if (model != null && listDataListener != null) {
            model.removeListDataListener(listDataListener);
        }
    }

    /** As in the JDK 8 delegate, no popup keyboard actions are registered. */
    protected void uninstallKeyboardActions() {
    }

    //===================================================================
    // begin Initialization routines
    //
    public BasicComboPopup(JComboBox<Object> combo) {
        super();
        setName("ComboPopup.popup");
        comboBox = combo;

        setLightWeightPopupEnabled(comboBox.isLightWeightPopupEnabled());

        // UI construction of the popup.
        list = createList();
        list.setName("ComboBox.list");
        configureList();
        scroller = createScroller();
        scroller.setName("ComboBox.scrollPane");
        configureScroller();
        configurePopup();

        installComboBoxListeners();
        installKeyboardActions();
    }

    /**
     * Overridden to unconditionally return false: the popup never takes focus
     * from its combo box.
     */
    @SuppressWarnings("deprecation")
    public boolean isFocusTraversable() {
        return false;
    }

    /**
     * Creates a listener
     * that will watch for mouse-press and release events on the combo box.
     *
     * <strong>Warning:</strong>
     * When overriding this method, make sure to maintain the existing
     * behavior.
     *
     * @return a <code>MouseListener</code> which will be added to
     * the combo box or null
     */
    protected MouseListener createMouseListener() {
        return getHandler();
    }

    /**
     * Creates the mouse motion listener which will be added to the combo
     * box.
     *
     * <strong>Warning:</strong>
     * When overriding this method, make sure to maintain the existing
     * behavior.
     *
     * @return a <code>MouseMotionListener</code> which will be added to
     *         the combo box or null
     */
    protected MouseMotionListener createMouseMotionListener() {
        return getHandler();
    }

    /**
     * Creates the key listener that will be added to the combo box. If
     * this method returns null then it will not be added to the combo box.
     *
     * @return a <code>KeyListener</code> or null
     */
    protected KeyListener createKeyListener() {
        return null;
    }

    /**
     * Creates a list selection listener that watches for selection changes in
     * the popup's list.  If this method returns null then it will not
     * be added to the popup list.
     *
     * @return an instance of a <code>ListSelectionListener</code> or null
     */
    protected ListSelectionListener createListSelectionListener() {
        return null;
    }

    /**
     * Creates a list data listener which will be added to the
     * <code>ComboBoxModel</code>. If this method returns null then
     * it will not be added to the combo box model.
     *
     * @return an instance of a <code>ListDataListener</code> or null
     */
    protected ListDataListener createListDataListener() {
        return null;
    }

    /**
     * Creates a mouse listener that watches for mouse events in
     * the popup's list. If this method returns null then it will
     * not be added to the combo box.
     *
     * @return an instance of a <code>MouseListener</code> or null
     */
    protected MouseListener createListMouseListener() {
        return getHandler();
    }

    /**
     * Creates a mouse motion listener that watches for mouse motion
     * events in the popup's list. If this method returns null then it will
     * not be added to the combo box.
     *
     * @return an instance of a <code>MouseMotionListener</code> or null
     */
    protected MouseMotionListener createListMouseMotionListener() {
        return getHandler();
    }

    /**
     * Creates a <code>PropertyChangeListener</code> which will be added to
     * the combo box. If this method returns null then it will not
     * be added to the combo box.
     *
     * @return an instance of a <code>PropertyChangeListener</code> or null
     */
    protected PropertyChangeListener createPropertyChangeListener() {
        return getHandler();
    }

    /**
     * Creates an <code>ItemListener</code> which will be added to the
     * combo box. If this method returns null then it will not
     * be added to the combo box.
     * <p>
     * Subclasses may override this method to return instances of their own
     * ItemEvent handlers.
     *
     * @return an instance of an <code>ItemListener</code> or null
     */
    protected ItemListener createItemListener() {
        return getHandler();
    }

    private Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    /**
     * Creates the JList used in the popup to display
     * the items in the combo box model. This method is called when the UI class
     * is created.
     *
     * @return a <code>JList</code> used to display the combo box items
     */
    protected JList<Object> createList() {
        return new JList<Object>(comboBox.getModel());
    }

    /**
     * Configures the list which is used to hold the combo box items in the
     * popup. This method is called when the UI class
     * is created.
     *
     * @see #createList
     */
    protected void configureList() {
        list.setFont(comboBox.getFont());
        list.setForeground(comboBox.getForeground());
        list.setBackground(comboBox.getBackground());
        Color selectionForeground = UIManager.getColor("ComboBox.selectionForeground");
        if (selectionForeground != null) {
            list.setSelectionForeground(selectionForeground);
        }
        Color selectionBackground = UIManager.getColor("ComboBox.selectionBackground");
        if (selectionBackground != null) {
            list.setSelectionBackground(selectionBackground);
        }
        list.setBorder(null);
        list.setCellRenderer(comboBox.getRenderer());
        list.setFocusable(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setListSelection(comboBox.getSelectedIndex());
        installListListeners();
    }

    /**
     * Adds the listeners to the list control.
     */
    protected void installListListeners() {
        if ((listMouseListener = createListMouseListener()) != null) {
            list.addMouseListener(listMouseListener);
        }
        if ((listMouseMotionListener = createListMouseMotionListener()) != null) {
            list.addMouseMotionListener(listMouseMotionListener);
        }
        if ((listSelectionListener = createListSelectionListener()) != null) {
            list.addListSelectionListener(listSelectionListener);
        }
    }

    void uninstallListListeners() {
        if (listMouseListener != null) {
            list.removeMouseListener(listMouseListener);
            listMouseListener = null;
        }
        if (listMouseMotionListener != null) {
            list.removeMouseMotionListener(listMouseMotionListener);
            listMouseMotionListener = null;
        }
        if (listSelectionListener != null) {
            list.removeListSelectionListener(listSelectionListener);
            listSelectionListener = null;
        }
        handler = null;
    }

    /**
     * Creates the scroll pane which houses the scrollable list.
     */
    protected JScrollPane createScroller() {
        return new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

    /**
     * Configures the scrollable portion which holds the list within
     * the combo box popup. This method is called when the UI class
     * is created.
     */
    protected void configureScroller() {
        scroller.setFocusable(false);
        scroller.getVerticalScrollBar().setFocusable(false);
        scroller.setBorder(null);
    }

    /**
     * Configures the popup portion of the combo box. This method is called
     * when the UI class is created.
     */
    protected void configurePopup() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorderPainted(true);
        setBorder(LIST_BORDER);
        setOpaque(false);
        add(scroller);
        setDoubleBuffered(true);
        setFocusable(false);
        // The JDK sets the invoker when show() places the menu; the hosted picker
        // never calls show(Component, int, int), so the combo is the invoker up front.
        setInvoker(comboBox);
    }

    /**
     * This method adds the necessary listeners to the JComboBox.
     */
    protected void installComboBoxListeners() {
        if ((propertyChangeListener = createPropertyChangeListener()) != null) {
            comboBox.addPropertyChangeListener(propertyChangeListener);
        }
        if ((itemListener = createItemListener()) != null) {
            comboBox.addItemListener(itemListener);
        }
        installComboBoxModelListeners(comboBox.getModel());
    }

    /**
     * Installs the listeners on the combo box model. Any listeners installed
     * on the combo box model should be removed in
     * <code>uninstallComboBoxModelListeners</code>.
     *
     * @param model The combo box model to install listeners
     * @see #uninstallComboBoxModelListeners
     */
    protected void installComboBoxModelListeners(ComboBoxModel<?> model) {
        if (model != null && (listDataListener = createListDataListener()) != null) {
            model.addListDataListener(listDataListener);
        }
    }

    /** As in the JDK 8 delegate, no popup keyboard actions are registered. */
    protected void installKeyboardActions() {
    }

    //
    // end Initialization routines
    //=================================================================


    //===================================================================
    // begin Event Listenters
    //

    private class Handler implements ItemListener, MouseListener, MouseMotionListener,
            PropertyChangeListener, Serializable {
        private static final long serialVersionUID = 1L;

        //
        // MouseListener
        // NOTE: this is added to both the JList and JComboBox
        //
        public void mouseClicked(MouseEvent e) {
        }

        public void mousePressed(MouseEvent e) {
            if (e.getSource() == list) {
                return;
            }
            if (!SwingUtilities.isLeftMouseButton(e) || !comboBox.isEnabled()) {
                return;
            }
            if (comboBox.isEditable()) {
                Component comp = comboBox.getEditor().getEditorComponent();
                if ((!(comp instanceof javax.swing.JComponent))
                        || ((javax.swing.JComponent) comp).isRequestFocusEnabled()) {
                    comp.requestFocus();
                }
            } else if (comboBox.isRequestFocusEnabled()) {
                comboBox.requestFocus();
            }
            togglePopup();
        }

        public void mouseReleased(MouseEvent e) {
            if (e.getSource() == list) {
                if (list.getModel().getSize() > 0) {
                    // JList mouse listener
                    if (comboBox.getSelectedIndex() == list.getSelectedIndex()) {
                        comboBox.getEditor().setItem(list.getSelectedValue());
                    }
                    comboBox.setSelectedIndex(list.getSelectedIndex());
                }
                comboBox.setPopupVisible(false);
                // workaround for cancelling an edited item (JDK bug 4530953)
                if (comboBox.isEditable() && comboBox.getEditor() != null) {
                    comboBox.configureEditor(comboBox.getEditor(), comboBox.getSelectedItem());
                }
                return;
            }
            stopAutoScrolling();
        }

        public void mouseEntered(MouseEvent e) {
        }

        public void mouseExited(MouseEvent e) {
        }

        //
        // MouseMotionListener:
        // NOTE: this is added to both the List and ComboBox
        //
        public void mouseMoved(MouseEvent anEvent) {
            if (anEvent.getSource() == list) {
                Point location = anEvent.getPoint();
                Rectangle r = list.getVisibleRect();
                if (r.contains(location)) {
                    updateListBoxSelectionForEvent(anEvent, false);
                }
            }
        }

        public void mouseDragged(MouseEvent e) {
            if (e.getSource() == list) {
                return;
            }
            if (isVisible()) {
                MouseEvent newEvent = convertMouseEvent(e);
                Rectangle r = list.getVisibleRect();

                if (newEvent.getPoint().y >= r.y && newEvent.getPoint().y <= r.y + r.height - 1) {
                    hasEntered = true;
                    if (isAutoScrolling) {
                        stopAutoScrolling();
                    }
                    Point location = newEvent.getPoint();
                    if (r.contains(location)) {
                        updateListBoxSelectionForEvent(newEvent, false);
                    }
                } else {
                    if (hasEntered) {
                        int directionToScroll = newEvent.getPoint().y < r.y ? SCROLL_UP : SCROLL_DOWN;
                        if (isAutoScrolling && scrollDirection != directionToScroll) {
                            stopAutoScrolling();
                            startAutoScrolling(directionToScroll);
                        } else if (!isAutoScrolling) {
                            startAutoScrolling(directionToScroll);
                        }
                    } else {
                        if (e.getPoint().y < 0) {
                            hasEntered = true;
                            startAutoScrolling(SCROLL_UP);
                        }
                    }
                }
            }
        }

        //
        // PropertyChangeListener
        //
        public void propertyChange(PropertyChangeEvent e) {
            @SuppressWarnings("unchecked")
            JComboBox<Object> comboBox = (JComboBox<Object>) e.getSource();
            String propertyName = e.getPropertyName();

            if ("model".equals(propertyName)) {
                @SuppressWarnings("unchecked")
                ComboBoxModel<Object> oldModel = (ComboBoxModel<Object>) e.getOldValue();
                @SuppressWarnings("unchecked")
                ComboBoxModel<Object> newModel = (ComboBoxModel<Object>) e.getNewValue();
                uninstallComboBoxModelListeners(oldModel);
                installComboBoxModelListeners(newModel);

                list.setModel(newModel);

                if (isVisible()) {
                    hide();
                }
            } else if ("renderer".equals(propertyName)) {
                @SuppressWarnings("unchecked")
                ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) e.getNewValue();
                list.setCellRenderer(renderer);
                if (isVisible()) {
                    hide();
                }
            } else if ("componentOrientation".equals(propertyName)) {
                // Pass along the new component orientation
                // to the list and the scroller
                java.awt.ComponentOrientation o = (java.awt.ComponentOrientation) e.getNewValue();
                JList<Object> list = getList();
                if (list != null && list.getComponentOrientation() != o) {
                    list.setComponentOrientation(o);
                }
                if (scroller != null && scroller.getComponentOrientation() != o) {
                    scroller.setComponentOrientation(o);
                }
                if (o != getComponentOrientation()) {
                    setComponentOrientation(o);
                }
            } else if ("lightWeightPopupEnabled".equals(propertyName)) {
                setLightWeightPopupEnabled(comboBox.isLightWeightPopupEnabled());
            } else if ("font".equals(propertyName)) {
                list.setFont(comboBox.getFont());
            }
        }

        //
        // ItemListener
        //
        public void itemStateChanged(ItemEvent e) {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                @SuppressWarnings("unchecked")
                JComboBox<Object> comboBox = (JComboBox<Object>) e.getSource();
                setListSelection(comboBox.getSelectedIndex());
            } else {
                setListSelection(-1);
            }
        }
    }

    //
    // end Event Listeners
    //=================================================================


    //===================================================================
    // begin Autoscroll methods
    //

    /**
     * This protected method is implementation specific and should be private.
     * do not call or override.
     */
    protected void startAutoScrolling(int direction) {
        // XXX - should be a private method within InvocationMouseMotionHandler
        // if possible.
        if (isAutoScrolling) {
            autoscrollTimer.stop();
        }

        isAutoScrolling = true;

        if (direction == SCROLL_UP) {
            scrollDirection = SCROLL_UP;
            Point convertedPoint = SwingUtilities.convertPoint(scroller, new Point(1, 1), list);
            int top = list.locationToIndex(convertedPoint);
            list.setSelectedIndex(top);

            autoscrollTimer = new Timer(100, new AutoScrollActionHandler(SCROLL_UP));
        } else if (direction == SCROLL_DOWN) {
            scrollDirection = SCROLL_DOWN;
            Point convertedPoint = SwingUtilities.convertPoint(scroller,
                    new Point(1, (scroller.getHeight() - 1) - 2), list);
            int bottom = list.locationToIndex(convertedPoint);
            list.setSelectedIndex(bottom);

            autoscrollTimer = new Timer(100, new AutoScrollActionHandler(SCROLL_DOWN));
        }
        autoscrollTimer.start();
    }

    /**
     * This protected method is implementation specific and should be private.
     * do not call or override.
     */
    protected void stopAutoScrolling() {
        isAutoScrolling = false;

        if (autoscrollTimer != null) {
            autoscrollTimer.stop();
            autoscrollTimer = null;
        }
    }

    /**
     * This protected method is implementation specific and should be private.
     * do not call or override.
     */
    protected void autoScrollUp() {
        int index = list.getSelectedIndex();
        if (index > 0) {
            list.setSelectedIndex(index - 1);
            list.ensureIndexIsVisible(index - 1);
        }
    }

    /**
     * This protected method is implementation specific and should be private.
     * do not call or override.
     */
    protected void autoScrollDown() {
        int index = list.getSelectedIndex();
        int lastItem = list.getModel().getSize() - 1;
        if (index < lastItem) {
            list.setSelectedIndex(index + 1);
            list.ensureIndexIsVisible(index + 1);
        }
    }

    //
    // end Autoscroll methods
    //=================================================================


    //===================================================================
    // begin Utility methods
    //

    /**
     * This is is a utility method that helps event handlers figure out where to
     * send the focus when the popup is brought up.  The standard implementation
     * delegates the focus to the editor (if the combo box is editable) or to
     * the JComboBox if it is not editable.
     */
    protected void delegateFocus(MouseEvent e) {
        if (comboBox.isEditable()) {
            Component comp = comboBox.getEditor().getEditorComponent();
            if ((!(comp instanceof javax.swing.JComponent))
                    || ((javax.swing.JComponent) comp).isRequestFocusEnabled()) {
                comp.requestFocus();
            }
        } else if (comboBox.isRequestFocusEnabled()) {
            comboBox.requestFocus();
        }
    }

    /**
     * Makes the popup visible if it is hidden and makes it hidden if it is
     * visible.
     */
    protected void togglePopup() {
        if (isVisible()) {
            hide();
        } else {
            show();
        }
    }

    /**
     * Sets the list selection index to the selectedIndex. This
     * method is used to synchronize the list selection with the
     * combo box selection.
     *
     * @param selectedIndex the index to set the list
     */
    private void setListSelection(int selectedIndex) {
        if (selectedIndex == -1) {
            list.clearSelection();
        } else {
            list.setSelectedIndex(selectedIndex);
            list.ensureIndexIsVisible(selectedIndex);
        }
    }

    protected MouseEvent convertMouseEvent(MouseEvent e) {
        Point convertedPoint = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), list);
        @SuppressWarnings("deprecation")
        MouseEvent newEvent = new MouseEvent((Component) e.getSource(), e.getID(), e.getWhen(),
                e.getModifiers(), convertedPoint.x, convertedPoint.y, e.getClickCount(),
                e.isPopupTrigger());
        return newEvent;
    }

    /**
     * Retrieves the height of the popup based on the current
     * ListCellRenderer and the maximum row count.
     */
    protected int getPopupHeightForRowCount(int maxRowCount) {
        // Set the cached value of the minimum row count
        int minRowCount = Math.min(maxRowCount, comboBox.getItemCount());
        int height = 0;
        ListCellRenderer<? super Object> renderer = list.getCellRenderer();
        Object value = null;

        for (int i = 0; i < minRowCount; ++i) {
            value = list.getModel().getElementAt(i);
            Component c = renderer.getListCellRendererComponent(list, value, i, false, false);
            height += c.getPreferredSize().height;
        }

        if (height == 0) {
            height = comboBox.getHeight();
        }

        // The JDK passes a null component; the shadow borders size against the scroller.
        Border border = scroller.getViewportBorder();
        if (border != null) {
            Insets insets = border.getBorderInsets(scroller);
            height += insets.top + insets.bottom;
        }

        border = scroller.getBorder();
        if (border != null) {
            Insets insets = border.getBorderInsets(scroller);
            height += insets.top + insets.bottom;
        }

        return height;
    }

    /**
     * Calculate the placement and size of the popup portion of the combo box based
     * on the combo box location and the enclosing screen bounds. If
     * no transformations are required, then the returned rectangle will
     * have the same values as the parameters.
     * <p>
     * Android: there is no screen to fit against; the panel host places the
     * picker itself, so the requested bounds come back unchanged.
     *
     * @param px starting x location
     * @param py starting y location
     * @param pw starting width
     * @param ph starting height
     * @return a rectangle which represents the placement and size of the popup
     */
    protected Rectangle computePopupBounds(int px, int py, int pw, int ph) {
        return new Rectangle(px, py, pw, ph);
    }

    /**
     * A utility method used by the event listeners.  Given a mouse event, it changes
     * the list selection to the list item below the mouse.
     */
    protected void updateListBoxSelectionForEvent(MouseEvent anEvent, boolean shouldScroll) {
        // XXX - only seems to be called from this class. shouldScroll flag is
        // never true
        Point location = anEvent.getPoint();
        if (list == null) {
            return;
        }
        int index = list.locationToIndex(location);
        if (index == -1) {
            if (location.y < 0) {
                index = 0;
            } else {
                index = comboBox.getModel().getSize() - 1;
            }
        }
        if (list.getSelectedIndex() != index) {
            list.setSelectedIndex(index);
            if (shouldScroll) {
                list.ensureIndexIsVisible(index);
            }
        }
    }

    //
    // end Utility methods
    //=================================================================

    /**
     * This class is the action handler for the autoscroll timer.
     */
    private class AutoScrollActionHandler implements ActionListener {
        private int direction;

        AutoScrollActionHandler(int direction) {
            this.direction = direction;
        }

        public void actionPerformed(ActionEvent e) {
            if (direction == SCROLL_UP) {
                autoScrollUp();
            } else {
                autoScrollDown();
            }
        }
    }
}
