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
package java.beans.beancontext;

import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

/**
 * JavaBeans wishing to be nested within, and obtain a reference to their
 * execution environment, or context, as defined by the BeanContext
 * sub-interface shall implement this interface.
 *
 * @since 1.2
 */
public interface BeanContextChild {
    /**
     * Objects that implement this interface shall fire a
     * java.beans.PropertyChangeEvent, with parameters: propertyName
     * "beanContext", oldValue (the previous nesting BeanContext instance,
     * or null), newValue (the current nesting BeanContext instance, or null).
     *
     * @param bc The BeanContext with which to associate this BeanContextChild.
     * @throws PropertyVetoException if the addition of the specified
     *         BeanContext is refused.
     */
    void setBeanContext(BeanContext bc) throws PropertyVetoException;

    /**
     * Gets the BeanContext associated with this BeanContextChild.
     * @return the BeanContext associated with this BeanContextChild.
     */
    BeanContext getBeanContext();

    /**
     * Adds a PropertyChangeListener to this BeanContextChild in order to
     * receive a PropertyChangeEvent whenever the specified property has changed.
     * @param name the name of the property to listen on
     * @param pcl the PropertyChangeListener to add
     */
    void addPropertyChangeListener(String name, PropertyChangeListener pcl);

    /**
     * Removes a PropertyChangeListener from this BeanContextChild.
     * @param name the name of the property that was listened on
     * @param pcl the PropertyChangeListener to remove
     */
    void removePropertyChangeListener(String name, PropertyChangeListener pcl);

    /**
     * Adds a VetoableChangeListener to this BeanContextChild to receive
     * events whenever the specified property changes.
     * @param name the name of the property to listen on
     * @param vcl the VetoableChangeListener to add
     */
    void addVetoableChangeListener(String name, VetoableChangeListener vcl);

    /**
     * Removes a VetoableChangeListener from this BeanContextChild.
     * @param name the name of the property that was listened on.
     * @param vcl the VetoableChangeListener to remove.
     */
    void removeVetoableChangeListener(String name, VetoableChangeListener vcl);
}
