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

import java.util.ArrayList;
import java.util.List;

/**
 * This is a support class to help build property editors.
 * <p>
 * It can be used either as a base class or as a delegate.
 */
public class PropertyEditorSupport implements PropertyEditor {
    private Object value;
    private Object source;
    private List<PropertyChangeListener> listeners;

    /**
     * Constructs a {@code PropertyEditorSupport} object.
     *
     * @since 1.5
     */
    public PropertyEditorSupport() {
        setSource(this);
    }

    /**
     * Constructs a {@code PropertyEditorSupport} object.
     *
     * @param source the source used for event firing
     * @since 1.5
     */
    public PropertyEditorSupport(Object source) {
        if (source == null) {
            throw new NullPointerException();
        }
        setSource(source);
    }

    /**
     * Returns the bean that is used as the
     * source of events.
     *
     * @return the source object or this instance
     * @since 1.5
     */
    public Object getSource() {
        return source;
    }

    /**
     * Sets the source bean.
     *
     * @param source source object to be used for events
     * @since 1.5
     */
    public void setSource(Object source) {
        this.source = source;
    }

    /**
     * Set (or change) the object that is to be edited.
     *
     * @param value The new target object to be edited.
     */
    public void setValue(Object value) {
        this.value = value;
        firePropertyChange();
    }

    /**
     * Gets the value of the property.
     *
     * @return The value of the property.
     */
    public Object getValue() {
        return value;
    }

    /**
     * Determines whether the class will honor the paintValue method.
     *
     * @return  True if the class will honor the paintValue method.
     */
    public boolean isPaintable() {
        return false;
    }

    /**
     * Paint a representation of the value into a given area of screen
     * real estate. The default does nothing, as on desktop, because
     * {@link #isPaintable} returns false.
     *
     * @param gfx  Graphics object to paint into.
     * @param box  Rectangle within graphics object into which we should paint.
     */
    public void paintValue(java.awt.Graphics gfx, java.awt.Rectangle box) {
    }

    /**
     * Returns "???", the JDK's placeholder initializer.
     *
     * @return A fragment of Java code representing an initializer for the
     *         current value.
     */
    public String getJavaInitializationString() {
        return "???";
    }

    /**
     * Gets the property value as a string suitable for presentation
     * to a human to edit.
     *
     * @return The property value as a string suitable for presentation
     *       to a human to edit.
     *       <p>   Returns null if the value can't be expressed as a string.
     */
    public String getAsText() {
        return (this.value != null) ? this.value.toString() : null;
    }

    /**
     * Sets the property value by parsing a given String. May raise
     * java.lang.IllegalArgumentException if either the String is
     * badly formatted or if this kind of property can't be expressed
     * as text.
     *
     * @param text  The string to be parsed.
     */
    public void setAsText(String text) throws java.lang.IllegalArgumentException {
        if (value instanceof String) {
            setValue(text);
            return;
        }
        throw new java.lang.IllegalArgumentException(text);
    }

    /**
     * If the property value must be one of a set of known tagged values,
     * then this method should return an array of the tag values.
     *
     * @return The tag values for this property, or null.
     */
    public String[] getTags() {
        return null;
    }

    /**
     * A PropertyEditor may chose to make available a full custom Component
     * that edits its property value.
     *
     * @return A java.awt.Component that will allow a human to directly
     *      edit the current property value; null for this class.
     */
    public java.awt.Component getCustomEditor() {
        return null;
    }

    /**
     * Determines whether the propertyEditor can provide a custom editor.
     *
     * @return  True if the propertyEditor can provide a custom editor.
     */
    public boolean supportsCustomEditor() {
        return false;
    }

    /**
     * Adds a listener for the value change.
     *
     * @param listener  the {@link PropertyChangeListener} to add
     */
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }
        listeners.add(listener);
    }

    /**
     * Removes a listener for the value change.
     *
     * @param listener  the {@link PropertyChangeListener} to remove
     */
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
    }

    /**
     * Report that we have been modified to any interested listeners.
     */
    public void firePropertyChange() {
        List<PropertyChangeListener> targets;
        synchronized (this) {
            if (listeners == null) {
                return;
            }
            targets = new ArrayList<>(listeners);
        }
        // Tell our listeners that "everything" has changed.
        PropertyChangeEvent evt = new PropertyChangeEvent(source, null, null, null);
        for (PropertyChangeListener target : targets) {
            target.propertyChange(evt);
        }
    }
}
