package rs117.hd;

import java.awt.image.BufferedImage;
import java.nio.IntBuffer;

/** HD texture resampling without the Android AWT shim's incomplete affine image operation. */
public final class AndroidTextureScaler {
    private AndroidTextureScaler() {}

    public static void uploadPixels(BufferedImage image, int width, int height, boolean flipX, IntBuffer out) {
        int sw = image.getWidth(), sh = image.getHeight();
        int[] pixels = image.getRGB(0, 0, sw, sh, null, 0, sw);
        out.clear();
        if (sw == width && sh == height) {
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    out.put(rgba(pixels[y * sw + (flipX ? sw - 1 - x : x)]));
        } else {
            int[] xs = new int[width * 4], ys = new int[height * 4];
            float[] xw = new float[xs.length], yw = new float[ys.length];
            samples(sw, width, flipX, xs, xw);
            samples(sh, height, false, ys, yw);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    float a = 0, r = 0, g = 0, b = 0;
                    for (int j = 0; j < 4; j++) {
                        int row = ys[y * 4 + j] * sw;
                        for (int i = 0; i < 4; i++) {
                            int pixel = pixels[row + xs[x * 4 + i]];
                            float weight = xw[x * 4 + i] * yw[y * 4 + j];
                            a += (pixel >>> 24) * weight;
                            r += ((pixel >>> 16) & 255) * weight;
                            g += ((pixel >>> 8) & 255) * weight;
                            b += (pixel & 255) * weight;
                        }
                    }
                    out.put(channel(a) << 24 | channel(b) << 16 | channel(g) << 8 | channel(r));
                }
            }
        }
        out.flip();
    }

    private static int rgba(int argb) {
        return (argb & 0xff00ff00) | ((argb >>> 16) & 255) | ((argb & 255) << 16);
    }

    private static int channel(float value) { return Math.max(0, Math.min(255, Math.round(value))); }

    private static void samples(int sourceSize, int destSize, boolean flip, int[] indices, float[] weights) {
        for (int n = 0; n < destSize; n++) {
            double position = (n + .5) * sourceSize / destSize - .5;
            if (flip) position = sourceSize - 1 - position;
            int start = (int) Math.floor(position) - 1;
            for (int k = 0; k < 4; k++) {
                indices[n * 4 + k] = Math.max(0, Math.min(sourceSize - 1, start + k));
                weights[n * 4 + k] = cubic((float) Math.abs(position - (start + k)));
            }
        }
    }

    private static float cubic(float x) {
        if (x <= 1) return (1.5f * x - 2.5f) * x * x + 1;
        if (x < 2) return ((-.5f * x + 2.5f) * x - 4) * x + 2;
        return 0;
    }
}
