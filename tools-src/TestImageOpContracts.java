// host-contract
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ByteLookupTable;
import java.awt.image.ConvolveOp;
import java.awt.image.ImagingOpException;
import java.awt.image.Kernel;
import java.awt.image.LookupOp;
import java.awt.image.Raster;
import java.awt.image.RescaleOp;
import java.awt.image.ShortLookupTable;
import java.awt.image.WritableRaster;

/**
 * java.awt.image op contracts: ConvolveOp, LookupOp and RescaleOp output pixels on a
 * fixture image, asserted against an independent model of what desktop produces. The
 * same assertions run on the real JDK (libmlib_image) and on the shadow, so the model
 * can only encode what both do.
 */
public final class TestImageOpContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static void equalImages(String name, BufferedImage expected, BufferedImage actual, int tolerance) {
        int w = expected.getWidth(), h = expected.getHeight();
        if (actual.getWidth() != w || actual.getHeight() != h) {
            check(name + " size", false);
            return;
        }
        int bad = 0;
        String first = null;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int e = expected.getRGB(x, y), a = actual.getRGB(x, y);
            boolean same = true;
            for (int shift = 0; shift <= 24; shift += 8) {
                if (Math.abs((e >>> shift & 255) - (a >>> shift & 255)) > tolerance) same = false;
            }
            if (!same) {
                bad++;
                if (first == null) first = String.format("(%d,%d) expected %08x got %08x", x, y, e, a);
            }
        }
        check(name + (bad == 0 ? "" : " [" + bad + " pixels differ, first " + first + "]"), bad == 0);
    }

    /** Deterministic fixture: colour gradients, a fully transparent band with colour in it and a hard edge. */
    private static BufferedImage fixture(int type) {
        int w = 23, h = 17;
        BufferedImage image = new BufferedImage(w, h, type);
        long seed = 0x9E3779B97F4A7C15L;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int noise = (int) (seed >>> 59);
            int r = Math.min(255, x * 11 + noise), g = Math.min(255, y * 15 + noise), b = (x * y * 7 + noise) & 255;
            int a = y < 4 ? 0 : x > 18 ? 128 + noise : 255;
            if (x >= 10 && x <= 12 && y >= 8 && y <= 10) { r = 255; g = 255; b = 255; }
            image.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
        }
        return image;
    }

    private static int clampByte(double v) { return v <= 0 ? 0 : v >= 255 ? 255 : (int) v; }

    /** Desktop convolves every raw band (alpha included, nothing premultiplied), kernel rotated 180 degrees, result truncated. */
    private static BufferedImage modelConvolve(BufferedImage src, float[] kernel, int kw, int kh, int edge) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, src.getType());
        int xo = (kw - 1) >> 1, yo = (kh - 1) >> 1;
        int left = kw % 2 == 0 ? kw / 2 : xo, right = kw % 2 == 0 ? kw / 2 : xo;
        int top = kh % 2 == 0 ? kh / 2 : yo, bottom = kh % 2 == 0 ? kh / 2 : yo;
        boolean hasAlpha = src.getColorModel().hasAlpha();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean interior = x >= left && x < w - right && y >= top && y < h - bottom;
            if (!interior) {
                out.setRGB(x, y, edge == ConvolveOp.EDGE_NO_OP ? src.getRGB(x, y) : (hasAlpha ? 0 : 0xff000000));
                continue;
            }
            double[] sum = new double[4];
            for (int j = 0; j < kh; j++) for (int i = 0; i < kw; i++) {
                float k = kernel[j * kw + i];
                if (k == 0) continue;
                int p = src.getRGB(x - (i - xo), y - (j - yo));
                for (int c = 0; c < 4; c++) sum[c] += k * (p >>> (c * 8) & 255);
            }
            int a = hasAlpha ? clampByte(Math.floor(sum[3])) : 255;
            out.setRGB(x, y, a << 24 | clampByte(Math.floor(sum[2])) << 16 | clampByte(Math.floor(sum[1])) << 8 | clampByte(Math.floor(sum[0])));
        }
        return out;
    }

    private static BufferedImage modelRescale(BufferedImage src, float[] scales, float[] offsets) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, src.getType());
        boolean hasAlpha = src.getColorModel().hasAlpha();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = src.getRGB(x, y);
            int[] c = { p >>> 16 & 255, p >>> 8 & 255, p & 255, p >>> 24 };
            int bands = hasAlpha ? 4 : 3;
            for (int b = 0; b < bands; b++) {
                if (b == 3 && scales.length < 4) continue;
                int index = scales.length == 1 ? 0 : b;
                int v = (int) (c[b] * scales[index] + offsets[index]);
                c[b] = v < 0 ? 0 : v > 255 ? 255 : v;
            }
            out.setRGB(x, y, (hasAlpha ? c[3] : 255) << 24 | c[0] << 16 | c[1] << 8 | c[2]);
        }
        return out;
    }

    private static BufferedImage modelLookup(BufferedImage src, byte[][] tables) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, src.getType());
        boolean hasAlpha = src.getColorModel().hasAlpha();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = src.getRGB(x, y);
            int[] c = { p >>> 16 & 255, p >>> 8 & 255, p & 255, p >>> 24 };
            for (int b = 0; b < 3; b++) c[b] = tables[tables.length == 1 ? 0 : b][c[b]] & 255;
            if (tables.length == 4 && hasAlpha) c[3] = tables[3][c[3]] & 255;
            out.setRGB(x, y, (hasAlpha ? c[3] : 255) << 24 | c[0] << 16 | c[1] << 8 | c[2]);
        }
        return out;
    }

    private static void convolve() {
        float[] box = new float[9];
        java.util.Arrays.fill(box, 1f / 9f);
        float[] shiftRight = { 0, 0, 0, 0, 0, 1, 0, 0, 0 };
        float[] sharpen = { 0, -1, 0, -1, 5, -1, 0, -1, 0 };
        float[] gauss5 = new float[25];
        int[] binom = { 1, 4, 6, 4, 1 };
        for (int j = 0; j < 5; j++) for (int i = 0; i < 5; i++) gauss5[j * 5 + i] = binom[i] * binom[j] / 256f;
        float[] even = { 0.25f, 0.25f, 0.25f, 0.25f };
        int[] types = { BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_RGB };
        for (int type : types) {
            String t = type == BufferedImage.TYPE_INT_ARGB ? "ARGB" : "RGB";
            BufferedImage src = fixture(type);
            for (int edge : new int[] { ConvolveOp.EDGE_ZERO_FILL, ConvolveOp.EDGE_NO_OP }) {
                String e = edge == ConvolveOp.EDGE_NO_OP ? "no-op" : "zero";
                // Integer kernels are exact on both sides; the fractional ones may land one
                // step off where medialib's fixed-point coefficients cross an integer.
                equalImages("convolve shift " + t + " " + e,
                    modelConvolve(src, shiftRight, 3, 3, edge),
                    new ConvolveOp(new Kernel(3, 3, shiftRight), edge, null).filter(src, null), 0);
                equalImages("convolve sharpen " + t + " " + e,
                    modelConvolve(src, sharpen, 3, 3, edge),
                    new ConvolveOp(new Kernel(3, 3, sharpen), edge, null).filter(src, null), 0);
                equalImages("convolve 3x3 box " + t + " " + e,
                    modelConvolve(src, box, 3, 3, edge),
                    new ConvolveOp(new Kernel(3, 3, box), edge, null).filter(src, null), 1);
                equalImages("convolve 5x5 gaussian " + t + " " + e,
                    modelConvolve(src, gauss5, 5, 5, edge),
                    new ConvolveOp(new Kernel(5, 5, gauss5), edge, null).filter(src, null), 1);
                equalImages("convolve 2x2 even kernel " + t + " " + e,
                    modelConvolve(src, even, 2, 2, edge),
                    new ConvolveOp(new Kernel(2, 2, even), edge, null).filter(src, null), 1);
            }
            // A caller-supplied destination of the same type is filled, not replaced.
            BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), type);
            BufferedImage returned = new ConvolveOp(new Kernel(3, 3, box)).filter(src, dst);
            check("convolve returns the given destination " + t, returned == dst);
            equalImages("convolve into given destination " + t, modelConvolve(src, box, 3, 3, ConvolveOp.EDGE_ZERO_FILL), dst, 1);
        }
        BufferedImage src = fixture(BufferedImage.TYPE_INT_ARGB);
        ConvolveOp op = new ConvolveOp(new Kernel(3, 3, box), ConvolveOp.EDGE_NO_OP, null);
        check("convolve edge condition", op.getEdgeCondition() == ConvolveOp.EDGE_NO_OP);
        check("convolve default edge condition", new ConvolveOp(new Kernel(3, 3, box)).getEdgeCondition() == ConvolveOp.EDGE_ZERO_FILL);
        Kernel copy = op.getKernel();
        check("convolve kernel copy", copy.getWidth() == 3 && copy.getHeight() == 3 && copy.getXOrigin() == 1 && copy.getYOrigin() == 1
            && java.util.Arrays.equals(copy.getKernelData(null), box));
        check("kernel origin of even size", new Kernel(4, 2, new float[8]).getXOrigin() == 1 && new Kernel(4, 2, new float[8]).getYOrigin() == 0);
        try { new Kernel(3, 3, new float[8]); check("kernel too small rejected", false); }
        catch (IllegalArgumentException expected) { check("kernel too small rejected", true); }
        Rectangle2D bounds = op.getBounds2D(src);
        check("convolve bounds", bounds.getX() == 0 && bounds.getY() == 0 && bounds.getWidth() == src.getWidth() && bounds.getHeight() == src.getHeight());
        Point2D p = op.getPoint2D(new Point2D.Double(3.5, 4.25), null);
        check("convolve point", p.getX() == 3.5 && p.getY() == 4.25);
        BufferedImage compatible = op.createCompatibleDestImage(src, null);
        check("convolve compatible image", compatible.getWidth() == src.getWidth() && compatible.getHeight() == src.getHeight()
            && compatible.getColorModel().hasAlpha() && compatible.getColorModel().getNumComponents() == 4 && compatible.getRGB(5, 5) == 0);
        try { op.filter(src, src); check("convolve rejects src == dst", false); }
        catch (IllegalArgumentException expected) { check("convolve rejects src == dst", true); }
        try { op.filter((BufferedImage) null, null); check("convolve rejects null src", false); }
        catch (NullPointerException expected) { check("convolve rejects null src", true); }
        // Raster path: same numbers, bands taken as they are.
        WritableRaster raster = op.filter(src.getRaster(), null);
        BufferedImage viaRaster = new BufferedImage(src.getColorModel(), raster, false, null);
        equalImages("convolve raster path", modelConvolve(src, box, 3, 3, ConvolveOp.EDGE_NO_OP), viaRaster, 1);
        try { op.filter(src.getRaster(), src.getRaster()); check("convolve raster rejects src == dst", false); }
        catch (IllegalArgumentException expected) { check("convolve raster rejects src == dst", true); }
        try {
            op.filter(src.getRaster(), new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB).getRaster());
            check("convolve raster rejects band mismatch", false);
        } catch (ImagingOpException expected) { check("convolve raster rejects band mismatch", true); }
        // A kernel whose weights exceed what medialib accepts is refused the way desktop refuses it.
        float[] huge = { 0, 0, 0, 0, 70000f, 0, 0, 0, 0 };
        try { new ConvolveOp(new Kernel(3, 3, huge)).filter(src, null); check("convolve refuses oversized kernel", false); }
        catch (ImagingOpException expected) { check("convolve refuses oversized kernel", true); }
        // Images smaller than the kernel are all edge.
        BufferedImage tiny = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        tiny.setRGB(0, 0, 0xff102030); tiny.setRGB(1, 1, 0x80405060);
        BufferedImage tinyOut = new ConvolveOp(new Kernel(3, 3, box), ConvolveOp.EDGE_NO_OP, null).filter(tiny, null);
        check("convolve tiny image copies source", tinyOut.getRGB(0, 0) == 0xff102030 && tinyOut.getRGB(1, 1) == 0x80405060 && tinyOut.getRGB(1, 0) == 0);
        BufferedImage tinyZero = new ConvolveOp(new Kernel(3, 3, box)).filter(tiny, null);
        check("convolve tiny image zero fill", tinyZero.getRGB(0, 0) == 0 && tinyZero.getRGB(1, 1) == 0);
    }

    private static void rescale() {
        int[] types = { BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_RGB };
        for (int type : types) {
            String t = type == BufferedImage.TYPE_INT_ARGB ? "ARGB" : "RGB";
            BufferedImage src = fixture(type);
            equalImages("rescale single factor " + t, modelRescale(src, new float[] { 1.5f }, new float[] { -20f }),
                new RescaleOp(1.5f, -20f, null).filter(src, null), 0);
            equalImages("rescale fractional truncates " + t, modelRescale(src, new float[] { 0.37f }, new float[] { 0.9f }),
                new RescaleOp(0.37f, 0.9f, null).filter(src, null), 0);
            equalImages("rescale per colour band " + t,
                modelRescale(src, new float[] { 0.5f, 1.2f, 2f }, new float[] { 10f, -5f, 0f }),
                new RescaleOp(new float[] { 0.5f, 1.2f, 2f }, new float[] { 10f, -5f, 0f }, null).filter(src, null), 0);
            if (type == BufferedImage.TYPE_INT_ARGB) {
                equalImages("rescale with alpha band " + t,
                    modelRescale(src, new float[] { 1f, 1f, 1f, 0.5f }, new float[] { 0f, 0f, 0f, 16f }),
                    new RescaleOp(new float[] { 1f, 1f, 1f, 0.5f }, new float[] { 0f, 0f, 0f, 16f }, null).filter(src, null), 0);
            }
            BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), type);
            check("rescale returns the given destination " + t, new RescaleOp(2f, 0f, null).filter(src, dst) == dst);
            equalImages("rescale into given destination " + t, modelRescale(src, new float[] { 2f }, new float[] { 0f }), dst, 0);
            // In place is allowed.
            BufferedImage copy = fixture(type);
            BufferedImage expected = modelRescale(copy, new float[] { 0.8f }, new float[] { 3f });
            new RescaleOp(0.8f, 3f, null).filter(copy, copy);
            equalImages("rescale in place " + t, expected, copy, 0);
        }
        BufferedImage src = fixture(BufferedImage.TYPE_INT_ARGB);
        RescaleOp op = new RescaleOp(new float[] { 1f, 2f }, new float[] { 3f, 4f, 5f }, null);
        check("rescale factor count is the shorter array", op.getNumFactors() == 2 && op.getScaleFactors(null).length == 2 && op.getOffsets(null)[1] == 4f);
        float[] into = new float[1];
        check("rescale factors copied into a shorter array", op.getScaleFactors(into) == into && into[0] == 1f);
        try { op.filter(src, null); check("rescale rejects a bad factor count", false); }
        catch (IllegalArgumentException expected) { check("rescale rejects a bad factor count", true); }
        try { new RescaleOp(1f, 0f, null).filter(src, new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB)); check("rescale rejects size mismatch", false); }
        catch (IllegalArgumentException expected) { check("rescale rejects size mismatch", true); }
        try { new RescaleOp(1f, 0f, null).filter(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED), null); check("rescale rejects indexed", false); }
        catch (IllegalArgumentException expected) { check("rescale rejects indexed", true); }
        // Raster path: every band including alpha when there are four factors, band 3 skipped for one or three.
        WritableRaster out = new RescaleOp(new float[] { 2f, 2f, 2f, 2f }, new float[4], null).filter(src.getRaster(), null);
        int[] pixel = out.getPixel(20, 12, (int[]) null), source = src.getRaster().getPixel(20, 12, (int[]) null);
        check("rescale raster scales alpha with four factors", pixel[3] == Math.min(255, source[3] * 2) && pixel[0] == Math.min(255, source[0] * 2));
        out = new RescaleOp(2f, 0f, null).filter(src.getRaster(), null);
        pixel = out.getPixel(20, 12, (int[]) null);
        check("rescale raster leaves band 3 with one factor", pixel[3] == source[3] && pixel[1] == Math.min(255, source[1] * 2));
        Rectangle2D bounds = op.getBounds2D(src);
        check("rescale bounds", bounds.getWidth() == src.getWidth() && bounds.getHeight() == src.getHeight());
        check("rescale point", op.getPoint2D(new Point2D.Float(1, 2), null).getX() == 1);
        check("rescale hints kept", new RescaleOp(1f, 0f, null).getRenderingHints() == null);
    }

    private static void lookup() {
        byte[] invert = new byte[256];
        byte[] half = new byte[256];
        byte[] threshold = new byte[256];
        byte[] identity = new byte[256];
        for (int i = 0; i < 256; i++) {
            invert[i] = (byte) (255 - i);
            half[i] = (byte) (i / 2);
            threshold[i] = (byte) (i < 128 ? 0 : 255);
            identity[i] = (byte) i;
        }
        int[] types = { BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_RGB };
        for (int type : types) {
            String t = type == BufferedImage.TYPE_INT_ARGB ? "ARGB" : "RGB";
            BufferedImage src = fixture(type);
            equalImages("lookup one table keeps alpha " + t, modelLookup(src, new byte[][] { invert }),
                new LookupOp(new ByteLookupTable(0, invert), null).filter(src, null), 0);
            equalImages("lookup three tables " + t, modelLookup(src, new byte[][] { invert, half, threshold }),
                new LookupOp(new ByteLookupTable(0, new byte[][] { invert, half, threshold }), null).filter(src, null), 0);
            if (type == BufferedImage.TYPE_INT_ARGB) {
                equalImages("lookup four tables touch alpha " + t, modelLookup(src, new byte[][] { identity, identity, identity, half }),
                    new LookupOp(new ByteLookupTable(0, new byte[][] { identity, identity, identity, half }), null).filter(src, null), 0);
            }
            BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), type);
            check("lookup returns the given destination " + t, new LookupOp(new ByteLookupTable(0, half), null).filter(src, dst) == dst);
            equalImages("lookup into given destination " + t, modelLookup(src, new byte[][] { half }), dst, 0);
            BufferedImage copy = fixture(type);
            BufferedImage expected = modelLookup(copy, new byte[][] { threshold });
            new LookupOp(new ByteLookupTable(0, threshold), null).filter(copy, copy);
            equalImages("lookup in place " + t, expected, copy, 0);
        }
        BufferedImage src = fixture(BufferedImage.TYPE_INT_ARGB);
        LookupOp op = new LookupOp(new ByteLookupTable(0, invert), null);
        check("lookup table returned by reference", op.getTable().getNumComponents() == 1 && op.getTable().getOffset() == 0);
        try { new LookupOp(new ByteLookupTable(0, new byte[][] { invert, half }), null).filter(src, null); check("lookup rejects two tables on ARGB", false); }
        catch (IllegalArgumentException expected) { check("lookup rejects two tables on ARGB", true); }
        try { op.filter(new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_INDEXED), null); check("lookup rejects indexed", false); }
        catch (IllegalArgumentException expected) { check("lookup rejects indexed", true); }
        // Raster path treats alpha as any other band.
        WritableRaster out = op.filter(src.getRaster(), null);
        int[] pixel = out.getPixel(20, 12, (int[]) null), source = src.getRaster().getPixel(20, 12, (int[]) null);
        check("lookup raster inverts every band", pixel[3] == 255 - source[3] && pixel[0] == 255 - source[0] && pixel[2] == 255 - source[2]);
        // Tables: offsets, short tables, pixel arrays.
        ByteLookupTable offsetTable = new ByteLookupTable(10, new byte[] { 1, 2, 3 });
        check("byte table lookupPixel honours offset", java.util.Arrays.equals(offsetTable.lookupPixel(new int[] { 10, 12 }, null), new int[] { 1, 3 }));
        check("byte table lookupPixel on bytes", java.util.Arrays.equals(offsetTable.lookupPixel(new byte[] { 11 }, null), new byte[] { 2 }));
        try { offsetTable.lookupPixel(new int[] { 9 }, null); check("byte table rejects index below offset", false); }
        catch (ArrayIndexOutOfBoundsException expected) { check("byte table rejects index below offset", true); }
        try { new ByteLookupTable(-1, invert); check("byte table rejects negative offset", false); }
        catch (IllegalArgumentException expected) { check("byte table rejects negative offset", true); }
        short[] wide = new short[65536];
        for (int i = 0; i < wide.length; i++) wide[i] = (short) (65535 - i);
        ShortLookupTable shortTable = new ShortLookupTable(0, wide);
        check("short table lookupPixel masks to unsigned", shortTable.lookupPixel(new int[] { 1 }, null)[0] == (int) (short) 65534
            && shortTable.lookupPixel(new short[] { 0 }, null)[0] == (short) 65535);
        check("short table components", new ShortLookupTable(0, new short[][] { wide, wide }).getNumComponents() == 2
            && new ShortLookupTable(5, wide).getOffset() == 5 && shortTable.getTable()[0] == wide);
        // Byte lookup through the short path: a 256-entry short table on a byte raster via LookupOp.filter(Raster, Raster).
        short[] shortHalf = new short[256];
        for (int i = 0; i < 256; i++) shortHalf[i] = (short) (i / 2);
        WritableRaster shortOut = new LookupOp(new ShortLookupTable(0, shortHalf), null).filter(src.getRaster(), null);
        int[] sp = shortOut.getPixel(20, 12, (int[]) null);
        check("lookup raster with short table", sp[0] == source[0] / 2 && sp[3] == source[3] / 2);
        Rectangle2D bounds = op.getBounds2D(src);
        check("lookup bounds", bounds.getWidth() == src.getWidth() && bounds.getHeight() == src.getHeight());
        check("lookup point", op.getPoint2D(new Point2D.Float(7, 8), null).getY() == 8);
        BufferedImage compatible = op.createCompatibleDestImage(src, null);
        check("lookup compatible image", compatible.getType() == BufferedImage.TYPE_INT_ARGB && compatible.getWidth() == src.getWidth());
    }

    private static void chained() {
        // Desktop's ImageUtil-style use: tint through a rescale, blur, then invert through a lookup.
        BufferedImage src = fixture(BufferedImage.TYPE_INT_ARGB);
        float[] box = new float[9];
        java.util.Arrays.fill(box, 1f / 9f);
        BufferedImage tinted = new RescaleOp(new float[] { 1f, 0.5f, 0.5f, 1f }, new float[] { 0f, 0f, 0f, 0f }, null).filter(src, null);
        BufferedImage blurred = new ConvolveOp(new Kernel(3, 3, box), ConvolveOp.EDGE_NO_OP, null).filter(tinted, null);
        BufferedImage expected = modelConvolve(modelRescale(src, new float[] { 1f, 0.5f, 0.5f, 1f }, new float[4]), box, 3, 3, ConvolveOp.EDGE_NO_OP);
        equalImages("rescale then convolve", expected, blurred, 1);
        byte[] invert = new byte[256];
        for (int i = 0; i < 256; i++) invert[i] = (byte) (255 - i);
        BufferedImage inverted = new LookupOp(new ByteLookupTable(0, invert), null).filter(blurred, null);
        equalImages("convolve then lookup", modelLookup(blurred, new byte[][] { invert }), inverted, 0);
        Raster data = blurred.getData();
        check("getData snapshot matches", data.getSample(11, 9, 0) == (blurred.getRGB(11, 9) >>> 16 & 255));
    }

    public static void main(String[] args) {
        convolve();
        rescale();
        lookup();
        chained();
        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
