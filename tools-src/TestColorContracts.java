// host-contract
import java.awt.Color;
import java.awt.color.CMMException;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.color.ICC_ProfileGray;
import java.awt.color.ICC_ProfileRGB;
import java.awt.color.ProfileDataException;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * java.awt.color contracts: the five standard colour spaces, their ICC profiles and
 * ColorConvertOp between them.  Both runs (the JDK's lcms and the shadow's matrix CMM)
 * are held to the same analytic model, so the assertions only encode what the two
 * share: the D50-adapted sRGB colorants of sRGB.pf, the IEC 61966-2.1 tone curve, and
 * CS_GRAY as *linear* gray (desktop's GRAY.pf has a gamma 1.0 TRC, which is why an
 * sRGB mid-gray converts to about 55, not 128).  Tolerances: 1/255 for the ColorSpace
 * round trips, 2 levels for 8-bit image conversions (lcms quantizes through 16-bit
 * tables and interpolates).  No test draws: a JDK Graphics2D in the patched run dies
 * in GraphicsPrimitiveMgr.initIDs.
 */
public final class TestColorContracts {
    private static int failures;
    private static int total;

    private static final double D50_X = 0.9642, D50_Y = 1.0, D50_Z = 0.8249;
    /* sRGB colorants adapted to D50 (Bradford), as carried by rXYZ/gXYZ/bXYZ of sRGB.pf. */
    private static final double[][] SRGB_TO_XYZ = {
        {0.4360747, 0.3850649, 0.1430804},
        {0.2225045, 0.7168786, 0.0606169},
        {0.0139322, 0.0971045, 0.7141733},
    };

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static boolean near(float[] expected, float[] actual, double tolerance) {
        if (expected.length > actual.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (!(Math.abs(expected[i] - actual[i]) <= tolerance)) return false;
        }
        return true;
    }

