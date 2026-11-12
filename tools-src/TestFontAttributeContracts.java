// host-contract
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphVector;
import java.awt.font.GraphicAttribute;
import java.awt.font.ImageGraphicAttribute;
import java.awt.font.OpenType;
import java.awt.font.ShapeGraphicAttribute;
import java.awt.font.TransformAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/**
 * java.awt.font value classes: FontRenderContext hint/transform semantics and equality,
 * TransformAttribute, the GraphicAttribute family's metrics and equality, the
 * GlyphJustificationInfo validation and the OpenType sfnt tag constants.
 */
public final class TestFontAttributeContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static boolean throwsIae(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void fontRenderContext() {
        FontRenderContext plain = new FontRenderContext(null, false, false);
        check("frc plain not aa", !plain.isAntiAliased());
        check("frc plain not fm", !plain.usesFractionalMetrics());
        check("frc plain not transformed", !plain.isTransformed());
        check("frc plain identity type", plain.getTransformType() == AffineTransform.TYPE_IDENTITY);
        check("frc plain aa hint off", plain.getAntiAliasingHint() == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        check("frc plain fm hint off", plain.getFractionalMetricsHint() == RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

        FontRenderContext aa = new FontRenderContext(new AffineTransform(), true, true);
        check("frc aa", aa.isAntiAliased() && aa.usesFractionalMetrics());
        check("frc identity transform is not transformed", !aa.isTransformed());
        FontRenderContext aaHints = new FontRenderContext(null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        check("frc boolean and hint constructors equal", aa.equals(aaHints) && aaHints.equals((Object) aa));
        check("frc equal hashCode", aa.hashCode() == aaHints.hashCode());
        check("frc differs by aa", !aa.equals(new FontRenderContext(null, false, true)));
        check("frc equals non-frc false", !aa.equals((Object) "x"));
        check("frc equals null false", !aa.equals((Object) null));

        FontRenderContext lcd = new FontRenderContext(null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
        check("frc lcd is aa", lcd.isAntiAliased());
        check("frc lcd hint kept", lcd.getAntiAliasingHint() == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        check("frc fm default not fractional", !lcd.usesFractionalMetrics());
        check("frc fm default hint kept", lcd.getFractionalMetricsHint() == RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
        FontRenderContext aaDefault = new FontRenderContext(null,
                RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        check("frc aa default not aa", !aaDefault.isAntiAliased());

        // FontRenderContext compares hint values by identity, so every value of both keys
        // must be its own object and every one must give a context unequal to the others.
        Object[] aaValues = {
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP,
            RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR,
            RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR,
        };
        Object[] fmValues = {
            RenderingHints.VALUE_FRACTIONALMETRICS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
            RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT,
        };
        Object[] allValues = new Object[aaValues.length + fmValues.length];
        System.arraycopy(aaValues, 0, allValues, 0, aaValues.length);
        System.arraycopy(fmValues, 0, allValues, aaValues.length, fmValues.length);
        boolean distinct = true;
        for (int i = 0; i < allValues.length; i++) {
            for (int j = i + 1; j < allValues.length; j++) {
                if (allValues[i] == allValues[j]) distinct = false;
            }
        }
        check("frc hint values distinct objects", distinct);
        boolean aaContextsDistinct = true;
        boolean aaFlags = true;
        for (int i = 0; i < aaValues.length; i++) {
            FontRenderContext a = new FontRenderContext(null, aaValues[i], RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            if (a.getAntiAliasingHint() != aaValues[i]) aaFlags = false;
            boolean expectAa = i != 1 && i != 2;
            if (a.isAntiAliased() != expectAa) aaFlags = false;
            for (int j = 0; j < aaValues.length; j++) {
                FontRenderContext b = new FontRenderContext(null, aaValues[j], RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
                if (a.equals(b) != (i == j)) aaContextsDistinct = false;
            }
        }
        check("frc each aa value kept and classified", aaFlags);
        check("frc contexts equal only for the same aa value", aaContextsDistinct);
        boolean fmFlags = true;
        for (int i = 0; i < fmValues.length; i++) {
            FontRenderContext f = new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, fmValues[i]);
            if (f.getFractionalMetricsHint() != fmValues[i] || f.usesFractionalMetrics() != (i == 0)) fmFlags = false;
            for (int j = 0; j < fmValues.length; j++) {
                FontRenderContext g = new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, fmValues[j]);
                if (f.equals(g) != (i == j)) fmFlags = false;
            }
        }
        check("frc each fm value kept, classified and distinct", fmFlags);
        check("frc aa value rejected as fm hint", throwsIae(new Runnable() {
            public void run() {
                new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
        }));
        check("frc null hint rejected", throwsIae(new Runnable() {
            public void run() {
                new FontRenderContext(null, null, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }
        }));

        check("frc bad aa hint", throwsIae(new Runnable() {
            public void run() {
                new FontRenderContext(null, RenderingHints.VALUE_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            }
        }));
        check("frc bad fm hint", throwsIae(new Runnable() {
            public void run() {
                new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON, "nope");
            }
        }));

        AffineTransform scale = AffineTransform.getScaleInstance(2, 2);
        FontRenderContext scaled = new FontRenderContext(scale, true, false);
        scale.setToIdentity();
        check("frc copies transform", scaled.getTransform().getScaleX() == 2);
        check("frc scaled transformed", scaled.isTransformed());
        check("frc scaled type", scaled.getTransformType() == AffineTransform.TYPE_UNIFORM_SCALE);
        scaled.getTransform().setToIdentity();
        check("frc getTransform is a copy", scaled.getTransform().getScaleX() == 2);
        check("frc scaled vs unscaled", !scaled.equals(new FontRenderContext(null, true, false)));
        check("frc scaled equal", scaled.equals(new FontRenderContext(AffineTransform.getScaleInstance(2, 2), true, false)));
    }

    private static void transformAttribute() {
        check("ta identity constant", TransformAttribute.IDENTITY.isIdentity());
        check("ta null is identity", new TransformAttribute(null).isIdentity());
        check("ta identity equals IDENTITY", new TransformAttribute(new AffineTransform()).equals(TransformAttribute.IDENTITY));
        AffineTransform rot = AffineTransform.getRotateInstance(0.5);
        TransformAttribute a = new TransformAttribute(rot);
        rot.setToIdentity();
        check("ta copies transform", !a.isIdentity() && !a.getTransform().isIdentity());
        check("ta equals", a.equals(new TransformAttribute(AffineTransform.getRotateInstance(0.5))));
        check("ta hashCode", a.hashCode() == new TransformAttribute(AffineTransform.getRotateInstance(0.5)).hashCode());
        check("ta identity hashCode 0", TransformAttribute.IDENTITY.hashCode() == 0);
        check("ta not equal other", !a.equals("x") && !a.equals(null));
    }

    private static void graphicAttributes() {
        check("ga alignment constants", GraphicAttribute.TOP_ALIGNMENT == -1 && GraphicAttribute.BOTTOM_ALIGNMENT == -2
                && GraphicAttribute.ROMAN_BASELINE == 0 && GraphicAttribute.CENTER_BASELINE == 1
                && GraphicAttribute.HANGING_BASELINE == 2);
        final Rectangle shape = new Rectangle(-2, -5, 10, 8);
        check("ga alignment too low", throwsIae(new Runnable() {
            public void run() {
                new ShapeGraphicAttribute(shape, -3, ShapeGraphicAttribute.FILL);
            }
        }));
        check("ga alignment too high", throwsIae(new Runnable() {
            public void run() {
                new ShapeGraphicAttribute(shape, 3, ShapeGraphicAttribute.FILL);
            }
        }));

        ShapeGraphicAttribute fill = new ShapeGraphicAttribute(shape, GraphicAttribute.CENTER_BASELINE, ShapeGraphicAttribute.FILL);
        check("sga ascent", fill.getAscent() == 5f);
        check("sga descent", fill.getDescent() == 3f);
        check("sga advance", fill.getAdvance() == 8f);
        check("sga alignment", fill.getAlignment() == GraphicAttribute.CENTER_BASELINE);
        check("sga fill bounds", fill.getBounds().equals(new Rectangle2D.Float(-2, -5, 10, 8)));
        ShapeGraphicAttribute stroke = new ShapeGraphicAttribute(shape, GraphicAttribute.CENTER_BASELINE, ShapeGraphicAttribute.STROKE);
        check("sga stroke bounds grow by one", stroke.getBounds().equals(new Rectangle2D.Float(-2, -5, 11, 9)));
        check("sga outline null tx is shape", fill.getOutline(null) == shape);
        Shape moved = fill.getOutline(AffineTransform.getTranslateInstance(3, 4));
        check("sga outline transformed", moved.getBounds2D().equals(new Rectangle2D.Double(1, -1, 10, 8)));
        check("sga equals", fill.equals(new ShapeGraphicAttribute(new Rectangle(-2, -5, 10, 8), 1, false)));
        check("sga equals Object", fill.equals((Object) new ShapeGraphicAttribute(new Rectangle(-2, -5, 10, 8), 1, false)));
        check("sga hashCode", fill.hashCode() == shape.hashCode());
        check("sga stroke differs", !fill.equals(stroke));
        check("sga alignment differs", !fill.equals(new ShapeGraphicAttribute(shape, 0, false)));
        check("sga not equal other", !fill.equals((Object) "x") && !fill.equals((ShapeGraphicAttribute) null));

        GlyphJustificationInfo info = fill.getJustificationInfo();
        check("ga justification weight", info.weight == 8f);
        check("ga justification grow", !info.growAbsorb && info.growPriority == GlyphJustificationInfo.PRIORITY_INTERCHAR
                && info.growLeftLimit == 8f / 3 && info.growRightLimit == 8f / 3);
        check("ga justification shrink", !info.shrinkAbsorb && info.shrinkPriority == GlyphJustificationInfo.PRIORITY_WHITESPACE
                && info.shrinkLeftLimit == 0 && info.shrinkRightLimit == 0);
        check("gji priorities", GlyphJustificationInfo.PRIORITY_KASHIDA == 0 && GlyphJustificationInfo.PRIORITY_NONE == 3);
        check("gji negative weight", throwsIae(new Runnable() {
            public void run() {
                new GlyphJustificationInfo(-1, false, 0, 0, 0, false, 0, 0, 0);
            }
        }));
        check("gji bad priority", throwsIae(new Runnable() {
            public void run() {
                new GlyphJustificationInfo(1, false, 4, 0, 0, false, 0, 0, 0);
            }
        }));
        check("gji negative limit", throwsIae(new Runnable() {
            public void run() {
                new GlyphJustificationInfo(1, false, 0, 0, 0, false, 0, 0, -1);
            }
        }));

        Image image = new SizedImage(10, 6);
        ImageGraphicAttribute iga = new ImageGraphicAttribute(image, GraphicAttribute.ROMAN_BASELINE, 2, 4);
        check("iga ascent", iga.getAscent() == 4f);
        check("iga descent", iga.getDescent() == 2f);
        check("iga advance", iga.getAdvance() == 8f);
        check("iga bounds", iga.getBounds().equals(new Rectangle2D.Float(-2, -4, 10, 6)));
        check("iga default outline is bounds", iga.getOutline(null).getBounds2D().equals(new Rectangle2D.Float(-2, -4, 10, 6)));
        ImageGraphicAttribute origin = new ImageGraphicAttribute(image, GraphicAttribute.TOP_ALIGNMENT);
        check("iga origin zero", origin.getAscent() == 0f && origin.getDescent() == 6f && origin.getAdvance() == 10f);
        check("iga equals", iga.equals(new ImageGraphicAttribute(image, 0, 2, 4)));
        check("iga origin differs", !iga.equals(new ImageGraphicAttribute(image, 0, 2, 3)));
        check("iga hashCode", iga.hashCode() == image.hashCode());

        // draw() is checked against a recording Graphics2D: the host patch carries the shadow Raster
        // but not the shadow BufferedImage (it needs the Android backend), so a real canvas would mix
        // the two pipelines; what the attributes owe the Graphics2D is the call sequence.
        Rectangle small = new Rectangle(0, -3, 4, 3);
        RecordingGraphics g = new RecordingGraphics();
        new ShapeGraphicAttribute(small, 0, ShapeGraphicAttribute.FILL).draw(g, 2.7f, 5.2f);
        check("sga fill draw at truncated origin", g.log.toString().equals("translate 2,5;fill@2,5;translate -2,-5;"));
        check("sga fill draws its own shape", g.lastShape == small);
        check("sga draw restores translation", g.getTransform().isIdentity());
        g = new RecordingGraphics();
        new ShapeGraphicAttribute(small, 0, ShapeGraphicAttribute.STROKE).draw(g, -1.5f, 0.9f);
        check("sga stroke draw outlines", g.log.toString().equals("translate -1,0;draw@-1,0;translate 1,0;"));
        final RecordingGraphics failing = new RecordingGraphics();
        failing.failPainting = true;
        boolean thrown = false;
        try {
            new ShapeGraphicAttribute(small, 0, ShapeGraphicAttribute.FILL).draw(failing, 3f, 4f);
        } catch (IllegalStateException expected) {
            thrown = true;
        }
        check("sga draw untranslates when painting throws", thrown && failing.getTransform().isIdentity());
        g = new RecordingGraphics();
        iga.draw(g, 5.5f, 7.9f);
        check("iga draw at truncated top-left", g.log.toString().equals("image 3,3 null;") && g.lastImage == image);
        check("iga draw leaves transform", g.getTransform().isIdentity());
    }

    /** An image of fixed size; ImageGraphicAttribute only asks for the size. */
    private static final class SizedImage extends Image {
        private final int width;
        private final int height;

        SizedImage(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int getWidth(ImageObserver observer) {
            return width;
        }

        public int getHeight(ImageObserver observer) {
            return height;
        }

        public ImageProducer getSource() {
            throw new UnsupportedOperationException("getSource");
        }

        public Graphics getGraphics() {
            throw new UnsupportedOperationException("getGraphics");
        }

        public Object getProperty(String name, ImageObserver observer) {
            return UndefinedProperty;
        }
    }

    /**
     * Records translate/fill/draw/drawImage and tracks the translation; everything else throws.
     * Implements the abstract members of both the JDK's and the shadow's Graphics2D, so it links
     * against whichever one the run loads.
     */
    private static final class RecordingGraphics extends Graphics2D {
        final StringBuilder log = new StringBuilder();
        final AffineTransform transform = new AffineTransform();
        Shape lastShape;
        Image lastImage;
        boolean failPainting;

        private static UnsupportedOperationException no(String member) {
            return new UnsupportedOperationException(member);
        }

        private String at() {
            return (int) transform.getTranslateX() + "," + (int) transform.getTranslateY();
        }

        private void paint(String op, Shape s) {
            if (failPainting) throw new IllegalStateException(op);
            lastShape = s;
            log.append(op).append('@').append(at()).append(';');
        }

        public void translate(int x, int y) {
            log.append("translate ").append(x).append(',').append(y).append(';');
            transform.translate(x, y);
        }

        public void translate(double tx, double ty) {
            log.append("translate ").append(tx).append(',').append(ty).append(';');
            transform.translate(tx, ty);
        }

        public AffineTransform getTransform() {
            return new AffineTransform(transform);
        }

        public void setTransform(AffineTransform tx) {
            transform.setTransform(tx);
        }

        public void transform(AffineTransform tx) {
            transform.concatenate(tx);
        }

        public void fill(Shape s) {
            paint("fill", s);
        }

        public void draw(Shape s) {
            paint("draw", s);
        }

        public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
            lastImage = img;
            log.append("image ").append(x).append(',').append(y).append(' ').append(observer).append(';');
            return true;
        }

        public Graphics create() { throw no("create"); }
        public Color getColor() { throw no("getColor"); }
        public void setColor(Color c) { throw no("setColor"); }
        public void setPaintMode() { throw no("setPaintMode"); }
        public void setXORMode(Color c1) { throw no("setXORMode"); }
        public Font getFont() { throw no("getFont"); }
        public void setFont(Font font) { throw no("setFont"); }
        public FontMetrics getFontMetrics(Font f) { throw no("getFontMetrics"); }
        public Rectangle getClipBounds() { throw no("getClipBounds"); }
        public void clipRect(int x, int y, int width, int height) { throw no("clipRect"); }
        public void setClip(int x, int y, int width, int height) { throw no("setClip"); }
        public Shape getClip() { throw no("getClip"); }
        public void setClip(Shape clip) { throw no("setClip"); }
        public void clip(Shape s) { throw no("clip"); }
        public void copyArea(int x, int y, int width, int height, int dx, int dy) { throw no("copyArea"); }
        public void drawLine(int x1, int y1, int x2, int y2) { throw no("drawLine"); }
        public void fillRect(int x, int y, int width, int height) { throw no("fillRect"); }
        public void clearRect(int x, int y, int width, int height) { throw no("clearRect"); }
        public void drawRoundRect(int x, int y, int width, int height, int aw, int ah) { throw no("drawRoundRect"); }
        public void fillRoundRect(int x, int y, int width, int height, int aw, int ah) { throw no("fillRoundRect"); }
        public void drawOval(int x, int y, int width, int height) { throw no("drawOval"); }
        public void fillOval(int x, int y, int width, int height) { throw no("fillOval"); }
        public void drawArc(int x, int y, int width, int height, int start, int arc) { throw no("drawArc"); }
        public void fillArc(int x, int y, int width, int height, int start, int arc) { throw no("fillArc"); }
        public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) { throw no("drawPolyline"); }
        public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) { throw no("drawPolygon"); }
        public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) { throw no("fillPolygon"); }
        public void drawString(String str, int x, int y) { throw no("drawString"); }
        public void drawString(String str, float x, float y) { throw no("drawString"); }
        public void drawString(AttributedCharacterIterator iterator, int x, int y) { throw no("drawString"); }
        public void drawString(AttributedCharacterIterator iterator, float x, float y) { throw no("drawString"); }
        public void drawGlyphVector(GlyphVector gv, float x, float y) { throw no("drawGlyphVector"); }
        public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver o) { throw no("drawImage"); }
        public boolean drawImage(Image img, int x, int y, Color bg, ImageObserver o) { throw no("drawImage"); }
        public boolean drawImage(Image img, int x, int y, int w, int h, Color bg, ImageObserver o) { throw no("drawImage"); }
        public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                                 int sx1, int sy1, int sx2, int sy2, ImageObserver o) { throw no("drawImage"); }
        public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                                 int sx1, int sy1, int sx2, int sy2, Color bg, ImageObserver o) { throw no("drawImage"); }
        public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) { throw no("drawImage"); }
        public void drawImage(BufferedImage img, int x, int y) { throw no("drawImage"); }
        public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) { throw no("drawImage"); }
        public void drawRenderedImage(RenderedImage img, AffineTransform xform) { throw no("drawRenderedImage"); }
        public void drawRenderableImage(RenderableImage img, AffineTransform xform) { throw no("drawRenderableImage"); }
        public boolean hit(Rectangle rect, Shape s, boolean onStroke) { throw no("hit"); }
        public GraphicsConfiguration getDeviceConfiguration() { throw no("getDeviceConfiguration"); }
        public void dispose() { }
        public Composite getComposite() { throw no("getComposite"); }
        public void setComposite(Composite comp) { throw no("setComposite"); }
        public Paint getPaint() { throw no("getPaint"); }
        public void setPaint(Paint paint) { throw no("setPaint"); }
        public Stroke getStroke() { throw no("getStroke"); }
        public void setStroke(Stroke s) { throw no("setStroke"); }
        public void rotate(double theta) { throw no("rotate"); }
        public void rotate(double theta, double x, double y) { throw no("rotate"); }
        public void scale(double sx, double sy) { throw no("scale"); }
        public void shear(double shx, double shy) { throw no("shear"); }
        public void setRenderingHint(RenderingHints.Key key, Object value) { throw no("setRenderingHint"); }
        public Object getRenderingHint(RenderingHints.Key key) { throw no("getRenderingHint"); }
        public void setRenderingHints(Map<?, ?> hints) { throw no("setRenderingHints"); }
        public void addRenderingHints(Map<?, ?> hints) { throw no("addRenderingHints"); }
        public RenderingHints getRenderingHints() { throw no("getRenderingHints"); }
        public Color getBackground() { throw no("getBackground"); }
        public void setBackground(Color color) { throw no("setBackground"); }
        public FontRenderContext getFontRenderContext() { throw no("getFontRenderContext"); }
    }

    private static void openTypeTags() throws IllegalAccessException {
        int count = 0;
        boolean all = true;
        for (Field f : OpenType.class.getDeclaredFields()) {
            if (!f.getName().startsWith("TAG_") || !Modifier.isStatic(f.getModifiers())) continue;
            count++;
            String name = f.getName().substring(4);
            String tag = name.equals("OS2") ? "OS/2" : name.equals("CVT") ? "cvt " : name.equals("CFF") ? "CFF " : name;
            int expected = -1;
            for (String candidate : new String[] {tag, tag.toLowerCase()}) {
                byte[] b = candidate.getBytes(StandardCharsets.US_ASCII);
                int v = (b[0] & 255) << 24 | (b[1] & 255) << 16 | (b[2] & 255) << 8 | (b[3] & 255);
                if (v == f.getInt(null)) expected = v;
            }
            if (expected == -1) {
                all = false;
                System.out.println("     tag mismatch " + f.getName() + " = 0x" + Integer.toHexString(f.getInt(null)));
            }
        }
        check("opentype tag count " + count, count == 51);
        check("opentype tags are their four-char codes", all);
        check("opentype cmap", OpenType.TAG_CMAP == 0x636d6170 && OpenType.TAG_GSUB == 0x47535542);
    }

    public static void main(String[] args) throws Exception {
        fontRenderContext();
        transformAttribute();
        graphicAttributes();
        openTypeTags();
        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
