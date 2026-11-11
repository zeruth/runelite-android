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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Boolean operations on polygon sets, backing {@link java.awt.geom.Area}.
 *
 * Scanbeam decomposition: every edge endpoint and every pairwise edge crossing becomes an
 * event y; between two consecutive event ys no edges cross, so the active edges of a beam
 * can be ordered left to right and the region between neighbours classified by the
 * operands' winding numbers. Only the edges where the result's insideness flips survive,
 * as trapezoid sides; horizontal boundary pieces fall out of comparing the inside x
 * intervals just above and just below each event y. The directed pieces are then chained
 * into loops and collinear runs merged.
 *
 * Degeneracies are what Area users actually hit (touching convex hulls sharing an edge,
 * rect-minus-rect with shared sides), so: coincident edges are grouped and classified as
 * one, and every x at an event y is snapped to a per-level canonical value so the pieces
 * of adjacent beams meet exactly when chained.
 *
 * Input contours are implicitly closed x,y arrays. Output loops are non-overlapping and
 * consistently oriented (outer boundaries negative shoelace area in y-down space, holes
 * positive), so even-odd and non-zero agree on them.
 */
public final class PolyClip {
    public static final int UNION = 0;
    public static final int DIFFERENCE = 1;
    public static final int INTERSECTION = 2;
    public static final int XOR = 3;

    private PolyClip() {}

    private static final class Edge {
        final double xt, yt, xb, yb;
        final int dir;
        final int owner;
        /** Event levels of the top / bottom endpoint. */
        int lt, lb;
        Edge(double xa, double ya, double xb, double yb, int owner) {
            if (ya < yb) {
                this.xt = xa; this.yt = ya; this.xb = xb; this.yb = yb; this.dir = 1;
            } else {
                this.xt = xb; this.yt = yb; this.xb = xa; this.yb = ya; this.dir = -1;
            }
            this.owner = owner;
        }
        double xAt(double y) {
            if (y <= yt) return xt;
            if (y >= yb) return xb;
            return xt + (y - yt) * (xb - xt) / (yb - yt);
        }
    }

    /**
     * Boolean op on two already-normalized loop sets, only clipping what can interact.
     *
     * A loop whose bounding box doesn't touch the other operand's bounding box has zero
     * winding everywhere the other operand exists, and any loop enclosing it (an outer
     * around a hole) has a bbox at least as large, so the touching set is closed under
     * containment. Those loops can be passed through (union / xor, or the minuend of a
     * difference) or dropped (intersection) without running the sweep. This is what keeps
     * incremental unions cheap — quest-helper adds every scenery hull in the scene to one
     * Area, one at a time, and each new hull only meets its neighbours.
     */
    public static double[][] op(double[][] a, double[][] b, int op) {
        if (a.length == 0 || b.length == 0) {
            switch (op) {
                case UNION: case XOR: return a.length == 0 ? b : a;
                case DIFFERENCE: return a;
                default: return new double[0][];
            }
        }
        double[] ba = bounds(a), bb = bounds(b);
        List<double[]> at = new ArrayList<>(), au = new ArrayList<>();
        List<double[]> bt = new ArrayList<>(), bu = new ArrayList<>();
        split(a, bb, at, au);
        split(b, ba, bt, bu);
        List<double[]> out = new ArrayList<>();
        if (!at.isEmpty() || !bt.isEmpty()) {
            double[][] r = compute(at.toArray(new double[0][]), false, bt.toArray(new double[0][]), false, op);
            for (double[] l : r) out.add(l);
        }
        if (op == UNION || op == XOR || op == DIFFERENCE) out.addAll(au);
        if (op == UNION || op == XOR) out.addAll(bu);
        return out.toArray(new double[0][]);
    }

    private static void split(double[][] loops, double[] other, List<double[]> touching, List<double[]> untouched) {
        for (double[] l : loops) {
            double x0 = Double.POSITIVE_INFINITY, y0 = x0, x1 = Double.NEGATIVE_INFINITY, y1 = x1;
            for (int i = 0; i < l.length; i += 2) {
                x0 = Math.min(x0, l[i]); x1 = Math.max(x1, l[i]);
                y0 = Math.min(y0, l[i + 1]); y1 = Math.max(y1, l[i + 1]);
            }
            // Inclusive: loops sharing only an edge with the other operand must still be
            // merged, or the outline would keep the internal seam.
            boolean touch = x0 <= other[2] && x1 >= other[0] && y0 <= other[3] && y1 >= other[1];
            (touch ? touching : untouched).add(l);
        }
    }

