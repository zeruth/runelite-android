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
 * This is a utility class that can be used by beans that support constrained
 * properties. It manages a list of listeners and dispatches
 * {@link PropertyChangeEvent}s to them. If a listener vetoes, every listener
 * already notified receives a reverting event and the veto is rethrown,
 * as on desktop.
 */
public class VetoableChangeSupport implements Serializable {
    private static final long serialVersionUID = -5090210921595982017L;

    private final Object source;
    private final List<VetoableChangeListener> all = new ArrayList<>();
    private final Map<String, List<VetoableChangeListener>> named = new LinkedHashMap<>();

    /**
     * Constructs a {@code VetoableChangeSupport} object.
     *
     * @param sourceBean  The bean to be given as the source for any events.
     */
    public VetoableChangeSupport(Object sourceBean) {
        if (sourceBean == null) {
            throw new NullPointerException();
        }
        source = sourceBean;
    }

    /**
     * Add a VetoableChangeListener to the listener list.
     * The listener is registered for all properties.
     * If {@code listener} is null, no exception is thrown and no action
     * is taken.
     *
     * @param listener  The VetoableChangeListener to be added
     */
    public void addVetoableChangeListener(VetoableChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (listener instanceof VetoableChangeListenerProxy) {
            VetoableChangeListenerProxy proxy = (VetoableChangeListenerProxy) listener;
            addVetoableChangeListener(proxy.getPropertyName(), proxy.getListener());
        } else {
            synchronized (this) {
                all.add(listener);
            }
        }
    }

