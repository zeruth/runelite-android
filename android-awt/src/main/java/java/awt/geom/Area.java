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

import net.runelite.awt.impl.Contours;
import net.runelite.awt.impl.PolyClip;

/**
 * Area backed by a normalized polygon set: non-overlapping loops, outer boundaries and
 * holes oppositely oriented, built and combined by {@link PolyClip}. Unions merge into a
 * single outline (quest-helper unions scenery hulls and then draws + fills the result, so
 * overlap seams or double-blended fill would show), and subtraction is real (RuneLite's
 * world map overlay clips to a rect minus the overview / surface-selector widgets).
 *
 * Curves are flattened on the way in, so {@link #isPolygonal()} is always true and path
 * iteration only yields lines; the flattening tolerance scales with the shape's size.
 */
public class Area implements Shape, Cloneable {
    private static final double[][] EMPTY = new double[0][];

    /** Normalized loops (x,y pairs, implicitly closed). Never mutated in place — clones
     *  share the arrays. */
    private double[][] loops = EMPTY;
    private Rectangle2D cachedBounds;

    public Area() {
    }

    public Area(Shape s) {
        if (s == null) throw new NullPointerException();
        if (s instanceof Area) {
            loops = ((Area) s).loops;
            return;
        }
        if (s instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) s;
            double x = r.getX(), y = r.getY(), w = r.getWidth(), h = r.getHeight();
            if (w > 0 && h > 0) loops = new double[][] {{x, y, x, y + h, x + w, y + h, x + w, y}};
            return;
        }
        PathIterator it;
        try { it = s.getPathIterator(null); } catch (Throwable t) { it = null; }
        if (it == null) return;
        Contours c = new Contours();
        c.appendFlattened(it, null, 1.0, flatnessFor(s));
        double[][] in = new double[c.n][];
        int off = 0;
        for (int i = 0; i < c.n; i++) {
            in[i] = java.util.Arrays.copyOfRange(c.xy, off, off + c.counts[i] * 2);
            off += c.counts[i] * 2;
        }
        loops = PolyClip.normalize(in, c.isEvenOdd());
    }

    private static double flatnessFor(Shape s) {
        try {
            Rectangle2D b = s.getBounds2D();
            double ext = Math.max(b.getWidth(), b.getHeight());
            if (ext > 0) return Math.max(1e-4, Math.min(0.05, ext * 1e-3));
        } catch (Throwable ignored) {
        }
        return 0.05;
    }

    private void set(double[][] l) {
        loops = l;
        cachedBounds = null;
    }

    public void add(Area rhs) {
        if (rhs == null) throw new NullPointerException();
        set(PolyClip.op(loops, rhs.loops, PolyClip.UNION));
    }

    public void subtract(Area rhs) {
        if (rhs == null) throw new NullPointerException();
        set(PolyClip.op(loops, rhs.loops, PolyClip.DIFFERENCE));
    }

    public void intersect(Area rhs) {
        if (rhs == null) throw new NullPointerException();
        set(PolyClip.op(loops, rhs.loops, PolyClip.INTERSECTION));
    }

    public void exclusiveOr(Area rhs) {
        if (rhs == null) throw new NullPointerException();
        set(PolyClip.op(loops, rhs.loops, PolyClip.XOR));
    }

    public void reset() {
        set(EMPTY);
    }

    public boolean isEmpty() {
        return loops.length == 0;
    }

    public boolean isPolygonal() {
        return true;
    }

    public boolean isRectangular() {
        if (loops.length == 0) return true;
        if (loops.length != 1 || loops[0].length != 8) return false;
        double[] l = loops[0];
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            if (l[i * 2] != l[j * 2] && l[i * 2 + 1] != l[j * 2 + 1]) return false;
        }
        return true;
    }

    public boolean isSingular() {
        return loops.length <= 1;
    }

    @Override
    public Rectangle2D getBounds2D() {
        Rectangle2D b = cachedBounds;
        if (b == null) {
            double[] r = PolyClip.bounds(loops);
            b = r == null ? new Rectangle2D.Double() : new Rectangle2D.Double(r[0], r[1], r[2] - r[0], r[3] - r[1]);
            cachedBounds = b;
        }
        return new Rectangle2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight());
    }

    @Override
    public Rectangle getBounds() {
        if (loops.length == 0) return new Rectangle();
        Rectangle2D b = getBounds2D();
        int x0 = (int) Math.floor(b.getMinX()), y0 = (int) Math.floor(b.getMinY());
        int x1 = (int) Math.ceil(b.getMaxX()), y1 = (int) Math.ceil(b.getMaxY());
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }

    @Override
    public boolean contains(double x, double y) {
        if (loops.length == 0) return false;
        Rectangle2D b = getBounds2D();
        if (x < b.getMinX() || y < b.getMinY() || x >= b.getMaxX() || y >= b.getMaxY()) return false;
        return PolyClip.contains(loops, x, y);
    }

    public boolean contains(Point2D p) {
        return contains(p.getX(), p.getY());
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        if (w <= 0 || h <= 0 || loops.length == 0) return false;
        if (!getBounds2D().contains(x, y, w, h)) return false;
        // Inside iff nothing of the rect is left once the area is taken away.
        double[][] rect = {{x, y, x, y + h, x + w, y + h, x + w, y}};
        return PolyClip.op(rect, loops, PolyClip.DIFFERENCE).length == 0;
    }

    public boolean contains(Rectangle2D r) {
        return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        if (w <= 0 || h <= 0 || loops.length == 0) return false;
        if (!getBounds2D().intersects(x, y, w, h)) return false;
        double[][] rect = {{x, y, x, y + h, x + w, y + h, x + w, y}};
        return PolyClip.op(rect, loops, PolyClip.INTERSECTION).length != 0;
    }

    public boolean intersects(Rectangle2D r) {
        return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    public void transform(AffineTransform t) {
        if (t == null) throw new NullPointerException();
        if (loops.length == 0 || t.isIdentity()) return;
        double det = t.getDeterminant();
        if (det == 0 || Double.isNaN(det)) {
            // Collapsed to a line or point: no area left.
            reset();
            return;
        }
        double[][] out = new double[loops.length][];
        for (int i = 0; i < loops.length; i++) {
            double[] l = loops[i];
            double[] m = new double[l.length];
            t.transform(l, 0, m, 0, l.length / 2);
            // A reflection flips every loop; reverse to keep the outer/hole orientation
            // convention PolyClip's output (and its queries) are written against.
            if (det < 0) {
                int n = m.length / 2;
                for (int a = 0, b = n - 1; a < b; a++, b--) {
                    double tx = m[a * 2], ty = m[a * 2 + 1];
                    m[a * 2] = m[b * 2]; m[a * 2 + 1] = m[b * 2 + 1];
                    m[b * 2] = tx; m[b * 2 + 1] = ty;
                }
            }
            out[i] = m;
        }
        set(out);
    }

    public Area createTransformedArea(AffineTransform t) {
        Area a = new Area(this);
        a.transform(t);
        return a;
    }

    /** Geometric equality, as in the JDK: true iff the symmetric difference is empty. */
    public boolean equals(Area other) {
        if (other == this) return true;
        if (other == null) return false;
        return PolyClip.op(loops, other.loops, PolyClip.XOR).length == 0;
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return new LoopIterator(loops, at);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at, double flatness) {
        return new LoopIterator(loops, at);
    }

    @Override
    public Object clone() {
        return new Area(this);
    }

    private static final class LoopIterator implements PathIterator {
        private final double[][] loops;
        private final AffineTransform at;
        private int loop;
        /** Vertex index within the loop; == vertex count means the SEG_CLOSE. */
        private int v;

        LoopIterator(double[][] loops, AffineTransform at) {
            this.loops = loops;
            this.at = at;
        }

        @Override public int getWindingRule() { return WIND_NON_ZERO; }
        @Override public boolean isDone() { return loop >= loops.length; }

        @Override public void next() {
            if (loop >= loops.length) return;
            if (++v > loops[loop].length / 2) {
                loop++;
                v = 0;
            }
        }

        @Override public int currentSegment(double[] coords) {
            double[] l = loops[loop];
            if (v == l.length / 2) return SEG_CLOSE;
            if (at != null) at.transform(l, v * 2, coords, 0, 1);
            else { coords[0] = l[v * 2]; coords[1] = l[v * 2 + 1]; }
            return v == 0 ? SEG_MOVETO : SEG_LINETO;
        }

        @Override public int currentSegment(float[] coords) {
            double[] d = new double[2];
            int type = currentSegment(d);
            if (type != SEG_CLOSE) { coords[0] = (float) d[0]; coords[1] = (float) d[1]; }
            return type;
        }
    }
}