    /** Normalize a single (possibly self-intersecting) polygon set under its fill rule. */
    public static double[][] normalize(double[][] a, boolean evenOdd) {
        return compute(a, evenOdd, new double[0][], false, UNION);
    }

    public static double[][] compute(double[][] a, boolean aEvenOdd, double[][] b, boolean bEvenOdd, int op) {
        List<Edge> edges = new ArrayList<>();
        double scale = 1;
        scale = addEdges(edges, a, 0, scale);
        scale = addEdges(edges, b, 1, scale);
        if (edges.isEmpty()) return new double[0][];
        final double eps = scale * 1e-11;

        Edge[] es = edges.toArray(new Edge[0]);
        Arrays.sort(es, (p, q) -> Double.compare(p.yt, q.yt));

        // ---- event ys: endpoints + interior crossings ----
        double[] ys = new double[es.length * 2 + 16];
        int ny = 0;
        for (Edge e : es) {
            if (ny + 2 > ys.length) ys = Arrays.copyOf(ys, ys.length * 2);
            ys[ny++] = e.yt;
            ys[ny++] = e.yb;
        }
        for (int i = 0; i < es.length; i++) {
            Edge p = es[i];
            double pMinX = Math.min(p.xt, p.xb), pMaxX = Math.max(p.xt, p.xb);
            for (int j = i + 1; j < es.length; j++) {
                Edge q = es[j];
                if (q.yt >= p.yb) break;
                if (Math.max(q.xt, q.xb) < pMinX || Math.min(q.xt, q.xb) > pMaxX) continue;
                double y0 = Math.max(p.yt, q.yt), y1 = Math.min(p.yb, q.yb);
                if (y1 - y0 <= eps) continue;
                double d0 = p.xAt(y0) - q.xAt(y0);
                double d1 = p.xAt(y1) - q.xAt(y1);
                if ((d0 > eps && d1 < -eps) || (d0 < -eps && d1 > eps)) {
                    double y = y0 + (y1 - y0) * (d0 / (d0 - d1));
                    if (ny == ys.length) ys = Arrays.copyOf(ys, ys.length * 2);
                    ys[ny++] = Math.max(y0, Math.min(y1, y));
                }
            }
        }
        Arrays.sort(ys, 0, ny);
        int m = 0;
        for (int i = 0; i < ny; i++) {
            if (m == 0 || ys[i] - ys[m - 1] > eps) ys[m++] = ys[i];
        }
        if (m < 2) return new double[0][];

        // Each endpoint belongs to the level it was merged into (the cluster's smallest y),
        // so beam membership is exact rather than another eps comparison.
        for (Edge e : es) {
            e.lt = levelOf(ys, m, e.yt);
            e.lb = levelOf(ys, m, e.yb);
        }
        Arrays.sort(es, (p, q) -> Integer.compare(p.lt, q.lt));

        // ---- per-beam active edges with raw x at the beam's top / bottom ----
        int beams = m - 1;
        Edge[][] act = new Edge[beams][];
        double[][] top = new double[beams][];
        double[][] bot = new double[beams][];
        List<Edge> active = new ArrayList<>();
        int next = 0;
        for (int k = 0; k < beams; k++) {
            double y0 = ys[k], y1 = ys[k + 1];
            while (next < es.length && es[next].lt <= k) active.add(es[next++]);
            for (int i = active.size() - 1; i >= 0; i--) {
                if (active.get(i).lb <= k) active.remove(i);
            }
            Edge[] a1 = active.toArray(new Edge[0]);
            double[] t = new double[a1.length], bt = new double[a1.length];
            for (int i = 0; i < a1.length; i++) {
                Edge e = a1[i];
                t[i] = e.lt == k ? e.xt : e.xAt(y0);
                bt[i] = e.lb == k + 1 ? e.xb : e.xAt(y1);
            }
            act[k] = a1;
            top[k] = t;
            bot[k] = bt;
        }

        // ---- canonical x per level, shared by the beam below and the beam above ----
        for (int k = 0; k < m; k++) {
            double[] below = k > 0 ? bot[k - 1] : null;
            double[] above = k < beams ? top[k] : null;
            canonicalize(below, above, eps);
        }

        // ---- classify; collect side pieces and the inside intervals at each level ----
        List<double[]> pieces = new ArrayList<>();
        double[][] topIv = new double[beams][];
        double[][] botIv = new double[beams][];
        for (int k = 0; k < beams; k++) {
            final Edge[] a1 = act[k];
            final double[] t = top[k], bt = bot[k];
            Integer[] order = new Integer[a1.length];
            for (int i = 0; i < order.length; i++) order[i] = i;
            Arrays.sort(order, (p, q) -> {
                int c = Double.compare(t[p] + bt[p], t[q] + bt[q]);
                return c != 0 ? c : Double.compare(t[p], t[q]);
            });
            int wa = 0, wb = 0;
            double[] ti = new double[8], bi = new double[8];
            int ni = 0;
            double lt = 0, lb = 0;
            int i = 0;
            while (i < order.length) {
                int g = order[i];
                boolean was = inside(op, wa, aEvenOdd, wb, bEvenOdd);
                int j = i;
                while (j < order.length && t[order[j]] == t[g] && bt[order[j]] == bt[g]) {
                    Edge e = a1[order[j]];
                    if (e.owner == 0) wa += e.dir; else wb += e.dir;
                    j++;
                }
                boolean now = inside(op, wa, aEvenOdd, wb, bEvenOdd);
                if (!was && now) {
                    lt = t[g];
                    lb = bt[g];
                } else if (was && !now) {
                    double rt = t[g], rb = bt[g];
                    if (ni + 2 > ti.length) { ti = Arrays.copyOf(ti, ti.length * 2); bi = Arrays.copyOf(bi, bi.length * 2); }
                    ti[ni] = lt; ti[ni + 1] = rt;
                    bi[ni] = lb; bi[ni + 1] = rb;
                    ni += 2;
                    // Left side runs down, right side runs up: interior is on one
                    // consistent side of every piece.
                    pieces.add(new double[] {lt, ys[k], lb, ys[k + 1]});
                    pieces.add(new double[] {rb, ys[k + 1], rt, ys[k]});
                }
                i = j;
            }
            topIv[k] = Arrays.copyOf(ti, ni);
            botIv[k] = Arrays.copyOf(bi, ni);
        }

        // ---- horizontal pieces: inside just above xor just below each level ----
        for (int k = 0; k < m; k++) {
            double[] below = union(k > 0 ? botIv[k - 1] : null);
            double[] above = union(k < beams ? topIv[k] : null);
            double y = ys[k];
            double[] d = subtract(below, above);
            for (int i = 0; i < d.length; i += 2) pieces.add(new double[] {d[i], y, d[i + 1], y});
            d = subtract(above, below);
            for (int i = 0; i < d.length; i += 2) pieces.add(new double[] {d[i + 1], y, d[i], y});
        }

        return chain(pieces, eps);
    }

