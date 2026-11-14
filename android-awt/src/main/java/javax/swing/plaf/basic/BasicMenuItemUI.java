/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing.plaf.basic;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.MenuDragMouseEvent;
import javax.swing.event.MenuDragMouseListener;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.ComponentInputMapUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.UIResource;

/**
 * BasicMenuItem implementation
 *
 * <p>Android port: the item keeps its host-painted look ({@link #paint} and
 * {@link #getPreferredMenuItemSize} hand back to the component's default painting and
 * measuring); the 8u mouse, menu-drag, accelerator and selection-path behaviour is kept.
 *
 * @author Georges Saab
 * @author David Karlton
 * @author Arnaud Weber
 * @author Fredrik Lagerblad
 */
public class BasicMenuItemUI extends MenuItemUI {
    protected JMenuItem menuItem = null;
    protected Color selectionBackground;
    protected Color selectionForeground;
    protected Color disabledForeground;
    protected Color acceleratorForeground;
    protected Color acceleratorSelectionForeground;

    /**
     * Accelerator delimiter string, such as {@code '+'} in {@code 'Ctrl+C'}.
     * @since 1.7
     */
    protected String acceleratorDelimiter;

    protected int defaultTextIconGap;
    protected Font acceleratorFont;

    protected MouseInputListener mouseInputListener;
    protected MenuDragMouseListener menuDragMouseListener;
    protected MenuKeyListener menuKeyListener;
    /**
     * <code>PropertyChangeListener</code> returned from
     * <code>createPropertyChangeListener</code>. You should not
     * need to access this field, rather if you want to customize the
     * <code>PropertyChangeListener</code> override
     * <code>createPropertyChangeListener</code>.
     *
     * @since 1.6
     * @see #createPropertyChangeListener
     */
    protected PropertyChangeListener propertyChangeListener;
    // BasicMenuUI also uses this.
    Handler handler;

    protected Icon arrowIcon = null;
    protected Icon checkIcon = null;

    protected boolean oldBorderPainted;

    /** Used for accelerator binding, lazily created. */
    InputMap windowInputMap;

    private static final String DO_CLICK = "doClick";

    public static ComponentUI createUI(JComponent c) {
        return new BasicMenuItemUI();
    }

    public void installUI(JComponent c) {
        menuItem = (JMenuItem) c;

        installDefaults();
        installComponents(menuItem);
        installListeners();
        installKeyboardActions();
    }

    protected void installDefaults() {
        String prefix = getPropertyPrefix();

        acceleratorFont = UIManager.getFont("MenuItem.acceleratorFont");
        // use default if missing so that BasicMenuItemUI can be used in other
        // LAFs like Nimbus
        if (acceleratorFont == null) {
            acceleratorFont = UIManager.getFont("MenuItem.font");
        }

        Object opaque = UIManager.get(getPropertyPrefix() + ".opaque");
        if (opaque != null) {
            LookAndFeel.installProperty(menuItem, "opaque", opaque);
        } else {
            LookAndFeel.installProperty(menuItem, "opaque", Boolean.TRUE);
        }
        if (menuItem.getMargin() == null
                || (menuItem.getMargin() instanceof UIResource)) {
            menuItem.setMargin(UIManager.getInsets(prefix + ".margin"));
        }

        LookAndFeel.installProperty(menuItem, "iconTextGap", Integer.valueOf(4));
        defaultTextIconGap = menuItem.getIconTextGap();

        LookAndFeel.installBorder(menuItem, prefix + ".border");
        oldBorderPainted = menuItem.isBorderPainted();
        LookAndFeel.installProperty(menuItem, "borderPainted",
                UIManager.getBoolean(prefix + ".borderPainted"));
        LookAndFeel.installColorsAndFont(menuItem,
                prefix + ".background",
                prefix + ".foreground",
                prefix + ".font");

        // MenuItem specific defaults
        if (selectionBackground == null
                || selectionBackground instanceof UIResource) {
            selectionBackground =
                UIManager.getColor(prefix + ".selectionBackground");
        }
        if (selectionForeground == null
                || selectionForeground instanceof UIResource) {
            selectionForeground =
                UIManager.getColor(prefix + ".selectionForeground");
        }
        if (disabledForeground == null
                || disabledForeground instanceof UIResource) {
            disabledForeground =
                UIManager.getColor(prefix + ".disabledForeground");
        }
        if (acceleratorForeground == null
                || acceleratorForeground instanceof UIResource) {
            acceleratorForeground =
                UIManager.getColor(prefix + ".acceleratorForeground");
        }
        if (acceleratorSelectionForeground == null
                || acceleratorSelectionForeground instanceof UIResource) {
            acceleratorSelectionForeground =
                UIManager.getColor(prefix + ".acceleratorSelectionForeground");
        }
        // Get accelerator delimiter
        acceleratorDelimiter =
            UIManager.getString("MenuItem.acceleratorDelimiter");
        if (acceleratorDelimiter == null) {
            acceleratorDelimiter = "+";
        }
        // Icons
        if (arrowIcon == null
                || arrowIcon instanceof UIResource) {
            arrowIcon = UIManager.getIcon(prefix + ".arrowIcon");
        }
        updateCheckIcon();
    }

