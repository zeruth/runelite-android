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

import android.media.AudioTrack;

import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Control;
import javax.sound.sampled.FloatControl;

/**
 * The controls the JDK's direct audio lines expose once open: MASTER_GAIN (dB), MUTE, and PAN
 * for mono or BALANCE for stereo, plus VOLUME (linear). They fold into one left/right pair for
 * {@link AudioTrack#setStereoVolume}; AudioTrack cannot amplify, so gain above 0 dB is capped
 * at unity there while the control still reports the value that was set.
 */
final class TrackControls {

    /** Receives the combined per-channel volume, each in [0, 1]. */
    interface Sink {
        void apply(float left, float right);
    }

    private final Sink sink;
    private final Gain gain = new Gain();
    private final Mute mute = new Mute();
    private final Pan pan;
    private final Volume volume = new Volume();
    private final Control[] controls;

    TrackControls(int channels, Sink sink) {
        this.sink = sink;
        pan = new Pan(channels == 2 ? FloatControl.Type.BALANCE : FloatControl.Type.PAN);
        controls = new Control[]{gain, mute, pan, volume};
    }

    Control[] controls() {
        return controls.clone();
    }

    boolean supports(Control.Type type) {
        for (Control control : controls) {
            if (control.getType() == type) {
                return true;
            }
        }
        return false;
    }

    Control get(Control.Type type) {
        for (Control control : controls) {
            if (control.getType() == type) {
                return control;
            }
        }
        throw new IllegalArgumentException("Unsupported control type: " + type);
    }

    /** Pushes the current values to the sink. */
    synchronized void apply() {
        float linear = mute.getValue() ? 0f
            : (float) Math.min(1.0, Math.pow(10.0, gain.getValue() / 20.0)) * volume.getValue();
        float position = pan.getValue();
        float left = position > 0f ? linear * (1f - position) : linear;
        float right = position < 0f ? linear * (1f + position) : linear;
        sink.apply(clamp(left), clamp(right));
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Applies {@code left}/{@code right} to a track, ignoring a track that was already released. */
    @SuppressWarnings("deprecation") // setStereoVolume is the only per-channel volume AudioTrack has.
    static void applyTo(AudioTrack track, float left, float right) {
        try {
            track.setStereoVolume(left, right);
        } catch (IllegalStateException ignored) {
            // released concurrently by close(); the next open applies the values again
        }
    }

    // Ranges and labels as the JDK's DirectAudioDevice reports them.
    private final class Gain extends FloatControl {
        Gain() {
            super(FloatControl.Type.MASTER_GAIN, -80f, 6.0206f, Math.abs(-80f / 128.0f), -1, 0f,
                "dB", "Minimum", "", "Maximum");
        }

        @Override
        public void setValue(float value) {
            super.setValue(value);
            apply();
        }
    }

    private final class Mute extends BooleanControl {
        Mute() {
            super(BooleanControl.Type.MUTE, false, "True", "False");
        }

        @Override
        public void setValue(boolean value) {
            super.setValue(value);
            apply();
        }
    }

    private final class Pan extends FloatControl {
        Pan(FloatControl.Type type) {
            super(type, -1f, 1f, 1f / 128f, -1, 0f, "", "Left", "Center", "Right");
        }

        @Override
        public void setValue(float value) {
            super.setValue(value);
            apply();
        }
    }

    private final class Volume extends FloatControl {
        Volume() {
            super(FloatControl.Type.VOLUME, 0f, 1f, 1f / 128f, -1, 1f, "", "Minimum", "", "Maximum");
        }

        @Override
        public void setValue(float value) {
            super.setValue(value);
            apply();
        }
    }
}
