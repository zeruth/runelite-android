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
package net.runelite.awt.impl;

import java.util.Locale;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;

/**
 * The accessible child of a list, table or tree: one cell, named by its value. The JDK
 * answers these through the cell renderer's own context; the default renderers are
 * {@code JLabel}s, so a cell reports role LABEL and the value's text as its name, which is
 * what a desktop screen reader sees for an unstyled list, table or tree.
 */
public final class AccessibleCell extends AccessibleContext implements Accessible {
    private final int index;
    private final boolean selected;

    public AccessibleCell(Accessible parent, int index, Object value, boolean selected) {
        this.index = index;
        this.selected = selected;
        setAccessibleParent(parent);
        accessibleName = value == null ? null : value.toString();
    }

    public AccessibleContext getAccessibleContext() {
        return this;
    }

    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.LABEL;
    }

    public AccessibleStateSet getAccessibleStateSet() {
        AccessibleStateSet states = new AccessibleStateSet();
        states.add(AccessibleState.ENABLED);
        states.add(AccessibleState.VISIBLE);
        states.add(AccessibleState.SELECTABLE);
        if (selected) states.add(AccessibleState.SELECTED);
        return states;
    }

    public int getAccessibleIndexInParent() {
        return index;
    }

    public int getAccessibleChildrenCount() {
        return 0;
    }

    public Accessible getAccessibleChild(int i) {
        return null;
    }

    public Locale getLocale() {
        return Locale.getDefault();
    }
}
