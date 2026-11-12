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

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuListener;

/**
 * Selection lives in the model, as in Swing: the combo listens to its ComboBoxModel and
 * turns contentsChanged/interval events into ItemEvents (DESELECTED old, SELECTED new) and
 * "comboBoxChanged" ActionEvents. Keeping a private copy of the selection here meant
 * array-built combos started blank and removeAllItems() never cleared anything.
 */
public class JComboBox<E> extends JComponent implements ListDataListener {
    private static final String uiClassID = "ComboBoxUI";
    public String getUIClassID() { return uiClassID; }
    private static final long serialVersionUID = -2378717525866512215L;

    public interface KeySelectionManager {
        int selectionForKey(char aKey, ComboBoxModel<?> aModel);
    }

    protected ComboBoxModel<E> dataModel;
    protected ListCellRenderer<? super E> renderer = new DefaultListCellRenderer();
    protected Object selectedItemReminder;
    protected boolean isEditable;
    protected ComboBoxEditor editor;
    private final ActionListener editorListener = event -> {
        if (!isEditable || editor == null) return;
        setSelectedItem(editor.getItem());
        String previous = this.actionCommand;
        try { actionCommand = "comboBoxEdited"; fireActionEvent(); }
        finally { actionCommand = previous; }
    };
    protected int maximumRowCount = 8;
    protected String actionCommand = "comboBoxChanged";
    private boolean selectingItem;
    private transient java.awt.Dimension measuredSize;
    private transient java.awt.Font measuredFont;

    @Override public void invalidate() {
        measuredSize = null;
        super.invalidate();
    }

    public JComboBox() { setModel(new DefaultComboBoxModel<>()); updateUI(); }
    public JComboBox(ComboBoxModel<E> model) { setModel(model); updateUI(); }
    public JComboBox(E[] items) { setModel(new DefaultComboBoxModel<>(items)); updateUI(); }
    public JComboBox(java.util.Vector<E> items) { setModel(new DefaultComboBoxModel<>(items)); updateUI(); }

    @Override public javax.swing.plaf.ComboBoxUI getUI() {
        return (javax.swing.plaf.ComboBoxUI) ui;
    }
    public void setUI(javax.swing.plaf.ComboBoxUI delegate) {
        super.setUI(delegate);
        revalidate(); repaint();
    }
    @Override public void updateUI() {
        setUI(new javax.swing.plaf.basic.BasicComboBoxUI());
        if (editor == null || editor instanceof javax.swing.plaf.UIResource)
            setEditor(new javax.swing.plaf.basic.BasicComboBoxEditor.UIResource());
    }
    public ComboBoxEditor getEditor() { return editor; }
    public void setEditor(ComboBoxEditor value) {
        ComboBoxEditor old = editor;
        if (old != null) old.removeActionListener(editorListener);
        editor = value;
        if (editor != null) {
            editor.addActionListener(editorListener);
            configureEditor(editor, getSelectedItem());
        }
        firePropertyChange("editor", old, value);
        invalidate(); repaint();
    }
    public void configureEditor(ComboBoxEditor editor, Object item) { editor.setItem(item); }

    public ComboBoxModel<E> getModel() { return dataModel; }
    public void setModel(ComboBoxModel<E> aModel) {
        ComboBoxModel<E> old = dataModel;
        if (old != null) old.removeListDataListener(this);
        dataModel = aModel;
        dataModel.addListDataListener(this);
        // Swing takes the model's current selection without firing for it.
        selectedItemReminder = dataModel.getSelectedItem();
        if (editor != null) configureEditor(editor, selectedItemReminder);
        firePropertyChange("model", old, aModel);
        invalidate();
    }
    public ListCellRenderer<? super E> getRenderer() { return renderer; }
    public void setRenderer(ListCellRenderer<? super E> r) {
        ListCellRenderer<? super E> old = renderer;
        renderer = r;
        firePropertyChange("renderer", old, r);
        invalidate(); repaint();
    }

    public boolean isEditable() { return isEditable; }
    public void setEditable(boolean b) {
        boolean old = isEditable;
        isEditable = b;
        if (editor != null) configureEditor(editor, getSelectedItem());
        firePropertyChange("editable", old, b);
        invalidate(); repaint();
    }
    public int getItemCount() { return dataModel.getSize(); }
    public E getItemAt(int index) { return dataModel.getElementAt(index); }

