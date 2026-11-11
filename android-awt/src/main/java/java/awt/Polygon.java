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

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

public class Polygon implements Shape, Serializable {
    private static final long serialVersionUID = -6460061437900069969L;

    public int npoints;
    public int[] xpoints;
    public int[] ypoints;
    protected Rectangle bounds;

    private static final int MIN_LENGTH = 4;

    public Polygon() {
        xpoints = new int[MIN_LENGTH];
        ypoints = new int[MIN_LENGTH];
    }

    public Polygon(int[] xpoints, int[] ypoints, int npoints) {
        if (npoints > xpoints.length || npoints > ypoints.length) {
            throw new IndexOutOfBoundsException("npoints > xpoints.length || npoints > ypoints.length");
        }
        if (npoints < 0) {
            throw new NegativeArraySizeException("npoints < 0");
        }
        this.npoints = npoints;
        this.xpoints = new int[npoints];
        this.ypoints = new int[npoints];
        System.arraycopy(xpoints, 0, this.xpoints, 0, npoints);
        System.arraycopy(ypoints, 0, this.ypoints, 0, npoints);
        calculateBounds(this.xpoints, this.ypoints, npoints);
    }

    public void reset() {
        npoints = 0;
        bounds = null;
    }

    public void invalidate() {
        bounds = null;
    }

    public void translate(int deltaX, int deltaY) {
        for (int i = 0; i < npoints; i++) {
            xpoints[i] += deltaX;
            ypoints[i] += deltaY;
        }
        if (bounds != null) {
            bounds.translate(deltaX, deltaY);
        }
    }

    public void addPoint(int x, int y) {
        if (npoints >= xpoints.length || npoints >= ypoints.length) {
            int newLength = npoints * 2;
            if (newLength < MIN_LENGTH) newLength = MIN_LENGTH;
            int[] nx = new int[newLength];
            int[] ny = new int[newLength];
            System.arraycopy(xpoints, 0, nx, 0, npoints);
            System.arraycopy(ypoints, 0, ny, 0, npoints);
            xpoints = nx;
            ypoints = ny;
        }
        xpoints[npoints] = x;
        ypoints[npoints] = y;
        npoints++;
        if (bounds != null) {
            updateBounds(x, y);
        }
    }

    public Rectangle getBoundingBox() {
        if (npoints == 0) return new Rectangle();
        if (bounds == null) calculateBounds(xpoints, ypoints, npoints);
        return bounds.getBounds();
    }

    @Override
    public Rectangle getBounds() {
        return getBoundingBox();
    }

    @Override
    public Rectangle2D getBounds2D() {
        return getBounds();
    }

    public boolean contains(Point p) {
        return contains(p.x, p.y);
    }

    public boolean contains(int x, int y) {
        return contains((double) x, (double) y);
    }

    @Override
    public boolean contains(double x, double y) {
        if (npoints <= 2 || !getBoundingBox().contains(x, y)) return false;
        int hits = 0;
        int lastx = xpoints[npoints - 1];
        int lasty = ypoints[npoints - 1];
        int curx, cury;
        for (int i = 0; i < npoints; lastx = curx, lasty = cury, i++) {
            curx = xpoints[i];
            cury = ypoints[i];
            if (cury == lasty) continue;
            int leftx;
            if (curx < lastx) {
                if (x >= lastx) continue;
                leftx = curx;
            } else {
                if (x >= curx) continue;
                leftx = lastx;
            }
            double test1, test2;
            if (cury < lasty) {
                if (y < cury || y >= lasty) continue;
                if (x < leftx) { hits++; continue; }
                test1 = x - curx;
                test2 = y - cury;
            } else {
                if (y < lasty || y >= cury) continue;
                if (x < leftx) { hits++; continue; }
                test1 = x - lastx;
                test2 = y - lasty;
            }
            if (test1 < (test2 / (lasty - cury) * (lastx - curx))) hits++;
        }
        return ((hits & 1) != 0);
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        // Conservative: only return true if entire rect bounding box is contained and
        // all four corners are inside. Matches OpenJDK behaviour closely enough for
        // RuneLite's use of Polygon.contains.
        if (npoints <= 0 || !getBoundingBox().intersects((int) x, (int) y, (int) w, (int) h)) return false;
        return contains(x, y) && contains(x + w, y) && contains(x, y + h) && contains(x + w, y + h);
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        if (npoints <= 0 || !getBoundingBox().intersects((int) x, (int) y, (int) w, (int) h)) return false;
        // Cheap approximation: bounding-box intersection. Refine later if needed.
        return true;
    }

    @Override
    public java.awt.geom.PathIterator getPathIterator(java.awt.geom.AffineTransform at) {
        return new PolygonPathIterator(this, at);
    }

    @Override
    public java.awt.geom.PathIterator getPathIterator(java.awt.geom.AffineTransform at, double flatness) {
        return getPathIterator(at);
    }

    private static final class PolygonPathIterator implements java.awt.geom.PathIterator {
        private final Polygon poly;
        private final java.awt.geom.AffineTransform at;
        private int idx;

        PolygonPathIterator(Polygon p, java.awt.geom.AffineTransform at) {
            this.poly = p;
            this.at = at;
            this.idx = (p.npoints == 0) ? 1 : 0;
        }

        @Override public int getWindingRule() { return WIND_EVEN_ODD; }
        @Override public boolean isDone() { return idx > poly.npoints; }
        @Override public void next() { idx++; }

        @Override
        public int currentSegment(float[] coords) {
            if (idx >= poly.npoints) return SEG_CLOSE;
            coords[0] = poly.xpoints[idx];
            coords[1] = poly.ypoints[idx];
            if (at != null) at.transform(coords, 0, coords, 0, 1);
            return (idx == 0) ? SEG_MOVETO : SEG_LINETO;
        }

        @Override
        public int currentSegment(double[] coords) {
            if (idx >= poly.npoints) return SEG_CLOSE;
            coords[0] = poly.xpoints[idx];
            coords[1] = poly.ypoints[idx];
            if (at != null) at.transform(coords, 0, coords, 0, 1);
            return (idx == 0) ? SEG_MOVETO : SEG_LINETO;
        }
    }

    private void calculateBounds(int[] xp, int[] yp, int np) {
        int boundsMinX = Integer.MAX_VALUE;
        int boundsMinY = Integer.MAX_VALUE;
        int boundsMaxX = Integer.MIN_VALUE;
        int boundsMaxY = Integer.MIN_VALUE;
        for (int i = 0; i < np; i++) {
            int x = xp[i];
            boundsMinX = Math.min(boundsMinX, x);
            boundsMaxX = Math.max(boundsMaxX, x);
            int y = yp[i];
            boundsMinY = Math.min(boundsMinY, y);
            boundsMaxY = Math.max(boundsMaxY, y);
        }
        bounds = new Rectangle(boundsMinX, boundsMinY,
            boundsMaxX - boundsMinX, boundsMaxY - boundsMinY);
    }

    private void updateBounds(int x, int y) {
        if (x < bounds.x) {
            bounds.width = bounds.width + (bounds.x - x);
            bounds.x = x;
        } else {
            bounds.width = Math.max(bounds.width, x - bounds.x);
        }
        if (y < bounds.y) {
            bounds.height = bounds.height + (bounds.y - y);
            bounds.y = y;
        } else {
            bounds.height = Math.max(bounds.height, y - bounds.y);
        }
    }
}
