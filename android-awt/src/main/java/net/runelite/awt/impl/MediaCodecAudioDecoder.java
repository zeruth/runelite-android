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

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * {@link CompressedAudio.Decoder} over MediaExtractor and a synchronous MediaCodec decoder,
 * pulled by the reader of the returned stream: each read feeds compressed samples in and copies
 * decoded PCM out, so a long music file is never held decoded in memory. Loaded by name from
 * {@link CompressedAudio}; {@code consumer-rules.pro} keeps the name and constructor.
 */
public final class MediaCodecAudioDecoder implements CompressedAudio.Decoder {
    private static final long TIMEOUT_US = 10000;
    /** Consecutive empty output polls after end of input before a decoder that never flags EOS is given up on. */
    private static final int DRAIN_POLLS = 300;
    // android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED and ENCODING_PCM_32BIT, API 31.
    private static final int PCM_24BIT_PACKED = 21;
    private static final int PCM_32BIT = 22;

    public MediaCodecAudioDecoder() {
    }

    @Override
    public AudioInputStream decode(File file, boolean temporary, AudioFormat encoded) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        MediaFormat input = null;
        try {
            extractor.setDataSource(file.getPath());
            String mime = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String type = format.getString(MediaFormat.KEY_MIME);
                if (type != null && type.startsWith("audio/")) {
                    extractor.selectTrack(i);
                    input = format;
                    mime = type;
                    break;
                }
            }
            if (input == null) {
                throw new IOException("No audio track in the " + encoded.getEncoding() + " data");
            }
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(input, null, null, 0);
            codec.start();
        } catch (IOException | RuntimeException e) {
            if (codec != null) {
                try { codec.release(); } catch (RuntimeException ignored) {}
            }
            extractor.release();
            if (temporary) file.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Cannot decode " + encoded.getEncoding() + ": " + e, e);
        }
        // From here the pump owns the codec, the extractor and the temporary file.
        Pump pump = new Pump(extractor, codec, temporary ? file : null);
        try {
            return new AudioInputStream(pump, pump.prime(input), AudioSystem.NOT_SPECIFIED);
        } catch (IOException | RuntimeException e) {
            pump.close();
            throw e;
        }
    }

    /** The decoder's PCM as an InputStream; MediaCodec is driven on the reading thread. */
    private static final class Pump extends InputStream {
        private final MediaExtractor extractor;
        private final MediaCodec codec;
        private final File temporary;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private MediaFormat output;
        private byte[] chunk = new byte[0];
        private int chunkPos, chunkLen;
        private boolean inputDone, outputDone, closed;
        private int idlePolls;

        Pump(MediaExtractor extractor, MediaCodec codec, File temporary) {
            this.extractor = extractor;
            this.codec = codec;
            this.temporary = temporary;
        }

        /** Decodes until the output format is known, which the AudioInputStream must report up front. */
        AudioFormat prime(MediaFormat input) throws IOException {
            while (output == null && !outputDone) {
                step();
            }
            MediaFormat format = output != null ? output : input;
            int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                ? format.getInteger(MediaFormat.KEY_PCM_ENCODING) : android.media.AudioFormat.ENCODING_PCM_16BIT;
            switch (encoding) {
                case android.media.AudioFormat.ENCODING_PCM_8BIT:
                    return new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, rate, 8, channels, channels, rate, false);
                case android.media.AudioFormat.ENCODING_PCM_FLOAT:
                    return new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, rate, 32, channels, 4 * channels, rate, false);
                case PCM_24BIT_PACKED:
                    return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 24, channels, 3 * channels, rate, false);
                case PCM_32BIT:
                    return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 32, channels, 4 * channels, rate, false);
                case android.media.AudioFormat.ENCODING_PCM_16BIT:
                    return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, 2 * channels, rate, false);
                default:
                    throw new IOException("Decoder produced unsupported PCM encoding " + encoding);
            }
        }

        private void step() throws IOException {
            try {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (in >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(in);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    output = codec.getOutputFormat();
                } else if (out >= 0) {
                    idlePolls = 0;
                    if (info.size > 0) {
                        ByteBuffer buffer = codec.getOutputBuffer(out);
                        if (buffer != null) {
                            if (chunk.length < info.size) chunk = new byte[info.size];
                            buffer.limit(info.offset + info.size);
                            buffer.position(info.offset);
                            buffer.get(chunk, 0, info.size);
                            chunkPos = 0;
                            chunkLen = info.size;
                        }
                        if (output == null) output = codec.getOutputFormat();
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                } else if (out == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone && ++idlePolls > DRAIN_POLLS) {
                    outputDone = true;
                }
            } catch (RuntimeException e) {
                // CodecException and the extractor's IllegalArgumentException reach the reader as I/O failures.
                throw new IOException("MediaCodec failed: " + e, e);
            }
        }

        private boolean fill() throws IOException {
            if (closed) throw new IOException("Stream closed");
            while (chunkPos >= chunkLen) {
                if (outputDone) return false;
                step();
            }
            return true;
        }

        @Override
        public int read() throws IOException {
            return fill() ? chunk[chunkPos++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (!fill()) return -1;
            int n = Math.min(len, chunkLen - chunkPos);
            System.arraycopy(chunk, chunkPos, b, off, n);
            chunkPos += n;
            return n;
        }

        @Override
        public int available() {
            return closed ? 0 : chunkLen - chunkPos;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try { codec.stop(); } catch (RuntimeException ignored) {}
            try { codec.release(); } catch (RuntimeException ignored) {}
            extractor.release();
            if (temporary != null) temporary.delete();
        }
    }
}
