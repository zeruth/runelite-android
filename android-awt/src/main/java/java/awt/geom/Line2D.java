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
import java.io.Serializable;

/**
 * Stub of {@link java.awt.geom.Line2D}. Quest-helper's DirectionArrow constructs
 * {@code Line2D.Double(x1, y1, x2, y2)} and reads {@code line.x1/y1/x2/y2} to draw
 * minimap and world arrows. We don't implement actual path iteration / hit testing —
 * the host's Graphics2D draws straight lines via {@code draw(Shape)} → drawLine on
 * the {x1,y1,x2,y2} fields.
 */
public abstract class Line2D implements Shape, Cloneable {
    protected Line2D() {}

    public abstract double getX1();
    public abstract double getY1();
    public abstract double getX2();
    public abstract double getY2();
    public abstract Point2D getP1();
    public abstract Point2D getP2();
    public abstract void setLine(double x1, double y1, double x2, double y2);

    public void setLine(Point2D p1, Point2D p2) {
        setLine(p1.getX(), p1.getY(), p2.getX(), p2.getY());
    }

    public void setLine(Line2D l) {
        setLine(l.getX1(), l.getY1(), l.getX2(), l.getY2());
    }

    @Override
    public Rectangle getBounds() {
        double x = Math.min(getX1(), getX2());
        double y = Math.min(getY1(), getY2());
        double w = Math.abs(getX2() - getX1());
        double h = Math.abs(getY2() - getY1());
        return new Rectangle((int) Math.floor(x), (int) Math.floor(y),
            (int) Math.ceil(w), (int) Math.ceil(h));
    }

    @Override public boolean contains(double x, double y) { return false; }
    @Override public boolean contains(double x, double y, double w, double h) { return false; }
    @Override public boolean contains(Point2D p) { return false; }
    @Override public boolean contains(Rectangle2D r) { return false; }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return getBounds2D().intersects(x, y, w, h);
    }

    @Override
    public boolean intersects(Rectangle2D r) {
        return getBounds2D().intersects(r);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return new LineIterator(this, at);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at, double flatness) {
        return new LineIterator(this, at);
    }

    @Override
    public Object clone() {
        try { return super.clone(); }
        catch (CloneNotSupportedException e) { throw new InternalError(e); }
    }

    public static class Double extends Line2D implements Serializable {
        private static final long serialVersionUID = 7979627399746467499L;
        public double x1, y1, x2, y2;
        public Double() {}
        public Double(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        public Double(Point2D p1, Point2D p2) {
            this(p1.getX(), p1.getY(), p2.getX(), p2.getY());
        }
        @Override public double getX1() { return x1; }
        @Override public double getY1() { return y1; }
        @Override public double getX2() { return x2; }
        @Override public double getY2() { return y2; }
        @Override public Point2D getP1() { return new Point2D.Double(x1, y1); }
        @Override public Point2D getP2() { return new Point2D.Double(x2, y2); }
        @Override public void setLine(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        @Override public Rectangle2D getBounds2D() {
            double x = Math.min(x1, x2);
            double y = Math.min(y1, y2);
            return new Rectangle2D.Double(x, y, Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    public static class Float extends Line2D implements Serializable {
        private static final long serialVersionUID = 6009674085554122265L;
        public float x1, y1, x2, y2;
        public Float() {}
        public Float(float x1, float y1, float x2, float y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        public Float(Point2D p1, Point2D p2) {
            this((float) p1.getX(), (float) p1.getY(), (float) p2.getX(), (float) p2.getY());
        }
        @Override public double getX1() { return x1; }
        @Override public double getY1() { return y1; }
        @Override public double getX2() { return x2; }
        @Override public double getY2() { return y2; }
        @Override public Point2D getP1() { return new Point2D.Float(x1, y1); }
        @Override public Point2D getP2() { return new Point2D.Float(x2, y2); }
        @Override public void setLine(double x1, double y1, double x2, double y2) {
            this.x1 = (float) x1; this.y1 = (float) y1; this.x2 = (float) x2; this.y2 = (float) y2;
        }
        public void setLine(float x1, float y1, float x2, float y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        @Override public Rectangle2D getBounds2D() {
            float x = Math.min(x1, x2);
            float y = Math.min(y1, y2);
            return new Rectangle2D.Float(x, y, Math.abs(x2 - x1), Math.abs(y2 - y1));
        }
    }

    // Two segments (SEG_MOVETO + SEG_LINETO), then done.
    private static final class LineIterator implements PathIterator {
        private final double x1, y1, x2, y2;
        private final AffineTransform at;
        private int index = 0;

        LineIterator(Line2D src, AffineTransform at) {
            this.x1 = src.getX1();
            this.y1 = src.getY1();
            this.x2 = src.getX2();
            this.y2 = src.getY2();
            this.at = at;
        }

        @Override public int getWindingRule() { return WIND_NON_ZERO; }
        @Override public boolean isDone() { return index > 1; }
        @Override public void next() { index++; }

        @Override
        public int currentSegment(float[] coords) {
            if (index == 0) { coords[0] = (float) x1; coords[1] = (float) y1; }
            else { coords[0] = (float) x2; coords[1] = (float) y2; }
            if (at != null) at.transform(coords, 0, coords, 0, 1);
            return index == 0 ? SEG_MOVETO : SEG_LINETO;
        }

        @Override
        public int currentSegment(double[] coords) {
            if (index == 0) { coords[0] = x1; coords[1] = y1; }
            else { coords[0] = x2; coords[1] = y2; }
            if (at != null) at.transform(coords, 0, coords, 0, 1);
            return index == 0 ? SEG_MOVETO : SEG_LINETO;
        }
    }
}
