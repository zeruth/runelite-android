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

import android.media.AudioAttributes;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.HandlerThread;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link Clip} on a per-clip MODE_STATIC {@link AudioTrack}, so short sounds mix over the
 * game's streamed audio without touching {@link AndroidAudioBridge}'s shared music track.
 *
 * What callers rely on, and why it is implemented the way it is:
 *  - RuneLite's AudioPlayer opens a Clip per sound, starts it, and on the STOP event
 *    closes the previous clip — so STOP must fire when playback runs out, not only on
 *    stop(). A notification marker at the last frame does that.
 *  - MASTER_GAIN is set in dB; AudioTrack takes linear volume, capped at unity.
 *  - The PCM is normalised to 16-bit signed little-endian, ≤ 2 channels: it plays on every
 *    API level we support, while 24/32-bit PCM needs API 31.
 * Line events are delivered on a dedicated thread, as the JDK does from its own.
 */
public final class AndroidClip implements Clip {
    private static final Handler EVENTS;

    static {
        HandlerThread thread = new HandlerThread("RL-Clip-Events");
        thread.setDaemon(true);
        thread.start();
        EVENTS = new Handler(thread.getLooper());
    }

    /**
     * Open clips, held strongly until close(). The JDK's mixer references every open line;
     * here nothing else does once AudioPlayer.play returns, so the clip was collected
     * mid-sound and AudioTrack's finalizer released it — C Engineer's voice lines cut off
     * after a fraction of a second.
     */
    private static final java.util.Set<AndroidClip> OPEN =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private final List<LineListener> listeners = new CopyOnWriteArrayList<>();
    private final MasterGain gain = new MasterGain();
    private final Object lock = new Object();

    private AudioTrack track;
    private AudioFormat format;
    private int frames;
    private int sampleRate;
    private int position;
    private int loopStart, loopEnd = -1;
    private boolean running;
    private int session; // bumped on every start/stop so a stale end-of-clip callback is ignored

    @Override
    public void open(AudioInputStream stream) throws LineUnavailableException, IOException {
        byte[] data = WavDecoder.Bytes.readAll(stream);
        open(stream.getFormat(), data, 0, data.length);
    }

