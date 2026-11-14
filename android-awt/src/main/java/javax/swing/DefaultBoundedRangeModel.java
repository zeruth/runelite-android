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

import java.io.Serializable;
import java.util.EventListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

/**
 * Swing's model: every setter funnels through setRangeProperties, which keeps
 * min <= value <= value+extent <= max and fires a ChangeEvent only on a real change.
 * JScrollPane drives its viewport from these events, so the clamping and firing have to
 * match the JDK's (a scrollbar setValue(0) is how plugins scroll to the top).
 */
public class DefaultBoundedRangeModel implements BoundedRangeModel, Serializable {
    private static final long serialVersionUID = 5034068491295259790L;

    protected transient ChangeEvent changeEvent = null;
    protected EventListenerList listenerList = new EventListenerList();

    private int value = 0;
    private int extent = 0;
    private int min = 0;
    private int max = 100;
    private boolean isAdjusting = false;

    public DefaultBoundedRangeModel() {}

    public DefaultBoundedRangeModel(int value, int extent, int min, int max) {
        if ((max >= min) && (value >= min) && ((value + extent) >= value) && ((value + extent) <= max)) {
            this.value = value;
            this.extent = extent;
            this.min = min;
            this.max = max;
        } else {
            throw new IllegalArgumentException("invalid range properties");
        }
    }

    @Override public int getValue() { return value; }
    @Override public int getExtent() { return extent; }
    @Override public int getMinimum() { return min; }
    @Override public int getMaximum() { return max; }
    @Override public boolean getValueIsAdjusting() { return isAdjusting; }

    @Override public void setValue(int n) {
        n = Math.min(n, Integer.MAX_VALUE - extent);
        int newValue = Math.max(n, min);
        if (newValue + extent > max) newValue = max - extent;
        setRangeProperties(newValue, extent, min, max, isAdjusting);
    }

    @Override public void setExtent(int n) {
        int newExtent = Math.max(0, n);
        if (value + newExtent > max) newExtent = max - value;
        setRangeProperties(value, newExtent, min, max, isAdjusting);
    }

    @Override public void setMinimum(int n) {
        int newMax = Math.max(n, max);
        int newValue = Math.max(n, value);
        int newExtent = Math.min(newMax - newValue, extent);
        setRangeProperties(newValue, newExtent, n, newMax, isAdjusting);
    }

    @Override public void setMaximum(int n) {
        int newMin = Math.min(n, min);
        int newExtent = Math.min(n - newMin, extent);
        int newValue = Math.min(n - newExtent, value);
        setRangeProperties(newValue, newExtent, newMin, n, isAdjusting);
    }

    @Override public void setValueIsAdjusting(boolean b) {
        setRangeProperties(value, extent, min, max, b);
    }

    @Override public void setRangeProperties(int newValue, int newExtent, int newMin, int newMax, boolean adjusting) {
        if (newMin > newMax) newMin = newMax;
        if (newValue > newMax) newMax = newValue;
        if (newValue < newMin) newMin = newValue;
        // Guard against overflow, as the JDK does.
        if (((long) newExtent + (long) newValue) > newMax) newExtent = newMax - newValue;
        if (newExtent < 0) newExtent = 0;

        boolean isChange = (newValue != value) || (newExtent != extent) || (newMin != min)
            || (newMax != max) || (adjusting != isAdjusting);
        if (isChange) {
            value = newValue;
            extent = newExtent;
            min = newMin;
            max = newMax;
            isAdjusting = adjusting;
            fireStateChanged();
        }
    }

    @Override public void addChangeListener(ChangeListener l) { listenerList.add(ChangeListener.class, l); }
    @Override public void removeChangeListener(ChangeListener l) { listenerList.remove(ChangeListener.class, l); }
    public ChangeListener[] getChangeListeners() { return listenerList.getListeners(ChangeListener.class); }

    protected void fireStateChanged() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ChangeListener.class) {
                if (changeEvent == null) changeEvent = new ChangeEvent(this);
                ((ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }

    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        return listenerList.getListeners(listenerType);
    }

    @Override public String toString() {
        String modelString = "value=" + getValue() + ", " + "extent=" + getExtent() + ", "
            + "min=" + getMinimum() + ", " + "max=" + getMaximum() + ", " + "adj=" + getValueIsAdjusting();
        return getClass().getName() + "[" + modelString + "]";
    }
}
