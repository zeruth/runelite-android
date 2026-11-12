// host-contract: patched-args=shadow
import java.awt.Component;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.AreaAveragingScaleFilter;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.CropImageFilter;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.MemoryImageSource;
import java.awt.image.PixelGrabber;
import java.awt.image.RGBImageFilter;
import java.awt.image.ReplicateScaleFilter;

/**
 * The image producer/consumer pipeline: BufferedImage.getSource through the crop, RGB,
 * replicate and area-averaging filters into a PixelGrabber, Image.getScaledInstance, and
 * MediaTracker over produced images. Every expectation is an independent model of the JDK
 * algorithm (ReplicateScaleFilter's source maps, AreaAveragingScaleFilter's alpha-weighted
 * box average), so the same assertions hold on the real JDK and on the shadow.
 *
 * <p>The host never compiles the shadow Toolkit (it names android.*), so the patched run still
 * has the JDK's HeadlessToolkit, whose ToolkitImage/ImageRepresentation cast the shadow's rasters
 * to JDK internals. Scaled images are therefore read through getSource() only, and with
 * {@code shadow} produced images and the tracker's target are built the way the shadow
 * Toolkit's createImage/checkImage/prepareImage build them.
 */
public final class TestImageProducerContracts {
    private static int failures;
    private static int total;
    private static boolean shadow;
    private static final String PRODUCER_IMAGE = "java.awt.image.ProducerImage";

    /** Toolkit.createImage(ImageProducer); on the shadow, what its Toolkit returns. */
    private static Image createImage(ImageProducer producer) throws Exception {
        if (!shadow) {
            return Toolkit.getDefaultToolkit().createImage(producer);
        }
        // Reflective so the plain-JDK run never links the shadow-only class.
        return (Image) Class.forName(PRODUCER_IMAGE).getConstructor(ImageProducer.class).newInstance(producer);
    }

    private static Object call(Image image, String method, ImageObserver observer) {
        try {
            return image.getClass().getMethod(method, ImageObserver.class).invoke(image, observer);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(method, e);
        }
    }

