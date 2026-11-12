/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Enumeration;
import java.util.Vector;

/**
 * The <code>MenuBar</code> class encapsulates the platform's
 * concept of a menu bar bound to a frame.
 * <p>
 * There is no native menu bar on Android: the bar keeps its menus, help menu
 * and shortcut lookup as on the JDK, so code that builds and queries one runs
 * unchanged, but nothing draws it. Its menus can still be shown through
 * {@link PopupMenu}-style Swing popups.
 *
 * @author Sami Shaio
 * @see        java.awt.Frame
 * @see        java.awt.Menu
 * @see        java.awt.MenuItem
 * @see        java.awt.MenuShortcut
 * @since      JDK1.0
 */
public class MenuBar extends MenuComponent implements MenuContainer {

    /**
     * This field represents a vector of the
     * actual menus that will be part of the MenuBar.
     *
     * @serial
     * @see #countMenus()
     */
    Vector<Menu> menus = new Vector<>();

    /**
     * This menu is a special menu dedicated to
     * help.  The one thing to note about this menu
     * is that on some platforms it appears at the
     * right edge of the menubar.
     *
     * @serial
     * @see #getHelpMenu()
     * @see #setHelpMenu(Menu)
     */
    Menu helpMenu;

    private static final String base = "menubar";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = -4930327919388951260L;

    /**
     * Creates a new menu bar.
     */
    public MenuBar() {
    }

    /**
     * Construct a name for this MenuComponent.  Called by getName() when
     * the name is null.
     */
    String constructComponentName() {
        synchronized (MenuBar.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Creates the menu bar's peer; there is none on Android.
     */
    public void addNotify() {
    }

    /**
     * Gets the help menu on the menu bar.
     * @return    the help menu on this menu bar.
     */
    public Menu getHelpMenu() {
        return helpMenu;
    }

    /**
     * Sets the specified menu to be this menu bar's help menu.
     * If this menu bar has an existing help menu, the old help menu is
     * removed from the menu bar, and replaced with the specified menu.
     * @param m    the menu to be set as the help menu
     */
    public void setHelpMenu(final Menu m) {
        synchronized (this) {
            if (helpMenu == m) {
                return;
            }
            if (helpMenu != null) {
                remove(helpMenu);
            }
            helpMenu = m;
            if (m != null) {
                if (m.parent != this) {
                    add(m);
                }
            }
        }
    }

    /**
     * Adds the specified menu to the menu bar.
     * If the menu has been part of another menu bar,
     * removes it from that menu bar.
     *
     * @param        m   the menu to be added
     * @return       the menu added
     * @see          java.awt.MenuBar#remove(int)
     * @see          java.awt.MenuBar#remove(java.awt.MenuComponent)
     */
    public Menu add(Menu m) {
        synchronized (this) {
            if (m.parent != null) {
                m.parent.remove(m);
            }
            m.parent = this;
            menus.addElement(m);
            return m;
        }
    }

    /**
     * Removes the menu located at the specified
     * index from this menu bar.
     * @param        index   the position of the menu to be removed.
     * @see          java.awt.MenuBar#add(java.awt.Menu)
     */
    public void remove(final int index) {
        synchronized (this) {
            Menu m = getMenu(index);
            menus.removeElementAt(index);
            m.parent = null;
            if (helpMenu == m) {
                helpMenu = null;
            }
        }
    }

    /**
     * Removes the specified menu component from this menu bar.
     * @param        m the menu component to be removed.
     * @see          java.awt.MenuBar#add(java.awt.Menu)
     */
    public void remove(MenuComponent m) {
        synchronized (this) {
            int index = menus.indexOf(m);
            if (index >= 0) {
                remove(index);
            }
        }
    }

    /**
     * Gets the number of menus on the menu bar.
     * @return     the number of menus on the menu bar.
     * @since      JDK1.1
     */
    public int getMenuCount() {
        return countMenus();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getMenuCount()</code>.
     */
    @Deprecated
    public int countMenus() {
        return menus.size();
    }

    /**
     * Gets the specified menu.
     * @param      i the index position of the menu to be returned.
     * @return     the menu at the specified index of this menu bar.
     */
    public Menu getMenu(int i) {
        return menus.elementAt(i);
    }

    /**
     * Gets an enumeration of all menu shortcuts this menu bar
     * is managing.
     * @return      an enumeration of menu shortcuts that this
     *                      menu bar is managing.
     * @see         java.awt.MenuShortcut
     * @since       JDK1.1
     */
    public synchronized Enumeration<MenuShortcut> shortcuts() {
        Vector<MenuShortcut> shortcuts = new Vector<>();
        for (Menu menu : menus) {
            menu.collectShortcuts(shortcuts);
        }
        return shortcuts.elements();
    }

    /**
     * Gets the instance of <code>MenuItem</code> associated
     * with the specified <code>MenuShortcut</code> object,
     * or <code>null</code> if none of the menu items being managed
     * by this menu bar is associated with the specified menu
     * shortcut.
     * @param        s the specified menu shortcut.
     * @return       the menu item for the shortcut, or <code>null</code>
     * @see          java.awt.MenuItem
     * @see          java.awt.MenuShortcut
     * @since        JDK1.1
     */
    public MenuItem getShortcutMenuItem(MenuShortcut s) {
        for (Menu menu : menus) {
            MenuItem item = menu.getShortcutMenuItem(s);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    /**
     * Deletes the specified menu shortcut.
     * @param     s the menu shortcut to delete.
     * @since     JDK1.1
     */
    public void deleteShortcut(MenuShortcut s) {
        for (Menu menu : menus) {
            menu.deleteShortcut(s);
        }
    }
}
