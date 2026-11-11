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
package javax.sound.sampled;

public class FloatControl extends Control {
    public static class Type extends Control.Type {
        public static final Type MASTER_GAIN = new Type("Master Gain");
        public static final Type VOLUME = new Type("Volume");
        public static final Type PAN = new Type("Pan");
        public static final Type BALANCE = new Type("Balance");
        public static final Type SAMPLE_RATE = new Type("Sample Rate");

        protected Type(String name) { super(name); }
    }

    private float value;
    private final float min, max;
    private final String units;
    private final float precision;
    private final int updatePeriod;
    private String minLabel = "", midLabel = "", maxLabel = "";

    public FloatControl(Type type, float min, float max, float precision, int updatePeriod, float initialValue, String units) {
        super(type);
        this.min = min; this.max = max;
        this.value = initialValue;
        this.units = units;
        this.precision = precision;
        this.updatePeriod = updatePeriod;
    }

    public FloatControl(Type type, float min, float max, float precision, int updatePeriod, float initialValue,
                        String units, String minLabel, String midLabel, String maxLabel) {
        this(type, min, max, precision, updatePeriod, initialValue, units);
        this.minLabel = minLabel; this.midLabel = midLabel; this.maxLabel = maxLabel;
    }

    public void setValue(float newValue) {
        if (newValue < min || newValue > max) throw new IllegalArgumentException("value out of range");
        this.value = newValue;
    }

    public float getValue() { return value; }
    public float getMinimum() { return min; }
    public float getMaximum() { return max; }
    public String getUnits() { return units; }
    public float getPrecision() { return precision; }
    public int getUpdatePeriod() { return updatePeriod; }
    public String getMinLabel() { return minLabel; }
    public String getMidLabel() { return midLabel; }
    public String getMaxLabel() { return maxLabel; }
    public void shift(float from, float to, int microseconds) {
        if (from < min || from > max) throw new IllegalArgumentException("value out of range");
        setValue(to);
    }
}
