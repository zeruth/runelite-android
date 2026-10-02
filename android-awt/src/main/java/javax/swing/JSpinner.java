package javax.swing;

import javax.swing.event.ChangeListener;

public class JSpinner extends JComponent {
    private static final long serialVersionUID = 1L;

    public static class DefaultEditor extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JSpinner spinner;
        private final JFormattedTextField textField;
        public DefaultEditor(JSpinner spinner) {
            this.spinner = spinner;
            this.textField = new JFormattedTextField(spinner.getValue());
            add(textField);
        }
        public JFormattedTextField getTextField() { return textField; }
        public JSpinner getSpinner() { return spinner; }
        public void commitEdit() {}
    }

    public static class NumberEditor extends DefaultEditor {
        private static final long serialVersionUID = 1L;
        public NumberEditor(JSpinner spinner) { super(spinner); }
        public NumberEditor(JSpinner spinner, String decimalFormatPattern) { super(spinner); }
    }

    private SpinnerModel model;
    private JComponent editor;

    public JSpinner() { this(new SpinnerNumberModel()); }
    public JSpinner(SpinnerModel model) {
        this.model = model;
        this.editor = model instanceof SpinnerNumberModel ? new NumberEditor(this) : new DefaultEditor(this);
    }

    public SpinnerModel getModel() { return model; }
    public void setModel(SpinnerModel model) { this.model = model; }
    public Object getValue() { return model.getValue(); }
    public void setValue(Object value) { model.setValue(value); }
    public Object getNextValue() { return model.getNextValue(); }
    public Object getPreviousValue() { return model.getPreviousValue(); }
    public JComponent getEditor() { return editor; }
    public void setEditor(JComponent editor) { this.editor = editor; }
    public void commitEdit() throws java.text.ParseException {}
    public void addChangeListener(ChangeListener l) { if (model != null) model.addChangeListener(l); }
    public void removeChangeListener(ChangeListener l) { if (model != null) model.removeChangeListener(l); }
}
