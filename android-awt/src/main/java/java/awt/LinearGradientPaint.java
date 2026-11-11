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

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

public final class LinearGradientPaint extends MultipleGradientPaint {
    private final Point2D start, end;
    public LinearGradientPaint(float x1, float y1, float x2, float y2, float[] fractions, Color[] colors) {
        this(new Point2D.Float(x1, y1), new Point2D.Float(x2, y2), fractions, colors);
    }
    public LinearGradientPaint(float x1, float y1, float x2, float y2, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(new Point2D.Float(x1, y1), new Point2D.Float(x2, y2), fractions, colors, cycle);
    }
    public LinearGradientPaint(Point2D start, Point2D end, float[] fractions, Color[] colors) {
        this(start, end, fractions, colors, CycleMethod.NO_CYCLE);
    }
    public LinearGradientPaint(Point2D start, Point2D end, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(start, end, fractions, colors, cycle, ColorSpaceType.SRGB, new AffineTransform());
    }
    public LinearGradientPaint(Point2D start, Point2D end, float[] fractions, Color[] colors, CycleMethod cycle, ColorSpaceType space, AffineTransform transform) {
        super(fractions, colors, cycle, space, transform);
        this.start = new Point2D.Double(start.getX(), start.getY());
        this.end = new Point2D.Double(end.getX(), end.getY());
        if (this.start.equals(this.end)) throw new IllegalArgumentException("Gradient endpoints coincide");
    }
    public Point2D getStartPoint() { return new Point2D.Double(start.getX(), start.getY()); }
    public Point2D getEndPoint() { return new Point2D.Double(end.getX(), end.getY()); }
    protected double fraction(double x, double y) {
        double dx = end.getX() - start.getX(), dy = end.getY() - start.getY();
        return ((x - start.getX()) * dx + (y - start.getY()) * dy) / (dx * dx + dy * dy);
    }
}
