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
package javax.accessibility;

import java.awt.IllegalComponentStateException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Locale;

/**
 * The minimum information all accessible objects return: name, description, role, state
 * set, parent and children, plus the optional sub-interfaces (component, text, value, ...),
 * which default to null.
 */
public abstract class AccessibleContext {

    public static final String ACCESSIBLE_NAME_PROPERTY = "AccessibleName";
    public static final String ACCESSIBLE_DESCRIPTION_PROPERTY = "AccessibleDescription";
    public static final String ACCESSIBLE_STATE_PROPERTY = "AccessibleState";
    public static final String ACCESSIBLE_VALUE_PROPERTY = "AccessibleValue";
    public static final String ACCESSIBLE_SELECTION_PROPERTY = "AccessibleSelection";
    public static final String ACCESSIBLE_CARET_PROPERTY = "AccessibleCaret";
    public static final String ACCESSIBLE_VISIBLE_DATA_PROPERTY = "AccessibleVisibleData";
    public static final String ACCESSIBLE_CHILD_PROPERTY = "AccessibleChild";
    public static final String ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY = "AccessibleActiveDescendant";
    public static final String ACCESSIBLE_TABLE_CAPTION_CHANGED = "accessibleTableCaptionChanged";
    public static final String ACCESSIBLE_TABLE_SUMMARY_CHANGED = "accessibleTableSummaryChanged";
    public static final String ACCESSIBLE_TABLE_MODEL_CHANGED = "accessibleTableModelChanged";
    public static final String ACCESSIBLE_TABLE_ROW_HEADER_CHANGED = "accessibleTableRowHeaderChanged";
    public static final String ACCESSIBLE_TABLE_ROW_DESCRIPTION_CHANGED = "accessibleTableRowDescriptionChanged";
    public static final String ACCESSIBLE_TABLE_COLUMN_HEADER_CHANGED = "accessibleTableColumnHeaderChanged";
    public static final String ACCESSIBLE_TABLE_COLUMN_DESCRIPTION_CHANGED = "accessibleTableColumnDescriptionChanged";
    public static final String ACCESSIBLE_ACTION_PROPERTY = "accessibleActionProperty";
    public static final String ACCESSIBLE_HYPERTEXT_OFFSET = "AccessibleHypertextOffset";
    public static final String ACCESSIBLE_TEXT_PROPERTY = "AccessibleText";
    public static final String ACCESSIBLE_INVALIDATE_CHILDREN = "accessibleInvalidateChildren";
    public static final String ACCESSIBLE_TEXT_ATTRIBUTES_CHANGED = "accessibleTextAttributesChanged";
    public static final String ACCESSIBLE_COMPONENT_BOUNDS_CHANGED = "accessibleComponentBoundsChanged";

    /** The accessible parent of this object. */
    protected Accessible accessibleParent = null;

    /** A localized String containing the name of the object. */
    protected String accessibleName = null;

    /** A localized String containing the description of the object. */
    protected String accessibleDescription = null;

    private PropertyChangeSupport accessibleChangeSupport = null;

    private AccessibleRelationSet relationSet = new AccessibleRelationSet();

    public AccessibleContext() {
    }

    public String getAccessibleName() {
        return accessibleName;
    }

    public void setAccessibleName(String s) {
        String oldName = accessibleName;
        accessibleName = s;
        firePropertyChange(ACCESSIBLE_NAME_PROPERTY, oldName, accessibleName);
    }

    public String getAccessibleDescription() {
        return accessibleDescription;
    }

    public void setAccessibleDescription(String s) {
        String oldDescription = accessibleDescription;
        accessibleDescription = s;
        firePropertyChange(ACCESSIBLE_DESCRIPTION_PROPERTY, oldDescription, accessibleDescription);
    }

    public abstract AccessibleRole getAccessibleRole();

    public abstract AccessibleStateSet getAccessibleStateSet();

    public Accessible getAccessibleParent() {
        return accessibleParent;
    }

    public void setAccessibleParent(Accessible a) {
        accessibleParent = a;
    }

    /** The 0-based index of this object in its accessible parent, or -1 without a parent. */
    public abstract int getAccessibleIndexInParent();

    public abstract int getAccessibleChildrenCount();

    public abstract Accessible getAccessibleChild(int i);

    public abstract Locale getLocale() throws IllegalComponentStateException;

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        if (accessibleChangeSupport == null) {
            accessibleChangeSupport = new PropertyChangeSupport(this);
        }
        accessibleChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        if (accessibleChangeSupport != null) {
            accessibleChangeSupport.removePropertyChangeListener(listener);
        }
    }

    public AccessibleAction getAccessibleAction() {
        return null;
    }

    public AccessibleComponent getAccessibleComponent() {
        return null;
    }

    public AccessibleSelection getAccessibleSelection() {
        return null;
    }

    public AccessibleText getAccessibleText() {
        return null;
    }

    public AccessibleEditableText getAccessibleEditableText() {
        return null;
    }

    public AccessibleValue getAccessibleValue() {
        return null;
    }

    public AccessibleIcon[] getAccessibleIcon() {
        return null;
    }

    public AccessibleRelationSet getAccessibleRelationSet() {
        return relationSet;
    }

    public AccessibleTable getAccessibleTable() {
        return null;
    }

    /**
     * Reports a bound property change to registered listeners. A {@link PropertyChangeEvent}
     * passed as the new value is forwarded as-is, which is how text and table changes carry
     * their own source and values.
     */
    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (accessibleChangeSupport != null) {
            if (newValue instanceof PropertyChangeEvent) {
                PropertyChangeEvent pce = (PropertyChangeEvent) newValue;
                accessibleChangeSupport.firePropertyChange(pce);
            } else {
                accessibleChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
            }
        }
    }
}
