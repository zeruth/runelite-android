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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

public class JMenuBar extends JComponent implements javax.accessibility.Accessible, MenuElement {
    private static final long serialVersionUID = 1L;

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJMenuBar();
        }
        return accessibleContext;
    }

    /** Accessibility support for menu bars: role MENU_BAR. */
    protected class AccessibleJMenuBar extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.MENU_BAR;
        }
    }

    private static final int DEFAULT_HEIGHT = 28;
    private SingleSelectionModel selectionModel = new DefaultSingleSelectionModel();
    private boolean borderPainted = true;
    private java.awt.Insets margin = new java.awt.Insets(0, 0, 0, 0);

    public JMenuBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(true);
        setBackground(new Color(0x3A3D43));
        setPreferredSize(new Dimension(0, DEFAULT_HEIGHT));
        setMinimumSize(new Dimension(0, DEFAULT_HEIGHT));
    }

    /** Typed delegate accessors, as in 8u; the bar is host-painted until a look and feel sets one. */
    @Override public javax.swing.plaf.MenuBarUI getUI() { return (javax.swing.plaf.MenuBarUI) ui; }
    public void setUI(javax.swing.plaf.MenuBarUI ui) { super.setUI(ui); }

    public JMenu add(JMenu c) {
        if (c != null) super.add(c);
        return c;
    }

    public JMenu getMenu(int index) {
        Component c = index >= 0 && getComponentCount() > index ? getComponent(index) : null;
        return c instanceof JMenu ? (JMenu) c : null;
    }

    public int getMenuCount() { return getComponentCount(); }
    public SingleSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(SingleSelectionModel model) {
        if (model == null) throw new IllegalArgumentException("selection model");
        SingleSelectionModel old = selectionModel; selectionModel = model;
        firePropertyChange("selectionModel", old, model); repaint();
    }
    public void setSelected(Component sel) {
        selectionModel.setSelectedIndex(getComponentIndex(sel));
    }

    public int getComponentIndex(Component c) {
        int n = getComponentCount();
        for (int i = 0; i < n; i++) {
            if (getComponent(i) == c) return i;
        }
        return -1;
    }

    /** @deprecated replaced by {@link java.awt.Container#getComponent(int)} */
    @Deprecated
    public Component getComponentAtIndex(int i) {
        return i < 0 || i >= getComponentCount() ? null : getComponent(i);
    }

    /** As in 8u, where the help menu was never implemented. */
    public void setHelpMenu(JMenu menu) {
        throw new Error("setHelpMenu() not yet implemented.");
    }

    /** As in 8u, where the help menu was never implemented. */
    public JMenu getHelpMenu() {
        throw new Error("getHelpMenu() not yet implemented.");
    }
    public boolean isSelected() { return selectionModel.isSelected(); }
    public void remove(int index) {
        int selected = selectionModel.getSelectedIndex(); super.remove(index);
        if (selected == index) selectionModel.clearSelection();
        else if (selected > index) selectionModel.setSelectedIndex(selected - 1);
    }
    public void remove(Component component) { for (int i = 0; i < getComponentCount(); i++) if (getComponent(i) == component) { remove(i); return; } }
    public void removeAll() { super.removeAll(); selectionModel.clearSelection(); }
    public void setBorderPainted(boolean b) { boolean old=borderPainted; borderPainted=b; firePropertyChange("borderPainted",old,b); revalidate(); repaint(); }
    public boolean isBorderPainted() { return borderPainted; }
    protected void paintBorder(java.awt.Graphics graphics) { if (borderPainted) super.paintBorder(graphics); }
    public boolean isManagingFocus() { return false; }
    public java.awt.Insets getMargin() { return (java.awt.Insets) margin.clone(); }
    public void setMargin(java.awt.Insets m) { java.awt.Insets old=margin; margin=m==null?new java.awt.Insets(0,0,0,0):(java.awt.Insets)m.clone(); firePropertyChange("margin",old,margin); revalidate(); repaint(); }
    public boolean isFloatable() { return false; }

    // MenuElement, as JMenuBar in 8u: the bar itself takes no mouse/key action, the
    // manager routes events to the menus it lists.
    public void processMouseEvent(java.awt.event.MouseEvent event, MenuElement path[], MenuSelectionManager manager) {}
    public void processKeyEvent(java.awt.event.KeyEvent e, MenuElement path[], MenuSelectionManager manager) {}
    public void menuSelectionChanged(boolean isIncluded) {}

    public MenuElement[] getSubElements() {
        java.util.List<MenuElement> tmp = new java.util.ArrayList<MenuElement>();
        int c = getComponentCount();
        for (int i = 0; i < c; i++) {
            Component m = getComponent(i);
            if (m instanceof MenuElement) tmp.add((MenuElement) m);
        }
        return tmp.toArray(new MenuElement[0]);
    }

    public Component getComponent() { return this; }

    // --- key bindings (OpenJDK 8u JMenuBar) ---------------------------------
    // The KeyboardManager hands every unclaimed WHEN_IN_FOCUSED_WINDOW stroke to the
    // registered bars; the bar passes it down its menus, which is how mnemonics fire.

    /**
     * Subclassed to check all the child menus.
     */
    protected boolean processKeyBinding(KeyStroke ks, java.awt.event.KeyEvent e,
                                        int condition, boolean pressed) {
        // See if we have a local binding.
        boolean retValue = super.processKeyBinding(ks, e, condition, pressed);
        if (!retValue) {
            MenuElement[] subElements = getSubElements();
            for (MenuElement subElement : subElements) {
                if (processBindingForKeyStrokeRecursive(subElement, ks, e, condition, pressed)) {
                    return true;
                }
            }
        }
        return retValue;
    }

    static boolean processBindingForKeyStrokeRecursive(MenuElement elem, KeyStroke ks,
                                                       java.awt.event.KeyEvent e, int condition, boolean pressed) {
        if (elem == null) {
            return false;
        }
        Component c = elem.getComponent();
        if (!(c.isVisible() || (c instanceof JPopupMenu)) || !c.isEnabled()) {
            return false;
        }
        if (c instanceof JComponent && ((JComponent) c).processKeyBinding(ks, e, condition, pressed)) {
            return true;
        }
        MenuElement[] subElements = elem.getSubElements();
        for (MenuElement subElement : subElements) {
            if (processBindingForKeyStrokeRecursive(subElement, ks, e, condition, pressed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Overrides <code>JComponent.addNotify</code> to register this
     * menu bar with the current keyboard manager.
     */
    public void addNotify() {
        super.addNotify();
        KeyboardManager.getCurrentManager().registerMenuBar(this);
    }

    /**
     * Overrides <code>JComponent.removeNotify</code> to unregister this
     * menu bar with the current keyboard manager.
     */
    public void removeNotify() {
        super.removeNotify();
        KeyboardManager.getCurrentManager().unregisterMenuBar(this);
    }
}
