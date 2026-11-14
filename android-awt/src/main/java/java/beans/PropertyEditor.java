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

/**
 * A PropertyEditor class provides support for GUIs that want to
 * allow users to edit a property value of a given type.
 */
public interface PropertyEditor {
    /**
     * Set (or change) the object that is to be edited.
     * @param value The new target object to be edited.
     */
    void setValue(Object value);

    /**
     * Gets the property value.
     * @return The value of the property.
     */
    Object getValue();

    /**
     * Determines whether this property editor is paintable.
     * @return  True if the class will honor the paintValue method.
     */
    boolean isPaintable();

    /**
     * Paint a representation of the value into a given area of screen
     * real estate.
     * @param gfx  Graphics object to paint into.
     * @param box  Rectangle within graphics object into which we should paint.
     */
    void paintValue(java.awt.Graphics gfx, java.awt.Rectangle box);

    /**
     * Returns a fragment of Java code that can be used to set a property
     * to match the editors current state.
     * @return a fragment of Java code representing an initializer for the
     *         current value.
     */
    String getJavaInitializationString();

    /**
     * Gets the property value as text.
     * @return The property value as a human editable string, or null if
     *         the value can't be expressed as an editable string.
     */
    String getAsText();

    /**
     * Set the property value by parsing a given String.
     * @param text  The string to be parsed.
     * @throws IllegalArgumentException if the String is badly formatted
     *         or this kind of property can't be expressed as text
     */
    void setAsText(String text) throws java.lang.IllegalArgumentException;

    /**
     * If the property value must be one of a set of known tagged values,
     * then this method should return an array of the tags.
     * @return The tag values for this property, or null if this property
     *         cannot be represented as a tagged value.
     */
    String[] getTags();

    /**
     * A PropertyEditor may choose to make available a full custom Component
     * that edits its property value.
     * @return A java.awt.Component that will allow a human to directly
     *         edit the current property value, or null if this is not supported.
     */
    java.awt.Component getCustomEditor();

    /**
     * Determines whether this property editor supports a custom editor.
     * @return  True if the propertyEditor can provide a custom editor.
     */
    boolean supportsCustomEditor();

    /**
     * Adds a listener for the value change.
     * @param listener  the {@link PropertyChangeListener} to add
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes a listener for the value change.
     * @param listener  the {@link PropertyChangeListener} to remove
     */
    void removePropertyChangeListener(PropertyChangeListener listener);
}
