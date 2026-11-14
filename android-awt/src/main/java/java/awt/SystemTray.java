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
package java.awt;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code SystemTray} class represents the system tray for a desktop.
 *
 * <p>Android has no system tray: {@link #isSupported()} is always
 * {@code false} and {@link #getSystemTray()} throws, as on a JDK desktop
 * without one. Upstream RuneLite checks {@code isSupported()} before building
 * its tray icon, so it never reaches the instance methods. Notifications go
 * through {@link TrayIcon#displayMessage}, which works without a tray.
 *
 * @since 1.6
 */
public class SystemTray {

    private static final SystemTray INSTANCE = new SystemTray();

    private final List<TrayIcon> icons = new ArrayList<TrayIcon>();
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

    private SystemTray() {
    }

    /**
     * Gets the {@code SystemTray} instance that represents the desktop's tray area.
     *
     * @throws UnsupportedOperationException always: Android has no system tray
     */
    public static SystemTray getSystemTray() {
        if (!isSupported()) {
            throw new UnsupportedOperationException("The system tray is not supported on the current platform.");
        }
        return INSTANCE;
    }

    /** Returns whether the system tray is supported: never on Android. */
    public static boolean isSupported() {
        return false;
    }

    /**
     * Adds a {@code TrayIcon} to the {@code SystemTray}.
     *
     * @throws NullPointerException if {@code trayIcon} is {@code null}
     * @throws IllegalArgumentException if the same instance is added more than once
     * @throws AWTException if the desktop system tray is missing, which is always on Android
     */
    public void add(TrayIcon trayIcon) throws AWTException {
        if (trayIcon == null) {
            throw new NullPointerException("adding null TrayIcon");
        }
        synchronized (this) {
            if (icons.contains(trayIcon)) {
                throw new IllegalArgumentException("adding TrayIcon that is already added");
            }
        }
        throw new AWTException("The system tray is not available");
    }

    /** Removes the specified {@code TrayIcon}; {@code null} or an icon never added is ignored. */
    public void remove(TrayIcon trayIcon) {
        if (trayIcon == null) {
            return;
        }
        TrayIcon[] oldArray;
        TrayIcon[] newArray;
        synchronized (this) {
            oldArray = getTrayIcons();
            if (!icons.remove(trayIcon)) {
                return;
            }
            newArray = getTrayIcons();
        }
        changeSupport.firePropertyChange("trayIcons", oldArray, newArray);
    }

    /** Returns an array of all icons added to this tray: always empty on Android. */
    public synchronized TrayIcon[] getTrayIcons() {
        return icons.toArray(new TrayIcon[0]);
    }

    /** Returns the size, in pixels, of the space a tray icon occupies: zero on Android. */
    public Dimension getTrayIconSize() {
        return new Dimension(0, 0);
    }

    /**
     * Adds a {@code PropertyChangeListener} for the {@code "trayIcons"} or
     * {@code "systemTray"} property; a {@code null} listener is ignored.
     */
    public synchronized void addPropertyChangeListener(String propertyName,
                                                       PropertyChangeListener listener) {
        if (listener == null) {
            return;
        }
        changeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public synchronized void removePropertyChangeListener(String propertyName,
                                                          PropertyChangeListener listener) {
        if (listener == null) {
            return;
        }
        changeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public synchronized PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return changeSupport.getPropertyChangeListeners(propertyName);
    }
}
