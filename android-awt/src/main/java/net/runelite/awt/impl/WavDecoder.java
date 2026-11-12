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

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The WAVE fast path of {@link AudioSystem#getAudioInputStream(InputStream)}. WAV is what
 * RuneLite's notification sounds and most plugins load (AudioPlayer → getAudioInputStream →
 * Clip), so a stream that starts with a RIFF/WAVE header goes straight to the ported WAVE
 * readers (integer PCM and G.711, IEEE float, WAVE_FORMAT_EXTENSIBLE) instead of being parsed
 * by the AIFF and AU readers first. Which reader accepts it, and the stream it returns, are
 * the same as the full provider walk, because the JDK's readers ahead of them reject any
 * RIFF stream.
 */
public final class WavDecoder {
    private static final int SNIFF = 12;

    private WavDecoder() {
    }

    /**
     * True when the next bytes of {@code in} are a RIFF/WAVE header; the stream is left where it
     * was. A stream without mark/reset is never sniffed.
     */
    public static boolean isWave(InputStream in) throws IOException {
        if (!in.markSupported()) return false;
        byte[] head = new byte[SNIFF];
        int count = 0;
        in.mark(SNIFF);
        try {
            while (count < SNIFF) {
                int n = in.read(head, count, SNIFF - count);
                if (n < 0) break;
                count += n;
            }
        } finally {
            in.reset();
        }
        return count == SNIFF && tag(head, 0, "RIFF") && tag(head, 8, "WAVE");
    }

    /** The samples of the WAVE file at the start of {@code in}, read by the ported WAVE readers. */
    public static AudioInputStream decode(InputStream in) throws UnsupportedAudioFileException, IOException {
        for (AudioFileReader reader : SampledProviders.waveReaders()) {
            try {
                return reader.getAudioInputStream(in);
            } catch (UnsupportedAudioFileException ignored) {
                // another WAVE format tag; the next reader may take it
            }
        }
        throw new UnsupportedAudioFileException("Stream of unsupported format");
    }

    private static boolean tag(byte[] data, int offset, String expected) {
        for (int i = 0; i < 4; i++) {
            if (data[offset + i] != expected.charAt(i)) return false;
        }
        return true;
    }

    static final class Bytes {
        static byte[] readAll(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
