import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.text.AttributedString;
import java.util.*;
import javax.imageio.ImageIO;

/** Runs against desktop Java and the Android shim; no client or user data is opened. */
public final class TestGraphicsCompatibility {
    private interface Check { void run() throws Exception; }
    private static int passed, failed;
    private static void check(String name, Check test) {
        try { test.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); error.printStackTrace(System.out); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void near(double actual, double expected, double tolerance) { require(Math.abs(actual - expected) <= tolerance, actual + " != " + expected); }
    private static BufferedImage image(int w, int h) { return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); }
    private static int colorDistance(int a, int b) { int maximum = 0; for (int shift = 0; shift <= 24; shift += 8) maximum = Math.max(maximum, Math.abs((a >>> shift & 255) - (b >>> shift & 255))); return maximum; }
    private static void color(int actual, int expected, int tolerance) { require(colorDistance(actual, expected) <= tolerance, Integer.toHexString(actual) + " != " + Integer.toHexString(expected)); }
    private static int nonempty(BufferedImage image) { int count = 0; for (int pixel : image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth())) if ((pixel >>> 24) != 0) count++; return count; }
    private static Path2D triangle() { Path2D path = new Path2D.Double(); path.moveTo(0, 0); path.lineTo(10, 0); path.lineTo(0, 10); path.closePath(); return path; }
    public static void main(String[] args) throws Exception {
        check("transform-composition", () -> {
            Graphics2D g = image(20, 20).createGraphics(); g.translate(3, 4); g.rotate(Math.PI / 2); g.shear(.25, .5); g.transform(AffineTransform.getScaleInstance(2, 3));
            AffineTransform expected = new AffineTransform(); expected.translate(3, 4); expected.rotate(Math.PI / 2); expected.shear(.25, .5); expected.scale(2, 3);
            require(g.getTransform().equals(expected), "transform lost"); g.dispose();
        });
        check("transform-inverse-overlap", () -> {
            AffineTransform transform = new AffineTransform(2, 1, 3, 4, 5, 6);
            Point2D point = transform.inverseTransform(transform.transform(new Point2D.Double(2, 7), null), null); near(point.getX(), 2, 1e-9); near(point.getY(), 7, 1e-9);
            double[] values = {1, 2, 3, 4, 0, 0}; transform.transform(values, 0, values, 2, 2);
            near(values[2], 13, 1e-9); near(values[3], 15, 1e-9); near(values[4], 23, 1e-9); near(values[5], 25, 1e-9);
        });
        check("path-triangle-containment", () -> { require(triangle().contains(2, 2), "triangle interior"); require(!triangle().contains(9, 9), "bounds-only containment"); require(!triangle().intersects(8, 8, 1, 1), "bounds-only intersection"); });
        check("path-nonzero-and-evenodd", () -> {
            Path2D path = new Path2D.Double(); path.append(new Rectangle2D.Double(0, 0, 20, 20), false); path.append(new Rectangle2D.Double(5, 5, 10, 10), false);
            require(path.contains(8, 8), "nonzero hole"); path.setWindingRule(Path2D.WIND_EVEN_ODD); require(!path.contains(8, 8), "evenodd missing hole");
        });
        check("path-curved-region-and-flattening", () -> {
            GeneralPath path = new GeneralPath(); path.moveTo(0, 0); path.quadTo(10, 20, 20, 0); path.closePath();
            require(path.contains(10, 5), "curve treated as chord"); require(!path.contains(10, 15), "curve bounds treated as interior");
            PathIterator iterator = path.getPathIterator(null, .1); float[] coords = new float[6]; int segments = 0;
            while (!iterator.isDone()) { int type = iterator.currentSegment(coords); require(type != PathIterator.SEG_QUADTO && type != PathIterator.SEG_CUBICTO, "unflattened curve"); segments++; iterator.next(); }
            require(segments > 4, "curve not subdivided"); require(path instanceof Path2D, "GeneralPath hierarchy");
        });
        check("path-append-connect", () -> {
            GeneralPath path = new GeneralPath(); path.moveTo(0, 0); path.lineTo(1, 1); GeneralPath next = new GeneralPath(); next.moveTo(5, 5); next.lineTo(6, 6); path.append(next, true);
            PathIterator iterator = path.getPathIterator(null); iterator.next(); iterator.next(); require(iterator.currentSegment(new double[6]) == PathIterator.SEG_LINETO, "append started a new contour");
        });
        check("ellipse-and-arc-intersections", () -> { require(!new Ellipse2D.Double(0, 0, 10, 10).intersects(0, 0, 1, 1), "ellipse bbox"); require(new Arc2D.Double(0, 0, 10, 10, 0, 90, Arc2D.PIE).contains(7, 3), "arc sector"); });
        check("affine-op-bounds-points-interpolation", () -> {
            AffineTransformOp op = new AffineTransformOp(AffineTransform.getScaleInstance(2, 3), new RenderingHints(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC));
            BufferedImage src = image(4, 5); near(op.getBounds2D(src).getWidth(), 8, 0); near(op.getBounds2D(src).getHeight(), 15, 0); near(op.getPoint2D(new Point2D.Double(2, 3), null).getY(), 9, 0);
            require(op.createCompatibleDestImage(src, null).getWidth() == 8, "destination dimensions"); require(op.getInterpolationType() == AffineTransformOp.TYPE_BICUBIC, "interpolation dropped");
        });
        check("affine-op-pixels", () -> { BufferedImage src = image(2, 1); src.setRGB(0, 0, Color.RED.getRGB()); src.setRGB(1, 0, Color.BLUE.getRGB()); BufferedImage out = new AffineTransformOp(AffineTransform.getScaleInstance(2, 2), 1).filter(src, null); color(out.getRGB(0, 0), Color.RED.getRGB(), 0); color(out.getRGB(3, 1), Color.BLUE.getRGB(), 0); });
        check("pixel-grabber-allocation", () -> { BufferedImage src = image(2, 2); src.setRGB(1, 1, 0xff123456); PixelGrabber grabber = new PixelGrabber(src, 0, 0, -1, -1, true); require(grabber.grabPixels(), "grab failed"); require(grabber.getColorModel() != null, "missing model"); require(((int[])grabber.getPixels())[3] == 0xff123456, "missing pixels"); require((grabber.getStatus() & (ImageObserver.ALLBITS | ImageObserver.FRAMEBITS)) != 0, "missing status"); });
        check("pixel-grabber-failure", () -> { ImageProducer producer = new ImageProducer() { public void addConsumer(ImageConsumer c) {} public boolean isConsumer(ImageConsumer c) { return false; } public void removeConsumer(ImageConsumer c) {} public void startProduction(ImageConsumer c) { c.imageComplete(ImageConsumer.IMAGEERROR); } public void requestTopDownLeftRightResend(ImageConsumer c) {} }; PixelGrabber grabber = new PixelGrabber(producer, 0, 0, 1, 1, new int[1], 0, 1); require(!grabber.grabPixels(), "failure reported success"); require((grabber.getStatus() & ImageObserver.ERROR) != 0, "missing error"); });
        check("filtered-memory-producer", () -> {
            MemoryImageSource source = new MemoryImageSource(2, 1, new int[]{0xffff0000, 0xff00ff00}, 0, 2);
            FilteredImageSource filtered = new FilteredImageSource(source, new RGBImageFilter() { public int filterRGB(int x, int y, int rgb) { return rgb ^ 0x00ffffff; } });
            PixelGrabber grabber = new PixelGrabber(filtered, 0, 0, 2, 1, new int[2], 0, 2);
            require(grabber.grabPixels(), "filtered producer failed");
            color(((int[])grabber.getPixels())[0], 0xff00ffff, 0); color(((int[])grabber.getPixels())[1], 0xffff00ff, 0);
        });
        check("linear-rgb-and-gray", () -> { ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY), linear = ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB); float[] rgb = gray.toRGB(new float[]{.5f}); require(rgb.length == 3, "gray component count"); near(rgb[0], .735, .01); require(!linear.isCS_sRGB(), "linear aliases sRGB"); near(linear.toRGB(new float[]{.5f, .5f, .5f})[0], .735, .01); });
        check("xyz-roundtrip", () -> { ColorSpace rgb = ColorSpace.getInstance(ColorSpace.CS_sRGB); float[] back = rgb.fromCIEXYZ(rgb.toCIEXYZ(new float[]{.2f, .4f, .7f})); near(back[0], .2, .003); near(back[1], .4, .003); near(back[2], .7, .003); });
        for (int kind = 1; kind <= 13; kind++) { final int type = kind; check("image-type-" + type, () -> {
            BufferedImage src = new BufferedImage(4, 4, type); src.setRGB(1, 1, 0xffff0000);
            if (type <= 7 || type == 8 || type == 9 || type == 13) color(src.getRGB(1, 1), 0xffff0000, 8);
            else if (type == 10 || type == 11) { int value = src.getRGB(1, 1); require((value >>> 16 & 255) == (value >>> 8 & 255) && (value >>> 8 & 255) == (value & 255), "gray image is colored"); }
            require(src.getRaster().getTransferType() == (type <= 4 ? DataBuffer.TYPE_INT : type == 8 || type == 9 || type == 11 ? DataBuffer.TYPE_USHORT : DataBuffer.TYPE_BYTE), "wrong backing type");
            BufferedImage child = src.getSubimage(1, 1, 2, 2); child.setRGB(0, 0, Color.WHITE.getRGB()); color(src.getRGB(1, 1), Color.WHITE.getRGB(), 0);
        }); }
        check("premultiplied-storage", () -> { BufferedImage src = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE); src.setRGB(0, 0, 0x80804020); int stored = ((DataBufferInt)src.getRaster().getDataBuffer()).getData()[0]; color(stored, 0x80402010, 1); color(src.getRGB(0, 0), 0x80804020, 1); });
        check("custom-raster-offset-stride-properties", () -> {
            ColorModel cm = ColorModel.getRGBdefault(); WritableRaster raster = Raster.createWritableRaster(new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, 3, 3, 5, new int[]{0xff0000, 0xff00, 0xff, 0xff000000}), new DataBufferInt(new int[30], 20, 2), new Point());
            Hashtable<String,String> props = new Hashtable<>(); props.put("sample", "kept"); BufferedImage src = new BufferedImage(cm, raster, false, props); src.setRGB(1, 2, 0xff123456); require(((DataBufferInt)raster.getDataBuffer()).getData()[13] == 0xff123456, "bad raster offset"); require("kept".equals(src.getProperty("sample")), "lost property");
        });
        check("coerce-alpha", () -> {
            BufferedImage src = image(2, 2); src.setRGB(0, 0, 0x80804020); src.coerceData(true);
            require(src.isAlphaPremultiplied(), "coercion not recorded"); color(src.getRGB(0, 0), 0x80804020, 1);
            color(((DataBufferInt)src.getRaster().getDataBuffer()).getData()[0], 0x80402010, 1);
            src.coerceData(false); color(((DataBufferInt)src.getRaster().getDataBuffer()).getData()[0], 0x80804020, 1);
        });
        check("producer-image-drawing", () -> {
            Image image = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(2, 1, new int[]{0xffff0000, 0xff00ff00}, 0, 2));
            BufferedImage dst = image(4, 3); Graphics2D g = dst.createGraphics();
            // A synchronous producer is immediately drawable; desktop Toolkit is asynchronous.
            PixelGrabber load = new PixelGrabber(image, 0, 0, -1, -1, true); require(load.grabPixels(), "producer load failed");
            g.drawImage(image, 1, 1, null); g.dispose(); color(dst.getRGB(1, 1), 0xffff0000, 0); color(dst.getRGB(2, 1), 0xff00ff00, 0);
        });
        check("rescale-subimage", () -> { BufferedImage parent = image(4, 4); parent.setRGB(1, 1, 0xff204060); BufferedImage src = parent.getSubimage(1, 1, 2, 2); BufferedImage out = new RescaleOp(2, 0, null).filter(src, null); color(out.getRGB(0, 0), 0xff4080c0, 0); require(parent.getRGB(1, 1) == 0xff204060, "modified source"); });
        check("canonical-custom-fast-path", () -> {
            ColorModel cm = ColorModel.getRGBdefault();
            BufferedImage custom = new BufferedImage(cm, cm.createCompatibleWritableRaster(4, 4), false, null);
            try {
                java.lang.reflect.Method method = BufferedImage.class.getMethod("isCanonicalBuffer");
                require(Boolean.TRUE.equals(method.invoke(custom)), "canonical custom buffer lost native path");
                require(Boolean.FALSE.equals(method.invoke(custom.getSubimage(1, 1, 2, 2))), "subimage incorrectly uses root native layout");
            } catch (NoSuchMethodException desktop) { /* Optimization is Android-specific. */ }
        });
        check("opaque-rgb-image-drawing", () -> {
            BufferedImage src = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB); src.setRGB(0, 0, Color.RED.getRGB());
            BufferedImage dst = image(3, 2); Graphics2D g = dst.createGraphics(); g.drawImage(src, 0, 0, null); g.dispose();
            color(dst.getRGB(0, 0), Color.RED.getRGB(), 0); color(dst.getRGB(1, 0), Color.BLACK.getRGB(), 0);
        });
        check("opaque-rgb-global-alpha", () -> {
            BufferedImage src = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB); src.setRGB(0, 0, Color.RED.getRGB());
            BufferedImage dst = image(2, 1); dst.setRGB(0, 0, Color.BLUE.getRGB()); Graphics2D g = dst.createGraphics();
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f)); g.drawImage(src, 0, 0, null);
            color(dst.getRGB(0, 0), 0xff80007f, 1);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, .5f)); g.drawImage(src, 1, 0, null); g.dispose();
            color(dst.getRGB(1, 0), 0x80ff0000, 1);
        });
        check("bulk-rgb-shared-stride", () -> {
            BufferedImage parent = image(5, 4), child = parent.getSubimage(1, 1, 3, 2);
            int[] pixels = {0xff123456, 0xff223344, 0xff445566, 0xff102030, 0xff506070, 0xff8090a0};
            child.setRGB(0, 0, 3, 2, pixels, 0, 3);
            require(Arrays.equals(child.getRGB(0, 0, 3, 2, null, 0, 3), pixels), "bulk child mismatch");
            color(parent.getRGB(1, 2), pixels[3], 0); require(parent.getRGB(0, 0) == 0, "bulk write escaped child");
            int[] reversed = child.getRGB(0, 0, 3, 2, new int[6], 3, -3);
            color(reversed[0], pixels[3], 0); color(reversed[5], pixels[2], 0);
        });
        check("system-color-hierarchy", () -> { Color tooltip = SystemColor.info; require(tooltip.getAlpha() == 255 && SystemColor.NUM_COLORS == 26, "system palette unavailable"); });
        check("graphics-native-solid", () -> { BufferedImage src = image(20, 20); Graphics2D g = src.createGraphics(); g.setColor(Color.RED); g.fillRect(2, 3, 4, 5); g.dispose(); color(src.getRGB(3, 4), Color.RED.getRGB(), 0); require(src.getRGB(0, 0) == 0, "escaped rectangle"); });
        check("graphics-shared-child", () -> { BufferedImage parent = image(20, 20), child = parent.getSubimage(3, 4, 8, 8); Graphics2D g = child.createGraphics(); g.setColor(Color.BLUE); g.fillRect(0, 0, 8, 8); g.dispose(); color(parent.getRGB(3, 4), Color.BLUE.getRGB(), 0); require(parent.getRGB(2, 4) == 0, "escaped subimage"); });
        check("graphics-scaled-shape", () -> { BufferedImage src = image(20, 20); Graphics2D g = src.createGraphics(); g.scale(2, 3); g.setColor(Color.RED); g.fillRect(1, 1, 2, 2); g.dispose(); color(src.getRGB(3, 4), Color.RED.getRGB(), 0); require(src.getRGB(1, 1) == 0, "transform ignored"); });
        check("graphics-rotated-image", () -> { BufferedImage src = image(2, 1); src.setRGB(0, 0, Color.RED.getRGB()); src.setRGB(1, 0, Color.BLUE.getRGB()); BufferedImage dst = image(10, 10); Graphics2D g = dst.createGraphics(); AffineTransform transform = new AffineTransform(); transform.translate(4, 2); transform.rotate(Math.PI / 2); g.drawImage(src, transform, null); g.dispose(); color(dst.getRGB(3, 2), Color.RED.getRGB(), 0); color(dst.getRGB(3, 3), Color.BLUE.getRGB(), 0); });
        check("graphics-mirrored-image", () -> {
            BufferedImage src = image(2, 1); src.setRGB(0, 0, Color.RED.getRGB()); src.setRGB(1, 0, Color.BLUE.getRGB());
            BufferedImage dst = image(4, 2); Graphics2D g = dst.createGraphics(); g.drawImage(src, 2, 0, 0, 1, 0, 0, 2, 1, null); g.dispose();
            color(dst.getRGB(0, 0), Color.BLUE.getRGB(), 0); color(dst.getRGB(1, 0), Color.RED.getRGB(), 0);
        });
        check("graphics-image-background", () -> { BufferedImage src = image(2, 2), dst = image(3, 3); Graphics2D g = dst.createGraphics(); g.setComposite(AlphaComposite.Src); g.drawImage(src, 0, 0, Color.GREEN, null); g.dispose(); color(dst.getRGB(0, 0), Color.GREEN.getRGB(), 0); });
        check("graphics-gradient", () -> { BufferedImage src = image(20, 4); Graphics2D g = src.createGraphics(); g.setPaint(new GradientPaint(0, 0, Color.RED, 20, 0, Color.BLUE)); g.fillRect(0, 0, 20, 4); g.dispose(); require((src.getRGB(1, 1) >>> 16 & 255) > 200 && (src.getRGB(18, 1) & 255) > 200, "gradient not rendered"); });
        check("graphics-linear-radial-gradients", () -> { BufferedImage src = image(20, 20); Graphics2D g = src.createGraphics(); g.setPaint(new LinearGradientPaint(0, 0, 20, 0, new float[]{0, .5f, 1}, new Color[]{Color.RED, Color.GREEN, Color.BLUE})); g.fillRect(0, 0, 20, 20); require((src.getRGB(10, 1) >>> 8 & 255) > 200, "middle gradient stop missing"); g.setPaint(new RadialGradientPaint(10, 10, 10, new float[]{0, 1}, new Color[]{Color.WHITE, Color.BLACK})); g.fillRect(0, 0, 20, 20); require((src.getRGB(10, 10) & 255) > (src.getRGB(0, 0) & 255), "radial gradient missing"); g.dispose(); });
        check("graphics-xor-restores", () -> { BufferedImage src = image(8, 8); Graphics2D g = src.createGraphics(); g.setColor(Color.BLUE); g.fillRect(0, 0, 8, 8); int before = src.getRGB(2, 2); g.setXORMode(Color.WHITE); g.setColor(Color.RED); g.fillRect(1, 1, 4, 4); require(src.getRGB(2, 2) != before, "xor ignored"); g.fillRect(1, 1, 4, 4); color(src.getRGB(2, 2), before, 0); g.dispose(); });
        check("graphics-custom-composite", () -> { final boolean[] called = {false}; BufferedImage src = image(8, 8); Graphics2D g = src.createGraphics(); g.setComposite((sourceCM, destinationCM, hints) -> new CompositeContext() { public void dispose() {} public void compose(Raster input, Raster destination, WritableRaster output) { called[0] = true; for (int y = 0; y < output.getHeight(); y++) for (int x = 0; x < output.getWidth(); x++) output.setDataElements(x + output.getMinX(), y + output.getMinY(), destinationCM.getDataElements(Color.MAGENTA.getRGB(), null)); } }); g.fillRect(2, 2, 3, 3); g.dispose(); require(called[0], "custom composite ignored"); color(src.getRGB(3, 3), Color.MAGENTA.getRGB(), 0); require(src.getRGB(0, 0) == 0, "composite escaped geometry"); });
        check("graphics-attributed-text", () -> { BufferedImage src = image(100, 30); Graphics2D g = src.createGraphics(); AttributedString text = new AttributedString("Test"); text.addAttribute(TextAttribute.FONT, new Font("Dialog", Font.PLAIN, 18)); text.addAttribute(TextAttribute.FOREGROUND, Color.RED); g.drawString(text.getIterator(), 2, 22); g.dispose(); require(nonempty(src) > 20, "attributed text missing"); });
        check("graphics-transform-clip", () -> { BufferedImage src = image(20, 20); Graphics2D g = src.createGraphics(); g.scale(2, 2); g.setClip(1, 2, 3, 4); Rectangle clip = g.getClipBounds(); require(clip.equals(new Rectangle(1, 2, 3, 4)), "clip not in user space: " + clip); g.setColor(Color.RED); g.fillRect(0, 0, 20, 20); color(src.getRGB(3, 5), Color.RED.getRGB(), 0); require(src.getRGB(0, 0) == 0, "clip ignored"); g.dispose(); });
        check("compatible-image", () -> { Graphics2D g = image(4, 4).createGraphics(); GraphicsConfiguration configuration = g.getDeviceConfiguration(); require(configuration.getColorModel() != null, "missing color model"); BufferedImage src = configuration.createCompatibleImage(3, 4, Transparency.TRANSLUCENT); require(src.getWidth() == 3 && src.getColorModel().hasAlpha(), "compatible image contract"); g.dispose(); });
        check("imageio-roundtrip-and-unsupported", () -> { BufferedImage src = image(2, 2); src.setRGB(0, 0, 0xff123456); ByteArrayOutputStream output = new ByteArrayOutputStream(); require(!ImageIO.write(src, "no-such-format", output), "unsupported format accepted"); require(output.size() == 0, "wrote unsupported format"); require(ImageIO.write(src, "png", output), "png rejected"); color(ImageIO.read(new ByteArrayInputStream(output.toByteArray())).getRGB(0, 0), 0xff123456, 0); require(ImageIO.read(new ByteArrayInputStream(new byte[]{1, 2, 3})) == null, "unknown decoder result"); });
        check("font-size-and-context", () -> { Font font = new Font("Dialog", Font.PLAIN, 12).deriveFont(12.75f); near(font.getSize2D(), 12.75, 0); require(TextAttribute.KERNING_ON instanceof Integer && TextAttribute.SUPERSCRIPT_SUPER instanceof Integer, "TextAttribute descriptor"); Graphics2D g = image(10, 10).createGraphics(); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF); g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON); FontRenderContext context = g.getFontRenderContext(); require(!context.isAntiAliased() && context.usesFractionalMetrics(), "context ignores hints"); require(font.getLineMetrics("Test", context).getHeight() > 0, "line metrics missing"); g.dispose(); });
        check("font-layout-attributes", () -> {
            Font font = new Font("Dialog", Font.PLAIN, 18);
            Font tracked = font.deriveFont(Collections.singletonMap(TextAttribute.TRACKING, .2f));
            FontRenderContext context = new FontRenderContext(null, true, true);
            require(tracked.getStringBounds("Testing", context).getWidth() > font.getStringBounds("Testing", context).getWidth(), "tracking discarded");
            Font scaled = font.deriveFont(AffineTransform.getScaleInstance(2, 1));
            require(scaled.isTransformed(), "font transform lost"); near(scaled.getStringBounds("Test", context).getWidth(), font.getStringBounds("Test", context).getWidth() * 2, 1);
        });
        check("fallback-gradient-global-coordinates", () -> {
            BufferedImage full = image(180, 150), clipped = image(180, 150);
            for (BufferedImage target : new BufferedImage[]{full, clipped}) {
                Graphics2D g = target.createGraphics();
                if (target == clipped) g.setClip(73, 49, 27, 31);
                g.translate(31, 19); g.rotate(.12);
                g.setPaint(new LinearGradientPaint(0, 0, 140, 80, new float[]{0, .4f, 1}, new Color[]{Color.RED, Color.GREEN, Color.BLUE}));
                g.fillRect(0, 0, 130, 100); g.dispose();
            }
            for (int y=49;y<80;y++) for (int x=73;x<100;x++) color(clipped.getRGB(x,y),full.getRGB(x,y),1);
            require(clipped.getRGB(72,49)==0,"drawing escaped clip");
        });
        check("fallback-reused-buffer-clear-and-xor", () -> {
            BufferedImage target=image(160,120); Graphics2D g=target.createGraphics();
            g.setColor(Color.BLUE);g.fillRect(0,0,160,120);
            g.setXORMode(Color.WHITE);g.setColor(Color.RED);
            g.fillOval(30,20,15,11);g.fillOval(30,20,15,11);
            color(target.getRGB(37,25),Color.BLUE.getRGB(),0);
            g.setPaintMode();g.setComposite(AlphaComposite.Clear);g.rotate(.01,80,60);
            g.fillRect(75,55,10,10);g.dispose();
            require(target.getRGB(80,60)==0,"clear did not clear");color(target.getRGB(20,20),Color.BLUE.getRGB(),0);
        });
        check("fallback-cropped-flipped-image", () -> {
            BufferedImage source=image(80,60);Graphics2D sg=source.createGraphics();
            sg.setColor(Color.RED);sg.fillRect(0,0,40,60);sg.setColor(Color.GREEN);sg.fillRect(40,0,40,60);sg.dispose();
            BufferedImage target=image(160,100);Graphics2D g=target.createGraphics();g.translate(10,5);
            g.drawImage(source,100,10,20,50,20,10,60,30,null);g.dispose();
            color(target.getRGB(40,30),Color.GREEN.getRGB(),0);color(target.getRGB(100,30),Color.RED.getRGB(),0);
            require(target.getRGB(15,30)==0,"image escaped destination");
        });
        check("fallback-text-clip-and-overhang", () -> {
            BufferedImage full=image(220,100),clipped=image(220,100);
            for(BufferedImage target:new BufferedImage[]{full,clipped}){
                Graphics2D g=target.createGraphics();if(target==clipped)g.setClip(65,20,60,50);
                g.translate(33,12);g.rotate(.08);g.setFont(new Font("Dialog",Font.ITALIC,26).deriveFont(new AffineTransform(1,0,.2,1,0,0)));
                g.setColor(Color.WHITE);g.drawString("Testing fj",3.5f,40.5f);g.dispose();
            }
            for(int y=20;y<70;y++)for(int x=65;x<125;x++)color(clipped.getRGB(x,y),full.getRGB(x,y),1);
            require(nonempty(clipped)>20,"text disappeared");
        });
        check("fallback-dirty-region-work-budget", () -> {
            CountingImage target=new CountingImage(1024,768);Graphics2D g=target.createGraphics();
            g.setPaint(new GradientPaint(0,0,Color.RED,1024,768,Color.BLUE));
            long started=System.nanoTime();for(int i=0;i<40;i++)g.fillRect(700+i%3,500,20,16);g.dispose();
            System.out.println("FALLBACK small-gradient readPixels="+target.readPixels+" writtenPixels="+target.writtenPixels+" ms="+(System.nanoTime()-started)/1000000);
            require(target.readPixels<40*2000L&&target.writtenPixels<40*2000L,"small drawing copied the full target");
            require(target.readBuffers.size()<=1&&target.writeBuffers.size()<=1,"repeated draws allocated pixel arrays");
            require((target.getRGB(710,508)>>>24)==255,"gradient disappeared");
            require(target.getRGB(0,0)==0,"drawing escaped dirty region");
        });
        check("fallback-source-crop-work-budget", () -> {
            CountingImage source=new CountingImage(1024,768);source.setRGB(500,400,Color.RED.getRGB());
            BufferedImage target=image(160,100);Graphics2D g=target.createGraphics();g.rotate(.01);
            for(int i=0;i<10;i++)g.drawImage(source,40,30,60,50,500,400,510,410,null);g.dispose();
            require(source.readPixels<=1440,"image crop read the entire source");
            require(source.readBuffers.size()<=1,"image draws allocated source arrays");
        });
        check("fallback-image-does-not-sample-paint", () -> {
            BufferedImage source=image(8,8);source.setRGB(3,3,Color.RED.getRGB());
            BufferedImage target=image(80,60);Graphics2D g=target.createGraphics();
            g.setPaint(new Paint(){
                public int getTransparency(){return Transparency.OPAQUE;}
                public PaintContext createContext(ColorModel cm,Rectangle device,Rectangle2D user,AffineTransform transform,RenderingHints hints){throw new AssertionError("image sampled unrelated paint");}
            });
            g.drawImage(source,AffineTransform.getTranslateInstance(31,22),null);g.dispose();
            color(target.getRGB(34,25),Color.RED.getRGB(),0);
        });
        check("fallback-copy-area-overlap-and-work-budget", () -> {
            CountingImage target=new CountingImage(512,384,BufferedImage.TYPE_4BYTE_ABGR);
            for(int x=200;x<220;x++)for(int y=150;y<166;y++)target.setRGB(x,y,0xff000000|(x<<8)|y);
            int[] before=target.getRGB(200,150,20,16,null,0,20);target.readPixels=0;
            Graphics2D g=target.createGraphics();g.setClip(206,153,12,12);g.copyArea(200,150,20,16,5,3);g.dispose();
            require(target.readPixels<2000,"copyArea copied the whole image");
            for(int y=153;y<165;y++)for(int x=206;x<218;x++)color(target.getRGB(x,y),before[(y-153)*20+x-205],0);
            color(target.getRGB(200,150),before[0],0);
        });
        check("fallback-reentrant-custom-paint", () -> {
            BufferedImage outer=image(150,100),nested=image(120,80);Graphics2D g=outer.createGraphics();
            Paint delegate=new GradientPaint(0,0,Color.RED,150,0,Color.BLUE);
            g.setPaint(new Paint(){
                public int getTransparency(){return Transparency.OPAQUE;}
                public PaintContext createContext(ColorModel cm,Rectangle device,Rectangle2D user,AffineTransform transform,RenderingHints hints){
                    Graphics2D ng=nested.createGraphics();ng.setPaint(delegate);ng.fillRect(80,50,20,15);ng.dispose();
                    return delegate.createContext(cm,device,user,transform,hints);
                }
            });g.fillRect(30,20,12,10);g.dispose();
            require((outer.getRGB(35,25)>>>24)==255,"outer drawing corrupted");require((nested.getRGB(85,55)>>>24)==255,"nested drawing corrupted");
            require(outer.getRGB(85,55)==0,"nested pixels leaked into outer");
        });
        if (args.length > 0) { long started = System.nanoTime(); writeGallery(new File(args[0])); System.out.println("GALLERY ms=" + (System.nanoTime() - started) / 1000000); }
        System.out.println("GRAPHICS RESULT passed=" + passed + " failed=" + failed);
        if (failed != 0) throw new AssertionError(failed + " graphics contracts failed");
    }
    private static final class CountingImage extends BufferedImage {
        long readPixels,writtenPixels;
        final Set<int[]> readBuffers=Collections.newSetFromMap(new IdentityHashMap<int[],Boolean>());
        final Set<int[]> writeBuffers=Collections.newSetFromMap(new IdentityHashMap<int[],Boolean>());
        CountingImage(int w,int h){super(w,h,BufferedImage.TYPE_INT_ARGB);}
        CountingImage(int w,int h,int type){super(w,h,type);}
        @Override public int[] getRGB(int x,int y,int w,int h,int[] data,int offset,int stride){readPixels+=(long)w*h;if(data!=null)readBuffers.add(data);return super.getRGB(x,y,w,h,data,offset,stride);}
        @Override public void setRGB(int x,int y,int w,int h,int[] data,int offset,int stride){writtenPixels+=(long)w*h;writeBuffers.add(data);super.setRGB(x,y,w,h,data,offset,stride);}
    }
    private static void writeGallery(File output) throws IOException {
        BufferedImage canvas = image(480, 320);
        Graphics2D g = canvas.createGraphics(); g.setColor(new Color(25, 29, 36)); g.fillRect(0, 0, 480, 320);
        g.setFont(new Font("Dialog", Font.BOLD, 18)); g.setColor(Color.WHITE); g.drawString("Graphics compatibility probe", 16, 26);
        g.setPaint(new LinearGradientPaint(20, 50, 210, 50, new float[]{0, .5f, 1}, new Color[]{Color.RED, Color.GREEN, Color.BLUE})); g.fillRoundRect(20, 50, 190, 80, 20, 20);
        g.setPaint(new RadialGradientPaint(330, 90, 75, new float[]{0, 1}, new Color[]{Color.WHITE, Color.BLUE})); g.fillOval(250, 45, 160, 90);
        g.setPaint(Color.WHITE); g.setFont(new Font("Dialog", Font.PLAIN, 16)); g.drawString("Linear gradient", 20, 150); g.drawString("Radial gradient", 250, 150);
        Graphics2D rotated = (Graphics2D)g.create(); rotated.translate(100, 230); rotated.rotate(-.3); rotated.setPaint(new Color(255, 190, 60)); rotated.fillRect(-55, -35, 110, 70); rotated.setColor(Color.BLACK); rotated.drawString("Rotated text", -45, 5); rotated.dispose();
        AttributedString text = new AttributedString("Styled text + shared raster"); text.addAttribute(TextAttribute.FONT, new Font("Dialog", Font.BOLD, 16)); text.addAttribute(TextAttribute.FOREGROUND, Color.CYAN, 0, 11); text.addAttribute(TextAttribute.FOREGROUND, Color.WHITE, 11, 26); g.drawString(text.getIterator(), 220, 205);
        BufferedImage child = canvas.getSubimage(230, 230, 180, 55); Graphics2D cg = child.createGraphics(); cg.setColor(new Color(70, 185, 125)); cg.fillRect(0, 0, 180, 55); cg.setColor(Color.BLACK); cg.drawString("Shared child image", 15, 32); cg.dispose(); g.dispose();
        ImageIO.write(canvas, "png", output);
    }
}
