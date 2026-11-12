/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.awt;

import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.NumericShaper;
import java.awt.font.TextAttribute;
import java.awt.font.TransformAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.AttributedCharacterIterator.Attribute;
import java.text.CharacterIterator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.runelite.awt.impl.FontBackend;
import net.runelite.awt.impl.FontFamilies;

/**
 * The {@code Font} class represents fonts, which are used to render text in a visible way.
 *
 * <p>The attribute model follows OpenJDK's {@code AttributeValues}: a font carries the
 * {@link TextAttribute}s it was defined with (always the primary ones: family, weight,
 * width, posture, size, transform, superscript, tracking), values are normalized on the
 * way in ({@code Number} sizes and weights become {@code Float}, a transform becomes a
 * {@link TransformAttribute}), a value of the wrong type or {@code null} is ignored, and
 * the style is derived from weight ({@code >= WEIGHT_BOLD}) and posture
 * ({@code >= POSTURE_OBLIQUE}).
 *
 * <p>Names resolve through {@code net.runelite.awt.impl.FontFamilies} (logical,
 * {@code createFont}-registered and Android system families); glyphs and metrics through
 * {@code FontBackend}, which renders with Android's Paint and Typeface (see
 * {@code AwtFontRegistry} for the family-to-Typeface mapping). This class names nothing
 * from {@code android.*} so the host contracts can run it against the JDK's.
 */
public class Font implements Serializable {
    private static final long serialVersionUID = -4206021311591459213L;

    public static final String DIALOG = "Dialog";
    public static final String DIALOG_INPUT = "DialogInput";
    public static final String SANS_SERIF = "SansSerif";
    public static final String SERIF = "Serif";
    public static final String MONOSPACED = "Monospaced";

    public static final int PLAIN = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;

    public static final int ROMAN_BASELINE = 0;
    public static final int CENTER_BASELINE = 1;
    public static final int HANGING_BASELINE = 2;

    public static final int TRUETYPE_FONT = 0;
    public static final int TYPE1_FONT = 1;

    public static final int LAYOUT_LEFT_TO_RIGHT = 0;
    public static final int LAYOUT_RIGHT_TO_LEFT = 1;
    public static final int LAYOUT_NO_START_CONTEXT = 2;
    public static final int LAYOUT_NO_LIMIT_CONTEXT = 4;

    /** The logical name of this {@code Font}, as passed to the constructor. */
    protected String name;
    /** The style of this {@code Font}: PLAIN, BOLD, ITALIC, or BOLD+ITALIC. */
    protected int style;
    /** The point size of this {@code Font}, rounded to integer. */
    protected int size;
    /** The point size of this {@code Font} in {@code float}. */
    protected float pointSize;

    /** Defined attributes, normalized; never mutated once the constructor returns. */
    private final HashMap<TextAttribute, Object> values;

    private transient int hash;

    private static final TextAttribute[] PRIMARY = {
        TextAttribute.FAMILY, TextAttribute.WEIGHT, TextAttribute.WIDTH, TextAttribute.POSTURE,
        TextAttribute.SIZE, TextAttribute.TRANSFORM, TextAttribute.SUPERSCRIPT, TextAttribute.TRACKING
    };
    private static final TextAttribute[] LAYOUT = {
        TextAttribute.CHAR_REPLACEMENT, TextAttribute.FOREGROUND, TextAttribute.BACKGROUND,
        TextAttribute.UNDERLINE, TextAttribute.STRIKETHROUGH, TextAttribute.RUN_DIRECTION,
        TextAttribute.BIDI_EMBEDDING, TextAttribute.JUSTIFICATION, TextAttribute.INPUT_METHOD_HIGHLIGHT,
        TextAttribute.INPUT_METHOD_UNDERLINE, TextAttribute.SWAP_COLORS, TextAttribute.NUMERIC_SHAPING,
        TextAttribute.KERNING, TextAttribute.LIGATURES, TextAttribute.TRACKING, TextAttribute.SUPERSCRIPT
    };
    private static final TextAttribute[] EXTRA = {
        TextAttribute.TRANSFORM, TextAttribute.SUPERSCRIPT, TextAttribute.WIDTH
    };
    /** Baseline offset per superscript level, in ems (OpenJDK's ssinfo). */
    private static final float[] SSINFO = {
        0.0f, 0.375f, 0.625f, 0.7916667f, 0.9027778f, 0.9768519f, 1.0262346f, 1.0591564f
    };

