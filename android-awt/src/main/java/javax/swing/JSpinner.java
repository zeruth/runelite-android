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

public class JSpinner extends JComponent implements javax.accessibility.Accessible {
    private static final String uiClassID = "SpinnerUI";
    public javax.swing.plaf.SpinnerUI getUI() { return (javax.swing.plaf.SpinnerUI) ui; }
    public void setUI(javax.swing.plaf.SpinnerUI ui) { super.setUI(ui); }
    public String getUIClassID() { return uiClassID; }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJSpinner();
        }
        return accessibleContext;
    }

    /** Accessibility support for spinners: role SPIN_BOX. */
    protected class AccessibleJSpinner extends AccessibleJComponent {
        public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.SPIN_BOX;
        }
    }
    private static final long serialVersionUID = 1L;

    public static class DefaultEditor extends JPanel implements ChangeListener, java.beans.PropertyChangeListener {
        private static final long serialVersionUID = 1L;
        private final JSpinner spinner;
        private final JFormattedTextField textField;
        public DefaultEditor(JSpinner spinner) {
            this.spinner = spinner;
            this.textField = new JFormattedTextField(spinner.getValue());
            add(textField);
            spinner.addChangeListener(this); textField.addPropertyChangeListener("value",this);
        }
        public JFormattedTextField getTextField() { return textField; }
        public JSpinner getSpinner() { return spinner; }
        public void commitEdit() throws java.text.ParseException { textField.commitEdit();spinner.setValue(textField.getValue()); }
        public void stateChanged(javax.swing.event.ChangeEvent event){textField.setValue(spinner.getValue());}
        public void propertyChange(java.beans.PropertyChangeEvent event){if(!java.util.Objects.equals(spinner.getValue(),textField.getValue()))try{spinner.setValue(textField.getValue());}catch(IllegalArgumentException invalid){textField.setValue(spinner.getValue());}}
        public void dismiss(JSpinner spinner){spinner.removeChangeListener(this);textField.removePropertyChangeListener("value",this);}
    }

    public static class DateEditor extends DefaultEditor {
        private final java.text.SimpleDateFormat format;
        public DateEditor(JSpinner spinner){this(spinner,"yyyy-MM-dd");}
        public DateEditor(JSpinner spinner,String pattern){super(spinner);if(!(spinner.getModel() instanceof SpinnerDateModel))throw new IllegalArgumentException("date model required");format=new java.text.SimpleDateFormat(pattern);format.setLenient(false);getTextField().setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.DateFormatter(format)));getTextField().setValue(spinner.getValue());}
        public java.text.SimpleDateFormat getFormat(){return format;}
        public SpinnerDateModel getModel(){return (SpinnerDateModel)getSpinner().getModel();}
    }
    public static class NumberEditor extends DefaultEditor {
        private final java.text.DecimalFormat format;
        public NumberEditor(JSpinner spinner){this(spinner,"#0.###");}
        public NumberEditor(JSpinner spinner,String pattern){super(spinner);if(!(spinner.getModel() instanceof SpinnerNumberModel))throw new IllegalArgumentException("number model required");format=new java.text.DecimalFormat(pattern);javax.swing.text.NumberFormatter formatter=new javax.swing.text.NumberFormatter(format);formatter.setValueClass(spinner.getValue().getClass());getTextField().setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(formatter));getTextField().setValue(spinner.getValue());}
        public java.text.DecimalFormat getFormat(){return format;}
        public SpinnerNumberModel getModel(){return (SpinnerNumberModel)getSpinner().getModel();}
    }

    private SpinnerModel model;
    private JComponent editor;
    private final javax.swing.event.EventListenerList listeners=new javax.swing.event.EventListenerList();
    private final ChangeListener modelListener=e->{javax.swing.event.ChangeEvent event=new javax.swing.event.ChangeEvent(this);for(ChangeListener listener:getChangeListeners())listener.stateChanged(event);repaint();};
    private boolean editorExplicit;
    protected JComponent createEditor(SpinnerModel model){return model instanceof SpinnerNumberModel?new NumberEditor(this):model instanceof SpinnerDateModel?new DateEditor(this):new DefaultEditor(this);}

    public JSpinner() { this(new SpinnerNumberModel()); }
    public JSpinner(SpinnerModel model) {
        this.model=java.util.Objects.requireNonNull(model);model.addChangeListener(modelListener);
        this.editor=createEditor(model);
    }

    public SpinnerModel getModel() { return model; }
    public void setModel(SpinnerModel model) { java.util.Objects.requireNonNull(model);SpinnerModel old=this.model;old.removeChangeListener(modelListener);this.model=model;model.addChangeListener(modelListener);if(!editorExplicit){if(editor instanceof DefaultEditor)((DefaultEditor)editor).dismiss(this);editor=createEditor(model);}firePropertyChange("model",old,model);modelListener.stateChanged(new javax.swing.event.ChangeEvent(model)); }
    public Object getValue() { return model.getValue(); }
    public void setValue(Object value) { model.setValue(value); }
    public Object getNextValue() { return model.getNextValue(); }
    public Object getPreviousValue() { return model.getPreviousValue(); }
    public JComponent getEditor() { return editor; }
    public void setEditor(JComponent editor) { java.util.Objects.requireNonNull(editor);JComponent old=this.editor;if(old instanceof DefaultEditor)((DefaultEditor)old).dismiss(this);this.editor=editor;editorExplicit=true;firePropertyChange("editor",old,editor);revalidate();repaint(); }
    public void commitEdit() throws java.text.ParseException { if (editor instanceof DefaultEditor) ((DefaultEditor)editor).commitEdit(); }
    public void addChangeListener(ChangeListener l) { listeners.add(ChangeListener.class,l); }
    public void removeChangeListener(ChangeListener l) { listeners.remove(ChangeListener.class,l); }
    public ChangeListener[] getChangeListeners() { return listeners.getListeners(ChangeListener.class); }
    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        return new java.awt.Dimension(Math.max(70, fm.stringWidth(String.valueOf(getValue())) + 30), fm.getHeight() + 10);
    }

    @Override protected void paintComponent(java.awt.Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(isEnabled() ? getForeground() : java.awt.Color.GRAY);
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g.setFont(getFont());
        java.awt.FontMetrics fm = g.getFontMetrics();
        g.drawString(String.valueOf(getValue()), 4, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g.drawString("+/-", Math.max(0, getWidth() - 24), (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
    }
}
