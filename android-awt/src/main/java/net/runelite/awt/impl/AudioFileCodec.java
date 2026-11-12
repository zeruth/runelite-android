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

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * The audio file readers and writers the JDK ships, for the containers it ships them for:
 * WAVE, AU (SND) and AIFF/AIFF-C are read; WAVE, AU and AIFF are written. WAVE samples go
 * through {@link WavDecoder}, which RuneLite's notification sounds already rely on.
 */
public final class AudioFileCodec {
    private static final AudioFileFormat.Type[] WRITABLE = {
        AudioFileFormat.Type.WAVE, AudioFileFormat.Type.AU, AudioFileFormat.Type.AIFF,
    };
    private static final int SNIFF = 12;
    private static final int AU_MAGIC = 0x2E736E64;
    private static final int WAVE_HEADER = 44;
    private static final int AU_HEADER = 24;
    private static final int AIFF_HEADER = 54;

    private AudioFileCodec() {
    }

    /** The parsed header of a file, the stream left at its first sample byte. */
    private static final class Parsed {
        final AudioFileFormat.Type type;
        final AudioFormat format;
        final long byteLength;
        final long dataLength;

        Parsed(AudioFileFormat.Type type, AudioFormat format, long byteLength, long dataLength) {
            this.type = type;
            this.format = format;
            this.byteLength = byteLength;
            this.dataLength = dataLength;
        }

        AudioFileFormat fileFormat() {
            long frames = dataLength >= 0 && format.getFrameSize() > 0 ? dataLength / format.getFrameSize() : -1;
            return new FileFormat(type, toInt(byteLength), format, toInt(frames));
        }
    }

    /** Reaches the protected constructor that carries the byte length. */
    private static final class FileFormat extends AudioFileFormat {
        FileFormat(Type type, int byteLength, AudioFormat format, int frameLength) {
            super(type, byteLength, format, frameLength);
        }
    }

    private static int toInt(long value) {
        return value < 0 || value > Integer.MAX_VALUE ? AudioSystem.NOT_SPECIFIED : (int) value;
    }

    // Reading.

    /** Reads the header of the file at the start of {@code in}, consuming it. */
    public static AudioFileFormat getAudioFileFormat(InputStream in) throws UnsupportedAudioFileException, IOException {
        return parse(in).fileFormat();
    }

    /**
     * The samples of the file at the start of {@code in}. The container is recognised from its
     * first bytes, which are pushed back (or reset) so the matching reader sees the whole file.
     */
    public static AudioInputStream getAudioInputStream(InputStream in) throws UnsupportedAudioFileException, IOException {
        InputStream source = in.markSupported() ? in : new PushbackInputStream(in, SNIFF);
        AudioFileFormat.Type type = sniff(source);
        if (AudioFileFormat.Type.WAVE.equals(type)) return WavDecoder.decode(source);
        Parsed parsed = parse(source);
        long frames = parsed.dataLength >= 0 && parsed.format.getFrameSize() > 0
            ? parsed.dataLength / parsed.format.getFrameSize() : AudioSystem.NOT_SPECIFIED;
        InputStream samples = parsed.dataLength >= 0 ? new WavDecoder.BoundedStream(source, parsed.dataLength) : source;
        return new AudioInputStream(samples, parsed.format, frames);
    }

    private static AudioFileFormat.Type sniff(InputStream in) throws UnsupportedAudioFileException, IOException {
        byte[] head = new byte[SNIFF];
        int count;
        if (in instanceof PushbackInputStream) {
            count = readUpTo(in, head);
            ((PushbackInputStream) in).unread(head, 0, count);
        } else {
            in.mark(SNIFF);
            count = readUpTo(in, head);
            in.reset();
        }
        if (count >= 12 && tag(head, 0, "RIFF") && tag(head, 8, "WAVE")) return AudioFileFormat.Type.WAVE;
        if (count >= 4 && tag(head, 0, ".snd")) return AudioFileFormat.Type.AU;
        if (count >= 12 && tag(head, 0, "FORM") && tag(head, 8, "AIFF")) return AudioFileFormat.Type.AIFF;
        if (count >= 12 && tag(head, 0, "FORM") && tag(head, 8, "AIFC")) return AudioFileFormat.Type.AIFC;
        throw new UnsupportedAudioFileException("Stream of unsupported format");
    }