    /**
     * Creates a new {@code Font} from the specified name, style and point size. A style
     * with bits other than BOLD and ITALIC is treated as PLAIN; a {@code null} name
     * becomes {@code "Default"}.
     */
    public Font(String name, int style, int size) {
        this.name = (name != null) ? name : "Default";
        this.style = (style & ~0x03) == 0 ? style : 0;
        this.size = size;
        this.pointSize = size;
        this.values = new HashMap<>();
        values.put(TextAttribute.FAMILY, this.name);
        values.put(TextAttribute.SIZE, pointSize);
        values.put(TextAttribute.WEIGHT, (this.style & BOLD) != 0 ? 2f : 1f);
        values.put(TextAttribute.POSTURE, (this.style & ITALIC) != 0 ? .2f : 0f);
        definePrimary(values);
    }

    /**
     * Creates a new {@code Font} with the specified attributes. Only {@link TextAttribute}
     * keys are recognized; {@code FONT} is ignored, as are values of the wrong type.
     */
    public Font(Map<? extends Attribute, ?> attributes) {
        this(merge(new HashMap<TextAttribute, Object>(), attributes, false), true);
    }

    /** Creates a new {@code Font} from the specified {@code font}. */
    protected Font(Font font) {
        this.name = font.name;
        this.style = font.style;
        this.size = font.size;
        this.pointSize = font.pointSize;
        this.values = new HashMap<>(font.values);
    }

    private Font(HashMap<TextAttribute, Object> values, boolean fromValues) {
        this.values = values;
        definePrimary(values);
        this.name = (String) values.get(TextAttribute.FAMILY);
        this.pointSize = (Float) values.get(TextAttribute.SIZE);
        this.size = (int) (pointSize + 0.5);
        int s = PLAIN;
        if ((Float) values.get(TextAttribute.WEIGHT) >= 2f) s |= BOLD;
        if ((Float) values.get(TextAttribute.POSTURE) >= .2f) s |= ITALIC;
        this.style = s;
    }

    // ---------------------------------------------------------------- attribute values

    private static Object defaultValue(TextAttribute a) {
        if (a == TextAttribute.FAMILY) return "Default";
        if (a == TextAttribute.WEIGHT || a == TextAttribute.WIDTH || a == TextAttribute.JUSTIFICATION) return 1f;
        if (a == TextAttribute.POSTURE || a == TextAttribute.TRACKING) return 0f;
        if (a == TextAttribute.SIZE) return 12f;
        if (a == TextAttribute.TRANSFORM) return TransformAttribute.IDENTITY;
        if (a == TextAttribute.SUPERSCRIPT || a == TextAttribute.BIDI_EMBEDDING
                || a == TextAttribute.KERNING || a == TextAttribute.LIGATURES) return 0;
        if (a == TextAttribute.UNDERLINE || a == TextAttribute.INPUT_METHOD_UNDERLINE) return -1;
        if (a == TextAttribute.STRIKETHROUGH || a == TextAttribute.SWAP_COLORS) return Boolean.FALSE;
        return null;
    }

    private static boolean isPrimary(TextAttribute a) {
        for (TextAttribute p : PRIMARY) {
            if (p == a) return true;
        }
        return false;
    }

