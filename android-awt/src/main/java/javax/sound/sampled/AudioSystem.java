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
package javax.sound.sampled;

import net.runelite.awt.impl.AndroidMixer;
import net.runelite.awt.impl.AudioFileCodec;
import net.runelite.awt.impl.PcmConverter;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * The JDK's entry point to sampled audio, over one mixer ({@link AndroidMixer}: AudioTrack
 * output, no capture, no ports), the file readers and writers of {@link AudioFileCodec} and
 * the PCM/law conversions of {@link PcmConverter}. Requests nothing supports fail as on the
 * JDK: {@code IllegalArgumentException} for lines and mixers, {@code
 * UnsupportedAudioFileException} for files.
 */
public class AudioSystem {
    public static final int NOT_SPECIFIED = -1;

    private AudioSystem() {
    }

    // Mixers and lines.

    public static Mixer.Info[] getMixerInfo() {
        return new Mixer.Info[]{AndroidMixer.INFO};
    }

    /** The mixer {@code info} names; {@code null} is the default mixer. */
    public static Mixer getMixer(Mixer.Info info) {
        if (info == null || info.equals(AndroidMixer.INFO)) {
            return AndroidMixer.get();
        }
        throw new IllegalArgumentException("Mixer not supported: " + info);
    }

    public static Line.Info[] getSourceLineInfo(Line.Info info) {
        return AndroidMixer.get().getSourceLineInfo(info);
    }

    public static Line.Info[] getTargetLineInfo(Line.Info info) {
        return AndroidMixer.get().getTargetLineInfo(info);
    }

    public static boolean isLineSupported(Line.Info info) {
        return AndroidMixer.get().isLineSupported(info);
    }

    public static Line getLine(Line.Info info) throws LineUnavailableException {
        Mixer mixer = AndroidMixer.get();
        if (!mixer.isLineSupported(info)) {
            throw new IllegalArgumentException("No line matching " + info + " is supported.");
        }
        return mixer.getLine(info);
    }

