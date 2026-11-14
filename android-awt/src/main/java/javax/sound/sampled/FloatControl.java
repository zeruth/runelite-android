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

public abstract class FloatControl extends Control {

    private final float minimum;
    private final float maximum;
    private final float precision;
    private final int updatePeriod;
    private final String units;
    private final String minLabel;
    private final String maxLabel;
    private final String midLabel;
    private float value;

    protected FloatControl(Type type, float minimum, float maximum, float precision, int updatePeriod,
                           float initialValue, String units, String minLabel, String midLabel, String maxLabel) {
        super(type);
        if (minimum > maximum) {
            throw new IllegalArgumentException("Minimum value " + minimum
                + " exceeds maximum value " + maximum + ".");
        }
        if (initialValue < minimum) {
            throw new IllegalArgumentException("Initial value " + initialValue
                + " smaller than allowable minimum value " + minimum + ".");
        }
        if (initialValue > maximum) {
            throw new IllegalArgumentException("Initial value " + initialValue
                + " exceeds allowable maximum value " + maximum + ".");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.precision = precision;
        this.updatePeriod = updatePeriod;
        this.value = initialValue;
        this.units = units;
        this.minLabel = minLabel;
        this.midLabel = midLabel;
        this.maxLabel = maxLabel;
    }

    protected FloatControl(Type type, float minimum, float maximum, float precision, int updatePeriod,
                           float initialValue, String units) {
        this(type, minimum, maximum, precision, updatePeriod, initialValue, units, "", "", "");
    }

    public void setValue(float newValue) {
        if (newValue > maximum) {
            throw new IllegalArgumentException("Requested value " + newValue
                + " exceeds allowable maximum value " + maximum + ".");
        }
        if (newValue < minimum) {
            throw new IllegalArgumentException("Requested value " + newValue
                + " smaller than allowable minimum value " + minimum + ".");
        }
        value = newValue;
    }

    public float getValue() { return value; }
    public float getMaximum() { return maximum; }
    public float getMinimum() { return minimum; }
    public String getUnits() { return units; }
    public String getMinLabel() { return minLabel; }
    public String getMidLabel() { return midLabel; }
    public String getMaxLabel() { return maxLabel; }
    public float getPrecision() { return precision; }
    public int getUpdatePeriod() { return updatePeriod; }

    /**
     * Changes the value from {@code from} to {@code to}. Like the JDK's default, the ramp is not
     * interpolated: the target value is applied at once (the JDK documents the same for
     * controls that do not support shifting).
     */
    public void shift(float from, float to, int microseconds) {
        if (from < minimum) {
            throw new IllegalArgumentException("Requested value " + from
                + " smaller than allowable minimum value " + minimum + ".");
        }
        if (from > maximum) {
            throw new IllegalArgumentException("Requested value " + from
                + " exceeds allowable maximum value " + maximum + ".");
        }
        setValue(to);
    }

    @Override
    public String toString() {
        return getType() + " with current value: " + getValue() + " " + units
            + " (range: " + minimum + " - " + maximum + ")";
    }

    public static class Type extends Control.Type {
        public static final Type MASTER_GAIN = new Type("Master Gain");
        public static final Type AUX_SEND = new Type("AUX Send");
        public static final Type AUX_RETURN = new Type("AUX Return");
        public static final Type REVERB_SEND = new Type("Reverb Send");
        public static final Type REVERB_RETURN = new Type("Reverb Return");
        public static final Type VOLUME = new Type("Volume");
        public static final Type PAN = new Type("Pan");
        public static final Type BALANCE = new Type("Balance");
        public static final Type SAMPLE_RATE = new Type("Sample Rate");

        protected Type(String name) { super(name); }
    }
}
