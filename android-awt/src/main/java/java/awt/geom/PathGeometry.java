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

import java.awt.Shape;
import sun.awt.geom.Curve;

/**
 * Shared winding and intersection tests over a path's exact segments, the
 * way OpenJDK's {@code Path2D} does them: the crossing counts come from
 * {@link Curve}, so curved segments are tested analytically rather than
 * through a flattened approximation.
 */
final class PathGeometry {
    static boolean contains(PathIterator it, double x, double y) {
        if (x * 0.0 + y * 0.0 != 0.0) {
            // NaN or infinite coordinates are outside every path.
            return false;
        }
        int mask = (it.getWindingRule() == PathIterator.WIND_NON_ZERO ? -1 : 1);
        return (Curve.pointCrossingsForPath(it, x, y) & mask) != 0;
    }

    static boolean rectangle(Shape shape, double x, double y, double w, double h, boolean containing) {
        return rectangle(shape.getPathIterator(null), x, y, w, h, containing);
    }

    static boolean rectangle(PathIterator it, double x, double y, double w, double h, boolean containing) {
        if (java.lang.Double.isNaN(x + w) || java.lang.Double.isNaN(y + h)) {
            return false;
        }
        if (w <= 0 || h <= 0) {
            return false;
        }
        int mask = (it.getWindingRule() == PathIterator.WIND_NON_ZERO ? -1 : 2);
        int crossings = Curve.rectCrossingsForPath(it, x, y, x + w, y + h);
        if (containing) {
            return crossings != Curve.RECT_INTERSECTS && (crossings & mask) != 0;
        }
        return crossings == Curve.RECT_INTERSECTS || (crossings & mask) != 0;
    }
}
