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

import com.sun.media.sound.AiffFileReader;
import com.sun.media.sound.AiffFileWriter;
import com.sun.media.sound.AlawCodec;
import com.sun.media.sound.AuFileReader;
import com.sun.media.sound.AuFileWriter;
import com.sun.media.sound.AudioFloatFormatConverter;
import com.sun.media.sound.PCMtoPCMCodec;
import com.sun.media.sound.SoftMidiAudioFileReader;
import com.sun.media.sound.UlawCodec;
import com.sun.media.sound.WaveExtensibleFileReader;
import com.sun.media.sound.WaveFileReader;
import com.sun.media.sound.WaveFileWriter;
import com.sun.media.sound.WaveFloatFileReader;
import com.sun.media.sound.WaveFloatFileWriter;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.AudioFileWriter;
import javax.sound.sampled.spi.FormatConversionProvider;
import javax.sound.sampled.spi.MixerProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The sampled-audio service providers {@code AudioSystem} iterates, fixed at build time. The JDK
 * finds them with {@code ServiceLoader} over {@code java.desktop}'s {@code provides} clauses; a
 * dexed APK has no such registration for the shadow classes, so the lists are spelled out here, in
 * the JDK's declaration order (the order decides which reader claims a stream first), including
 * Gervill's MIDI reader, which renders a standard MIDI file to PCM through the software synthesizer.
 * The compressed-audio reader and decoder come last, where a desktop classpath's mp3spi/vorbisspi
 * providers land.
 */
public final class SampledProviders {
    private static final List<AudioFileReader> READERS = Collections.unmodifiableList(Arrays.<AudioFileReader>asList(
        new AiffFileReader(), new AuFileReader(), new SoftMidiAudioFileReader(),
        new WaveFileReader(), new WaveFloatFileReader(), new WaveExtensibleFileReader(),
        new CompressedAudioFileReader()));

    /** The WAVE readers alone, for {@link WavDecoder}'s fast path; disjoint by WAVE format tag. */
    private static final List<AudioFileReader> WAVE_READERS = Collections.unmodifiableList(Arrays.<AudioFileReader>asList(
        READERS.get(3), READERS.get(4), READERS.get(5)));

    private static final List<AudioFileWriter> WRITERS = Collections.unmodifiableList(Arrays.<AudioFileWriter>asList(
        new AiffFileWriter(), new AuFileWriter(), new WaveFileWriter(), new WaveFloatFileWriter()));

    // Match desktop provider priority; AudioSystem uses the first matching codec.
    private static final List<FormatConversionProvider> CONVERTERS = Collections.unmodifiableList(
        Arrays.<FormatConversionProvider>asList(
            new UlawCodec(), new PCMtoPCMCodec(), new AudioFloatFormatConverter(), new AlawCodec(),
            new CompressedAudioCodec()));

    private static final List<MixerProvider> MIXERS = Collections.<MixerProvider>singletonList(new AndroidMixerProvider());

    private SampledProviders() {
    }

    public static List<AudioFileReader> readers() {
        return READERS;
    }

    static List<AudioFileReader> waveReaders() {
        return WAVE_READERS;
    }

    public static List<AudioFileWriter> writers() {
        return WRITERS;
    }

    public static List<FormatConversionProvider> converters() {
        return CONVERTERS;
    }

    public static List<MixerProvider> mixers() {
        return MIXERS;
    }

    /** Offers the one {@link AndroidMixer}. */
    private static final class AndroidMixerProvider extends MixerProvider {
        @Override
        public Mixer.Info[] getMixerInfo() {
            return new Mixer.Info[]{AndroidMixer.INFO};
        }

        @Override
        public Mixer getMixer(Mixer.Info info) {
            if (info == null || AndroidMixer.INFO.equals(info)) {
                return AndroidMixer.get();
            }
            throw new IllegalArgumentException("Mixer not supported: " + info);
        }
    }
}
