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

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

/**
 * The FeatureDescriptor class is the common baseclass for PropertyDescriptor,
 * EventSetDescriptor, and MethodDescriptor, etc.
 * <p>
 * It supports some common information that can be set and retrieved for
 * any of the introspection descriptors.
 * <p>
 * In addition it provides an extension mechanism so that arbitrary
 * attribute/value pairs can be associated with a design feature.
 *
 * @since 1.1
 */
public class FeatureDescriptor {
    private String name;
    private String displayName;
    private String shortDescription;
    private boolean expert;
    private boolean hidden;
    private boolean preferred;
    private Map<String, Object> table;

    /**
     * Constructs a {@code FeatureDescriptor}.
     */
    public FeatureDescriptor() {
    }

    /**
     * Gets the programmatic name of this feature.
     *
     * @return The programmatic name of the property/method/event
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the programmatic name of this feature.
     *
     * @param name  The programmatic name of the property/method/event
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the localized display name of this feature.
     *
     * @return The localized display name for the property/method/event.
     *  This defaults to the same as its programmatic name from getName.
     */
    public String getDisplayName() {
        return displayName == null ? getName() : displayName;
    }

    /**
     * Sets the localized display name of this feature.
     *
     * @param displayName  The localized display name for the
     *          property/method/event.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * The "expert" flag is used to distinguish between those features that are
     * intended for expert users from those that are intended for normal users.
     *
     * @return True if this feature is intended for use by experts only.
     */
    public boolean isExpert() {
        return expert;
    }

    /**
     * The "expert" flag is used to distinguish between features that are
     * intended for expert users from those that are intended for normal users.
     *
     * @param expert True if this feature is intended for use by experts only.
     */
    public void setExpert(boolean expert) {
        this.expert = expert;
    }

    /**
     * The "hidden" flag is used to identify features that are intended only
     * for tool use, and which should not be exposed to humans.
     *
     * @return True if this feature should be hidden from human users.
     */
    public boolean isHidden() {
        return hidden;
    }

    /**
     * The "hidden" flag is used to identify features that are intended only
     * for tool use, and which should not be exposed to humans.
     *
     * @param hidden  True if this feature should be hidden from human users.
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * The "preferred" flag is used to identify features that are particularly
     * important for presenting to humans.
     *
     * @return True if this feature should be preferentially shown to human users.
     * @since 1.2
     */
    public boolean isPreferred() {
        return preferred;
    }

    /**
     * The "preferred" flag is used to identify features that are particularly
     * important for presenting to humans.
     *
     * @param preferred  True if this feature should be preferentially shown
     *                   to human users.
     * @since 1.2
     */
    public void setPreferred(boolean preferred) {
        this.preferred = preferred;
    }

    /**
     * Gets the short description of this feature.
     *
     * @return  A localized short description associated with this
     *   property/method/event.  This defaults to be the display name.
     */
    public String getShortDescription() {
        return shortDescription == null ? getDisplayName() : shortDescription;
    }

    /**
     * You can associate a short descriptive string with a feature.  Normally
     * these descriptive strings should be less than about 40 characters.
     *
     * @param text  A (localized) short description to be associated with
     * this property/method/event.
     */
    public void setShortDescription(String text) {
        shortDescription = text;
    }

    /**
     * Associate a named attribute with this feature.
     *
     * @param attributeName  The locale-independent name of the attribute
     * @param value  The value.
     */
    public void setValue(String attributeName, Object value) {
        getTable().put(attributeName, value);
    }

    /**
     * Retrieve a named attribute with this feature.
     *
     * @param attributeName  The locale-independent name of the attribute
     * @return  The value of the attribute.  May be null if
     *     the attribute is unknown.
     */
    public Object getValue(String attributeName) {
        return table != null ? table.get(attributeName) : null;
    }

    /**
     * Gets an enumeration of the locale-independent names of this
     * feature.
     *
     * @return  An enumeration of the locale-independent names of any
     *    attributes that have been registered with setValue.
     */
    public Enumeration<String> attributeNames() {
        return Collections.enumeration(getTable().keySet());
    }

    private Map<String, Object> getTable() {
        if (table == null) {
            table = new TreeMap<>();
        }
        return table;
    }

    /**
     * Package-private merge: the attributes and flags of {@code y} win over
     * those of {@code x}, as when the Introspector combines a superclass
     * descriptor with the subclass one.
     */
    FeatureDescriptor(FeatureDescriptor x, FeatureDescriptor y) {
        expert = x.expert | y.expert;
        hidden = x.hidden | y.hidden;
        preferred = x.preferred | y.preferred;
        name = y.name;
        shortDescription = x.shortDescription;
        if (y.shortDescription != null) {
            shortDescription = y.shortDescription;
        }
        displayName = x.displayName;
        if (y.displayName != null) {
            displayName = y.displayName;
        }
        addTable(x.table);
        addTable(y.table);
    }

    /** Package-private copy constructor. */
    FeatureDescriptor(FeatureDescriptor old) {
        expert = old.expert;
        hidden = old.hidden;
        preferred = old.preferred;
        name = old.name;
        shortDescription = old.shortDescription;
        displayName = old.displayName;
        addTable(old.table);
    }

    private void addTable(Map<String, Object> t) {
        if (t != null && !t.isEmpty()) {
            getTable().putAll(t);
        }
    }

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object
     * @since 1.7
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getName());
        sb.append("[name=").append(name);
        appendTo(sb, "displayName", displayName);
        appendTo(sb, "shortDescription", shortDescription);
        appendTo(sb, "preferred", preferred);
        appendTo(sb, "hidden", hidden);
        appendTo(sb, "expert", expert);
        if (table != null && !table.isEmpty()) {
            sb.append("; values={");
            boolean first = true;
            for (Map.Entry<String, Object> e : table.entrySet()) {
                if (!first) sb.append("; ");
                first = false;
                sb.append(e.getKey()).append('=').append(e.getValue());
            }
            sb.append('}');
        }
        appendTo(sb);
        return sb.append(']').toString();
    }

    void appendTo(StringBuilder sb) {
    }

    static void appendTo(StringBuilder sb, String name, Object value) {
        if (value != null) {
            sb.append("; ").append(name).append('=').append(value);
        }
    }

    static void appendTo(StringBuilder sb, String name, boolean value) {
        if (value) {
            sb.append("; ").append(name);
        }
    }
}
