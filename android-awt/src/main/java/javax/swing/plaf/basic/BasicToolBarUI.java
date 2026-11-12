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
package javax.swing.plaf.basic;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Hashtable;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.ToolBarUI;
import javax.swing.plaf.UIResource;

/**
 * A Basic L&amp;F implementation of ToolBarUI, with the JDK 8 protected API so
 * look-and-feel subclasses (FlatLaf's {@code FlatToolBarUI}) link and run:
 * defaults, rollover and non-rollover button borders, focus navigation along
 * the bar, separator re-orientation and the docking/floating factories.
 *
 * <p>Floating is off: Android has no top-level windows to tear a tool bar off
 * into, so dragging the bar never undocks it, {@link #setFloating} keeps it
 * docked and {@link #isFloating} stays {@code false}. The factories
 * ({@link #createFloatingWindow}, {@link #createDragWindow}) still build their
 * objects for subclasses that call them.
 *
 * @author Georges Saab
 * @author Jeff Shapiro
 */
public class BasicToolBarUI extends ToolBarUI implements SwingConstants {
    protected JToolBar toolBar;
    private boolean floating;
    private int floatingX;
    private int floatingY;
    private RootPaneContainer floatingToolBar;
    protected DragWindow dragWindow;
    private Container dockingSource;
    private int dockingSensitivity = 0;
    protected int focusedCompIndex = -1;

    protected Color dockingColor = null;
    protected Color floatingColor = null;
    protected Color dockingBorderColor = null;
    protected Color floatingBorderColor = null;

    protected MouseInputListener dockingListener;
    protected PropertyChangeListener propertyListener;

    protected ContainerListener toolBarContListener;
    protected FocusListener toolBarFocusListener;

    protected String constraintBeforeFloating = BorderLayout.NORTH;

    private static final String IS_ROLLOVER = "JToolBar.isRollover";
    private static final String FOCUSED_COMP_INDEX = "JToolBar.focusedCompIndex";

    // Per instance, where 8u keeps them static: a static cache would leak one
    // look and feel's borders into the next after a UIManager.setLookAndFeel.
    private Border rolloverBorder;
    private Border nonRolloverBorder;
    private Border nonRolloverToggleBorder;
    private boolean rolloverBorders = false;

    private final HashMap<AbstractButton, Border> borderTable = new HashMap<AbstractButton, Border>();
    private final Hashtable<AbstractButton, Boolean> rolloverTable = new Hashtable<AbstractButton, Boolean>();

    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used. Key bindings are now defined by the LookAndFeel.
     * @deprecated As of Java 2 platform v1.3.
     */
    @Deprecated
    protected KeyStroke upKey;
    /** @deprecated As of Java 2 platform v1.3. */
    @Deprecated
    protected KeyStroke downKey;
    /** @deprecated As of Java 2 platform v1.3. */
    @Deprecated
    protected KeyStroke leftKey;
    /** @deprecated As of Java 2 platform v1.3. */
    @Deprecated
    protected KeyStroke rightKey;

    public static ComponentUI createUI(JComponent c) {
        return new BasicToolBarUI();
    }

    public void installUI(JComponent c) {
        toolBar = (JToolBar) c;

        installDefaults();
        installComponents();
        installListeners();
        installKeyboardActions();

        dockingSensitivity = 0;
        floating = false;
        floatingX = floatingY = 0;
        floatingToolBar = null;

        setOrientation(toolBar.getOrientation());
        LookAndFeel.installProperty(c, "opaque", Boolean.TRUE);

        Object index = c.getClientProperty(FOCUSED_COMP_INDEX);
        if (index instanceof Integer) {
            focusedCompIndex = ((Integer) index).intValue();
        }
    }

    public void uninstallUI(JComponent c) {
        uninstallDefaults();
        uninstallComponents();
        uninstallListeners();
        uninstallKeyboardActions();

        if (isFloating()) {
            setFloating(false, null);
        }

        floatingToolBar = null;
        dragWindow = null;
        dockingSource = null;

        c.putClientProperty(FOCUSED_COMP_INDEX, Integer.valueOf(focusedCompIndex));
    }

