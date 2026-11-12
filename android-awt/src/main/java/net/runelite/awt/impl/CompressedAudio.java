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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Compressed audio (MP3, Ogg Vorbis/Opus, AAC in ADTS or MP4, FLAC) for
 * {@link CompressedAudioFileReader} and {@link CompressedAudioCodec}: container sniffing, the
 * encodings and file types the desktop service providers report, and the decoder backend.
 *
 * <p>Desktop plugins get these formats from mp3spi/vorbisspi on their classpath, which report the
 * encoded stream as {@code MPEG1L3}/{@code VORBISENC} with an unspecified frame size and decode it
 * when the stream is converted to PCM. The same shape is kept here so
 * {@code getAudioInputStream(pcmFormat, getAudioInputStream(url))} works unchanged. Decoding is
 * done by a {@link Decoder} loaded by name ({@code MediaCodecAudioDecoder} on Android), so this
 * class and the reader and codec name nothing from {@code android.*} and the host contracts can
 * patch them over the JDK with a test decoder installed.
 */
public final class CompressedAudio {
    public static final AudioFormat.Encoding MPEG1L1 = new AudioFormat.Encoding("MPEG1L1");
    public static final AudioFormat.Encoding MPEG1L2 = new AudioFormat.Encoding("MPEG1L2");
    public static final AudioFormat.Encoding MPEG1L3 = new AudioFormat.Encoding("MPEG1L3");
    public static final AudioFormat.Encoding MPEG2L1 = new AudioFormat.Encoding("MPEG2L1");
    public static final AudioFormat.Encoding MPEG2L2 = new AudioFormat.Encoding("MPEG2L2");
    public static final AudioFormat.Encoding MPEG2L3 = new AudioFormat.Encoding("MPEG2L3");
    public static final AudioFormat.Encoding MPEG2DOT5L1 = new AudioFormat.Encoding("MPEG2DOT5L1");
    public static final AudioFormat.Encoding MPEG2DOT5L2 = new AudioFormat.Encoding("MPEG2DOT5L2");
    public static final AudioFormat.Encoding MPEG2DOT5L3 = new AudioFormat.Encoding("MPEG2DOT5L3");
    /** vorbisspi's name for Vorbis. */
    public static final AudioFormat.Encoding VORBISENC = new AudioFormat.Encoding("VORBISENC");
    public static final AudioFormat.Encoding OPUS = new AudioFormat.Encoding("OPUS");
    public static final AudioFormat.Encoding AAC = new AudioFormat.Encoding("AAC");
    public static final AudioFormat.Encoding ALAC = new AudioFormat.Encoding("ALAC");
    public static final AudioFormat.Encoding FLAC = new AudioFormat.Encoding("FLAC");

    static final AudioFormat.Encoding[] ENCODINGS = {
        MPEG1L1, MPEG1L2, MPEG1L3, MPEG2L1, MPEG2L2, MPEG2L3, MPEG2DOT5L1, MPEG2DOT5L2, MPEG2DOT5L3,
        VORBISENC, OPUS, AAC, ALAC, FLAC,
    };

    public static final AudioFileFormat.Type MP3 = new AudioFileFormat.Type("MP3", "mp3");
    public static final AudioFileFormat.Type OGG = new AudioFileFormat.Type("OGG", "ogg");
    public static final AudioFileFormat.Type ADTS = new AudioFileFormat.Type("AAC", "aac");
    public static final AudioFileFormat.Type M4A = new AudioFileFormat.Type("M4A", "m4a");
    public static final AudioFileFormat.Type FLAC_FILE = new AudioFileFormat.Type("FLAC", "flac");

    /** Bytes looked at after any ID3v2 tag; holds an MP4 {@code moov} that precedes the media data. */
    static final int HEAD = 256 * 1024;
    /** The largest ID3v2 tag (cover art included) skipped while sniffing a mark/reset stream. */
    static final int MAX_TAG = 4 * 1024 * 1024;
    /** How far past an ID3v2 tag the first MPEG/ADTS frame is searched for (encoders pad tags). */
    private static final int SYNC_WINDOW = 4096;

