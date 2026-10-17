package net.runelite.awt.impl;

import java.awt.Font;
import java.awt.FontMetrics;

/**
 * Font metrics backed by Android's {@link android.graphics.Paint}. Real layouts (BoxLayout,
 * GridLayout, ClientUI.Layout) read stringWidth/getHeight/getAscent to size labels; with
 * the default JDK FontMetrics base class everything would report 0 and children collapse.
 */
public final class BufferedImageFontMetrics extends FontMetrics {
    private static final java.util.concurrent.ConcurrentHashMap<Font, BufferedImageFontMetrics> CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static BufferedImageFontMetrics forFont(Font font) {
        if (font == null) return null;
        BufferedImageFontMetrics cached = CACHE.get(font);
        if (cached != null) return cached;
        BufferedImageFontMetrics fm = new BufferedImageFontMetrics(font);
        // putIfAbsent, not put: this is now the single process-wide metrics cache
        // (java.awt.Component.getFontMetrics delegates here), so two threads racing on the
        // same font should end up sharing one instance rather than each keeping its own
        // Paint + Typeface + width table alive.
        BufferedImageFontMetrics prev = CACHE.putIfAbsent(font, fm);
        return prev != null ? prev : fm;
    }

    private final android.graphics.Paint paint;
    private final int ascent;
    private final int descent;
    private final int leading;
    private final int height;
    /**
     * Widths of U+0000..U+007F, measured once. charWidth() used to allocate a
     * {@code char[1]} and a {@code float[1]} and cross into Minikin on every call, and
     * Swing text layout calls it per character — so a single label measure was dozens of
     * allocations. Everything the UI actually renders is in this range.
     */
    private final int[] asciiWidths = new int[128];
    /** Scratch for the non-ASCII path. Guarded by {@code this} because the Paint is
     *  shared across callers anyway. */
    private final char[] charScratch = new char[1];
    private final float[] widthScratch = new float[1];

    /** Android's Paint.setTextSize takes pixels; Java Font.size is conventionally points
     *  but the OSRS/RuneLite code paths treat it as pixels too, so pass through. */
    public static float pxSize(Font f) {
        return Math.max(1f, (float) f.getSize());
    }

    public BufferedImageFontMetrics(Font font) {
        super(font);
        paint = new android.graphics.Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(pxSize(font));
        paint.setTypeface(typefaceFor(font));
        android.graphics.Paint.FontMetrics afm = paint.getFontMetrics();
        this.ascent = Math.max(0, (int) Math.ceil(-afm.ascent));
        this.descent = Math.max(0, (int) Math.ceil(afm.descent));
        this.leading = Math.max(0, (int) Math.ceil(afm.leading));
        this.height = this.ascent + this.descent + this.leading;

        char[] ascii = new char[128];
        for (int i = 0; i < 128; i++) ascii[i] = (char) i;
        float[] w = new float[128];
        paint.getTextWidths(ascii, 0, 128, w);
        for (int i = 0; i < 128; i++) asciiWidths[i] = (int) Math.ceil(w[i]);
    }

    public static int canDisplayUpTo(Font font, String text) {
        android.graphics.Paint probe = new android.graphics.Paint();
        probe.setTypeface(typefaceFor(font));
        probe.setTextSize(font.getSize2D());
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            int end = index + Character.charCount(codePoint);
            if (!probe.hasGlyph(text.substring(index, end))) return index;
            index = end;
        }
        return -1;
    }

    /** Resolve an AWT Font down to an Android Typeface. If the family is registered
     *  (e.g. runescape.ttf loaded via Font.createFont), use that Typeface as-is —
     *  do NOT layer Typeface.BOLD/ITALIC on top, since the loaded face already
     *  encodes those (RuneLite ships runescape_bold.ttf as its own family). Only
     *  apply style bits when falling back to the system DEFAULT. */
    static android.graphics.Typeface typefaceFor(Font font) {
        android.graphics.Typeface registered = net.runelite.awt.impl.AwtFontRegistry.lookup(font.getName());
        if (registered != null) return registered;
        int style = font.getStyle();
        if (style == Font.BOLD) return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        if (style == Font.ITALIC) return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC);
        if (style == (Font.BOLD | Font.ITALIC)) return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC);
        return android.graphics.Typeface.DEFAULT;
    }

    @Override
    public int getAscent()  { return ascent; }
    @Override
    public int getDescent() { return descent; }
    @Override
    public int getLeading() { return leading; }
    @Override
    public int getHeight()  { return height; }

    @Override
    public int charWidth(int codePoint) {
        return charWidth((char) codePoint);
    }

    @Override
    public int charWidth(char ch) {
        if (ch < 128) return asciiWidths[ch];
        synchronized (this) {
            charScratch[0] = ch;
            paint.getTextWidths(charScratch, 0, 1, widthScratch);
            return (int) Math.ceil(widthScratch[0]);
        }
    }

    @Override
    public int charsWidth(char[] data, int off, int len) {
        if (data == null || len <= 0) return 0;
        return (int) Math.ceil(paint.measureText(data, off, len));
    }

    @Override
    public int stringWidth(String str) {
        if (str == null || str.isEmpty()) return 0;
        return (int) Math.ceil(paint.measureText(str));
    }
}

