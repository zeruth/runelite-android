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

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.util.Objects;

/** An image repeated in user space over an anchor rectangle. */
public class TexturePaint implements Paint {
    private final BufferedImage image;
    private final Rectangle2D anchor;

    public TexturePaint(BufferedImage image, Rectangle2D anchor) {
        this.image = Objects.requireNonNull(image, "image");
        this.anchor = new Rectangle2D.Double(anchor.getX(), anchor.getY(),
            anchor.getWidth(), anchor.getHeight());
    }

    public BufferedImage getImage() { return image; }
    public Rectangle2D getAnchorRect() {
        return new Rectangle2D.Double(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight());
    }
    @Override public int getTransparency() { return image.getColorModel().getTransparency(); }

    @Override public PaintContext createContext(ColorModel cm, Rectangle bounds,
            Rectangle2D userBounds, AffineTransform xform, RenderingHints hints) {
        final double a = xform.getScaleX(), b = xform.getShearX(), c = xform.getTranslateX();
        final double d = xform.getShearY(), e = xform.getScaleY(), f = xform.getTranslateY();
        final double det = a * e - b * d;
        return new PaintContext() {
            private BufferedImage buffer;
            @Override public void dispose() { buffer = null; }
            @Override public ColorModel getColorModel() {
                return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getColorModel();
            }
            @Override public Raster getRaster(int x, int y, int w, int h) {
                if (buffer == null || buffer.getWidth() != w || buffer.getHeight() != h)
                    buffer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int row = 0; row < h; row++) {
                    for (int col = 0; col < w; col++) {
                        double dx = x + col + 0.5 - c, dy = y + row + 0.5 - f;
                        double ux = det == 0 ? 0 : (e * dx - b * dy) / det;
                        double uy = det == 0 ? 0 : (a * dy - d * dx) / det;
                        double u = (ux - anchor.getX()) / anchor.getWidth();
                        double v = (uy - anchor.getY()) / anchor.getHeight();
                        int sx = (int) ((u - Math.floor(u)) * image.getWidth());
                        int sy = (int) ((v - Math.floor(v)) * image.getHeight());
                        buffer.setRGB(col, row, image.getRGB(sx, sy));
                    }
                }
                return buffer.getRaster();
            }
        };
    }
}
