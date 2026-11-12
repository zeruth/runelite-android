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
    public void show(Component invoker, int x, int y) {
        this.invoker = invoker;
        setVisible(true);
        shownPopup.set(this);
        // Without a real Swing root pane to mount onto, positioning here is best-effort.
        java.awt.Dimension d = getPreferredSize();
        setBounds(x, y, Math.max(80, d.width), Math.max(20, d.height));
        doLayout();
        repaint();
    }
    public Component getInvoker() { return invoker; }
    public void setInvoker(Component invoker) { this.invoker = invoker; }
    private String label;
    public void setLabel(String label) { String old=this.label;this.label=label;firePropertyChange("label",old,label); }
    public String getLabel() { return label; }
    public java.awt.Component getComponent() { return this; }
    public void menuSelectionChanged(boolean included) { if(!included)setVisible(false); }
    public void addPopupMenuListener(PopupMenuListener l) { if (l != null) popupListeners.add(l); }
    public void removePopupMenuListener(PopupMenuListener l) { popupListeners.remove(l); }
    @Override public void setVisible(boolean visible) {
        if (visible == isVisible()) return;
        javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(this);
        for (PopupMenuListener listener : new java.util.ArrayList<>(popupListeners)) {
            if (visible) listener.popupMenuWillBecomeVisible(event);
            else listener.popupMenuWillBecomeInvisible(event);
        }
        super.setVisible(visible);
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
