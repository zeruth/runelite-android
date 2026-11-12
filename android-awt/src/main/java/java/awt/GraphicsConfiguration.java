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

public abstract class GraphicsConfiguration {
    protected GraphicsConfiguration() {
    }

    public GraphicsDevice getDevice() { return null; }
    public java.awt.image.ColorModel getColorModel() { return java.awt.image.ColorModel.getRGBdefault(); }
    public java.awt.image.ColorModel getColorModel(int transparency) { return transparency==Transparency.OPAQUE?new java.awt.image.DirectColorModel(24,0xff0000,0xff00,0xff):getColorModel(); }
    public java.awt.geom.AffineTransform getDefaultTransform() { return new java.awt.geom.AffineTransform(); }
    public java.awt.geom.AffineTransform getNormalizingTransform() { return new java.awt.geom.AffineTransform(); }
    public Rectangle getBounds() { return new Rectangle(0, 0, 0, 0); }

    // VolatileImage factories. The patched OSRS gamepack calls these to allocate the
    // SW-mode double buffer; without them the Client thread dies with
    // NoSuchMethodError on first frame and the screen stays black (toggling
    // GpuGlesPlugin doesn't recover because the dead thread won't restart).
    // Backed by a heap-resident BufferedImage via BufferedBackedVolatileImage.
    public java.awt.image.VolatileImage createCompatibleVolatileImage(int width, int height) {
        return new net.runelite.awt.impl.BufferedBackedVolatileImage(width, height, Transparency.OPAQUE);
    }

    public java.awt.image.VolatileImage createCompatibleVolatileImage(int width, int height, int transparency) {
        return new net.runelite.awt.impl.BufferedBackedVolatileImage(width, height, transparency);
    }

    public java.awt.image.VolatileImage createCompatibleVolatileImage(int width, int height,
            ImageCapabilities caps) throws AWTException {
        return createCompatibleVolatileImage(width, height, caps, Transparency.OPAQUE);
    }

    /** As on desktop: only a request for acceleration the image cannot give fails. */
    public java.awt.image.VolatileImage createCompatibleVolatileImage(int width, int height,
            ImageCapabilities caps, int transparency) throws AWTException {
        java.awt.image.VolatileImage vi = createCompatibleVolatileImage(width, height, transparency);
        if (caps != null && caps.isAccelerated() && !vi.getCapabilities().isAccelerated()) {
            throw new AWTException("Supplied image capabilities could not be met by this graphics configuration.");
        }
        return vi;
    }

    /** Blitted, unaccelerated buffers: the software raster pipeline has no page flipping. */
    public BufferCapabilities getBufferCapabilities() {
        ImageCapabilities caps = getImageCapabilities();
        return new BufferCapabilities(caps, caps, null);
    }

    public ImageCapabilities getImageCapabilities() {
        return new ImageCapabilities(false);
    }
    public java.awt.image.BufferedImage createCompatibleImage(int width,int height){return createCompatibleImage(width,height,Transparency.OPAQUE);}
    public java.awt.image.BufferedImage createCompatibleImage(int width,int height,int transparency){return new java.awt.image.BufferedImage(width,height,transparency==Transparency.OPAQUE?java.awt.image.BufferedImage.TYPE_INT_RGB:java.awt.image.BufferedImage.TYPE_INT_ARGB);}
}
