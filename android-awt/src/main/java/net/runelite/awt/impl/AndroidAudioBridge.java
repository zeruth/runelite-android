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
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Creates independent PCM lines with safe native-track reuse during rapid reopen, and capture
 * lines over AudioRecord gated by the host's RECORD_AUDIO grant.
 */
public final class AndroidAudioBridge {
    /**
     * Supplied by the host activity: answers whether RECORD_AUDIO is granted, asking the user
     * when it is not yet, and blocking the calling (non-UI) thread until they answer.
     */
    public interface RecordPermission {
        boolean request();
    }

    private static volatile RecordPermission recordPermission;

    private AndroidAudioBridge() {
    }

    /** Installs the host's permission flow; {@code null} leaves the grant to the AudioRecord itself. */
    public static void setRecordPermission(RecordPermission handler) {
        recordPermission = handler;
    }

    public static RecordPermission getRecordPermission() {
        return recordPermission;
    }

    /**
     * Asks the host for RECORD_AUDIO before a capture line opens. Without a host (an app_process
     * probe running as shell, which holds the permission) AudioRecord's own check decides.
     */
    static void requireRecordPermission() throws LineUnavailableException {
        RecordPermission handler = recordPermission;
        if (handler != null && !handler.request()) {
            throw new LineUnavailableException(
                "Microphone access denied: RECORD_AUDIO was not granted to RuneLite (allow it in the app's permission settings)");
        }
    }

    /** A capture line for {@code format}; nothing records until it is opened and started. */
    public static TargetDataLine openTargetDataLine(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (format == null) {
            throw new LineUnavailableException("AudioFormat required");
        }
        return new AndroidTargetDataLine(format, bufferSize);
    }

    public static SourceDataLine openSourceDataLine(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (format == null) {
            throw new LineUnavailableException("AudioFormat required");
        }
        if (android.os.Build.VERSION.SDK_INT >= 26 && android.os.Build.VERSION.SDK_INT <= 30) {
            return new LegacySourceDataLine(format, bufferSize);
        }
        return new PooledSourceDataLine(format, bufferSize);
    }
}
