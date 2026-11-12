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

import java.awt.font.TextAttribute;
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
    private final float size;
    /** TextAttribute.UNDERLINE value, -1 when off. Decoration only: drawn by
     *  BufferedImageGraphics2D.drawString, never affects metrics. */
    private final int underline;
    private final boolean strikethrough;
    private final java.util.Map<TextAttribute, Object> layoutAttributes = new java.util.HashMap<>();

    public Font(String name, int style, int size) {
        this(name, style, size, -1, false);
    }

    private Font(String name, int style, float size, int underline, boolean strikethrough) {
        this.name = name != null ? name : "Default";
        this.style = style;
        this.size = size;
        this.underline = underline;
        this.strikethrough = strikethrough;
    }

    public Font(java.util.Map<? extends java.text.AttributedCharacterIterator.Attribute, ?> attributes) {
        this(new Font(DIALOG, PLAIN, 12).deriveFont(attributes));
    }

    protected Font(Font font) {
        this(font.name, font.style, font.size, font.underline, font.strikethrough);
        layoutAttributes.putAll(font.layoutAttributes);
    }

    public String getName()   { return name; }
    public String getFamily() { return name; }
    public int getStyle()     { return style; }
    public int getSize()      { return Math.round(size); }
    public float getSize2D()  { return size; }

    /** Non-JDK accessor: whether TextAttribute.UNDERLINE is on. */
    public boolean isUnderlined() { return underline >= 0; }
    /** Non-JDK accessor: whether TextAttribute.STRIKETHROUGH is on. */
    public boolean isStrikethrough() { return strikethrough; }

    /** First unsupported UTF-16 index, or -1 when the rendering font supports all text. */
    public int canDisplayUpTo(String text) {
        return net.runelite.awt.impl.BufferedImageFontMetrics.canDisplayUpTo(this, text);
    }

    public boolean isPlain()  { return getStyle() == PLAIN; }
    public boolean isBold()   { return (getStyle() & BOLD) != 0; }
    public boolean isItalic() { return (getStyle() & ITALIC) != 0; }

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
    public static Font getFont(java.util.Map<? extends java.text.AttributedCharacterIterator.Attribute, ?> attributes) {
        Object specified = attributes.get(TextAttribute.FONT);
        return specified instanceof Font ? (Font)specified : new Font(attributes);
    }
    public java.text.AttributedCharacterIterator.Attribute[] getAvailableAttributes() {
        return new java.text.AttributedCharacterIterator.Attribute[]{TextAttribute.FAMILY, TextAttribute.WEIGHT, TextAttribute.WIDTH, TextAttribute.POSTURE, TextAttribute.SIZE, TextAttribute.TRANSFORM, TextAttribute.SUPERSCRIPT, TextAttribute.TRACKING, TextAttribute.UNDERLINE, TextAttribute.STRIKETHROUGH};
    }
    public static Font getFont(String property) { return getFont(property,null); }
    public static Font getFont(String property,Font fallback) {
        String value=System.getProperty(property);
        return value==null?fallback:decode(value);
    }
    public static Font decode(String value) {
        if(value==null)return new Font(DIALOG,PLAIN,12);
        String[] parts=value.split("[- ]");int size=12,style=PLAIN,end=parts.length;
        try { size=Integer.parseInt(parts[end-1]);end--; } catch(NumberFormatException ignored) {}
        if(end>1){String s=parts[end-1];if(s.equalsIgnoreCase("BOLD")){style=BOLD;end--;}else if(s.equalsIgnoreCase("ITALIC")){style=ITALIC;end--;}else if(s.equalsIgnoreCase("BOLDITALIC")){style=BOLD|ITALIC;end--;}else if(s.equalsIgnoreCase("PLAIN"))end--;}
        String name=String.join(" ",java.util.Arrays.copyOf(parts,Math.max(1,end)));
        return new Font(name,style,size>0?size:12);
    }
    public static Font createFont(int fontFormat, java.io.InputStream fontStream) throws java.io.IOException, FontFormatException {
        if(fontFormat!=TRUETYPE_FONT && fontFormat!=TYPE1_FONT)throw new IllegalArgumentException("font format");
        if(fontFormat==TYPE1_FONT)throw new FontFormatException("Type 1 fonts are not supported by Android");
        String family = net.runelite.awt.impl.AwtFontRegistry.register(fontStream);
        if(family==null)throw new FontFormatException("Invalid TrueType font");
        return new Font(family, Font.PLAIN, 1);
    }

    public static Font createFont(int fontFormat, java.io.File fontFile) throws java.io.IOException, FontFormatException {
        try (java.io.InputStream in = new java.io.FileInputStream(fontFile)) {
            return createFont(fontFormat, in);
        }
    }

    // derive* keep the decoration attributes, as the JDK's do.
    public Font deriveFont(int style) {
        Font result = new Font(name, style, size, underline, strikethrough);
        result.layoutAttributes.putAll(layoutAttributes);
        return result;
    }

    public Font deriveFont(float size) {
        Font result = new Font(name, style, size, underline, strikethrough);
        result.layoutAttributes.putAll(layoutAttributes);
        return result;
    }

    public Font deriveFont(int style, float size) {
        Font result = new Font(name, style, size, underline, strikethrough);
        result.layoutAttributes.putAll(layoutAttributes);
        return result;
    }

    /**
     * The font's attribute map. quest-helper's Fonts.java seeds a HashMap with this, adds
     * UNDERLINE and calls deriveFont(Map) for its hover links, so the decoration keys
     * round-trip.
     */
    public java.util.Map<TextAttribute, ?> getAttributes() {
        java.util.Map<TextAttribute, Object> m = new java.util.HashMap<>(layoutAttributes);
        m.put(TextAttribute.FAMILY, name);
        m.put(TextAttribute.SIZE, (float) size);
        m.put(TextAttribute.WEIGHT, isBold() ? TextAttribute.WEIGHT_BOLD : TextAttribute.WEIGHT_REGULAR);
        m.put(TextAttribute.POSTURE, isItalic() ? TextAttribute.POSTURE_OBLIQUE : TextAttribute.POSTURE_REGULAR);
        m.put(TextAttribute.UNDERLINE, underline >= 0 ? (Object) underline : null);
        m.put(TextAttribute.STRIKETHROUGH, strikethrough ? TextAttribute.STRIKETHROUGH_ON : null);
        return m;
    }

    /**
     * Map-based deriveFont companion to getAttributes(). Honors family, size, a coarse
     * bold/italic style from WEIGHT/POSTURE, and UNDERLINE / STRIKETHROUGH. A key mapped
     * to null (or UNDERLINE -1) turns that attribute off; an absent key keeps ours.
     */
    public Font deriveFont(java.util.Map<? extends java.text.AttributedCharacterIterator.Attribute, ?> attrs) {
        if (attrs == null || attrs.isEmpty()) return this;
        String newName = name;
        int newStyle = style;
        float newSize = size;
        int newUnderline = underline;
        boolean newStrike = strikethrough;
        Object fam = attrs.get(TextAttribute.FAMILY);
        if (fam instanceof String) newName = (String) fam;
        Object sz = attrs.get(TextAttribute.SIZE);
        if (sz instanceof Number) newSize = ((Number) sz).floatValue();
        Object weight = attrs.get(TextAttribute.WEIGHT);
        if (weight instanceof Number) {
            if (((Number) weight).floatValue() >= TextAttribute.WEIGHT_BOLD) newStyle |= BOLD;
            else newStyle &= ~BOLD;
        }
        Object posture = attrs.get(TextAttribute.POSTURE);
        if (posture instanceof Number) {
            if (((Number) posture).floatValue() > 0.0f) newStyle |= ITALIC;
            else newStyle &= ~ITALIC;
        }
        if (attrs.containsKey(TextAttribute.UNDERLINE)) {
            Object u = attrs.get(TextAttribute.UNDERLINE);
            newUnderline = u instanceof Number ? Math.max(-1, ((Number) u).intValue()) : -1;
        }
        if (attrs.containsKey(TextAttribute.STRIKETHROUGH)) {
            newStrike = Boolean.TRUE.equals(attrs.get(TextAttribute.STRIKETHROUGH));
        }
        Font result = new Font(newName, newStyle, newSize, newUnderline, newStrike);
        result.layoutAttributes.putAll(layoutAttributes);
        for (java.util.Map.Entry<? extends java.text.AttributedCharacterIterator.Attribute, ?> entry : attrs.entrySet()) {
            if (entry.getKey() instanceof TextAttribute) {
                TextAttribute key = (TextAttribute) entry.getKey();
                if (entry.getValue() == null) result.layoutAttributes.remove(key);
                else result.layoutAttributes.put(key, entry.getValue());
            }
        }
        return result;
    }

    public Font deriveFont(java.awt.geom.AffineTransform transform) {
        return deriveFont(java.util.Collections.singletonMap(TextAttribute.TRANSFORM, new java.awt.font.TransformAttribute(java.util.Objects.requireNonNull(transform))));
    }
    public Font deriveFont(int style, java.awt.geom.AffineTransform transform) { return deriveFont(style).deriveFont(transform); }
    public java.awt.geom.AffineTransform getTransform() {
        Object value = layoutAttributes.get(TextAttribute.TRANSFORM);
        java.awt.geom.AffineTransform result = value instanceof java.awt.font.TransformAttribute ? ((java.awt.font.TransformAttribute)value).getTransform() : value instanceof java.awt.geom.AffineTransform ? new java.awt.geom.AffineTransform((java.awt.geom.AffineTransform)value) : new java.awt.geom.AffineTransform();
        Object width = layoutAttributes.get(TextAttribute.WIDTH);
        if (width instanceof Number) result.scale(((Number)width).doubleValue(), 1);
        Object superscript = layoutAttributes.get(TextAttribute.SUPERSCRIPT);
        if (superscript instanceof Number && ((Number)superscript).intValue() != 0) {
            int level = ((Number)superscript).intValue();
            double scale = Math.pow(2d / 3, Math.abs(level));
            result.translate(0, (level > 0 ? -1 : 1) * size * (1 - scale)); result.scale(scale, scale);
        }
        return result;
    }
    public boolean isTransformed() {
        if (layoutAttributes.isEmpty()) return false;
        Object transform = layoutAttributes.get(TextAttribute.TRANSFORM);
        if (transform instanceof java.awt.font.TransformAttribute && !((java.awt.font.TransformAttribute)transform).isIdentity()) return true;
        if (transform instanceof java.awt.geom.AffineTransform && !((java.awt.geom.AffineTransform)transform).isIdentity()) return true;
        Object width = layoutAttributes.get(TextAttribute.WIDTH), superscript = layoutAttributes.get(TextAttribute.SUPERSCRIPT);
        return width instanceof Number && ((Number)width).doubleValue() != 1 || superscript instanceof Number && ((Number)superscript).intValue() != 0;
    }
    public boolean hasLayoutAttributes() { return !layoutAttributes.isEmpty() || underline >= 0 || strikethrough; }
    public float getTracking() { Object value = layoutAttributes.get(TextAttribute.TRACKING); return value instanceof Number ? ((Number)value).floatValue() : 0; }

    public boolean canDisplay(char c){return canDisplay((int)c);}
    public boolean canDisplay(int codePoint){if(!Character.isValidCodePoint(codePoint))throw new IllegalArgumentException("code point");return canDisplayUpTo(new String(Character.toChars(codePoint)))<0;}
    public int canDisplayUpTo(char[] text,int start,int limit){int index=canDisplayUpTo(new String(text,start,limit-start));return index<0?-1:start+index;}
    public String getFamily(java.util.Locale locale){return getFamily();}
    public String getFontName(){return getName();}
    public String getFontName(java.util.Locale locale){return getName();}
    public String getPSName(){return getName().replace(" ","");}
    /**
     * Logical bounds as the JDK lays them out: x 0, y at minus the ascent, the advance as
     * width and ascent + descent + leading as height, so the height matches getLineMetrics.
     */
    public java.awt.geom.Rectangle2D getStringBounds(String text,java.awt.font.FontRenderContext context){
        char[] chars=text.toCharArray();
        return getStringBounds(chars,0,chars.length,context);
    }
    public java.awt.geom.Rectangle2D getStringBounds(String text,int start,int limit,java.awt.font.FontRenderContext context){
        return getStringBounds(text.substring(start,limit),context);
    }
    public java.awt.geom.Rectangle2D getStringBounds(char[] text,int start,int limit,java.awt.font.FontRenderContext context){
        checkRange(start,limit,text.length);
        android.graphics.Paint paint=net.runelite.awt.impl.BufferedImageFontMetrics.paintFor(this);
        android.graphics.Paint.FontMetrics metrics=paint.getFontMetrics();
        float advance=limit>start?paint.measureText(text,start,limit-start):0;
        java.awt.geom.Rectangle2D bounds=new java.awt.geom.Rectangle2D.Float(0,metrics.ascent,advance,metrics.descent-metrics.ascent+metrics.leading);
        return isTransformed()?getTransform().createTransformedShape(bounds).getBounds2D():bounds;
    }
    public java.awt.geom.Rectangle2D getStringBounds(java.text.CharacterIterator ci,int beginIndex,int limit,java.awt.font.FontRenderContext context){
        return getStringBounds(iteratorChars(ci,beginIndex,limit),0,limit-beginIndex,context);
    }
    public java.awt.font.LineMetrics getLineMetrics(String text,java.awt.font.FontRenderContext context){return new net.runelite.awt.impl.AndroidLineMetrics(this,text.length());}
    public java.awt.font.LineMetrics getLineMetrics(String text,int start,int limit,java.awt.font.FontRenderContext context){checkRange(start,limit,text.length());return new net.runelite.awt.impl.AndroidLineMetrics(this,limit-start);}
    public java.awt.font.LineMetrics getLineMetrics(char[] text,int start,int limit,java.awt.font.FontRenderContext context){checkRange(start,limit,text.length);return new net.runelite.awt.impl.AndroidLineMetrics(this,limit-start);}
    public java.awt.font.LineMetrics getLineMetrics(java.text.CharacterIterator ci,int beginIndex,int limit,java.awt.font.FontRenderContext context){
        iteratorChars(ci,beginIndex,limit);
        return new net.runelite.awt.impl.AndroidLineMetrics(this,limit-beginIndex);
    }
    /** The JDK's shape: (0, -ascent, max advance, ascent + descent + leading). */
    public java.awt.geom.Rectangle2D getMaxCharBounds(java.awt.font.FontRenderContext context){
        android.graphics.Paint.FontMetrics metrics=net.runelite.awt.impl.BufferedImageFontMetrics.paintFor(this).getFontMetrics();
        float maxAdvance=net.runelite.awt.impl.BufferedImageFontMetrics.forFont(this).getMaxAdvance();
        return new java.awt.geom.Rectangle2D.Float(0,metrics.ascent,maxAdvance,metrics.descent-metrics.ascent+metrics.leading);
    }
    /** As on the JDK: always false, the safe answer for fonts that may fall back per glyph. */
    public boolean hasUniformLineMetrics(){return false;}

    private static void checkRange(int start,int limit,int length){
        if(start<0)throw new IndexOutOfBoundsException("beginIndex: "+start);
        if(limit>length)throw new IndexOutOfBoundsException("limit: "+limit);
        if(start>limit)throw new IndexOutOfBoundsException("range length: "+(limit-start));
    }
    private static char[] iteratorChars(java.text.CharacterIterator ci,int beginIndex,int limit){
        if(beginIndex<ci.getBeginIndex())throw new IndexOutOfBoundsException("beginIndex: "+beginIndex);
        if(limit>ci.getEndIndex())throw new IndexOutOfBoundsException("limit: "+limit);
        if(beginIndex>limit)throw new IndexOutOfBoundsException("range length: "+(limit-beginIndex));
        char[] chars=new char[limit-beginIndex];
        ci.setIndex(beginIndex);
        for(int i=0;i<chars.length;i++){chars[i]=ci.current();ci.next();}
        return chars;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Font) {
            Font f = (Font) obj;
            return size == f.size && style == f.style && name.equals(f.name)
                && underline == f.underline && strikethrough == f.strikethrough && layoutAttributes.equals(f.layoutAttributes);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ style ^ Float.floatToIntBits(size) ^ ((underline + 1) << 16) ^ (strikethrough ? 0x40000000 : 0) ^ layoutAttributes.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName() + "[family=" + name + ",style=" + style + ",size=" + size + "]";
    }
}
