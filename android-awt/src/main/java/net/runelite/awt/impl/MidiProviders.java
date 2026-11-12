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

import com.sun.media.sound.AudioFileSoundbankReader;
import com.sun.media.sound.DLSSoundbankReader;
import com.sun.media.sound.JARSoundbankReader;
import com.sun.media.sound.RealTimeSequencerProvider;
import com.sun.media.sound.SF2SoundbankReader;
import com.sun.media.sound.SoftProvider;
import com.sun.media.sound.StandardMidiFileReader;
import com.sun.media.sound.StandardMidiFileWriter;

import javax.sound.midi.spi.MidiDeviceProvider;
import javax.sound.midi.spi.MidiFileReader;
import javax.sound.midi.spi.MidiFileWriter;
import javax.sound.midi.spi.SoundbankReader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The MIDI service providers {@code MidiSystem} iterates, fixed at build time, in the order of
 * {@code java.desktop}'s {@code provides} clauses (the JDK's {@code JDK13Services} finds them with
 * {@code ServiceLoader}, which has no registration for the shadow classes in a dexed APK). The
 * hardware port providers ({@code MidiInDeviceProvider}, {@code MidiOutDeviceProvider}) are not
 * offered: Android has no {@code javax.sound} MIDI ports, so the default sequencer is the
 * {@code RealTimeSequencer} and the default synthesizer and receiver are Gervill's
 * {@code SoftSynthesizer}, exactly what a desktop JDK picks on a machine without MIDI hardware.
 * <p>
 * No soundbank ships in the APK (0 bytes): {@code SoftSynthesizer} generates the JDK's emergency
 * soundbank on first use and caches it as {@code .gervill/soundbank-emg.sf2} under
 * {@code user.home}, or under {@code java.io.tmpdir} where ART leaves the home empty.
 * {@code TestMidiContracts} serializes {@code EmergencySoundbank.createSoundbank()} to memory with
 * the same {@code SF2Soundbank.save} the cache uses, prints its size ("emergency soundbank: ...
 * bytes") and requires the JDK's size: 1,878,120 bytes, 129 instruments, on JDK 21. The cache
 * lives on disk, not in the APK.
 * <p>
 * APK size budget: MIDI adds code only; the module has no {@code assets/} directory. Measured on
 * the debug javac output {@code assembleDebug} packs unchanged into the AAR's {@code classes.jar}
 * (a library AAR is not dexed): {@code javax.sound.midi} 57 KB, {@code com.sun.media.sound} 906 KB
 * (Gervill plus the sampled codecs and file readers of the sampled port), 0.97 MB uncompressed.
 * The budget is 1 MB uncompressed for those packages; the app's dex and R8 only shrink it.
 */
public final class MidiProviders {
    private static final List<MidiDeviceProvider> DEVICES = Collections.unmodifiableList(
        Arrays.<MidiDeviceProvider>asList(new RealTimeSequencerProvider(), new SoftProvider()));

    private static final List<MidiFileReader> FILE_READERS =
        Collections.<MidiFileReader>singletonList(new StandardMidiFileReader());

    private static final List<MidiFileWriter> FILE_WRITERS =
        Collections.<MidiFileWriter>singletonList(new StandardMidiFileWriter());

    private static final List<SoundbankReader> SOUNDBANK_READERS = Collections.unmodifiableList(
        Arrays.<SoundbankReader>asList(new AudioFileSoundbankReader(), new DLSSoundbankReader(),
            new JARSoundbankReader(), new SF2SoundbankReader()));

    private MidiProviders() {
    }

    /**
     * The providers of {@code providerClass}, one of the four {@code javax.sound.midi.spi} types;
     * an empty list for any other class, as {@code JDK13Services.getProviders} answers.
     */
    public static List<?> providers(Class<?> providerClass) {
        if (providerClass == MidiDeviceProvider.class) {
            return DEVICES;
        }
        if (providerClass == MidiFileReader.class) {
            return FILE_READERS;
        }
        if (providerClass == MidiFileWriter.class) {
            return FILE_WRITERS;
        }
        if (providerClass == SoundbankReader.class) {
            return SOUNDBANK_READERS;
        }
        return Collections.emptyList();
    }

    /**
     * The provider class part of the default named by the system property
     * {@code typeClass.getName()} ({@code "provider#instance"}, either part optional), or
     * {@code null}. The JDK also reads {@code lib/sound.properties}; there is none on Android.
     */
    public static String defaultProviderClassName(Class<?> typeClass) {
        String value = defaultProvider(typeClass);
        if (value == null) {
            return null;
        }
        int hash = value.indexOf('#');
        if (hash == 0) {
            return null;
        }
        return hash > 0 ? value.substring(0, hash) : value;
    }

    /** The instance name part of the same property, or {@code null}. */
    public static String defaultInstanceName(Class<?> typeClass) {
        String value = defaultProvider(typeClass);
        if (value == null) {
            return null;
        }
        int hash = value.indexOf('#');
        return hash >= 0 && hash < value.length() - 1 ? value.substring(hash + 1) : null;
    }

    private static String defaultProvider(Class<?> typeClass) {
        String value;
        try {
            value = System.getProperty(typeClass.getName());
        } catch (SecurityException e) {
            return null;
        }
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
