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

public class AudioFormat {
    public static class Encoding {
        public static final Encoding PCM_SIGNED = new Encoding("PCM_SIGNED");
        public static final Encoding PCM_UNSIGNED = new Encoding("PCM_UNSIGNED");
        public static final Encoding PCM_FLOAT = new Encoding("PCM_FLOAT");

        private final String name;

        public Encoding(String name) {
            this.name = name;
        }

        public final String toString() {
            return name;
        }

        @Override
        public final boolean equals(Object obj) {
            return obj instanceof Encoding && name.equals(((Encoding) obj).name);
        }

        @Override
        public final int hashCode() {
            return name.hashCode();
        }
    }

    private final Encoding encoding;
    private final float sampleRate;
    private final int sampleSizeInBits;
    private final int channels;
    private final int frameSize;
    private final float frameRate;
    private final boolean bigEndian;

    public AudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {
        this(signed ? Encoding.PCM_SIGNED : Encoding.PCM_UNSIGNED, sampleRate, sampleSizeInBits, channels,
            channels == AudioSystem.NOT_SPECIFIED || sampleSizeInBits == AudioSystem.NOT_SPECIFIED
                ? AudioSystem.NOT_SPECIFIED
                : ((sampleSizeInBits + 7) / 8) * channels,
            sampleRate, bigEndian);
    }

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels,
                        int frameSize, float frameRate, boolean bigEndian) {
        this.encoding = encoding;
        this.sampleRate = sampleRate;
        this.sampleSizeInBits = sampleSizeInBits;
        this.channels = channels;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.bigEndian = bigEndian;
    }

    public Encoding getEncoding()    { return encoding; }
    public float getSampleRate()     { return sampleRate; }
    public int getSampleSizeInBits() { return sampleSizeInBits; }
    public int getChannels()         { return channels; }
    public int getFrameSize()        { return frameSize; }
    public float getFrameRate()      { return frameRate; }
    public boolean isBigEndian()     { return bigEndian; }

    public boolean matches(AudioFormat other) {
        return encoding.equals(other.encoding)
            && (sampleRate == AudioSystem.NOT_SPECIFIED || other.sampleRate == AudioSystem.NOT_SPECIFIED || sampleRate == other.sampleRate)
            && (sampleSizeInBits == AudioSystem.NOT_SPECIFIED || other.sampleSizeInBits == AudioSystem.NOT_SPECIFIED || sampleSizeInBits == other.sampleSizeInBits)
            && (channels == AudioSystem.NOT_SPECIFIED || other.channels == AudioSystem.NOT_SPECIFIED || channels == other.channels)
            && (sampleSizeInBits <= 8 || bigEndian == other.bigEndian);
    }

    @Override
    public String toString() {
        return encoding + " " + sampleRate + " Hz, " + sampleSizeInBits + " bit, " + channels + " channels, " + (bigEndian ? "big-endian" : "little-endian");
    }
}