    /** The stored form of {@code v} for {@code a}, or null when OpenJDK would ignore it. */
    private static Object normalize(TextAttribute a, Object v) {
        if (v == null) return null;
        if (a == TextAttribute.FAMILY) return v instanceof String ? v : null;
        if (a == TextAttribute.WEIGHT || a == TextAttribute.WIDTH || a == TextAttribute.POSTURE
                || a == TextAttribute.SIZE || a == TextAttribute.TRACKING || a == TextAttribute.JUSTIFICATION) {
            return v instanceof Number ? (Object) Float.valueOf(((Number) v).floatValue()) : null;
        }
        if (a == TextAttribute.TRANSFORM) {
            if (v instanceof TransformAttribute) {
                return ((TransformAttribute) v).isIdentity() ? TransformAttribute.IDENTITY : v;
            }
            if (v instanceof AffineTransform) {
                AffineTransform at = (AffineTransform) v;
                return at.isIdentity() ? TransformAttribute.IDENTITY : new TransformAttribute(at);
            }
            return null;
        }
        if (a == TextAttribute.SUPERSCRIPT || a == TextAttribute.UNDERLINE || a == TextAttribute.BIDI_EMBEDDING
                || a == TextAttribute.INPUT_METHOD_UNDERLINE || a == TextAttribute.KERNING
                || a == TextAttribute.LIGATURES) {
            return v instanceof Integer ? v : null;
        }
        if (a == TextAttribute.STRIKETHROUGH || a == TextAttribute.SWAP_COLORS || a == TextAttribute.RUN_DIRECTION) {
            return v instanceof Boolean ? v : null;
        }
        if (a == TextAttribute.FOREGROUND || a == TextAttribute.BACKGROUND) return v instanceof Paint ? v : null;
        if (a == TextAttribute.NUMERIC_SHAPING) return v instanceof NumericShaper ? v : null;
        // GraphicAttribute and InputMethodHighlight/Annotation values are carried as given.
        if (a == TextAttribute.CHAR_REPLACEMENT || a == TextAttribute.INPUT_METHOD_HIGHLIGHT) return v;
        return null; // FONT, and keys Font does not recognize
    }

    private static HashMap<TextAttribute, Object> merge(HashMap<TextAttribute, Object> into,
            Map<? extends Attribute, ?> attributes, boolean secondaryOnly) {
        if (attributes == null) return into;
        for (Map.Entry<? extends Attribute, ?> e : attributes.entrySet()) {
            if (!(e.getKey() instanceof TextAttribute)) continue;
            TextAttribute a = (TextAttribute) e.getKey();
            if (secondaryOnly && isPrimary(a)) continue;
            Object v = normalize(a, e.getValue());
            if (v != null) into.put(a, v);
        }
        return into;
    }

    private static void definePrimary(HashMap<TextAttribute, Object> values) {
        for (TextAttribute a : PRIMARY) {
            if (!values.containsKey(a)) values.put(a, defaultValue(a));
        }
    }

    private boolean isNonDefault(TextAttribute a) {
        if (!values.containsKey(a)) return false;
        Object v = values.get(a);
        if (a == TextAttribute.TRANSFORM) return !((TransformAttribute) v).isIdentity();
        Object d = defaultValue(a);
        return d == null ? v != null : !d.equals(v);
    }

    private boolean anyNonDefault(TextAttribute[] keys) {
        for (TextAttribute a : keys) {
            if (isNonDefault(a)) return true;
        }
        return false;
    }

    private HashMap<TextAttribute, Object> copyValues() {
        return new HashMap<>(values);
    }

    private static void applyStyle(int style, HashMap<TextAttribute, Object> values) {
        values.put(TextAttribute.WEIGHT, (style & BOLD) != 0 ? 2f : 1f);
        values.put(TextAttribute.POSTURE, (style & ITALIC) != 0 ? .2f : 0f);
    }

    private static void applyTransform(AffineTransform trans, HashMap<TextAttribute, Object> values) {
        if (trans == null) {
            throw new IllegalArgumentException("transform must not be null");
        }
        values.put(TextAttribute.TRANSFORM, normalize(TextAttribute.TRANSFORM, trans));
    }

    // ---------------------------------------------------------------- factories

    /**
     * Returns a {@code Font} appropriate to the attributes. When {@code FONT} is present
     * its font is returned, with the other non-primary attributes applied on top.
     */
    public static Font getFont(Map<? extends Attribute, ?> attributes) {
        Font font = (Font) attributes.get(TextAttribute.FONT);
        if (font != null) {
            if (attributes.size() > 1) {
                return new Font(merge(font.copyValues(), attributes, true), true);
            }
            return font;
        }
        return new Font(attributes);
    }

