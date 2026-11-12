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

public class AdjustmentEvent extends AWTEvent {
    private static final long serialVersionUID = 5700290645205279921L;

    public static final int ADJUSTMENT_FIRST = 601;
    public static final int ADJUSTMENT_LAST = 601;
    public static final int ADJUSTMENT_VALUE_CHANGED = 601;

    public static final int UNIT_INCREMENT = 1;
    public static final int UNIT_DECREMENT = 2;
    // The JDK's values: plugins compiled against it inline these, so 3 must stay BLOCK_DECREMENT.
    public static final int BLOCK_DECREMENT = 3;
    public static final int BLOCK_INCREMENT = 4;
    public static final int TRACK = 5;

    private final int adjustmentType;
    private final int value;
    private final boolean isAdjusting;

    public AdjustmentEvent(java.awt.Adjustable source, int id, int type, int value) {
        this(source, id, type, value, false);
    }

    public AdjustmentEvent(java.awt.Adjustable source, int id, int type, int value, boolean isAdjusting) {
        super(source, id);
        this.adjustmentType = type;
        this.value = value;
        this.isAdjusting = isAdjusting;
    }

    public int getAdjustmentType() { return adjustmentType; }
    public int getValue() { return value; }
    public boolean getValueIsAdjusting() { return isAdjusting; }
    public java.awt.Adjustable getAdjustable() { return (java.awt.Adjustable) source; }
}
