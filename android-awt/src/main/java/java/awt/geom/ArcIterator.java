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

/**
 * Path iterator over pre-built segments, shared by {@link Ellipse2D} and {@link Arc2D}.
 * Elliptical arcs are emitted as cubic Béziers of at most 90° each (control length
 * {@code 4/3 tan(sweep/4)}), which is what lets {@code new Area(ellipse)}, clip shapes and
 * the generic stroker see real geometry instead of an empty path.
 */
final class ArcIterator implements PathIterator {
    private final byte[] types;
    private final double[] coords;
    private final int nSegs;
    private final AffineTransform at;
    private int seg;
    private int coord;

    private ArcIterator(byte[] types, double[] coords, int nSegs, AffineTransform at) {
        this.types = types;
        this.coords = coords;
        this.nSegs = nSegs;
        this.at = at;
    }

    /**
     * @param startDeg AWT angle convention: 0 = 3 o'clock, positive = counter-clockwise on
     *                 screen (towards -y).
     * @param closure  {@link Arc2D#OPEN}, {@link Arc2D#CHORD} or {@link Arc2D#PIE}.
     */
    static PathIterator arc(double x, double y, double w, double h, double startDeg, double extentDeg,
                            int closure, AffineTransform at) {
        if (w < 0 || h < 0 || Double.isNaN(w) || Double.isNaN(h)) {
            return new ArcIterator(new byte[0], new double[0], 0, at);
        }
        double ext = Math.max(-360, Math.min(360, extentDeg));
        int pieces = Math.max(1, (int) Math.ceil(Math.abs(ext) / 90.0 - 1e-9));
        byte[] t = new byte[pieces + 3];
        double[] c = new double[pieces * 6 + 4];
        int ns = 0, nc = 0;
        double rx = w / 2, ry = h / 2, cx = x + rx, cy = y + ry;
        double a0 = Math.toRadians(startDeg);
        double step = Math.toRadians(ext) / pieces;
        double k = 4.0 / 3.0 * Math.tan(step / 4);
        t[ns++] = SEG_MOVETO;
        c[nc++] = cx + rx * Math.cos(a0);
        c[nc++] = cy - ry * Math.sin(a0);
        for (int i = 0; i < pieces; i++) {
            double b0 = a0 + step * i, b1 = b0 + step;
            double cos0 = Math.cos(b0), sin0 = Math.sin(b0);
            double cos1 = Math.cos(b1), sin1 = Math.sin(b1);
            t[ns++] = SEG_CUBICTO;
            // Tangent of (cx + rx cos, cy - ry sin) is (-rx sin, -ry cos).
            c[nc++] = cx + rx * cos0 - k * rx * sin0;
            c[nc++] = cy - ry * sin0 - k * ry * cos0;
            c[nc++] = cx + rx * cos1 + k * rx * sin1;
            c[nc++] = cy - ry * sin1 + k * ry * cos1;
            c[nc++] = cx + rx * cos1;
            c[nc++] = cy - ry * sin1;
        }
        if (closure == Arc2D.PIE) {
            t[ns++] = SEG_LINETO;
            c[nc++] = cx;
            c[nc++] = cy;
        }
        if (closure != Arc2D.OPEN) t[ns++] = SEG_CLOSE;
        return new ArcIterator(t, c, ns, at);
    }

    @Override public int getWindingRule() { return WIND_NON_ZERO; }
    @Override public boolean isDone() { return seg >= nSegs; }

    @Override public void next() {
        if (seg >= nSegs) return;
        coord += points(types[seg]) * 2;
        seg++;
    }

    private static int points(int type) {
        switch (type) {
            case SEG_MOVETO: case SEG_LINETO: return 1;
            case SEG_QUADTO: return 2;
            case SEG_CUBICTO: return 3;
            default: return 0;
        }
    }

    @Override public int currentSegment(double[] out) {
        int type = types[seg];
        int n = points(type);
        if (at != null) at.transform(coords, coord, out, 0, n);
        else System.arraycopy(coords, coord, out, 0, n * 2);
        return type;
    }

    @Override public int currentSegment(float[] out) {
        double[] d = new double[6];
        int type = currentSegment(d);
        for (int i = 0; i < points(type) * 2; i++) out[i] = (float) d[i];
        return type;
    }
}
