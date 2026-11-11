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

public abstract class AccessibleContext {
    public static final String ACCESSIBLE_NAME_PROPERTY = "AccessibleName";
    public static final String ACCESSIBLE_DESCRIPTION_PROPERTY = "AccessibleDescription";
    protected String accessibleName, accessibleDescription;
    protected Accessible accessibleParent;
    private final java.beans.PropertyChangeSupport changes = new java.beans.PropertyChangeSupport(this);
    public AccessibleContext() {}
    public String getAccessibleName() { return accessibleName; }
    public void setAccessibleName(String s) { String old = accessibleName; accessibleName = s; firePropertyChange(ACCESSIBLE_NAME_PROPERTY, old, s); }
    public String getAccessibleDescription() { return accessibleDescription; }
    public void setAccessibleDescription(String s) { String old = accessibleDescription; accessibleDescription = s; firePropertyChange(ACCESSIBLE_DESCRIPTION_PROPERTY, old, s); }
    public Accessible getAccessibleParent() { return accessibleParent; }
    public void setAccessibleParent(Accessible parent) { accessibleParent = parent; }
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) { changes.addPropertyChangeListener(listener); }
    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) { changes.removePropertyChangeListener(listener); }
    public void firePropertyChange(String property, Object oldValue, Object newValue) { changes.firePropertyChange(property, oldValue, newValue); }
}
