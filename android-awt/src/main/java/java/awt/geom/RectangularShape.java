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
package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

public abstract class RectangularShape implements Shape, Cloneable {
    protected RectangularShape() {}

    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public abstract boolean isEmpty();
    public abstract void setFrame(double x, double y, double w, double h);

    public double getMinX() { return getX(); }
    public double getMinY() { return getY(); }
    public double getMaxX() { return getX() + getWidth(); }
    public double getMaxY() { return getY() + getHeight(); }
    public double getCenterX() { return getX() + getWidth() / 2; }
    public double getCenterY() { return getY() + getHeight() / 2; }

    public Rectangle2D getFrame() { return new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight()); }
    public void setFrame(java.awt.geom.Point2D loc, java.awt.geom.Dimension2D size) {
        setFrame(loc.getX(), loc.getY(), size.getWidth(), size.getHeight());
    }
    public void setFrame(Rectangle2D r) { setFrame(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    public void setFrameFromDiagonal(double x1, double y1, double x2, double y2) {
        if (x2 < x1) { double t = x1; x1 = x2; x2 = t; }
        if (y2 < y1) { double t = y1; y1 = y2; y2 = t; }
        setFrame(x1, y1, x2 - x1, y2 - y1);
    }
    public void setFrameFromDiagonal(Point2D p1, Point2D p2) {
        setFrameFromDiagonal(p1.getX(), p1.getY(), p2.getX(), p2.getY());
    }
    public void setFrameFromCenter(double cx, double cy, double cnX, double cnY) {
        double half_w = Math.abs(cnX - cx);
        double half_h = Math.abs(cnY - cy);
        setFrame(cx - half_w, cy - half_h, half_w * 2, half_h * 2);
    }
    public void setFrameFromCenter(Point2D c, Point2D corner) {
        setFrameFromCenter(c.getX(), c.getY(), corner.getX(), corner.getY());
    }

    public boolean contains(Point2D p) { return contains(p.getX(), p.getY()); }
    public boolean intersects(Rectangle2D r) { return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }
    public boolean contains(Rectangle2D r)   { return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight()); }

    @Override
    public Rectangle getBounds() {
        return new Rectangle((int) Math.floor(getX()), (int) Math.floor(getY()),
            (int) Math.ceil(getWidth()), (int) Math.ceil(getHeight()));
    }

    @Override
    public Object clone() {
        try { return super.clone(); } catch (CloneNotSupportedException e) { throw new InternalError(e); }
    }

    public abstract PathIterator getPathIterator(AffineTransform at);
    public abstract PathIterator getPathIterator(AffineTransform at, double flatness);
}