    @Override
    public void open(AudioFormat fmt, byte[] data, int offset, int bufferSize) throws LineUnavailableException {
        synchronized (lock) {
            if (track != null) throw new IllegalStateException("Clip is already open");
            byte[] pcm = toPcm16(fmt, data, offset, bufferSize);
            int channels = Math.min(2, fmt.getChannels());
            sampleRate = Math.round(fmt.getSampleRate());
            frames = pcm.length / (2 * channels);
            format = new AudioFormat(sampleRate, 16, channels, true, false);
            if (frames == 0) pcm = new byte[2 * channels]; // AudioTrack refuses a zero-sized static buffer
            try {
                AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(new android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channels == 1 ? android.media.AudioFormat.CHANNEL_OUT_MONO
                            : android.media.AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                    .setBufferSizeInBytes(pcm.length)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
                if (t.write(pcm, 0, pcm.length) < 0) {
                    t.release();
                    throw new LineUnavailableException("AudioTrack rejected clip data");
                }
                t.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                    @Override public void onMarkerReached(AudioTrack at) { ended(at); }
                    @Override public void onPeriodicNotification(AudioTrack at) {}
                }, EVENTS);
                track = t;
                OPEN.add(this);
                gain.apply();
            } catch (LineUnavailableException e) {
                throw e;
            } catch (Exception e) {
                throw new LineUnavailableException("AudioTrack init failed: " + e.getMessage());
            }
        }
        fire(LineEvent.Type.OPEN, 0);
    }

    @Override
    public void open() throws LineUnavailableException {
        throw new LineUnavailableException("A Clip has to be opened with its audio data");
    }

    @Override public void start() { play(0); }

    @Override
    public void loop(int count) {
        play(count == LOOP_CONTINUOUSLY ? -1 : Math.max(0, count));
    }

    private void play(int loops) {
        int at;
        synchronized (lock) {
            if (track == null || running) return;
            at = position;
            if (at >= frames && loops == 0) {
                // Nothing left to play: report the start and immediate stop, as the JDK does.
                running = true;
                session++;
            } else {
                try {
                    track.stop();
                    int end = loopEnd < 0 ? frames : Math.min(loopEnd + 1, frames);
                    if (loops != 0 && end > loopStart) track.setLoopPoints(loopStart, end, loops);
                    else track.setLoopPoints(0, 0, 0);
                    if (track.getPlaybackHeadPosition() != at) {
                        track.reloadStaticData();
                        track.setPlaybackHeadPosition(at);
                    }
                    track.setNotificationMarkerPosition(loops == 0 ? frames : 0);
                    track.play();
                } catch (IllegalStateException e) {
                    return;
                }
                running = true;
                session++;
            }
        }
        fire(LineEvent.Type.START, at);
        if (at >= frames && loops == 0) finish(session);
        else if (loops > 0) {
            // A static track's marker isn't reliable across loop wraps; time the end instead.
            final int expected = session;
            long total = (frames - at) + (long) loops * (Math.min(loopEnd < 0 ? frames : loopEnd + 1, frames) - loopStart);
            EVENTS.postDelayed(() -> finish(expected), total * 1000L / Math.max(1, sampleRate) + 20);
        }
    }

    private void ended(AudioTrack at) {
        int expected;
        synchronized (lock) {
            if (at != track) return;
            expected = session;
        }
        finish(expected);
    }

    private void finish(int expected) {
        synchronized (lock) {
            if (!running || session != expected) return;
            running = false;
            position = frames;
            if (track != null) {
                try { track.stop(); } catch (IllegalStateException ignored) {}
            }
        }
        fire(LineEvent.Type.STOP, frames);
    }

    @Override
    public void stop() {
        int at;
        synchronized (lock) {
            if (track == null || !running) return;
            running = false;
            session++;
            try {
                track.pause();
                position = Math.min(frames, track.getPlaybackHeadPosition());
            } catch (IllegalStateException ignored) {}
            at = position;
        }
        fire(LineEvent.Type.STOP, at);
    }

    @Override
    public void close() {
        boolean wasRunning;
        synchronized (lock) {
            if (track == null) return;
            wasRunning = running;
            running = false;
            session++;
            try { track.stop(); } catch (IllegalStateException ignored) {}
            track.release();
            track = null;
            OPEN.remove(this);
        }
        if (wasRunning) fire(LineEvent.Type.STOP, position);
        fire(LineEvent.Type.CLOSE, position);
    }

    @Override
    public void drain() {
        // Blocks until the clip has played out, like the JDK's.
        while (true) {
            synchronized (lock) {
                if (!running) return;
            }
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    @Override public void flush() {}

    @Override
    public void setFramePosition(int frame) {
        synchronized (lock) {
            position = Math.max(0, Math.min(frame, frames));
            if (track != null && running) {
                try { track.setPlaybackHeadPosition(position); } catch (IllegalStateException ignored) {}
            }
        }
    }

    @Override
    public void setMicrosecondPosition(long microseconds) {
        setFramePosition((int) (microseconds * sampleRate / 1_000_000L));
    }

    @Override
    public void setLoopPoints(int start, int end) {
        if (start < 0 || start >= Math.max(1, frames) || (end != -1 && end < start)) {
            throw new IllegalArgumentException("Invalid loop points " + start + ".." + end);
        }
        synchronized (lock) {
            loopStart = start;
            loopEnd = end;
        }
    }

    @Override public int getFrameLength() { return frames; }
    @Override public long getMicrosecondLength() { return sampleRate <= 0 ? 0 : frames * 1_000_000L / sampleRate; }

    @Override
    public int getFramePosition() {
        synchronized (lock) {
            if (track != null && running) {
                try { return Math.min(frames, track.getPlaybackHeadPosition()); } catch (IllegalStateException ignored) {}
            }
            return position;
        }
    }

    @Override public long getLongFramePosition() { return getFramePosition(); }
    @Override public long getMicrosecondPosition() { return sampleRate <= 0 ? 0 : getFramePosition() * 1_000_000L / sampleRate; }
    @Override public boolean isRunning() { synchronized (lock) { return running; } }
    @Override public boolean isActive() { return isRunning(); }
    @Override public AudioFormat getFormat() { return format; }
    @Override public int getBufferSize() { return format == null ? 0 : frames * format.getFrameSize(); }
    @Override public int available() { return 0; }
    @Override public float getLevel() { return AudioSystem.NOT_SPECIFIED; }
    @Override public boolean isOpen() { synchronized (lock) { return track != null; } }

    @Override public Line.Info getLineInfo() { return new DataLine.Info(Clip.class, format); }
    @Override public Control[] getControls() { return new Control[]{gain}; }
    @Override public boolean isControlSupported(Control.Type type) { return type == FloatControl.Type.MASTER_GAIN; }

    @Override
    public Control getControl(Control.Type type) {
        if (type == FloatControl.Type.MASTER_GAIN) return gain;
        throw new IllegalArgumentException("Unsupported control type: " + type);
    }

    @Override public void addLineListener(LineListener listener) { if (listener != null) listeners.add(listener); }
    @Override public void removeLineListener(LineListener listener) { listeners.remove(listener); }

    private void fire(LineEvent.Type type, long frame) {
        if (listeners.isEmpty()) return;
        LineEvent event = new LineEvent(this, type, frame);
        EVENTS.post(() -> {
            for (LineListener l : listeners) {
                try { l.update(event); } catch (Throwable t) {
                    android.util.Log.w("AndroidClip", "LineListener threw", t);
                }
            }
        });
    }

    /** dB gain, same range the JDK's mixers report; applied as linear volume (≤ 1). */
    private final class MasterGain extends FloatControl {
        MasterGain() { super(FloatControl.Type.MASTER_GAIN, -80f, 6.0206f, 0.01f, -1, 0f, "dB"); }

        @Override
        public void setValue(float value) {
            super.setValue(value);
            apply();
        }

        void apply() {
            AudioTrack t = track;
            if (t == null) return;
            float linear = (float) Math.pow(10.0, getValue() / 20.0);
            try { t.setVolume(Math.max(0f, Math.min(1f, linear))); } catch (IllegalStateException ignored) {}
        }
    }

    /** Any PCM layout → 16-bit signed little-endian, keeping at most the first two channels. */
    private static byte[] toPcm16(AudioFormat fmt, byte[] data, int offset, int length) throws LineUnavailableException {
        int bits = fmt.getSampleSizeInBits();
        int channels = fmt.getChannels();
        int bytesPer = (bits + 7) / 8;
        int frameSize = fmt.getFrameSize() > 0 ? fmt.getFrameSize() : bytesPer * channels;
        if (channels <= 0 || bytesPer <= 0 || frameSize < bytesPer * channels || fmt.getSampleRate() <= 0) {
            throw new LineUnavailableException("Unsupported clip format: " + fmt);
        }
        AudioFormat.Encoding enc = fmt.getEncoding();
        boolean isFloat = AudioFormat.Encoding.PCM_FLOAT.equals(enc);
        boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(enc);
        if (!isFloat && !unsigned && !AudioFormat.Encoding.PCM_SIGNED.equals(enc)) {
            throw new LineUnavailableException("Unsupported clip encoding: " + enc);
        }
        if (isFloat && bytesPer != 4 && bytesPer != 8) throw new LineUnavailableException("Unsupported float width: " + bits);
        boolean big = fmt.isBigEndian();
        int outChannels = Math.min(2, channels);
        int count = Math.max(0, Math.min(length, data.length - offset)) / frameSize;
        byte[] out = new byte[count * outChannels * 2];
        int o = 0;
        for (int f = 0; f < count; f++) {
            int base = offset + f * frameSize;
            for (int c = 0; c < outChannels; c++) {
                int p = base + c * bytesPer;
                long raw = 0;
                for (int i = 0; i < bytesPer; i++) {
                    int b = data[p + (big ? i : bytesPer - 1 - i)] & 0xFF;
                    raw = (raw << 8) | b;
                }
                int sample;
                if (isFloat) {
                    double v = bytesPer == 4 ? Float.intBitsToFloat((int) raw) : Double.longBitsToDouble(raw);
                    sample = (int) Math.round(Math.max(-1.0, Math.min(1.0, v)) * 32767.0);
                } else {
                    int shift = 64 - bytesPer * 8;
                    long signed = unsigned ? raw - (1L << (bytesPer * 8 - 1)) : (raw << shift) >> shift;
                    int width = bytesPer * 8;
                    sample = (int) (width >= 16 ? signed >> (width - 16) : signed << (16 - width));
                }
                out[o++] = (byte) sample;
                out[o++] = (byte) (sample >> 8);
            }
        }
        return out;
    }
}
