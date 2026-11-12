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
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import javax.swing.event.PopupMenuListener;

public class JPopupMenu extends JComponent implements javax.accessibility.Accessible, MenuElement {

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPopupMenu();
        }
        return accessibleContext;
    }

    /** Accessibility support for popup menus: role POPUP_MENU. */
    protected class AccessibleJPopupMenu extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.POPUP_MENU;
        }
    }
    private static final long serialVersionUID = 1L;

    public static class Separator extends JSeparator {
        private static final long serialVersionUID = 1L;
        public Separator() { super(SwingConstants.HORIZONTAL); }
        public String getUIClassID() { return "PopupMenuSeparatorUI"; }
    }

    private Component invoker;
    private final java.util.List<PopupMenuListener> popupListeners = new java.util.ArrayList<>();
    private static final ThreadLocal<JPopupMenu> shownPopup = new ThreadLocal<>();

    /** Android panel host consumes menus opened by plugin mouse listeners. */
    public static JPopupMenu takeShownPopupMenu() {
        JPopupMenu menu = shownPopup.get();
        shownPopup.remove();
        return menu;
    }

    public JPopupMenu() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(new java.awt.Color(0x3A3D43));
        setVisible(false);
    }
    public JPopupMenu(String label) { this();setLabel(label); }

    public JMenuItem add(JMenuItem item) { if (item != null) super.add(item); return item; }
    public JMenuItem add(String s) { JMenuItem it = new JMenuItem(s); super.add(it); return it; }
    public JMenuItem add(Action a) { JMenuItem it = new JMenuItem(a); super.add(it); return it; }
    public void addSeparator() { super.add(new Separator()); }
    public void insert(Action a, int index) { super.add(new JMenuItem(a), index); }
    public void insert(Component c, int index) { super.add(c, index); }
    @Override public void removeAll() { super.removeAll(); }

    /**
     * As in 8u: the popup's screen location is the invoker's screen origin plus (x, y), moved
     * back inside the host screen bounds. The panel host takes the shown menu through
     * {@link #takeShownPopupMenu} and presents its items itself.
     */
    public void show(Component invoker, int x, int y) {
        setInvoker(invoker);
        long lx = x;
        long ly = y;
        if (invoker != null) {
            Point origin = invoker.getLocationOnScreen();
            lx += origin.x;
            ly += origin.y;
        }
        desiredLocationX = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, lx));
        desiredLocationY = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, ly));
        setVisible(true);
        shownPopup.set(this);
        placeOnScreen();
    }

    private int desiredLocationX, desiredLocationY;

    /** Screen coordinates, as in 8u: moves an open popup at once. */
    @Override
    public void setLocation(int x, int y) {
        desiredLocationX = x;
        desiredLocationY = y;
        if (isVisible()) placeOnScreen();
    }

    private void placeOnScreen() {
        Dimension d = getPreferredSize();
        Dimension size = new Dimension(Math.max(80, d.width), Math.max(20, d.height));
        Point p = adjustPopupLocationToFitScreen(desiredLocationX, desiredLocationY, size);
        setBounds(p.x, p.y, size.width, size.height);
        doLayout();
        repaint();
    }

    private static final boolean popupPositionFixDisabled =
        "false".equals(System.getProperty("javax.swing.adjustPopupLocationToFit", ""));

    /** 8u adjustPopupLocationToFitScreen, against the host screen and its insets. */
    Point adjustPopupLocationToFitScreen(int xPosition, int yPosition, Dimension popupSize) {
        Point popupLocation = new Point(xPosition, yPosition);
        if (popupPositionFixDisabled) return popupLocation;
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        GraphicsConfiguration gc = invoker != null ? invoker.getGraphicsConfiguration() : getGraphicsConfiguration();
        Rectangle scrBounds = gc != null ? gc.getBounds() : new Rectangle(toolkit.getScreenSize());
        if (gc != null) {
            Insets insets = toolkit.getScreenInsets(gc);
            scrBounds.x += insets.left;
            scrBounds.y += insets.top;
            scrBounds.width -= insets.left + insets.right;
            scrBounds.height -= insets.top + insets.bottom;
        }
        long popupRightX = (long) popupLocation.x + (long) popupSize.width;
        long popupBottomY = (long) popupLocation.y + (long) popupSize.height;
        int scrRightX = scrBounds.x + scrBounds.width;
        int scrBottomY = scrBounds.y + scrBounds.height;
        if (popupRightX > (long) scrRightX) popupLocation.x = scrRightX - popupSize.width;
        if (popupBottomY > (long) scrBottomY) popupLocation.y = scrBottomY - popupSize.height;
        if (popupLocation.x < scrBounds.x) popupLocation.x = scrBounds.x;
        if (popupLocation.y < scrBounds.y) popupLocation.y = scrBounds.y;
        return popupLocation;
    }

    /** 8u's two-argument form, sized by the popup's preferred size. */
    Point adjustPopupLocationToFitScreen(int xPosition, int yPosition) {
        return adjustPopupLocationToFitScreen(xPosition, yPosition, getPreferredSize());
    }

    /** 8u: the popup at the root of a cascade of submenu popups. */
    JPopupMenu getRootPopupMenu() {
        JPopupMenu mp = this;
        while (mp != null && !mp.isPopupMenu() && mp.getInvoker() != null
                && mp.getInvoker().getParent() instanceof JPopupMenu) {
            mp = (JPopupMenu) mp.getInvoker().getParent();
        }
        return mp;
    }

    /** 8u: whether {@code popup} opens, directly or deeper, from a menu in this popup. */
    boolean isSubPopupMenu(JPopupMenu popup) {
        for (Component comp : getComponents()) {
            if (comp instanceof JMenu) {
                JPopupMenu subPopup = ((JMenu) comp).getPopupMenu();
                if (subPopup == popup || subPopup.isSubPopupMenu(popup)) return true;
            }
        }
        return false;
    }

    /** As in 8u, a popup has no settable margin: the space between its border and its items is zero. */
    public Insets getMargin() { return new Insets(0, 0, 0, 0); }

    /** Typed delegate accessors, as in 8u; the popup is host-presented until a look and feel sets one. */
    @Override public javax.swing.plaf.PopupMenuUI getUI() { return (javax.swing.plaf.PopupMenuUI) ui; }
    public void setUI(javax.swing.plaf.PopupMenuUI ui) { super.setUI(ui); }

    /** Lays the open popup out again at its preferred size. */
    public void pack() {
        if (isVisible()) placeOnScreen();
    }

    public void setPopupSize(Dimension d) {
        Dimension old = getPreferredSize();
        setPreferredSize(d);
        if (isVisible() && !old.equals(getPreferredSize())) placeOnScreen();
    }

    public void setPopupSize(int width, int height) { setPopupSize(new Dimension(width, height)); }

    public int getComponentIndex(Component c) {
        int n = getComponentCount();
        for (int i = 0; i < n; i++) {
            if (getComponent(i) == c) return i;
        }
        return -1;
    }

    /** @deprecated replaced by {@link java.awt.Container#getComponent(int)} */
    @Deprecated
    public Component getComponentAtIndex(int i) { return getComponent(i); }

    private SingleSelectionModel selectionModel = new DefaultSingleSelectionModel();
    public SingleSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(SingleSelectionModel model) {
        SingleSelectionModel old = selectionModel;
        selectionModel = model;
        firePropertyChange("selectionModel", old, model);
    }
    public void setSelected(Component sel) { selectionModel.setSelectedIndex(getComponentIndex(sel)); }

    /** BasicPopupMenuUI.isPopupTrigger: a right-button release. */
    public boolean isPopupTrigger(java.awt.event.MouseEvent e) {
        return e.getID() == java.awt.event.MouseEvent.MOUSE_RELEASED
            && (e.getModifiers() & java.awt.event.InputEvent.BUTTON3_MASK) != 0;
    }

    public Component getInvoker() { return invoker; }
    public void setInvoker(Component invoker) {
        Component old = this.invoker;
        this.invoker = invoker;
        firePropertyChange("invoker", old, invoker);
    }
    private String label;
    public void setLabel(String label) { String old=this.label;this.label=label;firePropertyChange("label",old,label); }
    public String getLabel() { return label; }
    public java.awt.Component getComponent() { return this; }

    /** As in 8u: a menu's popup opens and closes with the selected path; a context popup closes. */
    public void menuSelectionChanged(boolean included) {
        if (invoker instanceof JMenu) ((JMenu) invoker).setPopupMenuVisible(included);
        if (isPopupMenu() && !included) setVisible(false);
    }

    private boolean isPopupMenu() { return invoker != null && !(invoker instanceof JMenu); }

    /** As in 8u, a popup takes no mouse action of its own; the manager routes to its items. */
    public void processMouseEvent(java.awt.event.MouseEvent event, MenuElement path[], MenuSelectionManager manager) {}

    public void processKeyEvent(java.awt.event.KeyEvent e, MenuElement path[], MenuSelectionManager manager) {
        javax.swing.event.MenuKeyEvent mke = new javax.swing.event.MenuKeyEvent(e.getComponent(), e.getID(),
                e.getWhen(), e.getModifiers(), e.getKeyCode(), e.getKeyChar(), path, manager);
        processMenuKeyEvent(mke);
        if (mke.isConsumed()) {
            e.consume();
        }
    }

    private void processMenuKeyEvent(javax.swing.event.MenuKeyEvent e) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == javax.swing.event.MenuKeyListener.class) {
                javax.swing.event.MenuKeyListener l = (javax.swing.event.MenuKeyListener) listeners[i + 1];
                switch (e.getID()) {
                case java.awt.event.KeyEvent.KEY_PRESSED: l.menuKeyPressed(e); break;
                case java.awt.event.KeyEvent.KEY_RELEASED: l.menuKeyReleased(e); break;
                case java.awt.event.KeyEvent.KEY_TYPED: l.menuKeyTyped(e); break;
                default: break;
                }
            }
        }
    }

    public void addMenuKeyListener(javax.swing.event.MenuKeyListener l) {
        listenerList.add(javax.swing.event.MenuKeyListener.class, l);
    }

    public void removeMenuKeyListener(javax.swing.event.MenuKeyListener l) {
        listenerList.remove(javax.swing.event.MenuKeyListener.class, l);
    }

    public javax.swing.event.MenuKeyListener[] getMenuKeyListeners() {
        return listenerList.getListeners(javax.swing.event.MenuKeyListener.class);
    }
    public void addPopupMenuListener(PopupMenuListener l) { if (l != null) popupListeners.add(l); }
    public void removePopupMenuListener(PopupMenuListener l) { popupListeners.remove(l); }
    public PopupMenuListener[] getPopupMenuListeners() { return popupListeners.toArray(new PopupMenuListener[0]); }

    // Listeners are notified last-added first, as EventListenerList dispatch is in 8u.
    protected void firePopupMenuWillBecomeVisible() {
        javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(this);
        PopupMenuListener[] listeners = getPopupMenuListeners();
        for (int i = listeners.length - 1; i >= 0; i--) listeners[i].popupMenuWillBecomeVisible(event);
    }

    protected void firePopupMenuWillBecomeInvisible() {
        javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(this);
        PopupMenuListener[] listeners = getPopupMenuListeners();
        for (int i = listeners.length - 1; i >= 0; i--) listeners[i].popupMenuWillBecomeInvisible(event);
    }

    protected void firePopupMenuCanceled() {
        javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(this);
        PopupMenuListener[] listeners = getPopupMenuListeners();
        for (int i = listeners.length - 1; i >= 0; i--) listeners[i].popupMenuCanceled(event);
    }

    @Override public void setVisible(boolean visible) {
        if (visible == isVisible()) return;
        MenuSelectionManager manager = MenuSelectionManager.defaultManager();
        if (!visible) {
            getSelectionModel().clearSelection();
        } else if (isPopupMenu()) {
            // 8u: a context popup roots its own selected path before it shows, which is what
            // routes key events to its items and lets hiding it clear the path below.
            manager.setSelectedPath(new MenuElement[] {this});
        }
        if (visible) firePopupMenuWillBecomeVisible();
        else firePopupMenuWillBecomeInvisible();
        super.setVisible(visible);
        if (visible) return;
        // 8u: a context popup going away ends the menu selection it was part of.
        if (isPopupMenu() && manager.isComponentPartOfCurrentMenu(this)) {
            manager.clearSelectedPath();
        } else if (invoker instanceof JMenu) {
            closeMenuPath(manager);
        }
    }

    /**
     * A menu's popup hidden outside the manager (the host dismissing it, or a direct
     * {@code setVisible(false)}) takes its menu, and anything opened below it, off the selected
     * path, so the menu is deselected and its bar index cleared as when the manager closes it.
     * When the manager is the one hiding it the popup is already off the path and nothing happens.
     */
    private void closeMenuPath(MenuSelectionManager manager) {
        MenuElement[] path = manager.getSelectedPath();
        for (int i = 1; i < path.length; i++) {
            if (path[i] == this && path[i - 1] == invoker) {
                MenuElement[] kept = new MenuElement[i - 1];
                System.arraycopy(path, 0, kept, 0, kept.length);
                // A bar with no open menu is no selection; a context popup above stays open.
                if (kept.length == 0 || kept.length == 1 && kept[0] instanceof JMenuBar) {
                    manager.clearSelectedPath();
                } else {
                    manager.setSelectedPath(kept);
                }
                return;
            }
        }
    }
    public boolean isBorderPainted() { return false; }
    public void setBorderPainted(boolean b) {}
    private boolean lightWeightPopupEnabled = defaultLightWeightPopupEnabled;
    public boolean isLightWeightPopupEnabled() { return lightWeightPopupEnabled; }
    public void setLightWeightPopupEnabled(boolean enabled) { lightWeightPopupEnabled = enabled; }

    private static boolean defaultLightWeightPopupEnabled = true;
    public static void setDefaultLightWeightPopupEnabled(boolean aFlag) { defaultLightWeightPopupEnabled = aFlag; }
    public static boolean getDefaultLightWeightPopupEnabled() { return defaultLightWeightPopupEnabled; }

    // The menu's sub-elements are its MenuElement children (JMenuItem/JMenu); plain
    // Components (e.g. Separator) are excluded, matching real Swing. quest-helper uses
    // getSubElements().length to decide whether a right-click menu has any entries.
    public MenuElement[] getSubElements() {
        java.util.List<MenuElement> items = new java.util.ArrayList<>();
        for (java.awt.Component c : getComponents()) {
            if (c instanceof MenuElement) items.add((MenuElement) c);
        }
        return items.toArray(new MenuElement[0]);
    }
}
