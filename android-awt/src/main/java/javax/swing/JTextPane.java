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
package javax.swing;

import javax.swing.text.*;
import java.awt.*;

public class JTextPane extends JEditorPane {
    private java.util.Set<Component> embeddedComponents = new java.util.HashSet<>();
    private final MutableAttributeSet inputAttributes = new SimpleAttributeSet();
    public String getUIClassID() { return "TextPaneUI"; }
    public JTextPane() {
        setDocument(new DefaultStyledDocument());
        addCaretListener(e -> updateInputAttributes());
    }
    public JTextPane(Document doc) { this(); if (doc != null) setDocument(doc); }
    public JTextPane(StyledDocument doc) { this((Document) doc); }
    public StyledDocument getStyledDocument() { return (StyledDocument) getDocument(); }
    public void setStyledDocument(StyledDocument doc) { setDocument(doc); }
    public AttributeSet getCharacterAttributes() {
        int offset = Math.min(getCaretPosition(), getDocument().getLength());
        return getStyledDocument().getCharacterElement(offset).getAttributes();
    }
    public MutableAttributeSet getInputAttributes() { return inputAttributes; }
    private void updateInputAttributes() {
        if (inputAttributes == null || !(getDocument() instanceof StyledDocument)) return;
        int start = getSelectionStart();
        AttributeSet attrs = getStyledDocument().getCharacterElement(start == getSelectionEnd() ? Math.max(0, start - 1) : start).getAttributes();
        inputAttributes.removeAttributes(inputAttributes);
        inputAttributes.addAttributes(attrs);
        inputAttributes.removeAttribute(StyleConstants.ComponentAttribute);
        inputAttributes.removeAttribute(StyleConstants.IconAttribute);
        inputAttributes.removeAttribute(StyleConstants.NameAttribute);
    }
    public void setCharacterAttributes(AttributeSet attrs, boolean replace) {
        int start = getSelectionStart(), length = getSelectionEnd() - start;
        if (length != 0) getStyledDocument().setCharacterAttributes(start, length, attrs, replace);
        if (replace) inputAttributes.removeAttributes(inputAttributes);
        inputAttributes.addAttributes(attrs);
        revalidate(); repaint();
    }
    @Override public void replaceSelection(String value) {
        if (!isEditable()) return;
        replaceStyledSelection(value, new SimpleAttributeSet(inputAttributes));
    }
    private void replaceStyledSelection(String value, AttributeSet attrs) {
        int start = getSelectionStart(), length = getSelectionEnd() - start;
        try {
            Document doc = getDocument();
            if (doc instanceof AbstractDocument) ((AbstractDocument) doc).replace(start, length, value, attrs);
            else { doc.remove(start, length); doc.insertString(start, value, attrs); }
            setCaretPosition(start + (value == null ? 0 : value.length()));
        } catch (BadLocationException e) { throw new IllegalArgumentException(e); }
    }
    public void insertIcon(Icon icon) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setIcon(attrs, icon);
        replaceStyledSelection(" ", attrs);
    }
    public void insertComponent(Component component) {
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setComponent(attrs, component);
        replaceStyledSelection(" ", attrs);
        if (component != null && component.getParent() != this) add(component);
    }
    private Font styledFont(AttributeSet attrs) {
        Font base = getFont();
        String family = attrs.isDefined(StyleConstants.FontFamily) ? StyleConstants.getFontFamily(attrs) : base.getFamily();
        int size = attrs.isDefined(StyleConstants.FontSize) ? StyleConstants.getFontSize(attrs) : base.getSize();
        return new Font(family, (StyleConstants.isBold(attrs) ? Font.BOLD : 0) | (StyleConstants.isItalic(attrs) ? Font.ITALIC : 0), size);
    }
    private static final class Layout {
        final Rectangle[] positions;
        int width, height;
        Layout(int length) { positions = new Rectangle[length + 1]; }
    }
    private Layout layoutStyled(Graphics graphics) {
        String text = getText();
        Insets in = getInsets();
        int available = Math.max(1, getWidth() - in.left - in.right);
        int x = 0, y = in.top, lineHeight = getFontMetrics(getFont()).getHeight();
        Layout layout = new Layout(text.length());
        java.util.Set<Component> embedded = new java.util.HashSet<>();
        for (int i = 0; i < text.length();) {
            AttributeSet attrs = getStyledDocument().getCharacterElement(i).getAttributes();
            Font font = styledFont(attrs);
            FontMetrics fm = getFontMetrics(font);
            Icon icon = StyleConstants.getIcon(attrs);
            Component child = StyleConstants.getComponent(attrs);
            int next = i + Character.charCount(text.codePointAt(i));
            String glyph = text.substring(i, next);
            Dimension childSize = child == null ? null : child.getPreferredSize();
            int width = childSize != null ? childSize.width : icon != null ? icon.getIconWidth() : fm.stringWidth(glyph);
            int height = childSize != null ? childSize.height : icon != null ? icon.getIconHeight() : fm.getHeight();
            boolean newline = text.charAt(i) == '\n';
            if (!newline && x > 0 && x + width > available) { y += lineHeight; x = 0; lineHeight = fm.getHeight(); }
            lineHeight = Math.max(lineHeight, height);
            Rectangle bounds = new Rectangle(in.left + x, y, newline ? 1 : Math.max(1, width), lineHeight);
            for (int pos = i; pos < next; pos++) layout.positions[pos] = bounds;
            if (child != null) {
                embedded.add(child);
                if (child.getParent() != this) add(child);
                child.setBounds(bounds.x, bounds.y, width, height);
            }
            if (graphics != null && !newline) {
                if (getCaret().isSelectionVisible() && i >= getSelectionStart() && i < getSelectionEnd()) {
                    graphics.setColor(getSelectionColor()); graphics.fillRect(bounds.x, bounds.y, width, lineHeight);
                }
                if (icon != null) icon.paintIcon(this, graphics, bounds.x, bounds.y);
                else if (child == null) {
                    graphics.setFont(font);
                    Object color = attrs.getAttribute(StyleConstants.Foreground);
                    graphics.setColor(color instanceof Color ? (Color) color : getForeground());
                    graphics.drawString(glyph, bounds.x, bounds.y + fm.getAscent());
                }
            }
            if (newline) { layout.width = Math.max(layout.width, x); y += lineHeight; x = 0; lineHeight = fm.getHeight(); }
            else x += width;
            layout.width = Math.max(layout.width, x);
            i = next;
        }
        for (Component child : embeddedComponents) if (!embedded.contains(child) && child.getParent() == this) remove(child);
        embeddedComponents = embedded;
        layout.positions[text.length()] = new Rectangle(in.left + x, y, 1, lineHeight);
        layout.width += in.left + in.right;
        layout.height = y + lineHeight + in.bottom;
        return layout;
    }
    @Override public Rectangle modelToView(int offset) throws BadLocationException {
        if (!(getDocument() instanceof StyledDocument) || getDocument() instanceof javax.swing.text.html.HTMLDocument) return super.modelToView(offset);
        if (offset < 0 || offset > getDocument().getLength()) throw new BadLocationException("offset", offset);
        Rectangle bounds = new Rectangle(layoutStyled(null).positions[offset]);
        bounds.width = 1;
        return bounds;
    }
    @Override public int viewToModel(Point point) {
        if (!(getDocument() instanceof StyledDocument) || getDocument() instanceof javax.swing.text.html.HTMLDocument) return super.viewToModel(point);
        Layout layout = layoutStyled(null);
        int best = 0; double distance = Double.MAX_VALUE;
        for (int i = 0; i < layout.positions.length; i++) {
            Rectangle r = layout.positions[i];
            double dy = point.y < r.y ? r.y - point.y : point.y >= r.y + r.height ? point.y - r.y - r.height + 1 : 0;
            double score = dy * (getWidth() + 1.0) + Math.abs(point.x - r.x);
            if (score < distance) { distance = score; best = i; }
        }
        return best;
    }
    @Override public Dimension getPreferredSize() {
        if (isPreferredSizeSet() || !(getDocument() instanceof StyledDocument) || getDocument() instanceof javax.swing.text.html.HTMLDocument) return super.getPreferredSize();
        Layout layout = layoutStyled(null);
        return new Dimension(layout.width, layout.height);
    }
    @Override protected void paintComponent(Graphics graphics) {
        if (!(getDocument() instanceof StyledDocument) || getDocument() instanceof javax.swing.text.html.HTMLDocument) { super.paintComponent(graphics); return; }
        Graphics copy = graphics.create();
        try {
            if (isOpaque()) { copy.setColor(getBackground()); copy.fillRect(0, 0, getWidth(), getHeight()); }
            if (getHighlighter() != null) getHighlighter().paint(copy);
            layoutStyled(copy);
            getCaret().paint(copy);
        } finally { copy.dispose(); }
    }
}