    public static Clip getClip() throws LineUnavailableException {
        // The format the JDK asks its default mixer for; any PCM layout opens on the clip.
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, NOT_SPECIFIED,
                                             16, 2, 4, NOT_SPECIFIED, true);
        return (Clip) getLine(new DataLine.Info(Clip.class, format));
    }

    public static Clip getClip(Mixer.Info mixerInfo) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, NOT_SPECIFIED,
                                             16, 2, 4, NOT_SPECIFIED, true);
        return (Clip) getMixer(mixerInfo).getLine(new DataLine.Info(Clip.class, format));
    }

    public static SourceDataLine getSourceDataLine(AudioFormat format) throws LineUnavailableException {
        return (SourceDataLine) getLine(new DataLine.Info(SourceDataLine.class, format));
    }

    public static SourceDataLine getSourceDataLine(AudioFormat format, Mixer.Info mixerInfo)
            throws LineUnavailableException {
        return (SourceDataLine) getMixer(mixerInfo).getLine(new DataLine.Info(SourceDataLine.class, format));
    }

    /** Capture is not offered: there is no target line, so this fails as the JDK does without one. */
    public static TargetDataLine getTargetDataLine(AudioFormat format) throws LineUnavailableException {
        return (TargetDataLine) getLine(new DataLine.Info(TargetDataLine.class, format));
    }

    public static TargetDataLine getTargetDataLine(AudioFormat format, Mixer.Info mixerInfo)
            throws LineUnavailableException {
        return (TargetDataLine) getMixer(mixerInfo).getLine(new DataLine.Info(TargetDataLine.class, format));
    }

    // Conversion.

    public static AudioFormat.Encoding[] getTargetEncodings(AudioFormat.Encoding sourceEncoding) {
        if (sourceEncoding == null) throw new NullPointerException("sourceEncoding");
        return PcmConverter.targetEncodings(sourceEncoding);
    }

    public static AudioFormat.Encoding[] getTargetEncodings(AudioFormat sourceFormat) {
        if (sourceFormat == null) throw new NullPointerException("sourceFormat");
        return PcmConverter.targetEncodings(sourceFormat);
    }

    public static boolean isConversionSupported(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat) {
        if (targetEncoding == null || sourceFormat == null) throw new NullPointerException();
        return PcmConverter.isConversionSupported(targetEncoding, sourceFormat);
    }

    public static boolean isConversionSupported(AudioFormat targetFormat, AudioFormat sourceFormat) {
        if (targetFormat == null || sourceFormat == null) throw new NullPointerException();
        return PcmConverter.isConversionSupported(targetFormat, sourceFormat);
    }

    public static AudioFormat[] getTargetFormats(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat) {
        if (targetEncoding == null || sourceFormat == null) throw new NullPointerException();
        return PcmConverter.targetFormats(targetEncoding, sourceFormat);
    }

    public static AudioInputStream getAudioInputStream(AudioFormat.Encoding targetEncoding,
                                                       AudioInputStream sourceStream) {
        if (targetEncoding == null || sourceStream == null) throw new NullPointerException();
        return PcmConverter.convert(targetEncoding, sourceStream);
    }

    public static AudioInputStream getAudioInputStream(AudioFormat targetFormat, AudioInputStream sourceStream) {
        if (targetFormat == null || sourceStream == null) throw new NullPointerException();
        return PcmConverter.convert(targetFormat, sourceStream);
    }

    // Reading files.

    public static AudioFileFormat getAudioFileFormat(InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        if (stream == null) throw new NullPointerException("stream");
        return AudioFileCodec.getAudioFileFormat(stream);
    }

    public static AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException {
        InputStream in = new BufferedInputStream(url.openStream());
        try {
            return AudioFileCodec.getAudioFileFormat(in);
        } finally {
            in.close();
        }
    }

    public static AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        InputStream in = new BufferedInputStream(new FileInputStream(file));
        try {
            return AudioFileCodec.getAudioFileFormat(in);
        } finally {
            in.close();
        }
    }

    public static AudioInputStream getAudioInputStream(InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        if (stream == null) throw new NullPointerException("stream");
        return AudioFileCodec.getAudioInputStream(stream);
    }

    public static AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        return open(new BufferedInputStream(url.openStream()));
    }

    public static AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        return open(new BufferedInputStream(new FileInputStream(file)));
    }

    /** The stream of {@code in}, which is closed when it holds no supported file. */
    private static AudioInputStream open(InputStream in) throws UnsupportedAudioFileException, IOException {
        try {
            return AudioFileCodec.getAudioInputStream(in);
        } catch (UnsupportedAudioFileException | IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }

    // Writing files.

    public static AudioFileFormat.Type[] getAudioFileTypes() {
        return AudioFileCodec.getWritableTypes();
    }

    public static boolean isFileTypeSupported(AudioFileFormat.Type fileType) {
        if (fileType == null) throw new NullPointerException("fileType");
        return AudioFileCodec.isWritable(fileType);
    }

    public static AudioFileFormat.Type[] getAudioFileTypes(AudioInputStream stream) {
        if (stream == null) throw new NullPointerException("stream");
        return AudioFileCodec.getWritableTypes(stream);
    }

    public static boolean isFileTypeSupported(AudioFileFormat.Type fileType, AudioInputStream stream) {
        if (fileType == null || stream == null) throw new NullPointerException();
        return AudioFileCodec.isWritable(fileType, stream);
    }

    public static int write(AudioInputStream stream, AudioFileFormat.Type fileType, OutputStream out)
            throws IOException {
        if (stream == null || fileType == null || out == null) throw new NullPointerException();
        return AudioFileCodec.write(stream, fileType, out);
    }

    public static int write(AudioInputStream stream, AudioFileFormat.Type fileType, File out) throws IOException {
        if (stream == null || fileType == null || out == null) throw new NullPointerException();
        return AudioFileCodec.write(stream, fileType, out);
    }
}
