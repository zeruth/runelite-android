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

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one mixer {@link AudioSystem} reports: AudioTrack output, offering streamed
 * {@link SourceDataLine}s (the pooled lines of {@link AndroidAudioBridge}) and static {@link Clip}s.
 * It has no target lines and no ports; capture is not part of this mixer.
 */
public final class AndroidMixer implements Mixer {
    public static final Mixer.Info INFO = new MixerInfo();
    private static final AndroidMixer INSTANCE = new AndroidMixer();
    /** A stream line nobody asked a buffer size for gets the size the old bridge default gave it. */
    private static final int DEFAULT_BUFFER = 4096;

    private final Line.Info[] sourceInfo = sourceInfo();
    private final Map<Line, Boolean> created = Collections.synchronizedMap(new WeakHashMap<Line, Boolean>());
    private final List<LineListener> listeners = new CopyOnWriteArrayList<LineListener>();
    private volatile boolean open;

    private AndroidMixer() {
    }

    public static AndroidMixer get() {
        return INSTANCE;
    }

    private static final class MixerInfo extends Mixer.Info {
        MixerInfo() {
            super("Android AudioTrack", "runelite-android", "Android AudioTrack output mixer", "1.0");
        }
    }

    private static Line.Info[] sourceInfo() {
        List<AudioFormat> stream = new ArrayList<AudioFormat>();
        for (int channels = 1; channels <= 2; channels++) {
            stream.add(pcm(AudioFormat.Encoding.PCM_UNSIGNED, 8, channels, false));
            stream.add(pcm(AudioFormat.Encoding.PCM_SIGNED, 16, channels, false));
            stream.add(pcm(AudioFormat.Encoding.PCM_FLOAT, 32, channels, false));
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                stream.add(pcm(AudioFormat.Encoding.PCM_SIGNED, 24, channels, false));
                stream.add(pcm(AudioFormat.Encoding.PCM_SIGNED, 32, channels, false));
            }
        }
        // A clip converts any integer or float PCM to 16-bit on open, keeping the first two channels.
        List<AudioFormat> clip = new ArrayList<AudioFormat>();
        for (boolean big : new boolean[]{false, true}) {
            clip.add(pcm(AudioFormat.Encoding.PCM_SIGNED, 8, AudioSystem.NOT_SPECIFIED, big));
            clip.add(pcm(AudioFormat.Encoding.PCM_UNSIGNED, 8, AudioSystem.NOT_SPECIFIED, big));
            for (int bits = 16; bits <= 32; bits += 8) {
                clip.add(pcm(AudioFormat.Encoding.PCM_SIGNED, bits, AudioSystem.NOT_SPECIFIED, big));
                clip.add(pcm(AudioFormat.Encoding.PCM_UNSIGNED, bits, AudioSystem.NOT_SPECIFIED, big));
            }
            clip.add(pcm(AudioFormat.Encoding.PCM_FLOAT, 32, AudioSystem.NOT_SPECIFIED, big));
            clip.add(pcm(AudioFormat.Encoding.PCM_FLOAT, 64, AudioSystem.NOT_SPECIFIED, big));
        }
        return new Line.Info[]{
            new DataLine.Info(SourceDataLine.class, stream.toArray(new AudioFormat[0]),
                AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED),
            new DataLine.Info(Clip.class, clip.toArray(new AudioFormat[0]),
                AudioSystem.NOT_SPECIFIED, AudioSystem.NOT_SPECIFIED),
        };
    }

    private static AudioFormat pcm(AudioFormat.Encoding encoding, int bits, int channels, boolean bigEndian) {
        int frameSize = channels == AudioSystem.NOT_SPECIFIED ? AudioSystem.NOT_SPECIFIED : channels * ((bits + 7) / 8);
        return new AudioFormat(encoding, AudioSystem.NOT_SPECIFIED, bits, channels, frameSize,
            AudioSystem.NOT_SPECIFIED, bigEndian);
    }

    // Mixer.

    @Override public Mixer.Info getMixerInfo() { return INFO; }
    @Override public Line.Info[] getSourceLineInfo() { return sourceInfo.clone(); }
    @Override public Line.Info[] getTargetLineInfo() { return new Line.Info[0]; }

    @Override
    public Line.Info[] getSourceLineInfo(Line.Info info) {
        List<Line.Info> result = new ArrayList<Line.Info>();
        for (Line.Info supported : sourceInfo) {
            if (info.matches(supported)) result.add(supported);
        }
        return result.toArray(new Line.Info[0]);
    }

    @Override public Line.Info[] getTargetLineInfo(Line.Info info) { return new Line.Info[0]; }

    @Override
    public boolean isLineSupported(Line.Info info) {
        return supported(info) != null;
    }

    private Line.Info supported(Line.Info info) {
        if (info == null) return null;
        for (Line.Info candidate : sourceInfo) {
            if (info.matches(candidate)) return candidate;
        }
        return null;
    }

    @Override
    public Line getLine(Line.Info info) throws LineUnavailableException {
        Line.Info match = supported(info);
        if (match == null) throw new IllegalArgumentException("Line unsupported: " + info);
        Line line;
        if (match.getLineClass() == Clip.class) {
            line = new AndroidClip();
        } else {
            AudioFormat format = null;
            int bufferSize = DEFAULT_BUFFER;
            if (info instanceof DataLine.Info) {
                DataLine.Info request = (DataLine.Info) info;
                AudioFormat[] formats = request.getFormats();
                if (formats.length > 0) format = concrete(formats[0]);
                if (request.getMaxBufferSize() > 0) bufferSize = request.getMaxBufferSize();
            }
            if (format == null) format = new AudioFormat(44100f, 16, 2, true, false);
            // A buffer must hold whole frames for open() to accept it.
            bufferSize = Math.max(format.getFrameSize(), bufferSize - bufferSize % format.getFrameSize());
            line = AndroidAudioBridge.openSourceDataLine(format, bufferSize);
        }
        created.put(line, Boolean.TRUE);
        return line;
    }

    /** {@code requested} with each NOT_SPECIFIED field filled from 44.1 kHz 16-bit stereo. */
    private static AudioFormat concrete(AudioFormat requested) {
        if (requested == null) return null;
        float rate = requested.getSampleRate() == AudioSystem.NOT_SPECIFIED ? 44100f : requested.getSampleRate();
        int bits = requested.getSampleSizeInBits() == AudioSystem.NOT_SPECIFIED ? 16 : requested.getSampleSizeInBits();
        int channels = requested.getChannels() == AudioSystem.NOT_SPECIFIED ? 2 : requested.getChannels();
        int frameSize = requested.getFrameSize() == AudioSystem.NOT_SPECIFIED ? channels * ((bits + 7) / 8) : requested.getFrameSize();
        float frameRate = requested.getFrameRate() == AudioSystem.NOT_SPECIFIED ? rate : requested.getFrameRate();
        return new AudioFormat(requested.getEncoding(), rate, bits, channels, frameSize, frameRate,
            requested.isBigEndian(), requested.properties());
    }

    /** AudioTrack instances are bounded only by the platform's shared track pool, which it does not report. */
    @Override
    public int getMaxLines(Line.Info info) {
        return isLineSupported(info) ? AudioSystem.NOT_SPECIFIED : 0;
    }

    @Override
    public Line[] getSourceLines() {
        List<Line> lines = new ArrayList<Line>();
        synchronized (created) {
            for (Line line : created.keySet()) {
                if (line.isOpen()) lines.add(line);
            }
        }
        return lines.toArray(new Line[0]);
    }

    @Override public Line[] getTargetLines() { return new Line[0]; }

    @Override
    public void synchronize(Line[] lines, boolean maintainSync) {
        throw new IllegalArgumentException("Synchronization not supported by this mixer.");
    }

    @Override
    public void unsynchronize(Line[] lines) {
        throw new IllegalArgumentException("Synchronization not supported by this mixer.");
    }

    @Override public boolean isSynchronizationSupported(Line[] lines, boolean maintainSync) { return false; }

    // Line: the mixer itself only tracks open/closed; its lines work either way, as a JDK mixer's do.

    @Override public Line.Info getLineInfo() { return new Line.Info(Mixer.class); }

    @Override
    public void open() {
        if (open) return;
        open = true;
        fire(LineEvent.Type.OPEN);
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        fire(LineEvent.Type.CLOSE);
    }

    @Override public boolean isOpen() { return open; }
    @Override public Control[] getControls() { return new Control[0]; }
    @Override public boolean isControlSupported(Control.Type control) { return false; }

    @Override
    public Control getControl(Control.Type control) {
        throw new IllegalArgumentException("Unsupported control type: " + control);
    }

    @Override public void addLineListener(LineListener listener) { if (listener != null) listeners.add(listener); }
    @Override public void removeLineListener(LineListener listener) { listeners.remove(listener); }

    private void fire(LineEvent.Type type) {
        LineEvent event = new LineEvent(this, type, AudioSystem.NOT_SPECIFIED);
        for (LineListener listener : listeners) listener.update(event);
    }
}
