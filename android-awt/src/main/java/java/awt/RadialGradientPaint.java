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
import java.awt.geom.Rectangle2D;

public final class RadialGradientPaint extends MultipleGradientPaint {
    private final Point2D center, focus;
    private final float radius;
    private final double fx, fy;
    public RadialGradientPaint(float x, float y, float radius, float[] fractions, Color[] colors) {
        this(new Point2D.Float(x, y), radius, fractions, colors);
    }
    public RadialGradientPaint(Point2D center, float radius, float[] fractions, Color[] colors) {
        this(center, radius, center, fractions, colors, CycleMethod.NO_CYCLE);
    }
    public RadialGradientPaint(float x, float y, float radius, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(new Point2D.Float(x, y), radius, fractions, colors, cycle);
    }
    public RadialGradientPaint(Point2D center, float radius, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(center, radius, center, fractions, colors, cycle);
    }
    public RadialGradientPaint(float x, float y, float radius, float fx, float fy, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(new Point2D.Float(x, y), radius, new Point2D.Float(fx, fy), fractions, colors, cycle);
    }
    public RadialGradientPaint(Point2D center, float radius, Point2D focus, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(center, radius, focus, fractions, colors, cycle, ColorSpaceType.SRGB, new AffineTransform());
    }
    public RadialGradientPaint(Point2D center, float radius, Point2D focus, float[] fractions, Color[] colors, CycleMethod cycle, ColorSpaceType space, AffineTransform transform) {
        super(fractions, colors, cycle, space, transform);
        if (!(radius > 0)) throw new IllegalArgumentException("Radius must be positive");
        this.center = new Point2D.Double(center.getX(), center.getY());
        this.focus = new Point2D.Double(focus.getX(), focus.getY());
        this.radius = radius;
        double dx = focus.getX() - center.getX(), dy = focus.getY() - center.getY();
        double distance = Math.hypot(dx, dy), scale = distance >= radius ? radius * .999 / distance : 1;
        fx = center.getX() + dx * scale;
        fy = center.getY() + dy * scale;
    }
    public RadialGradientPaint(Rectangle2D bounds, float[] fractions, Color[] colors, CycleMethod cycle) {
        this(new Point2D.Double(0, 0), 1, new Point2D.Double(0, 0), fractions, colors, cycle, ColorSpaceType.SRGB, boundsTransform(bounds));
    }
    private static AffineTransform boundsTransform(Rectangle2D bounds) {
        if (bounds.isEmpty()) throw new IllegalArgumentException("Empty gradient bounds");
        AffineTransform transform = AffineTransform.getTranslateInstance(bounds.getCenterX(), bounds.getCenterY());
        transform.scale(bounds.getWidth() / 2, bounds.getHeight() / 2);
        return transform;
    }
    public Point2D getCenterPoint() { return new Point2D.Double(center.getX(), center.getY()); }
    public Point2D getFocusPoint() { return new Point2D.Double(focus.getX(), focus.getY()); }
    public float getRadius() { return radius; }
    protected double fraction(double x, double y) {
        double dx = x - fx, dy = y - fy;
        double a = dx * dx + dy * dy;
        if (a == 0) return 0;
        double ox = fx - center.getX(), oy = fy - center.getY();
        double b = 2 * (dx * ox + dy * oy), c = ox * ox + oy * oy - radius * radius;
        double intersection = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
        return 1 / intersection;
    }
}