    public String getActionCommand() { return actionCommand; }
    public void setActionCommand(String aCommand) { this.actionCommand = aCommand; }

    private MutableComboBoxModel<E> mutableModel() {
        if (!(dataModel instanceof MutableComboBoxModel)) {
            throw new RuntimeException("Cannot use this method with a non-Mutable data model.");
        }
        return (MutableComboBoxModel<E>) dataModel;
    }

    public void addItem(E item) { mutableModel().addElement(item); }
    public void insertItemAt(E item, int index) { mutableModel().insertElementAt(item, index); }
    public void removeItem(Object anObject) { mutableModel().removeElement(anObject); }
    public void removeItemAt(int anIndex) { mutableModel().removeElementAt(anIndex); }
    public void removeAllItems() {
        MutableComboBoxModel<E> model = mutableModel();
        int size = model.getSize();
        if (model instanceof DefaultComboBoxModel) {
            ((DefaultComboBoxModel<E>) model).removeAllElements();
        } else {
            for (int i = 0; i < size; ++i) model.removeElement(model.getElementAt(0));
        }
        selectedItemReminder = null;
    }

    public Object getSelectedItem() { return dataModel.getSelectedItem(); }
    public void setSelectedItem(Object anObject) {
        Object objectToSelect = anObject;
        if (selectedItemReminder == null || !selectedItemReminder.equals(anObject)) {
            if (anObject != null && !isEditable()) {
                // A non-editable combo rejects items that aren't in its list.
                boolean found = false;
                for (int i = 0; i < dataModel.getSize(); i++) {
                    E element = dataModel.getElementAt(i);
                    if (anObject.equals(element)) {
                        found = true;
                        objectToSelect = element;
                        break;
                    }
                }
                if (!found) return;
            }
            selectingItem = true;
            dataModel.setSelectedItem(objectToSelect);
            selectingItem = false;
            // For models that don't fire on selection.
            if (selectedItemReminder != dataModel.getSelectedItem()) selectedItemChanged();
        }
        fireActionEvent();
    }
    public int getSelectedIndex() {
        Object sObject = dataModel.getSelectedItem();
        for (int i = 0; i < dataModel.getSize(); i++) {
            E obj = dataModel.getElementAt(i);
            if (obj != null && obj.equals(sObject)) return i;
        }
        return -1;
    }
    public void setSelectedIndex(int anIndex) {
        int size = dataModel.getSize();
        if (anIndex == -1) setSelectedItem(null);
        else if (anIndex < -1 || anIndex >= size) throw new IllegalArgumentException("setSelectedIndex: " + anIndex + " out of bounds");
        else setSelectedItem(dataModel.getElementAt(anIndex));
    }
    public Object[] getSelectedObjects() {
        Object selectedObject = getSelectedItem();
        return selectedObject == null ? new Object[0] : new Object[]{selectedObject};
    }

    protected void selectedItemChanged() {
        if (selectedItemReminder != null) {
            fireItemStateChanged(new java.awt.event.ItemEvent(this, java.awt.event.ItemEvent.ITEM_STATE_CHANGED,
                selectedItemReminder, java.awt.event.ItemEvent.DESELECTED));
        }
        selectedItemReminder = dataModel.getSelectedItem();
        if (editor != null) configureEditor(editor, selectedItemReminder);
        if (selectedItemReminder != null) {
            fireItemStateChanged(new java.awt.event.ItemEvent(this, java.awt.event.ItemEvent.ITEM_STATE_CHANGED,
                selectedItemReminder, java.awt.event.ItemEvent.SELECTED));
        }
        invalidate();
    }

    protected void fireItemStateChanged(java.awt.event.ItemEvent e) {
        for (ItemListener l : new java.util.ArrayList<>(itemListeners)) {
            try { l.itemStateChanged(e); } catch (Throwable ignored) {}
        }
    }

    protected void fireActionEvent() {
        java.awt.event.ActionEvent e = new java.awt.event.ActionEvent(
            this, java.awt.event.ActionEvent.ACTION_PERFORMED, getActionCommand());
        for (ActionListener l : new java.util.ArrayList<>(actionListeners)) {
            try { l.actionPerformed(e); } catch (Throwable ignored) {}
        }
    }

