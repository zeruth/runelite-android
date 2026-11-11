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

import java.io.Serializable;

public abstract class Arc2D extends RectangularShape {
    public static final int OPEN = 0;
    public static final int CHORD = 1;
    public static final int PIE = 2;

    private int type;

    protected Arc2D(int type) { this.type = type; }

    public abstract double getAngleStart();
    public abstract double getAngleExtent();
    public abstract void setAngleStart(double angSt);
    public abstract void setAngleExtent(double angExt);
    public abstract void setArc(double x, double y, double w, double h, double angSt, double angExt, int closure);

    public int getArcType() { return type; }
    public void setArcType(int type) { this.type = type; }

    @Override
    public Rectangle2D getBounds2D() {
        return new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public boolean contains(double x, double y) {
        return PathGeometry.contains(getPathIterator(null),x,y);
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        return PathGeometry.rectangle(this,x,y,w,h,true);
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        return PathGeometry.rectangle(this,x,y,w,h,false);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return ArcIterator.arc(getX(), getY(), getWidth(), getHeight(),
            getAngleStart(), getAngleExtent(), getArcType(), at);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at, double flatness) {
        return new FlatteningPathIterator(getPathIterator(at),flatness);
    }

    public static class Double extends Arc2D implements Serializable {
        private static final long serialVersionUID = 728264085381197163L;
        public double x, y, width, height, start, extent;
        public Double() { super(OPEN); }
        public Double(int type) { super(type); }
        public Double(double x, double y, double w, double h, double start, double extent, int type) {
            super(type);
            setArc(x, y, w, h, start, extent, type);
        }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getAngleStart() { return start; }
        @Override public double getAngleExtent() { return extent; }
        @Override public void setAngleStart(double angSt) { this.start = angSt; }
        @Override public void setAngleExtent(double angExt) { this.extent = angExt; }
        @Override public boolean isEmpty() { return width <= 0 || height <= 0; }
        @Override public void setFrame(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }
        @Override public void setArc(double x, double y, double w, double h, double angSt, double angExt, int closure) {
            this.x = x; this.y = y; this.width = w; this.height = h;
            this.start = angSt; this.extent = angExt; setArcType(closure);
        }
    }

    public static class Float extends Arc2D implements Serializable {
        private static final long serialVersionUID = 9130349409449579394L;
        public float x, y, width, height, start, extent;
        public Float() { super(OPEN); }
        public Float(int type) { super(type); }
        public Float(float x, float y, float w, float h, float start, float extent, int type) {
            super(type);
            setArc(x, y, w, h, start, extent, type);
        }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getWidth() { return width; }
        @Override public double getHeight() { return height; }
        @Override public double getAngleStart() { return start; }
        @Override public double getAngleExtent() { return extent; }
        @Override public void setAngleStart(double angSt) { this.start = (float) angSt; }
        @Override public void setAngleExtent(double angExt) { this.extent = (float) angExt; }
        @Override public boolean isEmpty() { return width <= 0 || height <= 0; }
        @Override public void setFrame(double x, double y, double w, double h) {
            this.x = (float) x; this.y = (float) y; this.width = (float) w; this.height = (float) h;
        }
        @Override public void setArc(double x, double y, double w, double h, double angSt, double angExt, int closure) {
            this.x = (float) x; this.y = (float) y; this.width = (float) w; this.height = (float) h;
            this.start = (float) angSt; this.extent = (float) angExt; setArcType(closure);
        }
    }
}
