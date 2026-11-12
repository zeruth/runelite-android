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

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

public class JTextArea extends JTextComponent {
    private static final String uiClassID = "TextAreaUI";
    public String getUIClassID() { return uiClassID; }
    private static final long serialVersionUID = 4255143196853147601L;

    private int rows, columns, tabSize = 8;
    private boolean lineWrap, wrapStyleWord;

    public JTextArea() {}
    public JTextArea(String text) { setText(text); }
    public JTextArea(int rows, int columns) { this.rows = rows; this.columns = columns; }
    public JTextArea(String text, int rows, int columns) { setText(text); this.rows = rows; this.columns = columns; }
    public JTextArea(Document doc) { setDocument(doc); }
    public JTextArea(Document doc, String text, int rows, int columns) { setDocument(doc); setText(text); this.rows = rows; this.columns = columns; }

    public int getRows() { return rows; }
    public void setRows(int rows) { this.rows = rows; invalidate(); }
    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; invalidate(); }
    public int getTabSize() { return tabSize; }
    public void setTabSize(int size) { this.tabSize = size; }
    public boolean getLineWrap() { return lineWrap; }
    public void setLineWrap(boolean wrap) { this.lineWrap = wrap; invalidate(); }
    public boolean getWrapStyleWord() { return wrapStyleWord; }
    public void setWrapStyleWord(boolean word) { this.wrapStyleWord = word; invalidate(); }
    public void append(String str) { insert(str,getDocument().getLength()); }
    public void insert(String str,int pos) { try { getDocument().insertString(pos,str,null); } catch(javax.swing.text.BadLocationException e) { throw new IllegalArgumentException(e); } }
    public void replaceRange(String str,int start,int end) { if(end<start)throw new IllegalArgumentException("range");try { ((javax.swing.text.AbstractDocument)getDocument()).replace(start,end-start,str,null); }catch(javax.swing.text.BadLocationException e){throw new IllegalArgumentException(e);} }

    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Dimension size = plainPreferredSize(lineWrap, wrapStyleWord, tabSize);
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        java.awt.Insets in = getInsets();
        if (columns > 0) size.width = Math.max(size.width, columns * fm.charWidth('m') + in.left + in.right);
        if (rows > 0) size.height = Math.max(size.height, rows * fm.getHeight() + in.top + in.bottom);
        return size;
    }
    @Override protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        paintPlainText(g, lineWrap, wrapStyleWord, tabSize);
    }
}
