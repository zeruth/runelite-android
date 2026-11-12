/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.awt;

import java.io.IOException;
import java.net.URL;

/**
 * The splash screen can be displayed at application startup, before the
 * Java Virtual Machine (JVM) starts. The splash screen is displayed as an
 * undecorated window containing an image.
 * <p>
 * An Android process is never started with {@code -splash:} or a
 * {@code SplashScreen-Image} manifest entry, so there is never a splash screen:
 * {@link #getSplashScreen()} returns {@code null} and no instance exists.
 * 8u throws {@link HeadlessException} from {@code getSplashScreen} in a headless
 * environment; the shadow is headless only in that it has no native peers, and
 * returning {@code null} is what callers written for desktop (RuneLite's
 * launcher included) already handle.
 *
 * @author Oleg Semenov
 * @since 1.6
 */
public final class SplashScreen {

    private static final String UNAVAILABLE = "no splash screen available";

    private SplashScreen() {
    }

    /**
     * Returns the {@code SplashScreen} object used for
     * Java startup splash screen control on systems that support display.
     *
     * @return always {@code null} on Android: no splash screen is ever shown
     */
    public static SplashScreen getSplashScreen() {
        return null;
    }

    /**
     * Changes the splash screen image.
     *
     * @param imageURL the non-<code>null</code> URL for the new
     *        splash screen image
     * @throws NullPointerException if {@code imageURL} is <code>null</code>
     * @throws IOException if there was an error while loading the image
     * @throws IllegalStateException if the splash screen has already been
     *         closed
     */
    public void setImageURL(URL imageURL) throws NullPointerException, IOException, IllegalStateException {
        if (imageURL == null) {
            throw new NullPointerException();
        }
        throw new IllegalStateException(UNAVAILABLE);
    }

    /**
     * Returns the current splash screen image.
     *
     * @return URL for the current splash screen image file
     * @throws IllegalStateException if the splash screen has already been closed
     */
    public URL getImageURL() throws IllegalStateException {
        throw new IllegalStateException(UNAVAILABLE);
    }

    /**
     * Returns the bounds of the splash screen window as a {@link Rectangle}.
     *
     * @return a {@code Rectangle} containing the splash screen bounds
     * @throws IllegalStateException if the splash screen has already been closed
     */
    public Rectangle getBounds() throws IllegalStateException {
        throw new IllegalStateException(UNAVAILABLE);
    }

    /**
     * Returns the size of the splash screen window as a {@link Dimension}.
     *
     * @return a {@link Dimension} object indicating the splash screen size
     * @throws IllegalStateException if the splash screen has already been closed
     */
    public Dimension getSize() throws IllegalStateException {
        throw new IllegalStateException(UNAVAILABLE);
    }

    /**
     * Creates a graphics context (as a {@link Graphics2D} object) for the splash
     * screen overlay image.
     *
     * @return graphics context for the splash screen overlay surface
     * @throws IllegalStateException if the splash screen has already been closed
     */
    public Graphics2D createGraphics() throws IllegalStateException {
        throw new IllegalStateException(UNAVAILABLE);
    }

    /**
     * Updates the splash window with current contents of the overlay image.
     *
     * @throws IllegalStateException if the overlay image does not exist;
     *         for example, if {@code createGraphics} has never been called,
     *         or if the splash screen has already been closed
     */
    public void update() throws IllegalStateException {
        throw new IllegalStateException(UNAVAILABLE);
    }

    /**
     * Hides the splash screen, closes the window, and releases all associated
     * resources.
     *
     * @throws IllegalStateException if the splash screen has already been closed
     */
    public void close() throws IllegalStateException {
        throw new IllegalStateException(UNAVAILABLE);
    }

    /**
     * Determines whether the splash screen is visible.
     *
     * @return true if the splash screen is visible (has not been closed yet),
     *         false otherwise
     */
    public boolean isVisible() {
        return false;
    }
}
