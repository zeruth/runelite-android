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

import com.sun.media.sound.AudioFloatFormatConverter;
import com.sun.media.sound.PCMtoPCMCodec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.spi.FormatConversionProvider;

import java.io.IOException;

/**
 * Decodes the streams {@link CompressedAudioFileReader} returns to 16-bit signed PCM at the
 * source's rate and channel count, either byte order, as mp3spi's and vorbisspi's converters
 * offer. Only decoding is provided; with no platform decoder (the host JDK) nothing is offered.
 * When the platform decoder's own output differs from the requested format (float samples, an
 * HE-AAC stream that decodes at twice the rate its header names), the ported
 * {@link AudioFloatFormatConverter} bridges the difference.
 */
public final class CompressedAudioCodec extends FormatConversionProvider {
    private static final Encoding[] PCM = {Encoding.PCM_SIGNED};
    private static final Encoding[] NONE = new Encoding[0];

    @Override
    public Encoding[] getSourceEncodings() {
        return CompressedAudio.canDecode() ? CompressedAudio.ENCODINGS.clone() : NONE;
    }

    @Override
    public Encoding[] getTargetEncodings() {
        return CompressedAudio.canDecode() ? PCM.clone() : NONE;
    }

    @Override
    public Encoding[] getTargetEncodings(AudioFormat sourceFormat) {
        return decodes(sourceFormat) ? PCM.clone() : NONE;
    }

    @Override
    public AudioFormat[] getTargetFormats(Encoding targetEncoding, AudioFormat sourceFormat) {
        if (!Encoding.PCM_SIGNED.equals(targetEncoding) || !decodes(sourceFormat)) {
            return new AudioFormat[0];
        }
        return new AudioFormat[]{pcm(sourceFormat, false), pcm(sourceFormat, true)};
    }

    @Override
    public AudioInputStream getAudioInputStream(Encoding targetEncoding, AudioInputStream sourceStream) {
        if (!isConversionSupported(targetEncoding, sourceStream.getFormat())) {
            throw new IllegalArgumentException("Unsupported conversion: " + targetEncoding + " from "
                + sourceStream.getFormat());
        }
        return getAudioInputStream(pcm(sourceStream.getFormat(), false), sourceStream);
    }

    @Override
    public AudioInputStream getAudioInputStream(AudioFormat targetFormat, AudioInputStream sourceStream) {
        if (!isConversionSupported(targetFormat, sourceStream.getFormat())) {
            throw new IllegalArgumentException("Unsupported conversion: " + targetFormat + " from "
                + sourceStream.getFormat());
        }
        if (!(sourceStream instanceof CompressedAudio.EncodedStream)) {
            // The decoder needs the container, which only the reader's stream carries.
            throw new IllegalArgumentException("Can only decode a stream from AudioSystem.getAudioInputStream: "
                + sourceStream.getFormat());
        }
        AudioInputStream decoded;
        try {
            decoded = ((CompressedAudio.EncodedStream) sourceStream).decode();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot decode " + sourceStream.getFormat() + ": " + e.getMessage(), e);
        }
        AudioFormat actual = decoded.getFormat();
        AudioFormat wanted = complete(targetFormat, actual);
        if (actual.matches(wanted)) {
            return decoded;
        }
        // A byte-order or width change alone stays exact through the integer codec; a rate,
        // channel or float change goes through the float converter, as AudioSystem would pick.
        PCMtoPCMCodec integer = new PCMtoPCMCodec();
        if (integer.isConversionSupported(wanted, actual)) {
            return integer.getAudioInputStream(wanted, decoded);
        }
        return new AudioFloatFormatConverter().getAudioInputStream(wanted, decoded);
    }

    private static boolean decodes(AudioFormat source) {
        return CompressedAudio.isCompressed(source.getEncoding()) && CompressedAudio.canDecode();
    }

    /** 16-bit signed PCM with the source's rate and channels; unknowns stay NOT_SPECIFIED. */
    private static AudioFormat pcm(AudioFormat source, boolean bigEndian) {
        int channels = source.getChannels();
        float rate = source.getSampleRate();
        return new AudioFormat(Encoding.PCM_SIGNED, rate, 16, channels,
            channels == AudioSystem.NOT_SPECIFIED ? AudioSystem.NOT_SPECIFIED : channels * 2, rate, bigEndian);
    }

    /** The requested format with what it left open taken from the decoder's output. */
    private static AudioFormat complete(AudioFormat target, AudioFormat actual) {
        int channels = target.getChannels() == AudioSystem.NOT_SPECIFIED ? actual.getChannels() : target.getChannels();
        float rate = target.getSampleRate() == AudioSystem.NOT_SPECIFIED ? actual.getSampleRate() : target.getSampleRate();
        int bits = target.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED ? 16 : target.getSampleSizeInBits();
        return new AudioFormat(Encoding.PCM_SIGNED, rate, bits, channels, ((bits + 7) / 8) * channels, rate,
            target.isBigEndian());
    }
}