    /** Returns the {@code Font} the system property {@code nm} decodes to, or null. */
    public static Font getFont(String nm) {
        return getFont(nm, null);
    }

    /** Returns the {@code Font} the system property {@code nm} decodes to, or {@code font}. */
    public static Font getFont(String nm, Font font) {
        String str = null;
        try {
            str = System.getProperty(nm);
        } catch (SecurityException e) {
        }
        if (str == null) {
            return font;
        }
        return decode(str);
    }

    /**
     * Returns the {@code Font} that {@code str} describes: {@code fontname-style-pointsize},
     * {@code fontname-pointsize}, {@code fontname-style} or {@code fontname}, with the
     * separator being whichever of {@code '-'} and {@code ' '} occurs last. Style is one of
     * {@code PLAIN}, {@code BOLD}, {@code ITALIC}, {@code BOLDITALIC} (case-insensitive);
     * anything else is part of the name. A missing or non-positive size is 12.
     */
    public static Font decode(String str) {
        String fontName = str;
        String styleName = "";
        int fontSize = 12;
        int fontStyle = Font.PLAIN;

        if (str == null) {
            return new Font(DIALOG, fontStyle, fontSize);
        }

        int lastHyphen = str.lastIndexOf('-');
        int lastSpace = str.lastIndexOf(' ');
        char sepChar = (lastHyphen > lastSpace) ? '-' : ' ';
        int sizeIndex = str.lastIndexOf(sepChar);
        int styleIndex = str.lastIndexOf(sepChar, sizeIndex - 1);
        int strlen = str.length();

        if (sizeIndex > 0 && sizeIndex + 1 < strlen) {
            try {
                fontSize = Integer.valueOf(str.substring(sizeIndex + 1)).intValue();
                if (fontSize <= 0) {
                    fontSize = 12;
                }
            } catch (NumberFormatException e) {
                // Not a size; with no style found yet, perhaps this is the style.
                styleIndex = sizeIndex;
                sizeIndex = strlen;
                if (str.charAt(sizeIndex - 1) == sepChar) {
                    sizeIndex--;
                }
            }
        }

        if (styleIndex >= 0 && styleIndex + 1 < strlen) {
            styleName = str.substring(styleIndex + 1, sizeIndex);
            styleName = styleName.toLowerCase(Locale.ENGLISH);
            if (styleName.equals("bolditalic")) {
                fontStyle = Font.BOLD | Font.ITALIC;
            } else if (styleName.equals("italic")) {
                fontStyle = Font.ITALIC;
            } else if (styleName.equals("bold")) {
                fontStyle = Font.BOLD;
            } else if (styleName.equals("plain")) {
                fontStyle = Font.PLAIN;
            } else {
                // Not one of the styles, so part of the font name.
                styleIndex = sizeIndex;
                if (str.charAt(styleIndex - 1) == sepChar) {
                    styleIndex--;
                }
            }
            fontName = str.substring(0, styleIndex);
        } else {
            int fontEnd = strlen;
            if (styleIndex > 0) {
                fontEnd = styleIndex;
            } else if (sizeIndex > 0) {
                fontEnd = sizeIndex;
            }
            if (fontEnd > 0 && str.charAt(fontEnd - 1) == sepChar) {
                fontEnd--;
            }
            fontName = str.substring(0, fontEnd);
        }

        return new Font(fontName, fontStyle, fontSize);
    }

    /**
     * Returns a new {@code Font} of point size 1 and style PLAIN from TrueType data. The face
     * is registered under the family its 'name' table declares, so {@code new Font(family,
     * style, size)} reaches it afterwards (desktop needs {@code registerFont} for that; on
     * Android every created font is registered). RuneLite's FontManager loads
     * runescape*.ttf this way. Type 1 fonts are not supported by Android.
     */
    public static Font createFont(int fontFormat, InputStream fontStream)
            throws FontFormatException, IOException {
        if (fontFormat != TRUETYPE_FONT && fontFormat != TYPE1_FONT) {
            throw new IllegalArgumentException("font format not recognized");
        }
        if (fontFormat == TYPE1_FONT) {
            throw new FontFormatException("Type 1 fonts are not supported on Android");
        }
        String family = FontBackend.get().register(fontStream);
        if (family == null) {
            throw new FontFormatException("Unable to create font - bad font data");
        }
        return new Font(family, PLAIN, 1);
    }

