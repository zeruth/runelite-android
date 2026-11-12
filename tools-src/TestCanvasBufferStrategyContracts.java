import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.ImageCapabilities;
import java.awt.image.BufferStrategy;

/**
 * Canvas.createBufferStrategy over the backbuffer the Compose host displays: no strategy
 * until one is created, the JDK's argument checks, an AWTException for page flipping
 * (never available here), a blit strategy whose frame reaches the backbuffer only on
 * show(), a single-buffered strategy drawing straight into it, and dispose() clearing the
 * strategy. A device probe (the swing-ui-probe "canvas" command), not a host contract:
 * the shadow Canvas reaches the JNI rasterizer through BufferedImageGraphics2D, so the
 * host runner never patches it, and the JDK's headless Canvas has no peer to create a
 * strategy on.
 */
public final class TestCanvasBufferStrategyContracts {
    private final StringBuilder report = new StringBuilder();
    private int failures;
    private int total;

    private void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        report.append(actual ? "ok   " : "FAIL ").append(name).append('\n');
    }

    private static void fill(BufferStrategy strategy, Color color) {
        Graphics g = strategy.getDrawGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, 4, 3);
        } finally {
            g.dispose();
        }
    }

    private static int rgb(Canvas canvas, int x, int y) {
        return canvas.getBackbuffer().getRGB(x, y) & 0xffffff;
    }

    /** Runs every check; returns the report, or throws AssertionError carrying it. */
    public static String run() {
        TestCanvasBufferStrategyContracts test = new TestCanvasBufferStrategyContracts();
        test.checks();
        test.report.append(test.total - test.failures).append('/').append(test.total).append(" passed\n");
        if (test.failures != 0) {
            throw new AssertionError(test.report.toString());
        }
        return test.report.toString();
    }

    public static void main(String[] args) {
        System.out.print(run());
    }

    private void checks() {
        Canvas canvas = new Canvas();
        canvas.setSize(4, 3);
        check("no strategy before one is created", canvas.getBufferStrategy() == null);

        boolean threw = false;
        try {
            canvas.createBufferStrategy(0);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check("createBufferStrategy(0) throws IllegalArgumentException", threw);

        threw = false;
        try {
            canvas.createBufferStrategy(2, null);
        } catch (IllegalArgumentException expected) {
            threw = true;
        } catch (AWTException unexpected) {
            threw = false;
        }
        check("null capabilities throw IllegalArgumentException", threw);

        ImageCapabilities unaccelerated = new ImageCapabilities(false);
        threw = false;
        try {
            canvas.createBufferStrategy(2, new BufferCapabilities(unaccelerated, unaccelerated,
                BufferCapabilities.FlipContents.UNDEFINED));
        } catch (AWTException expected) {
            threw = true;
        }
        check("page flipping throws AWTException", threw);
        check("a failed request creates no strategy", canvas.getBufferStrategy() == null);

        canvas.createBufferStrategy(2);
        BufferStrategy blit = canvas.getBufferStrategy();
        check("createBufferStrategy(2) installs a strategy", blit != null);
        if (blit == null) {
            return;
        }
        check("the strategy is not page flipping", !blit.getCapabilities().isPageFlipping());
        check("a fresh strategy has its contents", !blit.contentsLost() && !blit.contentsRestored());

        fill(blit, Color.RED);
        check("drawing stays in the back buffer until show", rgb(canvas, 1, 1) != 0xff0000);
        blit.show();
        check("show copies the frame into the backbuffer",
            rgb(canvas, 1, 1) == 0xff0000 && rgb(canvas, 3, 2) == 0xff0000);

        canvas.setSize(5, 3);
        fill(blit, Color.GREEN);
        check("a resized canvas reports the back buffer restored", blit.contentsRestored());
        blit.show();
        check("the resized frame reaches the backbuffer", rgb(canvas, 2, 2) == 0x00ff00);

        canvas.createBufferStrategy(1);
        BufferStrategy single = canvas.getBufferStrategy();
        check("a new strategy replaces the old one", single != null && single != blit);
        if (single == null) {
            return;
        }
        fill(single, Color.BLUE);
        check("a single-buffered strategy draws straight into the backbuffer", rgb(canvas, 2, 1) == 0x0000ff);

        single.dispose();
        check("dispose clears the strategy", canvas.getBufferStrategy() == null);
    }
}
