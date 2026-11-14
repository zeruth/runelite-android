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
package javax.swing.text.html;

import javax.swing.Action;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;

public class HTMLEditorKit extends EditorKit {
    private static final long serialVersionUID = -1097137732655729898L;

    public static final String DEFAULT_CSS = "default.css";
    public static final String BOLD_ACTION = "html-bold-action";

    public static class HTMLFactory implements javax.swing.text.ViewFactory {
        public javax.swing.text.View create(javax.swing.text.Element element) { return new javax.swing.HtmlView(element); }
    }
    public abstract static class HTMLTextAction {}

    private StyleSheet styles;

    public HTMLEditorKit() {}

    @Override public String getContentType() { return "text/html"; }
    @Override public Action[] getActions() { return new Action[0]; }
    @Override public Caret createCaret() { return new javax.swing.text.DefaultCaret(); }
    @Override public javax.swing.text.ViewFactory getViewFactory() { return new HTMLFactory(); }
    @Override public Document createDefaultDocument() { return new HTMLDocument(getStyleSheet()); }
    @Override public Object clone() { return new HTMLEditorKit(); }

    public StyleSheet getStyleSheet() {
        if (styles == null) styles = new StyleSheet();
        return styles;
    }
    public void setStyleSheet(StyleSheet s) { this.styles = s; }

    public void insertHTML(javax.swing.text.html.HTMLDocument doc, int offset, String html, int popDepth, int pushDepth, javax.swing.text.html.HTML.Tag insertTag) throws BadLocationException, java.io.IOException { doc.insertString(offset,html,null); }
}