    // ListDataListener — Swing's JComboBox reacts to its own model exactly like this.
    @Override public void contentsChanged(ListDataEvent e) {
        Object oldSelection = selectedItemReminder;
        Object newSelection = dataModel.getSelectedItem();
        if (oldSelection == null || !oldSelection.equals(newSelection)) {
            selectedItemChanged();
            if (!selectingItem) fireActionEvent();
        }
        invalidate();
    }
    @Override public void intervalAdded(ListDataEvent e) {
        if (selectedItemReminder != dataModel.getSelectedItem()) selectedItemChanged();
        invalidate();
    }
    @Override public void intervalRemoved(ListDataEvent e) { contentsChanged(e); }

    public int getMaximumRowCount() { return maximumRowCount; }
    public void setMaximumRowCount(int count) { this.maximumRowCount = count; }

    // Prototype value real Swing uses to size the combo's cells before data loads. We don't
    // lay out from it, but plugins set it (resource-packs' HubPanel) and without the method
    // their panel constructor dies with NoSuchMethodError — which silently prevents the
    // plugin from registering its toolbar nav button.
    private Object prototypeDisplayValue;
    public Object getPrototypeDisplayValue() { return prototypeDisplayValue; }
    public void setPrototypeDisplayValue(Object value) { this.prototypeDisplayValue = value; invalidate(); }
    public interface PopupHandler { void show(JComboBox<?> combo, boolean visible); }
    private static volatile PopupHandler popupHandler;
    public static void setPopupHandler(PopupHandler handler) { popupHandler = handler; }
    private boolean popupVisible;
    private boolean lightWeightPopupEnabled = JPopupMenu.getDefaultLightWeightPopupEnabled();
    public boolean isLightWeightPopupEnabled() { return lightWeightPopupEnabled; }
    public void setLightWeightPopupEnabled(boolean value) {
        boolean old = lightWeightPopupEnabled;
        lightWeightPopupEnabled = value;
        firePropertyChange("lightWeightPopupEnabled", old, value);
    }
    private final javax.swing.event.EventListenerList popupListeners = new javax.swing.event.EventListenerList();
    public void showPopup() { setPopupVisible(true); }
    public void hidePopup() { setPopupVisible(false); }
    public void setPopupVisible(boolean visible) {
        if (visible == isPopupVisible() || (visible && !isEnabled())) return;
        if (getUI() != null) getUI().setPopupVisible(this, visible);
        else hostSetPopupVisible(visible);
    }
    public void hostSetPopupVisible(boolean visible) {
        if (popupVisible == visible || (visible && !isEnabled())) return;
        popupVisible = visible;
        javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(this);
        for (PopupMenuListener listener : getPopupMenuListeners()) {
            if (visible) listener.popupMenuWillBecomeVisible(event); else listener.popupMenuWillBecomeInvisible(event);
        }
        PopupHandler handler = popupHandler;
        if (handler != null) handler.show(this, visible);
        firePropertyChange("popupVisible", !visible, visible);
    }
    public boolean isPopupVisible() { return getUI() == null ? popupVisible : getUI().isPopupVisible(this); }
    public void firePopupMenuCanceled() {
        javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(this);
        for (PopupMenuListener listener : getPopupMenuListeners()) listener.popupMenuCanceled(event);
        hidePopup();
    }

    private final java.util.List<ActionListener> actionListeners = new java.util.ArrayList<>();
    private final java.util.List<ItemListener> itemListeners = new java.util.ArrayList<>();

    public void addActionListener(ActionListener l) { if (l != null) actionListeners.add(l); }
    public void removeActionListener(ActionListener l) { actionListeners.remove(l); }
    public ActionListener[] getActionListeners() { return actionListeners.toArray(new ActionListener[0]); }
    public void addItemListener(ItemListener l) { if (l != null) itemListeners.add(l); }
    public void removeItemListener(ItemListener l) { itemListeners.remove(l); }
    public ItemListener[] getItemListeners() { return itemListeners.toArray(new ItemListener[0]); }
    public void addPopupMenuListener(PopupMenuListener l) { popupListeners.add(PopupMenuListener.class, l); }
    public void removePopupMenuListener(PopupMenuListener l) { popupListeners.remove(PopupMenuListener.class, l); }
    public PopupMenuListener[] getPopupMenuListeners() { return popupListeners.getListeners(PopupMenuListener.class); }

    /** Stand-in for BasicComboBoxUI's popup list: renderers are handed a real JList and
     *  some (quest-helper's DropdownRenderer) read its colours. */
    private transient JList<E> renderList;

