// host-contract
import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferShort;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.math.BigInteger;

/**
 * java.awt.image model contracts: every predefined BufferedImage type round-trips
 * colors through setRGB/getRGB, its raster, a rebuilt image, a subimage and
 * setData/copyData; the Raster factories, children and float/double sample paths; and
 * the ColorModel family (565 scaling, coerceData, IndexColorModel's transparent pixel
 * and valid bits, float ComponentColorModel). Nothing here draws, so the patched run
 * never reaches the JDK's native loops.
 */
public final class TestRasterContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static void equal(String name, long expected, long actual) {
        boolean ok = expected == actual;
        total++;
        if (!ok) failures++;
        System.out.println((ok ? "ok   " : "FAIL ") + name
            + (ok ? "" : " expected " + Long.toHexString(expected) + " got " + Long.toHexString(actual)));
    }

    private static final String[] NAMES = {"CUSTOM", "INT_RGB", "INT_ARGB", "INT_ARGB_PRE", "INT_BGR", "3BYTE_BGR",
        "4BYTE_ABGR", "4BYTE_ABGR_PRE", "USHORT_565_RGB", "USHORT_555_RGB", "BYTE_GRAY", "USHORT_GRAY",
        "BYTE_BINARY", "BYTE_INDEXED"};

    private static boolean gray(int type) {
        return type == BufferedImage.TYPE_BYTE_GRAY || type == BufferedImage.TYPE_USHORT_GRAY;
    }

    private static boolean exactColor(int type) {
        return type == BufferedImage.TYPE_INT_RGB || type == BufferedImage.TYPE_INT_ARGB
            || type == BufferedImage.TYPE_INT_BGR || type == BufferedImage.TYPE_3BYTE_BGR
            || type == BufferedImage.TYPE_4BYTE_ABGR;
    }

    private static boolean straightAlpha(int type) {
        return type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_4BYTE_ABGR;
    }

    private static boolean hasAlpha(int type) {
        return type == BufferedImage.TYPE_INT_ARGB || type == BufferedImage.TYPE_INT_ARGB_PRE
            || type == BufferedImage.TYPE_4BYTE_ABGR || type == BufferedImage.TYPE_4BYTE_ABGR_PRE;
    }

    private static void imageType(int type) {
        String n = NAMES[type] + " ";
        BufferedImage img = new BufferedImage(5, 4, type);
        equal(n + "getType", type, img.getType());
        check(n + "raster size", img.getRaster().getWidth() == 5 && img.getRaster().getHeight() == 4);
        check(n + "compatible", img.getColorModel().isCompatibleRaster(img.getRaster()));

        img.setRGB(0, 0, 0xff000000);
        img.setRGB(1, 0, 0xffffffff);
        equal(n + "black", 0xff000000L, img.getRGB(0, 0) & 0xffffffffL);
        equal(n + "white", 0xffffffffL, img.getRGB(1, 0) & 0xffffffffL);

        int[] colors = {0xff123456, 0xff8040c0, 0xff00ff7f, 0xffc8c8c8};
        for (int i = 0; i < colors.length; i++) img.setRGB(i, 1, colors[i]);
        if (exactColor(type)) {
            for (int i = 0; i < colors.length; i++) {
                equal(n + "exact " + Integer.toHexString(colors[i]), colors[i] & 0xffffffffL, img.getRGB(i, 1) & 0xffffffffL);
            }
        }
        if (!gray(type)) {
            // What a lossy type stores, it stores again unchanged.
            for (int i = 0; i < colors.length; i++) {
                int once = img.getRGB(i, 1);
                img.setRGB(i, 2, once);
                equal(n + "stable " + Integer.toHexString(colors[i]), once & 0xffffffffL, img.getRGB(i, 2) & 0xffffffffL);
            }
        }
        if (straightAlpha(type)) {
            img.setRGB(4, 3, 0x80123456);
            equal(n + "alpha exact", 0x80123456L, img.getRGB(4, 3) & 0xffffffffL);
        }
        WritableRaster alpha = img.getAlphaRaster();
        check(n + "alpha raster " + (hasAlpha(type) ? "present" : "absent"), hasAlpha(type) == (alpha != null));
        if (alpha != null) {
            check(n + "alpha raster bands", alpha.getNumBands() == 1);
            equal(n + "alpha raster sample", 255, alpha.getSample(1, 0, 0));
        }

        // getRGB/setRGB bulk with an offset and a scansize wider than the region.
        int[] bulk = img.getRGB(0, 0, 4, 3, null, 2, 6);
        equal(n + "bulk length", 2 + 3 * 6, bulk.length);
        boolean same = true;
        for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) same &= bulk[2 + y * 6 + x] == img.getRGB(x, y);
        check(n + "bulk getRGB", same);
        BufferedImage copy = new BufferedImage(5, 4, type);
        copy.setRGB(0, 0, 4, 3, bulk, 2, 6);
        same = true;
        for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) same &= copy.getRGB(x, y) == img.getRGB(x, y);
        check(n + "bulk setRGB", same);

        // An image rebuilt from its own model and raster shares the pixels and the type.
        BufferedImage rebuilt = new BufferedImage(img.getColorModel(), img.getRaster(), img.isAlphaPremultiplied(), null);
        same = true;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 5; x++) same &= rebuilt.getRGB(x, y) == img.getRGB(x, y);
        check(n + "rebuilt pixels", same);
        if (!gray(type)) equal(n + "rebuilt type", type, rebuilt.getType());

        BufferedImage sub = img.getSubimage(1, 1, 3, 2);
        check(n + "subimage size", sub.getWidth() == 3 && sub.getHeight() == 2);
        equal(n + "subimage pixel", img.getRGB(2, 2) & 0xffffffffL, sub.getRGB(1, 1) & 0xffffffffL);
        sub.setRGB(0, 0, 0xffffffff);
        equal(n + "subimage writes through", 0xffffffffL, img.getRGB(1, 1) & 0xffffffffL);
        if (!gray(type)) equal(n + "subimage type", type, sub.getType());

        BufferedImage target = new BufferedImage(5, 4, type);
        target.setData(img.getData());
        same = true;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 5; x++) same &= target.getRGB(x, y) == img.getRGB(x, y);
        check(n + "setData(getData())", same);
        WritableRaster copied = img.copyData(null);
        Object a = null;
        Object b = null;
        same = true;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 5; x++) {
            a = copied.getDataElements(x, y, a);
            b = img.getRaster().getDataElements(x, y, b);
            same &= img.getColorModel().getRGB(a) == img.getColorModel().getRGB(b);
        }
        check(n + "copyData", same);
        Raster part = img.getData(new java.awt.Rectangle(1, 1, 2, 2));
        check(n + "getData(rect) origin", part.getMinX() == 1 && part.getMinY() == 1 && part.getWidth() == 2);
        equal(n + "getData(rect) sample", img.getRaster().getSample(2, 2, 0), part.getSample(2, 2, 0));
    }

    private static void rasters() {
        WritableRaster packed = Raster.createPackedRaster(DataBuffer.TYPE_INT, 4, 3,
            new int[]{0xff0000, 0xff00, 0xff}, null);
        check("packed masks: sample model", packed.getSampleModel() instanceof SinglePixelPackedSampleModel);
        packed.setPixel(1, 2, new int[]{1, 2, 3});
        equal("packed masks: element", 0x010203, ((int[]) packed.getDataElements(1, 2, null))[0]);

        WritableRaster bits = Raster.createPackedRaster(DataBuffer.TYPE_BYTE, 10, 2, 1, 2, null);
        check("packed bits: multi-pixel", bits.getSampleModel() instanceof MultiPixelPackedSampleModel);
        equal("packed bits: stride", 2, ((MultiPixelPackedSampleModel) bits.getSampleModel()).getPixelBitStride());
        bits.setSample(9, 1, 0, 3);
        equal("packed bits: sample", 3, bits.getSample(9, 1, 0));
        equal("packed bits: buffer", 6, bits.getDataBuffer().getSize());

        WritableRaster bandsPacked = Raster.createPackedRaster(DataBuffer.TYPE_BYTE, 2, 2, 3, 2, null);
        int[] masks = ((SinglePixelPackedSampleModel) bandsPacked.getSampleModel()).getBitMasks();
        check("packed bands: masks", masks[0] == 0x30 && masks[1] == 0x0c && masks[2] == 0x03);

        WritableRaster inter = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, 3, 2, 3, new Point(5, 7));
        check("interleaved: location", inter.getMinX() == 5 && inter.getMinY() == 7 && inter.getNumBands() == 3);
        inter.setPixel(6, 8, new int[]{10, 20, 30});
        int[] px = inter.getPixel(6, 8, (int[]) null);
        check("interleaved: pixel", px[0] == 10 && px[1] == 20 && px[2] == 30);
        int[] samples = inter.getSamples(5, 7, 3, 2, 1, (int[]) null);
        equal("interleaved: samples", 20, samples[4]);

        WritableRaster banded = Raster.createBandedRaster(DataBuffer.TYPE_INT, 3, 3, 2, null);
        check("banded: banks", banded.getDataBuffer().getNumBanks() == 2);
        banded.setSample(2, 2, 1, 77);
        equal("banded: sample", 77, banded.getSample(2, 2, 1));
        equal("banded: other band", 0, banded.getSample(2, 2, 0));

        WritableRaster child = inter.createWritableChild(6, 7, 2, 2, 0, 0, new int[]{2});
        check("child: bands", child.getNumBands() == 1 && child.getMinX() == 0);
        equal("child: sample", 30, child.getSample(0, 1, 0));
        child.setSample(0, 1, 0, 99);
        equal("child: writes through", 99, inter.getSample(6, 8, 2));
        check("child: parent", child.getParent() == inter);
        Raster moved = inter.createTranslatedChild(0, 0);
        equal("translated child", 99, moved.getSample(1, 1, 2));
        boolean threw = false;
        try {
            inter.createChild(4, 7, 2, 2, 0, 0, null);
        } catch (RasterFormatException e) {
            threw = true;
        }
        check("child outside throws RasterFormatException", threw);

        WritableRaster compatible = inter.createCompatibleWritableRaster(4, 4);
        check("compatible raster", compatible.getWidth() == 4 && compatible.getNumBands() == 3 && compatible.getMinX() == 0);

        WritableRaster floats = Raster.createWritableRaster(new BandedSampleModel(DataBuffer.TYPE_FLOAT, 2, 2, 2), null);
        check("float: buffer", floats.getDataBuffer() instanceof DataBufferFloat);
        floats.setSample(1, 1, 1, 0.25f);
        check("float: sample", floats.getSampleFloat(1, 1, 1) == 0.25f);
        check("float: double read", floats.getSampleDouble(1, 1, 1) == 0.25);
        floats.setPixel(0, 0, new double[]{1.5, -2.5});
        float[] fp = floats.getPixel(0, 0, (float[]) null);
        check("float: pixel", fp[0] == 1.5f && fp[1] == -2.5f);
        equal("float: int read truncates", -2, floats.getSample(0, 0, 1));
        float[] elements = (float[]) floats.getDataElements(0, 0, null);
        check("float: data elements", elements.length == 2 && elements[1] == -2.5f);

        WritableRaster doubles = Raster.createWritableRaster(new BandedSampleModel(DataBuffer.TYPE_DOUBLE, 2, 1, 1), null);
        check("double: buffer", doubles.getDataBuffer() instanceof DataBufferDouble);
        doubles.setSamples(0, 0, 2, 1, 0, new double[]{0.125, 3.75});
        double[] ds = doubles.getSamples(0, 0, 2, 1, 0, (double[]) null);
        check("double: samples", ds[0] == 0.125 && ds[1] == 3.75);

        DataBufferShort shorts = new DataBufferShort(4);
        shorts.setElem(1, -5);
        equal("short: signed", -5, shorts.getElem(1));
        equal("data type sizes", 64, DataBuffer.getDataTypeSize(DataBuffer.TYPE_DOUBLE));

        WritableRaster dst = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, 3, 2, 3, null);
        dst.setRect(-5, -7, inter);
        equal("setRect translated", 99, dst.getSample(1, 1, 2));
    }

    private static void colorModels() {
        DirectColorModel rgb565 = new DirectColorModel(16, 0xf800, 0x7e0, 0x1f);
        equal("565 transfer type", DataBuffer.TYPE_USHORT, rgb565.getTransferType());
        equal("565 red 16", 132, rgb565.getRed(16 << 11));
        equal("565 green 1", 4, rgb565.getGreen(1 << 5));
        short[] p565 = (short[]) rgb565.getDataElements(0xff808080, null);
        equal("565 encode", (16 << 11) | (32 << 5) | 16, p565[0] & 0xffff);
        check("565 alpha raster absent", rgb565.getAlphaRaster(rgb565.createCompatibleWritableRaster(1, 1)) == null);
        int[] comps = rgb565.getComponents(0xffff, null, 0);
        check("565 components", comps[0] == 31 && comps[1] == 63 && comps[2] == 31);

        ColorModel argb = ColorModel.getRGBdefault();
        equal("default getDataElements", 0x11223344, ((int[]) argb.getDataElements(0x11223344, null))[0]);
        equal("default getRed(Object)", 0x22, argb.getRed(new int[]{0x11223344}));

        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x80ff0000);
        img.coerceData(true);
        check("coerceData premultiplied", img.isAlphaPremultiplied());
        equal("coerceData red sample", 128, img.getRaster().getSample(0, 0, 0));
        equal("coerceData getRGB", 0x80ff0000L, img.getRGB(0, 0) & 0xffffffffL);
        img.coerceData(false);
        check("coerceData back", !img.isAlphaPremultiplied());
        equal("coerceData back sample", 255, img.getRaster().getSample(0, 0, 0));

        byte[] r = {0, (byte) 255, 0, 0};
        byte[] g = {0, 0, (byte) 255, 0};
        byte[] b = {0, 0, 0, (byte) 255};
        IndexColorModel icm = new IndexColorModel(2, 4, r, g, b, 1);
        equal("icm transparent pixel", 1, icm.getTransparentPixel());
        equal("icm transparent alpha", 0, icm.getAlpha(1));
        check("icm has alpha", icm.hasAlpha());
        equal("icm transparency", Transparency.BITMASK, icm.getTransparency());
        equal("icm valid pixels", 15, icm.getValidPixels().longValue());
        check("icm valid", icm.isValid() && icm.isValid(3) && !icm.isValid(4));
        equal("icm nearest", 2, ((byte[]) icm.getDataElements(0xff10f010, null))[0]);
        equal("icm transparent lookup", 1, ((byte[]) icm.getDataElements(0x00123456, null))[0]);
        int[] cmap = {0xff000000, 0xffff0000, 0xff00ff00};
        IndexColorModel partial = new IndexColorModel(2, 3, cmap, 0, DataBuffer.TYPE_BYTE, BigInteger.valueOf(5));
        check("icm partial validity", !partial.isValid() && partial.isValid(2) && !partial.isValid(1));
        IndexColorModel opaque = new IndexColorModel(2, 3, cmap, 0, false, -1, DataBuffer.TYPE_BYTE);
        check("icm opaque has no alpha", !opaque.hasAlpha() && opaque.getNumComponents() == 3);
        BufferedImage expanded = opaque.convertToIntDiscrete(opaque.createCompatibleWritableRaster(2, 1), false);
        equal("convertToIntDiscrete type", BufferedImage.TYPE_INT_RGB, expanded.getType());

        ComponentColorModel floatModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), true, false,
            Transparency.TRANSLUCENT, DataBuffer.TYPE_FLOAT);
        float[] fpix = (float[]) floatModel.getDataElements(0xff8040c0, null);
        check("float model elements", fpix.length == 4 && Math.abs(fpix[0] - 128 / 255f) < 1e-6 && fpix[3] == 1.0f);
        equal("float model getRGB", 0xff8040c0L, floatModel.getRGB(fpix) & 0xffffffffL);
        WritableRaster floatRaster = floatModel.createCompatibleWritableRaster(2, 2);
        check("float model raster", floatModel.isCompatibleRaster(floatRaster) && floatRaster.getTransferType() == DataBuffer.TYPE_FLOAT);
        BufferedImage floatImage = new BufferedImage(floatModel, floatRaster, false, null);
        floatImage.setRGB(1, 1, 0xff102030);
        equal("float image round trip", 0xff102030L, floatImage.getRGB(1, 1) & 0xffffffffL);
        equal("float image type", BufferedImage.TYPE_CUSTOM, floatImage.getType());

        ComponentColorModel ushort = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false,
            Transparency.OPAQUE, DataBuffer.TYPE_USHORT);
        short[] us = (short[]) ushort.getDataElements(0xffff0000, null);
        equal("ushort model red", 0xffff, us[0] & 0xffff);
        equal("ushort model getRGB", 0xffff0000L, ushort.getRGB(us) & 0xffffffffL);
    }

    public static void main(String[] args) {
        for (int type = BufferedImage.TYPE_INT_RGB; type <= BufferedImage.TYPE_BYTE_INDEXED; type++) imageType(type);
        rasters();
        colorModels();
        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
