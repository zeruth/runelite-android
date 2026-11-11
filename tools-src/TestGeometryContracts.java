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

        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