    /** {@link #createFont(int, InputStream)} over a file. */
    public static Font createFont(int fontFormat, File fontFile)
            throws FontFormatException, IOException {
        fontFile = new File(fontFile.getPath());
        if (fontFormat != TRUETYPE_FONT && fontFormat != TYPE1_FONT) {
            throw new IllegalArgumentException("font format not recognized");
        }
        if (!fontFile.canRead()) {
            throw new IOException("Can't read " + fontFile);
        }
        try (InputStream in = new FileInputStream(fontFile)) {
            return createFont(fontFormat, in);
        }
    }

    // JDK 9+ (10): jdk17u src/java.desktop/share/classes/java/awt/Font.java
    /**
     * Returns the fonts of a TrueType file or collection. Android's Typeface loads the first
     * face of a collection only, so the array has one element.
     */
    public static Font[] createFonts(InputStream fontStream) throws FontFormatException, IOException {
        return new Font[] {createFont(TRUETYPE_FONT, fontStream)};
    }

    // JDK 9+ (10): jdk17u src/java.desktop/share/classes/java/awt/Font.java
    public static Font[] createFonts(File fontFile) throws FontFormatException, IOException {
        fontFile = new File(fontFile.getPath());
        if (!fontFile.canRead()) {
            throw new IOException("Can't read " + fontFile);
        }
        return new Font[] {createFont(TRUETYPE_FONT, fontFile)};
    }

    // ---------------------------------------------------------------- names and style

    /** Returns the transform of this font: TRANSFORM, then SUPERSCRIPT and WIDTH applied. */
    public AffineTransform getTransform() {
        if (!isTransformed()) {
            return new AffineTransform();
        }
        AffineTransform at = isNonDefault(TextAttribute.TRANSFORM)
            ? ((TransformAttribute) values.get(TextAttribute.TRANSFORM)).getTransform()
            : new AffineTransform();
        int superscript = (Integer) values.get(TextAttribute.SUPERSCRIPT);
        if (superscript != 0) {
            double trans = 0;
            int n = 0;
            boolean up = superscript > 0;
            int sign = up ? -1 : 1;
            int ss = up ? superscript : -superscript;
            while ((ss & 7) > n) {
                int newn = ss & 7;
                trans += sign * (SSINFO[newn] - SSINFO[n]);
                ss >>= 3;
                sign = -sign;
                n = newn;
            }
            trans *= pointSize;
            double scale = Math.pow(2. / 3., n);
            at.preConcatenate(AffineTransform.getTranslateInstance(0, trans));
            at.scale(scale, scale);
        }
        if (isNonDefault(TextAttribute.WIDTH)) {
            at.scale((Float) values.get(TextAttribute.WIDTH), 1f);
        }
        return at;
    }

    /** Returns the family name: a logical, registered or Android system family, else Dialog. */
    public String getFamily() {
        return FontFamilies.resolve(name);
    }

    /** Fonts have no localized family names on Android; {@code l} must not be null. */
    public String getFamily(Locale l) {
        if (l == null) {
            throw new NullPointerException("null locale doesn't mean default");
        }
        return getFamily();
    }

    public String getPSName() {
        return FontFamilies.psName(name, style);
    }

    public String getName() {
        return name;
    }

    /** The face name: {@code "Dialog.bold"} for logical fonts, the 'name' table full name
     *  for created fonts. */
    public String getFontName() {
        return FontFamilies.fontName(name, style);
    }

    public String getFontName(Locale l) {
        if (l == null) {
            throw new NullPointerException("null locale doesn't mean default");
        }
        return getFontName();
    }

    public int getStyle() {
        return style;
    }

    public int getSize() {
        return size;
    }

