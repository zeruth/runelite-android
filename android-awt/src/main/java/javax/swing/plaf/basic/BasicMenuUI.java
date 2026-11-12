/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuDragMouseEvent;
import javax.swing.event.MenuDragMouseListener;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;
import javax.swing.event.MenuListener;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;

/**
 * A default L&amp;F implementation of MenuUI.  This implementation
 * is a "combined" view/controller.
 *
 * <p>Android port: {@link JMenu} keeps its own alt+mnemonic binding (its UI input and
 * action maps), so this delegate leaves those maps alone instead of replacing them.
 *
 * @author Georges Saab
 * @author David Karlton
 * @author Arnaud Weber
 */
public class BasicMenuUI extends BasicMenuItemUI {
    protected ChangeListener changeListener;
    protected MenuListener menuListener;

    private static boolean crossMenuMnemonic = true;

    public static ComponentUI createUI(JComponent x) {
        return new BasicMenuUI();
    }

    protected void installDefaults() {
        super.installDefaults();
        updateDefaultBackgroundColor();
        ((JMenu) menuItem).setDelay(200);
        crossMenuMnemonic = UIManager.getBoolean("Menu.crossMenuMnemonic");
    }

    protected String getPropertyPrefix() {
        return "Menu";
    }

    protected void installListeners() {
        super.installListeners();

        if (changeListener == null) {
            changeListener = createChangeListener(menuItem);
        }

        if (changeListener != null) {
            menuItem.addChangeListener(changeListener);
        }

        if (menuListener == null) {
            menuListener = createMenuListener(menuItem);
        }

        if (menuListener != null) {
            ((JMenu) menuItem).addMenuListener(menuListener);
        }
    }

    /** JMenu binds its own mnemonic; a menu has no accelerator to bind. */
    protected void installKeyboardActions() {
    }

    /** Leaves JMenu's own mnemonic maps in place. */
    protected void uninstallKeyboardActions() {
    }

    void updateAcceleratorBinding() {
    }

    protected MouseInputListener createMouseInputListener(JComponent c) {
        return getHandler();
    }

    protected MenuListener createMenuListener(JComponent c) {
        return null;
    }

    protected ChangeListener createChangeListener(JComponent c) {
        return null;
    }

    protected PropertyChangeListener createPropertyChangeListener(JComponent c) {
        return getHandler();
    }

    BasicMenuItemUI.Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    protected void uninstallDefaults() {
        menuItem.setArmed(false);
        menuItem.setSelected(false);
        super.uninstallDefaults();
    }

    protected void uninstallListeners() {
        super.uninstallListeners();

        if (changeListener != null) {
            menuItem.removeChangeListener(changeListener);
        }

        if (menuListener != null) {
            ((JMenu) menuItem).removeMenuListener(menuListener);
        }

        changeListener = null;
        menuListener = null;
        handler = null;
    }

    protected MenuDragMouseListener createMenuDragMouseListener(JComponent c) {
        return getHandler();
    }

    protected MenuKeyListener createMenuKeyListener(JComponent c) {
        return (MenuKeyListener) getHandler();
    }

    public Dimension getMaximumSize(JComponent c) {
        if (((JMenu) menuItem).isTopLevelMenu()) {
            Dimension d = c.getPreferredSize();
            return new Dimension(d.width, Short.MAX_VALUE);
        }
        return null;
    }

    protected void setupPostTimer(JMenu menu) {
        Timer timer = new Timer(menu.getDelay(), new PostAction(menu));
        timer.setRepeats(false);
        timer.start();
    }

    protected void appendPath(MenuElement[] path, MenuElement elem) {
        postPath(path, elem);
    }

    private static void postPath(MenuElement[] path, MenuElement elem) {
        MenuElement[] newPath = new MenuElement[path.length + 1];
        System.arraycopy(path, 0, newPath, 0, path.length);
        newPath[path.length] = elem;
        MenuSelectionManager.defaultManager().setSelectedPath(newPath);
    }

