package java.awt;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;

/** Shared sampling for linear and radial gradients in user coordinates. */
public abstract class MultipleGradientPaint implements Paint {
    public enum CycleMethod { NO_CYCLE, REFLECT, REPEAT }
    public enum ColorSpaceType { SRGB, LINEAR_RGB }

    private final float[] fractions;
    private final Color[] colors;
    private final CycleMethod cycleMethod;
    private final ColorSpaceType colorSpace;
    private final AffineTransform gradientTransform;
    private final int transparency;

    protected MultipleGradientPaint(float[] fractions, Color[] colors, CycleMethod cycle,
                                    ColorSpaceType space, AffineTransform transform) {
        java.util.Objects.requireNonNull(fractions);
        java.util.Objects.requireNonNull(colors);
        cycleMethod = java.util.Objects.requireNonNull(cycle);
        colorSpace = java.util.Objects.requireNonNull(space);
        gradientTransform = new AffineTransform(java.util.Objects.requireNonNull(transform));
        if (fractions.length < 2 || fractions.length != colors.length)
            throw new IllegalArgumentException("At least two matching stops are required");
        boolean opaque = true;
        for (int i = 0; i < fractions.length; i++) {
            if (!(fractions[i] >= 0 && fractions[i] <= 1) || (i > 0 && fractions[i] <= fractions[i - 1]))
                throw new IllegalArgumentException("Fractions must increase from zero to one");
            opaque &= java.util.Objects.requireNonNull(colors[i]).getAlpha() == 255;
        }
        int first = fractions[0] == 0 ? 0 : 1;
        int last = fractions[fractions.length - 1] == 1 ? 0 : 1;
        this.fractions = new float[fractions.length + first + last];
        this.colors = new Color[this.fractions.length];
        System.arraycopy(fractions, 0, this.fractions, first, fractions.length);
        System.arraycopy(colors, 0, this.colors, first, colors.length);
        if (first != 0) { this.fractions[0] = 0; this.colors[0] = colors[0]; }
        if (last != 0) { this.fractions[this.fractions.length - 1] = 1; this.colors[this.colors.length - 1] = colors[colors.length - 1]; }
        transparency = opaque ? OPAQUE : TRANSLUCENT;
    }

    public final float[] getFractions() { return fractions.clone(); }
    public final Color[] getColors() { return colors.clone(); }
    public final CycleMethod getCycleMethod() { return cycleMethod; }
    public final ColorSpaceType getColorSpace() { return colorSpace; }
    public final AffineTransform getTransform() { return new AffineTransform(gradientTransform); }
    public final int getTransparency() { return transparency; }
    protected abstract double fraction(double x, double y);

    public final PaintContext createContext(ColorModel model, Rectangle deviceBounds, Rectangle2D userBounds,
                                             AffineTransform transform, RenderingHints hints) {
        AffineTransform combined = new AffineTransform(transform);
        combined.concatenate(gradientTransform);
        final AffineTransform inverse;
        try { inverse = combined.createInverse(); }
        catch (java.awt.geom.NoninvertibleTransformException e) { throw new IllegalArgumentException("Singular gradient transform", e); }
        return new PaintContext() {
            private BufferedImage image;
            public void dispose() { net.runelite.awt.impl.GradientRasterCache.release(image); image=null; }
            public ColorModel getColorModel() { return ColorModel.getRGBdefault(); }
            public Raster getRaster(int x, int y, int width, int height) {
                if (image == null || image.getWidth() < width || image.getHeight() < height) {
                    net.runelite.awt.impl.GradientRasterCache.release(image);
                    image=net.runelite.awt.impl.GradientRasterCache.acquire(width,height);
                }
                int[] pixels = image.backingArray();
                double m00 = inverse.getScaleX(), m01 = inverse.getShearX(), m02 = inverse.getTranslateX();
                double m10 = inverse.getShearY(), m11 = inverse.getScaleY(), m12 = inverse.getTranslateY();
                for (int row = 0; row < height; row++) {
                    double px = m00 * (x + .5) + m01 * (y + row + .5) + m02;
                    double py = m10 * (x + .5) + m11 * (y + row + .5) + m12;
                    for (int col = 0; col < width; col++, px += m00, py += m10)
                        pixels[row * image.getWidth() + col] = sample(fraction(px, py));
                }
                return image.getRaster();
            }
        };
    }

    private int sample(double position) {
        if (cycleMethod == CycleMethod.REPEAT) position -= Math.floor(position);
        else if (cycleMethod == CycleMethod.REFLECT) {
            position -= Math.floor(position / 2) * 2;
            if (position > 1) position = 2 - position;
        } else position = Math.max(0, Math.min(1, position));
        int index = 0;
        while (index < fractions.length - 2 && position > fractions[index + 1]) index++;
        double t = (position - fractions[index]) / (fractions[index + 1] - fractions[index]);
        int a = colors[index].getRGB(), b = colors[index + 1].getRGB(), result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            double first = ((a >>> shift) & 255) / 255d, second = ((b >>> shift) & 255) / 255d;
            if (shift < 24 && colorSpace == ColorSpaceType.LINEAR_RGB) { first = linear(first); second = linear(second); }
            double value = first + (second - first) * t;
            if (shift < 24 && colorSpace == ColorSpaceType.LINEAR_RGB) value = encoded(value);
            result |= Math.max(0, Math.min(255, (int) Math.round(value * 255))) << shift;
        }
        return result;
    }
    private static double linear(double value) { return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4); }
    private static double encoded(double value) { return value <= .0031308 ? value * 12.92 : 1.055 * Math.pow(value, 1 / 2.4) - .055; }
}
