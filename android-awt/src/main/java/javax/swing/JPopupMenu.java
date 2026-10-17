package javax.swing;

import java.awt.Component;
import javax.swing.event.PopupMenuListener;

public class JPopupMenu extends JComponent {
    private static final long serialVersionUID = 1L;

    public static class Separator extends JSeparator {
        private static final long serialVersionUID = 1L;
        public Separator() { super(SwingConstants.HORIZONTAL); }
    }

    private Component invoker;
    private final java.util.List<PopupMenuListener> popupListeners = new java.util.ArrayList<>();
    private static final ThreadLocal<JPopupMenu> shownPopup = new ThreadLocal<>();

    /** Android panel host consumes menus opened by plugin mouse listeners. */
    public static JPopupMenu takeShownPopupMenu() {
        JPopupMenu menu = shownPopup.get();
        shownPopup.remove();
        return menu;
    }

    public JPopupMenu() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(new java.awt.Color(0x3A3D43));
        setVisible(false);
    }
    public JPopupMenu(String label) { this(); }

    public JMenuItem add(JMenuItem item) { if (item != null) super.add(item); return item; }
    public JMenuItem add(String s) { JMenuItem it = new JMenuItem(s); super.add(it); return it; }
    public JMenuItem add(Action a) { JMenuItem it = new JMenuItem(a); super.add(it); return it; }
    public void addSeparator() { super.add(new Separator()); }
    public void insert(Action a, int index) { super.add(new JMenuItem(a), index); }
    public void insert(Component c, int index) { super.add(c, index); }
    @Override public void removeAll() { super.removeAll(); }
    public void show(Component invoker, int x, int y) {
        this.invoker = invoker;
        setVisible(true);
        shownPopup.set(this);
        // Without a real Swing root pane to mount onto, positioning here is best-effort.
        java.awt.Dimension d = getPreferredSize();
        setBounds(x, y, Math.max(80, d.width), Math.max(20, d.height));
        doLayout();
        repaint();
    }
    public Component getInvoker() { return invoker; }
    public void setInvoker(Component invoker) { this.invoker = invoker; }
    public void setLabel(String label) {}
    public String getLabel() { return null; }
    public void addPopupMenuListener(PopupMenuListener l) { if (l != null) popupListeners.add(l); }
    public void removePopupMenuListener(PopupMenuListener l) { popupListeners.remove(l); }
    @Override public void setVisible(boolean visible) {
        if (visible == isVisible()) return;
        javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(this);
        for (PopupMenuListener listener : new java.util.ArrayList<>(popupListeners)) {
            if (visible) listener.popupMenuWillBecomeVisible(event);
            else listener.popupMenuWillBecomeInvisible(event);
        }
        super.setVisible(visible);
    }
    public boolean isBorderPainted() { return false; }
    public void setBorderPainted(boolean b) {}
    public boolean isLightWeightPopupEnabled() { return true; }
    public void setLightWeightPopupEnabled(boolean enabled) {}

    private static boolean defaultLightWeightPopupEnabled = true;
    public static void setDefaultLightWeightPopupEnabled(boolean aFlag) { defaultLightWeightPopupEnabled = aFlag; }
    public static boolean getDefaultLightWeightPopupEnabled() { return defaultLightWeightPopupEnabled; }

    // The menu's sub-elements are its MenuElement children (JMenuItem/JMenu); plain
    // Components (e.g. Separator) are excluded, matching real Swing. quest-helper uses
    // getSubElements().length to decide whether a right-click menu has any entries.
    public MenuElement[] getSubElements() {
        java.util.List<MenuElement> items = new java.util.ArrayList<>();
        for (java.awt.Component c : getComponents()) {
            if (c instanceof MenuElement) items.add((MenuElement) c);
        }
        return items.toArray(new MenuElement[0]);
    }
}
