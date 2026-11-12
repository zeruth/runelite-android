// host-contract: runs=patched
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import net.runelite.awt.impl.BufferedBackedVolatileImage;

/**
 * VolatileImage contracts of BufferedBackedVolatileImage, the Android VolatileImage
 * (createCompatibleVolatileImage and the Canvas blit strategy's back buffer): flush loses
 * the contents until validate restores them, validate reports IMAGE_OK otherwise, and
 * getSnapshot is a copy that neither side's later writes reach. Patched only: the class
 * is shadow-only, and the JDK hands out no VolatileImage headless. Pixels go through
 * backing() because the JDK's surface pipeline cannot draw into a shadow raster here.
 */
public final class TestVolatileImageContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "ok   " : "FAIL ") + name);
    }

    public static void main(String[] args) {
        BufferedBackedVolatileImage image = new BufferedBackedVolatileImage(4, 3, Transparency.OPAQUE);
        check("reports its size", image.getWidth() == 4 && image.getHeight() == 3
            && image.getWidth(null) == 4 && image.getHeight(null) == 3);
        check("keeps its transparency", image.getTransparency() == Transparency.OPAQUE);
        check("is not accelerated", !image.getCapabilities().isAccelerated());
        check("a fresh image has its contents", !image.contentsLost());
        check("validate on a fresh image is IMAGE_OK", image.validate(null) == VolatileImage.IMAGE_OK);

        image.backing().setRGB(1, 2, 0xff00ff00);
        BufferedImage snapshot = image.getSnapshot();
        check("getSnapshot is a separate image", snapshot != image.backing());
        check("getSnapshot copies the pixels", snapshot.getRGB(1, 2) == 0xff00ff00
            && snapshot.getWidth() == 4 && snapshot.getHeight() == 3);
        check("an opaque image snapshots as TYPE_INT_RGB", snapshot.getType() == BufferedImage.TYPE_INT_RGB);
        image.backing().setRGB(1, 2, 0xff0000ff);
        check("later writes to the image miss the snapshot", snapshot.getRGB(1, 2) == 0xff00ff00);
        snapshot.setRGB(0, 0, 0xffff0000);
        check("writes to the snapshot miss the image", image.backing().getRGB(0, 0) == 0xff000000);

        image.flush();
        check("flush loses the contents", image.contentsLost());
        image.backing();
        check("touching the raster does not restore the contents", image.contentsLost());
        image.flush();
        check("validate after flush is IMAGE_RESTORED", image.validate(null) == VolatileImage.IMAGE_RESTORED);
        check("validate clears contentsLost", !image.contentsLost());
        check("the next validate is IMAGE_OK", image.validate(null) == VolatileImage.IMAGE_OK);
        check("the restored raster keeps the size", image.backing().getWidth() == 4 && image.backing().getHeight() == 3);

        BufferedBackedVolatileImage translucent = new BufferedBackedVolatileImage(2, 2, Transparency.TRANSLUCENT);
        check("a translucent image keeps alpha", translucent.getSnapshot().getType() == BufferedImage.TYPE_INT_ARGB
            && translucent.getTransparency() == Transparency.TRANSLUCENT);

        check("getProperty is UndefinedProperty", image.getProperty("comment", null) == Image.UndefinedProperty);
        boolean threw = false;
        try {
            image.getProperty(null, null);
        } catch (NullPointerException expected) {
            threw = true;
        }
        check("getProperty(null) throws NullPointerException", threw);

        System.out.println(total - failures + "/" + total + " passed");
        if (failures != 0) System.exit(1);
    }
}
