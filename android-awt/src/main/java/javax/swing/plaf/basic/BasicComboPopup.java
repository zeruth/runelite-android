package javax.swing.plaf.basic;

import java.awt.event.*;
import javax.swing.*;

/** Swing list and styling hooks for the Android-hosted combo picker. */
public class BasicComboPopup extends JPopupMenu implements ComboPopup {
    protected JComboBox<Object> comboBox;
    protected JList<Object> list;
    protected JScrollPane scroller;
    private final java.beans.PropertyChangeListener comboChanges = event -> {
        if ("model".equals(event.getPropertyName())) list.setModel(comboBox.getModel());
        else if ("renderer".equals(event.getPropertyName())) list.setCellRenderer(comboBox.getRenderer());
        else if ("lightWeightPopupEnabled".equals(event.getPropertyName())) setLightWeightPopupEnabled(comboBox.isLightWeightPopupEnabled());
    };
    private final MouseListener mouse = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent event) {
            if (comboBox.isEnabled() && event.getButton() == MouseEvent.BUTTON1)
                comboBox.setPopupVisible(!comboBox.isPopupVisible());
        }
    };
    private final MouseMotionListener motion = new MouseMotionAdapter() {};

    public BasicComboPopup(JComboBox<Object> combo) {
        comboBox = combo;
        list = createList();
        configureList();
        scroller = createScroller();
        configureScroller();
        configurePopup();
        comboBox.addPropertyChangeListener(comboChanges);
    }
    protected JList<Object> createList() { return new JList<>(comboBox.getModel()); }
    protected void configureList() {
        list.setFont(comboBox.getFont());
        list.setForeground(comboBox.getForeground());
        list.setBackground(comboBox.getBackground());
        list.setCellRenderer(comboBox.getRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }
    protected JScrollPane createScroller() { return new JScrollPane(list); }
    protected void configureScroller() { scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); }
    protected void configurePopup() {
        setInvoker(comboBox);
        setLightWeightPopupEnabled(comboBox.isLightWeightPopupEnabled());
        add(scroller);
    }
    @Override public JList<Object> getList() { return list; }
    @Override public MouseListener getMouseListener() { return mouse; }
    @Override public MouseMotionListener getMouseMotionListener() { return motion; }
    @Override public KeyListener getKeyListener() { return null; }
    @Override public void show() {
        list.setSelectedIndex(comboBox.getSelectedIndex());
        setVisible(true);
        comboBox.hostSetPopupVisible(true);
    }
    @Override public void hide() {
        setVisible(false);
        comboBox.hostSetPopupVisible(false);
    }
    @Override public void uninstallingUI() {
        if (isVisible()) hide();
        comboBox.removePropertyChangeListener(comboChanges);
        list.setModel(new DefaultListModel<>());
    }
}
