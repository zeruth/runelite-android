// host-contract
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** java.awt.geom curve contracts: the same assertions run on the real JDK and on the shadow. */
public final class TestGeometryContracts {
    private static int failures;
    private static int total;
    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static boolean near(double[] a, double... b) {
        if (a.length < b.length) return false;
        for (int i = 0; i < b.length; i++) if (!near(a[i], b[i])) return false;
        return true;
    }
    private static double cubic(double[] eqn, double x) {
        return ((eqn[3] * x + eqn[2]) * x + eqn[1]) * x + eqn[0];
    }
    private static boolean hasRoot(double[] res, int n, double r) {
        for (int i = 0; i < n; i++) if (near(res[i], r)) return true;
        return false;
    }
    /** Flattened segments as {type, x, y}. */
    private static List<double[]> flatten(PathIterator it) {
        List<double[]> out = new ArrayList<>();
        double[] c = new double[6];
        while (!it.isDone()) {
            int t = it.currentSegment(c);
            out.add(new double[] {t, c[0], c[1]});
            it.next();
        }
        return out;
    }
    private static int count(List<double[]> segs, int type) {
        int n = 0;
        for (double[] s : segs) if ((int) s[0] == type) n++;
        return n;
    }

    public static void main(String[] args) {
        // --- solveQuadratic ---
        double[] q = {-6, 1, 1};            // x^2 + x - 6 = (x - 2)(x + 3)
        double[] r = new double[3];
        int n = QuadCurve2D.solveQuadratic(q, r);
        Arrays.sort(r, 0, n);
        check("solveQuadratic finds both roots of x^2+x-6", n == 2 && near(r, -3, 2));
        check("solveQuadratic in place keeps the same roots", QuadCurve2D.solveQuadratic(new double[] {-6, 1, 1}) == 2);
        check("solveQuadratic reports a constant as -1", QuadCurve2D.solveQuadratic(new double[] {3, 0, 0}, r) == -1);
        check("solveQuadratic solves a degenerate line", QuadCurve2D.solveQuadratic(new double[] {4, 2, 0}, r) == 1 && near(r[0], -2));
        check("solveQuadratic reports no real roots", QuadCurve2D.solveQuadratic(new double[] {1, 0, 1}, r) == 0);

        // --- solveCubic ---
        double[] three = {-6, 11, -6, 1};   // (x-1)(x-2)(x-3)
        double[] res = new double[3];
        n = CubicCurve2D.solveCubic(three, res);
        Arrays.sort(res, 0, n);
        check("solveCubic finds the three roots of (x-1)(x-2)(x-3)", n == 3 && near(res, 1, 2, 3));
        double[] inPlace = {-6, 11, -6, 1};
        n = CubicCurve2D.solveCubic(inPlace);
        Arrays.sort(inPlace, 0, n);
        check("solveCubic in place writes the roots over the coefficients", n == 3 && near(inPlace, 1, 2, 3));
        n = CubicCurve2D.solveCubic(new double[] {-1, 0, 0, 1}, res);
        check("solveCubic finds the single real root of x^3-1", n == 1 && near(res[0], 1));
        n = CubicCurve2D.solveCubic(new double[] {-8, 0, 0, 1}, res);
        check("solveCubic finds the single real root of x^3-8", n == 1 && near(res[0], 2));
        double[] dbl = {1, -1, -1, 1};      // (x-1)^2 (x+1)
        n = CubicCurve2D.solveCubic(dbl, res);
        boolean allRoots = n >= 2;
        for (int i = 0; i < n; i++) allRoots &= Math.abs(cubic(dbl, res[i])) < 1e-7;
        check("solveCubic reports the double root of (x-1)^2(x+1) once and the simple one", allRoots && hasRoot(res, n, 1) && hasRoot(res, n, -1));
        check("solveCubic without a cubic term degrades to the quadratic", CubicCurve2D.solveCubic(new double[] {-6, 1, 1, 0}, res) == 2);
        check("solveCubic reports a constant as -1", CubicCurve2D.solveCubic(new double[] {5, 0, 0, 0}, res) == -1);
        double[] wide = {-120, 74, -15, 1}; // (x-4)(x-5)(x-6)
        n = CubicCurve2D.solveCubic(wide, res);
        Arrays.sort(res, 0, n);
        check("solveCubic refines three close roots", n == 3 && near(res, 4, 5, 6));

        // --- subdivide ---
        CubicCurve2D cub = new CubicCurve2D.Double(0, 0, 0, 10, 10, 10, 10, 0);
        CubicCurve2D left = new CubicCurve2D.Double(), right = new CubicCurve2D.Double();
        cub.subdivide(left, right);
        check("cubic subdivide left half", near(new double[] {left.getX1(), left.getY1(), left.getCtrlX1(), left.getCtrlY1(),
            left.getCtrlX2(), left.getCtrlY2(), left.getX2(), left.getY2()}, 0, 0, 0, 5, 2.5, 7.5, 5, 7.5));
        check("cubic subdivide right half", near(new double[] {right.getX1(), right.getY1(), right.getCtrlX1(), right.getCtrlY1(),
            right.getCtrlX2(), right.getCtrlY2(), right.getX2(), right.getY2()}, 5, 7.5, 7.5, 7.5, 10, 5, 10, 0));
        double[] hold = new double[14];
        hold[0] = 0; hold[1] = 0; hold[2] = 0; hold[3] = 10; hold[4] = 10; hold[5] = 10; hold[6] = 10; hold[7] = 0;
        CubicCurve2D.subdivide(hold, 0, hold, 0, hold, 6);
        check("cubic array subdivide aliases the shared midpoint in one array",
            near(hold, 0, 0, 0, 5, 2.5, 7.5, 5, 7.5, 7.5, 7.5, 10, 5, 10, 0));
        CubicCurve2D self = new CubicCurve2D.Float(0, 0, 0, 10, 10, 10, 10, 0);
        self.subdivide(self, null);
        check("cubic subdivide into itself keeps the left half", near(self.getX2(), 5) && near(self.getY2(), 7.5) && near(self.getCtrlX2(), 2.5));

        QuadCurve2D quad = new QuadCurve2D.Double(0, 0, 5, 10, 10, 0);
        QuadCurve2D ql = new QuadCurve2D.Float(), qr = new QuadCurve2D.Double();
        QuadCurve2D.subdivide(quad, ql, qr);
        check("quad subdivide left half", near(new double[] {ql.getX1(), ql.getY1(), ql.getCtrlX(), ql.getCtrlY(), ql.getX2(), ql.getY2()}, 0, 0, 2.5, 5, 5, 5));
        check("quad subdivide right half", near(new double[] {qr.getX1(), qr.getY1(), qr.getCtrlX(), qr.getCtrlY(), qr.getX2(), qr.getY2()}, 5, 5, 7.5, 5, 10, 0));
        double[] qh = {0, 0, 5, 10, 10, 0, 0, 0, 0, 0};
        QuadCurve2D.subdivide(qh, 0, qh, 0, qh, 4);
        check("quad array subdivide aliases the shared midpoint", near(qh, 0, 0, 2.5, 5, 5, 5, 7.5, 5, 10, 0));

        // --- flatness ---
        check("quad flatness is the control point's distance to the chord", near(quad.getFlatness(), 10) && near(quad.getFlatnessSq(), 100));
        check("quad static flatness over an array", near(QuadCurve2D.getFlatnessSq(new double[] {0, 0, 5, 10, 10, 0}, 0), 100));
        check("cubic flatness is the farther control point's distance to the chord", near(cub.getFlatnessSq(), 100) && near(cub.getFlatness(), 10));
        check("cubic flatness picks the farther of the two control points",
            near(CubicCurve2D.getFlatness(0, 0, 0, 3, 10, 7, 10, 0), 7));
        check("flatness past the chord's end is measured to the end point", near(Line2D.ptSegDist(0, 0, 10, 0, 13, 4), 5));
        check("line distance ignores the segment's ends", near(Line2D.ptLineDist(0, 0, 10, 0, 13, 4), 4));
        check("halves are flatter than the whole", ql.getFlatnessSq() < quad.getFlatnessSq() && left.getFlatnessSq() < cub.getFlatnessSq());

        // --- FlatteningPathIterator ---
        List<double[]> flat = flatten(quad.getPathIterator(null, 0.5));
        boolean onCurve = flat.size() > 2 && (int) flat.get(0)[0] == PathIterator.SEG_MOVETO;
        for (double[] s : flat) {
            if ((int) s[0] == PathIterator.SEG_MOVETO) onCurve &= near(s[1], 0) && near(s[2], 0);
            else onCurve &= (int) s[0] == PathIterator.SEG_LINETO && near(s[2], 0.2 * s[1] * (10 - s[1]));
        }
        double[] last = flat.get(flat.size() - 1);
        check("flattened quad is MOVETO then LINETOs on the curve ending at the end point", onCurve && near(last[1], 10) && near(last[2], 0));
        List<double[]> coarse = flatten(quad.getPathIterator(null, 100));
        check("a flatness above the control distance emits one chord", coarse.size() == 2 && near(coarse.get(1)[1], 10));
        check("a recursion limit of 0 emits one chord", flatten(new FlatteningPathIterator(cub.getPathIterator(null), 0.01, 0)).size() == 2);
        List<double[]> fine = flatten(new FlatteningPathIterator(cub.getPathIterator(null), 0.01, 10));
        last = fine.get(fine.size() - 1);
        check("a fine cubic flattening subdivides many times and ends at the end point", count(fine, PathIterator.SEG_LINETO) >= 16 && near(last[1], 10) && near(last[2], 0));
        check("flattening limited to 3 levels emits at most 8 chords", count(flatten(new FlatteningPathIterator(cub.getPathIterator(null), 0.0001, 3)), PathIterator.SEG_LINETO) == 8);
        FlatteningPathIterator fpi = new FlatteningPathIterator(cub.getPathIterator(null), 0.25, 7);
        check("flattening iterator reports its flatness and limit", near(fpi.getFlatness(), 0.25) && fpi.getRecursionLimit() == 7 && fpi.getWindingRule() == PathIterator.WIND_NON_ZERO);
        boolean rejects = false;
        try { new FlatteningPathIterator(cub.getPathIterator(null), -1); } catch (IllegalArgumentException e) { rejects = true; }
        check("flattening iterator rejects a negative flatness", rejects);
        Path2D path = new Path2D.Double();
        path.moveTo(0, 0); path.quadTo(5, 10, 10, 0); path.curveTo(10, -5, 0, -5, 0, 0); path.closePath();
        List<double[]> flatPath = flatten(path.getPathIterator(null, 0.1));
        boolean linesOnly = count(flatPath, PathIterator.SEG_QUADTO) == 0 && count(flatPath, PathIterator.SEG_CUBICTO) == 0;
        check("Path2D flattening replaces quadTo and curveTo with line segments and keeps the close",
            linesOnly && count(flatPath, PathIterator.SEG_CLOSE) == 1 && count(flatPath, PathIterator.SEG_LINETO) > 8);
        float[] fc = new float[6];
        PathIterator ffi = quad.getPathIterator(AffineTransform.getTranslateInstance(1, 2), 100);
        ffi.next();
        check("flattening applies the transform from the source iterator", ffi.currentSegment(fc) == PathIterator.SEG_LINETO && fc[0] == 11f && fc[1] == 2f);

        // --- iterators ---
        double[] c = new double[6];
        PathIterator ci = cub.getPathIterator(null);
        boolean cubicIter = ci.currentSegment(c) == PathIterator.SEG_MOVETO && near(c, 0, 0);
        ci.next();
        cubicIter &= ci.currentSegment(c) == PathIterator.SEG_CUBICTO && near(c, 0, 10, 10, 10, 10, 0);
        ci.next();
        check("cubic iterator is a moveto and one cubicto", cubicIter && ci.isDone());
        PathIterator qi = quad.getPathIterator(AffineTransform.getScaleInstance(2, 3));
        qi.next();
        check("quad iterator applies the transform to the control and end point", qi.currentSegment(c) == PathIterator.SEG_QUADTO && near(c, 10, 30, 20, 0));

        // --- contains / intersects ---
        check("quad contains a point between chord and curve", quad.contains(5, 2) && quad.contains(new Point2D.Double(1, 1)));
        check("quad excludes points beyond the curve or the chord", !quad.contains(5, 6) && !quad.contains(5, -1) && !quad.contains(11, 0));
        check("quad intersects a rectangle inside it", quad.intersects(4, 1, 2, 2) && quad.intersects(new Rectangle2D.Double(-1, -1, 3, 3)));
        check("quad does not intersect a far rectangle", !quad.intersects(20, 20, 1, 1) && !quad.intersects(0, 0, 0, 5));
        check("quad contains a rectangle under the curve", quad.contains(4, 1, 2, 2) && quad.contains(new Rectangle2D.Double(4, 1, 2, 2)));
        check("quad does not contain its bounding box", !quad.contains(0, 0, 10, 10));
        // JDK 19+ computes tight curve bounds (JDK-8176501); 8u/17u and the shadow use the control hull.
        // Both are bounds of the curve, so only what every version agrees on is asserted.
        Rectangle2D qb = quad.getBounds2D();
        check("quad bounds span the end points and lie within the control hull",
            qb.getX() == 0 && qb.getY() == 0 && qb.getWidth() == 10 && qb.getHeight() >= 5 && qb.getHeight() <= 10
            && quad.getBounds().width == 10 && quad.getBounds().height == (int) Math.ceil(qb.getHeight()));

        check("cubic contains a point under the arch", cub.contains(5, 3) && cub.contains(new Point2D.Float(1, 1)));
        check("cubic excludes points over the arch and off its sides", !cub.contains(5, 9) && !cub.contains(-1, 1) && !cub.contains(5, -1));
        check("cubic rejects NaN and infinity", !cub.contains(Double.NaN, 1) && !cub.contains(1, Double.POSITIVE_INFINITY));
        check("cubic intersects a rectangle inside it and one crossing its edge", cub.intersects(4, 1, 2, 2) && cub.intersects(-1, 1, 3, 3));
        check("cubic does not intersect a far rectangle", !cub.intersects(-5, -5, 3, 3) && !cub.intersects(new Rectangle2D.Double(20, 0, 5, 5)));
        check("cubic contains a rectangle under the arch", cub.contains(4, 1, 2, 2) && cub.contains(new Rectangle2D.Double(3, 1, 4, 4)));
        check("cubic does not contain a rectangle crossing the arch", !cub.contains(4, 1, 2, 8) && !cub.contains(-5, -5, 3, 3));
        Rectangle2D cb = cub.getBounds2D();
        check("cubic bounds span the end points and lie within the control hull",
            cb.getX() == 0 && cb.getY() == 0 && cb.getWidth() == 10 && cb.getHeight() >= 7.5 && cb.getHeight() <= 10);

        // --- Path2D with curves and winding masks (Curve.pointCrossingsForPath) ---
        Path2D arch = new Path2D.Double();
        arch.moveTo(0, 0); arch.quadTo(5, 10, 10, 0); arch.closePath();
        check("Path2D with a quad contains a point under it", arch.contains(5, 2) && !arch.contains(5, 6));
        check("Path2D static contains agrees", Path2D.contains(arch.getPathIterator(null), 5, 2) && !Path2D.contains(arch.getPathIterator(null), 5, 6));
        check("Path2D with a quad intersects and contains rectangles", arch.intersects(4, 1, 2, 2) && arch.contains(4, 1, 2, 2) && !arch.contains(0, 0, 10, 10)
            && Path2D.intersects(arch.getPathIterator(null), 4, 1, 2, 2) && !Path2D.contains(arch.getPathIterator(null), 0, 0, 10, 10));
        Path2D dome = new Path2D.Float();
        dome.moveTo(0, 0); dome.curveTo(0, 10, 10, 10, 10, 0); dome.closePath();
        check("Path2D with a cubic contains a point under it", dome.contains(5, 3) && !dome.contains(5, 9) && dome.contains(new Point2D.Double(1, 1)));
        Path2D twice = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (int i = 0; i < 2; i++) { twice.moveTo(0, 0); twice.lineTo(10, 0); twice.lineTo(10, 10); twice.lineTo(0, 10); twice.closePath(); }
        check("even-odd winding excludes a doubly covered point", !twice.contains(5, 5));
        twice.setWindingRule(Path2D.WIND_NON_ZERO);
        check("non-zero winding includes a doubly covered point", twice.contains(5, 5));
        Path2D ring = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        ring.moveTo(0, 0); ring.lineTo(10, 0); ring.lineTo(10, 10); ring.lineTo(0, 10); ring.closePath();
        ring.moveTo(3, 3); ring.lineTo(7, 3); ring.lineTo(7, 7); ring.lineTo(3, 7); ring.closePath();
        check("even-odd ring excludes its hole", !ring.contains(5, 5) && ring.contains(1, 1) && !ring.contains(4, 4, 2, 2) && !ring.intersects(4, 4, 2, 2)
            && ring.intersects(2, 2, 2, 2) && !ring.contains(2, 2, 2, 2) && ring.contains(1, 1, 1, 8));
        Arc2D pie = new Arc2D.Double(0, 0, 10, 10, 0, 360, Arc2D.PIE);
        check("full arc contains its centre and not its corner", pie.contains(5, 5) && !pie.contains(0.5, 0.5) && pie.contains(1.5, 4, 1, 2) && !pie.contains(0, 0, 10, 10));
        Arc2D half = new Arc2D.Double(0, 0, 10, 10, 0, 180, Arc2D.CHORD);
        check("upper half arc contains above the chord only", half.contains(5, 2) && !half.contains(5, 8) && half.intersects(4, 4, 2, 2) && !half.intersects(4, 7, 2, 2));

        // --- clone / setCurve ---
        CubicCurve2D copy = (CubicCurve2D) cub.clone();
        check("cubic clone copies the coordinates", copy != cub && near(copy.getCtrlY1(), 10) && copy.getClass() == CubicCurve2D.Double.class);
        copy.setCurve(new Point2D[] {new Point2D.Double(1, 2), new Point2D.Double(3, 4), new Point2D.Double(5, 6), new Point2D.Double(7, 8)}, 0);
        check("cubic setCurve from points", near(copy.getX1(), 1) && near(copy.getCtrlY2(), 6) && near(copy.getY2(), 8));
        QuadCurve2D qf = new QuadCurve2D.Float();
        qf.setCurve(new double[] {9, 1.5, 2, 3, 4, 5}, 0);
        check("quad float setCurve from an array", qf.getP1().equals(new Point2D.Float(9f, 1.5f)) && near(qf.getCtrlPt().getX(), 2));

        randomInputs();

        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }

