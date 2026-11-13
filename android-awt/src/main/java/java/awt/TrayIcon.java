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

import java.awt.event.ActionListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.awt.AwtUnsupported;

/**
 * A {@code TrayIcon} object represents a tray icon that can be added to the
 * {@link SystemTray system tray}.
 *
 * <p>Android has no system tray, so {@link SystemTray#isSupported()} is
 * {@code false} and an icon is never added or shown, and its listeners never
 * fire. Unlike the JDK, the constructors do not throw
 * {@code UnsupportedOperationException} when the tray is unsupported: a plugin
 * may build an icon only to call {@link #displayMessage}, which the host app
 * turns into an Android notification on a channel per {@link MessageType}.
 *
 * @since 1.6
 */
public class TrayIcon {

    /**
     * The message type determines which icon is displayed in the caption of
     * the message, and which notification channel carries it on Android.
     */
    public enum MessageType {
        /** An error message */
        ERROR,
        /** A warning message */
        WARNING,
        /** An information message */
        INFO,
        /** Simple message */
        NONE
    }

    /** The host side of {@link #displayMessage}; {@code messageType} is never {@code null}. */
    public interface MessageBridge {
        void displayMessage(String caption, String text, MessageType messageType);
    }

    private static volatile MessageBridge messageBridge;

    private Image image;
    private String tooltip;
    private PopupMenu popup;
    private boolean autosize;
    private String actionCommand;
    private final List<MouseListener> mouseListeners = new CopyOnWriteArrayList<MouseListener>();
    private final List<MouseMotionListener> mouseMotionListeners = new CopyOnWriteArrayList<MouseMotionListener>();
    private final List<ActionListener> actionListeners = new CopyOnWriteArrayList<ActionListener>();

    /**
     * Creates a {@code TrayIcon} with the specified image.
     *
     * @throws IllegalArgumentException if {@code image} is {@code null}
     */
    public TrayIcon(Image image) {
        if (image == null) {
            throw new IllegalArgumentException("creating TrayIcon with null Image");
        }
        setImage(image);
    }

    /** Creates a {@code TrayIcon} with the specified image and tooltip text. */
    public TrayIcon(Image image, String tooltip) {
        this(image);
        setToolTip(tooltip);
    }

    /** Creates a {@code TrayIcon} with the specified image, tooltip and popup menu. */
    public TrayIcon(Image image, String tooltip, PopupMenu popup) {
        this(image, tooltip);
        setPopupMenu(popup);
    }

    /** Installs the host notification bridge; {@code null} uninstalls it. */
    public static void setMessageBridge(MessageBridge bridge) {
        messageBridge = bridge;
    }

    /** Uninstalls the bridge only when it is still {@code expected}. */
    public static synchronized void clearMessageBridge(MessageBridge expected) {
        if (messageBridge == expected) {
            messageBridge = null;
        }
    }

    /**
     * Sets the image for this {@code TrayIcon}.
     *
     * @throws NullPointerException if {@code image} is {@code null}
     */
    public void setImage(Image image) {
        if (image == null) {
            throw new NullPointerException("setting null Image");
        }
        this.image = image;
    }

    public Image getImage() {
        return image;
    }

    /** Sets the popup menu; {@code null} removes it. */
    public void setPopupMenu(PopupMenu popup) {
        this.popup = popup;
    }

    public PopupMenu getPopupMenu() {
        return popup;
    }

    public void setToolTip(String tooltip) {
        this.tooltip = tooltip;
    }

    public String getToolTip() {
        return tooltip;
    }

    /** Sets whether the image is resized to fit the tray icon space. */
    public void setImageAutoSize(boolean autosize) {
        this.autosize = autosize;
    }

    public boolean isImageAutoSize() {
        return autosize;
    }

    /** Adds the listener; a {@code null} listener is ignored. */
    public synchronized void addMouseListener(MouseListener listener) {
        if (listener != null) {
            mouseListeners.add(listener);
        }
    }

    public synchronized void removeMouseListener(MouseListener listener) {
        if (listener != null) {
            mouseListeners.remove(listener);
        }
    }

    public synchronized MouseListener[] getMouseListeners() {
        return mouseListeners.toArray(new MouseListener[0]);
    }

    /** Adds the listener; a {@code null} listener is ignored. */
    public synchronized void addMouseMotionListener(MouseMotionListener listener) {
        if (listener != null) {
            mouseMotionListeners.add(listener);
        }
    }

    public synchronized void removeMouseMotionListener(MouseMotionListener listener) {
        if (listener != null) {
            mouseMotionListeners.remove(listener);
        }
    }

    public synchronized MouseMotionListener[] getMouseMotionListeners() {
        return mouseMotionListeners.toArray(new MouseMotionListener[0]);
    }

    /** Returns the command name of the action event fired by this tray icon. */
    public String getActionCommand() {
        return actionCommand;
    }

    public void setActionCommand(String command) {
        actionCommand = command;
    }

    /** Adds the listener; a {@code null} listener is ignored. */
    public synchronized void addActionListener(ActionListener listener) {
        if (listener != null) {
            actionListeners.add(listener);
        }
    }

    public synchronized void removeActionListener(ActionListener listener) {
        if (listener != null) {
            actionListeners.remove(listener);
        }
    }

    public synchronized ActionListener[] getActionListeners() {
        return actionListeners.toArray(new ActionListener[0]);
    }

    /**
     * Displays a popup message near the tray icon; on Android, posts a
     * notification through the host bridge. Without a bridge (host tests, or
     * before the activity starts) it is a documented no-op.
     *
     * @param messageType the type of message, {@code null} meaning {@code NONE}
     * @throws NullPointerException if both {@code caption} and {@code text} are {@code null}
     */
    public void displayMessage(String caption, String text, MessageType messageType) {
        if (caption == null && text == null) {
            throw new NullPointerException("displaying the message with both caption and text being null");
        }
        MessageBridge bridge = messageBridge;
        if (bridge == null) {
            AwtUnsupported.noop("java.awt.TrayIcon.displayMessage(String, String, MessageType)",
                    "no notification bridge is installed");
            return;
        }
        bridge.displayMessage(caption, text, messageType == null ? MessageType.NONE : messageType);
    }

    /**
     * Returns the size, in pixels, of the space the tray icon occupies in the
     * system tray: zero, since it is never shown.
     */
    public Dimension getSize() {
        return new Dimension(0, 0);
    }
}