    private static final String ANDROID_DECODER = "net.runelite.awt.impl.MediaCodecAudioDecoder";
    private static final Decoder NONE = new Decoder() {
        @Override
        public AudioInputStream decode(File file, boolean temporary, AudioFormat encoded) throws IOException {
            throw new IOException("No compressed-audio decoder on this platform");
        }
    };
    private static volatile Decoder decoder;

    private CompressedAudio() {
    }

    /** Decodes one container file to PCM; implemented over MediaExtractor and MediaCodec on Android. */
    public interface Decoder {
        /**
         * The samples of the first audio track of {@code file}, as integer or float PCM in whatever
         * layout the platform decoder produces. When {@code temporary} is true the decoder owns the
         * file and deletes it when the returned stream is closed, or when decoding cannot start.
         */
        AudioInputStream decode(File file, boolean temporary, AudioFormat encoded) throws IOException;
    }

    /** Replaces the platform decoder; {@code null} reloads it. For tests and alternative backends. */
    public static void setDecoder(Decoder replacement) {
        decoder = replacement;
    }

    static Decoder decoder() {
        Decoder d = decoder;
        if (d == null) {
            try {
                d = (Decoder) Class.forName(ANDROID_DECODER).getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                d = NONE;
            }
            decoder = d;
        }
        return d;
    }

    static boolean canDecode() {
        return decoder() != NONE;
    }

    static boolean isCompressed(AudioFormat.Encoding encoding) {
        for (AudioFormat.Encoding e : ENCODINGS) {
            if (e.equals(encoding)) return true;
        }
        return false;
    }

    /**
     * An encoded stream as the readers return it: the container's bytes from its first byte, so
     * reading it yields the file the way desktop mp3spi's encoded stream does. The codec decodes the
     * file it came from, or, for a stream that is not a file (a jar resource, a URL), the bytes left
     * in it spooled to a temporary file, since MediaExtractor needs a seekable source.
     */
    public static final class EncodedStream extends AudioInputStream {
        private final File file;
        private final boolean owned;
        private final String extension;
        private volatile boolean consumed;

        EncodedStream(InputStream in, AudioFileFormat fileFormat, File file, boolean owned) {
            super(in, fileFormat.getFormat(), AudioSystem.NOT_SPECIFIED);
            this.file = file;
            this.owned = owned;
            this.extension = fileFormat.getType().getExtension();
        }

        @Override
        public int read() throws IOException {
            consumed = true;
            return super.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            consumed = true;
            return super.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            consumed = true;
            return super.skip(n);
        }

        /** The file to decode, and whether the decoder must delete it. */
        AudioInputStream decode() throws IOException {
            Decoder d = decoder();
            if (file != null && !consumed) {
                if (owned) close();
                return d.decode(file, false, getFormat());
            }
            File spool = File.createTempFile("rl-audio-", "." + extension);
            try {
                OutputStream out = new FileOutputStream(spool);
                try {
                    byte[] buffer = new byte[64 * 1024];
                    for (int n; (n = read(buffer, 0, buffer.length)) >= 0; ) {
                        out.write(buffer, 0, n);
                    }
                } finally {
                    out.close();
                }
                if (owned) close();
            } catch (IOException e) {
                spool.delete();
                throw e;
            }
            return d.decode(spool, true, getFormat());
        }
    }

    /**
     * The format of the compressed file at the current position of {@code in}, or {@code null};
     * the stream is reset to where it was.
     */
    static AudioFileFormat sniff(InputStream in) throws IOException {
        if (!in.markSupported()) {
            throw new IOException("mark/reset not supported");
        }
        in.mark(MAX_TAG + HEAD + 10);
        try {
            byte[] id3 = new byte[10];
            int n = readFully(in, id3, 0, id3.length);
            int tag = id3Length(id3, n);
            if (tag < 0) {
                byte[] head = new byte[HEAD];
                System.arraycopy(id3, 0, head, 0, n);
                n += readFully(in, head, n, head.length - n);
                return parse(head, n, false);
            }
            if (tag > MAX_TAG) {
                // A tag this size only fronts MP3 in practice; the decoder reads the real stream info.
                return fileFormat(MP3, new AudioFormat(MPEG1L3, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED,
                    AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED, true), null);
            }
            if (discard(in, tag - n) < tag - n) return null;
            byte[] head = new byte[HEAD];
            n = readFully(in, head, 0, head.length);
            return parse(head, n, true);
        } finally {
            in.reset();
        }
    }

