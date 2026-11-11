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

import java.awt.event.*;
import javax.swing.*;

/** Swing list and styling hooks for the Android-hosted combo picker. */
public class BasicComboPopup extends JPopupMenu implements ComboPopup {
    protected JComboBox<Object> comboBox;
    protected JList<Object> list;
    protected JScrollPane scroller;
    private final java.beans.PropertyChangeListener comboChanges = event -> {
        if ("model".equals(event.getPropertyName())) list.setModel(comboBox.getModel());
        else if ("renderer".equals(event.getPropertyName())) list.setCellRenderer(comboBox.getRenderer());
        else if ("lightWeightPopupEnabled".equals(event.getPropertyName())) setLightWeightPopupEnabled(comboBox.isLightWeightPopupEnabled());
    };
    private final MouseListener mouse = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent event) {
            if (comboBox.isEnabled() && event.getButton() == MouseEvent.BUTTON1)
                comboBox.setPopupVisible(!comboBox.isPopupVisible());
        }
    };
    private final MouseMotionListener motion = new MouseMotionAdapter() {};

    public BasicComboPopup(JComboBox<Object> combo) {
        comboBox = combo;
        list = createList();
        configureList();
        scroller = createScroller();
        configureScroller();
        configurePopup();
        comboBox.addPropertyChangeListener(comboChanges);
    }
    protected JList<Object> createList() { return new JList<>(comboBox.getModel()); }
    protected void configureList() {
        list.setFont(comboBox.getFont());
        list.setForeground(comboBox.getForeground());
        list.setBackground(comboBox.getBackground());
        list.setCellRenderer(comboBox.getRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }
    protected JScrollPane createScroller() { return new JScrollPane(list); }
    protected void configureScroller() { scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); }
    protected void configurePopup() {
        setInvoker(comboBox);
        setLightWeightPopupEnabled(comboBox.isLightWeightPopupEnabled());
        add(scroller);
    }
    @Override public JList<Object> getList() { return list; }
    @Override public MouseListener getMouseListener() { return mouse; }
    @Override public MouseMotionListener getMouseMotionListener() { return motion; }
    @Override public KeyListener getKeyListener() { return null; }
    @Override public void show() {
        list.setSelectedIndex(comboBox.getSelectedIndex());
        setVisible(true);
        comboBox.hostSetPopupVisible(true);
    }
    @Override public void hide() {
        setVisible(false);
        comboBox.hostSetPopupVisible(false);
    }
    @Override public void uninstallingUI() {
        if (isVisible()) hide();
        comboBox.removePropertyChangeListener(comboChanges);
        list.setModel(new DefaultListModel<>());
    }
}
