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

import javax.swing.JComponent;
import javax.swing.event.CaretListener;

public abstract class JTextComponent extends JComponent {
    private static final long serialVersionUID = -2911697971489974478L;

    public static final String DEFAULT_KEYMAP = "default";
    public static final String FOCUS_ACCELERATOR_KEY = "focusAcceleratorKey";

    protected Document doc;
    protected String text = "";
    private java.awt.Insets margin = new java.awt.Insets(0, 0, 0, 0);

    // Desktop text UIs install a border that reads the margin. Keep that contract
    // without adding padding to existing Android controls or to custom borders.
    private static final class MarginBorder extends javax.swing.border.AbstractBorder implements javax.swing.plaf.UIResource {
        private static final long serialVersionUID = 1L;
        @Override public java.awt.Insets getBorderInsets(java.awt.Component component) {
            return getBorderInsets(component, new java.awt.Insets(0, 0, 0, 0));
        }
        @Override public java.awt.Insets getBorderInsets(java.awt.Component component, java.awt.Insets insets) {
            java.awt.Insets value = ((JTextComponent) component).getMargin();
            if (value == null) insets.set(0, 0, 0, 0);
            else insets.set(value.top, value.left, value.bottom, value.right);
            return insets;
        }
    }
    private final javax.swing.event.DocumentListener documentListener = new DocumentRepaintListener();
    private final class DocumentRepaintListener implements javax.swing.event.DocumentListener, java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public void insertUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { revalidate(); repaint(); }
    }

    public JTextComponent() {
        setBorder(new MarginBorder());
        this.doc = new PlainDocument();
        this.doc.addDocumentListener(documentListener);
        setCaret(new DefaultCaret());
        setHighlighter(new DefaultHighlighter());
    }

    public java.awt.Insets getMargin() { return margin; }
    public void setMargin(java.awt.Insets value) {
        java.awt.Insets old = margin;
        margin = value;
        firePropertyChange("margin", old, value);
        revalidate();
        repaint();
    }

    public String getText() {
        if (doc != null) {
            try { return doc.getText(0, doc.getLength()); } catch (BadLocationException ignored) {}
        }
        return text;
    }
    public void setText(String t) {
        // Text is an input to every text component's preferred size.
        invalidate();
        this.text = t == null ? "" : t;
        if (doc != null) {
            try {
                doc.remove(0, doc.getLength());
                doc.insertString(0, this.text, null);
            } catch (BadLocationException ignored) {}
        }
    }

    public Document getDocument() { return doc; }
    public void setDocument(Document d) {
        if(d==null)throw new IllegalArgumentException("document");Document old=doc;
        if(old!=null)old.removeDocumentListener(documentListener);
        if(inputMethodRequestsHandler!=null){if(old!=null)old.removeDocumentListener(inputMethodRequestsHandler);d.addDocumentListener(inputMethodRequestsHandler);}
        composedTextStart=composedTextEnd=latestCommittedTextStart=latestCommittedTextEnd=null;
        if(caret!=null&&old!=null)caret.deinstall(this);doc=d;doc.addDocumentListener(documentListener);if(caret!=null)caret.install(this);
        if(highlighter!=null)highlighter.install(this);firePropertyChange("document",old,d);revalidate();repaint();
    }
    public String getText(int offset,int length) throws BadLocationException { return doc.getText(offset,length); }

    /** Shared plain-text measurement for multiline Swing controls. */
    protected java.util.List<String> textLines(int width, boolean wrap, boolean words, int tabs) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        layoutLines(width, wrap, words, tabs, lines, null);
        return lines;
    }
    /** Splits the text into painted lines; {@code offsets}, when given, receives per line the
     *  document offset of each painted character plus the line end (tabs expand to several). */
    private void layoutLines(int width, boolean wrap, boolean words, int tabs, java.util.List<String> lines, java.util.List<int[]> offsets) {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        String value = getText();
        int tab = Math.max(1, tabs), length = value.length();
        for (int start = 0;;) {
            int stop = start;
            while (stop < length && value.charAt(stop) != '\n' && value.charAt(stop) != '\r') stop++;
            StringBuilder expanded = new StringBuilder();
            int[] source = offsets == null ? null : new int[(stop - start) * tab + 1];
            for (int i = start; i < stop; i++) {
                char ch = value.charAt(i);
                int count = ch == '\t' ? tab - expanded.length() % tab : 1;
                while (count-- > 0) {
                    if (source != null) source[expanded.length()] = i;
                    expanded.append(ch == '\t' ? ' ' : ch);
                }
            }
            if (source != null) source[expanded.length()] = stop;
            String paragraph = expanded.toString();
            if (!wrap || width <= 0 || paragraph.isEmpty()) {
                lines.add(paragraph);
                if (offsets != null) offsets.add(java.util.Arrays.copyOf(source, paragraph.length() + 1));
            } else {
                for (int from = 0; from < paragraph.length();) {
                    String remaining = paragraph.substring(from);
                    int end = 0, lastSpace = -1;
                    while (end < remaining.length()) {
                        int next = end + Character.charCount(remaining.codePointAt(end));
                        if (end > 0 && fm.stringWidth(remaining.substring(0, next)) > width) break;
                        if (Character.isWhitespace(remaining.charAt(end))) lastSpace = next;
                        end = next;
                    }
                    if (words && end < remaining.length() && lastSpace > 0) end = lastSpace;
                    lines.add(remaining.substring(0, end));
                    if (offsets != null) offsets.add(java.util.Arrays.copyOfRange(source, from, from + end + 1));
                    from += end;
                }
            }
            if (stop >= length) break;
            start = stop + (value.charAt(stop) == '\r' && stop + 1 < length && value.charAt(stop + 1) == '\n' ? 2 : 1);
        }
    }
    protected int textWidth() {
        java.awt.Insets in = getInsets();
        return Math.max(0, getWidth() - in.left - in.right);
    }
    protected java.awt.Dimension plainPreferredSize(boolean wrap, boolean words, int tabs) {
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        java.awt.Insets in = getInsets();
        java.util.List<String> lines = textLines(textWidth(), wrap, words, tabs);
        int width = 0;
        for (String line : lines) width = Math.max(width, fm.stringWidth(line));
        return new java.awt.Dimension(width + in.left + in.right, lines.size() * fm.getHeight() + in.top + in.bottom);
    }
    protected void paintPlainText(java.awt.Graphics g, boolean wrap, boolean words, int tabs) {
        java.awt.Graphics copy = g.create();
        try {
            java.awt.Insets in = getInsets();
            copy.clipRect(in.left, in.top, textWidth(), Math.max(0, getHeight() - in.top - in.bottom));
            if(highlighter!=null)highlighter.paint(copy);
            if(caret!=null&&caret.isSelectionVisible()&&getSelectionStart()!=getSelectionEnd())new DefaultHighlighter.DefaultHighlightPainter(getSelectionColor()).paint(copy,getSelectionStart(),getSelectionEnd(),new java.awt.Rectangle(0,0,getWidth(),getHeight()),this);
            copy.setFont(getFont()); copy.setColor(isEnabled()?getForeground():getDisabledTextColor());
            java.awt.FontMetrics fm = getFontMetrics(getFont());
            int y = in.top + fm.getAscent();
            java.util.List<String> lines = new java.util.ArrayList<>();
            java.util.List<int[]> offsets = composedTextExists() ? new java.util.ArrayList<int[]>() : null;
            layoutLines(textWidth(), wrap, words, tabs, lines, offsets);
            for (int i = 0; i < lines.size(); i++) {
                copy.drawString(lines.get(i), in.left, y);
                if (offsets != null) paintComposedUnderline(copy, lines.get(i), 0, offsets.get(i), in.left, y);
                y += fm.getHeight();
            }
            if(caret!=null)caret.paint(copy);
        } finally { copy.dispose(); }
    }

    private boolean editable = true;
    public boolean isEditable() { return editable; }
    public void setEditable(boolean b) { editable = b; }

    private Caret caret;
    private Highlighter highlighter;
    private java.awt.Color selectedTextColor=java.awt.Color.WHITE,selectionColor=java.awt.Color.BLUE,disabledTextColor=java.awt.Color.GRAY,caretColor=java.awt.Color.BLACK;
    private final javax.swing.event.EventListenerList caretListeners=new javax.swing.event.EventListenerList();
    private final javax.swing.event.ChangeListener caretChanged=e -> {
        javax.swing.event.CaretEvent event=new javax.swing.event.CaretEvent(this){public int getDot(){return caret.getDot();}public int getMark(){return caret.getMark();}};
        for(CaretListener listener:caretListeners.getListeners(CaretListener.class))listener.caretUpdate(event);
        repaint();
    };
    public void selectAll(){select(0,doc.getLength());}
    public void select(int start,int end){start=Math.max(0,Math.min(start,doc.getLength()));end=Math.max(start,Math.min(end,doc.getLength()));caret.setDot(start);caret.moveDot(end);}
    public String getSelectedText(){int start=getSelectionStart(),end=getSelectionEnd();if(start==end)return null;try{return doc.getText(start,end-start);}catch(BadLocationException e){throw new IllegalStateException(e);}}
    public int getSelectionStart(){return Math.min(caret.getDot(),caret.getMark());}public int getSelectionEnd(){return Math.max(caret.getDot(),caret.getMark());}
    public void setSelectionStart(int value){select(value,Math.max(value,getSelectionEnd()));}public void setSelectionEnd(int value){select(Math.min(getSelectionStart(),value),value);}
    public void setCaretPosition(int position){if(position<0||position>doc.getLength())throw new IllegalArgumentException("caret position");caret.setDot(position);}
    public void moveCaretPosition(int position){if(position<0||position>doc.getLength())throw new IllegalArgumentException("caret position");caret.moveDot(position);}
    public int getCaretPosition(){return caret.getDot();}
    public void addCaretListener(CaretListener listener){caretListeners.add(CaretListener.class,listener);}public void removeCaretListener(CaretListener listener){caretListeners.remove(CaretListener.class,listener);}
    public CaretListener[] getCaretListeners(){return caretListeners.getListeners(CaretListener.class);}
    public Caret getCaret(){return caret;}public Highlighter getHighlighter(){return highlighter;}
    public void setHighlighter(Highlighter value){Highlighter old=highlighter;if(old!=null)old.deinstall(this);highlighter=value;if(value!=null)value.install(this);firePropertyChange("highlighter",old,value);repaint();}
    public void setCaret(Caret value){if(value==null)throw new IllegalArgumentException("caret");Caret old=caret;if(old!=null){old.removeChangeListener(caretChanged);old.deinstall(this);}caret=value;value.install(this);value.addChangeListener(caretChanged);firePropertyChange("caret",old,value);repaint();}
    public void setSelectedTextColor(java.awt.Color value){java.awt.Color old=selectedTextColor;selectedTextColor=value;firePropertyChange("selectedTextColor",old,value);repaint();}public java.awt.Color getSelectedTextColor(){return selectedTextColor;}
    public void setSelectionColor(java.awt.Color value){java.awt.Color old=selectionColor;selectionColor=value;firePropertyChange("selectionColor",old,value);repaint();}public java.awt.Color getSelectionColor(){return selectionColor;}
    public void setDisabledTextColor(java.awt.Color value){java.awt.Color old=disabledTextColor;disabledTextColor=value;firePropertyChange("disabledTextColor",old,value);repaint();}public java.awt.Color getDisabledTextColor(){return disabledTextColor;}
    public void setCaretColor(java.awt.Color value){java.awt.Color old=caretColor;caretColor=value;firePropertyChange("caretColor",old,value);repaint();}public java.awt.Color getCaretColor(){return caretColor;}
    public void replaceSelection(String value){if(!isEditable())return;int start=getSelectionStart(),length=getSelectionEnd()-start;try{if(doc instanceof AbstractDocument)((AbstractDocument)doc).replace(start,length,value,null);else{doc.remove(start,length);doc.insertString(start,value,null);}setCaretPosition(Math.min(doc.getLength(),start+(value==null?0:value.length())));}catch(BadLocationException e){throw new IllegalArgumentException(e);}}
    public void copy(){String value=getSelectedText();if(value!=null)getToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(value),null);}
    public void cut(){if(isEditable()){copy();replaceSelection("");}}
    public void paste(){if(!isEditable())return;try{java.awt.datatransfer.Transferable value=getToolkit().getSystemClipboard().getContents(this);if(value!=null&&value.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor))replaceSelection((String)value.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));}catch(java.awt.datatransfer.UnsupportedFlavorException|java.io.IOException ignored){}}
    public java.awt.Rectangle modelToView(int offset)throws BadLocationException{if(offset<0||offset>doc.getLength())throw new BadLocationException("offset",offset);String before=doc.getText(0,offset);int newline=before.lastIndexOf('\n');int row=0;for(int i=0;i<before.length();i++)if(before.charAt(i)=='\n')row++;java.awt.FontMetrics fm=getFontMetrics(getFont());java.awt.Insets in=getInsets();return new java.awt.Rectangle(in.left+fm.stringWidth(before.substring(newline+1)),in.top+row*fm.getHeight(),1,fm.getHeight());}
    public int viewToModel(java.awt.Point point){java.awt.FontMetrics fm=getFontMetrics(getFont());java.awt.Insets in=getInsets();int row=Math.max(0,(point.y-in.top)/Math.max(1,fm.getHeight()));String value=getText();int start=0;while(row-->0){int n=value.indexOf('\n',start);if(n<0)return value.length();start=n+1;}int end=value.indexOf('\n',start);if(end<0)end=value.length();for(int i=start;i<end;i++)if(fm.stringWidth(value.substring(start,i+1))>point.x-in.left)return i;return end;}

    // Input method support, after the JDK: composed text is inserted into the document between
    // two positions (so getText() and the views include it, as on desktop) and replaced by each
    // following InputMethodEvent; committed text reaches key listeners as KEY_TYPED events
    // unless the component handles input method events itself.
    private Position composedTextStart, composedTextEnd;
    private Position latestCommittedTextStart, latestCommittedTextEnd;
    private InputMethodRequestsHandler inputMethodRequestsHandler;
    private boolean checkedInputOverride, needToSendKeyTypedEvent;

    @Override public java.awt.im.InputMethodRequests getInputMethodRequests() {
        if (inputMethodRequestsHandler == null) {
            inputMethodRequestsHandler = new InputMethodRequestsHandler();
            if (doc != null) doc.addDocumentListener(inputMethodRequestsHandler);
        }
        return inputMethodRequestsHandler;
    }

    @Override public void addInputMethodListener(java.awt.event.InputMethodListener l) {
        super.addInputMethodListener(l);
        if (l != null) {
            needToSendKeyTypedEvent = false;
            checkedInputOverride = true;
        }
    }

    @Override protected void processInputMethodEvent(java.awt.event.InputMethodEvent e) {
        super.processInputMethodEvent(e);
        if (e.isConsumed() || !isEditable()) return;
        switch (e.getID()) {
            case java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED:
                replaceInputMethodText(e);
                // fall through
            case java.awt.event.InputMethodEvent.CARET_POSITION_CHANGED:
                setInputMethodCaretPosition(e);
                break;
            default:
                break;
        }
        e.consume();
    }

    private boolean composedTextExists() { return composedTextStart != null; }

    private void replaceInputMethodText(java.awt.event.InputMethodEvent e) {
        int commitCount = e.getCommittedCharacterCount();
        java.text.AttributedCharacterIterator text = e.getText();
        if (composedTextExists()) {
            int start = composedTextStart.getOffset();
            try { doc.remove(start, composedTextEnd.getOffset() - start); } catch (BadLocationException ignored) {}
            composedTextStart = composedTextEnd = null;
        }
        if (text == null) return;
        text.first();
        int committedStart = 0, committedEnd = 0;
        if (commitCount > 0) {
            committedStart = caret.getDot();
            if (shouldSynthesizeKeyEvents()) {
                for (char c = text.current(); commitCount > 0; c = text.next(), commitCount--) {
                    java.awt.event.KeyEvent typed = new java.awt.event.KeyEvent(this, java.awt.event.KeyEvent.KEY_TYPED,
                        java.awt.EventQueue.getMostRecentEventTime(), 0, java.awt.event.KeyEvent.VK_UNDEFINED, c);
                    // Listeners first, then bindings, then the default typed action: processKeyEvent's order.
                    dispatchKeyEvent(typed);
                    if (!typed.isConsumed() && !hostProcessKeyBinding(typed)) insertTypedText(String.valueOf(c));
                }
            } else {
                StringBuilder committed = new StringBuilder();
                for (char c = text.current(); commitCount > 0; c = text.next(), commitCount--) committed.append(c);
                insertTypedText(committed.toString());
            }
            committedEnd = caret.getDot();
        }
        int composedIndex = text.getIndex();
        if (composedIndex < text.getEndIndex()) {
            StringBuilder composed = new StringBuilder();
            for (char c = text.current(); c != java.text.CharacterIterator.DONE; c = text.next()) composed.append(c);
            SimpleAttributeSet attributes = new SimpleAttributeSet();
            attributes.addAttribute(StyleConstants.ComposedTextAttribute,
                new java.text.AttributedString(text, composedIndex, text.getEndIndex()));
            try {
                if (getSelectionStart() != getSelectionEnd()) replaceSelection("");
                int at = caret.getDot();
                doc.insertString(at, composed.toString(), attributes);
                composedTextStart = doc.createPosition(at);
                composedTextEnd = doc.createPosition(at + composed.length());
            } catch (BadLocationException ble) {
                composedTextStart = composedTextEnd = null;
            }
        }
        try {
            if (committedStart != committedEnd) {
                latestCommittedTextStart = doc.createPosition(committedStart);
                latestCommittedTextEnd = doc.createPosition(committedEnd);
            } else {
                latestCommittedTextStart = latestCommittedTextEnd = null;
            }
        } catch (BadLocationException ble) {
            latestCommittedTextStart = latestCommittedTextEnd = null;
        }
    }

    /** DefaultEditorKit.DefaultKeyTypedAction: printable text replaces the selection. */
    private void insertTypedText(String content) {
        if (!isEditable() || !isEnabled() || content.isEmpty()) return;
        char c = content.charAt(0);
        if (c >= 0x20 && c != 0x7F) replaceSelection(content);
    }

    /** The caret follows the input method's caret inside the composition. The JDK swaps in a
     *  ComposedTextCaret for the duration; the dot it places is the same. */
    private void setInputMethodCaretPosition(java.awt.event.InputMethodEvent e) {
        if (!composedTextExists()) return;
        int dot = composedTextStart.getOffset();
        java.awt.font.TextHitInfo hit = e.getCaret();
        if (hit != null) dot += hit.getInsertionIndex();
        caret.setDot(Math.max(0, Math.min(dot, doc.getLength())));
    }

    private boolean shouldSynthesizeKeyEvents() {
        if (!checkedInputOverride) {
            needToSendKeyTypedEvent = !isProcessInputMethodEventOverridden();
            checkedInputOverride = true;
        }
        return needToSendKeyTypedEvent;
    }

    private boolean isProcessInputMethodEventOverridden() {
        for (Class<?> type = getClass(); type != null && type != JTextComponent.class; type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod("processInputMethodEvent", java.awt.event.InputMethodEvent.class);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return false;
    }

    /**
     * Not in the JDK: underlines the composed text where it falls inside one painted line, as
     * every desktop input method highlight mapping does. {@code line} is painted from
     * {@code x}; {@code offsets[k]} is the document offset of its k-th character plus one entry
     * for the line end, or null when the line is the document text from {@code start} as is.
     */
    protected void paintComposedUnderline(java.awt.Graphics g, String line, int start, int[] offsets, int x, int baseline) {
        if (!composedTextExists() || line == null) return;
        int composedStart = composedTextStart.getOffset(), composedEnd = composedTextEnd.getOffset();
        int from = column(line, start, offsets, composedStart), to = column(line, start, offsets, composedEnd);
        if (from >= to) return;
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        int x0 = x + fm.stringWidth(line.substring(0, from)), x1 = x + fm.stringWidth(line.substring(0, to));
        int y = baseline + Math.max(1, fm.getDescent() / 2);
        g.drawLine(x0, y, Math.max(x0, x1 - 1), y);
    }
    private static int column(String line, int start, int[] offsets, int offset) {
        if (offsets == null) return Math.max(0, Math.min(line.length(), offset - start));
        for (int k = 0; k < line.length(); k++) if (offsets[k] >= offset) return k;
        return line.length();
    }

    private final class InputMethodRequestsHandler implements java.awt.im.InputMethodRequests,
            javax.swing.event.DocumentListener, java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public java.text.AttributedCharacterIterator cancelLatestCommittedText(
                java.text.AttributedCharacterIterator.Attribute[] attributes) {
            if (doc != null && latestCommittedTextStart != null
                    && latestCommittedTextStart.getOffset() != latestCommittedTextEnd.getOffset()) {
                try {
                    int start = latestCommittedTextStart.getOffset(), end = latestCommittedTextEnd.getOffset();
                    String latest = doc.getText(start, end - start);
                    doc.remove(start, end - start);
                    return new java.text.AttributedString(latest).getIterator();
                } catch (BadLocationException ignored) {
                }
            }
            return null;
        }

        public java.text.AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                java.text.AttributedCharacterIterator.Attribute[] attributes) {
            int composedStart = 0, composedEnd = 0;
            if (composedTextExists()) {
                composedStart = composedTextStart.getOffset();
                composedEnd = composedTextEnd.getOffset();
            }
            String committed;
            try {
                if (beginIndex < composedStart) {
                    if (endIndex <= composedStart) {
                        committed = getText(beginIndex, endIndex - beginIndex);
                    } else {
                        int firstPart = composedStart - beginIndex;
                        committed = getText(beginIndex, firstPart)
                            + getText(composedEnd, endIndex - beginIndex - firstPart);
                    }
                } else {
                    committed = getText(beginIndex + (composedEnd - composedStart), endIndex - beginIndex);
                }
            } catch (BadLocationException ble) {
                throw new IllegalArgumentException("Invalid range");
            }
            return new java.text.AttributedString(committed).getIterator();
        }

        public int getCommittedTextLength() {
            int length = doc.getLength();
            if (composedTextExists()) length -= composedTextEnd.getOffset() - composedTextStart.getOffset();
            return length;
        }

        public int getInsertPositionOffset() {
            int composedStart = 0, composedEnd = 0;
            if (composedTextExists()) {
                composedStart = composedTextStart.getOffset();
                composedEnd = composedTextEnd.getOffset();
            }
            int caretIndex = getCaretPosition();
            if (caretIndex < composedStart) return caretIndex;
            if (caretIndex < composedEnd) return composedStart;
            return caretIndex - (composedEnd - composedStart);
        }

        public java.awt.font.TextHitInfo getLocationOffset(int x, int y) {
            if (!composedTextExists()) return null;
            java.awt.Point origin = getLocationOnScreen();
            int offset = viewToModel(new java.awt.Point(x - origin.x, y - origin.y));
            int start = composedTextStart.getOffset(), end = composedTextEnd.getOffset();
            return offset >= start && offset <= end ? java.awt.font.TextHitInfo.leading(offset - start) : null;
        }

        public java.text.AttributedCharacterIterator getSelectedText(
                java.text.AttributedCharacterIterator.Attribute[] attributes) {
            String selection = JTextComponent.this.getSelectedText();
            return selection == null ? null : new java.text.AttributedString(selection).getIterator();
        }

        public java.awt.Rectangle getTextLocation(java.awt.font.TextHitInfo offset) {
            java.awt.Rectangle r;
            try {
                r = modelToView(getCaretPosition());
                java.awt.Point origin = getLocationOnScreen();
                r.translate(origin.x, origin.y);
            } catch (BadLocationException ble) {
                r = new java.awt.Rectangle();
            }
            return r;
        }

        // Text changed by anything but the input method can no longer be cancelled by it.
        public void insertUpdate(javax.swing.event.DocumentEvent e) { latestCommittedTextStart = latestCommittedTextEnd = null; }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { latestCommittedTextStart = latestCommittedTextEnd = null; }
        public void changedUpdate(javax.swing.event.DocumentEvent e) {}
    }
}