    public float getSize2D() {
        return pointSize;
    }

    public boolean isPlain() {
        return style == 0;
    }

    public boolean isBold() {
        return (style & BOLD) != 0;
    }

    public boolean isItalic() {
        return (style & ITALIC) != 0;
    }

    /** Whether TRANSFORM, SUPERSCRIPT or WIDTH make this font's transform non-identity. */
    public boolean isTransformed() {
        return anyNonDefault(EXTRA);
    }

    /** Whether any attribute that needs TextLayout (underline, kerning, tracking, ...) is set. */
    public boolean hasLayoutAttributes() {
        return anyNonDefault(LAYOUT);
    }

    /** Non-JDK accessor for the raster pipeline: whether TextAttribute.UNDERLINE is on. */
    public boolean isUnderlined() {
        Object u = values.get(TextAttribute.UNDERLINE);
        return u instanceof Integer && (Integer) u >= 0;
    }

    /** Non-JDK accessor for the raster pipeline: whether TextAttribute.STRIKETHROUGH is on. */
    public boolean isStrikethrough() {
        return Boolean.TRUE.equals(values.get(TextAttribute.STRIKETHROUGH));
    }

    /** Non-JDK accessor for the raster pipeline: TextAttribute.TRACKING in ems. */
    public float getTracking() {
        Object t = values.get(TextAttribute.TRACKING);
        return t instanceof Float ? (Float) t : 0f;
    }

    // ---------------------------------------------------------------- attributes

    /** Returns the defined attributes of this font; the map is unmodifiable. */
    public Map<TextAttribute, ?> getAttributes() {
        return Collections.unmodifiableMap(new HashMap<TextAttribute, Object>(values));
    }

    /** Returns the keys of all attributes this font supports (every TextAttribute but FONT). */
    public Attribute[] getAvailableAttributes() {
        Attribute[] attributes = {
            TextAttribute.FAMILY,
            TextAttribute.WEIGHT,
            TextAttribute.WIDTH,
            TextAttribute.POSTURE,
            TextAttribute.SIZE,
            TextAttribute.TRANSFORM,
            TextAttribute.SUPERSCRIPT,
            TextAttribute.CHAR_REPLACEMENT,
            TextAttribute.FOREGROUND,
            TextAttribute.BACKGROUND,
            TextAttribute.UNDERLINE,
            TextAttribute.STRIKETHROUGH,
            TextAttribute.RUN_DIRECTION,
            TextAttribute.BIDI_EMBEDDING,
            TextAttribute.JUSTIFICATION,
            TextAttribute.INPUT_METHOD_HIGHLIGHT,
            TextAttribute.INPUT_METHOD_UNDERLINE,
            TextAttribute.SWAP_COLORS,
            TextAttribute.NUMERIC_SHAPING,
            TextAttribute.KERNING,
            TextAttribute.LIGATURES,
            TextAttribute.TRACKING,
        };
        return attributes;
    }

    // ---------------------------------------------------------------- derive

    public Font deriveFont(int style, float size) {
        HashMap<TextAttribute, Object> v = copyValues();
        v.put(TextAttribute.SIZE, size);
        applyStyle(style, v);
        return new Font(v, true);
    }

    public Font deriveFont(int style, AffineTransform trans) {
        HashMap<TextAttribute, Object> v = copyValues();
        applyStyle(style, v);
        applyTransform(trans, v);
        return new Font(v, true);
    }

    public Font deriveFont(float size) {
        HashMap<TextAttribute, Object> v = copyValues();
        v.put(TextAttribute.SIZE, size);
        return new Font(v, true);
    }

    public Font deriveFont(AffineTransform trans) {
        HashMap<TextAttribute, Object> v = copyValues();
        applyTransform(trans, v);
        return new Font(v, true);
    }

    public Font deriveFont(int style) {
        HashMap<TextAttribute, Object> v = copyValues();
        applyStyle(style, v);
        return new Font(v, true);
    }

