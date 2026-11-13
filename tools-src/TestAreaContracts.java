// host-contract
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * java.awt.geom.Area contracts: the same assertions run on the real JDK and on the shadow.
 *
 * The boolean operations are pinned by digests of the path they produce (segment types and
 * coordinates to a micron), recorded from the real JDK: the sweep in sun.awt.geom is
 * deterministic, so a port that matches desktop reproduces the segments exactly, and a port
 * that merely produces the same region (flattened, differently split) fails here on purpose.
 */
public final class TestAreaContracts {
    private static int failures;
    private static int total;
    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    /** Segment types and coordinates of the outline, one segment per line, rounded to 1e-6. */
    private static String digest(java.awt.Shape s, AffineTransform at) {
        StringBuilder sb = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(at); !it.isDone(); it.next()) {
            int t = it.currentSegment(c);
            int n = t == PathIterator.SEG_MOVETO || t == PathIterator.SEG_LINETO ? 2
                : t == PathIterator.SEG_QUADTO ? 4 : t == PathIterator.SEG_CUBICTO ? 6 : 0;
            sb.append(t);
            for (int i = 0; i < n; i++) {
                sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.6f", c[i] + 0.0));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
    private static String digest(java.awt.Shape s) { return digest(s, null); }

    /** Short, stable fingerprint of a digest; the full digest is printed when it differs. */
    private static String fingerprint(String digest) {
        long h = 1125899906842597L;
        for (int i = 0; i < digest.length(); i++) h = 31 * h + digest.charAt(i);
        int segments = 0;
        for (int i = 0; i < digest.length(); i++) if (digest.charAt(i) == '\n') segments++;
        return segments + ":" + Long.toHexString(h);
    }
    private static void checkDigest(String name, java.awt.Shape s, String expected) {
        String d = digest(s);
        String f = fingerprint(d);
        boolean ok = f.equals(expected);
        check(name + " [" + f + "]", ok);
        if (!ok) System.out.print(d);
    }

    private static Area donut(double x, double y, double outer, double inner) {
        Area a = new Area(new Ellipse2D.Double(x, y, outer, outer));
        double off = (outer - inner) / 2;
        a.subtract(new Area(new Ellipse2D.Double(x + off, y + off, inner, inner)));
        return a;
    }

    private static int count(java.awt.Shape s, int type) {
        int n = 0;
        double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) if (it.currentSegment(c) == type) n++;
        return n;
    }

    public static void main(String[] args) {
        // --- construction ---
        Area empty = new Area();
        check("empty area is empty, rectangular, polygonal and singular",
            empty.isEmpty() && empty.isRectangular() && empty.isPolygonal() && empty.isSingular());
        check("empty area has empty bounds", empty.getBounds2D().isEmpty() && empty.getBounds().equals(new Rectangle()));
        check("empty area iterates nothing", empty.getPathIterator(null).isDone());
        boolean npe = false;
        try { new Area(null); } catch (NullPointerException e) { npe = true; }
        check("new Area(null) throws NullPointerException", npe);

        Area rect = new Area(new Rectangle2D.Double(10, 20, 30, 40));
        check("rectangle is rectangular, polygonal and singular", rect.isRectangular() && rect.isPolygonal() && rect.isSingular() && !rect.isEmpty());
        check("rectangle bounds are exact", rect.getBounds2D().equals(new Rectangle2D.Double(10, 20, 30, 40)) && rect.getBounds().equals(new Rectangle(10, 20, 30, 40)));
        checkDigest("rectangle outline", rect, "5:6d828ae7d1532bb6");
        check("area path iterators use the non-zero rule", rect.getPathIterator(null).getWindingRule() == PathIterator.WIND_NON_ZERO);
        check("degenerate rectangle gives an empty area", new Area(new Rectangle2D.Double(0, 0, 0, 10)).isEmpty());

        Path2D.Double bow = new Path2D.Double();
        bow.moveTo(0, 0); bow.lineTo(10, 10); bow.lineTo(10, 0); bow.lineTo(0, 10); bow.closePath();
        Area bowtie = new Area(bow);
        check("self-intersecting polygon splits into two pieces", !bowtie.isSingular() && bowtie.isPolygonal() && !bowtie.isRectangular());
        checkDigest("bow-tie normalized outline", bowtie, "10:4db48ccd0c2d4afa");
        Path2D.Double evenOdd = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        evenOdd.append(new Rectangle2D.Double(0, 0, 100, 100), false);
        evenOdd.append(new Rectangle2D.Double(25, 25, 50, 50), false);
        Area frame = new Area(evenOdd);
        check("even-odd path keeps its hole", !frame.contains(50, 50) && frame.contains(10, 10) && !frame.isSingular());
        Path2D.Double nonZero = new Path2D.Double(Path2D.WIND_NON_ZERO);
        nonZero.append(new Rectangle2D.Double(0, 0, 100, 100), false);
        nonZero.append(new Rectangle2D.Double(25, 25, 50, 50), false);
        check("non-zero path with same-direction inner loop fills it", new Area(nonZero).contains(50, 50) && new Area(nonZero).isRectangular());

        // --- rounded rectangles ---
        RoundRectangle2D rr1 = new RoundRectangle2D.Double(0, 0, 100, 60, 20, 20);
        RoundRectangle2D rr2 = new RoundRectangle2D.Double(50, 30, 100, 60, 30, 30);
        Area a1 = new Area(rr1);
        check("rounded rect is curved, not polygonal", !a1.isPolygonal() && !a1.isRectangular() && a1.isSingular());
        checkDigest("rounded rect outline", a1, "9:579caa15779d990c");
        Area union = new Area(rr1); union.add(new Area(rr2));
        Area diff = new Area(rr1); diff.subtract(new Area(rr2));
        Area inter = new Area(rr1); inter.intersect(new Area(rr2));
        Area xor = new Area(rr1); xor.exclusiveOr(new Area(rr2));
        checkDigest("rounded rects: union", union, "15:8e016efe51c3ecc5");
        checkDigest("rounded rects: subtract", diff, "11:115841aac7833e73");
        checkDigest("rounded rects: intersect", inter, "7:2432361ac05e0023");
        checkDigest("rounded rects: exclusiveOr", xor, "22:2a002b03c2f8032b");
        check("union is singular, xor is not", union.isSingular() && !xor.isSingular() && inter.isSingular() && diff.isSingular());
        check("union bounds span both operands", union.getBounds2D().equals(new Rectangle2D.Double(0, 0, 150, 90)));
        check("intersection bounds are the overlap", inter.getBounds2D().equals(new Rectangle2D.Double(50, 30, 50, 30)));
        check("union contains a point of either operand", union.contains(10, 30) && union.contains(140, 60) && !union.contains(10, 80));
        check("intersection contains only the overlap", inter.contains(75, 45) && !inter.contains(10, 30));
        check("subtract removes the overlap", diff.contains(10, 30) && !diff.contains(75, 45));
        check("xor removes the overlap and keeps both rims", xor.contains(10, 30) && xor.contains(140, 60) && !xor.contains(75, 45));
        check("rect containment", union.contains(new Rectangle2D.Double(20, 10, 60, 40)) && !union.contains(new Rectangle2D.Double(0, 0, 10, 10)));
        check("rect intersection", union.intersects(new Rectangle2D.Double(-5, -5, 10, 10)) && !union.intersects(new Rectangle2D.Double(120, 0, 30, 20)));

        // --- donuts ---
        Area d1 = donut(0, 0, 100, 50);
        Area d2 = donut(40, 0, 100, 50);
        check("donut is not singular and has a hole", !d1.isSingular() && !d1.contains(50, 50) && d1.contains(50, 10));
        checkDigest("donut outline", d1, "12:e1b005ff29b20c");
        Area du = new Area(d1); du.add(d2);
        Area dd = new Area(d1); dd.subtract(d2);
        Area di = new Area(d1); di.intersect(d2);
        Area dx = new Area(d1); dx.exclusiveOr(d2);
        checkDigest("donuts: union", du, "36:52281c559eee0eda");
        checkDigest("donuts: subtract", dd, "28:f609b0ba7d794c08");
        checkDigest("donuts: intersect", di, "22:7ece3f20dde5fb79");
        checkDigest("donuts: exclusiveOr", dx, "51:c5ea1a53dc60322b");
        check("donut union keeps both holes' uncovered parts", !du.contains(30, 50) && !du.contains(110, 50) && !du.contains(70, 50) && du.contains(70, 20));
        check("donut union is one outline with holes", du.getBounds2D().equals(new Rectangle2D.Double(0, 0, 140, 100)));

        // --- flattened iteration ---
        check("flattening iterator yields no curves", count(new FlatShape(union), PathIterator.SEG_CUBICTO) == 0
            && count(new FlatShape(union), PathIterator.SEG_LINETO) > 20);

        // --- transform ---
        AffineTransform rot = AffineTransform.getRotateInstance(Math.PI / 6, 50, 30);
        String before = digest(union);
        Area moved = union.createTransformedArea(rot);
        check("createTransformedArea leaves the source alone", digest(union).equals(before) && moved != union);
        Area inPlace = new Area(union);
        inPlace.transform(rot);
        check("transform equals createTransformedArea", digest(inPlace).equals(digest(moved)));
        checkDigest("rotated union", moved, "18:ca7b3014ecd61bd2");
        check("rotated union still curved", !moved.isPolygonal() && moved.isSingular());
        Area scaled = rect.createTransformedArea(AffineTransform.getScaleInstance(2, 0.5));
        check("scaled rectangle stays rectangular", scaled.isRectangular() && scaled.getBounds2D().equals(new Rectangle2D.Double(20, 10, 60, 20)));
        Area flipped = rect.createTransformedArea(AffineTransform.getScaleInstance(-1, 1));
        check("reflection keeps the interior", flipped.contains(-20, 30) && flipped.getBounds2D().equals(new Rectangle2D.Double(-40, 20, 30, 40)));
        check("transformed path iterator covers the same region", new Area(new TransformedShape(union, rot)).equals(moved));
        npe = false;
        try { rect.transform(null); } catch (NullPointerException e) { npe = true; }
        check("transform(null) throws NullPointerException", npe);

        // --- equals / clone / reset ---
        Area twoHalves = new Area(new Rectangle2D.Double(10, 20, 15, 40));
        twoHalves.add(new Area(new Rectangle2D.Double(25, 20, 15, 40)));
        check("two halves equal the whole rectangle", twoHalves.equals(rect) && rect.equals(twoHalves));
        check("halves merge into one rectangle", twoHalves.isRectangular() && twoHalves.isSingular());
        check("equals(Area) is geometric, equals(Object) is identity",
            rect.equals((Area) rect.clone()) && !rect.equals((Object) rect.clone()) && rect.equals((Object) rect));
        check("different areas are not equal", !rect.equals(a1) && !empty.equals(rect) && empty.equals(new Area()));
        check("equals(null) is false", !rect.equals((Area) null));
        Area copy = (Area) union.clone();
        copy.reset();
        check("reset empties the clone only", copy.isEmpty() && !union.isEmpty());
        Area fromArea = new Area(union);
        fromArea.subtract(union);
        check("area minus itself is empty", fromArea.isEmpty() && !union.isEmpty());
        Area self = new Area(union);
        self.add(self);
        check("adding an area to itself is a no-op", self.equals(union));
        self.exclusiveOr(self);
        check("xor with itself is empty", self.isEmpty());
        Area contained = new Area(rr1);
        contained.intersect(union);
        check("intersect with a superset keeps the operand", contained.equals(a1));

        System.out.println(total + " checks, " + failures + " failures");
        if (failures != 0) System.exit(1);
    }

    /** Exposes the transformed iterator through the Shape interface. */
    private static final class TransformedShape extends FlatShape {
        private final AffineTransform at;
        TransformedShape(Area area, AffineTransform at) { super(area); this.at = at; }
        @Override public PathIterator getPathIterator(AffineTransform ignored) { return area.getPathIterator(at); }
    }

    /** Exposes the flattened iterator through the Shape interface for the digest helpers. */
    private static class FlatShape implements java.awt.Shape {
        final Area area;
        FlatShape(Area area) { this.area = area; }
        public Rectangle getBounds() { return area.getBounds(); }
        public Rectangle2D getBounds2D() { return area.getBounds2D(); }
        public boolean contains(double x, double y) { return area.contains(x, y); }
        public boolean contains(java.awt.geom.Point2D p) { return area.contains(p); }
        public boolean intersects(double x, double y, double w, double h) { return area.intersects(x, y, w, h); }
        public boolean intersects(Rectangle2D r) { return area.intersects(r); }
        public boolean contains(double x, double y, double w, double h) { return area.contains(x, y, w, h); }
        public boolean contains(Rectangle2D r) { return area.contains(r); }
        public PathIterator getPathIterator(AffineTransform at) { return area.getPathIterator(at, 0.5); }
        public PathIterator getPathIterator(AffineTransform at, double flatness) { return area.getPathIterator(at, flatness); }
    }
}
