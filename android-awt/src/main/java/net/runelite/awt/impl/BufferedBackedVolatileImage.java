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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.ImageCapabilities;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;

/**
 * {@link VolatileImage} implementation backed by a regular {@link BufferedImage}.
 *
 * Real VolatileImage on the JDK uses a managed GPU-backed surface whose contents can
 * be "lost" (display mode change, screensaver, etc.), at which point the application
 * has to re-render. None of those concerns apply to a software rasterizer on Android:
 * the backing BufferedImage lives in heap memory and contents never go invalid. So
 * {@link #validate(GraphicsConfiguration)} always returns {@link #IMAGE_OK} and
 * {@link #contentsLost()} always returns {@code false}.
 *
 * Created by the {@code createCompatibleVolatileImage(int, int[, int])} overloads on
 * {@link GraphicsConfiguration}; the patched OSRS gamepack uses these to construct a
 * double-buffer for software-mode 3D rendering. Without this shim, the SW path dies
 * the first time the engine tries to validate the back-buffer.
 */
public final class BufferedBackedVolatileImage extends VolatileImage {
    private final BufferedImage backing;

    public BufferedBackedVolatileImage(int width, int height, int transparency) {
        // OPAQUE → TYPE_INT_RGB matches the BufferedImage createImage()/createGraphics
        // path the rest of android-awt already uses; anything else keeps a real alpha
        // channel so Java2D composite rules behave.
        int type = transparency == Transparency.OPAQUE
            ? BufferedImage.TYPE_INT_RGB
            : BufferedImage.TYPE_INT_ARGB;
        this.backing = new BufferedImage(width, height, type);
        this.transparency = transparency;
    }

    @Override public BufferedImage getSnapshot() { BufferedImage copy=new BufferedImage(getWidth(),getHeight(),backing.getType());copy.setRGB(0,0,getWidth(),getHeight(),backing.getRGB(0,0,getWidth(),getHeight(),null,0,getWidth()),0,getWidth());return copy; }
    @Override public int getWidth() { return backing.getWidth(); }
    @Override public int getHeight() { return backing.getHeight(); }
    @Override public Graphics2D createGraphics() { return backing.createGraphics(); }
    @Override public int validate(GraphicsConfiguration gc) { return IMAGE_OK; }
    @Override public boolean contentsLost() { return false; }
    @Override public ImageCapabilities getCapabilities() { return new ImageCapabilities(false); }

    @Override public Graphics getGraphics() { return createGraphics(); }
}
