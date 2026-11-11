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

public class MouseWheelEvent extends MouseEvent {
    private static final long serialVersionUID = 6459879390515399677L;

    public static final int WHEEL_UNIT_SCROLL = 0;
    public static final int WHEEL_BLOCK_SCROLL = 1;

    private final int scrollType;
    private final int scrollAmount;
    private final int wheelRotation;
    private final double preciseWheelRotation;

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation) {
        this(source, id, when, modifiers, x, y, clickCount, popupTrigger,
            scrollType, scrollAmount, wheelRotation, (double) wheelRotation);
    }

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation,
                            double preciseWheelRotation) {
        super(source, id, when, modifiers, x, y, clickCount, popupTrigger);
        this.scrollType = scrollType;
        this.scrollAmount = scrollAmount;
        this.wheelRotation = wheelRotation;
        this.preciseWheelRotation = preciseWheelRotation;
    }

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int xAbs, int yAbs, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation) {
        this(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger,
            scrollType, scrollAmount, wheelRotation, (double) wheelRotation);
    }

    public MouseWheelEvent(Component source, int id, long when, int modifiers,
                            int x, int y, int xAbs, int yAbs, int clickCount, boolean popupTrigger,
                            int scrollType, int scrollAmount, int wheelRotation,
                            double preciseWheelRotation) {
        super(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, NOBUTTON);
        this.scrollType = scrollType;
        this.scrollAmount = scrollAmount;
        this.wheelRotation = wheelRotation;
        this.preciseWheelRotation = preciseWheelRotation;
    }

    public int getScrollType()   { return scrollType; }
    public int getScrollAmount() { return scrollAmount; }
    public int getWheelRotation() { return wheelRotation; }
    public double getPreciseWheelRotation() { return preciseWheelRotation; }
    public int getUnitsToScroll() { return scrollAmount * wheelRotation; }
}
