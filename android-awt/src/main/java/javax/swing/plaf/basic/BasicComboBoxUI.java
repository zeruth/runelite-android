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

package javax.swing.plaf.basic;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import javax.swing.*;
import javax.swing.plaf.*;
import sun.swing.UIAction;

/**
 * Basic UI implementation for JComboBox.
 * <p>
 * The combo box is a compound component which means that it is an aggregate of
 * many simpler components. This class creates and manages the listeners
 * on the combo box and the combo box model. These listeners update the user
 * interface in response to changes in the properties and state of the combo box.
 * <p>
 * Android: the arrow button and the editor are children as in the JDK, but the
 * panel host routes a tap anywhere in the combo to the combo itself and draws
 * the open picker from the popup's list. Sizing stays with {@code JComboBox}'s
 * own measurement, and ENTER clicks the root pane's default button directly
 * because the shadow root pane has no WHEN_IN_FOCUSED_WINDOW "press" binding.
 *
 * @author Arnaud Weber
 * @author Tom Santos
 * @author Mark Davidson
 */
public class BasicComboBoxUI extends ComboBoxUI {
    protected JComboBox<Object> comboBox;
    /**
     * This protected field is implementation specific. Do not access directly
     * or override.
     */
    protected boolean hasFocus = false;

    // Control the selection behavior of the JComboBox when it is used
    // in the JTable DefaultCellEditor.
    private boolean isTableCellEditor = false;
    private static final String IS_TABLE_CELL_EDITOR = "JComboBox.isTableCellEditor";

    // This list is for drawing the current item in the combo box.
    protected JList<Object> listBox;

    // The implementation of ComboPopup that is used to show the popup.
    protected ComboPopup popup;

    // The Component that the ComboBoxEditor uses for editing
    protected Component editor;

    // The arrow button that invokes the popup.
    protected JButton arrowButton;

    /**
     * Used to render the currently selected item in the combo box.
     * It doesn't have anything to do with the popup's rendering.
     */
    protected CellRendererPane currentValuePane = new CellRendererPane();

    // Listeners that are attached to the JComboBox
    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Override the listener construction method instead.
     *
     * @see #createKeyListener
     */
    protected KeyListener keyListener;

    /**
     * If true the arrow button is as wide as it is tall, as in the JDK.
     *
     * @since 1.7
     */
    protected boolean squareButton = true;

    /**
     * Padding between the renderer and the edges of the current value area.
     *
     * @since 1.7
     */
    protected Insets padding;

    public static ComponentUI createUI(JComponent c) {
        return new BasicComboBoxUI();
    }

    static void loadActionMap(LazyActionMap map) {
        map.put(new Actions(Actions.HIDE));
        map.put(new Actions(Actions.PAGE_DOWN));
        map.put(new Actions(Actions.PAGE_UP));
        map.put(new Actions(Actions.HOME));
        map.put(new Actions(Actions.END));
        map.put(new Actions(Actions.DOWN));
        map.put(new Actions(Actions.DOWN_2));
        map.put(new Actions(Actions.TOGGLE));
        map.put(new Actions(Actions.TOGGLE_2));
        map.put(new Actions(Actions.UP));
        map.put(new Actions(Actions.UP_2));
        map.put(new Actions(Actions.ENTER));
    }

    //========================
    // begin UI Initialization
    //

    @SuppressWarnings("unchecked")
    @Override public void installUI(JComponent c) {
        comboBox = (JComboBox<Object>) c;
        installDefaults();
        popup = createPopup();
        listBox = popup.getList();

        // Is this combo box a cell editor?
        Boolean inTable = (Boolean) c.getClientProperty(IS_TABLE_CELL_EDITOR);
        if (inTable != null) {
            isTableCellEditor = inTable.equals(Boolean.TRUE);
        }

        // As the JDK: only a renderer or editor the look and feel put there is replaced, so a
        // subclass's createEditor (FlatLaf's) wins over the plain one.
        ListCellRenderer<?> renderer = comboBox.getRenderer();
        if (renderer == null || renderer instanceof UIResource) {
            comboBox.setRenderer(createRenderer());
        }
        ComboBoxEditor editor = comboBox.getEditor();
        if (editor == null || editor instanceof UIResource) {
            comboBox.setEditor(createEditor());
        }

        installComponents();
        comboBox.setLayout(createLayoutManager());
        installListeners();
        installKeyboardActions();
    }

