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
public class Menu extends MenuItem implements MenuContainer {
    private final java.util.List<MenuItem> items = new java.util.ArrayList<>();
    private final boolean tearOff;
    public Menu() { this(""); }
    public Menu(String label) { this(label, false); }
    public Menu(String label, boolean tearOff) { super(label); this.tearOff = tearOff; }
    private static int nameCounter = 0;
    @Override String constructComponentName() { synchronized (Menu.class) { return "menu" + nameCounter++; } }
    public boolean isTearOff() { return tearOff; }
    public MenuItem add(MenuItem item) { insert(item, items.size()); return item; }
    public void add(String label) { add(new MenuItem(label)); }
    public void addSeparator() { add("-"); }
    public void insert(MenuItem item, int index) {
        if (index < 0) throw new IllegalArgumentException("negative index");
        if (item == null) throw new NullPointerException("item");
        for (MenuContainer ancestor = this; ancestor instanceof Menu; ancestor = ((Menu) ancestor).getParent())
            if (ancestor == item) throw new IllegalArgumentException("menu cycle");
        if (item.parent != null) item.parent.remove(item);
        items.add(Math.min(index, items.size()), item); item.parent = this;
    }
    public void insert(String label, int index) { insert(new MenuItem(label), index); }
    public void insertSeparator(int index) { insert("-", index); }
    public int getItemCount() { return items.size(); }
    public int countItems() { return getItemCount(); }
    public MenuItem getItem(int index) { return items.get(index); }
    public void remove(int index) { MenuItem item = items.remove(index); item.parent = null; }
    @Override public void remove(MenuComponent item) { if (items.remove(item)) item.parent = null; }
    public void removeAll() { for (MenuItem item : items) item.parent = null; items.clear(); }
    /** There is no native menu peer on Android; the menu is realized as a Swing popup when shown. */
    public void addNotify() {}
    @Override public String paramString() { return super.paramString() + ",tearOff=" + tearOff + ",isHelpMenu=" + (getParent() instanceof MenuBar && ((MenuBar) getParent()).getHelpMenu() == this); }
    // The shortcut lookups MenuBar delegates to, recursive through submenus as on the JDK.
    void collectShortcuts(java.util.Vector<MenuShortcut> into) {
        for (MenuItem item : items) {
            if (item instanceof Menu) ((Menu) item).collectShortcuts(into);
            else if (item.getShortcut() != null) into.addElement(item.getShortcut());
        }
    }
    MenuItem getShortcutMenuItem(MenuShortcut s) {
        for (MenuItem item : items) {
            MenuItem found = item instanceof Menu ? ((Menu) item).getShortcutMenuItem(s) : s != null && s.equals(item.getShortcut()) ? item : null;
            if (found != null) return found;
        }
        return null;
    }
    void deleteShortcut(MenuShortcut s) {
        for (MenuItem item : items) {
            if (item instanceof Menu) ((Menu) item).deleteShortcut(s);
            else if (s != null && s.equals(item.getShortcut())) item.deleteShortcut();
        }
    }
    void fill(javax.swing.JPopupMenu popup) {
        for (MenuItem item : new java.util.ArrayList<>(items)) {
            if ("-".equals(item.getLabel())) popup.addSeparator(); else popup.add(item.swingItem());
        }
    }
    @Override javax.swing.JMenuItem swingItem() {
        javax.swing.JMenu menu = new javax.swing.JMenu(getLabel()); configure(menu); fill(menu.getPopupMenu()); return menu;
    }
}