    /**
     * Remove a VetoableChangeListener from the listener list.
     * This removes a VetoableChangeListener that was registered
     * for all properties.
     *
     * @param listener  The VetoableChangeListener to be removed
     */
    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        if (listener == null) {
            return;
        }
        if (listener instanceof VetoableChangeListenerProxy) {
            VetoableChangeListenerProxy proxy = (VetoableChangeListenerProxy) listener;
            removeVetoableChangeListener(proxy.getPropertyName(), proxy.getListener());
        } else {
            synchronized (this) {
                all.remove(listener);
            }
        }
    }

    /**
     * Returns an array of all the listeners that were added to the
     * VetoableChangeSupport object with addVetoableChangeListener().
     * Listeners added for a named property are returned wrapped in a
     * {@code VetoableChangeListenerProxy}.
     *
     * @return all of the {@code VetoableChangeListeners} added or an
     *         empty array if no listeners have been added
     * @since 1.4
     */
    public synchronized VetoableChangeListener[] getVetoableChangeListeners() {
        List<VetoableChangeListener> result = new ArrayList<>(all);
        for (Map.Entry<String, List<VetoableChangeListener>> e : named.entrySet()) {
            for (VetoableChangeListener l : e.getValue()) {
                result.add(new VetoableChangeListenerProxy(e.getKey(), l));
            }
        }
        return result.toArray(new VetoableChangeListener[0]);
    }

    /**
     * Add a VetoableChangeListener for a specific property. The listener
     * will be invoked only when a call on fireVetoableChange names that
     * specific property. If {@code propertyName} or {@code listener} is
     * null, no exception is thrown and no action is taken.
     *
     * @param propertyName  The name of the property to listen on.
     * @param listener  The VetoableChangeListener to be added
     * @since 1.2
     */
    public void addVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
        if (listener == null || propertyName == null) {
            return;
        }
        while (listener instanceof VetoableChangeListenerProxy) {
            listener = ((VetoableChangeListenerProxy) listener).getListener();
        }
        synchronized (this) {
            List<VetoableChangeListener> list = named.get(propertyName);
            if (list == null) {
                list = new ArrayList<>();
                named.put(propertyName, list);
            }
            list.add(listener);
        }
    }

    /**
     * Remove a VetoableChangeListener for a specific property.
     *
     * @param propertyName  The name of the property that was listened on.
     * @param listener  The VetoableChangeListener to be removed
     * @since 1.2
     */
    public void removeVetoableChangeListener(String propertyName, VetoableChangeListener listener) {
        if (listener == null || propertyName == null) {
            return;
        }
        while (listener instanceof VetoableChangeListenerProxy) {
            listener = ((VetoableChangeListenerProxy) listener).getListener();
        }
        synchronized (this) {
            List<VetoableChangeListener> list = named.get(propertyName);
            if (list != null) {
                list.remove(listener);
                if (list.isEmpty()) {
                    named.remove(propertyName);
                }
            }
        }
    }

    /**
     * Returns an array of all the listeners which have been associated
     * with the named property.
     *
     * @param propertyName  The name of the property being listened to
     * @return all the {@code VetoableChangeListeners} associated with
     *         the named property; if no such listeners have been added,
     *         or if {@code propertyName} is null, an empty array is
     *         returned.
     * @since 1.4
     */
    public synchronized VetoableChangeListener[] getVetoableChangeListeners(String propertyName) {
        List<VetoableChangeListener> list = propertyName == null ? null : named.get(propertyName);
        return list == null ? new VetoableChangeListener[0] : list.toArray(new VetoableChangeListener[0]);
    }

    /**
     * Reports a constrained property update to listeners
     * that have been registered to track updates of
     * all properties or a property with the specified name.
     * No event is fired if old and new values are equal and non-null.
     *
     * @param propertyName  the programmatic name of the property that is about to change
     * @param oldValue      the old value of the property
     * @param newValue      the new value of the property
     * @throws PropertyVetoException if one of listeners vetoes the property update
     */
    public void fireVetoableChange(String propertyName, Object oldValue, Object newValue)
            throws PropertyVetoException {
        if (oldValue == null || newValue == null || !oldValue.equals(newValue)) {
            fireVetoableChange(new PropertyChangeEvent(this.source, propertyName, oldValue, newValue));
        }
    }

    /**
     * Reports an integer constrained property update to listeners.
     *
     * @param propertyName  the programmatic name of the property that is about to change
     * @param oldValue      the old value of the property
     * @param newValue      the new value of the property
     * @throws PropertyVetoException if one of listeners vetoes the property update
     */
    public void fireVetoableChange(String propertyName, int oldValue, int newValue)
            throws PropertyVetoException {
        if (oldValue != newValue) {
            fireVetoableChange(propertyName, Integer.valueOf(oldValue), Integer.valueOf(newValue));
        }
    }

    /**
     * Reports a boolean constrained property update to listeners.
     *
     * @param propertyName  the programmatic name of the property that is about to change
     * @param oldValue      the old value of the property
     * @param newValue      the new value of the property
     * @throws PropertyVetoException if one of listeners vetoes the property update
     */
    public void fireVetoableChange(String propertyName, boolean oldValue, boolean newValue)
            throws PropertyVetoException {
        if (oldValue != newValue) {
            fireVetoableChange(propertyName, Boolean.valueOf(oldValue), Boolean.valueOf(newValue));
        }
    }

    /**
     * Fires a property change event to listeners that have been registered
     * to track updates of all properties or a property with the specified
     * name. Any listener can throw a {@code PropertyVetoException}; if one
     * does, a new event reverting this change is fired to all listeners
     * that already confirmed it and the exception is rethrown.
     *
     * @param event  the {@code PropertyChangeEvent} to be fired
     * @throws PropertyVetoException if one of listeners vetoes the property update
     */
    public void fireVetoableChange(PropertyChangeEvent event) throws PropertyVetoException {
        Object oldValue = event.getOldValue();
        Object newValue = event.getNewValue();
        if (oldValue != null && newValue != null && oldValue.equals(newValue)) {
            return;
        }
        String name = event.getPropertyName();
        VetoableChangeListener[] listeners;
        synchronized (this) {
            List<VetoableChangeListener> list = new ArrayList<>(all);
            List<VetoableChangeListener> forName = name == null ? null : named.get(name);
            if (forName != null) {
                list.addAll(forName);
            }
            listeners = list.toArray(new VetoableChangeListener[0]);
        }
        int current = 0;
        try {
            while (current < listeners.length) {
                listeners[current].vetoableChange(event);
                current++;
            }
        } catch (PropertyVetoException veto) {
            event = new PropertyChangeEvent(this.source, name, newValue, oldValue);
            for (int i = 0; i < current; i++) {
                try {
                    listeners[i].vetoableChange(event);
                } catch (PropertyVetoException exception) {
                    // ignore exceptions that occur during rolling back
                }
            }
            throw veto;
        }
    }

    /**
     * Check if there are any listeners for a specific property, including
     * those registered on all properties. If {@code propertyName}
     * is null, only check for listeners registered on all properties.
     *
     * @param propertyName  the property name.
     * @return true if there are one or more listeners for the given property
     */
    public synchronized boolean hasListeners(String propertyName) {
        if (!all.isEmpty()) {
            return true;
        }
        List<VetoableChangeListener> list = propertyName == null ? null : named.get(propertyName);
        return list != null && !list.isEmpty();
    }
}