    @Override public void uninstallUI(JComponent c) {
        uninstallKeyboardActions();
        uninstallListeners();
        uninstallComponents();
        popup.uninstallingUI();
        if (comboBox.getLayout() instanceof ComboBoxLayoutManager) {
            comboBox.setLayout(null);
        }
        comboBox = null; arrowButton = null; popup = null; listBox = null; editor = null;
    }

    /**
     * Installs the default colors, default font, default renderer, and default
     * editor into the JComboBox.
     */
    protected void installDefaults() {
        LookAndFeel.installProperty(comboBox, "opaque", Boolean.TRUE);
        squareButton = UIManager.get("ComboBox.squareButton") instanceof Boolean
                ? (Boolean) UIManager.get("ComboBox.squareButton") : true;
        padding = UIManager.getInsets("ComboBox.padding");
    }

    /**
     * Creates and initializes the components which make up the
     * aggregate combo box. This method is called as part of the UI
     * installation process.
     */
    protected void installComponents() {
        arrowButton = createArrowButton();
        if (arrowButton != null) {
            comboBox.add(arrowButton);
            configureArrowButton();
        }
        if (comboBox.isEditable()) {
            addEditor();
        }
        comboBox.add(currentValuePane);
    }

    /**
     * The aggregate components which comprise the combo box are
     * unregistered and uninitialized. This method is called as part of the
     * UI uninstallation process.
     */
    protected void uninstallComponents() {
        if (arrowButton != null) {
            unconfigureArrowButton();
        }
        if (editor != null) {
            unconfigureEditor();
        }
        comboBox.removeAll(); // Just to be safe.
        arrowButton = null;
    }

    /**
     * This public method is implementation specific and should be private.
     * do not call or override. To implement a specific editor create a
     * custom <code>ComboBoxEditor</code>
     *
     * @see #createEditor
     * @see javax.swing.JComboBox#setEditor
     * @see javax.swing.ComboBoxEditor
     */
    public void addEditor() {
        removeEditor();
        ComboBoxEditor cbEditor = comboBox.getEditor();
        editor = cbEditor == null ? null : cbEditor.getEditorComponent();
        if (editor != null) {
            configureEditor();
            comboBox.add(editor);
            if (comboBox.isFocusOwner()) {
                // Switch focus to the editor component
                editor.requestFocusInWindow();
            }
        }
    }

    /**
     * This public method is implementation specific and should be private.
     * do not call or override.
     *
     * @see #addEditor
     */
    public void removeEditor() {
        if (editor != null) {
            unconfigureEditor();
            comboBox.remove(editor);
            editor = null;
        }
    }

    /**
     * This protected method is implementation specific and should be private.
     * do not call or override.
     *
     * @see #addEditor
     */
    protected void configureEditor() {
        // Should be in the same state as the combobox
        editor.setEnabled(comboBox.isEnabled());
        editor.setFocusable(comboBox.isFocusable());
        editor.setFont(comboBox.getFont());
        // JComboBox registers its own action listener on the ComboBoxEditor
        // (setEditor), which commits the edit as the JDK's handler does.
        comboBox.configureEditor(comboBox.getEditor(), comboBox.getSelectedItem());
    }

    /**
     * This protected method is implementation specific and should be private.
     * Do not call or override.
     *
     * @see #addEditor
     */
    protected void unconfigureEditor() {
    }

    /**
     * This protected field is implementation specific. Do not access directly
     * or override. Override the listener construction method instead.
     *
     * @see #createPropertyChangeListener
     */
    protected java.beans.PropertyChangeListener propertyChangeListener;

