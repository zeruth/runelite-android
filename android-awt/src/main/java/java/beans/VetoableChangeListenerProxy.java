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

import java.util.EventListenerProxy;

/**
 * A class which extends the {@code EventListenerProxy} specifically for
 * associating a {@code VetoableChangeListener} with a "constrained" property.
 */
public class VetoableChangeListenerProxy extends EventListenerProxy<VetoableChangeListener>
        implements VetoableChangeListener {

    private final String propertyName;

    /**
     * Constructor which binds the {@code VetoableChangeListener} to a specific property.
     *
     * @param propertyName the name of the property to listen on
     * @param listener the listener object
     */
    public VetoableChangeListenerProxy(String propertyName, VetoableChangeListener listener) {
        super(listener);
        this.propertyName = propertyName;
    }

    /**
     * Forwards the property change event to the listener delegate.
     *
     * @param event the property change event
     * @exception PropertyVetoException if the recipient wishes the property
     *            change to be rolled back
     */
    public void vetoableChange(PropertyChangeEvent event) throws PropertyVetoException {
        getListener().vetoableChange(event);
    }

    /**
     * Returns the name of the named property associated with the listener.
     *
     * @return the name of the named property associated with the listener
     */
    public String getPropertyName() {
        return this.propertyName;
    }
}