    /** Opens the menu's popup if the menu is still last on the path when the delay ends. */
    private static final class PostAction implements ActionListener {
        private final JMenu menu;

        PostAction(JMenu menu) {
            this.menu = menu;
        }

        public void actionPerformed(ActionEvent e) {
            MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
            if (path.length > 0 && path[path.length - 1] == menu) {
                postPath(path, menu.getPopupMenu());
            }
        }
    }

    /*
     * Set the background color depending on whether this is a toplevel menu
     * in a menubar or a submenu of another menu.
     */
    private void updateDefaultBackgroundColor() {
        if (!UIManager.getBoolean("Menu.useMenuBarBackgroundForTopLevel")) {
            return;
        }
        JMenu menu = (JMenu) menuItem;
        if (menu.getBackground() instanceof UIResource) {
            if (menu.isTopLevelMenu()) {
                Color background = UIManager.getColor("MenuBar.background");
                if (background != null) {
                    menu.setBackground(background);
                }
            } else {
                Color background = UIManager.getColor(getPropertyPrefix() + ".background");
                if (background != null) {
                    menu.setBackground(background);
                }
            }
        }
    }

    /**
     * Instantiated and used by a menu item to handle the current menu selection
     * from mouse events. A MouseInputHandler processes and forwards all mouse events
     * to a shared instance of the MenuSelectionManager.
     * <p>
     * This class is protected so that it can be subclassed by other look and
     * feels to implement their own mouse handling behavior. All overridden
     * methods should call the parent methods so that the menu selection
     * is correct.
     *
     * @see javax.swing.MenuSelectionManager
     * @since 1.4
     */
    protected class MouseInputHandler implements MouseInputListener {
        // NOTE: This class exists only for backward compatibility. All
        // its functionality has been moved into Handler. If you need to add
        // new functionality add it to the Handler, but make sure this
        // class calls into the Handler.

        public void mouseClicked(MouseEvent e) {
            getHandler().mouseClicked(e);
        }

        /**
         * Invoked when the mouse has been clicked on the menu. This
         * method clears or sets the selection path of the
         * MenuSelectionManager.
         *
         * @param e the mouse event
         */
        public void mousePressed(MouseEvent e) {
            getHandler().mousePressed(e);
        }

        /**
         * Invoked when the mouse has been released on the menu. Delegates the
         * mouse event to the MenuSelectionManager.
         *
         * @param e the mouse event
         */
        public void mouseReleased(MouseEvent e) {
            getHandler().mouseReleased(e);
        }

        /**
         * Invoked when the cursor enters the menu. This method sets the selected
         * path for the MenuSelectionManager and handles the case
         * in which a menu item is used to pop up an additional menu, as in a
         * hierarchical menu system.
         *
         * @param e the mouse event; not used
         */
        public void mouseEntered(MouseEvent e) {
            getHandler().mouseEntered(e);
        }
        public void mouseExited(MouseEvent e) {
            getHandler().mouseExited(e);
        }

        /**
         * Invoked when a mouse button is pressed on the menu and then dragged.
         * Delegates the mouse event to the MenuSelectionManager.
         *
         * @param e the mouse event
         * @see java.awt.event.MouseMotionListener#mouseDragged
         */
        public void mouseDragged(MouseEvent e) {
            getHandler().mouseDragged(e);
        }

        public void mouseMoved(MouseEvent e) {
            getHandler().mouseMoved(e);
        }
    }

    /**
     * As of Java 2 platform 1.4, this previously undocumented class
     * is now obsolete. KeyBindings are now managed by the popup menu.
     */
    public class ChangeHandler implements ChangeListener {
        public JMenu    menu;
        public BasicMenuUI ui;
        public boolean  isSelected = false;
        public Component wasFocused;