    /**
     * Creates a <code>PropertyChangeListener</code> which will be added to
     * the combo box. If this method returns null then it will not
     * be added to the combo box.
     *
     * @return an instance of a <code>PropertyChangeListener</code> or null
     */
    protected java.beans.PropertyChangeListener createPropertyChangeListener() {
        return new PropertyChangeHandler();
    }

    /**
     * This listener watches for bound properties that have changed in the
     * combo box: the editor and the editable flag add or remove the editor
     * child, and enabled/focusable are passed on to the children.
     * <p>
     * This public inner class should be treated as protected.
     * Instantiate it only within subclasses of
     * <code>BasicComboBoxUI</code>.
     */
    public class PropertyChangeHandler implements java.beans.PropertyChangeListener {
        public void propertyChange(java.beans.PropertyChangeEvent e) {
            String propertyName = e.getPropertyName();
            if (comboBox == null) {
                return;
            }
            if ("editor".equals(propertyName) && comboBox.isEditable()) {
                addEditor();
                comboBox.revalidate();
            } else if ("editable".equals(propertyName)) {
                if (comboBox.isEditable()) {
                    comboBox.setRequestFocusEnabled(false);
                    addEditor();
                } else {
                    comboBox.setRequestFocusEnabled(true);
                    removeEditor();
                }
                comboBox.revalidate();
            } else if ("enabled".equals(propertyName)) {
                boolean enabled = comboBox.isEnabled();
                if (editor != null) {
                    editor.setEnabled(enabled);
                }
                if (arrowButton != null) {
                    arrowButton.setEnabled(enabled);
                }
                comboBox.repaint();
            } else if ("focusable".equals(propertyName)) {
                boolean focusable = comboBox.isFocusable();
                if (editor != null) {
                    editor.setFocusable(focusable);
                }
                if (arrowButton != null) {
                    arrowButton.setFocusable(focusable);
                }
                comboBox.repaint();
            } else if ("font".equals(propertyName)) {
                if (editor != null) {
                    editor.setFont(comboBox.getFont());
                }
                comboBox.revalidate();
            } else if ("componentOrientation".equals(propertyName)) {
                comboBox.revalidate();
            }
        }
    }

    protected ComboPopup createPopup() { return new BasicComboPopup(comboBox); }

    /**
     * Creates a <code>KeyListener</code> which will be added to the
     * combo box. If this method returns null then it will not be added
     * to the combo box.
     *
     * @return an instance <code>KeyListener</code> or null
     */
    protected KeyListener createKeyListener() {
        return new KeyHandler();
    }

    /**
     * Creates a layout manager for managing the components which make up the
     * combo box.
     *
     * @return an instance of a layout manager
     */
    protected LayoutManager createLayoutManager() {
        return new ComboBoxLayoutManager();
    }

    /**
     * Creates the default renderer that will be used in a non-editiable combo
     * box. A default renderer will used only if a renderer has not been
     * explicitly set with <code>setRenderer</code>.
     *
     * @return a <code>ListCellRender</code> used for the combo box
     * @see javax.swing.JComboBox#setRenderer
     */
    @SuppressWarnings("rawtypes")
    protected ListCellRenderer createRenderer() {
        return new BasicComboBoxRenderer.UIResource();
    }

    /**
     * Creates the default editor that will be used in editable combo boxes.
     * A default editor will be used only if an editor has not been
     * explicitly set with <code>setEditor</code>.
     *
     * @return a <code>ComboBoxEditor</code> used for the combo box
     * @see javax.swing.JComboBox#setEditor
     */
    protected ComboBoxEditor createEditor() {
        return new BasicComboBoxEditor.UIResource();
    }

