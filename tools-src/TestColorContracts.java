// host-contract
import java.awt.Color;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.color.ICC_ProfileGray;
import java.awt.color.ICC_ProfileRGB;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;

/**
 * java.awt.color contracts: the predefined spaces round-trip sRGB, linear RGB and D50 XYZ within
 * 1/255, the standard ICC profiles have desktop's classes, types and headers, and ColorConvertOp
 * to GRAY yields linear luminance (the JDK's GRAY.pf has gamma 1.0, so a mid-gray sRGB pixel
 * comes out dark).  The same assertions run on the JDK (LittleCMS) and on the shadow (matrix CMM).
 */
public final class TestColorContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static double linear(double v) {
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static double luminance(int rgb) {
        return 0.2225045 * linear((rgb >> 16 & 255) / 255.0) + 0.7168786 * linear((rgb >> 8 & 255) / 255.0)
            + 0.0606169 * linear((rgb & 255) / 255.0);
    }

    private static float maxDiff(float[] a, float[] b) {
        float d = 0;
        for (int i = 0; i < a.length; i++) d = Math.max(d, Math.abs(a[i] - b[i]));
        return d;
    }

    public static void main(String[] args) {
        ColorSpace srgb = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorSpace lin = ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
        ColorSpace xyz = ColorSpace.getInstance(ColorSpace.CS_CIEXYZ);
        ColorSpace gray = ColorSpace.getInstance(ColorSpace.CS_GRAY);
        ColorSpace pycc = ColorSpace.getInstance(ColorSpace.CS_PYCC);

        check("predefined spaces are singletons", srgb == ColorSpace.getInstance(ColorSpace.CS_sRGB));
        check("predefined spaces are ICC_ColorSpace", srgb instanceof ICC_ColorSpace && gray instanceof ICC_ColorSpace
              && xyz instanceof ICC_ColorSpace && pycc instanceof ICC_ColorSpace);
        check("isCS_sRGB", srgb.isCS_sRGB() && !lin.isCS_sRGB());
        check("types", srgb.getType() == ColorSpace.TYPE_RGB && gray.getType() == ColorSpace.TYPE_GRAY
              && xyz.getType() == ColorSpace.TYPE_XYZ && gray.getNumComponents() == 1 && pycc.getNumComponents() == 3);
        check("component names", "Red".equals(srgb.getName(0)) && "Gray".equals(gray.getName(0)) && "Z".equals(xyz.getName(2)));
        check("CIEXYZ range", xyz.getMinValue(0) == 0f && xyz.getMaxValue(1) == 1f + 32767f / 32768f && srgb.getMaxValue(2) == 1f);
        try {
            ColorSpace.getInstance(12345);
            check("unknown space throws", false);
        } catch (IllegalArgumentException expected) {
            check("unknown space throws", true);
        }

        float[] white = srgb.toCIEXYZ(new float[] {1, 1, 1});
        check("sRGB white is D50 " + white[0] + "," + white[1] + "," + white[2],
              maxDiff(white, new float[] {0.9642f, 1f, 0.8249f}) < 2e-3f);
        float mid = lin.fromRGB(new float[] {0.5f, 0.5f, 0.5f})[0];
        check("sRGB 0.5 is linear " + mid, Math.abs(mid - 0.21404f) < 4e-3f);

        float viaLinear = 0, viaXYZ = 0, linearXYZ = 0, viaRGB = 0;
        for (int r = 0; r < 256; r += 15) for (int g = 0; g < 256; g += 15) for (int b = 0; b < 256; b += 15) {
            float[] s = {r / 255f, g / 255f, b / 255f};
            float[] l = lin.fromCIEXYZ(srgb.toCIEXYZ(s));
            viaLinear = Math.max(viaLinear, maxDiff(s, srgb.fromCIEXYZ(lin.toCIEXYZ(l))));
            viaXYZ = Math.max(viaXYZ, maxDiff(s, xyz.toRGB(xyz.fromRGB(s))));
            linearXYZ = Math.max(linearXYZ, maxDiff(s, lin.fromCIEXYZ(lin.toCIEXYZ(s))));
            viaRGB = Math.max(viaRGB, maxDiff(s, lin.toRGB(lin.fromRGB(s))));
        }
        float limit = 1f / 255f;
        check("sRGB -> linear -> sRGB within 1/255 (max " + viaLinear + ")", viaLinear <= limit);
        check("sRGB -> XYZ -> sRGB within 1/255 (max " + viaXYZ + ")", viaXYZ <= limit);
        check("linear -> XYZ -> linear within 1/255 (max " + linearXYZ + ")", linearXYZ <= limit);
        check("linear toRGB/fromRGB within 1/255 (max " + viaRGB + ")", viaRGB <= limit);
        float[] grayOfWhite = gray.fromRGB(new float[] {1, 1, 1});
        check("gray of white is 1 (" + grayOfWhite[0] + ")", Math.abs(grayOfWhite[0] - 1f) < 2e-3f);

        ICC_Profile sp = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        check("sRGB profile is ICC_ProfileRGB", sp instanceof ICC_ProfileRGB);
        check("sRGB profile is the space's profile", ((ICC_ColorSpace) srgb).getProfile() == sp);
        check("sRGB profile class/types", sp.getProfileClass() == ICC_Profile.CLASS_DISPLAY
              && sp.getColorSpaceType() == ColorSpace.TYPE_RGB && sp.getPCSType() == ColorSpace.TYPE_XYZ
              && sp.getNumComponents() == 3);
        byte[] data = sp.getData();
        check("profile data has the acsp magic", data.length > 128 && data[36] == 'a' && data[37] == 'c'
              && data[38] == 's' && data[39] == 'p');
        check("header tag is 128 bytes", sp.getData(ICC_Profile.icSigHead).length == 128);
        check("wtpt tag present", sp.getData(ICC_Profile.icSigMediaWhitePointTag) != null);
        check("profile from its data is ICC_ProfileRGB", ICC_Profile.getInstance(data) instanceof ICC_ProfileRGB);
        float[][] matrix = ((ICC_ProfileRGB) sp).getMatrix();
        check("sRGB red Y colorant " + matrix[1][0], Math.abs(matrix[1][0] - 0.2225f) < 2e-3f);
        ICC_Profile gp = ICC_Profile.getInstance(ColorSpace.CS_GRAY);
        check("gray profile is ICC_ProfileGray with gamma 1", gp instanceof ICC_ProfileGray
              && ((ICC_ProfileGray) gp).getGamma() == 1f);
        ICC_Profile lp = ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB);
        check("linear profile gamma 1", lp instanceof ICC_ProfileRGB
              && ((ICC_ProfileRGB) lp).getGamma(ICC_ProfileRGB.GREENCOMPONENT) == 1f);
        try {
            ICC_Profile.getInstance(new byte[16]);
            check("garbage profile data throws", false);
        } catch (IllegalArgumentException expected) {
            check("garbage profile data throws", true);
        }

