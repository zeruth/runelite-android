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

import java.awt.Component;

public class FocusEvent extends ComponentEvent {
    private static final long serialVersionUID = 523220093017728481L;

    public static final int FOCUS_FIRST = 1004;
    public static final int FOCUS_GAINED = 1004;
    public static final int FOCUS_LOST = 1005;
    public static final int FOCUS_LAST = 1005;

    public enum Cause { UNKNOWN, MOUSE_EVENT, TRAVERSAL, TRAVERSAL_UP, TRAVERSAL_DOWN, TRAVERSAL_FORWARD, TRAVERSAL_BACKWARD, ROLLBACK, UNEXPECTED, ACTIVATION, CLEAR_GLOBAL_FOCUS_OWNER }
    private Cause cause = Cause.UNKNOWN;
    /** JDK 9+ (9). @throws IllegalArgumentException if cause is null */
    public FocusEvent(Component source, int id, boolean temporary, Component opposite, Cause cause) {
        this(source, id, temporary, opposite);
        if (cause == null) throw new IllegalArgumentException("null cause");
        this.cause = cause;
    }
    public Cause getCause() { return cause; }
    private final boolean temporary;
    private final Component opposite;

    public FocusEvent(Component source, int id) {
        this(source, id, false, null);
    }

    public FocusEvent(Component source, int id, boolean temporary) {
        this(source, id, temporary, null);
    }

    public FocusEvent(Component source, int id, boolean temporary, Component opposite) {
        super(source, id);
        this.temporary = temporary;
        this.opposite = opposite;
    }

    public boolean isTemporary() {
        return temporary;
    }

    public Component getOppositeComponent() {
        return opposite;
    }
}