    /** A MediaTracker target; on the shadow, one whose image checks are the shadow Toolkit's. */
    private static Component target() {
        if (!shadow) {
            return new Component() { };
        }
        return new Component() {
            @Override
            public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
                if (width == 0 || height == 0 || !image.getClass().getName().equals(PRODUCER_IMAGE)) {
                    return true;
                }
                return (Boolean) call(image, "prepare", observer);
            }

            @Override
            public int checkImage(Image image, int width, int height, ImageObserver observer) {
                if (!image.getClass().getName().equals(PRODUCER_IMAGE)) {
                    return ImageObserver.ALLBITS;
                }
                int flags = (Integer) call(image, "check", observer);
                return (width == 0 || height == 0) ? flags | ImageObserver.ALLBITS : flags;
            }
        };
    }

    /** The size an image's producer delivers, without asking the image (no ToolkitImage watcher). */
    private static int[] sizeOf(Image image) throws InterruptedException {
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, -1, -1, true);
        grabber.grabPixels(5000);
        return new int[] {grabber.getWidth(), grabber.getHeight()};
    }

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    private static final int SW = 7;
    private static final int SH = 5;

    private static BufferedImage fixture() {
        BufferedImage image = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SH; y++) {
            for (int x = 0; x < SW; x++) {
                // Alpha stays >= 128 so un-premultiplying cannot amplify rounding past 1/255.
                int a = 128 + (x * 37 + y * 11) % 128;
                int r = (x * 41 + y * 7) & 0xff;
                int g = (x * 13 + y * 59) & 0xff;
                int b = (x * 97 + y * 31) & 0xff;
                image.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
            }
        }
        return image;
    }

    private static int[] grab(ImageProducer producer, int w, int h) throws InterruptedException {
        int[] pixels = new int[w * h];
        PixelGrabber grabber = new PixelGrabber(producer, 0, 0, w, h, pixels, 0, w);
        boolean ok = grabber.grabPixels(5000);
        // FRAMEBITS or ALLBITS, as grabPixels itself tests: OffScreenImageSource sends
        // SINGLEFRAMEDONE first, the grabber detaches on it, and STATICIMAGEDONE never arrives.
        check("grab " + w + "x" + h + " completes",
            ok && (grabber.getStatus() & (ImageObserver.FRAMEBITS | ImageObserver.ALLBITS)) != 0);
        return pixels;
    }

    private static boolean within(int expected, int actual, int tolerance) {
        for (int shift = 0; shift < 32; shift += 8) {
            if (Math.abs((expected >>> shift & 0xff) - (actual >>> shift & 0xff)) > tolerance) return false;
        }
        return true;
    }

    private static void compare(String name, int[] expected, int[] actual, int tolerance) {
        int bad = 0;
        String first = null;
        for (int i = 0; i < expected.length; i++) {
            if (!within(expected[i], actual[i], tolerance)) {
                if (first == null) first = String.format("#%d expected %08x got %08x", i, expected[i], actual[i]);
                bad++;
            }
        }
        check(name + (bad == 0 ? "" : " (" + bad + " off, " + first + ")"), bad == 0);
    }

    private static int[] replicateModel(BufferedImage src, int dw, int dh) {
        int[] out = new int[dw * dh];
        for (int dy = 0; dy < dh; dy++) {
            int sy = (2 * dy * SH + SH) / (2 * dh);
            for (int dx = 0; dx < dw; dx++) {
                int sx = (2 * dx * SW + SW) / (2 * dw);
                out[dy * dw + dx] = src.getRGB(sx, sy);
            }
        }
        return out;
    }

    /** Box average with exact fractional coverage, colour weighted by alpha. */
    private static int[] areaModel(BufferedImage src, int dw, int dh) {
        int[] out = new int[dw * dh];
        for (int dy = 0; dy < dh; dy++) {
            double y0 = (double) dy * SH / dh, y1 = (double) (dy + 1) * SH / dh;
            for (int dx = 0; dx < dw; dx++) {
                double x0 = (double) dx * SW / dw, x1 = (double) (dx + 1) * SW / dw;
                double sa = 0, sr = 0, sg = 0, sb = 0, area = 0;
                for (int sy = (int) Math.floor(y0); sy < Math.ceil(y1); sy++) {
                    double cy = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) Math.floor(x0); sx < Math.ceil(x1); sx++) {
                        double weight = cy * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                        int argb = src.getRGB(sx, sy);
                        double a = argb >>> 24;
                        sa += weight * a;
                        sr += weight * a * (argb >> 16 & 0xff);
                        sg += weight * a * (argb >> 8 & 0xff);
                        sb += weight * a * (argb & 0xff);
                        area += weight;
                    }
                }
                int a = (int) Math.round(sa / area);
                int r = (int) Math.round(sr / sa);
                int g = (int) Math.round(sg / sa);
                int b = (int) Math.round(sb / sa);
                out[dy * dw + dx] = a << 24 | r << 16 | g << 8 | b;
            }
        }
        return out;
    }

    private static final class SwapRedBlue extends RGBImageFilter {
        SwapRedBlue() {
            canFilterIndexColorModel = true;
        }

        @Override
        public int filterRGB(int x, int y, int rgb) {
            return (rgb & 0xff00ff00) | (rgb & 0xff0000) >> 16 | (rgb & 0xff) << 16;
        }
    }

    private static int[] pixelsOf(Image image, int w, int h) throws InterruptedException {
        return grab(image.getSource(), w, h);
    }

    private static int[] all(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    public static void main(String[] args) throws Exception {
        shadow = java.util.Arrays.asList(args).contains("shadow");
        BufferedImage src = fixture();

        compare("BufferedImage.getSource -> PixelGrabber", all(src), grab(src.getSource(), SW, SH), 0);

        int[] crop = new int[4 * 3];
        for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++) crop[y * 4 + x] = src.getRGB(1 + x, 1 + y);
        compare("CropImageFilter(1,1,4,3)",
            crop, grab(new FilteredImageSource(src.getSource(), new CropImageFilter(1, 1, 4, 3)), 4, 3), 0);

        int[] swapped = all(src);
        SwapRedBlue model = new SwapRedBlue();
        for (int i = 0; i < swapped.length; i++) swapped[i] = model.filterRGB(0, 0, swapped[i]);
        compare("RGBImageFilter red/blue swap",
            swapped, grab(new FilteredImageSource(src.getSource(), new SwapRedBlue()), SW, SH), 0);

        compare("ReplicateScaleFilter up 13x9", replicateModel(src, 13, 9),
            grab(new FilteredImageSource(src.getSource(), new ReplicateScaleFilter(13, 9)), 13, 9), 0);
        compare("ReplicateScaleFilter down 3x2", replicateModel(src, 3, 2),
            grab(new FilteredImageSource(src.getSource(), new ReplicateScaleFilter(3, 2)), 3, 2), 0);

        compare("AreaAveragingScaleFilter down 3x2", areaModel(src, 3, 2),
            grab(new FilteredImageSource(src.getSource(), new AreaAveragingScaleFilter(3, 2)), 3, 2), 1);
        compare("AreaAveragingScaleFilter up 10x8", areaModel(src, 10, 8),
            grab(new FilteredImageSource(src.getSource(), new AreaAveragingScaleFilter(10, 8)), 10, 8), 1);

        // Filters chained: crop then scale, the order getScaledInstance callers build by hand.
        ImageProducer cropped = new FilteredImageSource(src.getSource(), new CropImageFilter(0, 0, SW, SH));
        compare("crop then replicate", replicateModel(src, 14, 10),
            grab(new FilteredImageSource(cropped, new ReplicateScaleFilter(14, 10)), 14, 10), 0);

        Image smooth = src.getScaledInstance(3, 2, Image.SCALE_SMOOTH);
        int[] smoothSize = sizeOf(smooth);
        check("SCALE_SMOOTH width", smoothSize[0] == 3);
        check("SCALE_SMOOTH height", smoothSize[1] == 2);
        compare("getScaledInstance SCALE_SMOOTH = area average", areaModel(src, 3, 2), pixelsOf(smooth, 3, 2), 1);

        Image fast = src.getScaledInstance(13, 9, Image.SCALE_FAST);
        compare("getScaledInstance SCALE_FAST = replicate", replicateModel(src, 13, 9), pixelsOf(fast, 13, 9), 0);

        // -1 keeps the aspect ratio with the filter's integer arithmetic: 7*4/5 = 5.
        Image aspect = src.getScaledInstance(-1, 4, Image.SCALE_DEFAULT);
        check("getScaledInstance(-1, 4) width", sizeOf(aspect)[0] == SW * 4 / SH);

        PixelGrabber forced = new PixelGrabber(smooth, 0, 0, -1, -1, true);
        check("PixelGrabber(-1,-1,forceRGB) grabs", forced.grabPixels(5000));
        check("PixelGrabber(-1,-1) size", forced.getWidth() == 3 && forced.getHeight() == 2);
        check("PixelGrabber forceRGB model", forced.getColorModel() == ColorModel.getRGBdefault());
        compare("PixelGrabber forceRGB pixels", areaModel(src, 3, 2), (int[]) forced.getPixels(), 1);

        int[] memory = all(src);
        Image produced = createImage(new MemoryImageSource(SW, SH, memory, 0, SW));
        Component target = target();
        MediaTracker tracker = new MediaTracker(target);
        tracker.addImage(produced, 1);
        tracker.addImage(src, 2);
        check("MediaTracker.waitForAll", tracker.waitForAll(5000));
        check("MediaTracker.checkAll", tracker.checkAll());
        check("MediaTracker.statusAll COMPLETE", tracker.statusAll(false) == MediaTracker.COMPLETE);
        check("MediaTracker.isErrorAny false", !tracker.isErrorAny() && tracker.getErrorsAny() == null);
        check("produced image ALLBITS",
            (target.checkImage(produced, -1, -1, null) & ImageObserver.ALLBITS) != 0);
        compare("produced image pixels", memory, pixelsOf(produced, SW, SH), 0);

        Image broken = createImage(new ImageProducer() {
            public void addConsumer(ImageConsumer ic) { }
            public boolean isConsumer(ImageConsumer ic) { return false; }
            public void removeConsumer(ImageConsumer ic) { }
            public void startProduction(ImageConsumer ic) { ic.imageComplete(ImageConsumer.IMAGEERROR); }
            public void requestTopDownLeftRightResend(ImageConsumer ic) { }
        });
        tracker.addImage(broken, 3);
        check("MediaTracker.waitForID(errored) returns", !tracker.waitForID(3, 5000));
        check("MediaTracker.isErrorID", tracker.isErrorID(3) && !tracker.isErrorID(1));
        Object[] errors = tracker.getErrorsID(3);
        check("MediaTracker.getErrorsID", errors != null && errors.length == 1 && errors[0] == broken);
        check("MediaTracker.statusID ERRORED", (tracker.statusID(3, false) & MediaTracker.ERRORED) != 0);
        tracker.removeImage(broken);
        check("MediaTracker.removeImage clears error", !tracker.isErrorAny());

        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }
}
