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
package javax.swing.colorchooser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * One tab of the default chooser per color space (the JDK's package-private
 * ColorChooserPanel): a slider and a spinner per component, and for RGB the
 * "Color Code" hex field. Edits write the model through
 * {@link AbstractColorChooserPanel#setSelectedColor}; model changes come back
 * through {@link #updateChooser} without re-entering the model.
 */
@SuppressWarnings("serial")
final class ColorChooserPanel extends AbstractColorChooserPanel
        implements ChangeListener, ActionListener, PropertyChangeListener {

    private final ColorModel model;
    private final int[] values;
    private JSlider[] sliders;
    private JSpinner[] spinners;
    private JPanel alphaRow;
    private JTextField text;
    private boolean adjusting;

    ColorChooserPanel(ColorModel model) {
        this.model = model;
        this.values = new int[model.getCount()];
        addPropertyChangeListener("colorTransparencySelectionEnabled", this);
    }

    @Override
    public String getDisplayName() {
        return model.getName();
    }

    @Override
    public Icon getSmallDisplayIcon() {
        return null;
    }

    @Override
    public Icon getLargeDisplayIcon() {
        return null;
    }

    @Override
    protected void buildChooser() {
        if (sliders != null) {
            return;
        }
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        int count = model.getCount();
        sliders = new JSlider[count];
        spinners = new JSpinner[count];
        for (int i = 0; i < count; i++) {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            JLabel label = new JLabel(model.getLabel(i));
            label.setPreferredSize(new Dimension(80, label.getPreferredSize().height));
            sliders[i] = new JSlider(model.getMinimum(i), model.getMaximum(i), model.getMinimum(i));
            sliders[i].addChangeListener(this);
            spinners[i] = new JSpinner(new SpinnerNumberModel(model.getMinimum(i), model.getMinimum(i),
                                                              model.getMaximum(i), 1));
            spinners[i].addChangeListener(this);
            row.add(label, BorderLayout.WEST);
            row.add(sliders[i], BorderLayout.CENTER);
            row.add(spinners[i], BorderLayout.EAST);
            add(row);
            if (i == count - 1) {
                alphaRow = row;
                row.setVisible(isColorTransparencySelectionEnabled());
            }
        }
        if ("RGB".equals(model.getName())) {
            JPanel row = new JPanel(new BorderLayout(4, 0));
            row.add(new JLabel("Color Code"), BorderLayout.WEST);
            text = new JTextField(8);
            text.addActionListener(this);
            row.add(text, BorderLayout.CENTER);
            add(row);
        }
    }

    @Override
    public void updateChooser() {
        Color color = getColorFromModel();
        if (color == null || sliders == null || adjusting) {
            return;
        }
        adjusting = true;
        try {
            model.setColor(color.getRGB(), values);
            for (int i = 0; i < values.length; i++) {
                sliders[i].setValue(values[i]);
                spinners[i].setValue(Integer.valueOf(values[i]));
            }
            if (text != null) {
                text.setText(String.format("%06X", color.getRGB() & 0xFFFFFF));
            }
        } finally {
            adjusting = false;
        }
    }

    @Override
    public void stateChanged(ChangeEvent event) {
        if (adjusting) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            if (event.getSource() == sliders[i]) {
                values[i] = sliders[i].getValue();
            } else if (event.getSource() == spinners[i]) {
                values[i] = ((Number) spinners[i].getValue()).intValue();
            }
        }
        commit(model.getColor(values));
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        String code = text.getText().trim();
        if (code.startsWith("#")) {
            code = code.substring(1);
        }
        try {
            int rgb = Integer.parseInt(code, 16);
            commit((values[values.length - 1] << 24) | (rgb & 0xFFFFFF));
        } catch (NumberFormatException e) {
            updateChooser();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (alphaRow != null) {
            alphaRow.setVisible(isColorTransparencySelectionEnabled());
        }
        if (!isColorTransparencySelectionEnabled()) {
            Color color = getColorFromModel();
            if (color != null && color.getAlpha() != 255) {
                commit(color.getRGB() | 0xFF000000);
            }
        }
    }

    private void commit(int argb) {
        if (!isColorTransparencySelectionEnabled()) {
            argb |= 0xFF000000;
        }
        // The JDK posts the new color and lets the model's change event
        // refresh every tab; this tab's own controls are refreshed too.
        setSelectedColor(new Color(argb, true));
        updateChooser();
    }
}