    protected void installListeners() {
        if ((propertyChangeListener = createPropertyChangeListener()) != null) {
            comboBox.addPropertyChangeListener(propertyChangeListener);
        }
        if ((keyListener = createKeyListener()) != null) {
            comboBox.addKeyListener(keyListener);
        }
        if (popup.getMouseListener() != null) comboBox.addMouseListener(popup.getMouseListener());
        if (popup.getMouseMotionListener() != null) comboBox.addMouseMotionListener(popup.getMouseMotionListener());
        if (popup.getKeyListener() != null) comboBox.addKeyListener(popup.getKeyListener());
    }

    protected void uninstallListeners() {
        if (propertyChangeListener != null) {
            comboBox.removePropertyChangeListener(propertyChangeListener);
            propertyChangeListener = null;
        }
        if (keyListener != null) {
            comboBox.removeKeyListener(keyListener);
            keyListener = null;
        }
        if (popup.getMouseListener() != null) comboBox.removeMouseListener(popup.getMouseListener());
        if (popup.getMouseMotionListener() != null) comboBox.removeMouseMotionListener(popup.getMouseMotionListener());
        if (popup.getKeyListener() != null) comboBox.removeKeyListener(popup.getKeyListener());
    }

    /**
     * Adds keyboard actions to the JComboBox.  Actions on enter and esc are already
     * supplied.  Add more actions as you need them.
     */
    protected void installKeyboardActions() {
        InputMap km = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        SwingUtilities.replaceUIInputMap(comboBox, JComponent.
                             WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, km);

        LazyActionMap.installLazyActionMap(comboBox, BasicComboBoxUI.class,
                                           "ComboBox.actionMap");
    }

    InputMap getInputMap(int condition) {
        if (condition == JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
            return (InputMap) UIManager.get("ComboBox.ancestorInputMap");
        }
        return null;
    }

    boolean isTableCellEditor() {
        return isTableCellEditor;
    }