    private static String str(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) sb.append(i == 0 ? "" : ", ").append(String.format("%.5f", v[i]));
        return sb.append(']').toString();
    }

    private static void near(String name, float[] expected, float[] actual, double tolerance) {
        boolean ok = near(expected, actual, tolerance);
        check(name + (ok ? "" : " expected " + str(expected) + " got " + str(actual)), ok);
    }

    private static double linear(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double encoded(double v) {
        return v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
    }

    private static float[] modelXyz(float[] srgb) {
        double r = linear(srgb[0]), g = linear(srgb[1]), b = linear(srgb[2]);
        float[] xyz = new float[3];
        for (int i = 0; i < 3; i++) {
            xyz[i] = (float) (SRGB_TO_XYZ[i][0] * r + SRGB_TO_XYZ[i][1] * g + SRGB_TO_XYZ[i][2] * b);
        }
        return xyz;
    }

    private static int modelGray(int rgb) {
        double r = linear((rgb >> 16 & 255) / 255.0), g = linear((rgb >> 8 & 255) / 255.0), b = linear((rgb & 255) / 255.0);
        double y = SRGB_TO_XYZ[1][0] * r + SRGB_TO_XYZ[1][1] * g + SRGB_TO_XYZ[1][2] * b;
        return (int) Math.round(Math.max(0, Math.min(1, y)) * 255);
    }

    private static final float[][] SAMPLES = {
        {0f, 0f, 0f}, {1f, 1f, 1f}, {0.5f, 0.5f, 0.5f}, {1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, 1f},
        {0.2f, 0.6f, 0.9f}, {0.85f, 0.35f, 0.1f}, {0.05f, 0.02f, 0.4f}, {0.7f, 0.7f, 0.2f},
    };

    public static void main(String[] args) {
        colorSpaces();
        roundTrips();
        profiles();
        colorMembers();
        convertImages();
        convertRasters();
        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }

    private static void colorSpaces() {
        ColorSpace srgb = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorSpace lin = ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
        ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorSpace xyz = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
        ColorSpace pycc = ColorSpace.getInstance(ColorSpace.CS_PYCC);
        check("getInstance is a singleton", srgb == ColorSpace.getInstance(ColorSpace.CS_sRGB));
        check("standard spaces are ICC_ColorSpace", srgb instanceof ICC_ColorSpace && lin instanceof ICC_ColorSpace
            && gray instanceof ICC_ColorSpace && xyz instanceof ICC_ColorSpace && pycc instanceof ICC_ColorSpace);
        check("isCS_sRGB only for sRGB", srgb.isCS_sRGB() && !lin.isCS_sRGB() && !gray.isCS_sRGB());
        check("types", srgb.getType() == ColorSpace.TYPE_RGB && lin.getType() == ColorSpace.TYPE_RGB
            && gray.getType() == ColorSpace.TYPE_GRAY && xyz.getType() == ColorSpace.TYPE_XYZ
            && pycc.getType() == ColorSpace.TYPE_3CLR);
        check("component counts", srgb.getNumComponents() == 3 && gray.getNumComponents() == 1
            && xyz.getNumComponents() == 3 && pycc.getNumComponents() == 3);
        check("component names", "Red".equals(srgb.getName(0)) && "Blue".equals(srgb.getName(2))
            && "Gray".equals(gray.getName(0)) && "X".equals(xyz.getName(0)) && "Z".equals(xyz.getName(2)));
        check("XYZ range 0..1+32767/32768", xyz.getMinValue(1) == 0f
            && Math.abs(xyz.getMaxValue(1) - (1f + 32767f / 32768f)) < 1e-6);
        check("sRGB range 0..1", srgb.getMinValue(0) == 0f && srgb.getMaxValue(2) == 1f);
        boolean threw = false;
        try {
            srgb.getName(3);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check("getName out of range throws IAE", threw);
        threw = false;
        try {
            ColorSpace.getInstance(4711);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check("getInstance of an unknown space throws IAE", threw);
    }

    private static void roundTrips() {
        double tol = 1.0 / 255;
        ColorSpace srgb = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorSpace lin = ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
        ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorSpace xyz = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
        near("sRGB white toCIEXYZ is D50", new float[] {(float) D50_X, (float) D50_Y, (float) D50_Z},
            srgb.toCIEXYZ(new float[] {1f, 1f, 1f}), tol);
        for (float[] s : SAMPLES) {
            String at = str(s);
            near("sRGB.toCIEXYZ " + at, modelXyz(s), srgb.toCIEXYZ(s), tol);
            near("sRGB->XYZ->sRGB " + at, s, srgb.fromCIEXYZ(srgb.toCIEXYZ(s)), tol);
            float[] l = new float[] {(float) linear(s[0]), (float) linear(s[1]), (float) linear(s[2])};
            near("LINEAR.fromRGB " + at, l, lin.fromRGB(s), tol);
            near("sRGB->linear->sRGB " + at, s, lin.toRGB(lin.fromRGB(s)), tol);
            near("linear->XYZ->linear " + at, l, lin.fromCIEXYZ(lin.toCIEXYZ(l)), tol);
            near("linear.toCIEXYZ matches sRGB.toCIEXYZ " + at, srgb.toCIEXYZ(s), lin.toCIEXYZ(l), tol);
            near("CIEXYZ.fromRGB matches sRGB.toCIEXYZ " + at, srgb.toCIEXYZ(s), xyz.fromRGB(s), tol);
            near("XYZ->sRGB via CIEXYZ.toRGB " + at, s, xyz.toRGB(srgb.toCIEXYZ(s)), tol);
            float y = modelXyz(s)[1];
            near("GRAY.fromRGB is linear luminance " + at, new float[] {y}, gray.fromRGB(s), tol);
            float e = (float) encoded(y);
            near("GRAY.toRGB re-encodes " + at, new float[] {e, e, e}, gray.toRGB(gray.fromRGB(s)), tol);
        }
        for (float g = 0f; g <= 1f; g += 0.125f) {
            near("GRAY->XYZ->GRAY " + g, new float[] {g}, gray.fromCIEXYZ(gray.toCIEXYZ(new float[] {g})), tol);
            near("GRAY.toCIEXYZ is g*D50 " + g, new float[] {(float) (g * D50_X), g, (float) (g * D50_Z)},
                gray.toCIEXYZ(new float[] {g}), tol);
        }
        ColorSpace pycc = ColorSpace.getInstance(ColorSpace.CS_PYCC);
        float[] mid = {0.5f, 0.5f, 0.5f};
        float[] back = pycc.toRGB(pycc.fromRGB(mid));
        near("PYCC round trip of mid gray within 2/255", mid, back, 2.0 / 255);
    }

    private static void profiles() {
        ICC_Profile srgb = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        ICC_Profile gray = ICC_Profile.getInstance(ColorSpace.CS_GRAY);
        ICC_Profile xyz = ICC_Profile.getInstance(ColorSpace.CS_CIEXYZ);
        check("sRGB profile is ICC_ProfileRGB", srgb instanceof ICC_ProfileRGB);
        check("linear profile is ICC_ProfileRGB", ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB) instanceof ICC_ProfileRGB);
        check("gray profile is ICC_ProfileGray", gray instanceof ICC_ProfileGray);
        check("profile singleton", srgb == ICC_Profile.getInstance(ColorSpace.CS_sRGB));
        check("ICC_ColorSpace.getProfile", ((ICC_ColorSpace) ColorSpace.getInstance(ColorSpace.CS_sRGB)).getProfile() == srgb);
        check("sRGB profile class is display", srgb.getProfileClass() == ICC_Profile.CLASS_DISPLAY);
        check("color space / PCS types", srgb.getColorSpaceType() == ColorSpace.TYPE_RGB
            && srgb.getPCSType() == ColorSpace.TYPE_XYZ && gray.getColorSpaceType() == ColorSpace.TYPE_GRAY
            && xyz.getColorSpaceType() == ColorSpace.TYPE_XYZ);
        check("getNumComponents", srgb.getNumComponents() == 3 && gray.getNumComponents() == 1);
        byte[] data = srgb.getData();
        check("getData carries 'acsp' and its own size", data.length > 132 && data[36] == 'a' && data[37] == 'c'
            && data[38] == 's' && data[39] == 'p'
            && ((data[0] & 255) << 24 | (data[1] & 255) << 16 | (data[2] & 255) << 8 | (data[3] & 255)) == data.length);
        byte[] header = srgb.getData(ICC_Profile.icSigHead);
        check("header tag is 128 bytes", header.length == 128 && header[36] == 'a');
        check("getMajorVersion >= 2", srgb.getMajorVersion() >= 2);
        check("description tag present", srgb.getData(ICC_Profile.icSigProfileDescriptionTag) != null);
        check("missing tag is null", srgb.getData(0x7a7a7a7a) == null);
        ICC_Profile copy = ICC_Profile.getInstance(data);
        check("getInstance(getData()) is an RGB profile", copy instanceof ICC_ProfileRGB
            && copy.getColorSpaceType() == ColorSpace.TYPE_RGB);
        ICC_ColorSpace copySpace = new ICC_ColorSpace(copy);
        near("copied profile converts like sRGB", ColorSpace.getInstance(ColorSpace.CS_sRGB).toCIEXYZ(SAMPLES[6]),
            copySpace.toCIEXYZ(SAMPLES[6]), 1.0 / 255);
        float[][] m = ((ICC_ProfileRGB) srgb).getMatrix();
        boolean matrix = true;
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            if (Math.abs(m[i][j] - SRGB_TO_XYZ[i][j]) > 2e-3) matrix = false;
        }
        check("ICC_ProfileRGB.getMatrix is the D50 sRGB colorants", matrix);
        boolean threw = false;
        try {
            ICC_Profile.getInstance(new byte[200]);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check("garbage profile data throws IAE", threw);
        threw = false;
        try {
            ((ICC_ProfileRGB) srgb).getGamma(ICC_ProfileRGB.REDCOMPONENT);
        } catch (ProfileDataException expected) {
            threw = true;
        }
        check("sRGB TRC is a table: getGamma throws ProfileDataException", threw);
        short[] trc = ((ICC_ProfileRGB) srgb).getTRC(ICC_ProfileRGB.GREENCOMPONENT);
        check("sRGB TRC table ends at 0 and 0xffff", trc.length > 2 && trc[0] == 0 && (trc[trc.length - 1] & 0xffff) == 0xffff);
        check("CMMException and ProfileDataException are RuntimeExceptions",
            new CMMException("x") instanceof RuntimeException && new ProfileDataException("x") instanceof RuntimeException);
    }

    private static void colorMembers() {
        double tol = 1.0 / 255;
        ColorSpace xyz = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
        ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        Color c = new Color(0.2f, 0.6f, 0.9f, 0.5f);
        near("float Color keeps its components", new float[] {0.2f, 0.6f, 0.9f, 0.5f}, c.getComponents(null), 0);
        near("Color.getColorComponents(CIEXYZ)", xyz.fromRGB(new float[] {0.2f, 0.6f, 0.9f}),
            c.getColorComponents(xyz, null), tol);
        float[] gc = c.getComponents(gray, null);
        check("Color.getComponents(GRAY) has alpha last", gc.length == 2 && gc[1] == 0.5f);
        // 0.4, not 0.5: encoded(0.5) * 255 = 187.52 sits on a rounding edge, where the few
        // 2^-16 by which the sRGB.pf colorants miss D50 decide the channel.
        Color g = new Color(gray, new float[] {0.4f}, 1f);
        check("Color(ColorSpace,...) keeps its space", g.getColorSpace() == gray && g.getColorComponents(null).length == 1);
        int e = (int) (encoded(0.4) * 255 + 0.5);
        check("Color(GRAY 0.4) packs the re-encoded sRGB value " + Integer.toHexString(g.getRGB()),
            Math.abs(g.getRed() - e) <= 1 && Math.abs(g.getGreen() - e) <= 1 && Math.abs(g.getBlue() - e) <= 1
            && g.getAlpha() == 255);
        check("int Color is in sRGB", new Color(10, 20, 30).getColorSpace().isCS_sRGB());
    }

    private static BufferedImage fixture() {
        int w = 16, h = 16;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        long seed = 0x9E3779B97F4A7C15L;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int rgb = (int) (seed >>> 40) & 0xffffff;
            if (y == 0) rgb = (x * 17) * 0x010101; // a neutral ramp
            image.setRGB(x, y, rgb);
        }
        return image;
    }

    private static void convertImages() {
        BufferedImage src = fixture();
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp toGray = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        BufferedImage out = toGray.filter(src, dst);
        check("filter returns the destination", out == dst);
        int bad = 0;
        String first = null;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int expected = modelGray(src.getRGB(x, y)), actual = dst.getRaster().getSample(x, y, 0);
            if (Math.abs(expected - actual) > 2) {
                bad++;
                if (first == null) first = String.format("(%d,%d) rgb %06x expected %d got %d", x, y, src.getRGB(x, y) & 0xffffff, expected, actual);
            }
        }
        check("ColorConvertOp INT_RGB -> BYTE_GRAY is linear luminance" + (bad == 0 ? "" : " [" + bad + " differ, first " + first + "]"), bad == 0);
        check("sRGB mid-gray 128 converts to ~55", Math.abs(modelGray(0x808080) - 55) <= 1);

        BufferedImage back = new ColorConvertOp(null).filter(dst, new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB));
        bad = 0;
        first = null;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int g = dst.getRaster().getSample(x, y, 0);
            int expected = (int) Math.round(encoded(g / 255.0) * 255), actual = back.getRGB(x, y);
            for (int shift = 0; shift <= 16; shift += 8) {
                if (Math.abs((actual >> shift & 255) - expected) > 2) {
                    bad++;
                    if (first == null) first = String.format("(%d,%d) gray %d expected %d got %06x", x, y, g, expected, actual & 0xffffff);
                    break;
                }
            }
        }
        check("ColorConvertOp BYTE_GRAY -> INT_RGB re-encodes" + (bad == 0 ? "" : " [" + bad + " differ, first " + first + "]"), bad == 0);

        BufferedImage auto = toGray.filter(src, null);
        check("filter(src, null) creates a 1-band GRAY image", auto.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY
            && auto.getRaster().getNumBands() == 1 && auto.getWidth() == w);
        check("ColorConvertOp.getRenderingHints null", toGray.getRenderingHints() == null);
    }

    private static void convertRasters() {
        BufferedImage img = fixture();
        int w = img.getWidth(), h = img.getHeight();
        WritableRaster src = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, w, h, 3, null);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int rgb = img.getRGB(x, y);
            src.setSample(x, y, 0, rgb >> 16 & 255);
            src.setSample(x, y, 1, rgb >> 8 & 255);
            src.setSample(x, y, 2, rgb & 255);
        }
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB),
            ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        WritableRaster gray = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, w, h, 1, null);
        op.filter(src, gray);
        int bad = 0;
        String first = null;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int expected = modelGray(img.getRGB(x, y)), actual = gray.getSample(x, y, 0);
            if (Math.abs(expected - actual) > 2) {
                bad++;
                if (first == null) first = String.format("(%d,%d) expected %d got %d", x, y, expected, actual);
            }
        }
        check("ColorConvertOp Raster sRGB -> GRAY" + (bad == 0 ? "" : " [" + bad + " differ, first " + first + "]"), bad == 0);

        ColorConvertOp toLinear = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB),
            ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB), null);
        WritableRaster lin = toLinear.filter(src, null);
        bad = 0;
        first = null;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) for (int b = 0; b < 3; b++) {
            int expected = (int) Math.round(linear(src.getSample(x, y, b) / 255.0) * 255), actual = lin.getSample(x, y, b);
            if (Math.abs(expected - actual) > 2) {
                bad++;
                if (first == null) first = String.format("(%d,%d,%d) expected %d got %d", x, y, b, expected, actual);
            }
        }
        check("ColorConvertOp Raster sRGB -> LINEAR_RGB" + (bad == 0 ? "" : " [" + bad + " differ, first " + first + "]"), bad == 0);
    }
}
