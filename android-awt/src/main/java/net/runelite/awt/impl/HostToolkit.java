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

import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;

/**
 * The default toolkit: the role sun.awt.windows.WToolkit plays on desktop. The host app pushes the
 * window state into it (lock keys from the key path, desktop properties from ViewConfiguration, the
 * beeper); it stays free of android.* so the host contracts run it on a desktop JDK.
 */
public final class HostToolkit extends Toolkit {
    private static final int CAPS = 1, NUM = 2, SCROLL = 4;

    private static volatile Runnable beeper;
    private static volatile int lockingKeys;

    public HostToolkit() {
    }

    /** The host's beeper (a ToneGenerator on Android); {@code null} leaves {@link #beep} silent. */
    public static void setBeeper(Runnable beeper) {
        HostToolkit.beeper = beeper;
    }

    /** Lock key state of the last host key event (Android KeyEvent meta state). */
    public static void updateLockingKeys(boolean capsLock, boolean numLock, boolean scrollLock) {
        lockingKeys = (capsLock ? CAPS : 0) | (numLock ? NUM : 0) | (scrollLock ? SCROLL : 0);
    }

    /** Sets a desktop property on the default toolkit; listeners fire on the event dispatch thread. */
    public static void updateDesktopProperty(String name, Object value) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        if (toolkit instanceof HostToolkit) {
            HostToolkit host = (HostToolkit) toolkit;
            host.getDesktopProperty(name); // initializes the defaults first, so they are not reported as new
            host.setDesktopProperty(name, value);
        }
    }

    @Override
    protected void initializeDesktopProperties() {
        // Desktop values until the host reports its own: Windows' click interval and drag threshold.
        desktopProperties.put("awt.multiClickInterval", 500);
        desktopProperties.put("DnD.gestureMotionThreshold", 5);
        desktopProperties.put("awt.mouse.numButtons", 3);
        desktopProperties.put("awt.wheelMousePresent", Boolean.TRUE);
        // Android renders text with grayscale antialiasing: Windows' "standard" smoothing (type 1).
        desktopProperties.put("awt.font.desktophints",
            new RenderingHints(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON));
        desktopProperties.put("win.text.fontSmoothingOn", Boolean.TRUE);
        desktopProperties.put("win.text.fontSmoothingType", 1);
        desktopProperties.put("win.text.fontSmoothingContrast", 1200);
        desktopProperties.put("win.text.fontSmoothingOrientation", 1);
    }

    @Override
    protected Object lazilyLoadDesktopProperty(String name) {
        // The host view relayouts on every resize, so live layout is always supported.
        return "awt.dynamicLayoutSupported".equals(name) ? Boolean.TRUE : null;
    }

    @Override
    public Insets getScreenInsets(GraphicsConfiguration gc) {
        return HeadlessGraphicsConfiguration.getInsets();
    }

    @Override
    public void beep() {
        Runnable target = beeper;
        if (target != null) target.run();
    }

    @Override
    public boolean getLockingKeyState(int keyCode) {
        checkLockingKey(keyCode, "Toolkit.getLockingKeyState");
        int state = lockingKeys;
        switch (keyCode) {
            case KeyEvent.VK_CAPS_LOCK: return (state & CAPS) != 0;
            case KeyEvent.VK_NUM_LOCK: return (state & NUM) != 0;
            case KeyEvent.VK_SCROLL_LOCK: return (state & SCROLL) != 0;
            default: throw new UnsupportedOperationException("Toolkit.getLockingKeyState: no Kana lock on Android");
        }
    }

    /** Android lets an app read the lock keys but not toggle them; the JDK allows this exception. */
    @Override
    public void setLockingKeyState(int keyCode, boolean on) {
        checkLockingKey(keyCode, "Toolkit.setLockingKeyState");
        throw new UnsupportedOperationException("Toolkit.setLockingKeyState: Android apps cannot toggle lock keys");
    }
}
