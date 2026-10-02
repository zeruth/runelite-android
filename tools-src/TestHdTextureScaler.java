import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import rs117.hd.AndroidTextureScaler;

public final class TestHdTextureScaler {
    public static void main(String[] args) {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = {0xff102030, 0x80405060, 0x00708090, 0xffa0b0c0, 0xffd0e0f0, 0xff010203};
        image.setRGB(0, 0, 3, 2, pixels, 0, 3);
        IntBuffer out = IntBuffer.allocate(4096);
        AndroidTextureScaler.uploadPixels(image, 3, 2, true, out);
        if (out.remaining() != 6 || out.get(0) != 0x00908070 || out.get(2) != 0xff302010 || out.get(3) != 0xff030201)
            throw new AssertionError("Mirror or RGBA byte order incorrect");
        // Reuse the destination after an opaque image: transparent pixels must replace it completely.
        BufferedImage clear = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        AndroidTextureScaler.uploadPixels(clear, 6, 4, true, out);
        while (out.hasRemaining()) if (out.get() != 0) throw new AssertionError("Stale destination pixels");
        // Compare scaling and mirroring against the real desktop AWT bicubic operation.
        BufferedImage gradient = new BufferedImage(7, 5, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 5; y++) for (int x = 0; x < 7; x++)
            gradient.setRGB(x, y, 0xff000000 | (x * 30) << 16 | (y * 40) << 8 | (x * 15 + y * 10));
        for (boolean flip : new boolean[]{false, true}) {
            for (int[] dimensions : new int[][]{{14, 10}, {3, 2}, {9, 13}}) {
                int w = dimensions[0], h = dimensions[1];
                BufferedImage reference = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                AffineTransform transform = new AffineTransform();
                if (flip) { transform.translate(w, 0); transform.scale(-1, 1); }
                transform.scale(w / 7.0, h / 5.0);
                new AffineTransformOp(transform, AffineTransformOp.TYPE_BICUBIC).filter(gradient, reference);
                AndroidTextureScaler.uploadPixels(gradient, w, h, flip, out);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int actual = out.get(y * w + x), expected = reference.getRGB(x, y);
                    for (int c = 0; c < 3; c++)
                        if (Math.abs(((actual >>> (c * 8)) & 255) - ((expected >>> ((2 - c) * 8)) & 255)) > 2)
                            throw new AssertionError("Bicubic mismatch at " + x + "," + y + " flip=" + flip);
                }
            }
        }
        System.out.println("PASS: HD texture orientation, transparent replacement, RGBA order and desktop bicubic reference");
    }
}
