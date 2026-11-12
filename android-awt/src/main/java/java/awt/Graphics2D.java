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
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;

public abstract class Graphics2D extends Graphics {
    protected Graphics2D() {
    }

    public abstract void draw(Shape s);
    public abstract void fill(Shape s);
    public GraphicsConfiguration getDeviceConfiguration() {
        return net.runelite.awt.impl.HeadlessGraphicsConfiguration.INSTANCE;
    }

    /** Intersect the clip with {@code s}; null clears the clip (JDK semantics). The default
     *  is a bounds approximation for Graphics2D subclasses that don't override it. */
    public void clip(Shape s) {
        if (s == null) {
            setClip(null);
            return;
        }
        Rectangle b = s.getBounds();
        clipRect(b.x, b.y, b.width, b.height);
    }
    public abstract boolean drawImage(java.awt.Image img, AffineTransform xform, ImageObserver obs);
    public abstract void drawImage(BufferedImage img, int x, int y);

    public void drawImage(BufferedImage image, java.awt.image.BufferedImageOp operation, int x, int y) {
        drawImage(operation == null ? image : operation.filter(image, null), x, y, null);
    }
    public void drawRenderedImage(java.awt.image.RenderedImage source, AffineTransform transform) {
        if (source == null) return;
        if (source instanceof BufferedImage) { drawImage((BufferedImage)source, transform, null); return; }
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.image.Raster raster = source.getData(); java.awt.image.ColorModel model = source.getColorModel(); Object pixel = null;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            pixel = raster.getDataElements(source.getMinX() + x, source.getMinY() + y, pixel);
            image.setRGB(x, y, model.getRGB(pixel));
        }
        AffineTransform positioned = new AffineTransform(transform); positioned.translate(source.getMinX(), source.getMinY());
        drawImage(image, positioned, null);
    }

    /**
     * Renders {@code img} at device resolution: the rendering is requested for the user
     * transform concatenated with the current one and drawn back through the inverse of
     * the current transform. Same order of concatenation as desktop's SunGraphics2D, so a
     * RenderableImage sees the same RenderContext transform; a null rendering draws
     * nothing.
     */
    public void drawRenderableImage(java.awt.image.renderable.RenderableImage img, AffineTransform xform) {
        if (img == null) {
            return;
        }
        AffineTransform pipeTransform = getTransform();
        AffineTransform concatTransform = new AffineTransform(xform);
        concatTransform.concatenate(pipeTransform);
        AffineTransform reverseTransform;
        java.awt.image.renderable.RenderContext rc = new java.awt.image.renderable.RenderContext(concatTransform);
        try {
            reverseTransform = pipeTransform.createInverse();
        } catch (java.awt.geom.NoninvertibleTransformException nte) {
            rc = new java.awt.image.renderable.RenderContext(pipeTransform);
            reverseTransform = new AffineTransform();
        }
        java.awt.image.RenderedImage rendering = img.createRendering(rc);
        drawRenderedImage(rendering, reverseTransform);
    }

    public boolean hit(Rectangle rectangle, Shape shape, boolean onStroke) {
        Shape rendered = onStroke ? getStroke().createStrokedShape(shape) : shape;
        return getTransform().createTransformedShape(rendered).intersects(rectangle);
    }

    public abstract Composite getComposite();
    public abstract void setComposite(Composite comp);

    public abstract Paint getPaint();
    public abstract void setPaint(Paint paint);

    public abstract Stroke getStroke();
    public abstract void setStroke(Stroke s);

    public abstract AffineTransform getTransform();
    public abstract void setTransform(AffineTransform tx);
    public abstract void transform(AffineTransform tx);
    public abstract void translate(double tx, double ty);
    public abstract void rotate(double theta);
    public abstract void rotate(double theta, double x, double y);
    public abstract void scale(double sx, double sy);
    public abstract void shear(double shx, double shy);

    public abstract void setRenderingHint(RenderingHints.Key hintKey, Object hintValue);
    public abstract Object getRenderingHint(RenderingHints.Key hintKey);
    public abstract void setRenderingHints(java.util.Map<?, ?> hints);
    public abstract void addRenderingHints(java.util.Map<?, ?> hints);
    public abstract RenderingHints getRenderingHints();

    public abstract Color getBackground();
    public abstract void setBackground(Color color);

    public abstract void drawString(java.text.AttributedCharacterIterator iterator, int x, int y);
    public abstract void drawString(java.text.AttributedCharacterIterator iterator, float x, float y);
    public abstract void drawString(String str, float x, float y);

    public abstract java.awt.font.FontRenderContext getFontRenderContext();
}
