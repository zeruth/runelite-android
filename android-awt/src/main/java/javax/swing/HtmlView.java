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

import java.awt.*;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;

/** View adapter for the HTML subset already rendered by Android plugin panels. */
public final class HtmlView extends View {
    private float width;
    public HtmlView(Element element) { super(element); }
    private String source() {
        int length = Math.max(0, Math.min(getEndOffset(), getDocument().getLength()) - getStartOffset());
        try { return getDocument().getText(getStartOffset(), length); }
        catch (BadLocationException e) { throw new IllegalStateException(e); }
    }
    private FontMetrics metrics() {
        Container owner = getContainer();
        Font font = owner == null ? new Font("Dialog", Font.PLAIN, 12) : owner.getFont();
        return owner == null ? Toolkit.getDefaultToolkit().getFontMetrics(font) : owner.getFontMetrics(font);
    }
    private HtmlTextRenderer.Layout layout(boolean offsets) {
        String html = source();
        if (offsets) return HtmlTextRenderer.measureWithSourceOffsets(html, metrics(), Math.round(width));
        if (getDocument() instanceof HTMLDocument) html = ((HTMLDocument) getDocument()).getStyleSheet().applyRules(html);
        return HtmlTextRenderer.measure(html, metrics(), Math.round(width));
    }
    @Override public void setSize(float width, float height) { this.width = Math.max(0, width); }
    @Override public float getPreferredSpan(int axis) {
        HtmlTextRenderer.Layout layout = layout(false);
        if (axis == X_AXIS) return layout.width;
        if (axis == Y_AXIS) return layout.height;
        throw new IllegalArgumentException("axis");
    }
    @Override public void paint(Graphics graphics, Shape allocation) {
        Rectangle bounds = allocation.getBounds(); Graphics copy = graphics.create();
        try {
            Container owner = getContainer();
            if (owner != null) copy.setFont(owner.getFont());
            copy.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
            HtmlTextRenderer.paint(copy, layout(false), metrics(), bounds.x, bounds.y, owner == null ? Color.BLACK : owner.getForeground());
        } finally { copy.dispose(); }
    }
    @Override public Shape modelToView(int position, Shape allocation, Position.Bias bias) throws BadLocationException {
        int offset = position - getStartOffset();
        if (offset < 0 || offset > source().length()) throw new BadLocationException("offset", position);
        Rectangle bounds = allocation.getBounds(); FontMetrics fm = metrics();
        HtmlTextRenderer.Layout layout = layout(true);
        Rectangle last = new Rectangle(bounds.x, bounds.y, 1, fm.getHeight());
        for (int row = 0; row < layout.lines.size(); row++) {
            int x = bounds.x;
            for (HtmlTextRenderer.Run run : layout.lines.get(row).runs) {
                for (int i = 0; i < run.text.length();) {
                    int next = i + Character.charCount(run.text.codePointAt(i));
                    Rectangle caret = new Rectangle(x + fm.stringWidth(run.text.substring(0, i)), bounds.y + row * fm.getHeight(), 1, fm.getHeight());
                    if (offset <= run.sourceOffsets[i]) return caret;
                    last = new Rectangle(x + fm.stringWidth(run.text.substring(0, next)), caret.y, 1, caret.height);
                    i = next;
                }
                x += fm.stringWidth(run.text);
            }
        }
        return last;
    }
    @Override public int viewToModel(float x, float y, Shape allocation, Position.Bias[] bias) {
        if (bias != null && bias.length > 0) bias[0] = Position.Bias.Forward;
        Rectangle bounds = allocation.getBounds(); FontMetrics fm = metrics(); HtmlTextRenderer.Layout layout = layout(true);
        int row = Math.max(0, Math.min(layout.lines.size() - 1, (int) ((y - bounds.y) / Math.max(1, fm.getHeight()))));
        int left = bounds.x, end = getStartOffset(); String html = source();
        for (HtmlTextRenderer.Run run : layout.lines.get(row).runs) {
            for (int i = 0; i < run.text.length();) {
                int next = i + Character.charCount(run.text.codePointAt(i));
                int startX = left + fm.stringWidth(run.text.substring(0, i));
                int endX = left + fm.stringWidth(run.text.substring(0, next));
                int source = run.sourceOffsets[i];
                if (x < (startX + endX) / 2.0) return getStartOffset() + source;
                int sourceEnd = source + Character.charCount(html.codePointAt(source));
                if (html.charAt(source) == '&') {
                    int semi = html.indexOf(';', source + 1);
                    if (semi > source && semi - source <= 7) sourceEnd = semi + 1;
                }
                end = getStartOffset() + sourceEnd;
                i = next;
            }
            left += fm.stringWidth(run.text);
        }
        return end;
    }
}
