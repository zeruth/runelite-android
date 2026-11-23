import java.awt.Rectangle;
import java.util.Arrays;
import net.runelite.client.plugins.mobile.InventoryScaleGeometry;

public final class VerifyInventoryScale {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (int percent : new int[]{100, 125, 150, 200}) {
            for (Rectangle source : new Rectangle[]{new Rectangle(200, 350, 80, 140), new Rectangle(0, 0, 80, 140), new Rectangle(100, 100, 80, 140)}) {
                InventoryScaleGeometry g = new InventoryScaleGeometry(source, percent, 320, 600);
                check(g.isIdentity() == (percent == 100), "isIdentity mismatch for percent=" + percent);
                check(new Rectangle(0, 0, 320, 600).contains(g.destination), "offscreen destination");
                check(g.destination.contains(source), "original panel must be covered");
                int[] pixels = new int[320 * 600];
                for (int i = 0; i < pixels.length; i++) pixels[i] = i + 1;
                int[] original = pixels.clone();
                g.composite(pixels, new int[source.width * source.height]);
                for (int y = 0; y < 600; y++) for (int x = 0; x < 320; x++) {
                    int index = y * 320 + x;
                    if (g.destination.contains(x, y)) {
                        check(source.contains(g.sourceX(x), g.sourceY(y)), "input escaped inventory");
                        check(pixels[index] == original[g.sourceY(y) * 320 + g.sourceX(x)], "display/input mismatch");
                    } else check(pixels[index] == original[index], "unrelated UI changed");
                }
                if (percent == 100) check(Arrays.equals(pixels, original), "100% must be identity");
            }
        }
        InventoryScaleGeometry small = new InventoryScaleGeometry(new Rectangle(5, 5, 80, 140), 200, 100, 150);
        check(new Rectangle(0, 0, 100, 150).contains(small.destination), "small viewport overflow");
        // A later panel's source overlaps an earlier panel's enlarged destination.
        // Capturing every source first prevents that later panel sampling scaled pixels.
        InventoryScaleGeometry[] panels = {
            new InventoryScaleGeometry(new Rectangle(60, 60, 30, 30), 200, 100, 100),
            new InventoryScaleGeometry(new Rectangle(35, 35, 20, 20), 150, 100, 100)
        };
        int[] pixels = new int[10000];
        for (int i = 0; i < pixels.length; i++) pixels[i] = i + 1;
        int[] original = pixels.clone();
        int[][] snapshots = {new int[900], new int[400]};
        for (int i = 0; i < 2; i++) panels[i].capture(pixels, snapshots[i]);
        for (InventoryScaleGeometry g : panels) g.clearSource(pixels);
        for (int i = 0; i < 2; i++) panels[i].paint(pixels, snapshots[i]);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) {
            int expected = original[y * 100 + x];
            for (InventoryScaleGeometry g : panels)
                if (g.destination.contains(x, y)) expected = original[g.sourceY(y) * 100 + g.sourceX(x)];
            check(pixels[y * 100 + x] == expected, "overlapping panels contaminated their pixel sources");
        }
        // Mixed overlap: enlarged panel 0 + 100% panel 1 intersecting panel 0's destination.
        // Panel 1's source must be captured before panel 0 paints and repainted on top.
        InventoryScaleGeometry[] mixed = {
            new InventoryScaleGeometry(new Rectangle(60, 60, 30, 30), 150, 100, 100),
            new InventoryScaleGeometry(new Rectangle(40, 40, 20, 20), 100, 100, 100)
        };
        check(!mixed[0].isIdentity() && mixed[1].isIdentity(), "mixed identity preconditions");
        check(mixed[1].source.intersects(mixed[0].destination), "mixed overlap precondition");
        Arrays.setAll(pixels, i -> i + 1);
        int[] mixedOrig = pixels.clone();
        int[][] mixedSnap = {new int[900], new int[400]};
        for (int i = 0; i < 2; i++) mixed[i].capture(pixels, mixedSnap[i]);
        mixed[0].clearSource(pixels);
        for (int i = 0; i < 2; i++) mixed[i].paint(pixels, mixedSnap[i]);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) {
            int expected = mixedOrig[y * 100 + x];
            for (InventoryScaleGeometry g : mixed)
                if (g.destination.contains(x, y)) expected = mixedOrig[g.sourceY(y) * 100 + g.sourceX(x)];
            check(pixels[y * 100 + x] == expected, "mixed-overlap mismatch at (" + x + "," + y + ")");
        }
        System.out.println("PASS: 12 scale/anchor combinations, full pixel/input agreement, unrelated UI preservation, 100% identity small viewport fit, mixed overlap");
    }
}