        public ChangeHandler(JMenu m, BasicMenuUI ui) {
            menu = m;
            this.ui = ui;
        }

        public void stateChanged(ChangeEvent e) { }
    }

    private class Handler extends BasicMenuItemUI.Handler implements MenuKeyListener {
        //
        // PropertyChangeListener
        //
        public void propertyChange(PropertyChangeEvent e) {
            if ("ancestor".equals(e.getPropertyName())) {
                updateDefaultBackgroundColor();
            }
            super.propertyChange(e);
        }

        //
        // MouseInputListener
        //
        public void mouseClicked(MouseEvent e) {
        }

        /**
         * Invoked when the mouse has been clicked on the menu. This
         * method clears or sets the selection path of the
         * MenuSelectionManager.
         *
         * @param e the mouse event
         */
        public void mousePressed(MouseEvent e) {
            JMenu menu = (JMenu) menuItem;
            if (!menu.isEnabled()) {
                return;
            }

            MenuSelectionManager manager =
                MenuSelectionManager.defaultManager();
            if (menu.isTopLevelMenu()) {
                if (menu.isSelected() && menu.getPopupMenu().isShowing()) {
                    manager.clearSelectedPath();
                } else {
                    Container cnt = menu.getParent();
                    if (cnt != null && cnt instanceof JMenuBar) {
                        MenuElement[] me = new MenuElement[2];
                        me[0] = (MenuElement) cnt;
                        me[1] = menu;
                        manager.setSelectedPath(me);
                    }
                }
            }

            MenuElement[] selectedPath = manager.getSelectedPath();
            if (selectedPath.length > 0
                    && selectedPath[selectedPath.length - 1] != menu.getPopupMenu()) {

                if (menu.isTopLevelMenu() || menu.getDelay() == 0) {
                    appendPath(selectedPath, menu.getPopupMenu());
                } else {
                    setupPostTimer(menu);
                }
            }
        }

        /**
         * Invoked when the mouse has been released on the menu. Delegates the
         * mouse event to the MenuSelectionManager.
         *
         * @param e the mouse event
         */
        public void mouseReleased(MouseEvent e) {
            JMenu menu = (JMenu) menuItem;
            if (!menu.isEnabled()) {
                return;
            }
            MenuSelectionManager manager =
                MenuSelectionManager.defaultManager();
            manager.processMouseEvent(e);
            if (!e.isConsumed()) {
                manager.clearSelectedPath();
            }
        }

        /**
         * Invoked when the cursor enters the menu. This method sets the selected
         * path for the MenuSelectionManager and handles the case
         * in which a menu item is used to pop up an additional menu, as in a
         * hierarchical menu system.
         *
         * @param e the mouse event; not used
         */
        public void mouseEntered(MouseEvent e) {
            JMenu menu = (JMenu) menuItem;
            // only disable the menu highlighting if it's disabled and the property isn't
            // true. This allows disabled rollovers to work in WinL&F
            if (!menu.isEnabled() && !UIManager.getBoolean("MenuItem.disabledAreNavigable")) {
                return;
            }

            MenuSelectionManager manager =
                MenuSelectionManager.defaultManager();
            MenuElement[] selectedPath = manager.getSelectedPath();
            if (!menu.isTopLevelMenu()) {
                if (!(selectedPath.length > 0
                        && selectedPath[selectedPath.length - 1] == menu.getPopupMenu())) {
                    if (menu.getDelay() == 0) {
                        appendPath(getPath(), menu.getPopupMenu());
                    } else {
                        manager.setSelectedPath(getPath());
                        setupPostTimer(menu);
                    }
                }
            } else {
                if (selectedPath.length > 0
                        && selectedPath[0] == menu.getParent()) {
                    // A top level menu's parent is by definition a JMenuBar;
                    // the popup follows only while another menu's popup is open.
                    MenuElement[] newPath = BasicPopupMenuUI.getLastPopup() != null
                        ? new MenuElement[3] : new MenuElement[2];
                    newPath[0] = (MenuElement) menu.getParent();
                    newPath[1] = menu;
                    if (newPath.length == 3) {
                        newPath[2] = menu.getPopupMenu();
                    }
                    manager.setSelectedPath(newPath);
                }
            }
        }
        public void mouseExited(MouseEvent e) {
        }

