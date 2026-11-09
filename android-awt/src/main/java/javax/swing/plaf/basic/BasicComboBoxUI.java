package javax.swing.plaf.basic;

import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.*;
import javax.swing.plaf.*;

/** Delegate hooks for plugin styling; Android's panel host owns the interactive picker. */
public class BasicComboBoxUI extends ComboBoxUI {
    protected JComboBox<Object> comboBox;
    protected JButton arrowButton;
    protected ComboPopup popup;
    protected JList<Object> listBox;
    public static ComponentUI createUI(JComponent c) { return new BasicComboBoxUI(); }
    @SuppressWarnings("unchecked")
    @Override public void installUI(JComponent c) {
        comboBox = (JComboBox<Object>) c;
        popup = createPopup();
        listBox = popup.getList();
        arrowButton = createArrowButton();
        installListeners();
    }
    @Override public void uninstallUI(JComponent c) {
        uninstallListeners();
        popup.uninstallingUI();
        comboBox = null; arrowButton = null; popup = null; listBox = null;
    }
    protected ComboPopup createPopup() { return new BasicComboPopup(comboBox); }
    protected void installListeners() {
        if (popup.getMouseListener() != null) comboBox.addMouseListener(popup.getMouseListener());
        if (popup.getMouseMotionListener() != null) comboBox.addMouseMotionListener(popup.getMouseMotionListener());
        if (popup.getKeyListener() != null) comboBox.addKeyListener(popup.getKeyListener());
    }
    protected void uninstallListeners() {
        if (popup.getMouseListener() != null) comboBox.removeMouseListener(popup.getMouseListener());
        if (popup.getMouseMotionListener() != null) comboBox.removeMouseMotionListener(popup.getMouseMotionListener());
        if (popup.getKeyListener() != null) comboBox.removeKeyListener(popup.getKeyListener());
    }
    public JList<Object> hostPopupList() { return listBox; }
    protected JButton createArrowButton() { return new BasicArrowButton(SwingConstants.SOUTH); }
    @Override public void setPopupVisible(JComboBox<?> c, boolean visible) { if (visible) popup.show(); else popup.hide(); }
    @Override public boolean isPopupVisible(JComboBox<?> c) { return popup != null && popup.isVisible(); }
    @Override public boolean isFocusTraversable(JComboBox<?> c) { return c.isEnabled(); }
    @Override public void paint(Graphics g, JComponent c) {
        Rectangle bounds = new Rectangle(4, 1, Math.max(0, c.getWidth() - 20), Math.max(0, c.getHeight() - 2));
        paintCurrentValueBackground(g, bounds, c.hasFocus());
        paintCurrentValue(g, bounds, c.hasFocus());
        paintArrow(g, Math.max(0, c.getWidth() - 16), 1, 15, bounds.height);
    }
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focused) {
        g.setColor(comboBox.getBackground());
        g.fillRect(0, 0, comboBox.getWidth(), comboBox.getHeight());
        g.setColor(comboBox.isEnabled() ? comboBox.getForeground() : java.awt.Color.GRAY);
        g.drawRect(0, 0, comboBox.getWidth() - 1, comboBox.getHeight() - 1);
    }
    public void paintCurrentValue(Graphics g, Rectangle bounds, boolean focused) { comboBox.hostPaintValue(g, bounds); }
    public void paintArrow(Graphics g, int x, int y, int width, int height) {
        if (arrowButton == null) return;
        arrowButton.setEnabled(comboBox.isEnabled());
        arrowButton.setSize(width, height);
        Graphics child = g.create(x, y, width, height);
        try { arrowButton.paint(child); } finally { child.dispose(); }
    }
}