    /**
     * Updates check Icon based on column layout
     */
    private void updateCheckIcon() {
        String prefix = getPropertyPrefix();

        if (checkIcon == null
                || checkIcon instanceof UIResource) {
            checkIcon = UIManager.getIcon(prefix + ".checkIcon");
        }
    }

    /**
     * @since 1.3
     */
    protected void installComponents(JMenuItem menuItem) {
    }

    protected String getPropertyPrefix() {
        return "MenuItem";
    }

    protected void installListeners() {
        if ((mouseInputListener = createMouseInputListener(menuItem)) != null) {
            menuItem.addMouseListener(mouseInputListener);
            menuItem.addMouseMotionListener(mouseInputListener);
        }
        if ((menuDragMouseListener = createMenuDragMouseListener(menuItem)) != null) {
            menuItem.addMenuDragMouseListener(menuDragMouseListener);
        }
        if ((menuKeyListener = createMenuKeyListener(menuItem)) != null) {
            menuItem.addMenuKeyListener(menuKeyListener);
        }
        if ((propertyChangeListener = createPropertyChangeListener(menuItem)) != null) {
            menuItem.addPropertyChangeListener(propertyChangeListener);
        }
    }

    protected void installKeyboardActions() {
        ActionMap map = new ActionMapUIResource();
        map.put(DO_CLICK, new Actions(DO_CLICK));
        SwingUtilities.replaceUIActionMap(menuItem, map);
        updateAcceleratorBinding();
    }

    public void uninstallUI(JComponent c) {
        menuItem = (JMenuItem) c;
        uninstallDefaults();
        uninstallComponents(menuItem);
        uninstallListeners();
        uninstallKeyboardActions();
        // Remove the textWidth and accWidth values from the parent's Client Properties.
        java.awt.Container parent = menuItem.getParent();
        if ((parent != null && parent instanceof JComponent)
                && !(menuItem instanceof JMenu && ((JMenu) menuItem).isTopLevelMenu())) {
            JComponent p = (JComponent) parent;
            p.putClientProperty(BasicMenuItemUI.MAX_TEXT_WIDTH, null);
            p.putClientProperty(BasicMenuItemUI.MAX_ACC_WIDTH, null);
        }
        menuItem = null;
    }

    // 8u keeps these keys in sun.swing.MenuItemLayoutHelper; the popup caches column widths there.
    private static final Object MAX_TEXT_WIDTH = "maxTextWidth";
    private static final Object MAX_ACC_WIDTH = "maxAccWidth";

    protected void uninstallDefaults() {
        LookAndFeel.uninstallBorder(menuItem);
        LookAndFeel.installProperty(menuItem, "borderPainted", oldBorderPainted);
        if (menuItem.getMargin() instanceof UIResource) {
            menuItem.setMargin(null);
        }
        if (arrowIcon instanceof UIResource) {
            arrowIcon = null;
        }
        if (checkIcon instanceof UIResource) {
            checkIcon = null;
        }
    }

    /**
     * @since 1.3
     */
    protected void uninstallComponents(JMenuItem menuItem) {
    }

    protected void uninstallListeners() {
        if (mouseInputListener != null) {
            menuItem.removeMouseListener(mouseInputListener);
            menuItem.removeMouseMotionListener(mouseInputListener);
        }
        if (menuDragMouseListener != null) {
            menuItem.removeMenuDragMouseListener(menuDragMouseListener);
        }
        if (menuKeyListener != null) {
            menuItem.removeMenuKeyListener(menuKeyListener);
        }
        if (propertyChangeListener != null) {
            menuItem.removePropertyChangeListener(propertyChangeListener);
        }

        mouseInputListener = null;
        menuDragMouseListener = null;
        menuKeyListener = null;
        propertyChangeListener = null;
        handler = null;
    }