        /**
         * Invoked when a mouse button is pressed on the menu and then dragged.
         * Delegates the mouse event to the MenuSelectionManager.
         *
         * @param e the mouse event
         * @see java.awt.event.MouseMotionListener#mouseDragged
         */
        public void mouseDragged(MouseEvent e) {
            JMenu menu = (JMenu) menuItem;
            if (!menu.isEnabled()) {
                return;
            }
            MenuSelectionManager.defaultManager().processMouseEvent(e);
        }
        public void mouseMoved(MouseEvent e) {
        }


        //
        // MenuDragHandler
        //
        public void menuDragMouseEntered(MenuDragMouseEvent e) {}
        public void menuDragMouseDragged(MenuDragMouseEvent e) {
            if (!menuItem.isEnabled()) {
                return;
            }

            MenuSelectionManager manager = e.getMenuSelectionManager();
            MenuElement[] path = e.getPath();

            Point p = e.getPoint();
            if (p.x >= 0 && p.x < menuItem.getWidth()
                    && p.y >= 0 && p.y < menuItem.getHeight()) {
                JMenu menu = (JMenu) menuItem;
                MenuElement[] selectedPath = manager.getSelectedPath();
                if (!(selectedPath.length > 0
                        && selectedPath[selectedPath.length - 1] == menu.getPopupMenu())) {
                    if (menu.isTopLevelMenu() || menu.getDelay() == 0
                            || e.getID() == MouseEvent.MOUSE_DRAGGED) {
                        appendPath(path, menu.getPopupMenu());
                    } else {
                        manager.setSelectedPath(path);
                        setupPostTimer(menu);
                    }
                }
            } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
                Component comp = manager.componentForPoint(e.getComponent(), e.getPoint());
                if (comp == null) {
                    manager.clearSelectedPath();
                }
            }

        }
        public void menuDragMouseExited(MenuDragMouseEvent e) {}
        public void menuDragMouseReleased(MenuDragMouseEvent e) {}


        //
        // MenuKeyListener
        //
        /**
         * Open the Menu
         */
        public void menuKeyTyped(MenuKeyEvent e) {
            if (!crossMenuMnemonic && BasicPopupMenuUI.getLastPopup() != null) {
                // when crossMenuMnemonic is not set, we don't open a toplevel
                // menu if another toplevel menu is already open
                return;
            }

            if (BasicPopupMenuUI.getLastPopup() != null) {
                // a posted popup handles its own items' mnemonics (BasicPopupMenuUI)
                return;
            }

            char key = Character.toLowerCase((char) menuItem.getMnemonic());
            MenuElement[] path = e.getPath();
            if (key == Character.toLowerCase(e.getKeyChar())) {
                JPopupMenu popupMenu = ((JMenu) menuItem).getPopupMenu();
                List<MenuElement> newList = new ArrayList<MenuElement>(Arrays.asList(path));
                newList.add(popupMenu);
                MenuElement[] subs = popupMenu.getSubElements();
                MenuElement sub =
                        BasicPopupMenuUI.findEnabledChild(subs, -1, true);
                if (sub != null) {
                    newList.add(sub);
                }
                MenuSelectionManager manager = e.getMenuSelectionManager();
                MenuElement[] newPath = newList.toArray(new MenuElement[0]);
                manager.setSelectedPath(newPath);
                e.consume();
            }
        }

        public void menuKeyPressed(MenuKeyEvent e) {
        }

        public void menuKeyReleased(MenuKeyEvent e) {
        }
    }
}
