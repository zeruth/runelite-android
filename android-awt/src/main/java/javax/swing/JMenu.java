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
    private final JPopupMenu popup = new JPopupMenu();
    public JMenu() {
        popup.setInvoker(this);
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener(){
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event){setSelected(true);if(getParent() instanceof JMenuBar)((JMenuBar)getParent()).setSelected(JMenu.this);}
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event){clear();}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event){clear();}
            private void clear(){setSelected(false);if(getParent() instanceof JMenuBar){JMenuBar bar=(JMenuBar)getParent();if(bar.getSelectionModel().getSelectedIndex()==bar.getComponentZOrder(JMenu.this))bar.setSelected(null);}}
        });
    }
    public JMenu(String text) { this(); setText(text); }
    public JMenu(String text, boolean tearOff) { this(text); }
    public JMenu(Action action) { this(); setAction(action); }
    public JPopupMenu getPopupMenu() { return popup; }
    public JMenuItem add(JMenuItem item) { return popup.add(item); }
    public JMenuItem add(String text) { return popup.add(text); }
    public JMenuItem add(Action action) { return popup.add(action); }
    public java.awt.Component add(java.awt.Component component) { return popup.add(component); }
    public void addSeparator() { popup.addSeparator(); }
    public JMenuItem insert(JMenuItem item, int index) { popup.insert(item,index); return item; }
    public JMenuItem insert(Action action, int index) { return insert(new JMenuItem(action),index); }
    public void insert(String text, int index) { insert(new JMenuItem(text),index); }
    public void insertSeparator(int index) { popup.insert(new JPopupMenu.Separator(),index); }
    public JMenuItem getItem(int index) { java.awt.Component child=popup.getComponent(index);return child instanceof JMenuItem?(JMenuItem)child:null; }
    public int getItemCount() { return popup.getComponentCount(); }
    public int getMenuComponentCount() { return popup.getComponentCount(); }
    public java.awt.Component getMenuComponent(int index) { return popup.getComponent(index); }
    public java.awt.Component[] getMenuComponents() { return popup.getComponents(); }
    public void remove(JMenuItem item) { popup.remove(item); }
    public void remove(int index) { popup.remove(index); }
    public void remove(java.awt.Component component) { popup.remove(component); }
    public void removeAll() { popup.removeAll(); }
    public boolean isTearOff() { return false; }
    public boolean isPopupMenuVisible() { return popup.isVisible(); }
    public void setPopupMenuVisible(boolean visible) { if(visible&&isEnabled())popup.show(this,0,getHeight());else popup.setVisible(false); }
    public void doClick(int pressTime) { setPopupMenuVisible(!isPopupMenuVisible()); }
    public MenuElement[] getSubElements() { return new MenuElement[]{popup}; }
    public void menuSelectionChanged(boolean included) { setSelected(included); }
}
