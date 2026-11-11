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
package java.awt;

import java.util.EventObject;

public abstract class AWTEvent extends EventObject {
    private static final long serialVersionUID = -1825314779160409405L;

    public static final long COMPONENT_EVENT_MASK = 0x01;
    public static final long CONTAINER_EVENT_MASK = 0x02;
    public static final long FOCUS_EVENT_MASK     = 0x04;
    public static final long KEY_EVENT_MASK       = 0x08;
    public static final long MOUSE_EVENT_MASK     = 0x10;
    public static final long MOUSE_MOTION_EVENT_MASK = 0x20;
    public static final long WINDOW_EVENT_MASK    = 0x40;
    public static final long ACTION_EVENT_MASK    = 0x80;
    public static final long ADJUSTMENT_EVENT_MASK = 0x100;
    public static final long ITEM_EVENT_MASK      = 0x200;
    public static final long TEXT_EVENT_MASK      = 0x400;
    public static final long INPUT_METHOD_EVENT_MASK = 0x800;
    public static final long PAINT_EVENT_MASK     = 0x2000;
    public static final long INVOCATION_EVENT_MASK = 0x4000;
    public static final long HIERARCHY_EVENT_MASK = 0x8000;
    public static final long HIERARCHY_BOUNDS_EVENT_MASK = 0x10000;
    public static final long MOUSE_WHEEL_EVENT_MASK = 0x20000;
    public static final long WINDOW_STATE_EVENT_MASK = 0x40000;
    public static final long WINDOW_FOCUS_EVENT_MASK = 0x80000;

    public static final int RESERVED_ID_MAX = 1999;

    protected int id;
    protected boolean consumed;

    public AWTEvent(Object source, int id) {
        super(source);
        this.id = id;
    }

    public int getID() {
        return id;
    }

    public String paramString() {
        return "";
    }

    @Override
    public String toString() {
        String name = (source != null) ? source.getClass().getName() : "null";
        return getClass().getName() + "[" + paramString() + "] on " + name;
    }

    protected void consume() {
        consumed = true;
    }

    protected boolean isConsumed() {
        return consumed;
    }
}