    private static int readUpTo(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int count = in.read(buffer, total, buffer.length - total);
            if (count < 0) break;
            total += count;
        }
        return total;
    }

    private static boolean tag(byte[] data, int offset, String expected) {
        for (int i = 0; i < 4; i++) {
            if (data[offset + i] != expected.charAt(i)) return false;
        }
        return true;
    }

    private static Parsed parse(InputStream in) throws UnsupportedAudioFileException, IOException {
        DataInputStream data = new DataInputStream(in);
        byte[] id = new byte[4];
        try {
            data.readFully(id);
            if (tag(id, 0, "RIFF")) {
                // WavDecoder reads the whole header itself, from the RIFF tag on.
                return parseWave(new DataInputStream(new PrefixedStream(id, in)));
            }
            if (tag(id, 0, ".snd")) return parseAu(data);
            if (tag(id, 0, "FORM")) return parseAiff(data);
        } catch (EOFException e) {
            throw new UnsupportedAudioFileException("Truncated audio file");
        }
        throw new UnsupportedAudioFileException("File of unsupported format");
    }

    private static Parsed parseWave(DataInputStream data) throws UnsupportedAudioFileException, IOException {
        WavDecoder.Header header = WavDecoder.readHeader(data);
        return new Parsed(AudioFileFormat.Type.WAVE, header.format,
            header.riffLength < 0 ? -1 : header.riffLength + 8, header.dataLength);
    }

    private static Parsed parseAu(DataInputStream data) throws UnsupportedAudioFileException, IOException {
        long offset = data.readInt() & 0xFFFFFFFFL;
        long size = data.readInt() & 0xFFFFFFFFL;
        int encoding = data.readInt();
        int rate = data.readInt();
        int channels = data.readInt();
        if (offset < AU_HEADER || rate <= 0 || channels <= 0) throw new UnsupportedAudioFileException("Invalid AU header");
        WavDecoder.skipFully(data, offset - AU_HEADER);
        AudioFormat.Encoding kind;
        int bits;
        switch (encoding) {
            case 1: kind = AudioFormat.Encoding.ULAW; bits = 8; break;
            case 2: kind = AudioFormat.Encoding.PCM_SIGNED; bits = 8; break;
            case 3: kind = AudioFormat.Encoding.PCM_SIGNED; bits = 16; break;
            case 4: kind = AudioFormat.Encoding.PCM_SIGNED; bits = 24; break;
            case 5: kind = AudioFormat.Encoding.PCM_SIGNED; bits = 32; break;
            case 6: kind = AudioFormat.Encoding.PCM_FLOAT; bits = 32; break;
            case 7: kind = AudioFormat.Encoding.PCM_FLOAT; bits = 64; break;
            case 27: kind = AudioFormat.Encoding.ALAW; bits = 8; break;
            default: throw new UnsupportedAudioFileException("Unsupported AU encoding " + encoding);
        }
        AudioFormat format = new AudioFormat(kind, rate, bits, channels, channels * bits / 8, rate, true);
        long dataLength = size == 0xFFFFFFFFL ? -1 : size;
        return new Parsed(AudioFileFormat.Type.AU, format, dataLength < 0 ? -1 : offset + dataLength, dataLength);
    }

    private static Parsed parseAiff(DataInputStream data) throws UnsupportedAudioFileException, IOException {
        long formSize = data.readInt() & 0xFFFFFFFFL;
        byte[] id = new byte[4];
        data.readFully(id);
        boolean aifc;
        if (tag(id, 0, "AIFF")) aifc = false;
        else if (tag(id, 0, "AIFC")) aifc = true;
        else throw new UnsupportedAudioFileException("Not an AIFF stream");
        AudioFormat format = null;
        long frames = -1;
        while (true) {
            data.readFully(id);
            long size = data.readInt() & 0xFFFFFFFFL;
            if (tag(id, 0, "COMM")) {
                int channels = data.readShort();
                frames = data.readInt() & 0xFFFFFFFFL;
                int bits = data.readShort();
                double rate = readExtended(data);
                long consumed = 18;
                AudioFormat.Encoding kind = AudioFormat.Encoding.PCM_SIGNED;
                boolean big = true;
                if (aifc && size >= 22) {
                    byte[] compression = new byte[4];
                    data.readFully(compression);
                    consumed += 4;
                    if (tag(compression, 0, "NONE") || tag(compression, 0, "twos")) {
                        kind = AudioFormat.Encoding.PCM_SIGNED;
                    } else if (tag(compression, 0, "sowt")) {
                        big = false;
                    } else if (tag(compression, 0, "raw ")) {
                        kind = AudioFormat.Encoding.PCM_UNSIGNED;
                    } else if (tag(compression, 0, "fl32") || tag(compression, 0, "FL32")) {
                        kind = AudioFormat.Encoding.PCM_FLOAT;
                        bits = 32;
                    } else if (tag(compression, 0, "fl64") || tag(compression, 0, "FL64")) {
                        kind = AudioFormat.Encoding.PCM_FLOAT;
                        bits = 64;
                    } else if (tag(compression, 0, "ulaw") || tag(compression, 0, "ULAW")) {
                        kind = AudioFormat.Encoding.ULAW;
                        bits = 8;
                    } else if (tag(compression, 0, "alaw") || tag(compression, 0, "ALAW")) {
                        kind = AudioFormat.Encoding.ALAW;
                        bits = 8;
                    } else {
                        throw new UnsupportedAudioFileException("Unsupported AIFF-C compression");
                    }
                }
                WavDecoder.skipFully(data, size - consumed + (size & 1));
                if (channels <= 0 || bits <= 0 || rate <= 0) throw new UnsupportedAudioFileException("Invalid AIFF COMM chunk");
                float sampleRate = (float) rate;
                format = new AudioFormat(kind, sampleRate, bits, channels, channels * ((bits + 7) / 8), sampleRate, big);
            } else if (tag(id, 0, "SSND")) {
                if (format == null) throw new UnsupportedAudioFileException("AIFF SSND chunk before COMM chunk");
                long offset = data.readInt() & 0xFFFFFFFFL;
                data.readInt();
                WavDecoder.skipFully(data, offset);
                long dataLength = Math.max(0, size - 8 - offset);
                if (frames >= 0) dataLength = Math.min(dataLength, frames * format.getFrameSize());
                return new Parsed(aifc ? AudioFileFormat.Type.AIFC : AudioFileFormat.Type.AIFF, format,
                    formSize + 8, dataLength);
            } else {
                WavDecoder.skipFully(data, size + (size & 1));
            }
        }
    }

    /** IEEE 754 80-bit extended, as AIFF stores the sample rate. */
    private static double readExtended(DataInputStream data) throws IOException {
        int exponent = data.readUnsignedShort();
        long mantissa = data.readLong();
        boolean negative = (exponent & 0x8000) != 0;
        exponent &= 0x7FFF;
        if (exponent == 0 && mantissa == 0) return 0;
        double value = Math.scalb((double) (mantissa >>> 11), exponent - 16383 - 52);
        return negative ? -value : value;
    }

    private static void writeExtended(DataOutputStream out, double value) throws IOException {
        if (value <= 0) {
            out.writeShort(0);
            out.writeLong(0);
            return;
        }
        long bits = Double.doubleToRawLongBits(value);
        int exponent = Math.getExponent(value);
        long mantissa = (1L << 63) | ((bits & 0xFFFFFFFFFFFFFL) << 11);
        out.writeShort(exponent + 16383);
        out.writeLong(mantissa);
    }

    /** Replays bytes already read in front of the rest of the stream. */
    private static final class PrefixedStream extends InputStream {
        private final byte[] prefix;
        private final InputStream in;
        private int position;

        PrefixedStream(byte[] prefix, InputStream in) {
            this.prefix = prefix;
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            return position < prefix.length ? prefix[position++] & 0xFF : in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (position < prefix.length) {
                int count = Math.min(len, prefix.length - position);
                System.arraycopy(prefix, position, b, off, count);
                position += count;
                return count;
            }
            return in.read(b, off, len);
        }
    }

    // Writing.

    public static AudioFileFormat.Type[] getWritableTypes() {
        return WRITABLE.clone();
    }

    public static AudioFileFormat.Type[] getWritableTypes(AudioInputStream stream) {
        List<AudioFileFormat.Type> types = new ArrayList<AudioFileFormat.Type>();
        for (AudioFileFormat.Type type : WRITABLE) {
            if (fileFormat(type, stream.getFormat()) != null) types.add(type);
        }
        return types.toArray(new AudioFileFormat.Type[0]);
    }

    public static boolean isWritable(AudioFileFormat.Type type) {
        for (AudioFileFormat.Type candidate : WRITABLE) {
            if (candidate.equals(type)) return true;
        }
        return false;
    }

    public static boolean isWritable(AudioFileFormat.Type type, AudioInputStream stream) {
        return fileFormat(type, stream.getFormat()) != null;
    }

    /** The sample format a file of {@code type} stores {@code source} in, or null when it cannot. */
    private static AudioFormat fileFormat(AudioFileFormat.Type type, AudioFormat source) {
        AudioFormat.Encoding encoding = source.getEncoding();
        int bits = source.getSampleSizeInBits();
        boolean integer = AudioFormat.Encoding.PCM_SIGNED.equals(encoding) || AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding);
        boolean law = AudioFormat.Encoding.ULAW.equals(encoding) || AudioFormat.Encoding.ALAW.equals(encoding);
        boolean floating = AudioFormat.Encoding.PCM_FLOAT.equals(encoding);
        AudioFormat target;
        if (AudioFileFormat.Type.WAVE.equals(type)) {
            if (integer) target = like(source, bits <= 8 ? AudioFormat.Encoding.PCM_UNSIGNED : AudioFormat.Encoding.PCM_SIGNED, false);
            else if (floating || law) target = like(source, encoding, false);
            else return null;
        } else if (AudioFileFormat.Type.AU.equals(type)) {
            if (integer) target = like(source, AudioFormat.Encoding.PCM_SIGNED, true);
            else if (floating || law) target = like(source, encoding, true);
            else return null;
        } else if (AudioFileFormat.Type.AIFF.equals(type)) {
            if (integer) target = like(source, AudioFormat.Encoding.PCM_SIGNED, true);
            else return null;
        } else {
            return null;
        }
        if (source.matches(target)) return source;
        return PcmConverter.isConversionSupported(target, source) ? target : null;
    }

    private static AudioFormat like(AudioFormat source, AudioFormat.Encoding encoding, boolean bigEndian) {
        int bits = source.getSampleSizeInBits();
        int frameSize = source.getChannels() * ((bits + 7) / 8);
        return new AudioFormat(encoding, source.getSampleRate(), bits, source.getChannels(), frameSize,
            source.getSampleRate(), bits > 8 && bigEndian);
    }

    public static int write(AudioInputStream stream, AudioFileFormat.Type type, OutputStream out) throws IOException {
        AudioFormat target = writable(stream, type);
        AudioInputStream data = PcmConverter.convert(target, stream);
        long expected = dataLength(data, target);
        if (expected < 0 && !AudioFileFormat.Type.AU.equals(type)) {
            throw new IOException("stream length not specified");
        }
        out.write(header(type, target, expected));
        long written = copy(data, out, expected);
        out.flush();
        return toTotal(type, written);
    }

    public static int write(AudioInputStream stream, AudioFileFormat.Type type, File file) throws IOException {
        AudioFormat target = writable(stream, type);
        AudioInputStream data = PcmConverter.convert(target, stream);
        long expected = dataLength(data, target);
        long written;
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        try {
            out.write(header(type, target, expected));
            written = copy(data, out, expected);
        } finally {
            out.close();
        }
        if (expected < 0) {
            // The length is known only now; every header here is fixed-size, so rewrite it in place.
            RandomAccessFile patch = new RandomAccessFile(file, "rw");
            try {
                patch.seek(0);
                patch.write(header(type, target, written));
            } finally {
                patch.close();
            }
        }
        return toTotal(type, written);
    }

    private static AudioFormat writable(AudioInputStream stream, AudioFileFormat.Type type) {
        AudioFormat target = fileFormat(type, stream.getFormat());
        if (target == null) {
            throw new IllegalArgumentException("could not write audio file: file type not supported: " + type);
        }
        return target;
    }

    private static long dataLength(AudioInputStream data, AudioFormat format) {
        long frames = data.getFrameLength();
        return frames == AudioSystem.NOT_SPECIFIED ? -1 : frames * format.getFrameSize();
    }

    /** Copies the samples; a stream that ends early is padded with silence so the header holds. */
    private static long copy(AudioInputStream data, OutputStream out, long expected) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        while (expected < 0 || total < expected) {
            int want = expected < 0 ? buffer.length : (int) Math.min(buffer.length, expected - total);
            int count = data.read(buffer, 0, want);
            if (count < 0) break;
            out.write(buffer, 0, count);
            total += count;
        }
        if (expected > total) {
            byte[] silence = new byte[buffer.length];
            byte fill = AudioFormat.Encoding.PCM_UNSIGNED.equals(data.getFormat().getEncoding()) ? (byte) 0x80 : 0;
            java.util.Arrays.fill(silence, fill);
            while (total < expected) {
                int count = (int) Math.min(silence.length, expected - total);
                out.write(silence, 0, count);
                total += count;
            }
        }
        return total;
    }

    /** RIFF and IFF chunks are word-aligned; the pad byte is not part of the data length. */
    private static long pad(AudioFileFormat.Type type, long dataBytes) {
        return AudioFileFormat.Type.AU.equals(type) ? 0 : dataBytes & 1;
    }

    private static int toTotal(AudioFileFormat.Type type, long dataBytes) {
        long header = AudioFileFormat.Type.WAVE.equals(type) ? WAVE_HEADER
            : AudioFileFormat.Type.AU.equals(type) ? AU_HEADER : AIFF_HEADER;
        return (int) Math.min(Integer.MAX_VALUE, header + dataBytes + pad(type, dataBytes));
    }

    private static byte[] header(AudioFileFormat.Type type, AudioFormat format, long dataLength) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(AIFF_HEADER);
        DataOutputStream out = new DataOutputStream(bytes);
        int channels = format.getChannels();
        int rate = Math.round(format.getSampleRate());
        int bits = format.getSampleSizeInBits();
        long data = Math.max(0, dataLength);
        long pad = data & 1;
        if (AudioFileFormat.Type.WAVE.equals(type)) {
            AudioFormat.Encoding encoding = format.getEncoding();
            int tag = AudioFormat.Encoding.PCM_FLOAT.equals(encoding) ? 3
                : AudioFormat.Encoding.ALAW.equals(encoding) ? 6
                : AudioFormat.Encoding.ULAW.equals(encoding) ? 7 : 1;
            out.writeBytes("RIFF");
            writeIntLE(out, (int) (36 + data + pad));
            out.writeBytes("WAVE");
            out.writeBytes("fmt ");
            writeIntLE(out, 16);
            writeShortLE(out, tag);
            writeShortLE(out, channels);
            writeIntLE(out, rate);
            writeIntLE(out, rate * format.getFrameSize());
            writeShortLE(out, format.getFrameSize());
            writeShortLE(out, bits);
            out.writeBytes("data");
            writeIntLE(out, (int) data);
        } else if (AudioFileFormat.Type.AU.equals(type)) {
            AudioFormat.Encoding encoding = format.getEncoding();
            int code;
            if (AudioFormat.Encoding.ULAW.equals(encoding)) code = 1;
            else if (AudioFormat.Encoding.ALAW.equals(encoding)) code = 27;
            else if (AudioFormat.Encoding.PCM_FLOAT.equals(encoding)) code = bits == 64 ? 7 : 6;
            else code = bits / 8 + 1;
            out.writeInt(AU_MAGIC);
            out.writeInt(AU_HEADER);
            out.writeInt(dataLength < 0 ? -1 : (int) data);
            out.writeInt(code);
            out.writeInt(rate);
            out.writeInt(channels);
        } else {
            out.writeBytes("FORM");
            out.writeInt((int) (46 + data + pad));
            out.writeBytes("AIFF");
            out.writeBytes("COMM");
            out.writeInt(18);
            out.writeShort(channels);
            out.writeInt((int) (data / format.getFrameSize()));
            out.writeShort(bits);
            writeExtended(out, format.getSampleRate());
            out.writeBytes("SSND");
            out.writeInt((int) (8 + data));
            out.writeInt(0);
            out.writeInt(0);
        }
        out.flush();
        return bytes.toByteArray();
    }

    private static void writeIntLE(DataOutputStream out, int value) throws IOException {
        out.write(value);
        out.write(value >>> 8);
        out.write(value >>> 16);
        out.write(value >>> 24);
    }

    private static void writeShortLE(DataOutputStream out, int value) throws IOException {
        out.write(value);
        out.write(value >>> 8);
    }
}
