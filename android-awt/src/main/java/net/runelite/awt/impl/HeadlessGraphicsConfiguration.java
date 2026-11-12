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
import java.awt.GraphicsDevice;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;

public final class HeadlessGraphicsConfiguration extends GraphicsConfiguration {
    public static final HeadlessGraphicsConfiguration INSTANCE = new HeadlessGraphicsConfiguration();

    private static volatile int[] display = {1, 1, 96};

    /** Host viewport dimensions in AWT logical pixels, updated after display or window changes. */
    public static void updateDisplay(int width, int height, int dpi) {
        if (width <= 0 || height <= 0 || dpi <= 0) throw new IllegalArgumentException("Display metrics");
        display = new int[]{width, height, dpi};
    }
    public static int getResolution() { return display[2]; }

    private static volatile int[] insets = {0, 0, 0, 0};

    /** Host system bars and display cutout over the viewport, in the units of {@link #updateDisplay}. */
    public static void updateInsets(int top, int left, int bottom, int right) {
        if (top < 0 || left < 0 || bottom < 0 || right < 0) throw new IllegalArgumentException("Display insets");
        insets = new int[]{top, left, bottom, right};
    }
    public static Insets getInsets() { int[] i = insets; return new Insets(i[0], i[1], i[2], i[3]); }

    private final GraphicsDevice device = new HeadlessGraphicsDevice(this);

    private HeadlessGraphicsConfiguration() {}

    @Override public GraphicsDevice getDevice() { return device; }
    @Override public Rectangle getBounds() { int[] metrics = display; return new Rectangle(0, 0, metrics[0], metrics[1]); }
    // Declared here as well as on the shadow base: the JDK's GraphicsConfiguration leaves them
    // abstract, and the host contracts run this class over the JDK's.
    @Override public ColorModel getColorModel() { return ColorModel.getRGBdefault(); }
    @Override public ColorModel getColorModel(int transparency) {
        return transparency == Transparency.OPAQUE ? new DirectColorModel(24, 0xff0000, 0xff00, 0xff) : getColorModel();
    }
    /** AWT user space is device pixels on the host view, as on an unscaled desktop. */
    @Override public AffineTransform getDefaultTransform() { return new AffineTransform(); }
    /** The JDK's definition: user space scaled so that 72 units span one inch at the host density. */
    @Override public AffineTransform getNormalizingTransform() {
        double scale = getResolution() / 72.0;
        return AffineTransform.getScaleInstance(scale, scale);
    }
    /** Window backbuffers are TYPE_INT_ARGB and the host composites them with their alpha. */
    @Override public boolean isTranslucencyCapable() { return true; }
}