    protected void uninstallKeyboardActions() {
        SwingUtilities.replaceUIActionMap(menuItem, null);
        SwingUtilities.replaceUIInputMap(menuItem, JComponent.
                                         WHEN_IN_FOCUSED_WINDOW, null);
    }

    protected MouseInputListener createMouseInputListener(JComponent c) {
        return getHandler();
    }

    protected MenuDragMouseListener createMenuDragMouseListener(JComponent c) {
        return getHandler();
    }

    protected MenuKeyListener createMenuKeyListener(JComponent c) {
        return null;
    }

    /**
     * Creates a <code>PropertyChangeListener</code> which will be added to
     * the menu item.
     * If this method returns null then it will not be added to the menu item.
     *
     * @return an instance of a <code>PropertyChangeListener</code> or null
     * @since 1.6
     */
    protected PropertyChangeListener
                                  createPropertyChangeListener(JComponent c) {
        return getHandler();
    }

    Handler getHandler() {
        if (handler == null) {
            handler = new Handler();
        }
        return handler;
    }

    InputMap createInputMap(int condition) {
        if (condition == JComponent.WHEN_IN_FOCUSED_WINDOW) {
            return new ComponentInputMapUIResource(menuItem);
        }
        return null;
    }

    void updateAcceleratorBinding() {
        KeyStroke accelerator = menuItem.getAccelerator();
        InputMap windowInputMap = SwingUtilities.getUIInputMap(
                menuItem, JComponent.WHEN_IN_FOCUSED_WINDOW);

        if (windowInputMap != null) {
            windowInputMap.clear();
        }
        if (accelerator != null) {
            if (windowInputMap == null) {
                windowInputMap = createInputMap(JComponent.
                                                WHEN_IN_FOCUSED_WINDOW);
                SwingUtilities.replaceUIInputMap(menuItem,
                           JComponent.WHEN_IN_FOCUSED_WINDOW, windowInputMap);
            }
            windowInputMap.put(accelerator, DO_CLICK);
        }
    }

    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    public Dimension getPreferredSize(JComponent c) {
        return getPreferredMenuItemSize(c,
                                        checkIcon,
                                        arrowIcon,
                                        defaultTextIconGap);
    }

    public Dimension getMaximumSize(JComponent c) {
        return getPreferredSize(c);
    }

    /** The host lays out icon, text, accelerator and arrow columns for the item. */
    protected Dimension getPreferredMenuItemSize(JComponent c,
                                                 Icon checkIcon,
                                                 Icon arrowIcon,
                                                 int defaultTextIconGap) {
        return c.hostDefaultPreferredSize();
    }

    /**
     * We draw the background in paintMenuItem()
     * so override update (which fills the background of opaque
     * components by default) to just call paint().
     *
     */
    public void update(Graphics g, JComponent c) {
        paint(g, c);
    }

    public void paint(Graphics g, JComponent c) {
        paintMenuItem(g, c, checkIcon, arrowIcon,
                      selectionBackground, selectionForeground,
                      defaultTextIconGap);
    }

    /** The host paints the item, its check and arrow icons and its accelerator. */
    protected void paintMenuItem(Graphics g, JComponent c,
                                     Icon checkIcon, Icon arrowIcon,
                                     Color background, Color foreground,
                                     int defaultTextIconGap) {
        c.hostPaintDefaultComponent(g);
    }

    /**
     * Draws the background of the menu item.
     *
     * @param g the paint graphics
     * @param menuItem menu item to be painted
     * @param bgColor selection background color
     * @since 1.4
     */
    protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
        ButtonModel model = menuItem.getModel();
        Color oldColor = g.getColor();
        int menuWidth = menuItem.getWidth();
        int menuHeight = menuItem.getHeight();

