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

public abstract class Point2D implements Cloneable, Serializable {
    private static final long serialVersionUID = 6150783262733311327L;

    protected Point2D() {
    }

    public abstract double getX();
    public abstract double getY();
    public abstract void setLocation(double x, double y);

    public void setLocation(Point2D p) {
        setLocation(p.getX(), p.getY());
    }

    public static double distanceSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    public static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(distanceSq(x1, y1, x2, y2));
    }

    public double distanceSq(double px, double py) { return distanceSq(getX(), getY(), px, py); }
    public double distanceSq(Point2D pt) { return distanceSq(getX(), getY(), pt.getX(), pt.getY()); }
    public double distance(double px, double py) { return Math.sqrt(distanceSq(px, py)); }
    public double distance(Point2D pt) { return Math.sqrt(distanceSq(pt)); }

    @Override
    public int hashCode() {
        long bits = java.lang.Double.doubleToLongBits(getX());
        bits ^= java.lang.Double.doubleToLongBits(getY()) * 31;
        return (int) bits ^ (int) (bits >> 32);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Point2D) {
            Point2D p2d = (Point2D) obj;
            return getX() == p2d.getX() && getY() == p2d.getY();
        }
        return false;
    }

    @Override
    public Object clone() {
        try { return super.clone(); } catch (CloneNotSupportedException e) { throw new InternalError(e); }
    }

    public static class Double extends Point2D {
        public double x, y;
        public Double() {}
        public Double(double x, double y) { this.x = x; this.y = y; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public void setLocation(double x, double y) { this.x = x; this.y = y; }
    }

    public static class Float extends Point2D {
        public float x, y;
        public Float() {}
        public Float(float x, float y) { this.x = x; this.y = y; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public void setLocation(double x, double y) { this.x = (float) x; this.y = (float) y; }
        public void setLocation(float x, float y) { this.x = x; this.y = y; }
    }
}
