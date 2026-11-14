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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The arrangement of data in a sound stream, with the JDK's public and protected surface:
 * protected fields subclasses read directly, the property map, and the asymmetric
 * {@link #matches} where only the argument's {@code NOT_SPECIFIED} values are wildcards.
 */
public class AudioFormat {

    protected Encoding encoding;
    protected float sampleRate;
    protected int sampleSizeInBits;
    protected int channels;
    protected int frameSize;
    protected float frameRate;
    protected boolean bigEndian;

    private HashMap<String, Object> properties;

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits,
                       int channels, int frameSize, float frameRate, boolean bigEndian) {
        this.encoding = encoding;
        this.sampleRate = sampleRate;
        this.sampleSizeInBits = sampleSizeInBits;
        this.channels = channels;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.bigEndian = bigEndian;
        this.properties = null;
    }

    public AudioFormat(Encoding encoding, float sampleRate, int sampleSizeInBits, int channels,
                       int frameSize, float frameRate, boolean bigEndian, Map<String, Object> properties) {
        this(encoding, sampleRate, sampleSizeInBits, channels, frameSize, frameRate, bigEndian);
        this.properties = new HashMap<String, Object>(properties);
    }

    public AudioFormat(float sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {
        this((signed ? Encoding.PCM_SIGNED : Encoding.PCM_UNSIGNED),
             sampleRate,
             sampleSizeInBits,
             channels,
             (channels == AudioSystem.NOT_SPECIFIED || sampleSizeInBits == AudioSystem.NOT_SPECIFIED)
                 ? AudioSystem.NOT_SPECIFIED
                 : ((sampleSizeInBits + 7) / 8) * channels,
             sampleRate,
             bigEndian);
    }

    public Encoding getEncoding()    { return encoding; }
    public float getSampleRate()     { return sampleRate; }
    public int getSampleSizeInBits() { return sampleSizeInBits; }
    public int getChannels()         { return channels; }
    public int getFrameSize()        { return frameSize; }
    public float getFrameRate()      { return frameRate; }
    public boolean isBigEndian()     { return bigEndian; }

    /** An unmodifiable copy of the properties; empty when the format was built without any. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> properties() {
        Map<String, Object> ret;
        if (properties == null) {
            ret = new HashMap<String, Object>(0);
        } else {
            ret = (Map<String, Object>) properties.clone();
        }
        return Collections.unmodifiableMap(ret);
    }

    public Object getProperty(String key) {
        if (properties == null) {
            return null;
        }
        return properties.get(key);
    }

    /**
     * True when this format satisfies {@code format}: same encoding, every field equal or
     * {@code NOT_SPECIFIED} in {@code format}, and the same byte order when samples are wider
     * than a byte. Wildcards in this format do not match concrete values in the argument.
     */
    public boolean matches(AudioFormat format) {
        return format.getEncoding().equals(getEncoding())
            && (format.getChannels() == AudioSystem.NOT_SPECIFIED || format.getChannels() == getChannels())
            && (format.getSampleRate() == (float) AudioSystem.NOT_SPECIFIED || format.getSampleRate() == getSampleRate())
            && (format.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED || format.getSampleSizeInBits() == getSampleSizeInBits())
            && (format.getFrameRate() == (float) AudioSystem.NOT_SPECIFIED || format.getFrameRate() == getFrameRate())
            && (format.getFrameSize() == AudioSystem.NOT_SPECIFIED || format.getFrameSize() == getFrameSize())
            && (getSampleSizeInBits() <= 8 || format.isBigEndian() == isBigEndian());
    }

    @Override
    public String toString() {
        String sEndian = "";
        if ((getEncoding().equals(Encoding.PCM_SIGNED) || getEncoding().equals(Encoding.PCM_UNSIGNED))
                && (getSampleSizeInBits() > 8 || getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED)) {
            sEndian = bigEndian ? "big-endian" : "little-endian";
        }

        String sSampleRate = getSampleRate() == (float) AudioSystem.NOT_SPECIFIED
            ? "unknown sample rate, " : getSampleRate() + " Hz, ";

        String sSampleSizeInBits = getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED
            ? "unknown bits per sample, " : getSampleSizeInBits() + " bit, ";

        String sChannels;
        if (getChannels() == 1) {
            sChannels = "mono, ";
        } else if (getChannels() == 2) {
            sChannels = "stereo, ";
        } else if (getChannels() == AudioSystem.NOT_SPECIFIED) {
            sChannels = " unknown number of channels, ";
        } else {
            sChannels = getChannels() + " channels, ";
        }

        String sFrameSize = getFrameSize() == AudioSystem.NOT_SPECIFIED
            ? "unknown frame size, " : getFrameSize() + " bytes/frame, ";

        String sFrameRate = "";
        if (Math.abs(getSampleRate() - getFrameRate()) > 0.00001) {
            sFrameRate = getFrameRate() == (float) AudioSystem.NOT_SPECIFIED
                ? "unknown frame rate, " : getFrameRate() + " frames/second, ";
        }

        return getEncoding() + " " + sSampleRate + sSampleSizeInBits + sChannels + sFrameSize + sFrameRate + sEndian;
    }

    /** A named sample encoding; two encodings are equal when their names are. */
    public static class Encoding {

        public static final Encoding PCM_SIGNED = new Encoding("PCM_SIGNED");
        public static final Encoding PCM_UNSIGNED = new Encoding("PCM_UNSIGNED");
        public static final Encoding PCM_FLOAT = new Encoding("PCM_FLOAT");
        public static final Encoding ULAW = new Encoding("ULAW");
        public static final Encoding ALAW = new Encoding("ALAW");

        private final String name;

        public Encoding(String name) {
            this.name = name;
        }

        @Override
        public final boolean equals(Object obj) {
            if (toString() == null) {
                return obj != null && obj.toString() == null;
            }
            if (obj instanceof Encoding) {
                return toString().equals(obj.toString());
            }
            return false;
        }

        @Override
        public final int hashCode() {
            if (toString() == null) {
                return 0;
            }
            return toString().hashCode();
        }

        @Override
        public final String toString() {
            return name;
        }
    }
}