    /**
     * Removes the focus InputMap and ActionMap.
     */
    protected void uninstallKeyboardActions() {
        SwingUtilities.replaceUIInputMap(comboBox, JComponent.
                                 WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
        SwingUtilities.replaceUIActionMap(comboBox, null);
    }

    public JList<Object> hostPopupList() { return listBox; }

    /**
     * Creates a button which will be used as the control to show or hide
     * the popup portion of the combo box.
     *
     * @return a button which represents the popup control
     */
    protected JButton createArrowButton() {
        java.awt.Color background = UIManager.getColor("ComboBox.buttonBackground");
        java.awt.Color shadow = UIManager.getColor("ComboBox.buttonShadow");
        java.awt.Color darkShadow = UIManager.getColor("ComboBox.buttonDarkShadow");
        java.awt.Color highlight = UIManager.getColor("ComboBox.buttonHighlight");
        // Without a Basic look and feel's defaults the shadow arrow button paints
        // with null colours; its one-argument constructor carries usable ones.
        JButton button = background == null || shadow == null || darkShadow == null || highlight == null
                ? new BasicArrowButton(SwingConstants.SOUTH)
                : new BasicArrowButton(SwingConstants.SOUTH, background, shadow, darkShadow, highlight);
        button.setName("ComboBox.arrowButton");
        return button;
    }

    /**
     * This public method is implementation specific and should be private.
     * do not call or override. To implement a specific arrow button
     * public method is implementation specific and should be private.
     * do not call or override.
     */
    public void configureArrowButton() {
        if ( arrowButton != null ) {
            arrowButton.setEnabled(comboBox.isEnabled());
            arrowButton.setFocusable(comboBox.isFocusable());
            arrowButton.setRequestFocusEnabled(false);
            if (popup.getMouseListener() != null) {
                arrowButton.addMouseListener( popup.getMouseListener() );
            }
            if (popup.getMouseMotionListener() != null) {
                arrowButton.addMouseMotionListener( popup.getMouseMotionListener() );
            }
            arrowButton.resetKeyboardActions();
            arrowButton.setInheritsPopupMenu(true);
        }
    }

    /**
     * This public method is implementation specific and should be private.
     * do not call or override.
     */
    public void unconfigureArrowButton() {
        if ( arrowButton != null ) {
            if (popup.getMouseListener() != null) {
                arrowButton.removeMouseListener( popup.getMouseListener() );
            }
            if (popup.getMouseMotionListener() != null) {
                arrowButton.removeMouseMotionListener( popup.getMouseMotionListener() );
            }
        }
    }

    //
    // end UI Initialization
    //======================


    //=================================
    // begin ComboBoxUI Implementation
    //

    /**
     * Tells if the popup is visible or not.
     */
    @Override public boolean isPopupVisible(JComboBox<?> c) { return popup != null && popup.isVisible(); }

    /**
     * Hides the popup.
     */
    @Override public void setPopupVisible(JComboBox<?> c, boolean visible) {
        if (visible) {
            popup.show();
        } else {
            popup.hide();
        }
    }

    /**
     * Determines if the JComboBox is focus traversable.  If the JComboBox is editable
     * this returns false, otherwise it returns true.
     */
    @Override public boolean isFocusTraversable(JComboBox<?> c) { return c.isEnabled(); }

    //
    // end ComboBoxUI Implementation
    //==============================


    //=================================
    // begin ComponentUI Implementation

    /**
     * Paints the current value of a non-editable combo box; the arrow
     * button and the editor are children and paint themselves.
     * <p>
     * Android: an editable combo still gets the background and outline of
     * {@link #paintCurrentValueBackground}, which the JDK leaves to the look
     * and feel border; {@code JComboBox.paintChildren} redraws the outline
     * over the editor, which would otherwise cover its edges.
     */
    @Override public void paint(Graphics g, JComponent c) {
        hasFocus = comboBox.hasFocus();
        if (arrowButton != null && arrowButton.getHeight() != comboBox.getHeight()
                - getInsets().top - getInsets().bottom) {
            // The layout runs on validation; paint can come first after a resize, and
            // the value rectangle is measured from the arrow button's bounds.
            layoutArrowButton();
        }
        Rectangle r = rectangleForCurrentValue();
        paintCurrentValueBackground(g, r, hasFocus);
        if (!comboBox.isEditable()) {
            paintCurrentValue(g, r, hasFocus);
        }
    }

    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focused) {
        g.setColor(comboBox.getBackground());
        g.fillRect(0, 0, comboBox.getWidth(), comboBox.getHeight());
        g.setColor(comboBox.isEnabled() ? comboBox.getForeground() : java.awt.Color.GRAY);
        g.drawRect(0, 0, comboBox.getWidth() - 1, comboBox.getHeight() - 1);
    }

    public void paintCurrentValue(Graphics g, Rectangle bounds, boolean focused) {
        Rectangle r = bounds;
        if (padding != null) {
            r = new Rectangle(bounds.x + padding.left, bounds.y + padding.top,
                              bounds.width - (padding.left + padding.right),
                              bounds.height - (padding.top + padding.bottom));
        }
        comboBox.hostPaintValue(g, r);
    }

    //
    // end ComponentUI Implementation
    //===============================


    //======================
    // begin Utility Methods
    //

    /**
     * Returns the area that is reserved for drawing the currently selected item.
     */
    protected Rectangle rectangleForCurrentValue() {
        int width = comboBox.getWidth();
        int height = comboBox.getHeight();
        Insets insets = getInsets();
        int buttonSize = height - (insets.top + insets.bottom);
        if ( arrowButton != null ) {
            buttonSize = arrowButton.getWidth();
        }
        if(comboBox.getComponentOrientation().isLeftToRight()) {
            return new Rectangle(insets.left, insets.top,
                             width - (insets.left + insets.right + buttonSize),
                             height - (insets.top + insets.bottom));
        }
        else {
            return new Rectangle(insets.left + buttonSize, insets.top,
                             width - (insets.left + insets.right + buttonSize),
                             height - (insets.top + insets.bottom));
        }
    }

    /**
     * Gets the insets from the JComboBox.
     */
    protected Insets getInsets() {
        return comboBox.getInsets();
    }

