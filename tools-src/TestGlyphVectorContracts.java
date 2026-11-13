// host-contract: patched-args=shadow
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.StringCharacterIterator;
import java.util.Arrays;

/**
 * Font.createGlyphVector / layoutGlyphVector and Graphics2D.drawGlyphVector: what hub
 * plugins (outlined overlay text, per-glyph placement) rely on, on the real JDK and the shadow.
 *
 * Only properties a different font engine still has to keep are asserted: one glyph per char,
 * advancing positions, an outline whose bounds are the visual bounds, and drawGlyphVector
 * producing drawString's pixels for an unmodified vector built from the graphics' own context.
 *
 * With {@code shadow} the host has no Android face, so {@link HostBackend} installs a
 * synthetic one (box glyphs, per-char advances) and the same assertions run
 * net.runelite.awt.impl.AndroidGlyphVector over it through the factories the shadow's
 * Font.createGlyphVector / layoutGlyphVector delegate to (see {@link ShadowGlyphs}). The pixel comparison needs BufferedImageGraphics2D, which only runs on Android;
 * there the run checks what decides it instead: an unmodified vector hands drawGlyphVector
 * its text ({@code defaultRun}), which goes down drawString's own TextCache path, and an
 * edited or right-to-left one does not.
 */
public final class TestGlyphVectorContracts {
    private static int failures;
    private static int total;
    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }
    private static boolean within(double a, double b, double tolerance) { return Math.abs(a - b) <= tolerance; }

    private static final String TEXT = "Hello, GlyphVector 123";

    public static void main(String[] args) throws Exception {
        boolean shadow = Arrays.asList(args).contains("shadow");
        // By name: on the plain JDK run the backend classes do not exist, and naming them in
        // this class would have the verifier load them.
        Glyphs factory = (Glyphs) Class.forName(shadow ? "TestGlyphVectorContracts$ShadowGlyphs" : "TestGlyphVectorContracts$JdkGlyphs")
            .getDeclaredConstructor().newInstance();
        Font font = new Font(Font.DIALOG, Font.PLAIN, 16);
        FontRenderContext frc = new FontRenderContext(null, true, true);

        // --- creation ---
        GlyphVector gv = factory.create(font, frc, TEXT);
        if (shadow) {
            // A fallback to the JDK's StandardGlyphVector would pass everything below without testing the shadow.
            check("the shadow builds an AndroidGlyphVector (" + gv.getClass().getName() + ")",
                gv.getClass().getName().equals("net.runelite.awt.impl.AndroidGlyphVector"));
        }
        check("one glyph per char", gv.getNumGlyphs() == TEXT.length());
        check("font and render context are kept", gv.getFont().equals(font) && gv.getFontRenderContext().equals(frc));
        check("char indices are the identity left to right",
            gv.getGlyphCharIndex(0) == 0 && gv.getGlyphCharIndex(TEXT.length() - 1) == TEXT.length() - 1);
        check("char[] and CharacterIterator overloads agree with String",
            gv.equals(factory.create(font, frc, TEXT.toCharArray()))
                && gv.equals(factory.create(font, frc, new StringCharacterIterator(TEXT))));
        int[] codes = gv.getGlyphCodes(0, gv.getNumGlyphs(), null);
        check("same char, same glyph code", codes[2] == codes[3] && codes[0] != codes[1]);
        check("int[] overload keeps the codes",
            java.util.Arrays.equals(factory.create(font, frc, codes).getGlyphCodes(0, codes.length, null), codes));
        boolean threw = false;
        try { gv.getGlyphCode(TEXT.length()); } catch (IndexOutOfBoundsException e) { threw = true; }
        check("getGlyphCode out of range throws IndexOutOfBoundsException", threw);

        // --- advances ---
        float[] positions = gv.getGlyphPositions(0, gv.getNumGlyphs() + 1, null);
        boolean monotonic = positions[0] == 0 && positions[1] == 0;
        for (int i = 1; i <= gv.getNumGlyphs(); i++) {
            monotonic &= positions[2 * i] > positions[2 * (i - 1)] && positions[2 * i + 1] == 0;
        }
        check("positions advance left to right along the baseline", monotonic);
        Rectangle2D logical = gv.getLogicalBounds();
        double width = factory.stringBounds(font, TEXT, frc).getWidth();
        check("logical width is the string advance within 1px (" + logical.getWidth() + " vs " + width + ")",
            within(logical.getWidth(), width, 1) && within(positions[2 * gv.getNumGlyphs()], width, 1));
        check("logical bounds span ascent above the baseline", logical.getY() < 0 && logical.getMaxY() > 0);
        GlyphMetrics h = gv.getGlyphMetrics(0);
        check("glyph metrics advance is the position step",
            within(h.getAdvance(), positions[2], 0.01) && h.getAdvanceY() == 0 && h.isStandard());
        GlyphMetrics space = gv.getGlyphMetrics(TEXT.indexOf(' '));
        check("a space has an advance and no ink", space.getAdvance() > 0 && space.getBounds2D().isEmpty());

        // --- outlines ---
        Rectangle2D outline = gv.getOutline().getBounds2D();
        Rectangle2D visual = gv.getVisualBounds();
        check("outline bounds equal visual bounds within 1px (" + outline + " vs " + visual + ")",
            within(outline.getX(), visual.getX(), 1) && within(outline.getY(), visual.getY(), 1)
                && within(outline.getMaxX(), visual.getMaxX(), 1) && within(outline.getMaxY(), visual.getMaxY(), 1));
        check("visual bounds have ink above the baseline", !visual.isEmpty() && visual.getMinY() < 0);
        Rectangle2D moved = gv.getOutline(10, 20).getBounds2D();
        check("getOutline(x, y) translates", within(moved.getX(), outline.getX() + 10, 1e-3) && within(moved.getY(), outline.getY() + 20, 1e-3));
        Rectangle2D first = gv.getGlyphOutline(0).getBounds2D();
        check("glyph outline lies inside the vector outline", outline.contains(first) || within(first.getMinX(), outline.getMinX(), 1e-3));

        // --- edits and equality ---
        GlyphVector edited = factory.create(font, frc, TEXT);
        check("equal vectors compare equal", gv.equals(edited) && gv.equals((Object) edited));
        edited.setGlyphPosition(1, new Point2D.Float(positions[2] + 5, 0));
        check("setGlyphPosition moves the glyph", within(edited.getGlyphPosition(1).getX(), positions[2] + 5, 1e-3));
        check("an edited vector is no longer equal", !gv.equals(edited));
        check("position edits are flagged", (edited.getLayoutFlags() & GlyphVector.FLAG_HAS_POSITION_ADJUSTMENTS) != 0);
        edited.performDefaultLayout();
        check("performDefaultLayout restores the positions", gv.equals(edited));
        edited.setGlyphTransform(0, AffineTransform.getScaleInstance(2, 2));
        check("setGlyphTransform is kept and flagged", edited.getGlyphTransform(0).getScaleX() == 2
            && (edited.getLayoutFlags() & GlyphVector.FLAG_HAS_TRANSFORMS) != 0);
        check("a glyph transform scales its outline",
            edited.getGlyphOutline(0).getBounds2D().getHeight() > gv.getGlyphOutline(0).getBounds2D().getHeight() * 1.5);
        edited.setGlyphTransform(0, null);
        check("a null transform resets the glyph", edited.getGlyphTransform(0) == null);

        // --- layoutGlyphVector ---
        char[] chars = ("[" + TEXT + "]").toCharArray();
        GlyphVector ltr = factory.layout(font, frc, chars, 1, chars.length - 1, Font.LAYOUT_LEFT_TO_RIGHT);
        check("layoutGlyphVector LTR covers the range", ltr.getNumGlyphs() == TEXT.length()
            && java.util.Arrays.equals(ltr.getGlyphCodes(0, TEXT.length(), null), codes));
        String hebrew = "שלום";
        GlyphVector rtl = factory.layout(font, frc, hebrew.toCharArray(), 0, hebrew.length(), Font.LAYOUT_RIGHT_TO_LEFT);
        check("layoutGlyphVector RTL keeps one glyph per char", rtl.getNumGlyphs() == hebrew.length());
        check("RTL visual order maps back to the logical chars in reverse",
            rtl.getGlyphCharIndex(0) == hebrew.length() - 1 && rtl.getGlyphCharIndex(hebrew.length() - 1) == 0);
        check("RTL run is flagged", (rtl.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0);
        threw = false;
        // The JDK's GlyphLayout throws IllegalArgumentException where the Javadoc names
        // ArrayIndexOutOfBoundsException; either is a rejection.
        try { factory.layout(font, frc, chars, 0, chars.length + 1, 0); }
        catch (IndexOutOfBoundsException | IllegalArgumentException e) { threw = true; }
        check("layoutGlyphVector rejects a limit past the text", threw);

        // --- drawGlyphVector against drawString ---
        if (shadow) {
            GlyphVector moved2 = factory.create(font, frc, TEXT);
            moved2.setGlyphPosition(3, new Point2D.Float(40, 2));
            GlyphVector turned = factory.create(font, frc, TEXT);
            turned.setGlyphTransform(1, AffineTransform.getRotateInstance(0.5));
            check("an unmodified vector draws as drawString's run", TEXT.equals(factory.defaultRun(gv)));
            check("an edited, transformed or RTL vector draws its outline instead",
                factory.defaultRun(moved2) == null && factory.defaultRun(turned) == null && factory.defaultRun(rtl) == null);
            moved2.performDefaultLayout();
            check("performDefaultLayout gives the drawString run back", TEXT.equals(factory.defaultRun(moved2)));
            check("a vector from glyph codes draws its outline",
                factory.defaultRun(factory.create(font, frc, codes)) == null);
        } else for (Object aa : new Object[] {RenderingHints.VALUE_TEXT_ANTIALIAS_OFF, RenderingHints.VALUE_TEXT_ANTIALIAS_ON}) {
            BufferedImage viaString = new BufferedImage(240, 40, BufferedImage.TYPE_INT_ARGB);
            BufferedImage viaVector = new BufferedImage(240, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gs = viaString.createGraphics();
            Graphics2D gg = viaVector.createGraphics();
            int ink = 0;
            for (Graphics2D g : new Graphics2D[] {gs, gg}) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aa);
                g.setFont(font);
                g.setColor(Color.WHITE);
            }
            gs.drawString(TEXT, 4, 28);
            // A different current font: drawGlyphVector draws in the vector's own font.
            gg.setFont(font.deriveFont(30f));
            gg.drawGlyphVector(font.createGlyphVector(gs.getFontRenderContext(), TEXT), 4, 28);
            gs.dispose();
            gg.dispose();
            int differing = 0;
            for (int y = 0; y < 40; y++) {
                for (int x = 0; x < 240; x++) {
                    int a = viaString.getRGB(x, y);
                    if (a != viaVector.getRGB(x, y)) differing++;
                    if ((a >>> 24) != 0) ink++;
                }
            }
            check("drawGlyphVector pixels equal drawString pixels, text AA " + aa + " (" + differing + " differ)",
                ink > 0 && differing == 0);
        }

        System.out.println(total + " checks, " + failures + " failures");
        if (failures != 0) System.exit(1);
    }

    /** Where the vectors under test come from: the JDK's Font, or the shadow's factory behind it. */
    public abstract static class Glyphs {
        abstract GlyphVector create(Font font, FontRenderContext frc, String text);
        abstract GlyphVector create(Font font, FontRenderContext frc, char[] text);
        abstract GlyphVector create(Font font, FontRenderContext frc, java.text.CharacterIterator text);
        abstract GlyphVector create(Font font, FontRenderContext frc, int[] glyphCodes);
        abstract GlyphVector layout(Font font, FontRenderContext frc, char[] text, int start, int limit, int flags);
        abstract Rectangle2D stringBounds(Font font, String text, FontRenderContext frc);
        String defaultRun(GlyphVector gv) { throw new UnsupportedOperationException("defaultRun"); }
    }

    public static final class JdkGlyphs extends Glyphs {
        @Override GlyphVector create(Font font, FontRenderContext frc, String text) { return font.createGlyphVector(frc, text); }
        @Override GlyphVector create(Font font, FontRenderContext frc, char[] text) { return font.createGlyphVector(frc, text); }
        @Override GlyphVector create(Font font, FontRenderContext frc, java.text.CharacterIterator text) { return font.createGlyphVector(frc, text); }
        @Override GlyphVector create(Font font, FontRenderContext frc, int[] glyphCodes) { return font.createGlyphVector(frc, glyphCodes); }
        @Override GlyphVector layout(Font font, FontRenderContext frc, char[] text, int start, int limit, int flags) {
            return font.layoutGlyphVector(frc, text, start, limit, flags);
        }
        @Override Rectangle2D stringBounds(Font font, String text, FontRenderContext frc) { return font.getStringBounds(text, frc); }
    }

    /**
     * The shadow's java.awt.Font is never patched over the JDK's (the font natives read its
     * private layout), so the patched run holds a JDK Font and calls the AndroidGlyphVector
     * factories the shadow Font's createGlyphVector / layoutGlyphVector delegate to, one call
     * each, over the HostBackend face. String bounds come from the same backend, as the
     * shadow Font's getStringBounds does.
     */
    public static final class ShadowGlyphs extends Glyphs {
        public ShadowGlyphs() { HostBackend.install(); }
        @Override GlyphVector create(Font font, FontRenderContext frc, String text) {
            char[] chars = text.toCharArray();
            return net.runelite.awt.impl.AndroidGlyphVector.layout(font, frc, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
        }
        @Override GlyphVector create(Font font, FontRenderContext frc, char[] text) {
            return net.runelite.awt.impl.AndroidGlyphVector.layout(font, frc, text, 0, text.length, Font.LAYOUT_LEFT_TO_RIGHT);
        }
        @Override GlyphVector create(Font font, FontRenderContext frc, java.text.CharacterIterator text) {
            return net.runelite.awt.impl.AndroidGlyphVector.layout(font, frc, text);
        }
        @Override GlyphVector create(Font font, FontRenderContext frc, int[] glyphCodes) {
            return net.runelite.awt.impl.AndroidGlyphVector.fromGlyphCodes(font, frc, glyphCodes);
        }
        @Override GlyphVector layout(Font font, FontRenderContext frc, char[] text, int start, int limit, int flags) {
            return net.runelite.awt.impl.AndroidGlyphVector.layout(font, frc, text, start, limit, flags);
        }
        @Override Rectangle2D stringBounds(Font font, String text, FontRenderContext frc) {
            return net.runelite.awt.impl.FontBackend.get().stringBounds(font, text);
        }
        @Override String defaultRun(GlyphVector gv) {
            return ((net.runelite.awt.impl.AndroidGlyphVector) gv).defaultRun();
        }
    }

    /**
     * A face with box glyphs: every non-space char is a rectangle from 0.05 em to its
     * advance less 0.05 em, 0.7 em tall on the baseline; advances vary per char so the
     * vector's sums are checked. Glyph codes are the code point plus 3 (TrueType-like).
     */
    public static final class HostBackend extends net.runelite.awt.impl.FontBackend {
        public static void install() {
            net.runelite.awt.impl.FontBackend.install(new HostBackend());
        }

        static float width(char c, float size) {
            if (Character.isLowSurrogate(c)) return 0;
            return Character.isWhitespace(c) ? 0.3f * size : (0.45f + (c % 5) * 0.05f) * size;
        }

        @Override public String register(InputStream in) { throw new UnsupportedOperationException("register"); }
        @Override public boolean canDisplay(Font font, int codePoint) { return true; }
        @Override public int numGlyphs(Font font) { return 0x10000; }
        @Override public float italicAngle(Font font) { return 0; }
        @Override public Rectangle2D stringBounds(Font font, String text) {
            float size = font.getSize2D(), w = 0;
            for (int i = 0; i < text.length(); i++) w += width(text.charAt(i), size);
            return new Rectangle2D.Float(0, -0.8f * size, w, size);
        }
        @Override public Rectangle2D maxCharBounds(Font font) { return stringBounds(font, "W"); }
        @Override public LineMetrics lineMetrics(Font font, int numChars) { throw new UnsupportedOperationException("lineMetrics"); }

        @Override
        public net.runelite.awt.impl.GlyphFace glyphFace(Font font, FontRenderContext frc) {
            final float size = font.getSize2D();
            return new net.runelite.awt.impl.GlyphFace() {
                @Override public float ascent() { return 0.8f * size; }
                @Override public float descent() { return 0.2f * size; }
                @Override public float leading() { return 0; }
                @Override public void advances(char[] text, int start, int count, int contextStart, int contextCount,
                                               boolean rtl, float[] out) {
                    for (int i = 0; i < count; i++) out[i] = width(text[start + i], size);
                }
                @Override public float advance(String cluster) { return width(cluster.charAt(0), size); }
                @Override public int glyphCode(int codePoint) { return codePoint + 3; }
                @Override public int codePointForGlyph(int glyphCode) { return glyphCode > 3 ? glyphCode - 3 : -1; }
                @Override public GeneralPath outline(String cluster) {
                    GeneralPath path = new GeneralPath();
                    char c = cluster.charAt(0);
                    if (Character.isWhitespace(c)) return path;
                    float w = width(c, size);
                    path.append(new Rectangle2D.Float(0.05f * size, -0.7f * size, w - 0.1f * size, 0.7f * size), false);
                    return path;
                }
            };
        }
    }
}
