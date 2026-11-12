/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.awt;

/**
 * The AWT menu bar of a {@link Frame}. The Android host has no native menu bar, so a
 * bar set on a frame keeps its menus and shortcuts for callers to query but is not drawn;
 * Swing's {@code JMenuBar} is the rendered equivalent.
 */
public class MenuBar extends MenuComponent implements MenuContainer {
    private static final long serialVersionUID = -4930327919388951260L;

    private final java.util.Vector<Menu> menus = new java.util.Vector<>();
    private Menu helpMenu;

    public MenuBar() {
    }

    /** Deliberate no-op: there is no native menu bar peer to create. */
    public void addNotify() {
    }

    @Override
    public void removeNotify() {
    }

    public Menu getHelpMenu() {
        return helpMenu;
    }

    public void setHelpMenu(Menu m) {
        synchronized (menus) {
            if (helpMenu == m) return;
            if (helpMenu != null) remove(helpMenu);
            helpMenu = m;
            if (m != null) {
                if (m.parent != this) add(m);
            }
        }
    }

    public Menu add(Menu m) {
        synchronized (menus) {
            if (m.parent != null) m.parent.remove(m);
            m.parent = this;
            menus.addElement(m);
            return m;
        }
    }

    public void remove(int index) {
        synchronized (menus) {
            Menu m = menus.elementAt(index);
            menus.removeElementAt(index);
            m.parent = null;
            if (helpMenu == m) helpMenu = null;
        }
    }

    @Override
    public void remove(MenuComponent m) {
        synchronized (menus) {
            int index = menus.indexOf(m);
            if (index >= 0) remove(index);
        }
    }

    public int getMenuCount() {
        return menus.size();
    }

    /** @deprecated As of JDK 1.1, replaced by {@link #getMenuCount}. */
    @Deprecated
    public int countMenus() {
        return getMenuCount();
    }

    public Menu getMenu(int i) {
        return menus.elementAt(i);
    }

    /** The shortcuts of every enabled item, in menu order, as the JDK enumerates them. */
    public synchronized java.util.Enumeration<MenuShortcut> shortcuts() {
        java.util.Vector<MenuShortcut> shortcuts = new java.util.Vector<>();
        for (Menu menu : new java.util.ArrayList<>(menus)) collectShortcuts(menu, shortcuts);
        return shortcuts.elements();
    }

    private static void collectShortcuts(Menu menu, java.util.Vector<MenuShortcut> out) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            MenuItem item = menu.getItem(i);
            if (item instanceof Menu) {
                collectShortcuts((Menu) item, out);
            } else if (item.isEnabled() && item.getShortcut() != null) {
                out.addElement(item.getShortcut());
            }
        }
    }

    public MenuItem getShortcutMenuItem(MenuShortcut s) {
        for (Menu menu : new java.util.ArrayList<>(menus)) {
            MenuItem found = shortcutItem(menu, s);
            if (found != null) return found;
        }
        return null;
    }

    private static MenuItem shortcutItem(Menu menu, MenuShortcut s) {
        if (!menu.isEnabled()) return null;
        for (int i = 0; i < menu.getItemCount(); i++) {
            MenuItem item = menu.getItem(i);
            if (item instanceof Menu) {
                MenuItem found = shortcutItem((Menu) item, s);
                if (found != null) return found;
            } else if (item.isEnabled() && s != null && s.equals(item.getShortcut())) {
                return item;
            }
        }
        return null;
    }

    public void deleteShortcut(MenuShortcut s) {
        MenuItem item = getShortcutMenuItem(s);
        if (item != null) item.deleteShortcut();
    }
}