    private void layoutArrowButton() {
        int width = comboBox.getWidth();
        int height = comboBox.getHeight();
        Insets insets = getInsets();
        int buttonHeight = height - (insets.top + insets.bottom);
        int buttonWidth = buttonHeight;
        if (arrowButton != null) {
            Insets arrowInsets = arrowButton.getInsets();
            buttonWidth = squareButton ?
                buttonHeight :
                arrowButton.getPreferredSize().width + arrowInsets.left + arrowInsets.right;
            if (comboBox.getComponentOrientation().isLeftToRight()) {
                arrowButton.setBounds(width - (insets.right + buttonWidth),
                                      insets.top, buttonWidth, buttonHeight);
            } else {
                arrowButton.setBounds(insets.left, insets.top,
                                      buttonWidth, buttonHeight);
            }
        }
    }

    //
    // end Utility Methods
    //====================


    //===============================
    // begin Keyboard Action Management
    //

    /**
     * Returns whether or not the supplied keyCode maps to a key that is used for
     * navigation.  This is used for optimizing key input by only passing non-
     * navigation keys to the type-ahead mechanism.  Subclasses should override this
     * if they change the navigation keys.
     */
    protected boolean isNavigationKey( int keyCode ) {
        return keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN ||
               keyCode == KeyEvent.VK_KP_UP || keyCode == KeyEvent.VK_KP_DOWN;
    }

    /**
     * Selects the next item in the list.  It won't change the selection if the
     * currently selected item is already the last item.
     */
    protected void selectNextPossibleValue() {
        int si;

        if ( comboBox.isPopupVisible() ) {
            si = listBox.getSelectedIndex();
        }
        else {
            si = comboBox.getSelectedIndex();
        }

        if ( si < comboBox.getModel().getSize() - 1 ) {
            listBox.setSelectedIndex( si + 1 );
            listBox.ensureIndexIsVisible( si + 1 );
            if ( !isTableCellEditor ) {
                if (!(UIManager.getBoolean("ComboBox.noActionOnKeyNavigation") && comboBox.isPopupVisible())) {
                    comboBox.setSelectedIndex(si+1);
                }
            }
            comboBox.repaint();
        }
    }

    /**
     * Selects the previous item in the list.  It won't change the selection if the
     * currently selected item is already the first item.
     */
    protected void selectPreviousPossibleValue() {
        int si;

        if ( comboBox.isPopupVisible() ) {
            si = listBox.getSelectedIndex();
        }
        else {
            si = comboBox.getSelectedIndex();
        }

        if ( si > 0 ) {
            listBox.setSelectedIndex( si - 1 );
            listBox.ensureIndexIsVisible( si - 1 );
            if ( !isTableCellEditor ) {
                if (!(UIManager.getBoolean("ComboBox.noActionOnKeyNavigation") && comboBox.isPopupVisible())) {
                    comboBox.setSelectedIndex(si-1);
                }
            }
            comboBox.repaint();
        }
    }

    /**
     * Hides the popup if it is showing and shows the popup if it is hidden.
     */
    protected void toggleOpenClose() {
        setPopupVisible(comboBox, !isPopupVisible(comboBox));
    }

    //
    // end Keyboard Action Management
    //===============================


    /**
     * This listener checks to see if the key event isn't a navigation key.  If
     * it finds a key event that wasn't a navigation key it dispatches it to
     * JComboBox.selectWithKeyChar() so that it can do type-ahead.
     *
     * This public inner class should be treated as protected.
     * Instantiate it only within subclasses of
     * <code>BasicComboBoxUI</code>.
     */
    public class KeyHandler extends KeyAdapter {
        @Override public void keyPressed( KeyEvent e ) {
            if (comboBox == null || !comboBox.isEnabled()
                    || comboBox.getModel().getSize() == 0
                    || isNavigationKey(e.getKeyCode())
                    || comboBox.isEditable()) {
                return;
            }
            char c = e.getKeyChar();
            if (c != KeyEvent.CHAR_UNDEFINED && !Character.isISOControl(c)
                    && !e.isControlDown() && !e.isAltDown() && !e.isMetaDown()) {
                if (comboBox.selectWithKeyChar(c)) {
                    e.consume();
                }
            }
        }
    }