    protected void installDefaults() {
        LookAndFeel.installBorder(toolBar, "ToolBar.border");
        LookAndFeel.installColorsAndFont(toolBar, "ToolBar.background",
                "ToolBar.foreground", "ToolBar.font");
        if (dockingColor == null || dockingColor instanceof UIResource) {
            dockingColor = UIManager.getColor("ToolBar.dockingBackground");
        }
        if (floatingColor == null || floatingColor instanceof UIResource) {
            floatingColor = UIManager.getColor("ToolBar.floatingBackground");
        }
        if (dockingBorderColor == null || dockingBorderColor instanceof UIResource) {
            dockingBorderColor = UIManager.getColor("ToolBar.dockingForeground");
        }
        if (floatingBorderColor == null || floatingBorderColor instanceof UIResource) {
            floatingBorderColor = UIManager.getColor("ToolBar.floatingForeground");
        }

        Object rolloverProp = toolBar.getClientProperty(IS_ROLLOVER);
        if (rolloverProp == null) {
            rolloverProp = UIManager.get("ToolBar.isRollover");
        }
        if (rolloverProp instanceof Boolean) {
            rolloverBorders = ((Boolean) rolloverProp).booleanValue();
        }

        if (rolloverBorder == null) {
            rolloverBorder = createRolloverBorder();
        }
        if (nonRolloverBorder == null) {
            nonRolloverBorder = createNonRolloverBorder();
        }
        if (nonRolloverToggleBorder == null) {
            nonRolloverToggleBorder = createNonRolloverToggleBorder();
        }

        setRolloverBorders(isRolloverBorders());
    }

    protected void uninstallDefaults() {
        LookAndFeel.uninstallBorder(toolBar);
        dockingColor = null;
        floatingColor = null;
        dockingBorderColor = null;
        floatingBorderColor = null;

        installNormalBorders(toolBar);

        rolloverBorder = null;
        nonRolloverBorder = null;
        nonRolloverToggleBorder = null;
    }

    protected void installComponents() {
    }

    protected void uninstallComponents() {
    }

    protected void installListeners() {
        dockingListener = createDockingListener();
        if (dockingListener != null) {
            toolBar.addMouseMotionListener(dockingListener);
            toolBar.addMouseListener(dockingListener);
        }

        propertyListener = createPropertyListener();
        if (propertyListener != null) {
            toolBar.addPropertyChangeListener(propertyListener);
        }

        toolBarContListener = createToolBarContListener();
        if (toolBarContListener != null) {
            toolBar.addContainerListener(toolBarContListener);
        }

        toolBarFocusListener = createToolBarFocusListener();
        if (toolBarFocusListener != null) {
            Component[] components = toolBar.getComponents();
            for (Component component : components) {
                component.addFocusListener(toolBarFocusListener);
            }
        }
    }

    protected void uninstallListeners() {
        if (dockingListener != null) {
            toolBar.removeMouseMotionListener(dockingListener);
            toolBar.removeMouseListener(dockingListener);
            dockingListener = null;
        }

        if (propertyListener != null) {
            toolBar.removePropertyChangeListener(propertyListener);
            propertyListener = null;
        }

        if (toolBarContListener != null) {
            toolBar.removeContainerListener(toolBarContListener);
            toolBarContListener = null;
        }

        if (toolBarFocusListener != null) {
            Component[] components = toolBar.getComponents();
            for (Component component : components) {
                component.removeFocusListener(toolBarFocusListener);
            }
            toolBarFocusListener = null;
        }
    }

