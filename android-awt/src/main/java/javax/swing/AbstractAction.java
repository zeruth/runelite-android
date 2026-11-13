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

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class AbstractAction implements Action, Cloneable, Serializable {
    private static final long serialVersionUID = -1748743320228L;

    protected boolean enabled = true;
    private Map<String, Object> values = new HashMap<>();
    // Buttons built from this action (AbstractButton.setAction) listen here to follow
    // NAME/enabled/icon edits, as Swing's SwingPropertyChangeSupport does.
    private transient CopyOnWriteArrayList<PropertyChangeListener> listeners;

    public AbstractAction() {}
    public AbstractAction(String name) { putValue(NAME, name); }
    public AbstractAction(String name, Icon icon) { this(name); putValue(SMALL_ICON, icon); }

    @Override public Object getValue(String key) {
        if ("enabled".equals(key)) return enabled;
        return values.get(key);
    }

    @Override public void putValue(String key, Object newValue) {
        Object oldValue;
        if ("enabled".equals(key)) {
            if (!(newValue instanceof Boolean)) newValue = false;
            oldValue = enabled;
            enabled = (Boolean) newValue;
        } else {
            oldValue = newValue == null ? values.remove(key) : values.put(key, newValue);
        }
        firePropertyChange(key, oldValue, newValue);
    }

    @Override public boolean isEnabled() { return enabled; }
    @Override public void setEnabled(boolean b) {
        boolean old = enabled;
        if (old != b) {
            enabled = b;
            firePropertyChange("enabled", old, b);
        }
    }

    public Object[] getKeys() { return values.isEmpty() ? null : values.keySet().toArray(); }

    @Override public synchronized void addPropertyChangeListener(PropertyChangeListener l) {
        if (l == null) return;
        if (listeners == null) listeners = new CopyOnWriteArrayList<>();
        listeners.add(l);
    }
    @Override public synchronized void removePropertyChangeListener(PropertyChangeListener l) {
        if (listeners != null) listeners.remove(l);
    }
    public synchronized PropertyChangeListener[] getPropertyChangeListeners() {
        return listeners == null ? new PropertyChangeListener[0] : listeners.toArray(new PropertyChangeListener[0]);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        CopyOnWriteArrayList<PropertyChangeListener> ls = listeners;
        if (ls == null || ls.isEmpty() || (oldValue != null && newValue != null && oldValue.equals(newValue))) return;
        PropertyChangeEvent e = new PropertyChangeEvent(this, propertyName, oldValue, newValue);
        for (PropertyChangeListener l : ls) l.propertyChange(e);
    }

    @Override public abstract void actionPerformed(ActionEvent e);

    @Override public Object clone() throws CloneNotSupportedException {
        AbstractAction copy = (AbstractAction) super.clone();
        // Swing clones the key table but not the listeners.
        copy.values = new HashMap<>(values);
        copy.listeners = null;
        return copy;
    }
}
