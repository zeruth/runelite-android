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
package javax.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.ComponentInputMapUIResource;

/**
 * A menu: a {@link JMenuItem} that owns a {@link JPopupMenu}. Selection follows the
 * {@link MenuSelectionManager} path as in 8u: a menu on the path is selected, and its popup
 * is shown while the popup is on the path. The mnemonic binding and the bar's selection
 * index, which 8u leaves to BasicMenuUI and BasicMenuBarUI, are kept by the menu itself:
 * the menu is host-painted without a delegate by default, and a plugin's alt+mnemonic still
 * has to open it (BasicMenuUI leaves these maps alone when a look and feel installs it).
 */
public class JMenu extends JMenuItem implements javax.accessibility.Accessible {
    private static final String uiClassID = "MenuUI";
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJMenu();
        }
        return accessibleContext;
    }

    /**
     * Accessibility support for menus: role MENU; the children are the accessible items of
     * the menu's popup, not of the menu component itself.
     */
    protected class AccessibleJMenu extends AccessibleJMenuItem {
        public int getAccessibleChildrenCount() {
            int count = 0;
            for (java.awt.Component child : getPopupMenu().getComponents()) {
                if (child instanceof javax.accessibility.Accessible) count++;
            }
            return count;
        }

        public javax.accessibility.Accessible getAccessibleChild(int i) {
            int count = 0;
            for (java.awt.Component child : getPopupMenu().getComponents()) {
                if (child instanceof javax.accessibility.Accessible) {
                    if (count == i) {
                        if (child instanceof JComponent) {
                            // The popup is not the items' accessible parent; the menu is.
                            ((javax.accessibility.Accessible) child).getAccessibleContext().setAccessibleParent(JMenu.this);
                        }
                        return (javax.accessibility.Accessible) child;
                    }
                    count++;
                }
            }
            return null;
        }

        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.MENU;
        }
    }

    private static final long serialVersionUID = 1L;
    private static final String SELECT_MENU = "selectMenu";

    private final JPopupMenu popup = new JPopupMenu();
    // Not initialised in its declaration: AbstractButton's constructor runs setModel first.
    private ChangeListener menuChangeListener;
    private int delay = 200;
    private Point customMenuLocation;
    private int lastMnemonic;

    public JMenu() {
        popup.setInvoker(this);
        if (menuChangeListener == null) {
            menuChangeListener = new MenuChangeListener();
            getModel().addChangeListener(menuChangeListener);
        }
        ActionMap actions = new ActionMapUIResource();
        actions.put(SELECT_MENU, new SelectMenuAction());
        SwingUtilities.replaceUIActionMap(this, actions);
        addPropertyChangeListener(MNEMONIC_CHANGED_PROPERTY, new java.beans.PropertyChangeListener() {
            @Override public void propertyChange(java.beans.PropertyChangeEvent e) { updateMnemonicBinding(); }
        });
        updateMnemonicBinding();
    }
    public JMenu(String text) { this(); setText(text); }
    public JMenu(String text, boolean tearOff) { this(text); }
    public JMenu(Action action) { this(); setAction(action); }

    @Override
    public void setModel(ButtonModel newModel) {
        ButtonModel oldModel = getModel();
        super.setModel(newModel);
        if (oldModel != null && menuChangeListener != null) oldModel.removeChangeListener(menuChangeListener);
        if (menuChangeListener == null) menuChangeListener = new MenuChangeListener();
        newModel.addChangeListener(menuChangeListener);
    }

    /** Fires menuSelected/menuDeselected, and keeps the bar's selection index (BasicMenuBarUI in 8u). */
    private final class MenuChangeListener implements ChangeListener, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private boolean selected;

        @Override public void stateChanged(ChangeEvent e) {
            boolean now = ((ButtonModel) e.getSource()).isSelected();
            if (now == selected) return;
            selected = now;
            if (now) fireMenuSelected(); else fireMenuDeselected();
            Container parent = getParent();
            if (parent instanceof JMenuBar) {
                JMenuBar bar = (JMenuBar) parent;
                int index = bar.getComponentIndex(JMenu.this);
                if (now) bar.getSelectionModel().setSelectedIndex(index);
                else if (bar.getSelectionModel().getSelectedIndex() == index) bar.getSelectionModel().clearSelection();
            }
        }
    }

    // --- mnemonic (BasicMenuUI.updateMnemonicBinding in 8u) -------------------

    private void updateMnemonicBinding() {
        int mnemonic = getModel().getMnemonic();
        if (mnemonic == lastMnemonic) return;
        InputMap windowMap = SwingUtilities.getUIInputMap(this, WHEN_IN_FOCUSED_WINDOW);
        if (lastMnemonic != 0 && windowMap != null) {
            windowMap.remove(KeyStroke.getKeyStroke(lastMnemonic, InputEvent.ALT_MASK, false));
        }
        if (mnemonic != 0) {
            if (windowMap == null) {
                windowMap = new ComponentInputMapUIResource(this);
                SwingUtilities.replaceUIInputMap(this, WHEN_IN_FOCUSED_WINDOW, windowMap);
            }
            windowMap.put(KeyStroke.getKeyStroke(mnemonic, InputEvent.ALT_MASK, false), SELECT_MENU);
        }
        lastMnemonic = mnemonic;
    }

    /** BasicMenuUI.Actions.SELECT: opens the menu by putting it and its popup on the selected path. */
    private static final class SelectMenuAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        @Override public void actionPerformed(ActionEvent e) {
            if (!(e.getSource() instanceof JMenu)) return;
            JMenu menu = (JMenu) e.getSource();
            MenuSelectionManager manager = MenuSelectionManager.defaultManager();
            if (menu.isTopLevelMenu()) {
                manager.setSelectedPath(new MenuElement[] {(MenuElement) menu.getParent(), menu, menu.getPopupMenu()});
                return;
            }
            MenuElement[] path = manager.getSelectedPath();
            if (path.length > 0 && path[path.length - 1] == menu.getParent()) {
                MenuElement[] next = new MenuElement[path.length + 2];
                System.arraycopy(path, 0, next, 0, path.length);
                next[path.length] = menu;
                next[path.length + 1] = menu.getPopupMenu();
                manager.setSelectedPath(next);
            } else {
                manager.setSelectedPath(buildMenuElementArray(menu));
            }
        }
    }

    // --- popup --------------------------------------------------------------

    public JPopupMenu getPopupMenu() { return popup; }
    public boolean isPopupMenuVisible() { return popup.isVisible(); }

    /**
     * As in 8u: shows the popup at {@link #getPopupMenuOrigin} (or the custom location) while
     * the menu is showing; a disabled menu can still close it. A menu in a bar the panel host
     * mounted is showing ({@code setHostShowing} on the mounted root).
     */
    public void setPopupMenuVisible(boolean visible) {
        if (visible == isPopupMenuVisible() || !(isEnabled() || !visible)) return;
        if (visible && isShowing()) {
            Point p = customMenuLocation != null ? customMenuLocation : getPopupMenuOrigin();
            popup.show(this, p.x, p.y);
        } else {
            popup.setVisible(false);
        }
    }

    /**
     * Where the popup opens, relative to this menu: below a top-level menu, to the right of a
     * submenu, flipped above or to the left when the host screen has no room for it (8u).
     */
    protected Point getPopupMenuOrigin() {
        int x;
        int y;
        Dimension s = getSize();
        Dimension pmSize = popup.getSize();
        if (pmSize.width == 0) pmSize = popup.getPreferredSize();
        Point position = getLocationOnScreen();
        GraphicsConfiguration gc = getGraphicsConfiguration();
        // The toolkit's screen size only when there is no configuration: a headless JDK toolkit throws.
        Rectangle screenBounds = gc != null ? gc.getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        if (gc != null) {
            Insets screenInsets = JPopupMenu.screenInsets(gc);
            screenBounds.width -= Math.abs(screenInsets.left + screenInsets.right);
            screenBounds.height -= Math.abs(screenInsets.top + screenInsets.bottom);
            position.x -= Math.abs(screenInsets.left);
            position.y -= Math.abs(screenInsets.top);
        }
        boolean leftToRight = getComponentOrientation().isLeftToRight();
        if (getParent() instanceof JPopupMenu) {
            int xOffset = UIManager.getInt("Menu.submenuPopupOffsetX");
            int yOffset = UIManager.getInt("Menu.submenuPopupOffsetY");
            if (leftToRight) {
                x = s.width + xOffset;
                if (position.x + x + pmSize.width >= screenBounds.width + screenBounds.x
                        && screenBounds.width - s.width < 2 * (position.x - screenBounds.x)) {
                    x = 0 - xOffset - pmSize.width;
                }
            } else {
                x = 0 - xOffset - pmSize.width;
                if (position.x + x < screenBounds.x
                        && screenBounds.width - s.width > 2 * (position.x - screenBounds.x)) {
                    x = s.width + xOffset;
                }
            }
            y = yOffset;
            if (position.y + y + pmSize.height >= screenBounds.height + screenBounds.y
                    && screenBounds.height - s.height < 2 * (position.y - screenBounds.y)) {
                y = s.height - yOffset - pmSize.height;
            }
        } else {
            int xOffset = UIManager.getInt("Menu.menuPopupOffsetX");
            int yOffset = UIManager.getInt("Menu.menuPopupOffsetY");
            if (leftToRight) {
                x = xOffset;
                if (position.x + x + pmSize.width >= screenBounds.width + screenBounds.x
                        && screenBounds.width - s.width < 2 * (position.x - screenBounds.x)) {
                    x = s.width - xOffset - pmSize.width;
                }
            } else {
                x = s.width - xOffset - pmSize.width;
                if (position.x + x < screenBounds.x
                        && screenBounds.width - s.width > 2 * (position.x - screenBounds.x)) {
                    x = xOffset;
                }
            }
            y = s.height + yOffset;
            if (position.y + y + pmSize.height >= screenBounds.height + screenBounds.y
                    && screenBounds.height - s.height < 2 * (position.y - screenBounds.y)) {
                y = 0 - yOffset - pmSize.height;
            }
        }
        return new Point(x, y);
    }

    public void setMenuLocation(int x, int y) {
        customMenuLocation = new Point(x, y);
        popup.setLocation(x, y);
    }

    /** Milliseconds before a submenu pops up or down; kept for the look and feel. */
    public int getDelay() { return delay; }
    public void setDelay(int d) {
        if (d < 0) throw new IllegalArgumentException("Delay must be a positive integer");
        delay = d;
    }

    public boolean isTopLevelMenu() { return getParent() instanceof JMenuBar; }
    public boolean isTearOff() { throw new Error("boolean isTearOff() {} not yet implemented"); }

    /** As in 8u, a menu has no accelerator; its mnemonic is how the keyboard opens it. */
    @Override
    public void setAccelerator(KeyStroke keyStroke) {
        throw new Error("setAccelerator() is not defined for JMenu.  Use setMnemonic() instead.");
    }

    @Override
    void configureAcceleratorFromAction(Action a) {}

    public boolean isMenuComponent(Component c) {
        if (c == this || c == popup) return true;
        for (Component child : popup.getComponents()) {
            if (child == c) return true;
            if (child instanceof JMenu && ((JMenu) child).isMenuComponent(c)) return true;
        }
        return false;
    }

    // --- items --------------------------------------------------------------

    public JMenuItem add(JMenuItem item) { return popup.add(item); }
    public JMenuItem add(String text) { return popup.add(text); }
    public JMenuItem add(Action action) { return popup.add(action); }
    public java.awt.Component add(java.awt.Component component) { popup.add(component); return component; }
    public java.awt.Component add(java.awt.Component component, int index) { popup.add(component, index); return component; }
    public void addSeparator() { popup.addSeparator(); }
    public JMenuItem insert(JMenuItem item, int index) {
        if (index < 0) throw new IllegalArgumentException("index less than zero.");
        popup.insert(item, index);
        return item;
    }
    public JMenuItem insert(Action action, int index) { return insert(new JMenuItem(action), index); }
    public void insert(String text, int index) {
        if (index < 0) throw new IllegalArgumentException("index less than zero.");
        insert(new JMenuItem(text), index);
    }
    public void insertSeparator(int index) {
        if (index < 0) throw new IllegalArgumentException("index less than zero.");
        popup.insert(new JPopupMenu.Separator(), index);
    }
    public JMenuItem getItem(int index) {
        if (index < 0) throw new IllegalArgumentException("index less than zero.");
        java.awt.Component child = popup.getComponent(index);
        return child instanceof JMenuItem ? (JMenuItem) child : null;
    }
    public int getItemCount() { return popup.getComponentCount(); }
    public int getMenuComponentCount() { return popup.getComponentCount(); }
    public java.awt.Component getMenuComponent(int index) { return popup.getComponent(index); }
    public java.awt.Component[] getMenuComponents() { return popup.getComponents(); }
    public void remove(JMenuItem item) { popup.remove(item); }
    public void remove(int index) {
        if (index < 0) throw new IllegalArgumentException("index less than zero.");
        if (index > getItemCount()) throw new IllegalArgumentException("index greater than the number of items.");
        popup.remove(index);
    }
    public void remove(java.awt.Component component) { popup.remove(component); }
    public void removeAll() { popup.removeAll(); }

    // --- MenuListener -------------------------------------------------------

    public void addMenuListener(MenuListener l) { listenerList.add(MenuListener.class, l); }
    public void removeMenuListener(MenuListener l) { listenerList.remove(MenuListener.class, l); }
    public MenuListener[] getMenuListeners() { return listenerList.getListeners(MenuListener.class); }

    private transient MenuEvent menuEvent;

    private MenuEvent menuEvent() {
        if (menuEvent == null) menuEvent = new MenuEvent(this);
        return menuEvent;
    }

    protected void fireMenuSelected() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuListener.class) ((MenuListener) listeners[i + 1]).menuSelected(menuEvent());
        }
    }

    protected void fireMenuDeselected() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuListener.class) ((MenuListener) listeners[i + 1]).menuDeselected(menuEvent());
        }
    }

    protected void fireMenuCanceled() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == MenuListener.class) ((MenuListener) listeners[i + 1]).menuCanceled(menuEvent());
        }
    }

    // --- MenuElement --------------------------------------------------------

    /** As in 8u: clicking a menu selects the path from its bar down to its popup. */
    public void doClick(int pressTime) {
        MenuSelectionManager.defaultManager().setSelectedPath(buildMenuElementArray(this));
    }

    private static MenuElement[] buildMenuElementArray(JMenu leaf) {
        java.util.LinkedList<MenuElement> elements = new java.util.LinkedList<MenuElement>();
        Component current = leaf.getPopupMenu();
        while (true) {
            if (current instanceof JPopupMenu) {
                JPopupMenu pop = (JPopupMenu) current;
                elements.addFirst(pop);
                current = pop.getInvoker();
            } else if (current instanceof JMenu) {
                JMenu menu = (JMenu) current;
                elements.addFirst(menu);
                current = menu.getParent();
            } else if (current instanceof JMenuBar) {
                elements.addFirst((JMenuBar) current);
                break;
            } else {
                break;
            }
        }
        return elements.toArray(new MenuElement[0]);
    }

    public MenuElement[] getSubElements() { return new MenuElement[]{popup}; }
    public void menuSelectionChanged(boolean included) { setSelected(included); }

    /** As in 8u: the menu manager sees key events first, so an open menu navigates by keyboard. */
    @Override
    protected void processKeyEvent(java.awt.event.KeyEvent e) {
        MenuSelectionManager.defaultManager().processKeyEvent(e);
        if (e.isConsumed()) return;
        super.processKeyEvent(e);
    }
}
