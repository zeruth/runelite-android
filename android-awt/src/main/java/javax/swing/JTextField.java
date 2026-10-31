package javax.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

public class JTextField extends JTextComponent {
    private static final long serialVersionUID = 353936840712925302L;

    public static final String notifyAction = "notify-field-accept";

    private int columns;
    private int horizontalAlignment = SwingConstants.LEADING;
    private final java.util.List<ActionListener> actionListeners = new java.util.ArrayList<>();

    public JTextField() {}
    public JTextField(String text) { setText(text); }
    public JTextField(int columns) { this.columns = columns; }
    public JTextField(String text, int columns) { setText(text); this.columns = columns; }
    public JTextField(Document doc, String text, int columns) { setDocument(doc); setText(text); this.columns = columns; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; cachedPref = null; invalidate(); }

    public int getHorizontalAlignment() { return horizontalAlignment; }
    public void setHorizontalAlignment(int alignment) { this.horizontalAlignment = alignment; }

    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public void postActionEvent() {
        ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, notifyAction);
        for (ActionListener l : new java.util.ArrayList<>(actionListeners)) {
            try { l.actionPerformed(ev); } catch (Throwable ignored) {}
        }
    }

    private java.awt.Dimension cachedPref;
    @Override public void invalidate() { cachedPref = null; super.invalidate(); }
    @Override
    public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Dimension c = cachedPref;
        if (c != null) return new java.awt.Dimension(c);
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        int colW = columns > 0
            ? columns * fm.charWidth('m')
            : Math.max(60, fm.stringWidth(getText() == null ? "" : getText()) + 8);
        c = new java.awt.Dimension(colW + 4, fm.getHeight() + 4);
        cachedPref = c;
        return new java.awt.Dimension(c);
    }

    @Override
    public void setText(String t) { super.setText(t); cachedPref = null; invalidate(); }
    @Override
    public void setFont(java.awt.Font f) { super.setFont(f); cachedPref = null; invalidate(); }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        String s = getText();
        if (s == null || s.isEmpty()) return;
        java.awt.FontMetrics fm = g.getFontMetrics(getFont());
        java.awt.Color saved = g.getColor();
        g.setColor(getForeground());
        g.setFont(getFont());
        int x = horizontalAlignment == SwingConstants.CENTER
            ? (getWidth() - fm.stringWidth(s)) / 2
            : (horizontalAlignment == SwingConstants.RIGHT || horizontalAlignment == SwingConstants.TRAILING
                ? getWidth() - fm.stringWidth(s) - 4
                : 4);
        int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(s, x, baseline);
        if (saved != null) g.setColor(saved);
    }
}