        if (menuItem.isOpaque()) {
            if (model.isArmed() || (menuItem instanceof JMenu && model.isSelected())) {
                g.setColor(bgColor);
                g.fillRect(0, 0, menuWidth, menuHeight);
            } else {
                g.setColor(menuItem.getBackground());
                g.fillRect(0, 0, menuWidth, menuHeight);
            }
            g.setColor(oldColor);
        } else if (model.isArmed() || (menuItem instanceof JMenu
                                       && model.isSelected())) {
            g.setColor(bgColor);
            g.fillRect(0, 0, menuWidth, menuHeight);
            g.setColor(oldColor);
        }
    }

    /**
     * Renders the text of the current menu item.
     * <p>
     * @param g graphics context
     * @param menuItem menu item to render
     * @param textRect bounding rectangle for rendering the text
     * @param text string to render
     * @since 1.4
     */
    protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
        ButtonModel model = menuItem.getModel();
        FontMetrics fm = g.getFontMetrics(menuItem.getFont());
        int mnemIndex = menuItem.getDisplayedMnemonicIndex();

        if (!model.isEnabled()) {
            // *** paint the text disabled
            if (UIManager.get("MenuItem.disabledForeground") instanceof Color) {
                g.setColor(UIManager.getColor("MenuItem.disabledForeground"));
                drawStringUnderlineCharAt(g, fm, text, mnemIndex,
                        textRect.x, textRect.y + fm.getAscent());
            } else {
                g.setColor(menuItem.getBackground().brighter());
                drawStringUnderlineCharAt(g, fm, text, mnemIndex,
                        textRect.x, textRect.y + fm.getAscent());
                g.setColor(menuItem.getBackground().darker());
                drawStringUnderlineCharAt(g, fm, text, mnemIndex,
                        textRect.x - 1, textRect.y + fm.getAscent() - 1);
            }
        } else {
            // *** paint the text normally
            if (model.isArmed() || (menuItem instanceof JMenu && model.isSelected())) {
                g.setColor(selectionForeground); // Uses protected field.
            }
            drawStringUnderlineCharAt(g, fm, text, mnemIndex,
                    textRect.x, textRect.y + fm.getAscent());
        }
    }

    /** SwingUtilities2.drawStringUnderlineCharAt: the string, then a one-pixel underline. */
    static void drawStringUnderlineCharAt(Graphics g, FontMetrics fm, String text,
                                          int underlinedIndex, int x, int y) {
        if (text == null || text.length() <= 0) {
            return;
        }
        g.drawString(text, x, y);
        if (underlinedIndex >= 0 && underlinedIndex < text.length()) {
            int underlineRectX = x + fm.stringWidth(text.substring(0, underlinedIndex));
            int underlineRectWidth = fm.charWidth(text.charAt(underlinedIndex));
            g.fillRect(underlineRectX, y + 1, underlineRectWidth, 1);
        }
    }

    public MenuElement[] getPath() {
        MenuSelectionManager m = MenuSelectionManager.defaultManager();
        MenuElement[] oldPath = m.getSelectedPath();
        MenuElement[] newPath;
        int i = oldPath.length;
        if (i == 0) {
            return new MenuElement[0];
        }
        Component parent = menuItem.getParent();
        if (oldPath[i - 1].getComponent() == parent) {
            // The parent popup menu is the last so far
            newPath = new MenuElement[i + 1];
            System.arraycopy(oldPath, 0, newPath, 0, i);
            newPath[i] = menuItem;
        } else {
            // A sibling menuitem is the current selection
            //
            //  This probably needs to handle 'exit submenu into
            // a menu item.  Search backwards along the current
            // selection until you find the parent popup menu,
            // then copy up to that and add yourself...
            int j;
            for (j = oldPath.length - 1; j >= 0; j--) {
                if (oldPath[j].getComponent() == parent) {
                    break;
                }
            }
            newPath = new MenuElement[j + 2];
            System.arraycopy(oldPath, 0, newPath, 0, j + 1);
            newPath[j + 1] = menuItem;
        }
        return newPath;
    }

    /**
     * Call this method when a menu item is to be activated.
     * This method handles some of the details of menu item activation
     * such as clearing the selected path and messaging the
     * JMenuItem's doClick() method.
     *
     * @param msm  A MenuSelectionManager. The visual feedback and
     *             internal bookkeeping tasks are delegated to
     *             this MenuSelectionManager. If <code>null</code> is
     *             passed as this argument, the
     *             <code>MenuSelectionManager.defaultManager</code> is
     *             used.
     * @see MenuSelectionManager
     * @see JMenuItem#doClick(int)
     * @since 1.4
     */
    protected void doClick(MenuSelectionManager msm) {
        // Visual feedback
        if (msm == null) {
            msm = MenuSelectionManager.defaultManager();
        }
        msm.clearSelectedPath();
        menuItem.doClick(0);
    }

    protected class MouseInputHandler implements MouseInputListener {
        // NOTE: This class exists only for backward compatibility. All
        // its functionality has been moved into Handler. If you need to add
        // new functionality add it to the Handler, but make sure this
        // class calls into the Handler.

        public void mouseClicked(MouseEvent e) {
            getHandler().mouseClicked(e);
        }
        public void mousePressed(MouseEvent e) {
            getHandler().mousePressed(e);
        }
        public void mouseReleased(MouseEvent e) {
            getHandler().mouseReleased(e);
        }
        public void mouseEntered(MouseEvent e) {
            getHandler().mouseEntered(e);
        }
        public void mouseExited(MouseEvent e) {
            getHandler().mouseExited(e);
        }
        public void mouseDragged(MouseEvent e) {
            getHandler().mouseDragged(e);
        }
        public void mouseMoved(MouseEvent e) {
            getHandler().mouseMoved(e);
        }
    }

    private static class Actions extends AbstractAction {
        Actions(String key) {
            super(key);
        }

        public void actionPerformed(ActionEvent e) {
            JMenuItem mi = (JMenuItem) e.getSource();
            MenuSelectionManager.defaultManager().clearSelectedPath();
            mi.doClick();
        }
    }

    class Handler implements MenuDragMouseListener,
                             MouseInputListener, PropertyChangeListener {
        //
        // MouseInputListener
        //
        public void mouseClicked(MouseEvent e) {}
        public void mousePressed(MouseEvent e) {
        }
        public void mouseReleased(MouseEvent e) {
            if (!menuItem.isEnabled()) {
                return;
            }
            MenuSelectionManager manager =
                MenuSelectionManager.defaultManager();
            Point p = e.getPoint();
            if (p.x >= 0 && p.x < menuItem.getWidth()
                    && p.y >= 0 && p.y < menuItem.getHeight()) {
                doClick(manager);
            } else {
                manager.processMouseEvent(e);
            }
        }
        public void mouseEntered(MouseEvent e) {
            MenuSelectionManager manager = MenuSelectionManager.defaultManager();
            int modifiers = e.getModifiers();
            // 4188027: drag enter/exit added in JDK 1.1.7A, JDK1.2
            if ((modifiers & (InputEvent.BUTTON1_MASK
                    | InputEvent.BUTTON2_MASK | InputEvent.BUTTON3_MASK)) != 0) {
                MenuSelectionManager.defaultManager().processMouseEvent(e);
            } else {
                manager.setSelectedPath(getPath());
            }
        }
        public void mouseExited(MouseEvent e) {
            MenuSelectionManager manager = MenuSelectionManager.defaultManager();

            int modifiers = e.getModifiers();
            // 4188027: drag enter/exit added in JDK 1.1.7A, JDK1.2
            if ((modifiers & (InputEvent.BUTTON1_MASK
                    | InputEvent.BUTTON2_MASK | InputEvent.BUTTON3_MASK)) != 0) {
                MenuSelectionManager.defaultManager().processMouseEvent(e);
            } else {
                MenuElement[] path = manager.getSelectedPath();
                if (path.length > 1 && path[path.length - 1] == menuItem) {
                    MenuElement[] newPath = new MenuElement[path.length - 1];
                    int i, c;
                    for (i = 0, c = path.length - 1; i < c; i++) {
                        newPath[i] = path[i];
                    }
                    manager.setSelectedPath(newPath);
                }
            }
        }

        public void mouseDragged(MouseEvent e) {
            MenuSelectionManager.defaultManager().processMouseEvent(e);
        }
        public void mouseMoved(MouseEvent e) {
        }

        //
        // MenuDragListener
        //
        public void menuDragMouseEntered(MenuDragMouseEvent e) {
            MenuSelectionManager manager = e.getMenuSelectionManager();
            MenuElement[] path = e.getPath();
            manager.setSelectedPath(path);
        }
        public void menuDragMouseDragged(MenuDragMouseEvent e) {
            MenuSelectionManager manager = e.getMenuSelectionManager();
            MenuElement[] path = e.getPath();
            manager.setSelectedPath(path);
        }
        public void menuDragMouseExited(MenuDragMouseEvent e) {}
        public void menuDragMouseReleased(MenuDragMouseEvent e) {
            if (!menuItem.isEnabled()) {
                return;
            }
            MenuSelectionManager manager = e.getMenuSelectionManager();
            Point p = e.getPoint();
            if (p.x >= 0 && p.x < menuItem.getWidth()
                    && p.y >= 0 && p.y < menuItem.getHeight()) {
                doClick(manager);
            } else {
                manager.clearSelectedPath();
            }
        }


        //
        // PropertyChangeListener
        //
        public void propertyChange(PropertyChangeEvent e) {
            String name = e.getPropertyName();

            if (name == "labelFor" || name == "displayedMnemonic"
                    || name == "accelerator") {
                updateAcceleratorBinding();
            } else if (name == "iconTextGap") {
                defaultTextIconGap = ((Number) e.getNewValue()).intValue();
            }
        }
    }
}