    /** The total length of an ID3v2 tag starting {@code b}, or -1. */
    private static int id3Length(byte[] b, int n) {
        if (n < 10 || b[0] != 'I' || b[1] != 'D' || b[2] != '3') return -1;
        if ((b[6] | b[7] | b[8] | b[9]) < 0) return -1; // the size is synchsafe: no byte has its top bit set
        int size = (b[6] << 21) | (b[7] << 14) | (b[8] << 7) | b[9];
        return 10 + size + ((b[5] & 0x10) != 0 ? 10 : 0);
    }

    static AudioFileFormat parse(byte[] b, int len, boolean afterTag) {
        if (len >= 4 && tag(b, 0, "fLaC")) return flac(b, len, 0);
        if (len >= 8 && tag(b, 4, "ftyp")) return mp4(b, len);
        if (len >= 4 && tag(b, 0, "OggS")) return ogg(b, len);
        int window = afterTag ? Math.min(SYNC_WINDOW, len - 4) : 0;
        for (int i = 0; i <= window && i + 4 <= len; i++) {
            if ((b[i] & 0xFF) != 0xFF) continue;
            AudioFileFormat format = adts(b, len, i);
            if (format == null) format = mpeg(b, len, i);
            if (format != null) return format;
        }
        return null;
    }

