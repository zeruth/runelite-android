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

import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.ImageCapabilities;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.VolatileImage;

/**
 * {@link VolatileImage} implementation backed by a regular {@link BufferedImage}.
 *
 * Real VolatileImage on the JDK uses a managed GPU-backed surface whose contents can
 * be "lost" (display mode change, screensaver, etc.), at which point the application
 * has to re-render. A heap raster on Android is never lost behind the application's
 * back; the only way to lose it is {@link #flush()}, which releases the raster the way
 * the JDK releases VRAM. The next {@link #validate(GraphicsConfiguration)} then
 * reallocates it and returns {@link #IMAGE_RESTORED}, and {@link #contentsLost()}
 * reports {@code true} until then, so the standard validate/render/contentsLost loop
 * behaves as on desktop. There is a single graphics device, so no configuration is
 * {@link #IMAGE_INCOMPATIBLE}.
 *
 * Created by the {@code createCompatibleVolatileImage} overloads on
 * {@link GraphicsConfiguration}; the patched OSRS gamepack uses these to construct a
 * double-buffer for software-mode 3D rendering, RuneLite for the stretched-mode buffer.
 * Also the back buffer of {@code Canvas.createBufferStrategy}.
 */
public final class BufferedBackedVolatileImage extends VolatileImage {
    private static final ImageCapabilities CAPABILITIES = new ImageCapabilities(false);

    private final int width;
    private final int height;
    private final int type;
    // Null between flush() and the next validate()/createGraphics().
    private volatile BufferedImage backing;
    private volatile boolean lost;

    public BufferedBackedVolatileImage(int width, int height, int transparency) {
        // OPAQUE → TYPE_INT_RGB matches the BufferedImage createImage()/createGraphics
        // path the rest of android-awt already uses; anything else keeps a real alpha
        // channel so Java2D composite rules behave.
        this.type = transparency == Transparency.OPAQUE
            ? BufferedImage.TYPE_INT_RGB
            : BufferedImage.TYPE_INT_ARGB;
        this.width = width;
        this.height = height;
        this.transparency = transparency;
        this.backing = new BufferedImage(width, height, type);
    }

    /**
     * The live raster, for blitting this image without a snapshot copy. Reallocates a
     * flushed image (contents undefined, as on desktop) without clearing the lost flag.
     */
    public BufferedImage backing() {
        BufferedImage b = backing;
        if (b == null) {
            synchronized (this) {
                b = backing;
                if (b == null) {
                    b = new BufferedImage(width, height, type);
                    backing = b;
                }
            }
        }
        return b;
    }

    @Override
    public BufferedImage getSnapshot() {
        BufferedImage source = backing();
        BufferedImage copy = new BufferedImage(width, height, type);
        copy.setRGB(0, 0, width, height, source.getRGB(0, 0, width, height, null, 0, width), 0, width);
        return copy;
    }

    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public int getWidth(ImageObserver observer) { return width; }
    @Override public int getHeight(ImageObserver observer) { return height; }

    @Override
    public Graphics2D createGraphics() {
        return backing().createGraphics();
    }

    @Override
    public int validate(GraphicsConfiguration gc) {
        if (!lost) {
            return IMAGE_OK;
        }
        backing();
        lost = false;
        return IMAGE_RESTORED;
    }

    @Override public boolean contentsLost() { return lost; }
    @Override public ImageCapabilities getCapabilities() { return CAPABILITIES; }

    /** Releases the raster; the contents are lost until the next {@link #validate}. */
    @Override
    public void flush() {
        synchronized (this) {
            backing = null;
            lost = true;
        }
    }
}
