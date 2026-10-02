package javax.swing;

import javax.swing.event.ChangeListener;

public class JSpinner extends JComponent {
    private static final long serialVersionUID = 1L;

    public static class DefaultEditor extends JPanel {
        private static final long serialVersionUID = 1L;
        public DefaultEditor(JSpinner spinner) {}
        public JFormattedTextField getTextField() { return new JFormattedTextField(); }
        public JSpinner getSpinner() { return null; }
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
    public JSpinner(SpinnerModel model) { this.model = model; }

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
