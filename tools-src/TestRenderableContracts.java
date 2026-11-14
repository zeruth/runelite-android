// host-contract: patched-args=shadow
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.text.AttributedCharacterIterator;
import java.util.Map;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.awt.image.RasterFormatException;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderContext;
import java.awt.image.renderable.RenderableImage;
import java.util.Vector;

/**
 * java.awt.image.renderable contracts: ParameterBlock bookkeeping, RenderContext copies,
 * and Graphics2D.drawRenderableImage asking for a rendering in device space and drawing
 * it untransformed. Translation-only transforms keep the assertions independent of the
 * concatenation order, which only desktop's SunGraphics2D defines.
 */
public final class TestRenderableContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    /** A 2x2 opaque red tile at the origin that records the context it was asked for. */
    private static final class RecordingRenderable implements RenderableImage {
        RenderContext seen;

        @Override public Vector<RenderableImage> getSources() { return null; }
        @Override public Object getProperty(String name) { return java.awt.Image.UndefinedProperty; }
        @Override public String[] getPropertyNames() { return null; }
        @Override public boolean isDynamic() { return false; }
        @Override public float getWidth() { return 2f; }
        @Override public float getHeight() { return 2f; }
        @Override public float getMinX() { return 0f; }
        @Override public float getMinY() { return 0f; }
        @Override public RenderedImage createScaledRendering(int w, int h, RenderingHints hints) {
            return createDefaultRendering();
        }
        @Override public RenderedImage createDefaultRendering() {
            return createRendering(new RenderContext(new AffineTransform()));
        }
        @Override public RenderedImage createRendering(RenderContext context) {
            seen = context;
            BufferedImage tile = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) tile.setRGB(x, y, 0xffff0000);
            return tile;
        }
    }

    public static void main(String[] args) {
        ParameterBlock pb = new ParameterBlock();
        pb.add(5).add(2.5f).add("label");
        check("ParameterBlock counts parameters", pb.getNumParameters() == 3);
        check("ParameterBlock unboxes int", pb.getIntParameter(0) == 5);
        check("ParameterBlock unboxes float", pb.getFloatParameter(1) == 2.5f);
        Class<?>[] classes = pb.getParamClasses();
        check("ParameterBlock reports primitive int class", classes[0] == Integer.TYPE);
        check("ParameterBlock reports primitive float class", classes[1] == Float.TYPE);
        check("ParameterBlock reports object class", classes[2] == String.class);
        BufferedImage source = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        pb.addSource(source);
        check("ParameterBlock keeps sources", pb.getNumSources() == 1 && pb.getRenderedSource(0) == source);
        ParameterBlock shallow = (ParameterBlock) pb.shallowClone();
        check("shallowClone shares the parameter vector", shallow.getParameters() == pb.getParameters());
        ParameterBlock deep = (ParameterBlock) pb.clone();
        check("clone copies the parameter vector", deep.getParameters() != pb.getParameters()
            && deep.getIntParameter(0) == 5);

        AffineTransform at = AffineTransform.getTranslateInstance(7, 9);
        RenderContext rc = new RenderContext(at);
        at.translate(1, 1);
        check("RenderContext copies its transform", rc.getTransform().getTranslateX() == 7);
        rc.getTransform().translate(100, 100);
        check("RenderContext.getTransform returns a copy", rc.getTransform().getTranslateX() == 7);
        check("RenderContext without a clip", rc.getAreaOfInterest() == null);

        if (args.length > 0 && args[0].equals("shadow")) {
            // Patched run: the JDK's BufferedImage holds a shadow raster its surface pipeline
            // cannot draw into (InvalidPipeException), and SunGraphics2D overrides both methods
            // anyway. A recording Graphics2D runs the shadow's own drawRenderableImage and
            // drawRenderedImage and shows what they hand to drawImage.
            checkShadowGraphics2D();
            checkExceptions();
            return;
        }
        BufferedImage dest = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dest.createGraphics();
        g.translate(3, 4);
        RecordingRenderable renderable = new RecordingRenderable();
        g.drawRenderableImage(renderable, AffineTransform.getTranslateInstance(1, 1));
        g.drawRenderableImage(null, new AffineTransform());
        g.dispose();
        AffineTransform requested = renderable.seen == null ? null : renderable.seen.getTransform();
        check("drawRenderableImage requests user x graphics transform", requested != null
            && requested.getTranslateX() == 4 && requested.getTranslateY() == 5);
        check("drawRenderableImage draws the rendering in device space",
            dest.getRGB(0, 0) == 0xffff0000 && dest.getRGB(1, 1) == 0xffff0000);
        check("drawRenderableImage leaves the rest untouched", dest.getRGB(2, 2) == 0 && dest.getRGB(4, 5) == 0);

        BufferedImage moved = new BufferedImage(6, 6, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = moved.createGraphics();
        g2.drawRenderedImage(renderable.createDefaultRendering(), AffineTransform.getTranslateInstance(2, 3));
        g2.drawRenderedImage(null, new AffineTransform());
        g2.dispose();
        check("drawRenderedImage applies its transform", moved.getRGB(2, 3) == 0xffff0000
            && moved.getRGB(3, 4) == 0xffff0000 && moved.getRGB(0, 0) == 0);

        checkExceptions();
    }

    /** Shadow Graphics2D's drawRenderableImage/drawRenderedImage, seen through what they draw. */
    private static void checkShadowGraphics2D() {
        RecordingGraphics2D g = new RecordingGraphics2D();
        g.translate(3, 4);
        RecordingRenderable renderable = new RecordingRenderable();
        g.drawRenderableImage(renderable, AffineTransform.getTranslateInstance(1, 1));
        AffineTransform requested = renderable.seen == null ? null : renderable.seen.getTransform();
        check("shadow drawRenderableImage requests user x graphics transform", requested != null
            && requested.getTranslateX() == 4 && requested.getTranslateY() == 5);
        check("shadow drawRenderableImage draws the rendering once", g.drawn.size() == 1);
        check("shadow drawRenderableImage undoes the graphics transform", g.drawn.size() == 1
            && g.transforms.get(0).getTranslateX() == -3 && g.transforms.get(0).getTranslateY() == -4);
        check("shadow drawRenderableImage draws the rendering's pixels", g.drawn.size() == 1
            && g.drawn.get(0).getRGB(1, 1) == 0xffff0000);
        g.drawRenderableImage(null, new AffineTransform());
        check("shadow drawRenderableImage(null) draws nothing", g.drawn.size() == 1);

        g = new RecordingGraphics2D();
        BufferedImage tile = (BufferedImage) renderable.createDefaultRendering();
        g.drawRenderedImage(tile, AffineTransform.getTranslateInstance(2, 3));
        check("shadow drawRenderedImage draws a BufferedImage as is", g.drawn.size() == 1 && g.drawn.get(0) == tile
            && g.transforms.get(0).getTranslateX() == 2 && g.transforms.get(0).getTranslateY() == 3);

        g = new RecordingGraphics2D();
        BufferedImage pixels = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xff00ff00);
        pixels.setRGB(1, 0, 0x800000ff);
        g.drawRenderedImage(new OffsetRendering(pixels, 5, 6), AffineTransform.getTranslateInstance(2, 3));
        BufferedImage converted = g.drawn.isEmpty() ? null : g.drawn.get(0);
        check("shadow drawRenderedImage converts any RenderedImage", converted != null
            && converted.getWidth() == 2 && converted.getHeight() == 1
            && converted.getRGB(0, 0) == 0xff00ff00 && converted.getRGB(1, 0) == 0x800000ff);
        check("shadow drawRenderedImage places the rendering at its minX/minY", converted != null
            && g.transforms.get(0).getTranslateX() == 7 && g.transforms.get(0).getTranslateY() == 9);
        g.drawRenderedImage(null, new AffineTransform());
        check("shadow drawRenderedImage(null) draws nothing", g.drawn.size() == 1);
    }

    /** A rendering whose pixel space starts at (minX, minY), as RenderableImageOp results may. */
    private static final class OffsetRendering implements RenderedImage {
        private final BufferedImage image;
        private final int minX;
        private final int minY;

        OffsetRendering(BufferedImage image, int minX, int minY) {
            this.image = image;
            this.minX = minX;
            this.minY = minY;
        }

        @Override public int getWidth() { return image.getWidth(); }
        @Override public int getHeight() { return image.getHeight(); }
        @Override public int getMinX() { return minX; }
        @Override public int getMinY() { return minY; }
        @Override public int getNumXTiles() { return 1; }
        @Override public int getNumYTiles() { return 1; }
        @Override public int getMinTileX() { return 0; }
        @Override public int getMinTileY() { return 0; }
        @Override public int getTileWidth() { return image.getWidth(); }
        @Override public int getTileHeight() { return image.getHeight(); }
        @Override public int getTileGridXOffset() { return minX; }
        @Override public int getTileGridYOffset() { return minY; }
        @Override public ColorModel getColorModel() { return image.getColorModel(); }
        @Override public SampleModel getSampleModel() { return image.getSampleModel(); }
        @Override public Vector<RenderedImage> getSources() { return null; }
        @Override public Object getProperty(String name) { return Image.UndefinedProperty; }
        @Override public String[] getPropertyNames() { return null; }
        @Override public Raster getTile(int tileX, int tileY) { return getData(); }
        @Override public Raster getData() { return image.getData().createTranslatedChild(minX, minY); }
        @Override public Raster getData(Rectangle rect) { throw new UnsupportedOperationException("getData(Rectangle)"); }
        @Override public WritableRaster copyData(WritableRaster raster) { throw new UnsupportedOperationException("copyData"); }
    }

    /**
     * Keeps a transform and records every drawImage(Image, AffineTransform, ImageObserver);
     * everything else is unused by the methods under test.
     */
    private static final class RecordingGraphics2D extends Graphics2D {
        final java.util.List<BufferedImage> drawn = new java.util.ArrayList<>();
        final java.util.List<AffineTransform> transforms = new java.util.ArrayList<>();
        private final AffineTransform transform = new AffineTransform();

        private static UnsupportedOperationException unused(String member) {
            return new UnsupportedOperationException(member);
        }

        @Override public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
            drawn.add((BufferedImage) img);
            transforms.add(new AffineTransform(xform));
            return true;
        }
        @Override public AffineTransform getTransform() { return new AffineTransform(transform); }
        @Override public void setTransform(AffineTransform tx) { transform.setTransform(tx); }
        @Override public void transform(AffineTransform tx) { transform.concatenate(tx); }
        @Override public void translate(double tx, double ty) { transform.translate(tx, ty); }
        @Override public void translate(int x, int y) { transform.translate(x, y); }
        @Override public void rotate(double theta) { transform.rotate(theta); }
        @Override public void rotate(double theta, double x, double y) { transform.rotate(theta, x, y); }
        @Override public void scale(double sx, double sy) { transform.scale(sx, sy); }
        @Override public void shear(double shx, double shy) { transform.shear(shx, shy); }
        @Override public void dispose() { }

        @Override public void drawGlyphVector(java.awt.font.GlyphVector glyphs, float x, float y) { throw unused("drawGlyphVector"); }
        @Override public void draw(Shape s) { throw unused("draw"); }
        @Override public void fill(Shape s) { throw unused("fill"); }
        @Override public void drawImage(BufferedImage img, int x, int y) { throw unused("drawImage"); }
        @Override public Composite getComposite() { throw unused("getComposite"); }
        @Override public void setComposite(Composite comp) { throw unused("setComposite"); }
        @Override public Paint getPaint() { throw unused("getPaint"); }
        @Override public void setPaint(Paint paint) { throw unused("setPaint"); }
        @Override public Stroke getStroke() { throw unused("getStroke"); }
        @Override public void setStroke(Stroke s) { throw unused("setStroke"); }
        @Override public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) { throw unused("setRenderingHint"); }
        @Override public Object getRenderingHint(RenderingHints.Key hintKey) { throw unused("getRenderingHint"); }
        @Override public void setRenderingHints(Map<?, ?> hints) { throw unused("setRenderingHints"); }
        @Override public void addRenderingHints(Map<?, ?> hints) { throw unused("addRenderingHints"); }
        @Override public RenderingHints getRenderingHints() { throw unused("getRenderingHints"); }
        @Override public Color getBackground() { throw unused("getBackground"); }
        @Override public void setBackground(Color color) { throw unused("setBackground"); }
        @Override public void drawString(AttributedCharacterIterator iterator, int x, int y) { throw unused("drawString"); }
        @Override public void drawString(AttributedCharacterIterator iterator, float x, float y) { throw unused("drawString"); }
        @Override public void drawString(String str, float x, float y) { throw unused("drawString"); }
        @Override public void drawString(String str, int x, int y) { throw unused("drawString"); }
        @Override public FontRenderContext getFontRenderContext() { throw unused("getFontRenderContext"); }
        @Override public Graphics create() { throw unused("create"); }
        @Override public Color getColor() { throw unused("getColor"); }
        @Override public void setColor(Color c) { throw unused("setColor"); }
        @Override public void setPaintMode() { throw unused("setPaintMode"); }
        @Override public void setXORMode(Color c1) { throw unused("setXORMode"); }
        @Override public Font getFont() { throw unused("getFont"); }
        @Override public void setFont(Font font) { throw unused("setFont"); }
        @Override public FontMetrics getFontMetrics(Font f) { throw unused("getFontMetrics"); }
        @Override public Rectangle getClipBounds() { throw unused("getClipBounds"); }
        @Override public void clipRect(int x, int y, int width, int height) { throw unused("clipRect"); }
        @Override public void setClip(int x, int y, int width, int height) { throw unused("setClip"); }
        @Override public Shape getClip() { throw unused("getClip"); }
        @Override public void setClip(Shape clip) { throw unused("setClip"); }
        @Override public void copyArea(int x, int y, int width, int height, int dx, int dy) { throw unused("copyArea"); }
        @Override public void drawLine(int x1, int y1, int x2, int y2) { throw unused("drawLine"); }
        @Override public void fillRect(int x, int y, int width, int height) { throw unused("fillRect"); }
        @Override public void clearRect(int x, int y, int width, int height) { throw unused("clearRect"); }
        @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { throw unused("drawRoundRect"); }
        @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { throw unused("fillRoundRect"); }
        @Override public void drawOval(int x, int y, int width, int height) { throw unused("drawOval"); }
        @Override public void fillOval(int x, int y, int width, int height) { throw unused("fillOval"); }
        @Override public void drawArc(int x, int y, int w, int h, int start, int arc) { throw unused("drawArc"); }
        @Override public void fillArc(int x, int y, int w, int h, int start, int arc) { throw unused("fillArc"); }
        @Override public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) { throw unused("drawPolyline"); }
        @Override public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) { throw unused("drawPolygon"); }
        @Override public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) { throw unused("fillPolygon"); }
        @Override public boolean drawImage(Image img, int x, int y, ImageObserver observer) { throw unused("drawImage"); }
        @Override public boolean drawImage(Image img, int x, int y, int w, int h, ImageObserver observer) { throw unused("drawImage"); }
        @Override public boolean drawImage(Image img, int x, int y, Color bg, ImageObserver observer) { throw unused("drawImage"); }
        @Override public boolean drawImage(Image img, int x, int y, int w, int h, Color bg, ImageObserver observer) { throw unused("drawImage"); }
        @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                                           int sx1, int sy1, int sx2, int sy2, ImageObserver observer) { throw unused("drawImage"); }
        @Override public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2,
                                           int sx1, int sy1, int sx2, int sy2, Color bg, ImageObserver observer) { throw unused("drawImage"); }
    }

    /** The exception checks, then the summary and exit status. */
    private static void checkExceptions() {
        // BandCombineOp, the last java.awt.image class: swap R and B, and add 10 to B via
        // the extra matrix column, in place as the class Javadoc allows.
        BufferedImage rgb = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, 0x102030);
        rgb.setRGB(1, 0, 0xf0e0d0);
        java.awt.image.BandCombineOp swap = new java.awt.image.BandCombineOp(
            new float[][] {{0, 0, 1, 0}, {0, 1, 0, 0}, {1, 0, 0, 10}}, null);
        WritableRaster combined = swap.filter(rgb.getRaster(), rgb.getRaster());
        check("BandCombineOp filters in place", combined == rgb.getRaster()
            && (rgb.getRGB(0, 0) & 0xffffff) == 0x30201a && (rgb.getRGB(1, 0) & 0xffffff) == 0xd0e0fa);
        check("BandCombineOp.getMatrix is a copy", swap.getMatrix() != swap.getMatrix()
            && swap.getMatrix()[2][3] == 10f);
        boolean threw = false;
        try {
            new java.awt.image.BandCombineOp(new float[][] {{1, 0}}, null).filter(rgb.getRaster(), null);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check("BandCombineOp rejects a matrix of the wrong width", threw);
        check("RasterFormatException keeps its message", "bad raster".equals(new RasterFormatException("bad raster").getMessage()));
        check("ImagingOpException keeps its message", "bad op".equals(new ImagingOpException("bad op").getMessage()));

        System.out.println(total - failures + "/" + total + " passed");
        // Thrown rather than System.exit: the swing-ui-probe "canvas" command runs main()
        // in-process on the device, where the plain path draws through android-awt's own
        // Graphics2D; on the host an uncaught error still exits non-zero.
        if (failures != 0) throw new AssertionError(failures + " renderable contract(s) failed");
    }
}
