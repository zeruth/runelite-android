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

import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class JScrollBar extends JComponent implements SwingConstants {
    private static final long serialVersionUID = 1L;

    protected int orientation = VERTICAL;
    protected int unitIncrement = 1;
    protected int blockIncrement = 10;
    protected BoundedRangeModel model;

    // Swing forwards every model ChangeEvent as an AdjustmentEvent; JScrollPane listens here
    // to move its viewport, and it survives setModel because we re-register on the new model.
    private final ChangeListener fwdAdjustmentEvents = new ChangeListener() {
        @Override public void stateChanged(ChangeEvent e) {
            Object src = e.getSource();
            if (src instanceof BoundedRangeModel) {
                BoundedRangeModel m = (BoundedRangeModel) src;
                fireAdjustmentValueChanged(AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, AdjustmentEvent.TRACK,
                    m.getValue(), m.getValueIsAdjusting());
            }
        }
    };
    private final java.util.concurrent.CopyOnWriteArrayList<AdjustmentListener> adjustmentListeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    public JScrollBar() { this(VERTICAL); }
    public JScrollBar(int orientation) { this(orientation, 0, 10, 0, 100); }
    public JScrollBar(int orientation, int value, int extent, int min, int max) {
        this.orientation = orientation;
        this.blockIncrement = extent == 0 ? 1 : extent;
        this.model = new DefaultBoundedRangeModel(value, extent, min, max);
        this.model.addChangeListener(fwdAdjustmentEvents);
    }

    public int getOrientation() { return orientation; }
    @Override public javax.swing.plaf.ScrollBarUI getUI() { return (javax.swing.plaf.ScrollBarUI) ui; }
    public void setUI(javax.swing.plaf.ScrollBarUI delegate) { super.setUI(delegate); }
    @Override public java.awt.Dimension getPreferredSize() {
        if (!isPreferredSizeSet() && ui != null) {
            java.awt.Dimension size = ui.getPreferredSize(this);
            if (size != null) return size;
        }
        return super.getPreferredSize();
    }
    public void setOrientation(int orientation) { this.orientation = orientation; invalidate(); }
    public BoundedRangeModel getModel() { return model; }
    public void setModel(BoundedRangeModel newModel) {
        if (model != null) model.removeChangeListener(fwdAdjustmentEvents);
        model = newModel;
        if (model != null) model.addChangeListener(fwdAdjustmentEvents);
    }
    public int getUnitIncrement() { return unitIncrement; }
    public int getUnitIncrement(int direction) { return unitIncrement; }
    public void setUnitIncrement(int u) { this.unitIncrement = u; }
    public int getBlockIncrement() { return blockIncrement; }
    public int getBlockIncrement(int direction) { return blockIncrement; }
    public void setBlockIncrement(int b) { this.blockIncrement = b; }
    public int getValue() { return model.getValue(); }
    public void setValue(int value) { model.setValue(value); }
    public int getVisibleAmount() { return model.getExtent(); }
    public void setVisibleAmount(int extent) { model.setExtent(extent); }
    public int getMinimum() { return model.getMinimum(); }
    public void setMinimum(int min) { model.setMinimum(min); }
    public int getMaximum() { return model.getMaximum(); }
    public void setMaximum(int max) { model.setMaximum(max); }
    public boolean getValueIsAdjusting() { return model.getValueIsAdjusting(); }
    public void setValueIsAdjusting(boolean b) { model.setValueIsAdjusting(b); }
    public void setValues(int newValue, int newExtent, int newMin, int newMax) {
        model.setRangeProperties(newValue, newExtent, newMin, newMax, model.getValueIsAdjusting());
    }

    public void addAdjustmentListener(AdjustmentListener l) { if (l != null) adjustmentListeners.add(l); }
    public void removeAdjustmentListener(AdjustmentListener l) { adjustmentListeners.remove(l); }
    public AdjustmentListener[] getAdjustmentListeners() { return adjustmentListeners.toArray(new AdjustmentListener[0]); }

    protected void fireAdjustmentValueChanged(int id, int type, int value) {
        fireAdjustmentValueChanged(id, type, value, getValueIsAdjusting());
    }

    private void fireAdjustmentValueChanged(int id, int type, int value, boolean isAdjusting) {
        if (adjustmentListeners.isEmpty()) return;
        AdjustmentEvent e = new AdjustmentEvent(this, id, type, value, isAdjusting);
        // Last registered first, like EventListenerList-backed Swing listeners.
        Object[] ls = adjustmentListeners.toArray();
        for (int i = ls.length - 1; i >= 0; i--) ((AdjustmentListener) ls[i]).adjustmentValueChanged(e);
    }

    /** Swing stretches a scrollbar only along its own axis. */
    @Override
    public java.awt.Dimension getMaximumSize() {
        if (isMaximumSizeSet()) return super.getMaximumSize();
        java.awt.Dimension pref = getPreferredSize();
        return orientation == VERTICAL ? new java.awt.Dimension(pref.width, Short.MAX_VALUE)
            : new java.awt.Dimension(Short.MAX_VALUE, pref.height);
    }
}
