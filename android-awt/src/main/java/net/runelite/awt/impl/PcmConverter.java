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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Sample-by-sample format conversion behind {@code AudioSystem.getAudioInputStream(format, stream)}
 * and the file writers: sign and byte-order changes of integer PCM at the same width (exact),
 * integer PCM to and from 32-bit float, and G.711 u-law/A-law to and from 16-bit PCM. Sample
 * rate and channel count are kept; changing either is not offered, so
 * {@link #isConversionSupported} answers false for it.
 */
public final class PcmConverter {
    private static final AudioFormat.Encoding[] ENCODINGS = {
        AudioFormat.Encoding.PCM_SIGNED, AudioFormat.Encoding.PCM_UNSIGNED, AudioFormat.Encoding.PCM_FLOAT,
        AudioFormat.Encoding.ULAW, AudioFormat.Encoding.ALAW,
    };

    private PcmConverter() {
    }

    private static boolean isInteger(AudioFormat.Encoding encoding) {
        return AudioFormat.Encoding.PCM_SIGNED.equals(encoding) || AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding);
    }

    private static boolean isLaw(AudioFormat.Encoding encoding) {
        return AudioFormat.Encoding.ULAW.equals(encoding) || AudioFormat.Encoding.ALAW.equals(encoding);
    }

    private static boolean known(AudioFormat.Encoding encoding) {
        for (AudioFormat.Encoding candidate : ENCODINGS) {
            if (candidate.equals(encoding)) return true;
        }
        return false;
    }

    /** True when every sample of {@code format} can be decoded: packed frames of a known encoding. */
    static boolean decodable(AudioFormat format) {
        AudioFormat.Encoding encoding = format.getEncoding();
        int bits = format.getSampleSizeInBits();
        int channels = format.getChannels();
        if (channels <= 0 || format.getSampleRate() <= 0) return false;
        boolean width;
        if (isInteger(encoding)) width = bits == 8 || bits == 16 || bits == 24 || bits == 32;
        else if (AudioFormat.Encoding.PCM_FLOAT.equals(encoding)) width = bits == 32 || bits == 64;
        else if (isLaw(encoding)) width = bits == 8;
        else return false;
        return width && format.getFrameSize() == channels * (bits / 8);
    }

    public static AudioFormat.Encoding[] targetEncodings(AudioFormat.Encoding source) {
        return known(source) ? ENCODINGS.clone() : new AudioFormat.Encoding[0];
    }

    public static AudioFormat.Encoding[] targetEncodings(AudioFormat source) {
        List<AudioFormat.Encoding> result = new ArrayList<AudioFormat.Encoding>();
        for (AudioFormat.Encoding encoding : ENCODINGS) {
            if (targetFormats(encoding, source).length > 0) result.add(encoding);
        }
        return result.toArray(new AudioFormat.Encoding[0]);
    }

    /** The concrete formats {@code source} converts to in {@code target}, same rate and channels. */
    public static AudioFormat[] targetFormats(AudioFormat.Encoding target, AudioFormat source) {
        List<AudioFormat> result = new ArrayList<AudioFormat>();
        if (!decodable(source) || !known(target)) return new AudioFormat[0];
        AudioFormat.Encoding from = source.getEncoding();
        int bits = source.getSampleSizeInBits();
        if (isInteger(target)) {
            if (isInteger(from)) {
                add(result, target, bits, source);
            } else if (isLaw(from)) {
                add(result, target, 16, source);
            } else {
                add(result, target, 8, source);
                add(result, target, 16, source);
                add(result, target, 24, source);
                add(result, target, 32, source);
            }
        } else if (AudioFormat.Encoding.PCM_FLOAT.equals(target)) {
            add(result, target, 32, source);
        } else if (isLaw(target)) {
            if (isLaw(from) || AudioFormat.Encoding.PCM_FLOAT.equals(from) || bits == 16) {
                add(result, target, 8, source);
            }
        }
        return result.toArray(new AudioFormat[0]);
    }

    private static void add(List<AudioFormat> result, AudioFormat.Encoding encoding, int bits, AudioFormat source) {
        int channels = source.getChannels();
        float rate = source.getSampleRate();
        if (bits == 8) {
            result.add(new AudioFormat(encoding, rate, bits, channels, channels, rate, false));
        } else {
            result.add(new AudioFormat(encoding, rate, bits, channels, channels * bits / 8, rate, false));
            result.add(new AudioFormat(encoding, rate, bits, channels, channels * bits / 8, rate, true));
        }
    }

    public static boolean isConversionSupported(AudioFormat target, AudioFormat source) {
        return source.matches(target) || concrete(target, source) != null;
    }

    public static boolean isConversionSupported(AudioFormat.Encoding target, AudioFormat source) {
        return source.getEncoding().equals(target) || targetFormats(target, source).length > 0;
    }

    private static AudioFormat concrete(AudioFormat target, AudioFormat source) {
        for (AudioFormat candidate : targetFormats(target.getEncoding(), source)) {
            if (candidate.matches(target)) return candidate;
        }
        return null;
    }

    /** {@code source} itself when it already matches {@code target}, else a converting stream. */
    public static AudioInputStream convert(AudioFormat target, AudioInputStream source) {
        AudioFormat from = source.getFormat();
        if (from.matches(target)) return source;
        AudioFormat to = concrete(target, from);
        if (to == null) {
            throw new IllegalArgumentException("Unsupported conversion: " + target + " from " + from);
        }
        return new AudioInputStream(new Converting(source, from, to), to, source.getFrameLength());
    }

    /** Converts to {@code target}, preferring the source's byte order where the encoding has one. */
    public static AudioInputStream convert(AudioFormat.Encoding target, AudioInputStream source) {
        AudioFormat from = source.getFormat();
        if (from.getEncoding().equals(target)) return source;
        AudioFormat[] formats = targetFormats(target, from);
        if (formats.length == 0) {
            throw new IllegalArgumentException("Unsupported conversion: " + target + " from " + from);
        }
        AudioFormat chosen = formats[0];
        for (AudioFormat candidate : formats) {
            if (candidate.isBigEndian() == from.isBigEndian()) {
                chosen = candidate;
                break;
            }
        }
        return new AudioInputStream(new Converting(source, from, chosen), chosen, source.getFrameLength());
    }

    // Samples travel as signed 32-bit values: an n-bit integer sample sits in the top n bits,
    // so integer-to-integer conversions at the same width are exact.

    static int decode(AudioFormat format, byte[] data, int offset) {
        AudioFormat.Encoding encoding = format.getEncoding();
        int bytes = format.getSampleSizeInBits() / 8;
        boolean big = format.isBigEndian();
        if (AudioFormat.Encoding.ULAW.equals(encoding)) return ulawToLinear(data[offset] & 0xFF) << 16;
        if (AudioFormat.Encoding.ALAW.equals(encoding)) return alawToLinear(data[offset] & 0xFF) << 16;
        long raw = 0;
        for (int i = 0; i < bytes; i++) {
            raw = (raw << 8) | (data[offset + (big ? i : bytes - 1 - i)] & 0xFF);
        }
        if (AudioFormat.Encoding.PCM_FLOAT.equals(encoding)) {
            double value = bytes == 4 ? Float.intBitsToFloat((int) raw) : Double.longBitsToDouble(raw);
            double scaled = Math.rint(value * 2147483648.0);
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, scaled));
        }
        int value = (int) (raw << (32 - bytes * 8));
        return AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding) ? value ^ Integer.MIN_VALUE : value;
    }

    static void encode(AudioFormat format, int sample, byte[] out, int offset) {
        AudioFormat.Encoding encoding = format.getEncoding();
        int bytes = format.getSampleSizeInBits() / 8;
        boolean big = format.isBigEndian();
        if (AudioFormat.Encoding.ULAW.equals(encoding)) {
            out[offset] = (byte) linearToUlaw(sample >> 16);
            return;
        }
        if (AudioFormat.Encoding.ALAW.equals(encoding)) {
            out[offset] = (byte) linearToAlaw(sample >> 16);
            return;
        }
        long raw;
        if (AudioFormat.Encoding.PCM_FLOAT.equals(encoding)) {
            raw = Float.floatToIntBits((float) (sample / 2147483648.0)) & 0xFFFFFFFFL;
        } else {
            int value = AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding) ? sample ^ Integer.MIN_VALUE : sample;
            raw = (value >>> (32 - bytes * 8)) & 0xFFFFFFFFL;
        }
        for (int i = 0; i < bytes; i++) {
            out[offset + (big ? bytes - 1 - i : i)] = (byte) (raw >>> (8 * i));
        }
    }

    // G.711, after the public-domain Sun reference implementation.

    static int ulawToLinear(int ulaw) {
        int u = ~ulaw & 0xFF;
        int t = ((u & 0x0F) << 3) + 0x84;
        t <<= (u & 0x70) >> 4;
        return (u & 0x80) != 0 ? 0x84 - t : t - 0x84;
    }

    static int alawToLinear(int alaw) {
        int a = alaw ^ 0x55;
        int t = (a & 0x0F) << 4;
        int segment = (a & 0x70) >> 4;
        if (segment == 0) {
            t += 8;
        } else {
            t += 0x108;
            if (segment > 1) t <<= segment - 1;
        }
        return (a & 0x80) != 0 ? t : -t;
    }

    static int linearToUlaw(int pcm) {
        int sign = (pcm >> 8) & 0x80;
        if (sign != 0) pcm = -pcm;
        if (pcm > 32635) pcm = 32635;
        pcm += 0x84;
        int exponent = 7;
        int mask = 0x4000;
        while ((pcm & mask) == 0 && exponent > 0) {
            exponent--;
            mask >>= 1;
        }
        int mantissa = (pcm >> (exponent + 3)) & 0x0F;
        return ~(sign | (exponent << 4) | mantissa) & 0xFF;
    }

    static int linearToAlaw(int pcm) {
        int value = pcm >> 3;
        int mask;
        if (value >= 0) {
            mask = 0xD5;
        } else {
            mask = 0x55;
            value = -value - 1;
        }
        int segment = 0;
        int end = 0x1F;
        while (segment < 8 && value > end) {
            segment++;
            end = (end << 1) | 1;
        }
        if (segment >= 8) return 0x7F ^ mask;
        int alaw = segment << 4;
        alaw |= segment < 2 ? (value >> 1) & 0x0F : (value >> segment) & 0x0F;
        return alaw ^ mask;
    }

    /** Reads whole source frames and hands out the converted bytes. */
    private static final class Converting extends InputStream {
        private final AudioInputStream in;
        private final AudioFormat from;
        private final AudioFormat to;
        private final int fromFrame;
        private final int toFrame;
        private final byte[] source;
        private final byte[] converted;
        private int position;
        private int limit;
        private boolean eof;

        Converting(AudioInputStream in, AudioFormat from, AudioFormat to) {
            this.in = in;
            this.from = from;
            this.to = to;
            this.fromFrame = from.getFrameSize();
            this.toFrame = to.getFrameSize();
            int frames = Math.max(1, 4096 / fromFrame);
            this.source = new byte[frames * fromFrame];
            this.converted = new byte[frames * toFrame];
        }

        private boolean fill() throws IOException {
            while (position >= limit) {
                if (eof) return false;
                int count = in.read(source, 0, source.length);
                if (count < 0) {
                    eof = true;
                    return false;
                }
                int frames = count / fromFrame;
                int channels = from.getChannels();
                int fromSample = from.getSampleSizeInBits() / 8;
                int toSample = to.getSampleSizeInBits() / 8;
                for (int f = 0; f < frames; f++) {
                    for (int c = 0; c < channels; c++) {
                        int sample = decode(from, source, f * fromFrame + c * fromSample);
                        encode(to, sample, converted, f * toFrame + c * toSample);
                    }
                }
                position = 0;
                limit = frames * toFrame;
            }
            return true;
        }

        @Override
        public int read() throws IOException {
            if (!fill()) return -1;
            return converted[position++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (!fill()) return -1;
            int count = Math.min(len, limit - position);
            System.arraycopy(converted, position, b, off, count);
            position += count;
            return count;
        }

        @Override
        public int available() throws IOException {
            return (limit - position) + (in.available() / fromFrame) * toFrame;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
