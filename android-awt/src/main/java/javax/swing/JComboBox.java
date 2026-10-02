package javax.swing;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import javax.swing.event.PopupMenuListener;

public class JComboBox<E> extends JComponent {
    private static final long serialVersionUID = -2378717525866512215L;

    public interface KeySelectionManager {
        int selectionForKey(char aKey, ComboBoxModel<?> aModel);
    }

    protected ComboBoxModel<E> dataModel;
    protected ListCellRenderer<? super E> renderer = new DefaultListCellRenderer();
    private Object selectedItem;
    private boolean editable;
    private int maximumRowCount = 8;

    public JComboBox() { this.dataModel = new DefaultComboBoxModel<>(); }
    public JComboBox(ComboBoxModel<E> model) { this.dataModel = model; }
    public JComboBox(E[] items) {
        DefaultComboBoxModel<E> m = new DefaultComboBoxModel<>();
        for (E i : items) m.addElement(i);
        this.dataModel = m;
    }
    public JComboBox(java.util.Vector<E> items) {
        DefaultComboBoxModel<E> m = new DefaultComboBoxModel<>();
        for (E i : items) m.addElement(i);
        this.dataModel = m;
    }

    public ComboBoxModel<E> getModel() { return dataModel; }
    public void setModel(ComboBoxModel<E> aModel) { this.dataModel = aModel; invalidate(); }
    public ListCellRenderer<? super E> getRenderer() { return renderer; }
    public void setRenderer(ListCellRenderer<? super E> r) { this.renderer = r; }

    public boolean isEditable() { return editable; }
    public void setEditable(boolean b) { this.editable = b; }
    public int getItemCount() { return dataModel.getSize(); }
    public E getItemAt(int index) { return dataModel.getElementAt(index); }
    public void addItem(E item) {
        if (dataModel instanceof MutableComboBoxModel) ((MutableComboBoxModel<E>) dataModel).addElement(item);
    }
    public void removeItem(Object anObject) {
        if (dataModel instanceof MutableComboBoxModel) ((MutableComboBoxModel<E>) dataModel).removeElement(anObject);
    }
    public void removeAllItems() {}
    public void removeItemAt(int anIndex) {
        if (dataModel instanceof MutableComboBoxModel) ((MutableComboBoxModel<E>) dataModel).removeElementAt(anIndex);
    }
    public Object getSelectedItem() { return selectedItem; }
    public void setSelectedItem(Object anObject) {
        if (java.util.Objects.equals(this.selectedItem, anObject)) return;
        Object old = this.selectedItem;
        this.selectedItem = anObject;
        dataModel.setSelectedItem(anObject);
        invalidate();
        if (old != null) {
            java.awt.event.ItemEvent dev = new java.awt.event.ItemEvent(
                this, java.awt.event.ItemEvent.ITEM_STATE_CHANGED, old,
                java.awt.event.ItemEvent.DESELECTED);
            for (java.awt.event.ItemListener l : new java.util.ArrayList<>(itemListeners)) {
                try { l.itemStateChanged(dev); } catch (Throwable ignored) {}
            }
        }
        if (anObject != null) {
            java.awt.event.ItemEvent sev = new java.awt.event.ItemEvent(
                this, java.awt.event.ItemEvent.ITEM_STATE_CHANGED, anObject,
                java.awt.event.ItemEvent.SELECTED);
            for (java.awt.event.ItemListener l : new java.util.ArrayList<>(itemListeners)) {
                try { l.itemStateChanged(sev); } catch (Throwable ignored) {}
            }
        }
        java.awt.event.ActionEvent aev = new java.awt.event.ActionEvent(
            this, java.awt.event.ActionEvent.ACTION_PERFORMED, "comboBoxChanged");
        for (java.awt.event.ActionListener l : new java.util.ArrayList<>(actionListeners)) {
            try { l.actionPerformed(aev); } catch (Throwable ignored) {}
        }
    }
    public int getSelectedIndex() {
        if (selectedItem == null || dataModel == null) return -1;
        for (int i = 0; i < dataModel.getSize(); i++) {
            if (java.util.Objects.equals(dataModel.getElementAt(i), selectedItem)) return i;
        }
        return -1;
    }
    public void setSelectedIndex(int anIndex) {
        if (dataModel == null || anIndex < 0 || anIndex >= dataModel.getSize()) return;
        setSelectedItem(dataModel.getElementAt(anIndex));
    }
    public Object[] getSelectedObjects() { return selectedItem == null ? new Object[0] : new Object[]{selectedItem}; }

    public int getMaximumRowCount() { return maximumRowCount; }
    public void setMaximumRowCount(int count) { this.maximumRowCount = count; }

    // Prototype value real Swing uses to size the combo's cells before data loads. We don't
    // lay out from it, but plugins set it (resource-packs' HubPanel) and without the method
    // their panel constructor dies with NoSuchMethodError — which silently prevents the
    // plugin from registering its toolbar nav button.
    private Object prototypeDisplayValue;
    public Object getPrototypeDisplayValue() { return prototypeDisplayValue; }
    public void setPrototypeDisplayValue(Object value) { this.prototypeDisplayValue = value; }
    public void showPopup() {}
    public void hidePopup() {}
    public void setPopupVisible(boolean v) {}
    public boolean isPopupVisible() { return false; }

    private final java.util.List<ActionListener> actionListeners = new java.util.ArrayList<>();
    private final java.util.List<ItemListener> itemListeners = new java.util.ArrayList<>();

    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public void addItemListener(ItemListener l) { if (l != null) itemListeners.add(l); }
    public void removeItemListener(ItemListener l) { itemListeners.remove(l); }
    public ItemListener[] getItemListeners() { return itemListeners.toArray(new ItemListener[0]); }
    public void addPopupMenuListener(PopupMenuListener l) {}
    public void removePopupMenuListener(PopupMenuListener l) {}

    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.FontMetrics fm = getFontMetrics(getFont());
        int width = 60;
        for (int i = 0; i < getItemCount(); i++) width = Math.max(width, fm.stringWidth(String.valueOf(getItemAt(i))));
        return new java.awt.Dimension(width + 24, fm.getHeight() + 10);
    }

    @Override protected void paintComponent(java.awt.Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(isEnabled() ? getForeground() : java.awt.Color.GRAY);
        g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        g.setFont(getFont());
        java.awt.FontMetrics fm = g.getFontMetrics();
        if (selectedItem != null) g.drawString(selectedItem.toString(), 4, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g.drawString("v", Math.max(0, getWidth() - 13), (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
    }
}