    protected void installKeyboardActions() {
        Object map = UIManager.get("ToolBar.ancestorInputMap");
        if (map instanceof InputMap) {
            SwingUtilities.replaceUIInputMap(toolBar,
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, (InputMap) map);
        }
        ActionMap actions = new ActionMapUIResource();
        actions.put("navigateRight", new NavigateAction(EAST));
        actions.put("navigateLeft", new NavigateAction(WEST));
        actions.put("navigateUp", new NavigateAction(NORTH));
        actions.put("navigateDown", new NavigateAction(SOUTH));
        SwingUtilities.replaceUIActionMap(toolBar, actions);
    }

    protected void uninstallKeyboardActions() {
        SwingUtilities.replaceUIActionMap(toolBar, null);
        SwingUtilities.replaceUIInputMap(toolBar,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
    }

    protected void navigateFocusedComp(int direction) {
        int nComp = toolBar.getComponentCount();
        int j;

        switch (direction) {
        case EAST:
        case SOUTH:
            if (focusedCompIndex < 0 || focusedCompIndex >= nComp) {
                break;
            }
            j = focusedCompIndex + 1;
            while (j != focusedCompIndex) {
                if (j >= nComp) {
                    j = 0;
                }
                Component comp = toolBar.getComponentAtIndex(j++);
                if (comp != null && comp.isFocusTraversable() && comp.isEnabled()) {
                    comp.requestFocus();
                    break;
                }
            }
            break;

        case WEST:
        case NORTH:
            if (focusedCompIndex < 0 || focusedCompIndex >= nComp) {
                break;
            }
            j = focusedCompIndex - 1;
            while (j != focusedCompIndex) {
                if (j < 0) {
                    j = nComp - 1;
                }
                Component comp = toolBar.getComponentAtIndex(j--);
                if (comp != null && comp.isFocusTraversable() && comp.isEnabled()) {
                    comp.requestFocus();
                    break;
                }
            }
            break;

        default:
            break;
        }
    }

    /**
     * Creates a rollover border for toolbar components: "ToolBar.rolloverBorder"
     * when the look and feel defines one. The fallback is the button's margin
     * plus a 3-pixel inset (8u's RolloverMarginBorder); 8u's painted
     * RolloverButtonBorder half is not ported with BasicBorders.
     *
     * @return a rollover border for toolbar components
     * @since 1.4
     */
    protected Border createRolloverBorder() {
        Object border = UIManager.get("ToolBar.rolloverBorder");
        if (border instanceof Border) {
            return (Border) border;
        }
        return new MarginUIResourceBorder();
    }

    /**
     * Creates the non rollover border for toolbar components:
     * "ToolBar.nonrolloverBorder" when the look and feel defines one, else the
     * margin border {@link #createRolloverBorder} falls back to.
     *
     * @return the non rollover border for toolbar components
     * @since 1.4
     */
    protected Border createNonRolloverBorder() {
        Object border = UIManager.get("ToolBar.nonrolloverBorder");
        if (border instanceof Border) {
            return (Border) border;
        }
        return new MarginUIResourceBorder();
    }

    private Border createNonRolloverToggleBorder() {
        Object border = UIManager.get("ToolBar.nonrolloverToggleBorder");
        if (border instanceof Border) {
            return (Border) border;
        }
        return new MarginUIResourceBorder();
    }

    /**
     * No longer used, use BasicToolBarUI.createFloatingWindow(JToolBar)
     *
     * @param toolbar the tool bar the frame would float
     * @return an undecorated, non-resizable frame titled with the bar's name
     * @see #createFloatingWindow
     */
    protected JFrame createFloatingFrame(JToolBar toolbar) {
        JFrame frame = new JFrame(toolbar.getName());
        frame.getRootPane().setName("ToolBar.FloatingFrame");
        frame.setResizable(false);
        frame.addWindowListener(createFrameListener());
        return frame;
    }

    /**
     * Creates a window which contains the toolbar after it has been dragged out
     * from its container. The shadow never shows it (floating is off).
     *
     * @param toolbar the tool bar the window would float
     * @return a <code>RootPaneContainer</code> object, containing the toolbar
     * @since 1.4
     */
    protected RootPaneContainer createFloatingWindow(JToolBar toolbar) {
        Window window = SwingUtilities.getWindowAncestor(toolbar);
        JDialog dialog;
        if (window instanceof Frame) {
            dialog = new JDialog((Frame) window, toolbar.getName(), false);
        } else if (window instanceof Dialog) {
            dialog = new JDialog((Dialog) window, toolbar.getName(), false);
        } else {
            dialog = new JDialog((Frame) null, toolbar.getName(), false);
        }
        dialog.getRootPane().setName("ToolBar.FloatingWindow");
        dialog.setTitle(toolbar.getName());
        dialog.setResizable(false);
        dialog.addWindowListener(createFrameListener());
        return dialog;
    }

    protected DragWindow createDragWindow(JToolBar toolbar) {
        Window frame = SwingUtilities.getWindowAncestor(toolbar);
        if (floatingToolBar instanceof Window) {
            frame = (Window) floatingToolBar;
        }
        return new DragWindow(frame);
    }

    /**
     * Returns a flag to determine whether rollover button borders are enabled.
     *
     * @return true if rollover borders are enabled; false otherwise
     * @since 1.4
     */
    public boolean isRolloverBorders() {
        return rolloverBorders;
    }

    /**
     * Sets the flag for enabling rollover borders on the toolbar and it will
     * also install the appropriate border depending on the state of the flag.
     *
     * @param rollover if true, rollover borders are installed; otherwise
     *        non-rollover borders are installed
     * @since 1.4
     */
    public void setRolloverBorders(boolean rollover) {
        rolloverBorders = rollover;

        if (rolloverBorders) {
            installRolloverBorders(toolBar);
        } else {
            installNonRolloverBorders(toolBar);
        }
    }

    /**
     * Installs rollover borders on all the child components of the JComponent.
     *
     * @param c container whose children will have rollover borders installed
     * @since 1.4
     */
    protected void installRolloverBorders(JComponent c) {
        Component[] components = c.getComponents();
        for (Component component : components) {
            if (component instanceof JComponent) {
                ((JComponent) component).updateUI();
                setBorderToRollover(component);
            }
        }
    }

    /**
     * Installs non-rollover borders on all the child components of the
     * JComponent.
     *
     * @param c container whose children will have non-rollover borders installed
     * @since 1.4
     */
    protected void installNonRolloverBorders(JComponent c) {
        Component[] components = c.getComponents();
        for (Component component : components) {
            if (component instanceof JComponent) {
                ((JComponent) component).updateUI();
                setBorderToNonRollover(component);
            }
        }
    }

    /**
     * Installs normal borders on all the child components of the JComponent:
     * the borders the components had before the toolbar replaced them.
     *
     * @param c container whose children will have normal borders installed
     * @since 1.4
     */
    protected void installNormalBorders(JComponent c) {
        Component[] components = c.getComponents();
        for (Component component : components) {
            setBorderToNormal(component);
        }
    }

    /**
     * Sets the border of the component to have a rollover border which was
     * created by {@link #createRolloverBorder}.
     *
     * @param c component which will have a rollover border installed
     * @since 1.4
     */
    protected void setBorderToRollover(Component c) {
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;

            Border border = borderTable.get(b);
            if (border == null || border instanceof UIResource) {
                borderTable.put(b, b.getBorder());
            }

            // Only set the border if it's the default border
            if (b.getBorder() instanceof UIResource) {
                b.setBorder(getRolloverBorder(b));
            }

            rolloverTable.put(b, b.isRolloverEnabled() ? Boolean.TRUE : Boolean.FALSE);
            b.setRolloverEnabled(true);
        }
    }

