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
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Reads MP3, Ogg (Vorbis, Opus, FLAC), AAC (ADTS and MP4/M4A) and FLAC files the way mp3spi and
 * vorbisspi do on the desktop: the stream returned is the encoded file, its format the codec's
 * ({@code MPEG1L3}, {@code VORBISENC}, ...) with an unspecified frame size and length, and
 * {@link CompressedAudioCodec} turns it into PCM. Listed after the JDK's readers, as a provider jar
 * on the desktop classpath is.
 */
public final class CompressedAudioFileReader extends AudioFileReader {

    @Override
    public AudioFileFormat getAudioFileFormat(InputStream stream) throws UnsupportedAudioFileException, IOException {
        return require(CompressedAudio.sniff(stream));
    }

    @Override
    public AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream in = new BufferedInputStream(url.openStream());
        try {
            return require(CompressedAudio.sniff(in));
        } finally {
            in.close();
        }
    }

    @Override
    public AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            return require(CompressedAudio.sniff(in));
        } finally {
            in.close();
        }
    }

    /** The stream is left at the container's first byte, which is where the decoder starts. */
    @Override
    public AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException {
        AudioFileFormat format = require(CompressedAudio.sniff(stream));
        return new CompressedAudio.EncodedStream(stream, format, null, false);
    }

    @Override
    public AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        File file = fileOf(url);
        if (file != null) {
            return getAudioInputStream(file);
        }
        InputStream in = new BufferedInputStream(url.openStream());
        try {
            AudioFileFormat format = require(CompressedAudio.sniff(in));
            return new CompressedAudio.EncodedStream(in, format, null, true);
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            AudioFileFormat format = require(CompressedAudio.sniff(in));
            return new CompressedAudio.EncodedStream(in, format, file, true);
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    private static AudioFileFormat require(AudioFileFormat format) throws UnsupportedAudioFileException {
        if (format == null) {
            throw new UnsupportedAudioFileException("Not an MP3, Ogg, AAC, MP4 or FLAC stream");
        }
        return format;
    }

    /** A {@code file:} URL as a File, so the decoder reads it in place instead of spooling a copy. */
    private static File fileOf(URL url) {
        if (!"file".equalsIgnoreCase(url.getProtocol())) {
            return null;
        }
        try {
            File file = new File(url.toURI());
            return file.isFile() ? file : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }
}
