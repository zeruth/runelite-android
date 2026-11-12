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
package sun.swing;

import java.beans.PropertyChangeListener;
import javax.swing.Action;

/**
 * The JDK's base for the shared, stateless actions of the Basic delegates.
 * One instance serves every component, so it keeps no per-component state:
 * the name is the only value, {@code putValue} and {@code setEnabled} do
 * nothing (as in the JDK), and enablement is asked per sender through
 * {@link #isEnabled(Object)}, which {@code SwingUtilities.notifyAction}
 * calls with the source component.
 */
public abstract class UIAction implements Action {
    private String name;

    public UIAction(String name) {
        this.name = name;
    }

    public final String getName() {
        return name;
    }

    public Object getValue(String key) {
        if (key == NAME) {
            return name;
        }
        return null;
    }

    // UIAction is shared, so it cannot hold per-component values.
    public void putValue(String key, Object value) {
    }

    // UIAction is shared, so it cannot hold per-component enablement.
    public void setEnabled(boolean b) {
    }

    /**
     * Cover method for {@code isEnabled(null)}.
     */
    public final boolean isEnabled() {
        return isEnabled(null);
    }

    /**
     * Subclasses that need to conditionalize the enabled state should
     * override this. Be aware that {@code sender} may be null.
     *
     * @param sender Widget enabled state is being asked for, may be null.
     */
    public boolean isEnabled(Object sender) {
        return true;
    }

    // No bound properties to listen to.
    public void addPropertyChangeListener(PropertyChangeListener listener) {
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
    }
}
