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
import java.awt.Rectangle;

/**
 * Paint request. Like the JDK, it is not meant for listeners; the Android host repaints
 * through {@link Component#repaint} generations, so nothing here posts one, but code that
 * constructs or inspects it keeps working.
 */
public class PaintEvent extends ComponentEvent {
    private static final long serialVersionUID = 1267492026433337593L;

    public static final int PAINT_FIRST = 800;
    public static final int PAINT_LAST = 801;
    public static final int PAINT = PAINT_FIRST;
    public static final int UPDATE = PAINT_FIRST + 1;

    Rectangle updateRect;

    public PaintEvent(Component source, int id, Rectangle updateRect) {
        super(source, id);
        this.updateRect = updateRect;
    }

    public Rectangle getUpdateRect() {
        return updateRect;
    }

    public void setUpdateRect(Rectangle updateRect) {
        this.updateRect = updateRect;
    }

    @Override
    public String paramString() {
        String typeStr;
        switch (id) {
            case PAINT:
                typeStr = "PAINT";
                break;
            case UPDATE:
                typeStr = "UPDATE";
                break;
            default:
                typeStr = "unknown type";
        }
        return typeStr + ",updateRect=" + (updateRect != null ? updateRect.toString() : "null");
    }
}
