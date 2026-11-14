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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

public class JTextField extends JTextComponent {
    private static final String uiClassID = "TextFieldUI";
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJTextField();
        }
        return accessibleContext;
    }

    /** Accessibility support for text fields: a TEXT context that is SINGLE_LINE. */
    protected class AccessibleJTextField extends AccessibleJTextComponent {
        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
            javax.accessibility.AccessibleStateSet states = super.getAccessibleStateSet();
            states.add(javax.accessibility.AccessibleState.SINGLE_LINE);
            return states;
        }
    }
    private static final long serialVersionUID = 353936840712925302L;

    public static final String notifyAction = "notify-field-accept";

    private int columns;
    private String actionCommand;
    private int scrollOffset;
    private int horizontalAlignment = SwingConstants.LEADING;
    private final java.util.List<ActionListener> actionListeners = new java.util.ArrayList<>();

    public JTextField() {}
    public JTextField(String text) { setText(text); }
    public JTextField(int columns) { this.columns = columns; }
    public JTextField(String text, int columns) { setText(text); this.columns = columns; }
    public JTextField(Document doc, String text, int columns) { setDocument(doc); setText(text); this.columns = columns; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; cachedPref = null; invalidate(); }

    public int getHorizontalAlignment() { return horizontalAlignment; }
    public void setHorizontalAlignment(int alignment) { this.horizontalAlignment = alignment; }

    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public void postActionEvent() {
        ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, actionCommand == null ? getText() : actionCommand);
        for (ActionListener l : new java.util.ArrayList<>(actionListeners)) {
            try { l.actionPerformed(ev); } catch (Throwable ignored) {}
        }
    }

    public void setActionCommand(String command) { actionCommand = command; }
    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int offset) {
        int available = Math.max(0, textWidth() - 8);
        int maximum = Math.max(0, getFontMetrics(getFont()).stringWidth(getText()) - available);
        scrollOffset = Math.max(0, Math.min(offset, maximum));
        repaint();
    }
    private int textOriginX(java.awt.FontMetrics fm) {
        java.awt.Insets in = getInsets();
        int available = Math.max(0, textWidth() - 8);
        int width = fm.stringWidth(getText());
        int alignment = horizontalAlignment;
        if (alignment == SwingConstants.LEADING) alignment = getComponentOrientation().isLeftToRight() ? SwingConstants.LEFT : SwingConstants.RIGHT;
        if (alignment == SwingConstants.TRAILING) alignment = getComponentOrientation().isLeftToRight() ? SwingConstants.RIGHT : SwingConstants.LEFT;
        int padding = width < available ? (alignment == SwingConstants.CENTER ? (available - width) / 2 : alignment == SwingConstants.RIGHT ? available - width : 0) : 0;
        return in.left + 4 + padding - Math.min(scrollOffset, Math.max(0, width - available));
    }
    @Override public java.awt.Rectangle modelToView(int offset) throws javax.swing.text.BadLocationException {
        if (offset < 0 || offset > getDocument().getLength()) throw new javax.swing.text.BadLocationException("offset", offset);
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        java.awt.Insets in = getInsets();
        int y = in.top + (getHeight() - in.top - in.bottom - fm.getHeight()) / 2;
        return new java.awt.Rectangle(textOriginX(fm) + fm.stringWidth(getDocument().getText(0, offset)), y, 1, fm.getHeight());
    }
    @Override public int viewToModel(java.awt.Point point) {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        String value = getText();
        int x = point.x - textOriginX(fm), previous = 0;
        for (int i = 0; i < value.length();) {
            int next = i + Character.charCount(value.codePointAt(i));
            int right = fm.stringWidth(value.substring(0, next));
            if (x < (previous + right) / 2.0) return i;
            previous = right; i = next;
        }
        return value.length();
    }

    private java.awt.Dimension cachedPref;
    @Override public void invalidate() { cachedPref = null; super.invalidate(); }
    @Override
    public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Dimension c = cachedPref;
        if (c != null) return new java.awt.Dimension(c);
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        int colW = columns > 0
            ? columns * fm.charWidth('m')
            : Math.max(60, fm.stringWidth(getText() == null ? "" : getText()) + 8);
        java.awt.Insets in = getInsets();
        c = new java.awt.Dimension(colW + 4 + in.left + in.right, fm.getHeight() + 4 + in.top + in.bottom);
        cachedPref = c;
        return new java.awt.Dimension(c);
    }

    @Override
    public void setText(String t) { super.setText(t); cachedPref = null; invalidate(); }
    @Override
    public void setFont(java.awt.Font f) { super.setFont(f); cachedPref = null; invalidate(); }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        String s = getText();
        if (s == null || s.isEmpty()) return;
        java.awt.Graphics copy = g.create();
        try {
            java.awt.FontMetrics fm = copy.getFontMetrics(getFont());
            copy.setColor(getForeground()); copy.setFont(getFont());
            java.awt.Insets in = getInsets();
            int x = textOriginX(fm);
            copy.clipRect(in.left, in.top, textWidth(), Math.max(0, getHeight() - in.top - in.bottom));
            int baseline = in.top + (getHeight() - in.top - in.bottom - fm.getHeight()) / 2 + fm.getAscent();
            copy.drawString(s, x, baseline);
            paintComposedUnderline(copy, s, 0, null, x, baseline);
        } finally { copy.dispose(); }
    }
}
