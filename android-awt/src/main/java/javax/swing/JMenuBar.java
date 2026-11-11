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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

public class JMenuBar extends JComponent {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_HEIGHT = 28;
    private SingleSelectionModel selectionModel = new DefaultSingleSelectionModel();
    private boolean borderPainted = true;
    private java.awt.Insets margin = new java.awt.Insets(0, 0, 0, 0);

    public JMenuBar() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(true);
        setBackground(new Color(0x3A3D43));
        setPreferredSize(new Dimension(0, DEFAULT_HEIGHT));
        setMinimumSize(new Dimension(0, DEFAULT_HEIGHT));
    }

    public JMenu add(JMenu c) {
        if (c != null) super.add(c);
        return c;
    }

    public JMenu getMenu(int index) {
        Component c = index >= 0 && getComponentCount() > index ? getComponent(index) : null;
        return c instanceof JMenu ? (JMenu) c : null;
    }

    public int getMenuCount() { return getComponentCount(); }
    public SingleSelectionModel getSelectionModel() { return selectionModel; }
    public void setSelectionModel(SingleSelectionModel model) {
        if (model == null) throw new IllegalArgumentException("selection model");
        SingleSelectionModel old = selectionModel; selectionModel = model;
        firePropertyChange("selectionModel", old, model); repaint();
    }
    public void setSelected(Component sel) {
        int index = -1;
        for (int i = 0; i < getComponentCount(); i++) if (getComponent(i) == sel) { index = i; break; }
        selectionModel.setSelectedIndex(index);
    }
    public boolean isSelected() { return selectionModel.isSelected(); }
    public void remove(int index) {
        int selected = selectionModel.getSelectedIndex(); super.remove(index);
        if (selected == index) selectionModel.clearSelection();
        else if (selected > index) selectionModel.setSelectedIndex(selected - 1);
    }
    public void remove(Component component) { for (int i = 0; i < getComponentCount(); i++) if (getComponent(i) == component) { remove(i); return; } }
    public void removeAll() { super.removeAll(); selectionModel.clearSelection(); }
    public void setBorderPainted(boolean b) { boolean old=borderPainted; borderPainted=b; firePropertyChange("borderPainted",old,b); revalidate(); repaint(); }
    public boolean isBorderPainted() { return borderPainted; }
    protected void paintBorder(java.awt.Graphics graphics) { if (borderPainted) super.paintBorder(graphics); }
    public boolean isManagingFocus() { return false; }
    public java.awt.Insets getMargin() { return (java.awt.Insets) margin.clone(); }
    public void setMargin(java.awt.Insets m) { java.awt.Insets old=margin; margin=m==null?new java.awt.Insets(0,0,0,0):(java.awt.Insets)m.clone(); firePropertyChange("margin",old,margin); revalidate(); repaint(); }
    public boolean isFloatable() { return false; }
}
