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
package net.runelite.awt.impl;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.TextUI;
import javax.swing.text.*;

/** Exposes the existing component renderer through Swing's text UI lookup API. */
public final class TextComponentUI extends TextUI {
    public static final TextComponentUI INSTANCE = new TextComponentUI();
    private static final Object ROOT = new Object();
    private static final EditorKit PLAIN = new DefaultEditorKit();
    private TextComponentUI() {}
    public Rectangle modelToView(JTextComponent t, int pos) throws BadLocationException { return t.modelToView(pos); }
    public Rectangle modelToView(JTextComponent t, int pos, Position.Bias bias) throws BadLocationException { return t.modelToView(pos); }
    public int viewToModel(JTextComponent t, Point point) { return t.viewToModel(point); }
    public int viewToModel(JTextComponent t, Point point, Position.Bias[] bias) {
        if (bias != null && bias.length > 0) bias[0] = Position.Bias.Forward;
        return t.viewToModel(point);
    }
    public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias bias, int direction, Position.Bias[] result) throws BadLocationException {
        return getRootView(t).getView(0).getNextVisualPositionFrom(pos, bias, t.getVisibleRect(), direction, result);
    }
    public void damageRange(JTextComponent t, int p0, int p1) { t.repaint(); }
    public void damageRange(JTextComponent t, int p0, int p1, Position.Bias b0, Position.Bias b1) { t.repaint(); }
    public EditorKit getEditorKit(JTextComponent t) { return t instanceof JEditorPane ? ((JEditorPane) t).getEditorKit() : PLAIN; }
    public View getRootView(JTextComponent t) {
        RootView root = (RootView) t.getClientProperty(ROOT);
        if (root == null || root.getDocument() != t.getDocument()) {
            root = new RootView(t, getEditorKit(t)); t.putClientProperty(ROOT, root);
        }
        return root;
    }
    private static final class RootView extends View {
        private final JTextComponent owner;
        private final View child;
        private final ViewFactory factory;
        RootView(JTextComponent owner, EditorKit kit) {
            super(owner.getDocument().getDefaultRootElement()); this.owner = owner;
            ViewFactory configured = kit == null ? null : kit.getViewFactory();
            factory = configured == null ? PlainView::new : configured;
            child = factory.create(getElement()); child.setParent(this);
        }
        public Container getContainer() { return owner; }
        public ViewFactory getViewFactory() { return factory; }
        public int getViewCount() { return 1; }
        public View getView(int index) { if (index != 0) throw new ArrayIndexOutOfBoundsException(index); return child; }
        public float getPreferredSpan(int axis) { return child.getPreferredSpan(axis); }
        public void setSize(float width, float height) { child.setSize(width, height); }
        public void paint(Graphics graphics, Shape allocation) { child.paint(graphics, allocation); }
        public Shape modelToView(int pos, Shape allocation, Position.Bias bias) throws BadLocationException { return child.modelToView(pos, allocation, bias); }
        public int viewToModel(float x, float y, Shape allocation, Position.Bias[] bias) { return child.viewToModel(x, y, allocation, bias); }
    }
}