    @SuppressWarnings("unchecked")
    private JList<E> renderList() {
        if (getUI() instanceof javax.swing.plaf.basic.BasicComboBoxUI) {
            JList<?> popupList = ((javax.swing.plaf.basic.BasicComboBoxUI) getUI()).hostPopupList();
            if (popupList != null) return (JList<E>) popupList;
        }
        JList<E> list = renderList;
        if (list == null) renderList = list = new JList<>(dataModel);
        else if (list.getModel() != dataModel) list.setModel(dataModel);
        list.setFont(getFont());
        list.setForeground(getForeground());
        list.setBackground(getBackground());
        return list;
    }

    /** The plugin's renderer component for `item`, or null if it throws. */
    @SuppressWarnings("unchecked")
    private java.awt.Component rendererComponent(Object item, int index) {
        ListCellRenderer<? super E> r = renderer;
        if (r == null) return null;
        try {
            return r.getListCellRendererComponent(renderList(), (E) item, index, index >= 0 && index == getSelectedIndex(), false);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String textOf(java.awt.Component c, Object item) {
        if (c instanceof JLabel) return ((JLabel) c).getText();
        if (c instanceof AbstractButton) return ((AbstractButton) c).getText();
        return item == null ? "" : item.toString();
    }

    /**
     * Text the combo's cell renderer shows for `item` (at list `index`, -1 for the
     * current value) — what desktop Swing draws, e.g. quest-helper's title-cased enum names
     * rather than toString()'s SHOW_MEETS_REQS. The Android panel host builds its picker
     * from this.
     */
    public String hostDisplayText(Object item, int index) {
        return textOf(rendererComponent(item, index), item);
    }

    @Override public java.awt.Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        java.awt.Font font = getFont();
        if (measuredSize == null || !java.util.Objects.equals(font, measuredFont)) {
            java.awt.FontMetrics fm = getFontMetrics(font);
            int width = 60;
            if (prototypeDisplayValue != null) {
                width = Math.max(width, fm.stringWidth(hostDisplayText(prototypeDisplayValue, -1)));
            } else {
                for (int i = 0; i < getItemCount(); i++)
                    width = Math.max(width, fm.stringWidth(hostDisplayText(getItemAt(i), i)));
            }
            measuredSize = new java.awt.Dimension(width + 24, fm.getHeight() + 10);
            measuredFont = font;
        }
        return new java.awt.Dimension(measuredSize);
    }

    /**
     * BasicComboBoxUI.paintCurrentValue: the renderer component paints the selected value,
     * with the combo's font and colours forced on it, left of the arrow.
     */
    @Override protected void paintComponent(java.awt.Graphics g) {
        if (paintDelegate(g)) return;
        hostPaintValue(g, new java.awt.Rectangle(4, 1, Math.max(0, getWidth() - 20), Math.max(0, getHeight() - 2)));
    }
    public void hostPaintValue(java.awt.Graphics g, java.awt.Rectangle bounds) {
        java.awt.Color fg = isEnabled() ? getForeground() : java.awt.Color.GRAY;
        g.setColor(fg);
        g.setFont(getFont());
        java.awt.FontMetrics fm = g.getFontMetrics();
        int baseline = bounds.y + (bounds.height - fm.getHeight()) / 2 + fm.getAscent();
        Object selected = getSelectedItem();
        java.awt.Component c = isEditable && editor != null ? editor.getEditorComponent() : rendererComponent(selected, -1);
        int cellW = bounds.width, cellH = bounds.height;
        boolean painted = false;
        if (c instanceof JComponent && cellW > 0 && cellH > 0) {
            c.setFont(getFont());
            c.setForeground(fg);
            c.setBackground(getBackground());
            c.setBounds(0, 0, cellW, cellH);
            if (c instanceof java.awt.Container) ((java.awt.Container) c).validate();
            java.awt.Graphics cg = g.create(bounds.x, bounds.y, cellW, cellH);
            try {
                c.paint(cg);
                painted = true;
            } catch (Throwable ignored) {
            } finally {
                cg.dispose();
            }
        }
        if (!painted) {
            String text = textOf(c, selected);
            if (text != null && !text.isEmpty()) {
                g.setColor(fg);
                g.drawString(text, bounds.x, baseline);
            }
        }
    }
}
