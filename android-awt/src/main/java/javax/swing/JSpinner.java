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

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.SpinnerUI;
import javax.swing.text.DateFormatter;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;

public class JSpinner extends JComponent implements javax.accessibility.Accessible {
    private static final String uiClassID = "SpinnerUI";

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

    /**
     * The JDK's base editor: a panel laying out one formatted text field, kept in sync with the
     * spinner in both directions. The field is read-only unless a subclass installs a formatter
     * that can parse what the user types.
     */
    public static class DefaultEditor extends JPanel implements ChangeListener, java.beans.PropertyChangeListener, LayoutManager {
        private static final long serialVersionUID = 1L;
        private final JSpinner spinner;
        private final JFormattedTextField textField;

        public DefaultEditor(JSpinner spinner) {
            super((LayoutManager) null);
            this.spinner = spinner;
            textField = new JFormattedTextField();
            textField.setName("Spinner.formattedTextField");
            textField.setValue(spinner.getValue());
            textField.addPropertyChangeListener(this);
            textField.setEditable(false);
            String toolTipText = spinner.getToolTipText();
            if (toolTipText != null) textField.setToolTipText(toolTipText);
            add(textField);
            setLayout(this);
            spinner.addChangeListener(this);
        }

        public JFormattedTextField getTextField() { return textField; }
        public JSpinner getSpinner() { return spinner; }

        /** Pushes the field's text through its formatter; a legal value reaches the model via propertyChange. */
        public void commitEdit() throws ParseException { textField.commitEdit(); }

        public void dismiss(JSpinner spinner) { spinner.removeChangeListener(this); }

        public void stateChanged(ChangeEvent e) { textField.setValue(spinner.getValue()); }

        public void propertyChange(java.beans.PropertyChangeEvent e) {
            if (e.getSource() != textField || !"value".equals(e.getPropertyName())) return;
            Object lastValue = spinner.getValue();
            try {
                spinner.setValue(textField.getValue());
            } catch (IllegalArgumentException iae) {
                try { textField.setValue(lastValue); } catch (IllegalArgumentException ignored) { }
            }
        }

        public void addLayoutComponent(String name, java.awt.Component child) { }
        public void removeLayoutComponent(java.awt.Component child) { }

        private Dimension insetSize(Container parent) {
            Insets insets = parent.getInsets();
            return new Dimension(insets.left + insets.right, insets.top + insets.bottom);
        }

        public Dimension preferredLayoutSize(Container parent) {
            Dimension size = insetSize(parent);
            if (parent.getComponentCount() > 0) {
                Dimension child = parent.getComponent(0).getPreferredSize();
                size.width += child.width;
                size.height += child.height;
            }
            return size;
        }

        public Dimension minimumLayoutSize(Container parent) {
            Dimension size = insetSize(parent);
            if (parent.getComponentCount() > 0) {
                Dimension child = parent.getComponent(0).getMinimumSize();
                size.width += child.width;
                size.height += child.height;
            }
            return size;
        }

        public void layoutContainer(Container parent) {
            if (parent.getComponentCount() > 0) {
                Insets insets = parent.getInsets();
                parent.getComponent(0).setBounds(insets.left, insets.top,
                    parent.getWidth() - insets.left - insets.right,
                    parent.getHeight() - insets.top - insets.bottom);
            }
        }
    }

    /** The JDK formatter of NumberEditor: its range is the model's, read at every parse. */
    private static final class NumberEditorFormatter extends NumberFormatter {
        private final SpinnerNumberModel model;
        NumberEditorFormatter(SpinnerNumberModel model, NumberFormat format) {
            super(format);
            this.model = model;
            setValueClass(model.getValue().getClass());
        }
        @Override public void setMinimum(Comparable min) { model.setMinimum(min); }
        @Override public Comparable getMinimum() { return model.getMinimum(); }
        @Override public void setMaximum(Comparable max) { model.setMaximum(max); }
        @Override public Comparable getMaximum() { return model.getMaximum(); }
    }

