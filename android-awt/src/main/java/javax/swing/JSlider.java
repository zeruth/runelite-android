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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.SliderUI;

/**
 * The JDK slider over a {@link BoundedRangeModel}. Painting and input are what BasicSliderUI
 * does with the thumb enlarged for touch: the track runs between half-thumb insets, a press or
 * drag moves the value to the touched point, and snapToTicks rounds it on release.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class JSlider extends JComponent implements SwingConstants, javax.accessibility.Accessible {
    private static final long serialVersionUID = 1L;
    private static final String uiClassID = "SliderUI";

    protected BoundedRangeModel sliderModel;
    protected int majorTickSpacing;
    protected int minorTickSpacing;
    protected boolean snapToTicks = false;
    protected int orientation;
    protected ChangeListener changeListener = createChangeListener();
    protected transient ChangeEvent changeEvent = null;

    private boolean paintTicks = false;
    private boolean paintTrack = true;
    private boolean paintLabels = false;
    private boolean isInverted = false;
    private Dictionary labelTable;

    public JSlider() { this(HORIZONTAL, 0, 100, 50); }
    public JSlider(int orientation) { this(orientation, 0, 100, 50); }
    public JSlider(int min, int max) { this(HORIZONTAL, min, max, (min + max) / 2); }
    public JSlider(int min, int max, int value) { this(HORIZONTAL, min, max, value); }
    public JSlider(int orientation, int min, int max, int value) {
        checkOrientation(orientation);
        this.orientation = orientation;
        setModel(new DefaultBoundedRangeModel(value, 0, min, max));
        updateUI();
    }
    public JSlider(BoundedRangeModel brm) {
        this.orientation = HORIZONTAL;
        setModel(brm);
        updateUI();
    }

    private void checkOrientation(int orientation) {
        if (orientation != VERTICAL && orientation != HORIZONTAL) {
            throw new IllegalArgumentException("orientation must be one of: VERTICAL, HORIZONTAL");
        }
    }

    private class ModelListener implements ChangeListener, java.io.Serializable {
        public void stateChanged(ChangeEvent e) { fireStateChanged(); }
    }

    protected ChangeListener createChangeListener() { return new ModelListener(); }

    public SliderUI getUI() { return (SliderUI) ui; }
    public void setUI(SliderUI ui) { super.setUI(ui); }
    /**
     * The JDK installs the look and feel's delegate, which owns the track geometry, sizing, input
     * and painting, as in the JDK; one compiled against
     * BasicSliderUI members this shadow lacks fails to link and the Basic delegate stands in.
     */
    public void updateUI() {
        try {
            javax.swing.plaf.ComponentUI delegate = UIManager.getUI(this);
            setUI(delegate instanceof SliderUI ? (SliderUI) delegate : new javax.swing.plaf.basic.BasicSliderUI(this));
        } catch (LinkageError incompatible) {
            ui = null;
            setUI(new javax.swing.plaf.basic.BasicSliderUI(this));
        }
    }
    public String getUIClassID() { return uiClassID; }

    // JComponent's sizing, as in the JDK: the delegate answers unless a size was set.
    @Override public Dimension getPreferredSize() {
        Dimension size = isPreferredSizeSet() || ui == null ? null : ui.getPreferredSize(this);
        return size != null ? size : super.getPreferredSize();
    }

    @Override public Dimension getMinimumSize() {
        Dimension size = isMinimumSizeSet() || ui == null ? null : ui.getMinimumSize(this);
        return size != null ? size : super.getMinimumSize();
    }

    @Override public Dimension getMaximumSize() {
        Dimension size = isMaximumSizeSet() || ui == null ? null : ui.getMaximumSize(this);
        return size != null ? size : super.getMaximumSize();
    }

    public void addChangeListener(ChangeListener l) { listenerList.add(ChangeListener.class, l); }
    public void removeChangeListener(ChangeListener l) { listenerList.remove(ChangeListener.class, l); }
    public ChangeListener[] getChangeListeners() { return listenerList.getListeners(ChangeListener.class); }

    protected void fireStateChanged() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ChangeListener.class) {
                if (changeEvent == null) changeEvent = new ChangeEvent(this);
                ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
        repaint();
    }

    public BoundedRangeModel getModel() { return sliderModel; }
    public void setModel(BoundedRangeModel newModel) {
        BoundedRangeModel oldModel = getModel();
        if (oldModel != null) oldModel.removeChangeListener(changeListener);
        sliderModel = newModel;
        if (newModel != null) newModel.addChangeListener(changeListener);
        firePropertyChange("model", oldModel, sliderModel);
        repaint();
    }

    public int getValue() { return getModel().getValue(); }
    public void setValue(int n) {
        BoundedRangeModel m = getModel();
        if (m.getValue() == n) return;
        m.setValue(n);
    }

    public int getMinimum() { return getModel().getMinimum(); }
    public void setMinimum(int minimum) {
        int oldMin = getModel().getMinimum();
        getModel().setMinimum(minimum);
        firePropertyChange("minimum", Integer.valueOf(oldMin), Integer.valueOf(minimum));
    }

    public int getMaximum() { return getModel().getMaximum(); }
    public void setMaximum(int maximum) {
        int oldMax = getModel().getMaximum();
        getModel().setMaximum(maximum);
        firePropertyChange("maximum", Integer.valueOf(oldMax), Integer.valueOf(maximum));
    }

    public boolean getValueIsAdjusting() { return getModel().getValueIsAdjusting(); }
    public void setValueIsAdjusting(boolean b) {
        BoundedRangeModel m = getModel();
        boolean oldValue = m.getValueIsAdjusting();
        m.setValueIsAdjusting(b);
        firePropertyChange("valueIsAdjusting", oldValue, b);
    }

    public int getExtent() { return getModel().getExtent(); }
    public void setExtent(int extent) { getModel().setExtent(extent); }

    public int getOrientation() { return orientation; }
    public void setOrientation(int orientation) {
        checkOrientation(orientation);
        int oldValue = this.orientation;
        this.orientation = orientation;
        firePropertyChange("orientation", oldValue, orientation);
        if (orientation != oldValue) revalidate();
    }

    /** No lazy table here: the JDK creates standard labels only from setPaintLabels/setMajorTickSpacing. */
    public Dictionary getLabelTable() { return labelTable; }

    public void setLabelTable(Dictionary labels) {
        Dictionary oldTable = labelTable;
        labelTable = labels;
        updateLabelUIs();
        firePropertyChange("labelTable", oldTable, labelTable);
        if (labels != oldTable) {
            revalidate();
            repaint();
        }
    }

    /** Sizes every label to its preferred size, the JDK's contract for labels in the table. */
    protected void updateLabelUIs() {
        Dictionary labels = getLabelTable();
        if (labels == null) return;
        Enumeration elements = labels.elements();
        while (elements.hasMoreElements()) {
            Object value = elements.nextElement();
            if (value instanceof JComponent) {
                JComponent component = (JComponent) value;
                component.updateUI();
                component.setSize(component.getPreferredSize());
            }
        }
    }

    public Hashtable createStandardLabels(int increment) { return createStandardLabels(increment, getMinimum()); }

    public Hashtable createStandardLabels(int increment, int start) {
        if (start > getMaximum() || start < getMinimum()) {
            throw new IllegalArgumentException("Slider label start point out of range.");
        }
        if (increment <= 0) {
            throw new IllegalArgumentException("Label incremement must be > 0");
        }
        Hashtable table = new Hashtable();
        for (int labelIndex = start; labelIndex <= getMaximum(); labelIndex += increment) {
            JLabel label = new JLabel(String.valueOf(labelIndex), JLabel.CENTER);
            label.setName("Slider.label");
            label.setSize(label.getPreferredSize());
            table.put(Integer.valueOf(labelIndex), label);
            if (labelIndex > Integer.MAX_VALUE - increment) break;
        }
        return table;
    }

    public boolean getInverted() { return isInverted; }
    public void setInverted(boolean b) {
        boolean oldValue = isInverted;
        isInverted = b;
        firePropertyChange("inverted", oldValue, isInverted);
        if (b != oldValue) repaint();
    }

    public int getMajorTickSpacing() { return majorTickSpacing; }
    public void setMajorTickSpacing(int n) {
        int oldValue = majorTickSpacing;
        majorTickSpacing = n;
        if (labelTable == null && getMajorTickSpacing() > 0 && getPaintLabels()) {
            setLabelTable(createStandardLabels(getMajorTickSpacing()));
        }
        firePropertyChange("majorTickSpacing", oldValue, majorTickSpacing);
        if (majorTickSpacing != oldValue && getPaintTicks()) repaint();
    }

    public int getMinorTickSpacing() { return minorTickSpacing; }
    public void setMinorTickSpacing(int n) {
        int oldValue = minorTickSpacing;
        minorTickSpacing = n;
        firePropertyChange("minorTickSpacing", oldValue, minorTickSpacing);
        if (minorTickSpacing != oldValue && getPaintTicks()) repaint();
    }

    public boolean getSnapToTicks() { return snapToTicks; }
    public void setSnapToTicks(boolean b) {
        boolean oldValue = snapToTicks;
        snapToTicks = b;
        firePropertyChange("snapToTicks", oldValue, snapToTicks);
    }

    public boolean getPaintTicks() { return paintTicks; }
    public void setPaintTicks(boolean b) {
        boolean oldValue = paintTicks;
        paintTicks = b;
        firePropertyChange("paintTicks", oldValue, paintTicks);
        if (paintTicks != oldValue) { revalidate(); repaint(); }
    }

    public boolean getPaintTrack() { return paintTrack; }
    public void setPaintTrack(boolean b) {
        boolean oldValue = paintTrack;
        paintTrack = b;
        firePropertyChange("paintTrack", oldValue, paintTrack);
        if (paintTrack != oldValue) repaint();
    }

    public boolean getPaintLabels() { return paintLabels; }
    public void setPaintLabels(boolean b) {
        boolean oldValue = paintLabels;
        paintLabels = b;
        if (labelTable == null && getMajorTickSpacing() > 0) {
            setLabelTable(createStandardLabels(getMajorTickSpacing()));
        }
        firePropertyChange("paintLabels", oldValue, paintLabels);
        if (paintLabels != oldValue) { revalidate(); repaint(); }
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJSlider();
        }
        return accessibleContext;
    }

    /** Accessibility support for sliders: role SLIDER, valued by the slider's model. */
    protected class AccessibleJSlider extends AccessibleJComponent implements javax.accessibility.AccessibleValue {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.SLIDER;
        }

        public javax.accessibility.AccessibleValue getAccessibleValue() {
            return this;
        }

        public Number getCurrentAccessibleValue() {
            return Integer.valueOf(getValue());
        }

        public boolean setCurrentAccessibleValue(Number n) {
            if (n == null) {
                return false;
            }
            setValue(n.intValue());
            return true;
        }

        public Number getMinimumAccessibleValue() {
            return Integer.valueOf(getMinimum());
        }

        public Number getMaximumAccessibleValue() {
            BoundedRangeModel model = getModel();
            return Integer.valueOf(model.getMaximum() - model.getExtent());
        }
    }
}
