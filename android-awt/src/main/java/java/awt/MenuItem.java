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
import java.awt.event.*;
public class MenuItem extends MenuComponent {
    private String label, command;
    private boolean enabled = true;
    private MenuShortcut shortcut;
    private final java.util.List<ActionListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    public MenuItem() { this(""); }
    public MenuItem(String label) { this(label, null); }
    public MenuItem(String label, MenuShortcut shortcut) { this.label = label; this.shortcut = shortcut; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void enable() { setEnabled(true); }
    public void enable(boolean enabled) { setEnabled(enabled); }
    public void disable() { setEnabled(false); }
    public String getActionCommand() { return command == null ? label : command; }
    public void setActionCommand(String command) { this.command = command; }
    public MenuShortcut getShortcut() { return shortcut; }
    public void setShortcut(MenuShortcut shortcut) { this.shortcut = shortcut; }
    public void deleteShortcut() { shortcut = null; }
    public void addActionListener(ActionListener listener) { if (listener != null) listeners.add(listener); }
    public void removeActionListener(ActionListener listener) { listeners.remove(listener); }
    public ActionListener[] getActionListeners() { return listeners.toArray(new ActionListener[0]); }
    @SuppressWarnings("unchecked")
    public <T extends java.util.EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == ActionListener.class) return (T[]) getActionListeners();
        return (T[]) java.lang.reflect.Array.newInstance(listenerType, 0);
    }
    @Override public String paramString() {
        String str = ",label=" + label;
        if (shortcut != null) str += ",shortcut=" + shortcut;
        return super.paramString() + str;
    }
    @Override protected void processEvent(AWTEvent event) { if (event instanceof ActionEvent) processActionEvent((ActionEvent) event); }
    protected void processActionEvent(ActionEvent event) { for (ActionListener listener : listeners) listener.actionPerformed(event); }
    javax.swing.JMenuItem swingItem() {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        configure(item); return item;
    }
    void configure(javax.swing.JMenuItem item) {
        item.setEnabled(enabled);
        if (getFont() != null) item.setFont(getFont());
        item.addActionListener(event -> processActionEvent(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand(), event.getWhen(), event.getModifiers())));
    }
}
