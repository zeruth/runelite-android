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

import java.applet.AudioClip;
import java.net.URL;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * {@link AudioClip} over a {@code javax.sound.sampled} {@link Clip}, what {@code
 * Applet.newAudioClip} returns. The URL is decoded on first use, not at construction, as
 * the JDK's {@code AppletAudioClip} does; a URL that cannot be read or decoded leaves a
 * clip that plays nothing, which is also the JDK's behaviour (it swallows load errors).
 */
public final class SampledAudioClip implements AudioClip {
    private final URL url;
    private Clip clip;
    private boolean loadFailed;

    public SampledAudioClip(URL url) {
        this.url = url;
    }

    private Clip clip() {
        if (clip == null && !loadFailed) {
            try {
                Clip c = AudioSystem.getClip();
                c.open(AudioSystem.getAudioInputStream(url));
                clip = c;
            } catch (Exception | LinkageError e) {
                loadFailed = true;
            }
        }
        return clip;
    }

    /** Restarts the clip from its first frame and plays it once. */
    public synchronized void play() {
        Clip c = clip();
        if (c == null) return;
        c.stop();
        c.setFramePosition(0);
        c.start();
    }

    /** Restarts the clip from its first frame and repeats it until {@link #stop()}. */
    public synchronized void loop() {
        Clip c = clip();
        if (c == null) return;
        c.stop();
        c.setFramePosition(0);
        c.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public synchronized void stop() {
        if (clip != null) clip.stop();
    }
}
