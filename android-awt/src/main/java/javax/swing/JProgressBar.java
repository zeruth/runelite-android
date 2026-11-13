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

import javax.swing.event.ChangeListener;

public class JProgressBar extends JComponent implements SwingConstants, javax.accessibility.Accessible {
    private static final String uiClassID = "ProgressBarUI";
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJProgressBar();
        }
        return accessibleContext;
    }

    /** Accessibility support for progress bars: role PROGRESS_BAR, valued by the model. */
    protected class AccessibleJProgressBar extends AccessibleJComponent implements javax.accessibility.AccessibleValue {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.PROGRESS_BAR;
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
    private static final long serialVersionUID = 1L;
    protected BoundedRangeModel model = new DefaultBoundedRangeModel();
    protected int orientation = HORIZONTAL;
    protected boolean stringPainted;
    protected boolean indeterminate;
    protected String progressString;
    private boolean borderPainted = true;

    /**
     * Listens for change events sent by the progress bar's model,
     * redispatching them to change-event listeners registered upon
     * this progress bar.
     */
    protected ChangeListener changeListener = null;

    /**
     * Only one <code>ChangeEvent</code> is needed per instance since the
     * event's only interesting property is the immutable source, which
     * is the progress bar.
     */
    protected transient javax.swing.event.ChangeEvent changeEvent = null;

    private final javax.swing.event.EventListenerList listeners=new javax.swing.event.EventListenerList();
    private final ChangeListener modelListener=e->{fireStateChanged();repaint();};
    public JProgressBar() { model.addChangeListener(modelListener); updateUI(); }

    /** Resets the UI property to a value from the current look and feel. */
    public void updateUI() { setUI((javax.swing.plaf.ProgressBarUI) UIManager.getUI(this)); }

    /**
     * Subclasses that want to handle change events from the model
     * differently can override this to return an instance of a custom
     * <code>ChangeListener</code> implementation.
     */
    protected ChangeListener createChangeListener() {
        return e -> fireStateChanged();
    }

    /** Send a ChangeEvent, whose source is this JProgressBar, to all registered ChangeListeners. */
    protected void fireStateChanged() {
        Object[] l = listeners.getListenerList();
        for (int i = l.length - 2; i >= 0; i -= 2) {
            if (l[i] == ChangeListener.class) {
                if (changeEvent == null) {
                    changeEvent = new javax.swing.event.ChangeEvent(this);
                }
                ((ChangeListener) l[i + 1]).stateChanged(changeEvent);
            }
        }
    }

    protected String paramString() {
        return super.paramString()
                + ",orientation=" + (orientation == HORIZONTAL ? "HORIZONTAL" : "VERTICAL")
                + ",paintBorder=" + borderPainted
                + ",paintString=" + stringPainted
                + ",progressString=" + (progressString != null ? progressString : "")
                + ",indeterminateString=" + indeterminate;
    }
    public JProgressBar(int orient) { this();setOrientation(orient); }
    public JProgressBar(int min, int max) { this(); setMinimum(min); setMaximum(max); }
    public JProgressBar(int orient, int min, int max) { this(orient); setMinimum(min); setMaximum(max); }
    public JProgressBar(BoundedRangeModel newModel) { this();setModel(newModel); }

    public int getOrientation() { return orientation; }
    public void setOrientation(int o) { if(o!=HORIZONTAL&&o!=VERTICAL)throw new IllegalArgumentException("orientation");int old=orientation;orientation=o;firePropertyChange("orientation",old,o);revalidate();repaint(); }
    public int getValue() { return model.getValue(); }
    public void setValue(int n) { model.setValue(n); }
    public int getMinimum() { return model.getMinimum(); }
    public void setMinimum(int n) { model.setMinimum(n); }
    public int getMaximum() { return model.getMaximum(); }
    public void setMaximum(int n) { model.setMaximum(n); }
    public boolean isStringPainted() { return stringPainted; }
    public void setStringPainted(boolean b) {
        boolean old = stringPainted;
        stringPainted = b;
        firePropertyChange("stringPainted", old, stringPainted);
        if (stringPainted != old) {
            revalidate();
            repaint();
        }
    }
    public boolean isBorderPainted() { return borderPainted; }
    public void setBorderPainted(boolean b) { boolean old = borderPainted; borderPainted = b; firePropertyChange("borderPainted", old, b); if (old != b) repaint(); }
    @Override protected void paintBorder(java.awt.Graphics g) { if (borderPainted) super.paintBorder(g); }
    /** As on the JDK: without an explicit string, the percent complete in the default locale. */
    public String getString() {
        if (progressString != null) {
            return progressString;
        }
        return java.text.NumberFormat.getPercentInstance().format(getPercentComplete());
    }
    public void setString(String s) {
        String old = progressString;
        progressString = s;
        firePropertyChange("string", old, progressString);
        if (progressString == null || old == null || !progressString.equals(old)) {
            repaint();
        }
    }
    public boolean isIndeterminate() { return indeterminate; }
    /** Fires "indeterminate": BasicProgressBarUI starts and stops its animation timer on it. */
    public void setIndeterminate(boolean b) {
        boolean old = indeterminate;
        indeterminate = b;
        firePropertyChange("indeterminate", old, indeterminate);
    }
    public BoundedRangeModel getModel() { return model; }
    public void setModel(BoundedRangeModel m) { java.util.Objects.requireNonNull(m);BoundedRangeModel old=model;old.removeChangeListener(modelListener);model=m;m.addChangeListener(modelListener);firePropertyChange("model",old,m);repaint(); }
    public double getPercentComplete() {
        double range = model.getMaximum() - model.getMinimum();
        return range == 0 ? 0 : (model.getValue() - model.getMinimum()) / range;
    }
    public void addChangeListener(ChangeListener l) { listeners.add(ChangeListener.class,l); }
    public void removeChangeListener(ChangeListener l) { listeners.remove(ChangeListener.class,l); }
    public ChangeListener[] getChangeListeners() { return listeners.getListeners(ChangeListener.class); }
    // JComponent's sizing of the JDK, which the shadow JComponent skips: BasicProgressBarUI
    // measures the bar and its string; a null answer falls back to the component.
    @Override
    public java.awt.Dimension getPreferredSize() {
        if (!isPreferredSizeSet() && ui != null) {
            java.awt.Dimension d = ui.getPreferredSize(this);
            if (d != null) return d;
        }
        return super.getPreferredSize();
    }

    @Override
    public java.awt.Dimension getMinimumSize() {
        if (!isMinimumSizeSet() && ui != null) {
            java.awt.Dimension d = ui.getMinimumSize(this);
            if (d != null) return d;
        }
        return super.getMinimumSize();
    }

    @Override
    public java.awt.Dimension getMaximumSize() {
        if (!isMaximumSizeSet() && ui != null) {
            java.awt.Dimension d = ui.getMaximumSize(this);
            if (d != null) return d;
        }
        return super.getMaximumSize();
    }

    public void setUI(javax.swing.plaf.ProgressBarUI ui) { super.setUI(ui); }
    public javax.swing.plaf.ProgressBarUI getUI() { return (javax.swing.plaf.ProgressBarUI) super.getUI(); }
}
