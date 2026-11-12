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

import android.media.AudioRecord;
import android.media.MediaRecorder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A capture line over {@link AudioRecord}: 16-bit signed or 32-bit float PCM, mono or stereo,
 * 8 to 48 kHz, either byte order. A capture thread moves data from the AudioRecord into a ring
 * buffer of {@link #getBufferSize()} bytes while the line runs, which is what gives the line a
 * JDK {@link #available()} (AudioRecord reports none) and lets data captured before
 * {@link #stop()} still be read afterwards. When the ring is full new data is dropped, as a JDK
 * line overruns.
 *
 * <p>The microphone is only recording between {@code start()} and {@code stop()}: stop stops
 * the AudioRecord, so the system's microphone privacy indicator goes off with it, and close
 * releases it. When the user has blocked the microphone in the privacy toggle the platform
 * delivers silence, and the line reports it as captured silence, as the platform intends.
 *
 * <p>Controls are the JDK direct line's (MASTER_GAIN, MUTE, PAN or BALANCE, VOLUME) applied in
 * software to the captured samples; gain is capped at 0 dB like the playback lines.
 */
final class AndroidTargetDataLine implements TargetDataLine {
    static final float MIN_RATE = 8000f;
    static final float MAX_RATE = 48000f;
    /** The capture thread moves this much audio per AudioRecord read. */
    private static final int CHUNK_MILLIS = 20;

    private final AudioFormat defaultFormat;
    private final int defaultBuffer;
    private final Object lock = new Object();
    private final List<LineListener> listeners = new CopyOnWriteArrayList<LineListener>();

    // Guarded by lock.
    private AudioFormat format;
    private AudioRecord record;
    private TrackControls controls;
    private Thread capture;
    private boolean running;
    private byte[] ring;
    private int head;
    private int count;
    private long captured;
    private int flushes;

    private volatile float level;
    private volatile float leftGain = 1f;
    private volatile float rightGain = 1f;

    AndroidTargetDataLine(AudioFormat format, int bufferSize) {
        this.defaultFormat = format;
        this.defaultBuffer = bufferSize;
    }

    static boolean supports(AudioFormat format) {
        if (format == null) return false;
        AudioFormat.Encoding encoding = format.getEncoding();
        int bits = format.getSampleSizeInBits();
        boolean pcm16 = AudioFormat.Encoding.PCM_SIGNED.equals(encoding) && bits == 16;
        boolean float32 = AudioFormat.Encoding.PCM_FLOAT.equals(encoding) && bits == 32;
        int channels = format.getChannels();
        float rate = format.getSampleRate();
        return (pcm16 || float32) && (channels == 1 || channels == 2)
            && format.getFrameSize() == channels * (bits / 8)
            && rate >= MIN_RATE && rate <= MAX_RATE
            && (format.getFrameRate() == AudioSystem.NOT_SPECIFIED || Math.abs(format.getFrameRate() - rate) < 0.5f);
    }

    // Lifecycle.

    @Override
    public void open() throws LineUnavailableException {
        open(defaultFormat, defaultBuffer);
    }

    @Override
    public void open(AudioFormat format) throws LineUnavailableException {
        open(format, AudioSystem.NOT_SPECIFIED);
    }

    @Override
    public void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (format == null) throw new NullPointerException("format");
        synchronized (lock) {
            if (record != null) {
                if (!format.matches(this.format)) {
                    throw new IllegalStateException("Line is already open with format " + this.format
                        + " and bufferSize " + ring.length);
                }
                return;
            }
        }
        if (!supports(format)) {
            throw new LineUnavailableException("line with format " + format + " not supported.");
        }
        int frameSize = format.getFrameSize();
        if (bufferSize != AudioSystem.NOT_SPECIFIED && bufferSize > 0 && bufferSize % frameSize != 0) {
            throw new IllegalArgumentException("Buffer size does not represent an integral number of sample frames!");
        }
        AndroidAudioBridge.requireRecordPermission();
        int chunk = chunkBytes(format);
        int ringSize = bufferSize > 0 ? bufferSize : (int) (format.getSampleRate() / 2) * frameSize;
        ringSize = Math.max(ringSize, chunk);
        AudioRecord created = create(format, ringSize);
        TrackControls lineControls = new TrackControls(format.getChannels(), new TrackControls.Sink() {
            @Override
            public void apply(float left, float right) {
                leftGain = left;
                rightGain = right;
            }
        });
        synchronized (lock) {
            if (record != null) {
                // Opened concurrently by another caller; keep theirs.
                created.release();
                return;
            }
            this.format = format;
            record = created;
            controls = lineControls;
            ring = new byte[ringSize];
            head = 0;
            count = 0;
            captured = 0;
            level = 0f;
            leftGain = 1f;
            rightGain = 1f;
        }
        fire(LineEvent.Type.OPEN);
    }

    // The RECORD_AUDIO grant is checked by AndroidAudioBridge.requireRecordPermission() just before.
    @android.annotation.SuppressLint("MissingPermission")
    private static AudioRecord create(AudioFormat format, int ringSize) throws LineUnavailableException {
        int rate = Math.round(format.getSampleRate());
        int mask = format.getChannels() == 2 ? android.media.AudioFormat.CHANNEL_IN_STEREO
            : android.media.AudioFormat.CHANNEL_IN_MONO;
        int encoding = AudioFormat.Encoding.PCM_FLOAT.equals(format.getEncoding())
            ? android.media.AudioFormat.ENCODING_PCM_FLOAT : android.media.AudioFormat.ENCODING_PCM_16BIT;
        int minimum = AudioRecord.getMinBufferSize(rate, mask, encoding);
        if (minimum <= 0) {
            throw new LineUnavailableException("line with format " + format + " not supported by this device.");
        }
        int frameSize = format.getFrameSize();
        int nativeSize = Math.max(minimum * 2, ringSize);
        nativeSize += (frameSize - nativeSize % frameSize) % frameSize;
        AudioRecord created;
        try {
            created = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, mask, encoding, nativeSize);
        } catch (RuntimeException e) {
            LineUnavailableException failure = new LineUnavailableException(
                "Microphone unavailable for " + format + ": " + e.getMessage());
            failure.initCause(e);
            throw failure;
        }
        if (created.getState() != AudioRecord.STATE_INITIALIZED) {
            created.release();
            throw new LineUnavailableException("Microphone unavailable for " + format
                + ": AudioRecord did not initialize (RECORD_AUDIO not granted, or the microphone is held by another app)");
        }
        return created;
    }

    private static int chunkBytes(AudioFormat format) {
        int frames = Math.max(1, Math.round(format.getSampleRate() * CHUNK_MILLIS / 1000f));
        return frames * format.getFrameSize();
    }

    @Override
    public void start() {
        AudioRecord current;
        synchronized (lock) {
            if (record == null || running) return;
            current = record;
            try {
                current.startRecording();
            } catch (IllegalStateException e) {
                return;
            }
            running = true;
            Thread thread = new Thread(new Capture(current), "AudioRecord-capture");
            thread.setDaemon(true);
            capture = thread;
            thread.start();
        }
        fire(LineEvent.Type.START);
    }

    @Override
    public void stop() {
        if (halt()) {
            fire(LineEvent.Type.STOP);
        }
    }

    /** Stops recording and waits for the capture thread; true when the line was running. */
    private boolean halt() {
        AudioRecord current;
        Thread thread;
        synchronized (lock) {
            if (!running) return false;
            running = false;
            current = record;
            thread = capture;
            capture = null;
            lock.notifyAll();
        }
        try {
            // Unblocks the capture thread's read and turns the privacy indicator off.
            current.stop();
        } catch (IllegalStateException ignored) {
            // never started recording
        }
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        level = 0f;
        return true;
    }

    @Override
    public void close() {
        boolean wasRunning = halt();
        AudioRecord current;
        synchronized (lock) {
            current = record;
            if (current == null) return;
            record = null;
            controls = null;
            count = 0;
            head = 0;
            lock.notifyAll();
        }
        current.release();
        if (wasRunning) fire(LineEvent.Type.STOP);
        fire(LineEvent.Type.CLOSE);
    }

    // Data.

    @Override
    public int read(byte[] b, int off, int len) {
        int frameSize;
        synchronized (lock) {
            if (record == null) return 0;
            frameSize = format.getFrameSize();
        }
        if (len % frameSize != 0) {
            throw new IllegalArgumentException("Requested length does not represent an integral number of sample frames.");
        }
        if (off < 0 || len < 0 || off + len > b.length) throw new ArrayIndexOutOfBoundsException();
        int done = 0;
        synchronized (lock) {
            int flush = flushes;
            while (done < len) {
                if (count > 0) {
                    int n = Math.min(len - done, Math.min(count, ring.length - head));
                    System.arraycopy(ring, head, b, off + done, n);
                    head = (head + n) % ring.length;
                    count -= n;
                    done += n;
                    lock.notifyAll();
                    continue;
                }
                // A stopped line hands back what it has instead of waiting for data that will not come.
                if (!running || record == null || flush != flushes) break;
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return done;
    }

    @Override
    public void drain() {
        synchronized (lock) {
            int flush = flushes;
            while (running && record != null && count > 0 && flush == flushes) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    public void flush() {
        synchronized (lock) {
            if (record == null) return;
            head = 0;
            count = 0;
            flushes++;
            lock.notifyAll();
        }
    }

    @Override
    public int available() {
        synchronized (lock) {
            return record == null ? 0 : count;
        }
    }

    /** Moves AudioRecord data into the ring while the line runs on {@code source}. */
    private final class Capture implements Runnable {
        private final AudioRecord source;

        Capture(AudioRecord source) {
            this.source = source;
        }

        @Override
        public void run() {
            AudioFormat captureFormat;
            synchronized (lock) {
                captureFormat = format;
            }
            int chunk = chunkBytes(captureFormat);
            ByteBuffer direct = ByteBuffer.allocateDirect(chunk);
            byte[] data = new byte[chunk];
            int frameSize = captureFormat.getFrameSize();
            while (true) {
                synchronized (lock) {
                    if (!running || record != source) return;
                }
                direct.clear();
                int n = source.read(direct, chunk);
                if (n <= 0) {
                    if (n < 0) return; // released, or the device went away
                    continue;
                }
                n -= n % frameSize;
                direct.get(data, 0, n);
                level = process(data, n, captureFormat);
                store(data, n, frameSize);
            }
        }

        private void store(byte[] data, int n, int frameSize) {
            synchronized (lock) {
                if (record != source) return;
                captured += n / frameSize;
                int room = ring.length - count;
                int keep = Math.min(n, room);
                int tail = (head + count) % ring.length;
                int first = Math.min(keep, ring.length - tail);
                System.arraycopy(data, 0, ring, tail, first);
                System.arraycopy(data, first, ring, 0, keep - first);
                count += keep;
                lock.notifyAll();
            }
        }
    }

    /**
     * Applies the controls' gain, reorders bytes for a big-endian format (AudioRecord writes
     * little-endian) and returns the peak level, in [0, 1], of the processed chunk.
     */
    private float process(byte[] data, int n, AudioFormat captureFormat) {
        boolean stereo = captureFormat.getChannels() == 2;
        boolean big = captureFormat.isBigEndian();
        float left = leftGain;
        float right = rightGain;
        // On mono a pan position only changes which channel is attenuated; neither is there.
        if (!stereo) left = right = Math.max(left, right);
        float peak = 0f;
        if (AudioFormat.Encoding.PCM_FLOAT.equals(captureFormat.getEncoding())) {
            for (int i = 0, sample = 0; i + 3 < n; i += 4, sample++) {
                int bits = (data[i] & 0xff) | (data[i + 1] & 0xff) << 8 | (data[i + 2] & 0xff) << 16 | data[i + 3] << 24;
                float value = Float.intBitsToFloat(bits) * (stereo && (sample & 1) == 1 ? right : left);
                peak = Math.max(peak, Math.abs(value));
                bits = Float.floatToRawIntBits(value);
                if (big) {
                    data[i] = (byte) (bits >>> 24);
                    data[i + 1] = (byte) (bits >>> 16);
                    data[i + 2] = (byte) (bits >>> 8);
                    data[i + 3] = (byte) bits;
                } else {
                    data[i] = (byte) bits;
                    data[i + 1] = (byte) (bits >>> 8);
                    data[i + 2] = (byte) (bits >>> 16);
                    data[i + 3] = (byte) (bits >>> 24);
                }
            }
        } else {
            for (int i = 0, sample = 0; i + 1 < n; i += 2, sample++) {
                int value = (short) ((data[i] & 0xff) | data[i + 1] << 8);
                float gain = stereo && (sample & 1) == 1 ? right : left;
                if (gain != 1f) value = Math.round(value * gain);
                peak = Math.max(peak, Math.abs(value) / 32768f);
                if (big) {
                    data[i] = (byte) (value >> 8);
                    data[i + 1] = (byte) value;
                } else {
                    data[i] = (byte) value;
                    data[i + 1] = (byte) (value >> 8);
                }
            }
        }
        return Math.min(1f, peak);
    }

    // State.

    @Override
    public boolean isOpen() {
        synchronized (lock) {
            return record != null;
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }

    @Override
    public boolean isActive() {
        return isRunning();
    }

    @Override
    public AudioFormat getFormat() {
        synchronized (lock) {
            return format != null ? format : defaultFormat;
        }
    }

    @Override
    public int getBufferSize() {
        synchronized (lock) {
            return ring != null && record != null ? ring.length : defaultBuffer;
        }
    }

    @Override
    public int getFramePosition() {
        return (int) getLongFramePosition();
    }

    /** Frames captured from the microphone since the line was opened, including any the ring dropped. */
    @Override
    public long getLongFramePosition() {
        synchronized (lock) {
            return captured;
        }
    }

    @Override
    public long getMicrosecondPosition() {
        AudioFormat current = getFormat();
        return (long) (getLongFramePosition() * 1_000_000d / current.getFrameRate());
    }

    /** Peak amplitude of the last captured chunk, 0 while the line is stopped. */
    @Override
    public float getLevel() {
        return isRunning() ? level : 0f;
    }

    @Override
    public Line.Info getLineInfo() {
        return new DataLine.Info(TargetDataLine.class, getFormat(), getBufferSize());
    }

    // Controls and listeners.

    private TrackControls controls() {
        synchronized (lock) {
            return controls;
        }
    }

    @Override
    public Control[] getControls() {
        TrackControls c = controls();
        return c == null ? new Control[0] : c.controls();
    }

    @Override
    public boolean isControlSupported(Control.Type type) {
        TrackControls c = controls();
        return c != null && c.supports(type);
    }

    @Override
    public Control getControl(Control.Type type) {
        TrackControls c = controls();
        if (c == null) throw new IllegalArgumentException("Unsupported control type: " + type);
        return c.get(type);
    }

    @Override
    public void addLineListener(LineListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void removeLineListener(LineListener listener) {
        listeners.remove(listener);
    }

    private void fire(LineEvent.Type type) {
        LineEvent event = new LineEvent(this, type, getLongFramePosition());
        for (LineListener listener : listeners) listener.update(event);
    }
}
