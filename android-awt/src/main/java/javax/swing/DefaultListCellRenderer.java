package javax.swing;

import java.awt.Component;
import java.io.Serializable;

public class DefaultListCellRenderer extends JLabel implements ListCellRenderer<Object>, Serializable {
    private static final long serialVersionUID = 7708515637856787613L;

    public static class UIResource extends DefaultListCellRenderer implements javax.swing.plaf.UIResource {
        private static final long serialVersionUID = 1L;
    }

    public DefaultListCellRenderer() {}

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        setText(value == null ? "" : value.toString());
        setFont(list.getFont());
        setEnabled(list.isEnabled());
        setOpaque(true);
        setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
        setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
        return this;
    }
}
