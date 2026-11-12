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
package java.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A utility class for beans that support constrained properties: it keeps the
 * listeners, unnamed and per property, and fires {@code PropertyChangeEvent}s
 * to them. When a listener vetoes a change, every listener notified before it
 * is told about the reversion (old and new value swapped) and the veto is
 * rethrown, as the desktop class does.
 */
public class VetoableChangeSupport implements Serializable {
    private static final long serialVersionUID = -5090210921595982017L;

    private final Object source;
    // Key null holds the listeners registered for every property.
    private final Map<String, List<VetoableChangeListener>> map = new LinkedHashMap<>();

    /**
     * Constructs a {@code VetoableChangeSupport} object.
     *
     * @param sourceBean the bean to be given as the source for any events
     */
    public VetoableChangeSupport(Object sourceBean) {
        if (sourceBean == null) {
            throw new NullPointerException();
        }
        source = sourceBean;
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (listener instanceof VetoableChangeListenerProxy) {
            VetoableChangeListenerProxy proxy = (VetoableChangeListenerProxy) listener;
            addVetoableChangeListener(proxy.getPropertyName(), proxy.getListener());
        } else {
            add(null, listener);
        }
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (listener instanceof VetoableChangeListenerProxy) {
            VetoableChangeListenerProxy proxy = (VetoableChangeListenerProxy) listener;
            removeVetoableChangeListener(proxy.getPropertyName(), proxy.getListener());
        } else {
            remove(null, listener);
        }
    }

    /**
     * Returns all listeners; the ones registered for a single property come
     * back wrapped in a {@link VetoableChangeListenerProxy}.
     */
    public synchronized VetoableChangeListener[] getVetoableChangeListeners() {
        List<VetoableChangeListener> out = new ArrayList<>();
        for (Map.Entry<String, List<VetoableChangeListener>> entry : map.entrySet()) {
            for (VetoableChangeListener listener : entry.getValue()) {
                out.add(entry.getKey() == null ? listener
                        : new VetoableChangeListenerProxy(entry.getKey(), listener));
            }
        }
        return out.toArray(new VetoableChangeListener[0]);
    }

    public void addVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
        if (listener == null || propertyName == null) {
            return;
        }
        add(propertyName, unwrap(listener));
    }

    public void removeVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
        if (listener == null || propertyName == null) {
            return;
        }
        remove(propertyName, unwrap(listener));
    }

    // A proxy handed to the named overloads would otherwise be stored wrapped and
    // come back double-wrapped from getVetoableChangeListeners().
    private static VetoableChangeListener unwrap(VetoableChangeListener listener) {
        while (listener instanceof VetoableChangeListenerProxy) {
            listener = ((VetoableChangeListenerProxy) listener).getListener();
        }
        return listener;
    }

    public synchronized VetoableChangeListener[] getVetoableChangeListeners(String propertyName) {
        List<VetoableChangeListener> listeners = propertyName == null ? null : map.get(propertyName);
        return listeners == null ? new VetoableChangeListener[0]
                : listeners.toArray(new VetoableChangeListener[0]);
    }

    public void fireVetoableChange(String propertyName, Object oldValue, Object newValue)
            throws PropertyVetoException {
        if (oldValue == null || newValue == null || !oldValue.equals(newValue)) {
            fireVetoableChange(new PropertyChangeEvent(this.source, propertyName, oldValue, newValue));
        }
    }

    public void fireVetoableChange(String propertyName, int oldValue, int newValue)
            throws PropertyVetoException {
        if (oldValue != newValue) {
            fireVetoableChange(propertyName, Integer.valueOf(oldValue), Integer.valueOf(newValue));
        }
    }

    public void fireVetoableChange(String propertyName, boolean oldValue, boolean newValue)
            throws PropertyVetoException {
        if (oldValue != newValue) {
            fireVetoableChange(propertyName, Boolean.valueOf(oldValue), Boolean.valueOf(newValue));
        }
    }

    public void fireVetoableChange(PropertyChangeEvent event) throws PropertyVetoException {
        Object oldValue = event.getOldValue();
        Object newValue = event.getNewValue();
        if (oldValue != null && newValue != null && oldValue.equals(newValue)) {
            return;
        }
        String name = event.getPropertyName();
        List<VetoableChangeListener> listeners = new ArrayList<>();
        synchronized (this) {
            List<VetoableChangeListener> common = map.get(null);
            if (common != null) listeners.addAll(common);
            List<VetoableChangeListener> named = name != null ? map.get(name) : null;
            if (named != null) listeners.addAll(named);
        }
        int current = 0;
        try {
            while (current < listeners.size()) {
                listeners.get(current).vetoableChange(event);
                current++;
            }
        } catch (PropertyVetoException veto) {
            PropertyChangeEvent revert = new PropertyChangeEvent(this.source, name, newValue, oldValue);
            for (int i = 0; i < current; i++) {
                try {
                    listeners.get(i).vetoableChange(revert);
                } catch (PropertyVetoException ignored) {
                    // A listener cannot veto the reversion.
                }
            }
            throw veto;
        }
    }

    public synchronized boolean hasListeners(String propertyName) {
        if (map.containsKey(null)) {
            return true;
        }
        return propertyName != null && map.containsKey(propertyName);
    }

    private synchronized void add(String name, VetoableChangeListener listener) {
        List<VetoableChangeListener> list = map.get(name);
        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }
        list.add(listener);
    }

    private synchronized void remove(String name, VetoableChangeListener listener) {
        List<VetoableChangeListener> list = map.get(name);
        if (list != null && list.remove(listener) && list.isEmpty()) {
            map.remove(name);
        }
    }
}