    /**
     * Returns this font with {@code attributes} applied on top. quest-helper's Fonts.java
     * seeds a HashMap from {@link #getAttributes()}, adds UNDERLINE and derives its hover
     * links this way, so every recognized key round-trips.
     */
    public Font deriveFont(Map<? extends Attribute, ?> attributes) {
        if (attributes == null) {
            return this;
        }
        return new Font(merge(copyValues(), attributes, false), true);
    }

    // ---------------------------------------------------------------- glyphs

    public boolean canDisplay(char c) {
        return FontBackend.get().canDisplay(this, c);
    }

    public boolean canDisplay(int codePoint) {
        if (!Character.isValidCodePoint(codePoint)) {
            throw new IllegalArgumentException("invalid code point: " + Integer.toHexString(codePoint));
        }
        return FontBackend.get().canDisplay(this, codePoint);
    }

    /** Index of the first character of {@code str} this font cannot display, or -1. */
    public int canDisplayUpTo(String str) {
        FontBackend backend = FontBackend.get();
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (backend.canDisplay(this, c)) {
                continue;
            }
            if (!Character.isHighSurrogate(c)) {
                return i;
            }
            if (!backend.canDisplay(this, str.codePointAt(i))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public int canDisplayUpTo(char[] text, int start, int limit) {
        FontBackend backend = FontBackend.get();
        for (int i = start; i < limit; i++) {
            char c = text[i];
            if (backend.canDisplay(this, c)) {
                continue;
            }
            if (!Character.isHighSurrogate(c)) {
                return i;
            }
            if (!backend.canDisplay(this, Character.codePointAt(text, i, limit))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public int canDisplayUpTo(CharacterIterator iter, int start, int limit) {
        FontBackend backend = FontBackend.get();
        char c = iter.setIndex(start);
        for (int i = start; i < limit; i++, c = iter.next()) {
            if (backend.canDisplay(this, c)) {
                continue;
            }
            if (!Character.isHighSurrogate(c)) {
                return i;
            }
            char c2 = iter.next();
            if (!Character.isLowSurrogate(c2)) {
                return i;
            }
            if (!backend.canDisplay(this, Character.toCodePoint(c, c2))) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /** Slope (run over rise) of the italic style this font renders with; 0 when upright. */
    public float getItalicAngle() {
        return FontBackend.get().italicAngle(this);
    }

    public int getNumGlyphs() {
        return FontBackend.get().numGlyphs(this);
    }

    /** Glyph 0 (.notdef) is the missing glyph of every TrueType/OpenType face Android loads. */
    public int getMissingGlyphCode() {
        return 0;
    }

    /** ROMAN_BASELINE for every character, as OpenJDK's Font2D answers too. */
    public byte getBaselineFor(char c) {
        return ROMAN_BASELINE;
    }

    /** Always false, as on desktop: line metrics may vary across the fonts of a run. */
    public boolean hasUniformLineMetrics() {
        return false;
    }

    // JDK 9+ (9): jdk17u src/java.desktop/share/classes/java/awt/Font.java
    /**
     * Whether {@code chars[start, end)} contains a script that needs complex layout
     * (combining diacritics, Hebrew, Arabic, Indic, Thai, Tibetan, Myanmar, Hangul Jamo,
     * Khmer, joiners and directional controls).
     */
    public static boolean textRequiresLayout(char[] chars, int start, int end) {
        if (chars == null) {
            throw new NullPointerException("null char array");
        }
        if (start < 0 || end > chars.length) {
            throw new ArrayIndexOutOfBoundsException("start < 0 or end > len");
        }
        for (int i = start; i < end; i++) {
            if (isComplexCharCode(chars[i])) {
                return true;
            }
        }
        return false;
    }

    // OpenJDK's FontUtilities.isComplexCharCode ranges.
    private static boolean isComplexCharCode(int code) {
        if (code < 0x0300 || code > 0x206f) {
            return false;
        } else if (code <= 0x036f) {
            return true;
        } else if (code < 0x0590) {
            return false;
        } else if (code <= 0x06ff) {
            return true;
        } else if (code < 0x0900) {
            return false;
        } else if (code <= 0x0e7f) {
            return true;
        } else if (code < 0x0f00) {
            return false;
        } else if (code <= 0x0fff) {
            return true;
        } else if (code < 0x10a0) {
            return true;
        } else if (code < 0x1100) {
            return false;
        } else if (code < 0x11ff) {
            return true;
        } else if (code < 0x1780) {
            return false;
        } else if (code <= 0x17ff) {
            return true;
        } else if (code < 0x200c) {
            return false;
        } else if (code <= 0x200d) {
            return true;
        } else if (code >= 0x202a && code <= 0x202e) {
            return true;
        } else if (code >= 0x206a && code <= 0x206f) {
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- metrics

    public LineMetrics getLineMetrics(String str, FontRenderContext frc) {
        return FontBackend.get().lineMetrics(this, str.length());
    }

    public LineMetrics getLineMetrics(String str, int beginIndex, int limit, FontRenderContext frc) {
        return FontBackend.get().lineMetrics(this, limit - beginIndex);
    }

    public LineMetrics getLineMetrics(char[] chars, int beginIndex, int limit, FontRenderContext frc) {
        return FontBackend.get().lineMetrics(this, limit - beginIndex);
    }

    public LineMetrics getLineMetrics(CharacterIterator ci, int beginIndex, int limit, FontRenderContext frc) {
        return FontBackend.get().lineMetrics(this, limit - beginIndex);
    }

    public Rectangle2D getStringBounds(String str, FontRenderContext frc) {
        return FontBackend.get().stringBounds(this, str);
    }

    public Rectangle2D getStringBounds(String str, int beginIndex, int limit, FontRenderContext frc) {
        return getStringBounds(str.substring(beginIndex, limit), frc);
    }

    public Rectangle2D getStringBounds(char[] chars, int beginIndex, int limit, FontRenderContext frc) {
        if (beginIndex < 0) {
            throw new IndexOutOfBoundsException("beginIndex: " + beginIndex);
        }
        if (limit > chars.length) {
            throw new IndexOutOfBoundsException("limit: " + limit);
        }
        if (beginIndex > limit) {
            throw new IndexOutOfBoundsException("range length: " + (limit - beginIndex));
        }
        return getStringBounds(new String(chars, beginIndex, limit - beginIndex), frc);
    }

    public Rectangle2D getStringBounds(CharacterIterator ci, int beginIndex, int limit, FontRenderContext frc) {
        int start = ci.getBeginIndex();
        int end = ci.getEndIndex();
        if (beginIndex < start) {
            throw new IndexOutOfBoundsException("beginIndex: " + beginIndex);
        }
        if (limit > end) {
            throw new IndexOutOfBoundsException("limit: " + limit);
        }
        if (beginIndex > limit) {
            throw new IndexOutOfBoundsException("range length: " + (limit - beginIndex));
        }
        char[] arr = new char[limit - beginIndex];
        ci.setIndex(beginIndex);
        for (int idx = 0; idx < arr.length; idx++) {
            arr[idx] = ci.current();
            ci.next();
        }
        return getStringBounds(new String(arr), frc);
    }

    public Rectangle2D getMaxCharBounds(FontRenderContext frc) {
        return FontBackend.get().maxCharBounds(this);
    }

    // ---------------------------------------------------------------- Object

    @Override
    public int hashCode() {
        if (hash == 0) {
            int h = name.hashCode() ^ style ^ size;
            if (isNonDefault(TextAttribute.TRANSFORM)) {
                h ^= values.get(TextAttribute.TRANSFORM).hashCode();
            }
            hash = h;
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Font) {
            Font font = (Font) obj;
            return size == font.size
                && style == font.style
                && pointSize == font.pointSize
                && name.equals(font.name)
                && values.equals(font.values);
        }
        return false;
    }

    @Override
    public String toString() {
        String strStyle;
        if (isBold()) {
            strStyle = isItalic() ? "bolditalic" : "bold";
        } else {
            strStyle = isItalic() ? "italic" : "plain";
        }
        return getClass().getName() + "[family=" + getFamily() + ",name=" + name
            + ",style=" + strStyle + ",size=" + size + "]";
    }
}