    private static final int[][] MPEG_KBPS = {
        {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448}, // MPEG-1 layer I
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},    // MPEG-1 layer II
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},     // MPEG-1 layer III
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},    // MPEG-2/2.5 layer I
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},         // MPEG-2/2.5 layers II, III
    };
    private static final int[] MPEG_RATES = {44100, 48000, 32000};

    /** An MPEG audio frame header at {@code i}, confirmed by the next frame when it is in the buffer. */
    private static AudioFileFormat mpeg(byte[] b, int len, int i) {
        int h = be32(b, i);
        if ((h >>> 21) != 0x7FF) return null;
        int version = (h >>> 19) & 3;  // 0 = 2.5, 2 = 2, 3 = 1
        int layerBits = (h >>> 17) & 3;
        int bitrate = (h >>> 12) & 15;
        int rateIndex = (h >>> 10) & 3;
        // Free-format frames (bitrate 0) have no computable length to confirm against the next
        // frame, which leaves a lone 0xFF in arbitrary data too easy to accept; they are not taken.
        if (version == 1 || layerBits == 0 || bitrate == 0 || bitrate == 15 || rateIndex == 3) return null;
        int layer = 4 - layerBits;
        int rate = MPEG_RATES[rateIndex] >> (version == 3 ? 0 : version == 2 ? 1 : 2);
        int samples = layer == 1 ? 384 : layer == 3 && version != 3 ? 576 : 1152;
        int kbps = MPEG_KBPS[version == 3 ? layer - 1 : layer == 1 ? 3 : 4][bitrate];
        int padding = (h >>> 9) & 1;
        int length = layer == 1 ? (12 * kbps * 1000 / rate + padding) * 4 : samples / 8 * kbps * 1000 / rate + padding;
        int next = i + length;
        if (next + 4 <= len && (be32(b, next) & 0xFFFE0C00) != (h & 0xFFFE0C00)) return null;
        String name = "MPEG" + (version == 3 ? "1" : version == 2 ? "2" : "2DOT5") + "L" + layer;
        int channels = ((h >>> 6) & 3) == 3 ? 1 : 2;
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("bitrate", kbps * 1000);
        AudioFormat format = new AudioFormat(encoding(name), rate, AudioSystem.NOT_SPECIFIED, channels,
            AudioSystem.NOT_SPECIFIED, rate / (float) samples, true);
        return fileFormat(MP3, format, properties);
    }

    private static final int[] AAC_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000,
        12000, 11025, 8000, 7350};

    /** An ADTS frame header at {@code i}: MPEG's sync with the layer bits MPEG audio reserves. */
    private static AudioFileFormat adts(byte[] b, int len, int i) {
        if (i + 7 > len || (b[i] & 0xFF) != 0xFF || (b[i + 1] & 0xF6) != 0xF0) return null;
        int rateIndex = (b[i + 2] >> 2) & 15;
        if (rateIndex >= AAC_RATES.length) return null;
        int channels = ((b[i + 2] & 1) << 2) | ((b[i + 3] >> 6) & 3);
        int length = ((b[i + 3] & 3) << 11) | ((b[i + 4] & 0xFF) << 3) | ((b[i + 5] & 0xFF) >> 5);
        if (length < 7) return null;
        int next = i + length;
        if (next + 2 <= len && ((b[next] & 0xFF) != 0xFF || (b[next + 1] & 0xF6) != 0xF0)) return null;
        int rate = AAC_RATES[rateIndex];
        AudioFormat format = new AudioFormat(AAC, rate, AudioSystem.NOT_SPECIFIED,
            channels == 0 ? AudioSystem.NOT_SPECIFIED : channels, AudioSystem.NOT_SPECIFIED, rate / 1024f, true);
        return fileFormat(ADTS, format, null);
    }

    /** The identification header in the first Ogg page: Vorbis, Opus or Ogg FLAC. */
    private static AudioFileFormat ogg(byte[] b, int len) {
        if (len < 27 || b[4] != 0) return null;
        int p = 27 + (b[26] & 0xFF);
        if (p + 19 > len) return null;
        int ns = AudioSystem.NOT_SPECIFIED;
        if (b[p] == 1 && tag(b, p + 1, "vorbis")) {
            int channels = b[p + 11] & 0xFF;
            int rate = le32(b, p + 12);
            if (channels == 0 || rate <= 0) return null;
            return fileFormat(OGG, new AudioFormat(VORBISENC, rate, ns, channels, ns, ns, true), null);
        }
        if (tag(b, p, "OpusHead")) {
            int channels = b[p + 9] & 0xFF;
            if (channels == 0) return null;
            // Opus always decodes at 48 kHz; the header's input rate is informational.
            return fileFormat(OGG, new AudioFormat(OPUS, 48000, ns, channels, ns, ns, true), null);
        }
        if (b[p] == 0x7F && tag(b, p + 1, "FLAC")) {
            AudioFileFormat flac = flac(b, len, p + 9);
            return flac == null ? null : fileFormat(OGG, flac.getFormat(), flac.properties());
        }
        return null;
    }

    /** {@code fLaC} at {@code o} followed by its STREAMINFO block. */
    private static AudioFileFormat flac(byte[] b, int len, int o) {
        int d = o + 8;
        if (!tag(b, o, "fLaC") || d + 18 > len || (b[o + 4] & 0x7F) != 0) return null;
        int rate = ((b[d + 10] & 0xFF) << 12) | ((b[d + 11] & 0xFF) << 4) | ((b[d + 12] & 0xFF) >> 4);
        int channels = ((b[d + 12] >> 1) & 7) + 1;
        int bits = (((b[d + 12] & 1) << 4) | ((b[d + 13] >> 4) & 15)) + 1;
        long total = ((long) (b[d + 13] & 15) << 32) | (be32(b, d + 14) & 0xFFFFFFFFL);
        if (rate == 0) return null;
        Map<String, Object> properties = null;
        if (total > 0) {
            properties = new HashMap<String, Object>();
            properties.put("duration", total * 1000000L / rate);
        }
        int ns = AudioSystem.NOT_SPECIFIED;
        AudioFormat format = new AudioFormat(FLAC, rate, bits, channels, ns, ns, false);
        return fileFormat(FLAC_FILE, format, properties);
    }

    /**
     * An ISO base media file: the first audio sample entry under {@code moov}, or an unknown rate and
     * channel count when the {@code moov} box lies past the sniffed bytes (it often trails the media).
     */
    private static AudioFileFormat mp4(byte[] b, int len) {
        int found = sampleEntry(b, 0, len, 0);
        int ns = AudioSystem.NOT_SPECIFIED;
        if (found == -2) return null; // a moov with no audio track
        if (found < 0) return fileFormat(M4A, new AudioFormat(AAC, ns, ns, ns, ns, ns, true), null);
        String type = new String(b, found + 4, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        AudioFormat.Encoding encoding = "mp4a".equals(type) ? AAC : "alac".equals(type) ? ALAC
            : "fLaC".equals(type) ? FLAC : "Opus".equals(type) ? OPUS : ".mp3".equals(type) ? MPEG1L3
            : encoding(type.trim().toUpperCase(java.util.Locale.ROOT));
        int channels = ((b[found + 24] & 0xFF) << 8) | (b[found + 25] & 0xFF);
        int rate = (be32(b, found + 32) >>> 16) & 0xFFFF;
        AudioFormat format = new AudioFormat(encoding, rate == 0 ? ns : rate, ns, channels == 0 ? ns : channels,
            ns, ns, true);
        return fileFormat(M4A, format, null);
    }

    private static final String[] AUDIO_ENTRIES = {"mp4a", "alac", "fLaC", "Opus", ".mp3", "ac-3", "ec-3", "samr"};

    /** Offset of an audio sample entry within [start, end); -1 when not seen, -2 when moov has none. */
    private static int sampleEntry(byte[] b, int start, int end, int depth) {
        int at = start;
        boolean moov = false;
        while (at + 8 <= end) {
            long size = be32(b, at) & 0xFFFFFFFFL;
            int header = 8;
            if (size == 1) {
                if (at + 16 > end) return -1;
                size = ((long) be32(b, at + 8) << 32) | (be32(b, at + 12) & 0xFFFFFFFFL);
                header = 16;
            } else if (size == 0) {
                size = end - at;
            }
            if (size < header) return -1;
            int boxEnd = (int) Math.min(end, at + size);
            if (tag(b, at + 4, "moov") || tag(b, at + 4, "trak") || tag(b, at + 4, "mdia")
                    || tag(b, at + 4, "minf") || tag(b, at + 4, "stbl")) {
                if (depth < 6) {
                    int inner = sampleEntry(b, at + header, boxEnd, depth + 1);
                    if (inner >= 0) return inner;
                    if (tag(b, at + 4, "moov") && at + size <= end) moov = true;
                }
            } else if (tag(b, at + 4, "stsd")) {
                int entry = at + header + 8;
                if (entry + 36 <= boxEnd) {
                    for (String audio : AUDIO_ENTRIES) {
                        if (tag(b, entry + 4, audio)) return entry;
                    }
                }
            }
            if (at + size > end) break;
            at += (int) size;
        }
        return moov ? -2 : -1;
    }

    private static AudioFileFormat fileFormat(AudioFileFormat.Type type, AudioFormat format, Map<String, Object> properties) {
        Map<String, Object> props = properties == null ? Collections.<String, Object>emptyMap() : properties;
        return new AudioFileFormat(type, format, AudioSystem.NOT_SPECIFIED, props);
    }

    private static AudioFormat.Encoding encoding(String name) {
        for (AudioFormat.Encoding e : ENCODINGS) {
            if (e.toString().equals(name)) return e;
        }
        return new AudioFormat.Encoding(name);
    }

    static int readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(b, off + total, len - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static int discard(InputStream in, int count) throws IOException {
        byte[] scratch = new byte[8192];
        int done = 0;
        while (done < count) {
            int n = in.read(scratch, 0, Math.min(scratch.length, count - done));
            if (n < 0) break;
            done += n;
        }
        return done;
    }

    private static boolean tag(byte[] b, int at, String expected) {
        if (at < 0 || at + expected.length() > b.length) return false;
        for (int i = 0; i < expected.length(); i++) {
            if (b[at + i] != expected.charAt(i)) return false;
        }
        return true;
    }

    private static int be32(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static int le32(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24);
    }
}
