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

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;

/**
 * Growable list of polyline contours (x,y pairs) — the common currency between path
 * flattening, stroking, Area's polygon clipper and the native {@code fillPath}. Reused
 * through thread-local scratch on the draw paths, so it never shrinks.
 */
public final class Contours {
    public double[] xy = new double[64];
    /** Doubles used in {@link #xy}. */
    public int nCoords;
    /** Vertex count per contour. */
    public int[] counts = new int[8];
    public boolean[] closed = new boolean[8];
    public int n;
    public int windingRule = PathIterator.WIND_NON_ZERO;

    private int start = -1;
    private double startX, startY;

    public void reset() {
        nCoords = 0;
        n = 0;
        start = -1;
        pending = false;
        windingRule = PathIterator.WIND_NON_ZERO;
    }

    public boolean isEvenOdd() { return windingRule == PathIterator.WIND_EVEN_ODD; }

    public void moveTo(double x, double y) {
        end(false);
        pending = false;
        ensureContour();
        start = nCoords;
        startX = x;
        startY = y;
        counts[n] = 0;
        closed[n] = false;
        point(x, y);
    }

    public void lineTo(double x, double y) {
        if (start < 0) {
            moveTo(x, y);
            return;
        }
        int c = counts[n];
        if (c > 0 && xy[nCoords - 2] == x && xy[nCoords - 1] == y) return;
        point(x, y);
    }

    /** Ends the path: a close followed by nothing must not leak its start point into the
     *  next path appended to this (reused) buffer. */
    private void finish() {
        end(false);
        pending = false;
    }

    public void close() {
        if (start < 0) return;
        end(true);
        // A segment after a close without a moveTo starts at the closed contour's start.
        pendingX = startX;
        pendingY = startY;
        pending = true;
    }

    private boolean pending;
    private double pendingX, pendingY;

    /** Finish the current contour (if any). */
    public void end(boolean close) {
        if (start < 0) return;
        int c = counts[n];
        // Drop a closing vertex that repeats the start.
        if (c > 1 && xy[nCoords - 2] == xy[start] && xy[nCoords - 1] == xy[start + 1]) {
            nCoords -= 2;
            c--;
            counts[n] = c;
        }
        if (c > 0) {
            closed[n] = close;
            n++;
        } else {
            nCoords = start;
        }
        start = -1;
        pending = false;
    }

    /** Append a whole contour. */
    public void add(double[] pts, int off, int count, boolean close) {
        end(false);
        ensureContour();
        ensureCoords(count * 2);
        System.arraycopy(pts, off, xy, nCoords, count * 2);
        nCoords += count * 2;
        counts[n] = count;
        closed[n] = close;
        n++;
    }

    private void point(double x, double y) {
        ensureCoords(2);
        xy[nCoords++] = x;
        xy[nCoords++] = y;
        counts[n]++;
    }

    private void ensureContour() {
        if (n >= counts.length) {
            counts = java.util.Arrays.copyOf(counts, counts.length * 2);
            closed = java.util.Arrays.copyOf(closed, closed.length * 2);
        }
    }

    public void ensureCoords(int extra) {
        if (nCoords + extra > xy.length) {
            xy = java.util.Arrays.copyOf(xy, Math.max(xy.length * 2, nCoords + extra));
        }
    }

    /** Offset of contour {@code i} in {@link #xy}, by walking counts (contours are few). */
    public int offsetOf(int i) {
        int off = 0;
        for (int k = 0; k < i; k++) off += counts[k] * 2;
        return off;
    }

    // ---------------------------------------------------------------------------------
    // Flattening
    // ---------------------------------------------------------------------------------

    /**
     * Device-space chord tolerance of the JDK's software fill, clip and thin-stroke loops:
     * ShapeSpanIterator.c subdivides a curve until every control point is within
     * {@code sqrt(MAX_FLAT_SQ) = 1.0} px of its chord. Fills, clips and thin draws pass this
     * so curves come out with the same segment count as on desktop.
     */
    public static final double JDK_FLATNESS = 1.0;

