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
package java.awt.event;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;

public class HierarchyEvent extends AWTEvent {
    private static final long serialVersionUID = -5337576970038043502L;

    public static final int HIERARCHY_FIRST = 1400;
    public static final int HIERARCHY_CHANGED = 1400;
    public static final int ANCESTOR_MOVED = 1401;
    public static final int ANCESTOR_RESIZED = 1402;
    public static final int HIERARCHY_LAST = 1402;

    public static final int PARENT_CHANGED = 0x1;
    public static final int DISPLAYABILITY_CHANGED = 0x2;
    public static final int SHOWING_CHANGED = 0x4;

    private final Component changed;
    private final Container changedParent;
    private final long changeFlags;

    public HierarchyEvent(Component source, int id, Component changed, Container changedParent) {
        this(source, id, changed, changedParent, 0);
    }

    public HierarchyEvent(Component source, int id, Component changed, Container changedParent, long changeFlags) {
        super(source, id);
        this.changed = changed;
        this.changedParent = changedParent;
        this.changeFlags = changeFlags;
    }

    public Component getComponent() {
        return (source instanceof Component) ? (Component) source : null;
    }

    public Component getChanged() {
        return changed;
    }

    public Container getChangedParent() {
        return changedParent;
    }

    public long getChangeFlags() {
        return changeFlags;
    }
}
