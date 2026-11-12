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
public class CheckboxMenuItem extends MenuItem implements ItemSelectable {
    private boolean state;
    private final java.util.List<ItemListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    public CheckboxMenuItem() { this(""); }
    public CheckboxMenuItem(String label) { this(label, false); }
    public CheckboxMenuItem(String label, boolean state) { super(label); this.state = state; }
    public boolean getState() { return state; }
    public void setState(boolean state) { this.state = state; }
    public Object[] getSelectedObjects() { return state ? new Object[]{getLabel()} : null; }
    public void addItemListener(ItemListener listener) { if (listener != null) listeners.add(listener); }
    public void removeItemListener(ItemListener listener) { listeners.remove(listener); }
    public ItemListener[] getItemListeners() { return listeners.toArray(new ItemListener[0]); }
    @SuppressWarnings("unchecked")
    @Override public <T extends java.util.EventListener> T[] getListeners(Class<T> listenerType) {
        if (listenerType == ItemListener.class) return (T[]) getItemListeners();
        return super.getListeners(listenerType);
    }
    @Override public String paramString() { return super.paramString() + ",state=" + state; }
    protected void processItemEvent(ItemEvent event) { for (ItemListener listener : listeners) listener.itemStateChanged(event); }
    @Override protected void processEvent(AWTEvent event) { if (event instanceof ItemEvent) processItemEvent((ItemEvent) event); else super.processEvent(event); }
    @Override javax.swing.JMenuItem swingItem() {
        javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(getLabel(), state);
        item.setEnabled(isEnabled());
        item.addItemListener(event -> { state = item.isSelected(); processItemEvent(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, getLabel(), state ? ItemEvent.SELECTED : ItemEvent.DESELECTED)); });
        return item;
    }
}
