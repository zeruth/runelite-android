package net.runelite.awt.impl;

import java.awt.image.BufferedImage;

/** Contexts borrow one bounded scratch raster; nested paints receive separate storage. */
public final class GradientRasterCache {
    private static final int MAX_PIXELS = 256 * 1024;
    private static final ThreadLocal<BufferedImage> CACHE = new ThreadLocal<>();
    private GradientRasterCache() {}

    public static BufferedImage acquire(int width, int height) {
        BufferedImage image = CACHE.get();
        CACHE.remove();
        if (image != null && image.getWidth() >= width && image.getHeight() >= height) return image;
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public static void release(BufferedImage image) {
        if (image == null || (long)image.getWidth() * image.getHeight() > MAX_PIXELS) return;
        BufferedImage cached = CACHE.get();
        if (cached == null || (long)cached.getWidth() * cached.getHeight() < (long)image.getWidth() * image.getHeight())
            CACHE.set(image);
    }
}