    /**
     * This layout manager handles the 'standard' layout of combo boxes.  It puts
     * the arrow button to the right and the editor to the left.  If there is no
     * editor it still keeps the arrow button to the right.
     *
     * This public inner class should be treated as protected.
     * Instantiate it only within subclasses of
     * <code>BasicComboBoxUI</code>.
     */
    public class ComboBoxLayoutManager implements LayoutManager {
        public void addLayoutComponent(String name, Component comp) {}

        public void removeLayoutComponent(Component comp) {}

        public Dimension preferredLayoutSize(Container parent) {
            return parent.getPreferredSize();
        }

        public Dimension minimumLayoutSize(Container parent) {
            return parent.getMinimumSize();
        }

        public void layoutContainer(Container parent) {
            layoutArrowButton();
            if (editor != null) {
                Rectangle cvb = rectangleForCurrentValue();
                editor.setBounds(cvb);
            }
        }
    }

    private static class Actions extends UIAction {
        private static final String HIDE = "hidePopup";
        private static final String DOWN = "selectNext";
        private static final String DOWN_2 = "selectNext2";
        private static final String TOGGLE = "togglePopup";
        private static final String TOGGLE_2 = "spacePopup";
        private static final String UP = "selectPrevious";
        private static final String UP_2 = "selectPrevious2";
        private static final String ENTER = "enterPressed";
        private static final String PAGE_DOWN = "pageDownPassThrough";
        private static final String PAGE_UP = "pageUpPassThrough";
        private static final String HOME = "homePassThrough";
        private static final String END = "endPassThrough";

        Actions(String name) {
            super(name);
        }