    /**
     * Append {@code it}'s path, with quadratic / cubic segments subdivided until within
     * {@code flatness} of the true curve. The backend flattens itself rather than asking
     * {@code getPathIterator(at, flatness)} so the tolerance is applied in device space,
     * after {@code at} and {@code scale} (the graphics deviceScale), which is where the
     * JDK's software loops measure it too (ShapeSpanIterator.c, MAX_FLAT_SQ).
     */
    public void appendFlattened(PathIterator it, AffineTransform at, double scale, double flatness) {
        if (it == null) return;
        windingRule = it.getWindingRule();
        double[] c = new double[6];
        double cx = 0, cy = 0;
        boolean have = false;
        double tol = Math.max(flatness, 1e-6);
        while (!it.isDone()) {
            int seg = it.currentSegment(c);
            if (at != null) at.transform(c, 0, c, 0, seg == PathIterator.SEG_CUBICTO ? 3 : seg == PathIterator.SEG_QUADTO ? 2 : 1);
            if (scale != 1.0) for (int k = 0; k < 6; k++) c[k] *= scale;
            if (pending && seg != PathIterator.SEG_MOVETO && seg != PathIterator.SEG_CLOSE) {
                moveTo(pendingX, pendingY);
                cx = pendingX; cy = pendingY;
                have = true;
            }
            switch (seg) {
                case PathIterator.SEG_MOVETO:
                    moveTo(c[0], c[1]);
                    cx = c[0]; cy = c[1];
                    have = true;
                    break;
                case PathIterator.SEG_LINETO:
                    lineTo(c[0], c[1]);
                    cx = c[0]; cy = c[1];
                    have = true;
                    break;
                case PathIterator.SEG_QUADTO:
                    if (!have) moveTo(cx, cy);
                    quad(cx, cy, c[0], c[1], c[2], c[3], tol, 0);
                    cx = c[2]; cy = c[3];
                    have = true;
                    break;
                case PathIterator.SEG_CUBICTO:
                    if (!have) moveTo(cx, cy);
                    cubic(cx, cy, c[0], c[1], c[2], c[3], c[4], c[5], tol, 0);
                    cx = c[4]; cy = c[5];
                    have = true;
                    break;
                case PathIterator.SEG_CLOSE:
                    if (start >= 0) {
                        cx = startX; cy = startY;
                    }
                    close();
                    have = false;
                    break;
                default:
                    break;
            }
            it.next();
        }
        finish();
    }

    private void quad(double x0, double y0, double x1, double y1, double x2, double y2, double tol, int depth) {
        if (depth >= 16 || distToChord(x1, y1, x0, y0, x2, y2) <= tol) {
            lineTo(x2, y2);
            return;
        }
        double ax = (x0 + x1) / 2, ay = (y0 + y1) / 2;
        double bx = (x1 + x2) / 2, by = (y1 + y2) / 2;
        double mx = (ax + bx) / 2, my = (ay + by) / 2;
        quad(x0, y0, ax, ay, mx, my, tol, depth + 1);
        quad(mx, my, bx, by, x2, y2, tol, depth + 1);
    }

    private void cubic(double x0, double y0, double x1, double y1, double x2, double y2,
                       double x3, double y3, double tol, int depth) {
        if (depth >= 16 || Math.max(distToChord(x1, y1, x0, y0, x3, y3),
                                    distToChord(x2, y2, x0, y0, x3, y3)) <= tol) {
            lineTo(x3, y3);
            return;
        }
        double ax = (x0 + x1) / 2, ay = (y0 + y1) / 2;
        double bx = (x1 + x2) / 2, by = (y1 + y2) / 2;
        double cx = (x2 + x3) / 2, cy = (y2 + y3) / 2;
        double dx = (ax + bx) / 2, dy = (ay + by) / 2;
        double ex = (bx + cx) / 2, ey = (by + cy) / 2;
        double mx = (dx + ex) / 2, my = (dy + ey) / 2;
        cubic(x0, y0, ax, ay, dx, dy, mx, my, tol, depth + 1);
        cubic(mx, my, ex, ey, cx, cy, x3, y3, tol, depth + 1);
    }

    private static double distToChord(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-12) return Math.hypot(px - x0, py - y0);
        return Math.abs((px - x0) * dy - (py - y0) * dx) / len;
    }
}