    // ---- #64: Line2D, Rectangle2D, Arc2D, Ellipse2D, AffineTransform, Point2D, Dimension2D on random inputs ----
    // Every expected value is written from the OpenJDK semantics with independent arithmetic; the plain-JDK
    // run validates the formulas, the patched run validates the shadow.

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
    }
    private static double sq(double v) { return v * v; }
    private static double cross(double x1, double y1, double x2, double y2, double px, double py) {
        return (px - x1) * (y2 - y1) - (py - y1) * (x2 - x1);
    }
    private static int sign(double v) { return v < 0 ? -1 : v > 0 ? 1 : 0; }
    /** Proper intersection of two segments; random doubles never touch or overlap. */
    private static boolean segmentsCross(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4) {
        int a = sign(cross(x1, y1, x2, y2, x3, y3)), b = sign(cross(x1, y1, x2, y2, x4, y4));
        int c = sign(cross(x3, y3, x4, y4, x1, y1)), d = sign(cross(x3, y3, x4, y4, x2, y2));
        return a * b <= 0 && c * d <= 0;
    }
    private static boolean segmentMeetsRect(double x1, double y1, double x2, double y2, double rx, double ry, double rw, double rh) {
        boolean in1 = x1 >= rx && x1 <= rx + rw && y1 >= ry && y1 <= ry + rh;
        boolean in2 = x2 >= rx && x2 <= rx + rw && y2 >= ry && y2 <= ry + rh;
        return in1 || in2
            || segmentsCross(x1, y1, x2, y2, rx, ry, rx + rw, ry)
            || segmentsCross(x1, y1, x2, y2, rx + rw, ry, rx + rw, ry + rh)
            || segmentsCross(x1, y1, x2, y2, rx + rw, ry + rh, rx, ry + rh)
            || segmentsCross(x1, y1, x2, y2, rx, ry + rh, rx, ry);
    }
    private static double normDeg(double a) {
        a %= 360.0;
        if (a < 0) a += 360.0;
        return a;
    }
    private static boolean samePoint(Point2D p, double x, double y) { return close(p.getX(), x) && close(p.getY(), y); }
    private static boolean sameRect(Rectangle2D r, double x, double y, double w, double h) {
        return close(r.getX(), x) && close(r.getY(), y) && close(r.getWidth(), w) && close(r.getHeight(), h);
    }
    private static boolean identity(AffineTransform t) {
        double[] m = new double[6];
        t.getMatrix(m);
        return close(m[0], 1) && close(m[1], 0) && close(m[2], 0) && close(m[3], 1) && close(m[4], 0) && close(m[5], 0);
    }

    private static void randomInputs() {
        java.util.Random rnd = new java.util.Random(64);
        final int N = 300;
        int[] bad = new int[40];
        String[] names = {
            "Line2D.relativeCCW is the sign of the cross product",                       // 0
            "Line2D.ptLineDistSq/ptLineDist is the perpendicular distance",               // 1
            "Line2D.ptSegDistSq/ptSegDist clamps to the segment",                         // 2
            "Line2D instance distances agree with the statics",                           // 3
            "Line2D.linesIntersect is the orientation test",                              // 4
            "Line2D.intersectsLine/intersects(Line2D) agree with linesIntersect",         // 5
            "Line2D.intersects(rect) is the segment-rectangle test",                      // 6
            "Line2D.contains is always false",                                            // 7
            "Line2D.Float/Double setLine and getBounds2D",                                // 8
            "Rectangle2D.outcode bits",                                                   // 9
            "Rectangle2D.intersectsLine is the segment-rectangle test",                   // 10
            "Rectangle.intersectsLine is the segment-rectangle test",                     // 11
            "Rectangle2D.createUnion / createIntersection",                               // 12
            "Rectangle2D.add(Point2D) and Double.add(x, y) grow to the point",            // 13
            "Rectangle2D.Double.add(Rectangle2D) is the union",                           // 14
            "Rectangle.outcode / createUnion / createIntersection on ints",               // 15
            "Arc2D.getStartPoint / getEndPoint",                                          // 16
            "Arc2D.containsAngle",                                                        // 17
            "Arc2D.setAngles from two points",                                            // 18
            "Arc2D.setArc(Rectangle2D, ...) and the Double(Rectangle2D, ...) constructor",// 19
            "Ellipse2D.Float.setFrame(float...)",                                         // 20
            "AffineTransform.quadrantRotate(n) matches rotate(n * PI/2)",                 // 21
            "AffineTransform.quadrantRotate(n, x, y) matches rotate(n * PI/2, x, y)",     // 22
            "AffineTransform.setToQuadrantRotation and setToRotation(theta, x, y)",       // 23
            "AffineTransform.getRotateInstance(vecx, vecy) rotates (1, 0) onto the vector",// 24
            "AffineTransform.createInverse undoes the transform",                         // 25
            "AffineTransform.deltaTransform drops the translation",                       // 26
            "AffineTransform.inverseTransform arrays round-trip",                         // 27
            "AffineTransform.getType flags",                                              // 28
            "Point2D.distanceSq / distance statics",                                      // 29
            "Dimension2D setSize / clone",                                                // 30
        };
        for (int i = 0; i < N; i++) {
            double x1 = rnd.nextDouble() * 200 - 100, y1 = rnd.nextDouble() * 200 - 100;
            double x2 = rnd.nextDouble() * 200 - 100, y2 = rnd.nextDouble() * 200 - 100;
            double px = rnd.nextDouble() * 200 - 100, py = rnd.nextDouble() * 200 - 100;
            double x3 = rnd.nextDouble() * 200 - 100, y3 = rnd.nextDouble() * 200 - 100;
            double x4 = rnd.nextDouble() * 200 - 100, y4 = rnd.nextDouble() * 200 - 100;
            double rx = rnd.nextDouble() * 100 - 50, ry = rnd.nextDouble() * 100 - 50;
            double rw = rnd.nextDouble() * 80 + 0.5, rh = rnd.nextDouble() * 80 + 0.5;

            // --- Line2D ---
            double cr = cross(x1, y1, x2, y2, px, py);
            if (Line2D.relativeCCW(x1, y1, x2, y2, px, py) != sign(cr)) bad[0]++;
            double len2 = sq(x2 - x1) + sq(y2 - y1);
            double lineSq = cr * cr / len2;
            if (!close(Line2D.ptLineDistSq(x1, y1, x2, y2, px, py), lineSq) || !close(Line2D.ptLineDist(x1, y1, x2, y2, px, py), Math.sqrt(lineSq))) bad[1]++;
            double t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / len2;
            double segSq = t <= 0 ? sq(px - x1) + sq(py - y1) : t >= 1 ? sq(px - x2) + sq(py - y2) : lineSq;
            if (!close(Line2D.ptSegDistSq(x1, y1, x2, y2, px, py), segSq) || !close(Line2D.ptSegDist(x1, y1, x2, y2, px, py), Math.sqrt(segSq))) bad[2]++;
            Line2D ld = new Line2D.Double(x1, y1, x2, y2);
            Point2D pp = new Point2D.Double(px, py);
            if (!close(ld.ptSegDistSq(px, py), segSq) || !close(ld.ptSegDist(pp), Math.sqrt(segSq))
                || !close(ld.ptLineDistSq(pp), lineSq) || !close(ld.ptLineDist(px, py), Math.sqrt(lineSq))
                || ld.relativeCCW(px, py) != sign(cr) || ld.relativeCCW(pp) != sign(cr)) bad[3]++;
            boolean crossing = segmentsCross(x1, y1, x2, y2, x3, y3, x4, y4);
            if (Line2D.linesIntersect(x1, y1, x2, y2, x3, y3, x4, y4) != crossing) bad[4]++;
            if (ld.intersectsLine(x3, y3, x4, y4) != crossing || ld.intersectsLine(new Line2D.Float((float) x3, (float) y3, (float) x4, (float) y4))
                != segmentsCross(x1, y1, x2, y2, (float) x3, (float) y3, (float) x4, (float) y4)) bad[5]++;
            boolean meets = segmentMeetsRect(x1, y1, x2, y2, rx, ry, rw, rh);
            if (ld.intersects(rx, ry, rw, rh) != meets || ld.intersects(new Rectangle2D.Double(rx, ry, rw, rh)) != meets) bad[6]++;
            if (ld.contains(px, py) || ld.contains(pp) || ld.contains(rx, ry, rw, rh) || ld.contains(new Rectangle2D.Double(rx, ry, rw, rh)) || ld.contains(x1, y1)) bad[7]++;
            Line2D.Float lf = new Line2D.Float();
            lf.setLine(x1, y1, x2, y2);
            Line2D.Double ld2 = new Line2D.Double();
            ld2.setLine(new Point2D.Double(x1, y1), new Point2D.Double(x2, y2));
            Rectangle2D lb = ld2.getBounds2D();
            Line2D.Double ld3 = new Line2D.Double();
            ld3.setLine(lf);
            if (lf.x1 != (float) x1 || lf.y2 != (float) y2 || ld2.x1 != x1 || ld2.y2 != y2 || ld3.x2 != (float) x2
                || !sameRect(lb, Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1))
                || !(lf.getBounds2D() instanceof Rectangle2D.Float) || !(lb instanceof Rectangle2D.Double)
                || !samePoint(ld2.getP1(), x1, y1) || !samePoint(ld2.getP2(), x2, y2)) bad[8]++;

            // --- Rectangle2D ---
            Rectangle2D rd = new Rectangle2D.Double(rx, ry, rw, rh);
            Rectangle2D rf = new Rectangle2D.Float((float) rx, (float) ry, (float) rw, (float) rh);
            int code = (px < rx ? Rectangle2D.OUT_LEFT : px > rx + rw ? Rectangle2D.OUT_RIGHT : 0)
                | (py < ry ? Rectangle2D.OUT_TOP : py > ry + rh ? Rectangle2D.OUT_BOTTOM : 0);
            int codeF = (px < (float) rx ? Rectangle2D.OUT_LEFT : px > (float) rx + (double) (float) rw ? Rectangle2D.OUT_RIGHT : 0)
                | (py < (float) ry ? Rectangle2D.OUT_TOP : py > (float) ry + (double) (float) rh ? Rectangle2D.OUT_BOTTOM : 0);
            if (rd.outcode(px, py) != code || rd.outcode(pp) != code || rf.outcode(px, py) != codeF
                || new Rectangle2D.Double(rx, ry, 0, rh).outcode(px, py) != ((Rectangle2D.OUT_LEFT | Rectangle2D.OUT_RIGHT) | (code & (Rectangle2D.OUT_TOP | Rectangle2D.OUT_BOTTOM)))) bad[9]++;
            if (rd.intersectsLine(x1, y1, x2, y2) != meets || rd.intersectsLine(ld) != meets) bad[10]++;
            int ix = rnd.nextInt(100) - 50, iy = rnd.nextInt(100) - 50, iw = rnd.nextInt(60) + 1, ih = rnd.nextInt(60) + 1;
            java.awt.Rectangle ri = new java.awt.Rectangle(ix, iy, iw, ih);
            if (ri.intersectsLine(x1, y1, x2, y2) != segmentMeetsRect(x1, y1, x2, y2, ix, iy, iw, ih)) bad[11]++;
            double ox = rnd.nextDouble() * 100 - 50, oy = rnd.nextDouble() * 100 - 50;
            double ow = rnd.nextDouble() * 80 + 0.5, oh = rnd.nextDouble() * 80 + 0.5;
            Rectangle2D other = new Rectangle2D.Double(ox, oy, ow, oh);
            double ux = Math.min(rx, ox), uy = Math.min(ry, oy);
            double uw = Math.max(rx + rw, ox + ow) - ux, uh = Math.max(ry + rh, oy + oh) - uy;
            double cx = Math.max(rx, ox), cy = Math.max(ry, oy);
            double cw = Math.min(rx + rw, ox + ow) - cx, ch = Math.min(ry + rh, oy + oh) - cy;
            Rectangle2D un = rd.createUnion(other), in = rd.createIntersection(other);
            Rectangle2D unS = new Rectangle2D.Double(), inS = new Rectangle2D.Double();
            Rectangle2D.union(rd, other, unS);
            Rectangle2D.intersect(rd, other, inS);
            if (!sameRect(un, ux, uy, uw, uh) || !sameRect(in, cx, cy, cw, ch) || !sameRect(unS, ux, uy, uw, uh) || !sameRect(inS, cx, cy, cw, ch)
                || !(un instanceof Rectangle2D.Double) || !(rf.createUnion(other) instanceof Rectangle2D.Double)
                || !(rf.createIntersection(rf) instanceof Rectangle2D.Float)
                || (rd.intersects(other) != (cw > 0 && ch > 0)) || (in.isEmpty() == (cw > 0 && ch > 0))) bad[12]++;
            Rectangle2D grown = new Rectangle2D.Double(rx, ry, rw, rh);
            grown.add(pp);
            Rectangle2D.Double grown2 = new Rectangle2D.Double(rx, ry, rw, rh);
            grown2.add(px, py);
            double gx = Math.min(rx, px), gy = Math.min(ry, py);
            double gw = Math.max(rx + rw, px) - gx, gh = Math.max(ry + rh, py) - gy;
            if (!sameRect(grown, gx, gy, gw, gh) || !sameRect(grown2, gx, gy, gw, gh)) bad[13]++;
            Rectangle2D.Double grown3 = new Rectangle2D.Double(rx, ry, rw, rh);
            grown3.add(other);
            if (!sameRect(grown3, ux, uy, uw, uh)) bad[14]++;
            int jx = rnd.nextInt(100) - 50, jy = rnd.nextInt(100) - 50, jw = rnd.nextInt(60) + 1, jh = rnd.nextInt(60) + 1;
            java.awt.Rectangle rj = new java.awt.Rectangle(jx, jy, jw, jh);
            int icode = (px < ix ? Rectangle2D.OUT_LEFT : px > ix + iw ? Rectangle2D.OUT_RIGHT : 0)
                | (py < iy ? Rectangle2D.OUT_TOP : py > iy + ih ? Rectangle2D.OUT_BOTTOM : 0);
            Rectangle2D riu = ri.createUnion(rj), rii = ri.createIntersection(rj);
            int iux = Math.min(ix, jx), iuy = Math.min(iy, jy);
            int icx = Math.max(ix, jx), icy = Math.max(iy, jy);
            int icw = Math.min(ix + iw, jx + jw) - icx, ich = Math.min(iy + ih, jy + jh) - icy;
            boolean intersectionOk = icw > 0 && ich > 0
                ? sameRect(rii, icx, icy, icw, ich) && rii.equals(ri.intersection(rj)) && ri.intersects(rj)
                : rii.isEmpty() && ri.intersection(rj).isEmpty() && !ri.intersects(rj);
            if (ri.outcode(px, py) != icode || !(riu instanceof java.awt.Rectangle) || !(rii instanceof java.awt.Rectangle)
                || !sameRect(riu, iux, iuy, Math.max(ix + iw, jx + jw) - iux, Math.max(iy + ih, jy + jh) - iuy)
                || !riu.equals(ri.union(rj)) || !intersectionOk
                || !(ri.createIntersection(other) instanceof Rectangle2D.Double)
                || !sameRect(ri.createUnion(other), Math.min(ix, ox), Math.min(iy, oy),
                    Math.max(ix + iw, ox + ow) - Math.min(ix, ox), Math.max(iy + ih, oy + oh) - Math.min(iy, oy))) bad[15]++;

            // --- Arc2D ---
            double start = rnd.nextDouble() * 720 - 360, extent = rnd.nextDouble() * 720 - 360;
            Arc2D arc = new Arc2D.Double(rx, ry, rw, rh, start, extent, rnd.nextInt(3));
            double acx = rx + rw / 2, acy = ry + rh / 2;
            double sRad = Math.toRadians(start), eRad = Math.toRadians(start + extent);
            if (!samePoint(arc.getStartPoint(), acx + rw / 2 * Math.cos(sRad), acy - rh / 2 * Math.sin(sRad))
                || !samePoint(arc.getEndPoint(), acx + rw / 2 * Math.cos(eRad), acy - rh / 2 * Math.sin(eRad))) bad[16]++;
            double probe = rnd.nextDouble() * 1080 - 540;
            double rel = normDeg(normDeg(probe) - normDeg(start));
            boolean expectContains = Math.abs(extent) >= 360
                || (extent >= 0 ? rel < extent : (rel == 0 ? true : 360 - rel < -extent));
            if (arc.containsAngle(probe) != expectContains) bad[17]++;
            Arc2D fa = new Arc2D.Float((float) rx, (float) ry, (float) rw, (float) rh, 0, 0, Arc2D.OPEN);
            fa.setAngles(x1, y1, x2, y2);
            double fcx = fa.getCenterX(), fcy = fa.getCenterY(), fw = fa.getWidth(), fh = fa.getHeight();
            double a1 = Math.atan2(fw * (fcy - y1), fh * (x1 - fcx)), a2 = Math.atan2(fw * (fcy - y2), fh * (x2 - fcx)) - a1;
            if (a2 <= 0) a2 += 2 * Math.PI;
            if (Math.abs(fa.getAngleStart() - (float) Math.toDegrees(a1)) > 1e-4 || Math.abs(fa.getAngleExtent() - (float) Math.toDegrees(a2)) > 1e-4) bad[18]++;
            Arc2D.Double ad = new Arc2D.Double(other, start, extent, Arc2D.PIE);
            Arc2D.Double ad2 = new Arc2D.Double();
            ad2.setArc(other, start, extent, Arc2D.CHORD);
            Arc2D.Double ad3 = new Arc2D.Double();
            ad3.setArc(new Point2D.Double(ox, oy), new java.awt.Dimension((int) ow, (int) oh), start, extent, Arc2D.OPEN);
            Arc2D.Double ad4 = new Arc2D.Double();
            ad4.setArc(ad);
            Arc2D.Double ad5 = new Arc2D.Double();
            ad5.setArcByCenter(ox, oy, 5, start, extent, Arc2D.PIE);
            if (ad.x != ox || ad.height != oh || ad.start != start || ad.extent != extent || ad.getArcType() != Arc2D.PIE
                || ad2.width != ow || ad2.getArcType() != Arc2D.CHORD || ad3.width != (int) ow || ad3.y != oy || ad3.getArcType() != Arc2D.OPEN
                || ad4.getArcType() != Arc2D.PIE || ad4.extent != extent || !ad4.equals(ad) || !ad.getBounds2D().equals(ad4.getBounds2D())
                || !sameRect(ad5.getFrame(), ox - 5, oy - 5, 10, 10) || !sameRect(ad.getFrame(), ox, oy, ow, oh)) bad[19]++;

            // --- Ellipse2D ---
            Ellipse2D.Float ef = new Ellipse2D.Float();
            ef.setFrame((float) rx, (float) ry, (float) rw, (float) rh);
            Ellipse2D.Float ef2 = new Ellipse2D.Float((float) rx, (float) ry, (float) rw, (float) rh);
            if (ef.x != (float) rx || ef.height != (float) rh || !ef.equals(ef2) || ef.hashCode() != ef2.hashCode()
                || !sameRect(ef.getBounds2D(), (float) rx, (float) ry, (float) rw, (float) rh) || ef.isEmpty()
                || !ef.contains(ef.getCenterX(), ef.getCenterY()) || ef.contains(rx, ry)) bad[20]++;

            // --- AffineTransform ---
            int n = rnd.nextInt(17) - 8;
            double tx = rnd.nextDouble() * 20 - 10, ty = rnd.nextDouble() * 20 - 10;
            double sx = rnd.nextDouble() * 3 + 0.5, sy = rnd.nextDouble() * 3 + 0.5;
            AffineTransform base = new AffineTransform(sx, 0.3, -0.2, sy, tx, ty);
            AffineTransform q = new AffineTransform(base), r = new AffineTransform(base);
            q.quadrantRotate(n);
            r.rotate(n * Math.PI / 2);
            Point2D qp = q.transform(pp, null), rp = r.transform(pp, null);
            AffineTransform qi = AffineTransform.getQuadrantRotateInstance(n);
            Point2D qip = qi.transform(pp, null);
            double[] exp = {px, py};
            for (int k = 0; k < ((n % 4) + 4) % 4; k++) exp = new double[] {-exp[1], exp[0]};
            if (!samePoint(qp, rp.getX(), rp.getY()) || !samePoint(qip, exp[0], exp[1])) bad[21]++;
            AffineTransform q2 = new AffineTransform(base), r2 = new AffineTransform(base);
            q2.quadrantRotate(n, x1, y1);
            r2.rotate(n * Math.PI / 2, x1, y1);
            Point2D q2p = q2.transform(pp, null), r2p = r2.transform(pp, null);
            Point2D q3p = AffineTransform.getQuadrantRotateInstance(n, x1, y1).transform(pp, null);
            // Rotation about c: R p + (c - R c).
            if (!samePoint(q2p, r2p.getX(), r2p.getY()) || !samePoint(q3p, exp[0] + (x1 - rotX(n, x1, y1)), exp[1] + (y1 - rotY(n, x1, y1)))) bad[22]++;
            double theta = rnd.nextDouble() * 2 * Math.PI;
            AffineTransform s1 = new AffineTransform(base), s2 = new AffineTransform(base), s3 = new AffineTransform(base);
            s1.setToQuadrantRotation(n);
            s2.setToQuadrantRotation(n, x1, y1);
            s3.setToRotation(theta, x1, y1);
            Point2D s1p = s1.transform(pp, null), s2p = s2.transform(pp, null), s3p = s3.transform(pp, null);
            double rc = Math.cos(theta), rs = Math.sin(theta);
            double ex3 = x1 + rc * (px - x1) - rs * (py - y1), ey3 = y1 + rs * (px - x1) + rc * (py - y1);
            if (!samePoint(s1p, exp[0], exp[1]) || !samePoint(s2p, q3p.getX(), q3p.getY()) || !samePoint(s3p, ex3, ey3)
                || !samePoint(AffineTransform.getRotateInstance(theta, x1, y1).transform(pp, null), ex3, ey3)) bad[23]++;
            double vx = rnd.nextDouble() * 10 - 5, vy = rnd.nextDouble() * 10 - 5;
            AffineTransform vrot = AffineTransform.getRotateInstance(vx, vy);
            double vlen = Math.hypot(vx, vy);
            Point2D unit = vrot.transform(new Point2D.Double(1, 0), null);
            AffineTransform vrot2 = new AffineTransform(base);
            vrot2.setToRotation(vx, vy, x1, y1);
            Point2D anchored = vrot2.transform(new Point2D.Double(x1 + 1, y1), null);
            AffineTransform vrot3 = new AffineTransform(base);
            vrot3.rotate(vx, vy);
            AffineTransform vrot4 = new AffineTransform(base);
            vrot4.concatenate(vrot);
            if (!samePoint(unit, vx / vlen, vy / vlen) || !samePoint(anchored, x1 + vx / vlen, y1 + vy / vlen)
                || !samePoint(vrot3.transform(pp, null), vrot4.transform(pp, null).getX(), vrot4.transform(pp, null).getY())) bad[24]++;
            try {
                AffineTransform inv = base.createInverse();
                AffineTransform both = new AffineTransform(base);
                both.concatenate(inv);
                AffineTransform inv2 = new AffineTransform(base);
                inv2.invert();
                Point2D back = inv.transform(base.transform(pp, null), null);
                double det = sx * sy - 0.3 * -0.2;
                if (!identity(both) || !samePoint(back, px, py) || !close(base.getDeterminant(), det)
                    || !samePoint(inv2.transform(base.transform(pp, null), null), px, py)) bad[25]++;
            } catch (NoninvertibleTransformException e) {
                bad[25]++;
            }
            boolean singular = false;
            try { new AffineTransform(1, 2, 2, 4, tx, ty).createInverse(); } catch (NoninvertibleTransformException e) { singular = true; }
            if (!singular) bad[25]++;
            double[] src = {px, py, x1, y1, x2, y2};
            double[] dst = new double[6];
            base.deltaTransform(src, 0, dst, 0, 3);
            Point2D dp = base.deltaTransform(pp, null);
            Point2D dpf = base.deltaTransform(new Point2D.Float((float) px, (float) py), null);
            if (!close(dst[0], sx * px + -0.2 * py) || !close(dst[1], 0.3 * px + sy * py) || !close(dst[4], sx * x2 + -0.2 * y2) || !close(dst[5], 0.3 * x2 + sy * y2)
                || !samePoint(dp, dst[0], dst[1]) || !(dpf instanceof Point2D.Float) || Math.abs(dpf.getX() - dst[0]) > 1e-3) bad[26]++;
            try {
                double[] fwd = new double[6], back = new double[6];
                base.transform(src, 0, fwd, 0, 3);
                base.inverseTransform(fwd, 0, back, 0, 3);
                Point2D ip = base.inverseTransform(base.transform(pp, null), null);
                // In-place with an overlapping window: the JDK copies the source first.
                double[] alias = fwd.clone();
                base.inverseTransform(alias, 0, alias, 2, 2);
                if (!close(back[0], px) || !close(back[1], py) || !close(back[4], x2) || !close(back[5], y2) || !samePoint(ip, px, py)
                    || !close(alias[2], px) || !close(alias[3], py) || !close(alias[4], x1) || !close(alias[5], y1)) bad[27]++;
            } catch (NoninvertibleTransformException e) {
                bad[27]++;
            }
            double us = rnd.nextDouble() * 3 + 0.5;
            if (us == 1.0 || sx == sy) continue;
            int tr = AffineTransform.TYPE_TRANSLATION;
            int quadrant = (((n % 4) + 4) % 4) == 0 ? AffineTransform.TYPE_IDENTITY : AffineTransform.TYPE_QUADRANT_ROTATION;
            if (new AffineTransform().getType() != AffineTransform.TYPE_IDENTITY
                || AffineTransform.getTranslateInstance(tx, ty).getType() != tr
                || AffineTransform.getScaleInstance(us, us).getType() != AffineTransform.TYPE_UNIFORM_SCALE
                || AffineTransform.getScaleInstance(sx, sy).getType() != AffineTransform.TYPE_GENERAL_SCALE
                || AffineTransform.getScaleInstance(-1, 1).getType() != AffineTransform.TYPE_FLIP
                || AffineTransform.getScaleInstance(-us, us).getType() != (AffineTransform.TYPE_FLIP | AffineTransform.TYPE_UNIFORM_SCALE)
                || AffineTransform.getScaleInstance(-1, -1).getType() != AffineTransform.TYPE_QUADRANT_ROTATION
                || AffineTransform.getQuadrantRotateInstance(n).getType() != quadrant
                // cos^2 + sin^2 is rarely exactly 1.0, so a rotation may also carry TYPE_UNIFORM_SCALE (the JDK does too).
                || (AffineTransform.getRotateInstance(theta).getType() & ~AffineTransform.TYPE_UNIFORM_SCALE) != AffineTransform.TYPE_GENERAL_ROTATION
                || AffineTransform.getShearInstance(0.5, 0.25).getType() != AffineTransform.TYPE_GENERAL_TRANSFORM
                || (AffineTransform.getRotateInstance(theta, x1, y1).getType() & tr) == 0
                || AffineTransform.getQuadrantRotateInstance(2, x1, y1).getType() != (tr | AffineTransform.TYPE_QUADRANT_ROTATION)
                || (s3.getType() & ~AffineTransform.TYPE_UNIFORM_SCALE) != (tr | AffineTransform.TYPE_GENERAL_ROTATION)
                || (AffineTransform.getTranslateInstance(0, 0).getType() | AffineTransform.getScaleInstance(1, 1).getType()) != AffineTransform.TYPE_IDENTITY
                || AffineTransform.getScaleInstance(us, us).getType() != AffineTransform.getScaleInstance(us, us).getType()
                || AffineTransform.getScaleInstance(sx, sy).getType() != AffineTransform.getScaleInstance(sx, sy).getType()) bad[28]++;
            AffineTransform mask = new AffineTransform(1, 0, 0, 1, 0, 0);
            mask.scale(1, 1);
            if (mask.getType() != AffineTransform.TYPE_IDENTITY || !mask.isIdentity()) bad[28]++;
            AffineTransform tuni = AffineTransform.getTranslateInstance(tx, ty);
            tuni.scale(us, us);
            if (tuni.getType() != (tr | AffineTransform.TYPE_UNIFORM_SCALE) || tuni.getScaleX() != us || tuni.getTranslateX() != tx) bad[28]++;

            // --- Point2D / Dimension2D ---
            if (!close(Point2D.distanceSq(x1, y1, x2, y2), len2) || !close(Point2D.distance(x1, y1, x2, y2), Math.sqrt(len2))
                || !close(new Point2D.Double(x1, y1).distanceSq(x2, y2), len2) || !close(new Point2D.Double(x1, y1).distance(new Point2D.Float((float) x2, (float) y2)),
                    Math.sqrt(sq((float) x2 - x1) + sq((float) y2 - y1)))) bad[29]++;
            java.awt.Dimension dim = new java.awt.Dimension();
            Dimension2D d2 = dim;
            d2.setSize(new java.awt.Dimension(iw, ih));
            Dimension2D d3 = (Dimension2D) d2.clone();
            d2.setSize(rw, rh);
            if (d3.getWidth() != iw || d3.getHeight() != ih || d3 == d2 || !(d3 instanceof java.awt.Dimension) || !d3.equals(new java.awt.Dimension(iw, ih))
                || dim.width != (int) Math.ceil(rw) || dim.height != (int) Math.ceil(rh)) bad[30]++;
        }
        for (int k = 0; k < names.length; k++) {
            check(names[k] + " (" + N + " random inputs)", bad[k] == 0);
        }
    }

    /** The image of (x, y) under a quadrant rotation about the origin. */
    private static double rotX(int n, double x, double y) {
        double[] p = {x, y};
        for (int k = 0; k < ((n % 4) + 4) % 4; k++) p = new double[] {-p[1], p[0]};
        return p[0];
    }
    private static double rotY(int n, double x, double y) {
        double[] p = {x, y};
        for (int k = 0; k < ((n % 4) + 4) % 4; k++) p = new double[] {-p[1], p[0]};
        return p[1];
    }
}
