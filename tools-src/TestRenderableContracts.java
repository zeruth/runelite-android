// host-contract
import java.awt.Graphics2D;
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

        check("RasterFormatException keeps its message", "bad raster".equals(new RasterFormatException("bad raster").getMessage()));
        check("ImagingOpException keeps its message", "bad op".equals(new ImagingOpException("bad op").getMessage()));

        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }
}
