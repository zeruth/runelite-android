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
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * RIFF/WAVE reader backing {@link AudioSystem#getAudioInputStream}. Desktop Java ships
 * readers for WAV/AU/AIFF; WAV is what plugins and RuneLite's own notification sounds use
 * (AudioPlayer → AudioSystem.getAudioInputStream → Clip), so that is what is covered here:
 * integer PCM, IEEE float and WAVE_FORMAT_EXTENSIBLE wrapping either. Anything else throws
 * UnsupportedAudioFileException, the same contract the JDK has for formats it can't read.
 */
public final class WavDecoder {
    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_FLOAT = 3;
    private static final int FORMAT_ALAW = 6;
    private static final int FORMAT_ULAW = 7;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;

    private WavDecoder() {
    }

    public static AudioInputStream decode(InputStream in) throws UnsupportedAudioFileException, IOException {
        DataInputStream data = new DataInputStream(in);
        Header header = readHeader(data);
        // Streaming encoders write 0 / 0xFFFFFFFF for an unknown length; read to EOF then.
        if (header.dataLength < 0) return readToEnd(data, header.format);
        long frames = header.format.getFrameSize() > 0 ? header.dataLength / header.format.getFrameSize() : AudioSystem.NOT_SPECIFIED;
        return new AudioInputStream(new BoundedStream(in, header.dataLength), header.format, frames);
    }

    /** What precedes the samples: the format, the RIFF length and the data length (-1 when unknown). */
    static final class Header {
        final AudioFormat format;
        final long riffLength;
        final long dataLength;

        Header(AudioFormat format, long riffLength, long dataLength) {
            this.format = format;
            this.riffLength = riffLength;
            this.dataLength = dataLength;
        }
    }

    /** Reads up to the first byte of the data chunk. */
    static Header readHeader(DataInputStream data) throws UnsupportedAudioFileException, IOException {
        byte[] id = new byte[4];
        try {
            data.readFully(id);
            if (!tag(id, "RIFF")) throw new UnsupportedAudioFileException("Not a RIFF/WAVE stream");
            long riff = readIntLE(data) & 0xFFFFFFFFL;
            data.readFully(id);
            if (!tag(id, "WAVE")) throw new UnsupportedAudioFileException("Not a RIFF/WAVE stream");

            AudioFormat format = null;
            while (true) {
                data.readFully(id);
                long size = readIntLE(data) & 0xFFFFFFFFL;
                if (tag(id, "fmt ")) {
                    byte[] fmt = new byte[(int) size];
                    data.readFully(fmt);
                    format = parseFormat(fmt);
                    if ((size & 1) != 0) data.skipBytes(1);
                } else if (tag(id, "data")) {
                    if (format == null) throw new UnsupportedAudioFileException("WAVE data chunk before fmt chunk");
                    boolean unknown = size == 0 || size == 0xFFFFFFFFL;
                    return new Header(format, riff == 0 || riff == 0xFFFFFFFFL ? -1 : riff, unknown ? -1 : size);
                } else {
                    skipFully(data, size + (size & 1));
                }
            }
        } catch (EOFException e) {
            throw new UnsupportedAudioFileException("Truncated WAVE stream");
        }
    }

    private static AudioFormat parseFormat(byte[] fmt) throws UnsupportedAudioFileException {
        if (fmt.length < 16) throw new UnsupportedAudioFileException("WAVE fmt chunk too short");
        int tag = u16(fmt, 0);
        int channels = u16(fmt, 2);
        int sampleRate = s32(fmt, 4);
        int blockAlign = u16(fmt, 12);
        int bits = u16(fmt, 14);
        if (tag == FORMAT_EXTENSIBLE) {
            // cbSize(2) validBits(2) channelMask(4) then the SubFormat GUID, whose first two
            // bytes are the real format tag.
            if (fmt.length < 26) throw new UnsupportedAudioFileException("WAVE extensible fmt chunk too short");
            tag = u16(fmt, 24);
        }
        AudioFormat.Encoding encoding;
        if (tag == FORMAT_PCM) encoding = bits <= 8 ? AudioFormat.Encoding.PCM_UNSIGNED : AudioFormat.Encoding.PCM_SIGNED;
        else if (tag == FORMAT_FLOAT) encoding = AudioFormat.Encoding.PCM_FLOAT;
        else if (tag == FORMAT_ALAW && bits == 8) encoding = AudioFormat.Encoding.ALAW;
        else if (tag == FORMAT_ULAW && bits == 8) encoding = AudioFormat.Encoding.ULAW;
        else throw new UnsupportedAudioFileException("Unsupported WAVE encoding 0x" + Integer.toHexString(tag));
        if (channels <= 0 || sampleRate <= 0 || bits <= 0) throw new UnsupportedAudioFileException("Invalid WAVE fmt chunk");
        int frameSize = blockAlign > 0 ? blockAlign : ((bits + 7) / 8) * channels;
        return new AudioFormat(encoding, sampleRate, bits, channels, frameSize, sampleRate, false);
    }

    private static AudioInputStream readToEnd(InputStream in, AudioFormat format) throws IOException {
        byte[] all = Bytes.readAll(in);
        long frames = format.getFrameSize() > 0 ? all.length / format.getFrameSize() : AudioSystem.NOT_SPECIFIED;
        return new AudioInputStream(new ByteArrayInputStream(all), format, frames);
    }

    private static boolean tag(byte[] id, String expected) {
        for (int i = 0; i < 4; i++) if (id[i] != expected.charAt(i)) return false;
        return true;
    }

    private static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte(), b2 = in.readUnsignedByte(), b3 = in.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static int u16(byte[] b, int off) { return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8); }
    private static int s32(byte[] b, int off) { return u16(b, off) | (u16(b, off + 2) << 16); }

    static void skipFully(InputStream in, long n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException();
                skipped = 1;
            }
            n -= skipped;
        }
    }

    static final class Bytes {
        static byte[] readAll(InputStream in) throws IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    /** Stops at the end of the data chunk so trailing chunks (LIST, id3) aren't played. */
    static final class BoundedStream extends InputStream {
        private final InputStream in;
        private long left;

        BoundedStream(InputStream in, long left) { this.in = in; this.left = left; }

        @Override public int read() throws IOException {
            if (left <= 0) return -1;
            int b = in.read();
            if (b >= 0) left--;
            return b;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }

        @Override public int available() throws IOException { return (int) Math.min(in.available(), left); }
        @Override public void close() throws IOException { in.close(); }
    }
}
