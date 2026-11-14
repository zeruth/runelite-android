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

/**
 * The {@code Taskbar} class allows a Java application to interact with the
 * system task area (taskbar, Dock, etc.).
 *
 * <p>Android has no taskbar a single app can drive: the launcher owns icons,
 * badges come from posted notifications and attention is requested with a
 * notification too. {@link #isTaskbarSupported()} is therefore always
 * {@code false}, {@link #getTaskbar()} throws, and every feature reports
 * unsupported, as on a JDK desktop without a taskbar peer.
 *
 * @since 9
 */
// JDK 9+ (9)
public class Taskbar {

    /** List of provided features. */
    public static enum Feature {
        /** Represents a textual icon badge feature. */
        ICON_BADGE_TEXT,
        /** Represents a numerical icon badge feature. */
        ICON_BADGE_NUMBER,
        /** Represents a graphical icon badge feature for a window. */
        ICON_BADGE_IMAGE_WINDOW,
        /** Represents an icon feature. */
        ICON_IMAGE,
        /** Represents a menu feature. */
        MENU,
        /** Represents a progress state feature for a specified window. */
        PROGRESS_STATE_WINDOW,
        /** Represents a progress value feature. */
        PROGRESS_VALUE,
        /** Represents a progress value feature for a specified window. */
        PROGRESS_VALUE_WINDOW,
        /** Represents a user attention request feature. */
        USER_ATTENTION,
        /** Represents a user attention request feature for a specified window. */
        USER_ATTENTION_WINDOW
    }

    /** Kinds of available window progress states. */
    public static enum State {
        /** Stops displaying the progress. */
        OFF,
        /** The progress indicator displays with normal color and determinate mode. */
        NORMAL,
        /** Shows progress as paused. */
        PAUSED,
        /** The progress indicator displays activity without specifying what proportion is done. */
        INDETERMINATE,
        /** Shows that an error has occurred. */
        ERROR
    }

    private static final Taskbar INSTANCE = new Taskbar();

    private Taskbar() {
    }

    private void checkFeatureSupport(Feature featureType) {
        if (!isSupported(featureType)) {
            throw new UnsupportedOperationException("The " + featureType.name()
                    + " feature is not supported on the current platform!");
        }
    }

    /**
     * Returns the {@code Taskbar} instance of the current context.
     *
     * @throws UnsupportedOperationException always: Android has no taskbar
     */
    public static synchronized Taskbar getTaskbar() {
        if (!isTaskbarSupported()) {
            throw new UnsupportedOperationException("Taskbar API is not supported on the current platform");
        }
        return INSTANCE;
    }

    /** Tests whether a {@code Taskbar} is supported: never on Android. */
    public static boolean isTaskbarSupported() {
        return false;
    }

    /** Tests whether a feature is supported: none is on Android. */
    public boolean isSupported(Feature feature) {
        return false;
    }

    /**
     * Requests user attention to this application. Android's counterpart is a
     * heads-up notification, which {@link TrayIcon#displayMessage} already
     * posts, so there is no separate request.
     *
     * @throws UnsupportedOperationException always ({@code USER_ATTENTION})
     */
    public void requestUserAttention(final boolean enabled, final boolean critical) {
        checkFeatureSupport(Feature.USER_ATTENTION);
    }

    /** @throws UnsupportedOperationException always ({@code USER_ATTENTION_WINDOW}) */
    public void requestWindowUserAttention(Window w) {
        checkFeatureSupport(Feature.USER_ATTENTION_WINDOW);
    }

    /** @throws UnsupportedOperationException always ({@code MENU}) */
    public void setMenu(final PopupMenu menu) {
        checkFeatureSupport(Feature.MENU);
    }

    /** @throws UnsupportedOperationException always ({@code MENU}) */
    public PopupMenu getMenu() {
        checkFeatureSupport(Feature.MENU);
        return null;
    }

    /** @throws UnsupportedOperationException always ({@code ICON_IMAGE}): the launcher owns the icon */
    public void setIconImage(final Image image) {
        checkFeatureSupport(Feature.ICON_IMAGE);
    }

    /** @throws UnsupportedOperationException always ({@code ICON_IMAGE}) */
    public Image getIconImage() {
        checkFeatureSupport(Feature.ICON_IMAGE);
        return null;
    }

    /**
     * Affixes a small system-provided badge to this application's icon. On
     * Android the launcher derives badges from the app's posted notifications;
     * an app cannot set a number or text on its icon directly.
     *
     * @throws UnsupportedOperationException always ({@code ICON_BADGE_NUMBER} / {@code ICON_BADGE_TEXT})
     */
    public void setIconBadge(final String badge) {
        if (badge != null) {
            try {
                Integer.parseInt(badge);
                checkFeatureSupport(Feature.ICON_BADGE_NUMBER);
                return;
            } catch (NumberFormatException ignored) {
                // a non-numeric badge is a text badge
            }
        }
        checkFeatureSupport(Feature.ICON_BADGE_TEXT);
    }

    /** @throws UnsupportedOperationException always ({@code ICON_BADGE_IMAGE_WINDOW}) */
    public void setWindowIconBadge(Window w, final Image badge) {
        checkFeatureSupport(Feature.ICON_BADGE_IMAGE_WINDOW);
    }

    /** @throws UnsupportedOperationException always ({@code PROGRESS_VALUE}) */
    public void setProgressValue(int value) {
        checkFeatureSupport(Feature.PROGRESS_VALUE);
    }

    /** @throws UnsupportedOperationException always ({@code PROGRESS_VALUE_WINDOW}) */
    public void setWindowProgressValue(Window w, int value) {
        checkFeatureSupport(Feature.PROGRESS_VALUE_WINDOW);
    }

    /** @throws UnsupportedOperationException always ({@code PROGRESS_STATE_WINDOW}) */
    public void setWindowProgressState(Window w, State state) {
        checkFeatureSupport(Feature.PROGRESS_STATE_WINDOW);
    }
}