    /**
     * Returns a rollover border for the button.
     *
     * @param b the button to calculate the rollover border for
     * @return the rollover border
     * @since 1.6
     */
    protected Border getRolloverBorder(AbstractButton b) {
        return rolloverBorder;
    }

    /**
     * Sets the border of the component to have a non-rollover border which was
     * created by {@link #createNonRolloverBorder}.
     *
     * @param c component which will have a non-rollover border installed
     * @since 1.4
     */
    protected void setBorderToNonRollover(Component c) {
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;

            Border border = borderTable.get(b);
            if (border == null || border instanceof UIResource) {
                borderTable.put(b, b.getBorder());
            }

            // Only set the border if it's the default border
            if (b.getBorder() instanceof UIResource) {
                b.setBorder(getNonRolloverBorder(b));
            }

            rolloverTable.put(b, b.isRolloverEnabled() ? Boolean.TRUE : Boolean.FALSE);
            b.setRolloverEnabled(false);
        }
    }

    /**
     * Returns a non-rollover border for the button.
     *
     * @param b the button to calculate the non-rollover border for
     * @return the non-rollover border
     * @since 1.6
     */
    protected Border getNonRolloverBorder(AbstractButton b) {
        if (b instanceof JToggleButton) {
            return nonRolloverToggleBorder;
        }
        return nonRolloverBorder;
    }

    /**
     * Sets the border of the component to have a normal border: the border it
     * had before a rollover or non-rollover border replaced it.
     *
     * @param c component which will have a normal border re-installed
     * @since 1.4
     */
    protected void setBorderToNormal(Component c) {
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;

            Border border = borderTable.remove(b);
            b.setBorder(border);

            Boolean value = rolloverTable.remove(b);
            if (value != null) {
                b.setRolloverEnabled(value.booleanValue());
            }
        }
    }

    public void setFloatingLocation(int x, int y) {
        floatingX = x;
        floatingY = y;
    }

    public boolean isFloating() {
        return floating;
    }

    /**
     * Deliberate no-op for {@code b == true}: Android has no top-level window to
     * float the bar in, so it stays docked where it is and {@link #isFloating}
     * stays {@code false}. Docking ({@code b == false}) is already the state.
     *
     * @param b {@code true} to float the tool bar
     * @param p the location the bar would float at
     */
    public void setFloating(boolean b, Point p) {
        floating = false;
        if (p != null) {
            setFloatingLocation(p.x, p.y);
        }
    }

    public void setOrientation(int orientation) {
        toolBar.setOrientation(orientation);

        if (dragWindow != null) {
            dragWindow.setOrientation(orientation);
        }
    }

    /**
     * Gets the color displayed when over a docking area
     *
     * @return the color displayed when over a docking area
     */
    public Color getDockingColor() {
        return dockingColor;
    }

    /**
     * Sets the color displayed when over a docking area
     *
     * @param c the new docking color
     */
    public void setDockingColor(Color c) {
        this.dockingColor = c;
    }

    /**
     * Gets the color displayed when over a floating area
     *
     * @return the color displayed when over a floating area
     */
    public Color getFloatingColor() {
        return floatingColor;
    }

    /**
     * Sets the color displayed when over a floating area
     *
     * @param c the new floating color
     */
    public void setFloatingColor(Color c) {
        this.floatingColor = c;
    }

    /**
     * Returns whether the bar could dock in {@code c} at {@code p}: within one
     * bar thickness of one of the container's edges.
     *
     * @param c the container to dock into
     * @param p a point in {@code c}'s coordinates
     * @return {@code true} if {@code p} is in a docking area of {@code c}
     */
    public boolean canDock(Component c, Point p) {
        return p != null && getDockingConstraint(c, p) != null;
    }

    private String getDockingConstraint(Component c, Point p) {
        if (p == null) {
            return constraintBeforeFloating;
        }
        if (p.x >= 0 && p.y >= 0 && p.x < c.getWidth() && p.y < c.getHeight()) {
            Dimension size = toolBar.getSize();
            dockingSensitivity = (toolBar.getOrientation() == HORIZONTAL) ? size.height : size.width;
            if (p.y < dockingSensitivity) {
                return BorderLayout.NORTH;
            }
            if (p.x >= c.getWidth() - dockingSensitivity) {
                return BorderLayout.EAST;
            }
            if (p.x < dockingSensitivity) {
                return BorderLayout.WEST;
            }
            if (p.y >= c.getHeight() - dockingSensitivity) {
                return BorderLayout.SOUTH;
            }
        }
        return null;
    }

    /**
     * Deliberate no-op: floating is off (see the class documentation), so a
     * drag shows no drag window.
     *
     * @param position the pointer position in the bar's coordinates
     * @param origin the position the drag started at
     */
    protected void dragTo(Point position, Point origin) {
    }

    /**
     * Deliberate no-op: floating is off (see the class documentation), so a
     * drag that ends away from a docking area leaves the bar docked.
     *
     * @param position the pointer position in the bar's coordinates
     * @param origin the position the drag started at
     */
    protected void floatAt(Point position, Point origin) {
    }

    protected ContainerListener createToolBarContListener() {
        return new ToolBarContListener();
    }

    protected FocusListener createToolBarFocusListener() {
        return new ToolBarFocusListener();
    }

    protected PropertyChangeListener createPropertyListener() {
        return new PropertyListener();
    }

    protected MouseInputListener createDockingListener() {
        return new DockingListener(toolBar);
    }

    protected WindowListener createFrameListener() {
        return new FrameListener();
    }

    /**
     * Paints the contents of the window used for dragging.
     *
     * @param g Graphics to paint to.
     * @throws NullPointerException is <code>g</code> is null
     * @since 1.5
     */
    protected void paintDragWindow(Graphics g) {
        g.setColor(dragWindow.getBackground());
        int w = dragWindow.getWidth();
        int h = dragWindow.getHeight();
        g.fillRect(0, 0, w, h);
        g.setColor(dragWindow.getBorderColor());
        g.drawRect(0, 0, w - 1, h - 1);
    }

    /** The button margin plus 3 pixels, the inner half of 8u's rollover border. */
    @SuppressWarnings("serial") // Same-version serialization only
    private static final class MarginUIResourceBorder extends EmptyBorder implements UIResource {
        MarginUIResourceBorder() {
            super(3, 3, 3, 3);
        }

        public Insets getBorderInsets(Component c) {
            return getBorderInsets(c, new Insets(0, 0, 0, 0));
        }

        public Insets getBorderInsets(Component c, Insets insets) {
            Insets margin = null;
            if (c instanceof AbstractButton) {
                margin = ((AbstractButton) c).getMargin();
            }
            if (margin == null || margin instanceof UIResource) {
                insets.left = left;
                insets.top = top;
                insets.right = right;
                insets.bottom = bottom;
            } else {
                insets.left = margin.left;
                insets.top = margin.top;
                insets.right = margin.right;
                insets.bottom = margin.bottom;
            }
            return insets;
        }
    }

    @SuppressWarnings("serial") // Same-version serialization only
    private final class NavigateAction extends AbstractAction {
        private final int direction;

        NavigateAction(int direction) {
            this.direction = direction;
        }

        public void actionPerformed(ActionEvent e) {
            navigateFocusedComp(direction);
        }
    }

    protected class FrameListener extends WindowAdapter {
        public void windowClosing(WindowEvent w) {
            if (toolBar.isFloatable()) {
                if (dragWindow != null) {
                    dragWindow.setVisible(false);
                }
                setFloating(false, null);
            }
        }
    }

    protected class ToolBarContListener implements ContainerListener {
        public void componentAdded(ContainerEvent e) {
            Component c = e.getChild();
            if (toolBarFocusListener != null) {
                c.addFocusListener(toolBarFocusListener);
            }
            if (isRolloverBorders()) {
                setBorderToRollover(c);
            } else {
                setBorderToNonRollover(c);
            }
        }

        public void componentRemoved(ContainerEvent e) {
            Component c = e.getChild();
            if (toolBarFocusListener != null) {
                c.removeFocusListener(toolBarFocusListener);
            }
            // Revert the button border
            setBorderToNormal(c);
        }
    }

    protected class ToolBarFocusListener implements FocusListener {
        public void focusGained(FocusEvent e) {
            Component c = e.getComponent();
            focusedCompIndex = toolBar.getComponentIndex(c);
        }

        public void focusLost(FocusEvent e) {
        }
    }

    protected class PropertyListener implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent e) {
            String propertyName = e.getPropertyName();
            if ("lookAndFeel".equals(propertyName)) {
                toolBar.updateUI();
            } else if ("orientation".equals(propertyName)) {
                // Separators run across the bar: vertical in a horizontal bar.
                Component[] components = toolBar.getComponents();
                int orientation = ((Integer) e.getNewValue()).intValue();
                for (Component component : components) {
                    if (component instanceof JToolBar.Separator) {
                        JToolBar.Separator separator = (JToolBar.Separator) component;
                        separator.setOrientation(orientation == HORIZONTAL ? VERTICAL : HORIZONTAL);
                        Dimension size = separator.getSeparatorSize();
                        if (size != null && size.width != size.height) {
                            separator.setSeparatorSize(new Dimension(size.height, size.width));
                        }
                    }
                }
            } else if (IS_ROLLOVER.equals(propertyName)) {
                installNormalBorders(toolBar);
                Object value = e.getNewValue();
                setRolloverBorders(value instanceof Boolean && ((Boolean) value).booleanValue());
            }
        }
    }

    /**
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicToolBarUI. It tracks the
     * drag gesture; with floating off a drag never undocks the bar.
     */
    public class DockingListener implements MouseInputListener {
        protected JToolBar toolBar;
        protected boolean isDragging = false;
        protected Point origin = null;

        public DockingListener(JToolBar t) {
            this.toolBar = t;
        }

        public void mouseClicked(MouseEvent e) {
        }

        public void mousePressed(MouseEvent e) {
            if (!toolBar.isEnabled()) {
                return;
            }
            isDragging = false;
        }

        public void mouseReleased(MouseEvent e) {
            if (!toolBar.isEnabled()) {
                return;
            }
            if (isDragging) {
                floatAt(e.getPoint(), origin);
            }
            origin = null;
            isDragging = false;
        }

        public void mouseDragged(MouseEvent e) {
            if (!toolBar.isEnabled() || !toolBar.isFloatable()) {
                return;
            }
            isDragging = true;
            if (origin == null) {
                origin = e.getPoint();
            }
            dragTo(e.getPoint(), origin);
        }

        public void mouseMoved(MouseEvent e) {
        }

        public void mouseEntered(MouseEvent e) {
        }

        public void mouseExited(MouseEvent e) {
        }
    }

    @SuppressWarnings("serial") // Superclass is not serializable across versions
    protected class DragWindow extends Window {
        Color borderColor = Color.gray;
        int orientation = toolBar.getOrientation();
        Point offset; // offset of the pointer in the drag window

        DragWindow(Window w) {
            super(w);
        }

        /**
         * Returns the orientation of the toolbar window when the toolbar is
         * floating.
         *
         * @return the orientation
         * @since 1.6
         */
        public int getOrientation() {
            return orientation;
        }

        public void setOrientation(int o) {
            if (isShowing()) {
                if (o == this.orientation) {
                    return;
                }
                this.orientation = o;
                Dimension size = getSize();
                setSize(new Dimension(size.height, size.width));
                if (offset != null) {
                    offset = new Point(offset.y, offset.x);
                }
                repaint();
            }
        }

        public Point getOffset() {
            return offset;
        }

        public void setOffset(Point p) {
            this.offset = p;
        }

        public void setBorderColor(Color c) {
            if (this.borderColor == c) {
                return;
            }
            this.borderColor = c;
            repaint();
        }

        public Color getBorderColor() {
            return this.borderColor;
        }

        public void paint(Graphics g) {
            paintDragWindow(g);
            // Paint the children
            super.paint(g);
        }

        public Insets getInsets() {
            return new Insets(1, 1, 1, 1);
        }
    }
}
