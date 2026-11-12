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
package javax.swing.plaf.basic;

import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.*;
import javax.swing.plaf.*;

/** Delegate hooks for plugin styling; Android's panel host owns the interactive picker. */
public class BasicComboBoxUI extends ComboBoxUI {
    protected JComboBox<Object> comboBox;
    protected JButton arrowButton;
    protected ComboPopup popup;
    protected JList<Object> listBox;
    public static ComponentUI createUI(JComponent c) { return new BasicComboBoxUI(); }
    @SuppressWarnings("unchecked")
    @Override public void installUI(JComponent c) {
        comboBox = (JComboBox<Object>) c;
        // As the JDK: only a renderer or editor the look and feel put there is replaced, so a
        // subclass's createEditor (FlatLaf's) wins over the plain one.
        ListCellRenderer<?> renderer = comboBox.getRenderer();
        if (renderer == null || renderer instanceof UIResource) {
            comboBox.setRenderer(createRenderer());
        }
        ComboBoxEditor editor = comboBox.getEditor();
        if (editor == null || editor instanceof UIResource) {
            comboBox.setEditor(createEditor());
        }
        popup = createPopup();
        listBox = popup.getList();
        arrowButton = createArrowButton();
        installListeners();
    }
    @Override public void uninstallUI(JComponent c) {
        uninstallListeners();
        popup.uninstallingUI();
        comboBox = null; arrowButton = null; popup = null; listBox = null;
    }
    protected ComboPopup createPopup() { return new BasicComboPopup(comboBox); }

    /**
     * Creates the default renderer that will be used in a non-editiable combo
     * box. A default renderer will used only if a renderer has not been
     * explicitly set with <code>setRenderer</code>.
     *
     * @return a <code>ListCellRender</code> used for the combo box
     * @see javax.swing.JComboBox#setRenderer
     */
    @SuppressWarnings("rawtypes")
    protected ListCellRenderer createRenderer() {
        return new BasicComboBoxRenderer.UIResource();
    }

    /**
     * Creates the default editor that will be used in editable combo boxes.
     * A default editor will be used only if an editor has not been
     * explicitly set with <code>setEditor</code>.
     *
     * @return a <code>ComboBoxEditor</code> used for the combo box
     * @see javax.swing.JComboBox#setEditor
     */
    protected ComboBoxEditor createEditor() {
        return new BasicComboBoxEditor.UIResource();
    }
    protected void installListeners() {
        if (popup.getMouseListener() != null) comboBox.addMouseListener(popup.getMouseListener());
        if (popup.getMouseMotionListener() != null) comboBox.addMouseMotionListener(popup.getMouseMotionListener());
        if (popup.getKeyListener() != null) comboBox.addKeyListener(popup.getKeyListener());
    }
    protected void uninstallListeners() {
        if (popup.getMouseListener() != null) comboBox.removeMouseListener(popup.getMouseListener());
        if (popup.getMouseMotionListener() != null) comboBox.removeMouseMotionListener(popup.getMouseMotionListener());
        if (popup.getKeyListener() != null) comboBox.removeKeyListener(popup.getKeyListener());
    }
    public JList<Object> hostPopupList() { return listBox; }
    protected JButton createArrowButton() { return new BasicArrowButton(SwingConstants.SOUTH); }
    @Override public void setPopupVisible(JComboBox<?> c, boolean visible) { if (visible) popup.show(); else popup.hide(); }
    @Override public boolean isPopupVisible(JComboBox<?> c) { return popup != null && popup.isVisible(); }
    @Override public boolean isFocusTraversable(JComboBox<?> c) { return c.isEnabled(); }
    @Override public void paint(Graphics g, JComponent c) {
        Rectangle bounds = new Rectangle(4, 1, Math.max(0, c.getWidth() - 20), Math.max(0, c.getHeight() - 2));
        paintCurrentValueBackground(g, bounds, c.hasFocus());
        paintCurrentValue(g, bounds, c.hasFocus());
        paintArrow(g, Math.max(0, c.getWidth() - 16), 1, 15, bounds.height);
    }
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focused) {
        g.setColor(comboBox.getBackground());
        g.fillRect(0, 0, comboBox.getWidth(), comboBox.getHeight());
        g.setColor(comboBox.isEnabled() ? comboBox.getForeground() : java.awt.Color.GRAY);
        g.drawRect(0, 0, comboBox.getWidth() - 1, comboBox.getHeight() - 1);
    }
    public void paintCurrentValue(Graphics g, Rectangle bounds, boolean focused) { comboBox.hostPaintValue(g, bounds); }
    public void paintArrow(Graphics g, int x, int y, int width, int height) {
        if (arrowButton == null) return;
        arrowButton.setEnabled(comboBox.isEnabled());
        arrowButton.setSize(width, height);
        Graphics child = g.create(x, y, width, height);
        try { arrowButton.paint(child); } finally { child.dispose(); }
    }
}
