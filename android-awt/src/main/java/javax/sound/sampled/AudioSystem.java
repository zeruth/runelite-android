/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.sound.sampled;

import net.runelite.awt.impl.AndroidMixer;
import net.runelite.awt.impl.SampledProviders;
import net.runelite.awt.impl.WavDecoder;

import javax.sound.sampled.spi.AudioFileReader;
import javax.sound.sampled.spi.AudioFileWriter;
import javax.sound.sampled.spi.FormatConversionProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The JDK's entry point to sampled audio, over one mixer ({@link AndroidMixer}) and the ported
 * {@code com.sun.media.sound} file readers, file writers and format converters that
 * {@link SampledProviders} lists in place of the JDK's {@code ServiceLoader} lookup. The
 * conversion and file methods walk the providers exactly as the JDK does, so the provider that
 * claims a stream, and the stream it returns, are the JDK's.
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

    public static TargetDataLine getTargetDataLine(AudioFormat format) throws LineUnavailableException {
        return (TargetDataLine) getLine(new DataLine.Info(TargetDataLine.class, format));
    }

    public static TargetDataLine getTargetDataLine(AudioFormat format, Mixer.Info mixerInfo)
            throws LineUnavailableException {
        return (TargetDataLine) getMixer(mixerInfo).getLine(new DataLine.Info(TargetDataLine.class, format));
    }

    // Conversion.

    public static AudioFormat.Encoding[] getTargetEncodings(AudioFormat.Encoding sourceEncoding) {
        Objects.requireNonNull(sourceEncoding);
        List<AudioFormat.Encoding> encodings = new ArrayList<AudioFormat.Encoding>();
        for (FormatConversionProvider codec : SampledProviders.converters()) {
            if (codec.isSourceEncodingSupported(sourceEncoding)) {
                Collections.addAll(encodings, codec.getTargetEncodings());
            }
        }
        if (!encodings.contains(sourceEncoding)) {
            encodings.add(sourceEncoding);
        }
        return encodings.toArray(new AudioFormat.Encoding[encodings.size()]);
    }

    public static AudioFormat.Encoding[] getTargetEncodings(AudioFormat sourceFormat) {
        Objects.requireNonNull(sourceFormat);
        List<AudioFormat.Encoding> encodings = new ArrayList<AudioFormat.Encoding>();
        for (FormatConversionProvider codec : SampledProviders.converters()) {
            Collections.addAll(encodings, codec.getTargetEncodings(sourceFormat));
        }
        if (!encodings.contains(sourceFormat.getEncoding())) {
            encodings.add(sourceFormat.getEncoding());
        }
        return encodings.toArray(new AudioFormat.Encoding[encodings.size()]);
    }

    public static boolean isConversionSupported(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat) {
        Objects.requireNonNull(targetEncoding);
        Objects.requireNonNull(sourceFormat);
        if (sourceFormat.getEncoding().equals(targetEncoding)) {
            return true;
        }
        for (FormatConversionProvider codec : SampledProviders.converters()) {
            if (codec.isConversionSupported(targetEncoding, sourceFormat)) {
                return true;
            }
        }
        return false;
    }

    public static AudioInputStream getAudioInputStream(AudioFormat.Encoding targetEncoding,
                                                       AudioInputStream sourceStream) {
        Objects.requireNonNull(targetEncoding);
        Objects.requireNonNull(sourceStream);
        if (sourceStream.getFormat().getEncoding().equals(targetEncoding)) {
            return sourceStream;
        }
        for (FormatConversionProvider codec : SampledProviders.converters()) {
            if (codec.isConversionSupported(targetEncoding, sourceStream.getFormat())) {
                return codec.getAudioInputStream(targetEncoding, sourceStream);
            }
        }
        throw new IllegalArgumentException("Unsupported conversion: " + targetEncoding
                                           + " from " + sourceStream.getFormat());
    }

    public static AudioFormat[] getTargetFormats(AudioFormat.Encoding targetEncoding, AudioFormat sourceFormat) {
        Objects.requireNonNull(targetEncoding);
        Objects.requireNonNull(sourceFormat);
        List<AudioFormat> formats = new ArrayList<AudioFormat>();
        boolean matchFound = false;
        for (FormatConversionProvider codec : SampledProviders.converters()) {
            for (AudioFormat format : codec.getTargetFormats(targetEncoding, sourceFormat)) {
                formats.add(format);
                if (sourceFormat.matches(format)) {
                    matchFound = true;
                }
            }
        }
        if (targetEncoding.equals(sourceFormat.getEncoding()) && !matchFound) {
            formats.add(sourceFormat);
        }
        return formats.toArray(new AudioFormat[formats.size()]);
    }

    public static boolean isConversionSupported(AudioFormat targetFormat, AudioFormat sourceFormat) {
        Objects.requireNonNull(targetFormat);
        Objects.requireNonNull(sourceFormat);
        if (sourceFormat.matches(targetFormat)) {
            return true;
        }
        for (FormatConversionProvider codec : SampledProviders.converters()) {
            if (codec.isConversionSupported(targetFormat, sourceFormat)) {
                return true;
            }
        }
        return false;
    }

    public static AudioInputStream getAudioInputStream(AudioFormat targetFormat, AudioInputStream sourceStream) {
        if (sourceStream.getFormat().matches(targetFormat)) {
            return sourceStream;
        }
        for (FormatConversionProvider codec : SampledProviders.converters()) {
            if (codec.isConversionSupported(targetFormat, sourceStream.getFormat())) {
                return codec.getAudioInputStream(targetFormat, sourceStream);
            }
        }
        throw new IllegalArgumentException("Unsupported conversion: " + targetFormat
                                           + " from " + sourceStream.getFormat());
    }

    // Reading files.

    public static AudioFileFormat getAudioFileFormat(InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(stream);
        for (AudioFileReader reader : SampledProviders.readers()) {
            try {
                return reader.getAudioFileFormat(stream);
            } catch (UnsupportedAudioFileException ignored) {
                // the next reader may know the container
            }
        }
        throw new UnsupportedAudioFileException("Stream of unsupported format");
    }

    public static AudioFileFormat getAudioFileFormat(URL url) throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(url);
        for (AudioFileReader reader : SampledProviders.readers()) {
            try {
                return reader.getAudioFileFormat(url);
            } catch (UnsupportedAudioFileException ignored) {
                // the next reader may know the container
            }
        }
        throw new UnsupportedAudioFileException("URL of unsupported format");
    }

    public static AudioFileFormat getAudioFileFormat(File file) throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(file);
        for (AudioFileReader reader : SampledProviders.readers()) {
            try {
                return reader.getAudioFileFormat(file);
            } catch (UnsupportedAudioFileException ignored) {
                // the next reader may know the container
            }
        }
        throw new UnsupportedAudioFileException("File of unsupported format");
    }

    /**
     * A RIFF/WAVE stream (RuneLite's notification sounds, most plugin clips) goes straight to the
     * WAVE readers through {@link WavDecoder}; anything else takes the JDK's provider walk.
     */
    public static AudioInputStream getAudioInputStream(InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(stream);
        if (WavDecoder.isWave(stream)) {
            return WavDecoder.decode(stream);
        }
        for (AudioFileReader reader : SampledProviders.readers()) {
            try {
                return reader.getAudioInputStream(stream);
            } catch (UnsupportedAudioFileException ignored) {
                // the next reader may know the container
            }
        }
        throw new UnsupportedAudioFileException("Stream of unsupported format");
    }

    public static AudioInputStream getAudioInputStream(URL url) throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(url);
        for (AudioFileReader reader : SampledProviders.readers()) {
            try {
                return reader.getAudioInputStream(url);
            } catch (UnsupportedAudioFileException ignored) {
                // the next reader may know the container
            }
        }
        throw new UnsupportedAudioFileException("URL of unsupported format");
    }

    public static AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        Objects.requireNonNull(file);
        for (AudioFileReader reader : SampledProviders.readers()) {
            try {
                return reader.getAudioInputStream(file);
            } catch (UnsupportedAudioFileException ignored) {
                // the next reader may know the container
            }
        }
        throw new UnsupportedAudioFileException("File of unsupported format");
    }

    // Writing files.

    public static AudioFileFormat.Type[] getAudioFileTypes() {
        Set<AudioFileFormat.Type> types = new HashSet<AudioFileFormat.Type>();
        for (AudioFileWriter writer : SampledProviders.writers()) {
            Collections.addAll(types, writer.getAudioFileTypes());
        }
        return types.toArray(new AudioFileFormat.Type[0]);
    }

    public static boolean isFileTypeSupported(AudioFileFormat.Type fileType) {
        Objects.requireNonNull(fileType);
        for (AudioFileWriter writer : SampledProviders.writers()) {
            if (writer.isFileTypeSupported(fileType)) {
                return true;
            }
        }
        return false;
    }

    public static AudioFileFormat.Type[] getAudioFileTypes(AudioInputStream stream) {
        Objects.requireNonNull(stream);
        Set<AudioFileFormat.Type> types = new HashSet<AudioFileFormat.Type>();
        for (AudioFileWriter writer : SampledProviders.writers()) {
            Collections.addAll(types, writer.getAudioFileTypes(stream));
        }
        return types.toArray(new AudioFileFormat.Type[0]);
    }

    public static boolean isFileTypeSupported(AudioFileFormat.Type fileType, AudioInputStream stream) {
        Objects.requireNonNull(fileType);
        Objects.requireNonNull(stream);
        for (AudioFileWriter writer : SampledProviders.writers()) {
            if (writer.isFileTypeSupported(fileType, stream)) {
                return true;
            }
        }
        return false;
    }

    public static int write(AudioInputStream stream, AudioFileFormat.Type fileType, OutputStream out)
            throws IOException {
        Objects.requireNonNull(stream);
        Objects.requireNonNull(fileType);
        Objects.requireNonNull(out);
        for (AudioFileWriter writer : SampledProviders.writers()) {
            try {
                return writer.write(stream, fileType, out);
            } catch (IllegalArgumentException ignored) {
                // this provider cannot write the stream; try the next
            }
        }
        throw new IllegalArgumentException("could not write audio file: file type not supported: " + fileType);
    }

    public static int write(AudioInputStream stream, AudioFileFormat.Type fileType, File out) throws IOException {
        Objects.requireNonNull(stream);
        Objects.requireNonNull(fileType);
        Objects.requireNonNull(out);
        for (AudioFileWriter writer : SampledProviders.writers()) {
            try {
                return writer.write(stream, fileType, out);
            } catch (IllegalArgumentException ignored) {
                // this provider cannot write the stream; try the next
            }
        }
        throw new IllegalArgumentException("could not write audio file: file type not supported: " + fileType);
    }
}
