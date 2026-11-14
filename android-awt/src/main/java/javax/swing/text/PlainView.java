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
package javax.swing.text;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** Text view for the existing Android renderer, with document-relative hit testing. */
public class PlainView extends View {
    private float width;
    public PlainView(Element element) { super(element); }
    protected FontMetrics getFontMetrics() {
        Container owner = getContainer();
        Font font = owner == null ? new Font("Dialog", Font.PLAIN, 12) : owner.getFont();
        return owner == null ? Toolkit.getDefaultToolkit().getFontMetrics(font) : owner.getFontMetrics(font);
    }
    private String text() {
        int end = Math.min(getDocument().getLength(), getEndOffset());
        try { return getDocument().getText(getStartOffset(), Math.max(0, end - getStartOffset())); }
        catch (BadLocationException e) { throw new IllegalStateException(e); }
    }
    private List<Integer> starts(String text) {
        List<Integer> starts = new ArrayList<>(); starts.add(0);
        Container owner = getContainer();
        boolean wrap = !(owner instanceof JTextField) && (!(owner instanceof JTextArea) || ((JTextArea) owner).getLineWrap());
        FontMetrics fm = getFontMetrics();
        int line = 0;
        for (int i = 0; i < text.length();) {
            int next = i + Character.charCount(text.codePointAt(i));
            if (text.charAt(i) == '\n') { starts.add(next); line = next; }
            else if (wrap && width > 0 && i > line && fm.stringWidth(text.substring(line, next)) > width) { starts.add(i); line = i; }
            i = next;
        }
        return starts;
    }
    @Override public void setSize(float width, float height) { this.width = width; }
    @Override public float getPreferredSpan(int axis) {
        String text = text(); List<Integer> starts = starts(text); FontMetrics fm = getFontMetrics();
        if (axis == Y_AXIS) return starts.size() * fm.getHeight();
        if (axis != X_AXIS) throw new IllegalArgumentException("axis");
        int span = 0;
        for (int row = 0; row < starts.size(); row++) {
            int end = row + 1 == starts.size() ? text.length() : starts.get(row + 1);
            span = Math.max(span, fm.stringWidth(text.substring(starts.get(row), end).replace("\n", "")));
        }
        return span;
    }
    @Override public void paint(Graphics graphics, Shape allocation) {
        Rectangle bounds = allocation.getBounds();
        Graphics copy = graphics.create();
        try {
            copy.clipRect(bounds.x, bounds.y, bounds.width, bounds.height);
            Container owner = getContainer();
            if (owner != null) { copy.setFont(owner.getFont()); copy.setColor(owner.getForeground()); }
            String text = text(); List<Integer> starts = starts(text); FontMetrics fm = getFontMetrics();
            for (int row = 0; row < starts.size(); row++) {
                int end = row + 1 == starts.size() ? text.length() : starts.get(row + 1);
                copy.drawString(text.substring(starts.get(row), end).replace("\n", ""), bounds.x, bounds.y + row * fm.getHeight() + fm.getAscent());
            }
        } finally { copy.dispose(); }
    }
    @Override public Shape modelToView(int position, Shape allocation, Position.Bias bias) throws BadLocationException {
        String text = text(); int offset = position - getStartOffset();
        if (offset < 0 || offset > text.length()) throw new BadLocationException("offset", position);
        List<Integer> starts = starts(text); int row = 0;
        while (row + 1 < starts.size() && (starts.get(row + 1) < offset || starts.get(row + 1) == offset && bias != Position.Bias.Backward)) row++;
        Rectangle bounds = allocation.getBounds(); FontMetrics fm = getFontMetrics();
        return new Rectangle(bounds.x + fm.stringWidth(text.substring(starts.get(row), offset).replace("\n", "")), bounds.y + row * fm.getHeight(), 1, fm.getHeight());
    }
    @Override public int viewToModel(float x, float y, Shape allocation, Position.Bias[] biasReturn) {
        if (biasReturn != null && biasReturn.length > 0) biasReturn[0] = Position.Bias.Forward;
        Rectangle bounds = allocation.getBounds(); FontMetrics fm = getFontMetrics(); String text = text(); List<Integer> starts = starts(text);
        int row = Math.max(0, Math.min(starts.size() - 1, (int) ((y - bounds.y) / Math.max(1, fm.getHeight()))));
        int start = starts.get(row), end = row + 1 == starts.size() ? text.length() : starts.get(row + 1);
        if (end > start && text.charAt(end - 1) == '\n') end--;
        int previous = 0;
        for (int i = start; i < end;) {
            int next = i + Character.charCount(text.codePointAt(i)); int right = fm.stringWidth(text.substring(start, next));
            if (x - bounds.x < (previous + right) / 2.0) return getStartOffset() + i;
            previous = right; i = next;
        }
        return getStartOffset() + end;
    }
}
