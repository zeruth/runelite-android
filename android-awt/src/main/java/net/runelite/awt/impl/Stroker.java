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

/**
 * BasicStroke outline generation for flattened paths.
 *
 * Instead of computing the exact offset outline, the stroke is emitted as overlapping
 * pieces — one quad per segment, a join polygon per vertex, caps at open ends — all with
 * the same orientation. Filled in a single non-zero pass their union is exactly the
 * stroked area, so a translucent outline blends once even where pieces overlap (corners).
 * Pieces are all written with negative shoelace area, so
 * {@code new Area(stroke.createStrokedShape(s))} sees a consistent winding too.
 */
public final class Stroker {
    private Stroker() {}

    public static final int CAP_BUTT = 0, CAP_ROUND = 1, CAP_SQUARE = 2;
    public static final int JOIN_MITER = 0, JOIN_ROUND = 1, JOIN_BEVEL = 2;

    /** Stroke every contour of {@code path} into {@code out} (cleared first by the caller). */
    public static void stroke(Contours path, double halfWidth, int cap, int join, double miterLimit, Contours out) {
        if (!(halfWidth > 0)) return;
        int off = 0;
        double[] scratch = new double[16];
        for (int c = 0; c < path.n; c++) {
            int cnt = path.counts[c];
            if (scratch.length < cnt * 2) scratch = new double[cnt * 2];
            strokePolyline(path.xy, off, cnt, path.closed[c], halfWidth, cap, join, miterLimit, scratch, out);
            off += cnt * 2;
        }
    }

    /**
     * Split {@code path} into its "on" dash pieces as open contours. {@code dash} and
     * {@code phase} must already be in the path's coordinate space.
     */
    public static void dash(Contours path, float[] dash, double scale, double phase, Contours out) {
        double total = 0;
        boolean any = false;
        for (float d : dash) {
            if (d < 0) return;
            total += d * scale;
            if (d > 0) any = true;
        }
        if (!any || !(total > 0)) {
            // Not a usable pattern: draw solid.
            for (int c = 0, off = 0; c < path.n; c++) {
                out.add(path.xy, off, path.counts[c], path.closed[c]);
                off += path.counts[c] * 2;
            }
            return;
        }
        int off = 0;
        for (int c = 0; c < path.n; c++) {
            int cnt = path.counts[c];
            boolean closed = path.closed[c];
            // Pattern restarts per subpath, at the phase.
            int idx = 0;
            boolean on = true;
            double rem = dash[0] * scale;
            double ph = phase % total;
            if (ph < 0) ph += total;
            while (ph > 0) {
                if (ph >= rem) {
                    ph -= rem;
                    idx = (idx + 1) % dash.length;
                    rem = dash[idx] * scale;
                    on = !on;
                } else {
                    rem -= ph;
                    ph = 0;
                }
            }
            int segs = closed ? cnt : cnt - 1;
            double px = path.xy[off], py = path.xy[off + 1];
            if (on) out.moveTo(px, py);
            for (int s = 0; s < segs; s++) {
                int j = (s + 1) % cnt;
                double qx = path.xy[off + j * 2], qy = path.xy[off + j * 2 + 1];
                double len = Math.hypot(qx - px, qy - py);
                double t = 0;
                while (len - t > rem) {
                    t += rem;
                    double f = t / len;
                    double x = px + (qx - px) * f, y = py + (qy - py) * f;
                    if (on) { out.lineTo(x, y); out.end(false); }
                    else out.moveTo(x, y);
                    on = !on;
                    idx = (idx + 1) % dash.length;
                    rem = dash[idx] * scale;
                }
                rem -= len - t;
                if (on) out.lineTo(qx, qy);
                px = qx; py = qy;
            }
            out.end(false);
            off += cnt * 2;
        }
    }

