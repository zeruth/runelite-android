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
package java.awt.image;

import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.Set;

public class MemoryImageSource implements ImageProducer {
    private final int width, height;
    private final Set<ImageConsumer> consumers = new LinkedHashSet<>();
    private final Hashtable<?, ?> properties;
    private ColorModel model;
    private Object pixels;
    private int offset, scansize;
    private boolean animated, fullBufferUpdates;
    public MemoryImageSource(int w, int h, int[] pixels, int offset, int scansize) { this(w, h, pixels, offset, scansize, null); }
    public MemoryImageSource(int w, int h, int[] pixels, int offset, int scansize, Hashtable<?, ?> properties) { this(w, h, ColorModel.getRGBdefault(), pixels, offset, scansize, properties); }
    public MemoryImageSource(int w, int h, ColorModel model, int[] pixels, int offset, int scansize) { this(w, h, model, pixels, offset, scansize, null); }
    public MemoryImageSource(int w, int h, ColorModel model, int[] pixels, int offset, int scansize, Hashtable<?, ?> properties) { this(w, h, model, (Object)pixels, offset, scansize, properties); }
    public MemoryImageSource(int w, int h, ColorModel model, byte[] pixels, int offset, int scansize) { this(w, h, model, pixels, offset, scansize, null); }
    public MemoryImageSource(int w, int h, ColorModel model, byte[] pixels, int offset, int scansize, Hashtable<?, ?> properties) { this(w, h, model, (Object)pixels, offset, scansize, properties); }
    private MemoryImageSource(int w, int h, ColorModel model, Object pixels, int offset, int scansize, Hashtable<?, ?> properties) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("Image dimensions");
        width = w; height = h; this.model = java.util.Objects.requireNonNull(model); this.pixels = java.util.Objects.requireNonNull(pixels); this.offset = offset; this.scansize = scansize; this.properties = properties == null ? new Hashtable<>() : properties;
    }
    public synchronized void addConsumer(ImageConsumer consumer) {
        if (!consumers.add(consumer)) return;
        try {
            consumer.setDimensions(width, height); consumer.setProperties(properties); consumer.setColorModel(model); consumer.setHints(hints());
            send(consumer, 0, 0, width, height);
            consumer.imageComplete(animated ? ImageConsumer.SINGLEFRAMEDONE : ImageConsumer.STATICIMAGEDONE);
            if (!animated) consumers.remove(consumer);
        } catch (RuntimeException error) { consumers.remove(consumer); consumer.imageComplete(ImageConsumer.IMAGEERROR); }
    }
    public synchronized boolean isConsumer(ImageConsumer consumer) { return consumers.contains(consumer); }
    public synchronized void removeConsumer(ImageConsumer consumer) { consumers.remove(consumer); }
    public void startProduction(ImageConsumer consumer) { addConsumer(consumer); }
    public synchronized void requestTopDownLeftRightResend(ImageConsumer consumer) { if (consumers.contains(consumer)) send(consumer, 0, 0, width, height); }
    private int hints() { return animated ? (fullBufferUpdates ? ImageConsumer.TOPDOWNLEFTRIGHT | ImageConsumer.COMPLETESCANLINES : ImageConsumer.RANDOMPIXELORDER) : ImageConsumer.TOPDOWNLEFTRIGHT | ImageConsumer.COMPLETESCANLINES | ImageConsumer.SINGLEPASS | ImageConsumer.SINGLEFRAME; }
    public synchronized void setAnimated(boolean animated) {
        this.animated = animated;
        if (!animated) { for (ImageConsumer c : new LinkedHashSet<>(consumers)) c.imageComplete(ImageConsumer.STATICIMAGEDONE); consumers.clear(); }
    }
    public synchronized void setFullBufferUpdates(boolean full) { fullBufferUpdates = full; if (animated) for (ImageConsumer c : new LinkedHashSet<>(consumers)) c.setHints(hints()); }
    public void newPixels() { newPixels(0, 0, width, height, true); }
    public void newPixels(int x, int y, int w, int h) { newPixels(x, y, w, h, true); }
    public synchronized void newPixels(int x, int y, int w, int h, boolean frame) {
        if (!animated) return;
        if (fullBufferUpdates) { x = y = 0; w = width; h = height; }
        int right = Math.min(width, x + w), bottom = Math.min(height, y + h); x = Math.max(0, x); y = Math.max(0, y); w = Math.max(0, right - x); h = Math.max(0, bottom - y);
        for (ImageConsumer c : new LinkedHashSet<>(consumers)) { if (w > 0 && h > 0) send(c, x, y, w, h); if (frame && consumers.contains(c)) c.imageComplete(ImageConsumer.SINGLEFRAMEDONE); }
    }
    public synchronized void newPixels(int[] pixels, ColorModel model, int offset, int scansize) { this.pixels = pixels; this.model = model; this.offset = offset; this.scansize = scansize; newPixels(); }
    public synchronized void newPixels(byte[] pixels, ColorModel model, int offset, int scansize) { this.pixels = pixels; this.model = model; this.offset = offset; this.scansize = scansize; newPixels(); }
    private void send(ImageConsumer consumer, int x, int y, int w, int h) {
        if (pixels instanceof int[]) consumer.setPixels(x, y, w, h, model, (int[])pixels, offset + y * scansize + x, scansize);
        else consumer.setPixels(x, y, w, h, model, (byte[])pixels, offset + y * scansize + x, scansize);
    }
}