    private static double addEdges(List<Edge> out, double[][] contours, int owner, double scale) {
        if (contours == null) return scale;
        for (double[] c : contours) {
            int n = c.length / 2;
            if (n < 3) continue;
            for (int i = 0; i < n; i++) {
                int j = i + 1 == n ? 0 : i + 1;
                double xa = c[i * 2], ya = c[i * 2 + 1], xb = c[j * 2], yb = c[j * 2 + 1];
                if (!(isFinite(xa) && isFinite(ya) && isFinite(xb) && isFinite(yb))) continue;
                scale = Math.max(scale, Math.max(Math.max(Math.abs(xa), Math.abs(ya)), Math.max(Math.abs(xb), Math.abs(yb))));
                if (ya == yb) continue;
                out.add(new Edge(xa, ya, xb, yb, owner));
            }
        }
        return scale;
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    /** Largest level index whose y is <= {@code y}. */
    private static int levelOf(double[] ys, int m, double y) {
        int lo = 0, hi = m - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (ys[mid] <= y) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    private static boolean inside(int op, int wa, boolean aEo, int wb, boolean bEo) {
        boolean a = aEo ? (wa & 1) != 0 : wa != 0;
        boolean b = bEo ? (wb & 1) != 0 : wb != 0;
        switch (op) {
            case UNION: return a || b;
            case DIFFERENCE: return a && !b;
            case INTERSECTION: return a && b;
            default: return a != b;
        }
    }

    /** Snap near-equal xs at one level (from the beam below and the beam above) to one value. */
    private static void canonicalize(double[] below, double[] above, double eps) {
        int nb = below != null ? below.length : 0, na = above != null ? above.length : 0;
        int n = nb + na;
        if (n < 2) return;
        double[] v = new double[n];
        for (int i = 0; i < nb; i++) v[i] = below[i];
        for (int i = 0; i < na; i++) v[nb + i] = above[i];
        double[] sorted = v.clone();
        Arrays.sort(sorted);
        // reps[i] = representative for sorted[i]
        double[] reps = new double[n];
        for (int i = 0; i < n; i++) {
            reps[i] = (i > 0 && sorted[i] - sorted[i - 1] <= eps) ? reps[i - 1] : sorted[i];
        }
        for (int i = 0; i < nb; i++) below[i] = reps[find(sorted, below[i])];
        for (int i = 0; i < na; i++) above[i] = reps[find(sorted, above[i])];
    }

    private static int find(double[] sorted, double v) {
        int idx = Arrays.binarySearch(sorted, v);
        return idx < 0 ? Math.max(0, Math.min(sorted.length - 1, -idx - 1)) : idx;
    }

    /** Sorted union of [x0,x1] pairs (zero-width pairs dropped, touching pairs merged). */
    private static double[] union(double[] iv) {
        if (iv == null || iv.length == 0) return new double[0];
        int n = iv.length / 2;
        double[][] p = new double[n][];
        int k = 0;
        for (int i = 0; i < n; i++) {
            double a = Math.min(iv[i * 2], iv[i * 2 + 1]), b = Math.max(iv[i * 2], iv[i * 2 + 1]);
            if (b > a) p[k++] = new double[] {a, b};
        }
        Arrays.sort(p, 0, k, (x, y) -> Double.compare(x[0], y[0]));
        double[] out = new double[k * 2];
        int o = 0;
        for (int i = 0; i < k; i++) {
            if (o > 0 && p[i][0] <= out[o - 1]) {
                out[o - 1] = Math.max(out[o - 1], p[i][1]);
            } else {
                out[o++] = p[i][0];
                out[o++] = p[i][1];
            }
        }
        return Arrays.copyOf(out, o);
    }

    /** a \ b for sorted disjoint interval lists. */
    private static double[] subtract(double[] a, double[] b) {
        double[] out = new double[a.length + b.length + 2];
        int o = 0;
        int j = 0;
        for (int i = 0; i < a.length; i += 2) {
            double s = a[i], e = a[i + 1];
            while (j < b.length && b[j + 1] <= s) j += 2;
            int jj = j;
            double cur = s;
            while (jj < b.length && b[jj] < e) {
                if (b[jj] > cur) {
                    if (o + 2 > out.length) out = Arrays.copyOf(out, out.length * 2);
                    out[o++] = cur;
                    out[o++] = b[jj];
                }
                cur = Math.max(cur, b[jj + 1]);
                jj += 2;
            }
            if (cur < e) {
                if (o + 2 > out.length) out = Arrays.copyOf(out, out.length * 2);
                out[o++] = cur;
                out[o++] = e;
            }
        }
        return Arrays.copyOf(out, o);
    }

    private static final class Pt {
        final double x, y;
        Pt(double x, double y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Pt)) return false;
            Pt p = (Pt) o;
            return Double.doubleToLongBits(x) == Double.doubleToLongBits(p.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(p.y);
        }
        @Override public int hashCode() {
            long h = Double.doubleToLongBits(x) * 31 + Double.doubleToLongBits(y);
            return (int) (h ^ (h >>> 32));
        }
    }

    private static double[][] chain(List<double[]> pieces, double eps) {
        int n = pieces.size();
        HashMap<Pt, List<Integer>> out = new HashMap<>();
        for (int i = 0; i < n; i++) {
            double[] p = pieces.get(i);
            Pt s = new Pt(p[0] + 0.0, p[1] + 0.0);
            List<Integer> l = out.get(s);
            if (l == null) out.put(s, l = new ArrayList<>(2));
            l.add(i);
        }
        boolean[] used = new boolean[n];
        List<double[]> loops = new ArrayList<>();
        double[] buf = new double[64];
        for (int i = 0; i < n; i++) {
            if (used[i]) continue;
            int len = 0;
            int cur = i;
            double sx = pieces.get(i)[0], sy = pieces.get(i)[1];
            boolean closed = false;
            while (true) {
                used[cur] = true;
                double[] p = pieces.get(cur);
                if (len + 2 > buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
                buf[len++] = p[0];
                buf[len++] = p[1];
                if (p[2] == sx && p[3] == sy) { closed = true; break; }
                List<Integer> l = out.get(new Pt(p[2] + 0.0, p[3] + 0.0));
                int nx = -1;
                if (l != null) for (int c : l) if (!used[c]) { nx = c; break; }
                if (nx < 0) break;
                cur = nx;
            }
            if (!closed) continue;
            double[] loop = simplify(buf, len / 2, eps);
            if (loop != null) loops.add(loop);
        }
        return loops.toArray(new double[0][]);
    }

    /** Drop duplicate and collinear vertices; null if nothing with area is left. */
    static double[] simplify(double[] pts, int n, double eps) {
        double[] s = new double[n * 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            double x = pts[i * 2], y = pts[i * 2 + 1];
            s[k * 2] = x;
            s[k * 2 + 1] = y;
            k++;
            while (k >= 3 && collinear(s, k - 3, k - 2, k - 1, eps)) {
                s[(k - 2) * 2] = s[(k - 1) * 2];
                s[(k - 2) * 2 + 1] = s[(k - 1) * 2 + 1];
                k--;
            }
            if (k >= 2 && s[(k - 1) * 2] == s[(k - 2) * 2] && s[(k - 1) * 2 + 1] == s[(k - 2) * 2 + 1]) k--;
        }
        // Wrap-around: the seam vertices can also be collinear.
        boolean changed = true;
        int head = 0;
        while (changed && k - head >= 3) {
            changed = false;
            if (collinearIdx(s, k - 2, k - 1, head, eps)) { k--; changed = true; }
            else if (collinearIdx(s, k - 1, head, head + 1, eps)) { head++; changed = true; }
        }
        int cnt = k - head;
        if (cnt < 3) return null;
        double area = 0;
        for (int i = 0; i < cnt; i++) {
            int a = head + i, b = head + (i + 1) % cnt;
            area += s[a * 2] * s[b * 2 + 1] - s[b * 2] * s[a * 2 + 1];
        }
        if (Math.abs(area) <= eps * eps) return null;
        return Arrays.copyOfRange(s, head * 2, k * 2);
    }

    private static boolean collinear(double[] s, int a, int b, int c, double eps) {
        return collinearIdx(s, a, b, c, eps);
    }

    private static boolean collinearIdx(double[] s, int a, int b, int c, double eps) {
        double ux = s[b * 2] - s[a * 2], uy = s[b * 2 + 1] - s[a * 2 + 1];
        double vx = s[c * 2] - s[b * 2], vy = s[c * 2 + 1] - s[b * 2 + 1];
        double lu = Math.abs(ux) + Math.abs(uy), lv = Math.abs(vx) + Math.abs(vy);
        if (lu <= eps || lv <= eps) return true;
        double cross = ux * vy - uy * vx;
        return Math.abs(cross) <= 1e-9 * lu * lv;
    }

    // ---------------------------------------------------------------------------------
    // Queries on normalized loops
    // ---------------------------------------------------------------------------------

    /** Non-zero point containment with Java2D insideness (left/top edges inside). */
    public static boolean contains(double[][] loops, double px, double py) {
        int w = 0;
        for (double[] c : loops) {
            int n = c.length / 2;
            for (int i = 0; i < n; i++) {
                int j = i + 1 == n ? 0 : i + 1;
                double x0 = c[i * 2], y0 = c[i * 2 + 1], x1 = c[j * 2], y1 = c[j * 2 + 1];
                if (y0 <= py && py < y1) {
                    if (x0 + (py - y0) * (x1 - x0) / (y1 - y0) > px) w++;
                } else if (y1 <= py && py < y0) {
                    if (x1 + (py - y1) * (x0 - x1) / (y0 - y1) > px) w--;
                }
            }
        }
        return w != 0;
    }

    /** [minX, minY, maxX, maxY], or null for an empty set. */
    public static double[] bounds(double[][] loops) {
        if (loops.length == 0) return null;
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, x1 = Double.NEGATIVE_INFINITY, y1 = x1;
        for (double[] c : loops) {
            for (int i = 0; i < c.length; i += 2) {
                x0 = Math.min(x0, c[i]); x1 = Math.max(x1, c[i]);
                y0 = Math.min(y0, c[i + 1]); y1 = Math.max(y1, c[i + 1]);
            }
        }
        return new double[] {x0, y0, x1, y1};
    }

    /** Shoelace sum (twice the signed area) of one loop. */
    public static double signedArea2(double[] c) {
        double a = 0;
        int n = c.length / 2;
        for (int i = 0; i < n; i++) {
            int j = i + 1 == n ? 0 : i + 1;
            a += c[i * 2] * c[j * 2 + 1] - c[j * 2] * c[i * 2 + 1];
        }
        return a;
    }
}
