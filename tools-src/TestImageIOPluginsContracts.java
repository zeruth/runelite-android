// host-contract: patched-args=shadow
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * javax.imageio plugin contracts: PNG, GIF, BMP and WBMP round trips over a generated
 * fixture set come back sample- and RGB-identical, an animated GIF reports every frame
 * while ImageIO.read returns the first, the PNG writer's progressive and (JDK 9+)
 * compression params, and the writer format names. Both runs assert the same values, so
 * a pass on the shadow is a pass against the JDK's own plugins.
 */
public final class TestImageIOPluginsContracts {
    private static int failures;
    private static int total;
    /** Set by the runner's {@code patched-args=shadow} on the run against the shadow classes. */
    private static boolean shadow;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static final int W = 13;
    private static final int H = 7;

    // ---- fixtures -------------------------------------------------------------------

    private static int argbAt(int x, int y) {
        int a = (x % 3 == 0) ? 0xff : (x % 3 == 1 ? 0x80 : 0x00);
        int r = (x * 19 + y * 7) & 0xff;
        int g = (x * 5 + y * 31) & 0xff;
        int b = (x * y * 11 + 3) & 0xff;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static BufferedImage direct(int type) {
        BufferedImage img = new BufferedImage(W, H, type);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.setRGB(x, y, argbAt(x, y));
            }
        }
        return img;
    }

    private static BufferedImage gray() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.getRaster().setSample(x, y, 0, (x * 17 + y * 29) & 0xff);
            }
        }
        return img;
    }

    private static BufferedImage gray16() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.getRaster().setSample(x, y, 0, (x * 4099 + y * 7919) & 0xffff);
            }
        }
        return img;
    }

    private static IndexColorModel palette(int bits, int size, int transparent, boolean alphas) {
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        byte[] a = new byte[size];
        for (int i = 0; i < size; i++) {
            r[i] = (byte) (i * 37 + 5);
            g[i] = (byte) (255 - i * 13);
            b[i] = (byte) (i * i * 3);
            a[i] = (byte) (i % 4 == 1 ? 0x40 : 0xff);
        }
        if (alphas) {
            return new IndexColorModel(bits, size, r, g, b, a);
        }
        return new IndexColorModel(bits, size, r, g, b, transparent);
    }

    private static BufferedImage indexed(IndexColorModel icm, int shift) {
        int type = icm.getPixelSize() < 8
            ? BufferedImage.TYPE_BYTE_BINARY : BufferedImage.TYPE_BYTE_INDEXED;
        BufferedImage img = new BufferedImage(W, H, type, icm);
        int size = icm.getMapSize();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.getRaster().setSample(x, y, 0, (x + 2 * y + shift) % size);
            }
        }
        return img;
    }

    private static BufferedImage binary() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                img.getRaster().setSample(x, y, 0, ((x ^ y) & 1) == 0 || x == y ? 1 : 0);
            }
        }
        return img;
    }

    // ---- helpers --------------------------------------------------------------------

    private static byte[] write(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean written = ImageIO.write(img, format, out);
        return written ? out.toByteArray() : null;
    }

    private static byte[] writeWithParam(BufferedImage img, String format, ImageWriteParam param,
                                         ImageWriter writer) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(out);
        writer.setOutput(ios);
        writer.write(null, new IIOImage(img, null, null), param);
        ios.close();
        writer.dispose();
        return out.toByteArray();
    }

    /**
     * Same size, the same samples band by band, and the same getRGB. 16-bit gray skips
     * getRGB: that one goes through a colour-space conversion, which is not a codec
     * property.
     */
    private static boolean same(BufferedImage expected, BufferedImage actual, boolean rgb) {
        if (actual == null || actual.getWidth() != expected.getWidth()
            || actual.getHeight() != expected.getHeight()) {
            return false;
        }
        Raster e = expected.getRaster();
        Raster a = actual.getRaster();
        if (e.getNumBands() != a.getNumBands()) {
            return false;
        }
        int[] es = new int[e.getNumBands()];
        int[] as = new int[a.getNumBands()];
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (!Arrays.equals(e.getPixel(x, y, es), a.getPixel(x, y, as))) {
                    System.out.println("     sample mismatch at " + x + "," + y + ": "
                        + Arrays.toString(es) + " vs " + Arrays.toString(as));
                    return false;
                }
                if (rgb && expected.getRGB(x, y) != actual.getRGB(x, y)) {
                    System.out.println("     rgb mismatch at " + x + "," + y + ": "
                        + Integer.toHexString(expected.getRGB(x, y)) + " vs "
                        + Integer.toHexString(actual.getRGB(x, y)));
                    return false;
                }
            }
        }
        return true;
    }

    private static void roundTrip(String label, String format, BufferedImage img, boolean rgb)
        throws Exception {
        byte[] data = write(img, format);
        check(format + " " + label + " is written", data != null && data.length > 0);
        if (data == null) {
            return;
        }
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(data));
        check(format + " " + label + " round trip is identical", same(img, back, rgb));
    }

    // ---- contracts ------------------------------------------------------------------

    private static void roundTrips() throws Exception {
        BufferedImage argb = direct(BufferedImage.TYPE_INT_ARGB);
        BufferedImage rgb = direct(BufferedImage.TYPE_INT_RGB);
        BufferedImage bgr = direct(BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage gray = gray();
        BufferedImage indexed = indexed(palette(8, 16, -1, false), 0);
        BufferedImage indexedTrans = indexed(palette(8, 16, 3, false), 1);
        BufferedImage indexedAlpha = indexed(palette(8, 12, -1, true), 2);
        BufferedImage packed2 = indexed(palette(2, 4, -1, false), 0);
        BufferedImage binary = binary();

        roundTrip("INT_ARGB", "png", argb, true);
        roundTrip("INT_RGB", "png", rgb, true);
        roundTrip("3BYTE_BGR", "png", bgr, true);
        roundTrip("BYTE_GRAY", "png", gray, true);
        roundTrip("USHORT_GRAY", "png", gray16(), false);
        roundTrip("BYTE_INDEXED", "png", indexed, true);
        roundTrip("BYTE_INDEXED tRNS alpha", "png", indexedAlpha, true);
        roundTrip("BYTE_BINARY 2-bit", "png", packed2, true);
        roundTrip("BYTE_BINARY", "png", binary, true);

        roundTrip("BYTE_INDEXED", "gif", indexed, true);
        roundTrip("BYTE_INDEXED transparent", "gif", indexedTrans, true);
        roundTrip("BYTE_BINARY", "gif", binary, true);

        roundTrip("INT_RGB", "bmp", rgb, true);
        roundTrip("3BYTE_BGR", "bmp", bgr, true);
        roundTrip("BYTE_INDEXED", "bmp", indexed, true);
        roundTrip("BYTE_BINARY", "bmp", binary, true);

        roundTrip("BYTE_BINARY", "wbmp", binary, true);

        check("bmp refuses INT_ARGB like the JDK", write(argb, "bmp") == null);
        check("wbmp refuses INT_RGB", write(rgb, "wbmp") == null);
    }

    private static void pngParams() throws Exception {
        BufferedImage argb = direct(BufferedImage.TYPE_INT_ARGB);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        check("png param can write progressive", param.canWriteProgressive());
        check("png param can write compressed", param.canWriteCompressed());
        check("png compression types", Arrays.equals(param.getCompressionTypes(),
            new String[] {"Deflate"}));
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        check("png compression is lossless", param.isCompressionLossless());
        check("png default quality", param.getCompressionQuality() == 0.5f);
        check("png quality values", Arrays.equals(param.getCompressionQualityValues(),
            new float[] {0.00f, 0.30f, 0.75f, 1.00f}));

        param.setCompressionQuality(0.0f);
        param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        byte[] small = writeWithParam(argb, "png", param, writer);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(small));
        check("png interlaced best compression round trip", same(argb, back, true));

        ImageWriter plain = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam off = plain.getDefaultWriteParam();
        off.setCompressionMode(ImageWriteParam.MODE_DISABLED);
        byte[] stored = writeWithParam(argb, "png", off, plain);
        back = ImageIO.read(new ByteArrayInputStream(stored));
        check("png uncompressed round trip", same(argb, back, true));
        check("png uncompressed is larger than best compression", stored.length > small.length);

        ImageReader reader = ImageIO.getImageReadersByFormatName("png").next();
        ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(small));
        reader.setInput(iis);
        check("png reader size", reader.getWidth(0) == W && reader.getHeight(0) == H);
        check("png reader one image", reader.getNumImages(true) == 1);
        reader.dispose();
        iis.close();
    }

    private static void animatedGif() throws Exception {
        IndexColorModel icm = palette(8, 16, -1, false);
        BufferedImage[] frames = {indexed(icm, 0), indexed(icm, 5), indexed(icm, 9)};

        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        check("gif writer can write sequences", writer.canWriteSequence());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageOutputStream ios = ImageIO.createImageOutputStream(out);
        writer.setOutput(ios);
        writer.prepareWriteSequence(null);
        for (BufferedImage frame : frames) {
            writer.writeToSequence(new IIOImage(frame, null, null), null);
        }
        writer.endWriteSequence();
        ios.close();
        writer.dispose();
        byte[] data = out.toByteArray();

        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
        reader.setInput(iis, false);
        check("animated gif getNumImages", reader.getNumImages(true) == frames.length);
        for (int i = 0; i < frames.length; i++) {
            check("animated gif frame " + i, same(frames[i], reader.read(i), true));
        }
        boolean threw = false;
        try {
            reader.read(frames.length);
        } catch (IndexOutOfBoundsException e) {
            threw = true;
        }
        check("animated gif read past the last frame throws", threw);
        reader.dispose();
        iis.close();

        check("ImageIO.read of an animated gif is the first frame",
            same(frames[0], ImageIO.read(new ByteArrayInputStream(data)), true));
    }

    private static Set<String> names(String[] names) {
        Set<String> set = new TreeSet<String>();
        for (String n : names) {
            // The JDK 9+ TIFF plugin is outside the 8u plugin set this card ports.
            if (!n.equalsIgnoreCase("tif") && !n.equalsIgnoreCase("tiff")) {
                set.add(n);
            }
        }
        return set;
    }

    private static boolean webpAvailable() {
        try {
            Class.forName("com.sun.imageio.plugins.webp.WebPImageWriterSpi");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static void formatNames() {
        Set<String> expected = new TreeSet<String>(Arrays.asList(
            "bmp", "BMP", "gif", "GIF", "jpeg", "JPEG", "jpg", "JPG",
            "png", "PNG", "wbmp", "WBMP"));
        // WebP is the Android extra; its writer sits on the platform codec, which the
        // host run cannot compile, so it is expected exactly when the class exists.
        if (shadow && webpAvailable()) {
            expected.add("webp");
            expected.add("WEBP");
        }
        Set<String> writers = names(ImageIO.getWriterFormatNames());
        System.out.println("     writer format names: " + writers);
        check("writer format names are the JDK set" + (expected.contains("webp") ? " plus webp" : ""),
            writers.equals(expected));
        Set<String> readers = names(ImageIO.getReaderFormatNames());
        check("reader format names cover the writer set", readers.containsAll(expected));

        Iterator<ImageReader> gif = ImageIO.getImageReadersBySuffix("gif");
        check("gif reader by suffix", gif.hasNext());
        Iterator<ImageWriter> png = ImageIO.getImageWritersByMIMEType("image/png");
        check("png writer by MIME type", png.hasNext());
        ImageWriter w = png.next();
        ImageReader paired = ImageIO.getImageReader(w);
        check("png writer pairs with a png reader", paired != null
            && Arrays.asList(paired.getOriginatingProvider().getFormatNames()).contains("png"));
    }

    public static void main(String[] args) throws Exception {
        shadow = args.length > 0 && "shadow".equals(args[0]);
        roundTrips();
        pngParams();
        animatedGif();
        formatNames();
        System.out.println(total + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