    private static final class DateEditorFormatter extends DateFormatter {
        private final SpinnerDateModel model;
        DateEditorFormatter(SpinnerDateModel model, DateFormat format) {
            super(format);
            this.model = model;
        }
        @Override public void setMinimum(Comparable min) { model.setStart(min); }
        @Override public Comparable getMinimum() { return model.getStart(); }
        @Override public void setMaximum(Comparable max) { model.setEnd(max); }
        @Override public Comparable getMaximum() { return model.getEnd(); }
    }

    public static class DateEditor extends DefaultEditor {
        private static final long serialVersionUID = 1L;
        private final SimpleDateFormat format;

        public DateEditor(JSpinner spinner) { this(spinner, defaultDatePattern()); }
        public DateEditor(JSpinner spinner, String dateFormatPattern) {
            this(spinner, new SimpleDateFormat(dateFormatPattern, Locale.getDefault()));
        }
        private DateEditor(JSpinner spinner, SimpleDateFormat format) {
            super(spinner);
            if (!(spinner.getModel() instanceof SpinnerDateModel)) {
                throw new IllegalArgumentException("model not a SpinnerDateModel");
            }
            this.format = format;
            JFormattedTextField ftf = getTextField();
            ftf.setEditable(true);
            ftf.setFormatterFactory(new DefaultFormatterFactory(new DateEditorFormatter((SpinnerDateModel) spinner.getModel(), format)));
            ftf.setValue(spinner.getValue());
        }
        private static String defaultDatePattern() {
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault());
            return format instanceof SimpleDateFormat ? ((SimpleDateFormat) format).toPattern() : "M/d/yy h:mm a";
        }
        public SimpleDateFormat getFormat() { return format; }
        public SpinnerDateModel getModel() { return (SpinnerDateModel) getSpinner().getModel(); }
    }

    public static class NumberEditor extends DefaultEditor {
        private static final long serialVersionUID = 1L;
        private final DecimalFormat format;

        public NumberEditor(JSpinner spinner) { this(spinner, defaultNumberPattern()); }
        public NumberEditor(JSpinner spinner, String decimalFormatPattern) {
            this(spinner, new DecimalFormat(decimalFormatPattern));
        }
        private NumberEditor(JSpinner spinner, DecimalFormat format) {
            super(spinner);
            if (!(spinner.getModel() instanceof SpinnerNumberModel)) {
                throw new IllegalArgumentException("model not a SpinnerNumberModel");
            }
            this.format = format;
            JFormattedTextField ftf = getTextField();
            ftf.setEditable(true);
            ftf.setFormatterFactory(new DefaultFormatterFactory(new NumberEditorFormatter((SpinnerNumberModel) spinner.getModel(), format)));
            ftf.setValue(spinner.getValue());
            ftf.setHorizontalAlignment(SwingConstants.RIGHT);
        }
        private static String defaultNumberPattern() {
            NumberFormat format = NumberFormat.getNumberInstance(Locale.getDefault());
            return format instanceof DecimalFormat ? ((DecimalFormat) format).toPattern() : "#,##0.###";
        }
        public DecimalFormat getFormat() { return format; }
        public SpinnerNumberModel getModel() { return (SpinnerNumberModel) getSpinner().getModel(); }
    }

    public static class ListEditor extends DefaultEditor {
        private static final long serialVersionUID = 1L;

        public ListEditor(JSpinner spinner) {
            super(spinner);
            if (!(spinner.getModel() instanceof SpinnerListModel)) {
                throw new IllegalArgumentException("model not a SpinnerListModel");
            }
            JFormattedTextField ftf = getTextField();
            ftf.setEditable(true);
            ftf.setFormatterFactory(new DefaultFormatterFactory(new ListFormatter()));
            ftf.setValue(spinner.getValue());
        }
        public SpinnerListModel getModel() { return (SpinnerListModel) getSpinner().getModel(); }

        /**
         * The JDK's ListFormatter: the text is the value and the model judges it. An unknown string
         * makes SpinnerListModel.setValue throw, which DefaultEditor catches and reverts the field;
         * the JDK's prefix-completion document filter is not installed here.
         */
        private static final class ListFormatter extends JFormattedTextField.AbstractFormatter {
            public String valueToString(Object value) { return value == null ? "" : value.toString(); }
            public Object stringToValue(String string) { return string; }
        }
    }

    private SpinnerModel model;
    private JComponent editor;
    private boolean editorExplicitlySet;
    private transient ChangeEvent changeEvent;
    private final ChangeListener modelListener = e -> fireStateChanged();

    public JSpinner() { this(new SpinnerNumberModel()); }
    /** The editor is created first; the delegate installed by updateUI adds it with the arrow buttons. */
    public JSpinner(SpinnerModel model) {
        if (model == null) throw new NullPointerException("model cannot be null");
        this.model = model;
        this.editor = createEditor(model);
        setInheritsPopupMenu(true);
        updateUI();
        model.addChangeListener(modelListener);
    }

    public SpinnerUI getUI() { return (SpinnerUI) ui; }
    public void setUI(SpinnerUI ui) { super.setUI(ui); }
    public String getUIClassID() { return uiClassID; }
    /**
     * The look and feel's delegate. One compiled against BasicSpinnerUI members this shadow lacks
     * fails to link while installing; its partial children are dropped and the Basic delegate
     * stands in.
     */
    public void updateUI() {
        try {
            javax.swing.plaf.ComponentUI delegate = UIManager.getUI(this);
            setUI(delegate instanceof SpinnerUI ? (SpinnerUI) delegate : new javax.swing.plaf.basic.BasicSpinnerUI());
        } catch (LinkageError incompatible) {
            ui = null;
            removeAll();
            setUI(new javax.swing.plaf.basic.BasicSpinnerUI());
        }
        invalidate();
    }

    protected JComponent createEditor(SpinnerModel model) {
        if (model instanceof SpinnerDateModel) return new DateEditor(this);
        if (model instanceof SpinnerListModel) return new ListEditor(this);
        if (model instanceof SpinnerNumberModel) return new NumberEditor(this);
        return new DefaultEditor(this);
    }

    public SpinnerModel getModel() { return model; }
    public void setModel(SpinnerModel model) {
        if (model == null) throw new IllegalArgumentException("null model");
        if (model.equals(this.model)) return;
        SpinnerModel old = this.model;
        old.removeChangeListener(modelListener);
        this.model = model;
        model.addChangeListener(modelListener);
        firePropertyChange("model", old, model);
        if (!editorExplicitlySet) {
            setEditor(createEditor(model));
            editorExplicitlySet = false;
        }
        repaint();
        revalidate();
    }

    public Object getValue() { return model.getValue(); }
    public void setValue(Object value) { model.setValue(value); }
    public Object getNextValue() { return model.getNextValue(); }
    public Object getPreviousValue() { return model.getPreviousValue(); }

    public JComponent getEditor() { return editor; }
    public void setEditor(JComponent editor) {
        if (editor == null) throw new IllegalArgumentException("null editor");
        if (editor.equals(this.editor)) return;
        JComponent old = this.editor;
        this.editor = editor;
        if (old instanceof DefaultEditor) ((DefaultEditor) old).dismiss(this);
        editorExplicitlySet = true;
        // The delegate's property listener swaps the "Editor" child.
        firePropertyChange("editor", old, editor);
        revalidate();
        repaint();
    }

    public void commitEdit() throws ParseException {
        if (editor instanceof DefaultEditor) ((DefaultEditor) editor).commitEdit();
    }

    public void addChangeListener(ChangeListener listener) { listenerList.add(ChangeListener.class, listener); }
    public void removeChangeListener(ChangeListener listener) { listenerList.remove(ChangeListener.class, listener); }
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
}
