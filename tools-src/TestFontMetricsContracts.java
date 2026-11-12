// host-contract
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.StringCharacterIterator;

/**
 * FontMetrics / LineMetrics contracts plugins lean on to lay out overlay text: widths add up per
 * character, the logical bounds are one line tall, every overload agrees with the String one,
 * and the deprecated Toolkit metrics are the ones the Graphics paints with.
 */
public final class TestFontMetricsContracts {
    private static int failures;
    private static int total;

    private static void check(String name, boolean actual) {
        total++;
        if (!actual) failures++;
        System.out.println((actual ? "PASS " : "FAIL ") + name);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static boolean sameRect(Rectangle2D a, Rectangle2D b) {
        return near(a.getX(), b.getX()) && near(a.getY(), b.getY())
            && near(a.getWidth(), b.getWidth()) && near(a.getHeight(), b.getHeight());
    }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        Font font = new Font(Font.DIALOG, Font.PLAIN, 16);
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        FontMetrics fm = g.getFontMetrics(font);
        String text = "Hello, Gielinor 123";

        int sum = 0;
        for (int i = 0; i < text.length(); i++) sum += fm.charWidth(text.charAt(i));
        check("stringWidth equals the sum of charWidths", fm.stringWidth(text) == sum);
        check("charsWidth equals stringWidth", fm.charsWidth(text.toCharArray(), 0, text.length()) == fm.stringWidth(text));
        check("bytesWidth equals stringWidth for ASCII", fm.bytesWidth(text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), 0, text.length()) == fm.stringWidth(text));
        check("charWidth(int) equals charWidth(char)", fm.charWidth((int) 'W') == fm.charWidth('W'));
        check("getWidths covers Latin-1", fm.getWidths().length == 256 && fm.getWidths()['W'] == fm.charWidth('W'));
        check("empty string is zero wide", fm.stringWidth("") == 0);
        check("getHeight is ascent + descent + leading", fm.getHeight() == fm.getAscent() + fm.getDescent() + fm.getLeading());
        check("metrics are positive", fm.getAscent() > 0 && fm.getDescent() > 0 && fm.stringWidth(text) > 0);
        check("max advance covers W", fm.getMaxAdvance() >= fm.charWidth('W'));

        LineMetrics lm = fm.getLineMetrics(text, g);
        Rectangle2D bounds = fm.getStringBounds(text, g);
        check("getStringBounds height equals ascent + descent + leading",
            near(bounds.getHeight(), lm.getAscent() + lm.getDescent() + lm.getLeading()));
        check("getStringBounds starts at minus the ascent", near(bounds.getY(), -lm.getAscent()) && near(bounds.getX(), 0));
        check("getStringBounds height within a pixel of getHeight", Math.abs(bounds.getHeight() - fm.getHeight()) < 2);
        check("getStringBounds width within a pixel per glyph of stringWidth",
            Math.abs(bounds.getWidth() - fm.stringWidth(text)) <= text.length());
        check("line metrics count the characters", lm.getNumChars() == text.length());

        String word = text.substring(7, 15);
        Rectangle2D wordBounds = fm.getStringBounds(word, g);
        check("getStringBounds(String, begin, limit)", sameRect(fm.getStringBounds(text, 7, 15, g), wordBounds));
        check("getStringBounds(char[], begin, limit)", sameRect(fm.getStringBounds(text.toCharArray(), 7, 15, g), wordBounds));
        check("getStringBounds(CharacterIterator, begin, limit)",
            sameRect(fm.getStringBounds(new StringCharacterIterator(text), 7, 15, g), wordBounds));
        check("Font.getStringBounds(CharacterIterator)",
            sameRect(font.getStringBounds(new StringCharacterIterator(text), 7, 15, g.getFontRenderContext()), wordBounds));
        check("getLineMetrics(CharacterIterator) counts the range",
            fm.getLineMetrics(new StringCharacterIterator(text), 7, 15, g).getNumChars() == 8);
        check("getLineMetrics(char[]) height", near(fm.getLineMetrics(text.toCharArray(), 0, 5, g).getHeight(), lm.getHeight()));
        boolean threw = false;
        try {
            font.getStringBounds(text.toCharArray(), 5, 2, g.getFontRenderContext());
        } catch (IndexOutOfBoundsException expected) {
            threw = true;
        }
        check("getStringBounds rejects a reversed range", threw);

        Rectangle2D max = fm.getMaxCharBounds(g);
        check("getMaxCharBounds is one line tall", near(max.getHeight(), lm.getHeight()) && near(max.getY(), -lm.getAscent()));
        check("getMaxCharBounds is at least a W wide", max.getWidth() >= fm.getStringBounds("W", g).getWidth() - 1e-3);

        FontMetrics tk = Toolkit.getDefaultToolkit().getFontMetrics(font);
        check("Toolkit.getFontMetrics equals Graphics.getFontMetrics",
            tk.getFont().equals(font) && tk.stringWidth(text) == fm.stringWidth(text) && tk.getHeight() == fm.getHeight()
                && tk.getAscent() == fm.getAscent() && tk.getDescent() == fm.getDescent() && tk.charWidth('W') == fm.charWidth('W'));

        FontMetrics anonymous = new FontMetrics(font) { };
        check("anonymous FontMetrics keeps its font", anonymous.getFont() == font);
        check("anonymous FontMetrics line metrics", near(anonymous.getStringBounds(text, null).getHeight(), bounds.getHeight()));

        g.drawChars(text.toCharArray(), 0, 5, 0, 8);
        g.drawBytes(text.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), 0, 5, 0, 8);
        check("drawChars and drawBytes draw", true);
        g.dispose();

        System.out.println(total - failures + "/" + total + " passed");
        if (failures > 0) System.exit(1);
    }
}