        int w = 16, h = 16;
        BufferedImage src = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            src.setRGB(x, y, (x * 17) << 16 | (y * 17) << 8 | ((x * 7 + y * 11) & 255));
        }
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        ColorConvertOp toGray = new ColorConvertOp(gray, null);
        toGray.filter(src, dst);
        int worst = 0;
        String first = null;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int expected = (int) Math.round(255 * luminance(src.getRGB(x, y)));
            int actual = dst.getRaster().getSample(x, y, 0);
            int d = Math.abs(expected - actual);
            if (d > worst) {
                worst = d;
                first = String.format("(%d,%d) rgb %06x expected %d got %d", x, y, src.getRGB(x, y) & 0xffffff, expected, actual);
            }
        }
        check("ColorConvertOp to GRAY is linear luminance within 2 (worst " + worst + (first == null ? "" : " at " + first) + ")",
              worst <= 2);
        BufferedImage created = toGray.filter(src, null);
        check("created GRAY destination", created.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY
              && created.getRaster().getNumBands() == 1 && created.getWidth() == w);
        check("created destination matches the given one",
              created.getRaster().getSample(5, 9, 0) == dst.getRaster().getSample(5, 9, 0));

        ComponentColorModel rgbModel = new ComponentColorModel(srgb, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster raster = rgbModel.createCompatibleWritableRaster(4, 1);
        int[] values = {0, 64, 128, 255};
        for (int x = 0; x < 4; x++) for (int b = 0; b < 3; b++) raster.setSample(x, 0, b, values[x]);
        WritableRaster linearRaster = new ColorConvertOp(srgb, lin, null).filter(raster, null);
        int rasterWorst = 0;
        for (int x = 0; x < 4; x++) {
            int expected = (int) Math.round(255 * linear(values[x] / 255.0));
            rasterWorst = Math.max(rasterWorst, Math.abs(expected - linearRaster.getSample(x, 0, 1)));
        }
        check("Raster sRGB -> linear within 2 (worst " + rasterWorst + ")", rasterWorst <= 2 && linearRaster.getNumBands() == 3);
        try {
            new ColorConvertOp(gray, null).filter(raster, null);
            check("Raster filter without a source space throws", false);
        } catch (IllegalArgumentException expected) {
            check("Raster filter without a source space throws", true);
        }

        Color grayColor = new Color(gray, new float[] {0.5f}, 1f);
        check("Color in GRAY space is sRGB-encoded (" + grayColor.getRed() + ")", Math.abs(grayColor.getRed() - 187) <= 1
              && grayColor.getColorSpace() == gray && grayColor.getColorComponents(null)[0] == 0.5f);
        float[] inGray = Color.white.getColorComponents(gray, null);
        check("white in GRAY is 1 (" + inGray[0] + ")", inGray.length == 1 && Math.abs(inGray[0] - 1f) < 2e-3f);

        System.out.println(total - failures + "/" + total + " passed");
        if (failures > 0) System.exit(1);
    }
}
