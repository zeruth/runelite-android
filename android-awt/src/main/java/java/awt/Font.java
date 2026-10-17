package java.awt;

import java.io.Serializable;

public class Font implements Serializable {
    private static final long serialVersionUID = -4206021311591459213L;

    public static final int PLAIN = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;

    public static final String DIALOG = "Dialog";
    public static final String DIALOG_INPUT = "DialogInput";
    public static final String SANS_SERIF = "SansSerif";
    public static final String SERIF = "Serif";
    public static final String MONOSPACED = "Monospaced";

    private final String name;
    private final int style;
    private final int size;

    public Font(String name, int style, int size) {
        this.name = name != null ? name : "Default";
        this.style = style;
        this.size = size;
    }

    public String getName()   { return name; }
    public String getFamily() { return name; }
    public int getStyle()     { return style; }
    public int getSize()      { return size; }
    public float getSize2D()  { return size; }

    /** First unsupported UTF-16 index, or -1 when the rendering font supports all text. */
    public int canDisplayUpTo(String text) {
        return net.runelite.awt.impl.BufferedImageFontMetrics.canDisplayUpTo(this, text);
    }

    public boolean isPlain()  { return style == PLAIN; }
    public boolean isBold()   { return (style & BOLD) != 0; }
    public boolean isItalic() { return (style & ITALIC) != 0; }

    public static final int TRUETYPE_FONT = 0;
    public static final int TYPE1_FONT = 1;

    /**
     * Reads a TrueType blob, registers it in {@code AwtFontRegistry} keyed by the
     * font's actual family name (parsed from the TTF 'name' table), and returns a
     * Font whose {@code getFamily()} matches. Subsequent {@code new Font(family,
     * style, size)} calls re-use the registered Typeface.
     *
     * Used by RuneLite's FontManager static initializer to load runescape.ttf,
     * runescape_bold.ttf, runescape_small.ttf and any plugin-side custom fonts.
     */
    public static Font createFont(int fontFormat, java.io.InputStream fontStream) throws java.io.IOException, FontFormatException {
        String family = net.runelite.awt.impl.AwtFontRegistry.register(fontStream);
        if (family == null) family = Font.DIALOG;
        return new Font(family, Font.PLAIN, 12);
    }

    public static Font createFont(int fontFormat, java.io.File fontFile) throws java.io.IOException, FontFormatException {
        try (java.io.InputStream in = new java.io.FileInputStream(fontFile)) {
            return createFont(fontFormat, in);
        }
    }

    public Font deriveFont(int style) {
        return new Font(name, style, size);
    }

    public Font deriveFont(float size) {
        return new Font(name, this.style, (int) size);
    }

    public Font deriveFont(int style, float size) {
        return new Font(name, style, (int) size);
    }

    /**
     * Stub: real AWT exposes the font's full attribute map (family/weight/posture/...).
     * Plugin code (e.g. quest-helper's Fonts.java) seeds a HashMap with this then adds
     * its own keys (UNDERLINE, etc.) and calls deriveFont(Map). For us the styling
     * keys don't matter — Android's Paint handles styling differently — but the call
     * itself has to succeed for the plugin's static init to complete.
     */
    public java.util.Map<java.awt.font.TextAttribute, ?> getAttributes() {
        java.util.Map<java.awt.font.TextAttribute, Object> m = new java.util.HashMap<>();
        m.put(java.awt.font.TextAttribute.FAMILY, name);
        m.put(java.awt.font.TextAttribute.SIZE, (float) size);
        m.put(java.awt.font.TextAttribute.WEIGHT, isBold() ? java.awt.font.TextAttribute.WEIGHT_BOLD : java.awt.font.TextAttribute.WEIGHT_REGULAR);
        m.put(java.awt.font.TextAttribute.POSTURE, isItalic() ? java.awt.font.TextAttribute.POSTURE_OBLIQUE : java.awt.font.TextAttribute.POSTURE_REGULAR);
        return m;
    }

    /**
     * Map-based deriveFont companion to getAttributes(). We honor only what we can
     * meaningfully render through android-awt: family (name), size, and a coarse
     * bold/italic style flag inferred from WEIGHT/POSTURE if present.
     */
    public Font deriveFont(java.util.Map<? extends java.text.AttributedCharacterIterator.Attribute, ?> attrs) {
        if (attrs == null || attrs.isEmpty()) return this;
        String newName = name;
        int newStyle = style;
        int newSize = size;
        Object fam = attrs.get(java.awt.font.TextAttribute.FAMILY);
        if (fam instanceof String) newName = (String) fam;
        Object sz = attrs.get(java.awt.font.TextAttribute.SIZE);
        if (sz instanceof Number) newSize = ((Number) sz).intValue();
        Object weight = attrs.get(java.awt.font.TextAttribute.WEIGHT);
        if (weight instanceof Number) {
            if (((Number) weight).floatValue() >= java.awt.font.TextAttribute.WEIGHT_BOLD) newStyle |= BOLD;
            else newStyle &= ~BOLD;
        }
        Object posture = attrs.get(java.awt.font.TextAttribute.POSTURE);
        if (posture instanceof Number) {
            if (((Number) posture).floatValue() > 0.0f) newStyle |= ITALIC;
            else newStyle &= ~ITALIC;
        }
        return new Font(newName, newStyle, newSize);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Font) {
            Font f = (Font) obj;
            return size == f.size && style == f.style && name.equals(f.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ style ^ size;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[family=" + name + ",style=" + style + ",size=" + size + "]";
    }
}