        @SuppressWarnings("unchecked")
        public void actionPerformed( ActionEvent e ) {
            String key = getName();
            JComboBox<Object> comboBox = (JComboBox<Object>)e.getSource();
            BasicComboBoxUI ui = comboBox.getUI() instanceof BasicComboBoxUI
                    ? (BasicComboBoxUI) comboBox.getUI() : null;
            if (key == HIDE) {
                comboBox.firePopupMenuCanceled();
                comboBox.setPopupVisible(false);
            }
            else if (key == PAGE_DOWN || key == PAGE_UP ||
                     key == HOME || key == END) {
                int index = getNextIndex(comboBox, key);
                if (index >= 0 && index < comboBox.getItemCount()) {
                    if (UIManager.getBoolean("ComboBox.noActionOnKeyNavigation") && comboBox.isPopupVisible()
                            && ui != null) {
                        ui.listBox.setSelectedIndex(index);
                        ui.listBox.ensureIndexIsVisible(index);
                        comboBox.repaint();
                    } else {
                        comboBox.setSelectedIndex(index);
                    }
                }
            }
            else if (key == DOWN) {
                if (comboBox.isShowing() ) {
                    if ( comboBox.isPopupVisible() ) {
                        if (ui != null) {
                            ui.selectNextPossibleValue();
                        }
                    } else {
                        comboBox.setPopupVisible(true);
                    }
                }
            }
            else if (key == DOWN_2) {
                // Special case in which pressing the arrow keys will not
                // make the popup appear - except for editable combo boxes
                // and combo boxes inside a table.
                if (comboBox.isShowing() ) {
                    if ( (comboBox.isEditable() ||
                            (ui != null && ui.isTableCellEditor()))
                         && !comboBox.isPopupVisible() ) {
                        comboBox.setPopupVisible(true);
                    } else {
                        if (ui != null) {
                            ui.selectNextPossibleValue();
                        }
                    }
                }
            }
            else if (key == TOGGLE || key == TOGGLE_2) {
                if (ui != null && (key == TOGGLE || !comboBox.isEditable())) {
                    if ( ui.isTableCellEditor() ) {
                        // Forces the selection of the list item if the
                        // combo box is in a JTable.
                        comboBox.setSelectedIndex(ui.popup.getList().
                                                  getSelectedIndex());
                    }
                    else {
                        comboBox.setPopupVisible(!comboBox.isPopupVisible());
                    }
                }
            }
            else if (key == UP) {
                if (ui != null) {
                    if (ui.isPopupVisible(comboBox)) {
                        ui.selectPreviousPossibleValue();
                    }
                    else if (UIManager.getBoolean("ComboBox.showPopupOnNavigation")) {
                        ui.setPopupVisible(comboBox, true);
                    }
                }
            }
            else if (key == UP_2) {
                 // Special case in which pressing the arrow keys will not
                 // make the popup appear - except for editable combo boxes.
                 if (comboBox.isShowing() && ui != null) {
                     if ( comboBox.isEditable() && !comboBox.isPopupVisible()) {
                         comboBox.setPopupVisible(true);
                     } else {
                         ui.selectPreviousPossibleValue();
                     }
                 }
             }

            else if (key == ENTER) {
                if (comboBox.isPopupVisible()) {
                    if (UIManager.getBoolean("ComboBox.noActionOnKeyNavigation")) {
                        Object listItem = ui == null ? null : ui.popup.getList().getSelectedValue();
                        if (listItem != null) {
                            comboBox.getEditor().setItem(listItem);
                            comboBox.setSelectedItem(listItem);
                        }
                        comboBox.setPopupVisible(false);
                    } else {
                        // Forces the selection of the list item
                        boolean isEnterSelectablePopup =
                                UIManager.getBoolean("ComboBox.isEnterSelectablePopup");
                        if (ui != null && (!comboBox.isEditable() || isEnterSelectablePopup
                                || ui.isTableCellEditor)) {
                            Object listItem = ui.popup.getList().getSelectedValue();
                            if (listItem != null) {
                                // Use the selected value from popup
                                // to set the selected item in combo box,
                                // but ensure before that JComboBox.actionPerformed()
                                // won't use editor's value to set the selected item
                                comboBox.getEditor().setItem(listItem);
                                comboBox.setSelectedItem(listItem);
                            }
                        }
                        comboBox.setPopupVisible(false);
                    }
                }
                else {
                    // Hide combo box if it is a table cell editor
                    if (ui != null && ui.isTableCellEditor && !comboBox.isEditable()) {
                        comboBox.setSelectedItem(comboBox.getSelectedItem());
                    }
                    // The JDK runs the root pane's "press" binding; here the
                    // default button is clicked directly.
                    JRootPane root = SwingUtilities.getRootPane(comboBox);
                    if (root != null) {
                        JButton defaultButton = root.getDefaultButton();
                        if (defaultButton != null && defaultButton.isEnabled()) {
                            defaultButton.doClick();
                        }
                    }
                }
            }
        }

        private int getNextIndex(JComboBox<?> comboBox, String key) {
            int listHeight = comboBox.getMaximumRowCount();

            int selectedIndex = comboBox.getSelectedIndex();
            if (UIManager.getBoolean("ComboBox.noActionOnKeyNavigation")
                    && (comboBox.getUI() instanceof BasicComboBoxUI)) {
                selectedIndex = ((BasicComboBoxUI) comboBox.getUI()).listBox.getSelectedIndex();
            }

            if (key == PAGE_UP) {
                int index = selectedIndex - listHeight;
                return (index < 0 ? 0: index);
            }
            else if (key == PAGE_DOWN) {
                int index = selectedIndex + listHeight;
                int max = comboBox.getItemCount();
                return (index < max ? index: max-1);
            }
            else if (key == HOME) {
                return 0;
            }
            else if (key == END) {
                return comboBox.getItemCount() - 1;
            }
            return comboBox.getSelectedIndex();
        }

        @Override
        public boolean isEnabled(Object c) {
            if (getName() == HIDE) {
                return (c != null && ((JComboBox)c).isPopupVisible());
            }
            return true;
        }
    }
}
