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

import android.media.AudioTrack;
import javax.sound.sampled.AudioFormat;

/** API 26–30 startup buffering; modern devices keep PooledSourceDataLine's original hot paths. */
final class LegacySourceDataLine extends PooledSourceDataLine {
    LegacySourceDataLine(AudioFormat format, int bufferSize) { super(format, bufferSize); }

    private static final class Startup {
        final AudioTrack track;
        final int originalFrames;
        int effectiveFrames, underruns;
        boolean priming = true, failed;
        long primePosition;

        Startup(Slot slot) {
            track = slot.track;
            originalFrames = effectiveFrames = slot.bufferSize / slot.format.getFrameSize();
            underruns = track.getUnderrunCount();
        }

        void prepare(long played, long written, int incoming) {
            if (failed) throw new IllegalStateException("Legacy AudioTrack startup adjustment failed");
            progress(played);
            int count = track.getUnderrunCount();
            if (count != underruns || played >= written) {
                priming = true;
                primePosition = played;
            }
            underruns = count;
            if (priming) {
                long queued = Math.max(0L, written - played) + incoming;
                resize(queued == 0 ? originalFrames : (int) Math.min(originalFrames, queued));
                primePosition = played;
            }
        }

        void progress(long played) {
            if (!failed && priming && played > primePosition) {
                resize(originalFrames);
                priming = false;
            }
        }

        void resume(long played, long written) {
            if (failed) throw new IllegalStateException("Legacy AudioTrack startup adjustment failed");
            priming = true;
            primePosition = played;
            long queued = Math.max(0L, written - played);
            if (queued > 0) resize((int) Math.min(originalFrames, queued));
        }

        void reset(long played) {
            resize(originalFrames);
            priming = true;
            primePosition = played;
            underruns = track.getUnderrunCount();
        }

        private void resize(int frames) {
            if (effectiveFrames == frames) return;
            int actual;
            try { actual = track.setBufferSizeInFrames(frames); }
            catch (RuntimeException failure) { failed = true; throw failure; }
            if (actual <= 0 || actual > frames || frames == originalFrames && actual != originalFrames) {
                failed = true;
                track.setBufferSizeInFrames(originalFrames);
                effectiveFrames = originalFrames;
                throw new IllegalStateException("AudioTrack rejected legacy startup buffer: " + actual + " for " + frames);
            }
            effectiveFrames = actual;
        }
    }

    // The existing native Slot owns this state across logical close/reopen operations.
    private void adjust(int incoming, boolean resume, boolean reset) {
        Slot current = borrowLegacy();
        if (current == null) return;
        try {
            synchronized (current) {
                Startup state = (Startup) current.legacyStartup;
                if (state == null) current.legacyStartup = state = new Startup(current);
                try {
                    long played = current.played();
                    if (reset) state.reset(played);
                    else if (resume) state.resume(played, current.written);
                    else state.prepare(played, current.written, incoming);
                } catch (RuntimeException failure) { current.unusable = true; throw failure; }
            }
        } finally { releaseLegacy(current); }
    }

    private void observe() {
        Slot current = borrowLegacy();
        if (current == null) return;
        try {
            synchronized (current) {
                Startup state = (Startup) current.legacyStartup;
                if (state != null) {
                    try { state.progress(current.played()); }
                    catch (RuntimeException failure) { current.unusable = true; throw failure; }
                }
            }
        } finally { releaseLegacy(current); }
    }

    @Override public int write(byte[] data, int offset, int length) {
        AudioFormat format = getFormat();
        if (data != null && offset >= 0 && length > 0 && offset <= data.length - length
            && format.getFrameSize() > 0 && length % format.getFrameSize() == 0) {
            synchronized (this) { adjust(length / format.getFrameSize(), false, false); }
        }
        int count = super.write(data, offset, length);
        if (count > 0) synchronized (this) { adjust(0, false, false); }
        return count;
    }

    @Override public synchronized void start() {
        if (!isRunning()) adjust(0, true, false);
        super.start();
    }

    @Override public synchronized long getLongFramePosition() {
        long position = super.getLongFramePosition();
        observe();
        return position;
    }

    @Override public synchronized int available() {
        observe();
        return super.available();
    }

    @Override public synchronized void flush() {
        super.flush();
        adjust(0, false, true);
    }

    @Override public void close() {
        try { super.close(); }
        catch (RuntimeException failure) {
            Slot current = borrowLegacy();
            boolean unusable = current != null && current.unusable;
            if (current != null) releaseLegacy(current);
            if (unusable) super.close();
            throw failure;
        }
    }
}