    private static void strokePolyline(double[] src, int off, int cnt, boolean closed, double hw,
                                       int cap, int join, double miterLimit, double[] p, Contours out) {
        // Drop repeated points; a closed contour's repeated start is dropped too.
        int m = 0;
        for (int i = 0; i < cnt; i++) {
            double x = src[off + i * 2], y = src[off + i * 2 + 1];
            if (m > 0 && p[(m - 1) * 2] == x && p[(m - 1) * 2 + 1] == y) continue;
            p[m * 2] = x;
            p[m * 2 + 1] = y;
            m++;
        }
        if (closed && m > 1 && p[0] == p[(m - 1) * 2] && p[1] == p[(m - 1) * 2 + 1]) m--;
        if (m == 0) return;
        if (m == 1) {
            // Zero-length subpath: a square or round dot, nothing for butt caps.
            double x = p[0], y = p[1];
            if (cap == CAP_SQUARE) addPoly(out, new double[] {x - hw, y - hw, x + hw, y - hw, x + hw, y + hw, x - hw, y + hw}, 4);
            else if (cap == CAP_ROUND) addCircle(out, x, y, hw);
            return;
        }
        if (m == 2) closed = false;
        int segs = closed ? m : m - 1;
        double[] q = new double[8];
        for (int s = 0; s < segs; s++) {
            int j = (s + 1) % m;
            double x1 = p[s * 2], y1 = p[s * 2 + 1], x2 = p[j * 2], y2 = p[j * 2 + 1];
            double dx = x2 - x1, dy = y2 - y1;
            double len = Math.hypot(dx, dy);
            double ux = dx / len, uy = dy / len;
            double nx = -uy * hw, ny = ux * hw;
            double sx = 0, sy = 0, ex = 0, ey = 0;
            if (!closed && cap == CAP_SQUARE) {
                if (s == 0) { sx = ux * hw; sy = uy * hw; }
                if (s == segs - 1) { ex = ux * hw; ey = uy * hw; }
            }
            q[0] = x1 - sx + nx; q[1] = y1 - sy + ny;
            q[2] = x1 - sx - nx; q[3] = y1 - sy - ny;
            q[4] = x2 + ex - nx; q[5] = y2 + ey - ny;
            q[6] = x2 + ex + nx; q[7] = y2 + ey + ny;
            addPoly(out, q, 4);
        }
        if (!closed && cap == CAP_ROUND) {
            addCircle(out, p[0], p[1], hw);
            addCircle(out, p[(m - 1) * 2], p[(m - 1) * 2 + 1], hw);
        }
        int first = closed ? 0 : 1, last = closed ? m - 1 : m - 2;
        for (int v = first; v <= last; v++) {
            int a = (v - 1 + m) % m, b = (v + 1) % m;
            addJoin(out, p[a * 2], p[a * 2 + 1], p[v * 2], p[v * 2 + 1], p[b * 2], p[b * 2 + 1], hw, join, miterLimit, q);
        }
    }

    private static void addJoin(Contours out, double ax, double ay, double vx, double vy, double bx, double by,
                                double hw, int join, double miterLimit, double[] q) {
        double d1x = vx - ax, d1y = vy - ay, d2x = bx - vx, d2y = by - vy;
        double l1 = Math.hypot(d1x, d1y), l2 = Math.hypot(d2x, d2y);
        d1x /= l1; d1y /= l1; d2x /= l2; d2y /= l2;
        double cross = d1x * d2y - d1y * d2x;
        double dot = d1x * d2x + d1y * d2y;
        if (join == JOIN_ROUND) {
            if (Math.abs(cross) > 1e-9 || dot < 0) addCircle(out, vx, vy, hw);
            return;
        }
        if (Math.abs(cross) <= 1e-9) return; // straight on, or a 180° turn (bevel is empty)
        // Outer side is opposite the turn direction.
        double s = cross > 0 ? -1 : 1;
        double o1x = -d1y * hw * s, o1y = d1x * hw * s;
        double o2x = -d2y * hw * s, o2y = d2x * hw * s;
        if (join == JOIN_MITER) {
            double mx = o1x + o2x, my = o1y + o2y;
            double ml = Math.hypot(mx, my);
            if (ml > 1e-12) {
                // cos of half the angle between the offset normals; miter ratio = 1/cos.
                double cosHalf = (mx * o1x + my * o1y) / (ml * hw);
                if (cosHalf > 1e-12 && 1 / cosHalf <= miterLimit) {
                    double k = hw / cosHalf / ml;
                    q[0] = vx; q[1] = vy;
                    q[2] = vx + o1x; q[3] = vy + o1y;
                    q[4] = vx + mx * k; q[5] = vy + my * k;
                    q[6] = vx + o2x; q[7] = vy + o2y;
                    addPoly(out, q, 4);
                    return;
                }
            }
        }
        q[0] = vx; q[1] = vy;
        q[2] = vx + o1x; q[3] = vy + o1y;
        q[4] = vx + o2x; q[5] = vy + o2y;
        addPoly(out, q, 3);
    }

    private static void addCircle(Contours out, double cx, double cy, double r) {
        int n = r <= 0.25 ? 8 : (int) Math.ceil(Math.PI / Math.acos(Math.max(-1, 1 - 0.2 / r)));
        n = Math.max(8, Math.min(128, n));
        double[] c = new double[n * 2];
        for (int i = 0; i < n; i++) {
            double a = -2 * Math.PI * i / n;
            c[i * 2] = cx + Math.cos(a) * r;
            c[i * 2 + 1] = cy + Math.sin(a) * r;
        }
        addPoly(out, c, n);
    }

    /** Append with negative shoelace orientation; degenerate pieces are dropped. */
    private static void addPoly(Contours out, double[] pts, int n) {
        double a = 0;
        for (int i = 0; i < n; i++) {
            int j = i + 1 == n ? 0 : i + 1;
            a += pts[i * 2] * pts[j * 2 + 1] - pts[j * 2] * pts[i * 2 + 1];
        }
        if (a == 0 || Double.isNaN(a)) return;
        if (a < 0) {
            out.add(pts, 0, n, true);
            return;
        }
        out.end(false);
        out.ensureCoords(n * 2);
        for (int i = n - 1; i >= 0; i--) {
            if (i == n - 1) out.moveTo(pts[i * 2], pts[i * 2 + 1]);
            else out.lineTo(pts[i * 2], pts[i * 2 + 1]);
        }
        out.end(true);
    }
}
