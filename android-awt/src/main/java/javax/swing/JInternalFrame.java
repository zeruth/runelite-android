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

package javax.swing;

import java.awt.*;
import java.beans.PropertyVetoException;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;
import javax.swing.plaf.DesktopIconUI;
import javax.swing.plaf.InternalFrameUI;

/**
 * A lightweight object that provides many of the features of
 * a native frame, including dragging, closing, becoming an icon,
 * resizing, title display, and support for a menu bar.
 *
 * <p>The shadow port keeps the 8u state machine (the closed, icon, maximum and
 * selected properties, their vetoable changes, the internal frame events and the
 * default close operation). The desktop icon and the desktop manager are not
 * ported: there is no JDesktopPane on Android, so the frame lays itself out in
 * whatever container holds it.
 *
 * @author David Kloba
 * @author Rich Schiavi
 */
@SuppressWarnings("serial") // Same-version serialization only
public class JInternalFrame extends JComponent implements
        WindowConstants,
        RootPaneContainer
{
    /**
     * The <code>JRootPane</code> instance that manages the
     * content pane
     * and optional menu bar for this internal frame, as well as the
     * glass pane.
     */
    protected JRootPane rootPane;

    /**
     * If true then calls to <code>add</code> and <code>setLayout</code>
     * will be forwarded to the <code>contentPane</code>.
     */
    protected boolean rootPaneCheckingEnabled = false;

    /** The frame can be closed. */
    protected boolean closable;
    /** The frame has been closed. */
    protected boolean isClosed;
    /** The frame can be expanded to the size of the desktop pane. */
    protected boolean maximizable;
    /**
     * The frame has been expanded to its maximum size.
     * @see #maximizable
     */
    protected boolean isMaximum;
    /**
     * The frame can "iconified" (shrunk down and displayed as
     * an icon-image).
     */
    protected boolean iconable;
    /**
     * The frame has been iconified.
     * @see #isIcon()
     */
    protected boolean isIcon;
    /** The frame's size can be changed. */
    protected boolean resizable;
    /** The frame is currently selected. */
    protected boolean isSelected;
    /** The icon shown in the top-left corner of this internal frame. */
    protected Icon frameIcon;
    /** The title displayed in this internal frame's title bar. */
    protected String  title;
    /**
     * The icon that is displayed when this internal frame is iconified.
     * @see #iconable
     */
    protected JDesktopIcon desktopIcon;

    private Rectangle normalBounds = null;

    private int defaultCloseOperation = DISPOSE_ON_CLOSE;

    private Component lastFocusOwner;

    /** Bound property name. */
    public final static String CONTENT_PANE_PROPERTY = "contentPane";
    /** Bound property name. */
    public final static String MENU_BAR_PROPERTY = "JMenuBar";
    /** Bound property name. */
    public final static String TITLE_PROPERTY = "title";
    /** Bound property name. */
    public final static String LAYERED_PANE_PROPERTY = "layeredPane";
    /** Bound property name. */
    public final static String ROOT_PANE_PROPERTY = "rootPane";
    /** Bound property name. */
    public final static String GLASS_PANE_PROPERTY = "glassPane";
    /** Bound property name. */
    public final static String FRAME_ICON_PROPERTY = "frameIcon";

    /**
     * Constrained property name indicated that this frame has
     * selected status.
     */
    public final static String IS_SELECTED_PROPERTY = "selected";
    /** Constrained property name indicating that the internal frame is closed. */
    public final static String IS_CLOSED_PROPERTY = "closed";
    /** Constrained property name indicating that the internal frame is maximized. */
    public final static String IS_MAXIMUM_PROPERTY = "maximum";
    /** Constrained property name indicating that the internal frame is iconified. */
    public final static String IS_ICON_PROPERTY = "icon";

    private boolean opened;

    /**
     * Creates a non-resizable, non-closable, non-maximizable,
     * non-iconifiable <code>JInternalFrame</code> with no title.
     */
    public JInternalFrame() {
        this("", false, false, false, false);
    }

    /**
     * Creates a non-resizable, non-closable, non-maximizable,
     * non-iconifiable <code>JInternalFrame</code> with the specified title.
     *
     * @param title  the non-<code>null</code> <code>String</code>
     *     to display in the title bar
     */
    public JInternalFrame(String title) {
        this(title, false, false, false, false);
    }

    /**
     * Creates a non-closable, non-maximizable, non-iconifiable
     * <code>JInternalFrame</code> with the specified title
     * and resizability.
     */
    public JInternalFrame(String title, boolean resizable) {
        this(title, resizable, false, false, false);
    }

    /**
     * Creates a non-maximizable, non-iconifiable <code>JInternalFrame</code>
     * with the specified title, resizability, and
     * closability.
     */
    public JInternalFrame(String title, boolean resizable, boolean closable) {
        this(title, resizable, closable, false, false);
    }

    /**
     * Creates a non-iconifiable <code>JInternalFrame</code>
     * with the specified title,
     * resizability, closability, and maximizability.
     */
    public JInternalFrame(String title, boolean resizable, boolean closable,
                          boolean maximizable) {
        this(title, resizable, closable, maximizable, false);
    }

    /**
     * Creates a <code>JInternalFrame</code> with the specified title,
     * resizability, closability, maximizability, and iconifiability.
     * All <code>JInternalFrame</code> constructors use this one.
     */
    public JInternalFrame(String title, boolean resizable, boolean closable,
                                boolean maximizable, boolean iconifiable) {

        // Layout before root pane (8u does the reverse): with no InternalFrameUI to lay the
        // frame out, the BorderLayout has to see the CENTER constraint when the root pane is added.
        setLayout(new BorderLayout());
        setRootPane(createRootPane());
        this.title = title;
        this.resizable = resizable;
        this.closable = closable;
        this.maximizable = maximizable;
        isMaximum = false;
        this.iconable = iconifiable;
        isIcon = false;
        setVisible(false);
        setRootPaneCheckingEnabled(true);
        desktopIcon = new JDesktopIcon(this);
        updateUI();
    }

    /**
     * Deliberate no-op: no <code>InternalFrameUI</code> delegate (BasicInternalFrameUI and
     * its title pane) is ported, so a look and feel's mapping for "InternalFrameUI" cannot
     * load. The frame paints as a plain container around its root pane.
     */
    public void updateUI() {
    }

    /**
     * Called by the constructor to set up the <code>JRootPane</code>.
     * @return  a new <code>JRootPane</code>
     */
    protected JRootPane createRootPane() {
        return new JRootPane();
    }

    /**
     * Returns the name of the look-and-feel
     * class that renders this component.
     *
     * @return the string "InternalFrameUI"
     */
    public String getUIClassID() {
        return "InternalFrameUI";
    }

    /**
     * Returns the look-and-feel object that renders this component.
     *
     * @return the <code>InternalFrameUI</code> object that renders
     *          this component
     */
    public InternalFrameUI getUI() {
        return (InternalFrameUI)ui;
    }

    /**
     * Sets the UI delegate for this <code>JInternalFrame</code>.
     *
     * @param ui  the UI delegate
     */
    public void setUI(InternalFrameUI ui) {
        boolean checkingEnabled = isRootPaneCheckingEnabled();
        try {
            setRootPaneCheckingEnabled(false);
            super.setUI(ui);
        }
        finally {
            setRootPaneCheckingEnabled(checkingEnabled);
        }
    }

    /**
     * Returns the <code>JDesktopIcon</code> used when this
     * <code>JInternalFrame</code> is iconified.
     *
     * @return the <code>JDesktopIcon</code> displayed on the desktop
     */
    public JDesktopIcon getDesktopIcon() {
        return desktopIcon;
    }

    /**
     * Sets the <code>JDesktopIcon</code> associated with this
     * <code>JInternalFrame</code>.
     *
     * @param d the <code>JDesktopIcon</code> to display on the desktop
     */
    public void setDesktopIcon(JDesktopIcon d) {
        JDesktopIcon oldValue = getDesktopIcon();
        desktopIcon = d;
        firePropertyChange("desktopIcon", oldValue, d);
    }

    /**
     * Convenience method that searches the ancestor hierarchy for a
     * <code>JDesktop</code> instance. If <code>JInternalFrame</code>
     * finds none, the <code>desktopIcon</code> tree is searched.
     *
     * @return the <code>JDesktopPane</code> this internal frame belongs to,
     *         or <code>null</code> if none is found
     */
    public JDesktopPane getDesktopPane() {
        Container p = getParent();
        while (p != null && !(p instanceof JDesktopPane)) {
            p = p.getParent();
        }
        if (p == null && desktopIcon != null) {
            p = desktopIcon.getParent();
            while (p != null && !(p instanceof JDesktopPane)) {
                p = p.getParent();
            }
        }
        return (JDesktopPane) p;
    }

    /**
     * Returns whether calls to <code>add</code> and
     * <code>setLayout</code> are forwarded to the <code>contentPane</code>.
     */
    protected boolean isRootPaneCheckingEnabled() {
        return rootPaneCheckingEnabled;
    }

    /**
     * Sets whether calls to <code>add</code> and
     * <code>setLayout</code> are forwarded to the <code>contentPane</code>.
     */
    protected void setRootPaneCheckingEnabled(boolean enabled) {
        rootPaneCheckingEnabled = enabled;
    }

    /*
     * 8u forwards through addImpl; the shadow Container has no single add funnel, so
     * each add form forwards to the content pane while root pane checking is on.
     */
    public Component add(Component comp) {
        if (isRootPaneCheckingEnabled()) {
            return getContentPane().add(comp);
        }
        return super.add(comp);
    }

    public Component add(Component comp, int index) {
        if (isRootPaneCheckingEnabled()) {
            return getContentPane().add(comp, index);
        }
        return super.add(comp, index);
    }

    public void add(Component comp, Object constraints) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().add(comp, constraints);
        } else {
            super.add(comp, constraints);
        }
    }

    public void add(Component comp, Object constraints, int index) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().add(comp, constraints, index);
        } else {
            super.add(comp, constraints, index);
        }
    }

    /**
     * Removes the specified component from the container. If
     * <code>comp</code> is not a child of the <code>JInternalFrame</code>
     * this will forward the call to the <code>contentPane</code>.
     */
    public void remove(Component comp) {
        int oldCount = getComponentCount();
        super.remove(comp);
        if (oldCount == getComponentCount()) {
            getContentPane().remove(comp);
        }
    }

    /**
     * Ensures that, by default, the layout of this component cannot be set.
     * Overridden to conditionally forward the call to the
     * <code>contentPane</code>.
     */
    public void setLayout(LayoutManager manager) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().setLayout(manager);
        }
        else {
            super.setLayout(manager);
        }
    }

    /**
     * Returns the current <code>JMenuBar</code> for this
     * <code>JInternalFrame</code>, or <code>null</code>
     * if no menu bar has been set.
     *
     * @deprecated As of Swing version 1.0.3,
     * replaced by <code>getJMenuBar()</code>.
     */
    @Deprecated
    public JMenuBar getMenuBar() {
      return getRootPane().getJMenuBar();
    }

    /** Returns the current <code>JMenuBar</code> for this <code>JInternalFrame</code>. */
    public JMenuBar getJMenuBar() {
        return getRootPane().getJMenuBar();
    }

    /**
     * Sets the <code>menuBar</code> property for this <code>JInternalFrame</code>.
     *
     * @deprecated As of Swing version 1.0.3
     *  replaced by <code>setJMenuBar(JMenuBar m)</code>.
     */
    @Deprecated
    public void setMenuBar(JMenuBar m) {
        JMenuBar oldValue = getMenuBar();
        getRootPane().setJMenuBar(m);
        firePropertyChange(MENU_BAR_PROPERTY, oldValue, m);
    }

    /** Sets the <code>menuBar</code> property for this <code>JInternalFrame</code>. */
    public void setJMenuBar(JMenuBar m){
        JMenuBar oldValue = getMenuBar();
        getRootPane().setJMenuBar(m);
        firePropertyChange(MENU_BAR_PROPERTY, oldValue, m);
    }

    /** Returns the content pane for this internal frame. */
    public Container getContentPane() {
        return getRootPane().getContentPane();
    }

    /**
     * Sets this <code>JInternalFrame</code>'s <code>contentPane</code>
     * property.
     *
     * @exception java.awt.IllegalComponentStateException (a runtime
     *              exception) if the content pane parameter is <code>null</code>
     */
    public void setContentPane(Container c) {
        Container oldValue = getContentPane();
        getRootPane().setContentPane(c);
        firePropertyChange(CONTENT_PANE_PROPERTY, oldValue, c);
    }

    /** Returns the layered pane for this internal frame. */
    public JLayeredPane getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    /**
     * Sets this <code>JInternalFrame</code>'s
     * <code>layeredPane</code> property.
     *
     * @exception java.awt.IllegalComponentStateException (a runtime
     *              exception) if the layered pane parameter is <code>null</code>
     */
    public void setLayeredPane(JLayeredPane layered) {
        if (layered == null) {
            throw new IllegalComponentStateException("layeredPane cannot be set to null.");
        }
        JLayeredPane oldValue = getLayeredPane();
        getRootPane().setLayeredPane(layered);
        firePropertyChange(LAYERED_PANE_PROPERTY, oldValue, layered);
    }

    /** Returns the glass pane for this internal frame. */
    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    /** Sets this <code>JInternalFrame</code>'s <code>glassPane</code> property. */
    public void setGlassPane(Component glass) {
        Component oldValue = getGlassPane();
        getRootPane().setGlassPane(glass);
        firePropertyChange(GLASS_PANE_PROPERTY, oldValue, glass);
    }

    /** Returns the <code>rootPane</code> object for this internal frame. */
    public JRootPane getRootPane() {
        return rootPane;
    }

    /** Sets the <code>rootPane</code> property for this <code>JInternalFrame</code>. */
    protected void setRootPane(JRootPane root) {
        if (rootPane != null) {
            remove(rootPane);
        }
        JRootPane oldValue = getRootPane();
        rootPane = root;
        if (rootPane != null) {
            boolean checkingEnabled = isRootPaneCheckingEnabled();
            try {
                setRootPaneCheckingEnabled(false);
                add(rootPane, BorderLayout.CENTER);
            }
            finally {
                setRootPaneCheckingEnabled(checkingEnabled);
            }
        }
        firePropertyChange(ROOT_PANE_PROPERTY, oldValue, root);
    }

    /** Sets whether this <code>JInternalFrame</code> can be closed by some user action. */
    public void setClosable(boolean b) {
        Boolean oldValue = closable ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = b ? Boolean.TRUE : Boolean.FALSE;
        closable = b;
        firePropertyChange("closable", oldValue, newValue);
    }

    /** Returns whether this <code>JInternalFrame</code> can be closed by some user action. */
    public boolean isClosable() {
        return closable;
    }

    /** Returns whether this <code>JInternalFrame</code> is currently closed. */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Closes this internal frame if the argument is <code>true</code>.
     * Do not invoke this method with a <code>false</code> argument;
     * the result of invoking <code>setClosed(false)</code>
     * is unspecified.
     *
     * <p>If the internal frame is already closed, this method does nothing
     * and returns immediately. Otherwise, this method begins by firing an
     * <code>INTERNAL_FRAME_CLOSING</code> event, then sets the
     * <code>closed</code> property to <code>true</code> unless a listener
     * vetoes the property change. This method finishes by making the
     * internal frame invisible and unselected, and then firing an
     * <code>INTERNAL_FRAME_CLOSED</code> event.
     *
     * @exception PropertyVetoException when the attempt to set the
     *            property is vetoed by the <code>JInternalFrame</code>
     */
    public void setClosed(boolean b) throws PropertyVetoException {
        if (isClosed == b) {
            return;
        }

        Boolean oldValue = isClosed ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = b ? Boolean.TRUE : Boolean.FALSE;
        if (b) {
          fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_CLOSING);
        }
        fireVetoableChange(IS_CLOSED_PROPERTY, oldValue, newValue);
        isClosed = b;
        if (isClosed) {
          setVisible(false);
        }
        firePropertyChange(IS_CLOSED_PROPERTY, oldValue, newValue);
        if (isClosed) {
          dispose();
        } else if (!opened) {
          /* this bogus -- we haven't defined what
             setClosed(false) means. */
          //        fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_OPENED);
          //            opened = true;
        }
    }

    /** Sets whether the <code>JInternalFrame</code> can be resized by some user action. */
    public void setResizable(boolean b) {
        Boolean oldValue = resizable ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = b ? Boolean.TRUE : Boolean.FALSE;
        resizable = b;
        firePropertyChange("resizable", oldValue, newValue);
    }

    /** Returns whether the <code>JInternalFrame</code> can be resized by some user action. */
    public boolean isResizable() {
        // don't allow resizing when maximized.
        return isMaximum ? false : resizable;
    }

    /** Sets the <code>iconable</code> property. */
    public void setIconifiable(boolean b) {
        Boolean oldValue = iconable ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = b ? Boolean.TRUE : Boolean.FALSE;
        iconable = b;
        firePropertyChange("iconable", oldValue, newValue);
    }

    /** Gets the <code>iconable</code> property, which by default is <code>false</code>. */
    public boolean isIconifiable() {
        return iconable;
    }

    /** Returns whether the <code>JInternalFrame</code> is currently iconified. */
    public boolean isIcon() {
        return isIcon;
    }

    /**
     * Iconifies or de-iconifies this internal frame, if the look and feel
     * supports iconification. If the internal frame's state changes to
     * iconified, this method fires an <code>INTERNAL_FRAME_ICONIFIED</code>
     * event. If the state changes to de-iconified, an
     * <code>INTERNAL_FRAME_DEICONIFIED</code> event is fired.
     *
     * @exception PropertyVetoException when the attempt to set the
     *            property is vetoed by the <code>JInternalFrame</code>
     */
    public void setIcon(boolean b) throws PropertyVetoException {
        if (isIcon == b) {
            return;
        }

        /* If an internal frame is being iconified before it has a
           parent, (e.g., client wants it to start iconic), create the
           parent if possible so that we can place the icon in its
           proper place on the desktop. I am not sure the call to
           validate() is necessary, since we are not going to display
           this frame yet */
        firePropertyChange("ancestor", null, getParent());

        Boolean oldValue = isIcon ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = b ? Boolean.TRUE : Boolean.FALSE;
        fireVetoableChange(IS_ICON_PROPERTY, oldValue, newValue);
        isIcon = b;
        firePropertyChange(IS_ICON_PROPERTY, oldValue, newValue);
        if (b)
          fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_ICONIFIED);
        else
          fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED);
    }

    /** Sets the <code>maximizable</code> property. */
    public void setMaximizable(boolean b) {
        Boolean oldValue = maximizable ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = b ? Boolean.TRUE : Boolean.FALSE;
        maximizable = b;
        firePropertyChange("maximizable", oldValue, newValue);
    }

    /** Gets the value of the <code>maximizable</code> property. */
    public boolean isMaximizable() {
        return maximizable;
    }

    /** Returns whether the <code>JInternalFrame</code> is currently maximized. */
    public boolean isMaximum() {
        return isMaximum;
    }

    /**
     * Maximizes and restores this internal frame.  A maximized frame is resized to
     * fully fit the <code>JDesktopPane</code> area associated with the
     * <code>JInternalFrame</code>.
     * A restored frame's size is set to the <code>JInternalFrame</code>'s
     * actual size.
     *
     * @exception PropertyVetoException when the attempt to set the
     *            property is vetoed by the <code>JInternalFrame</code>
     */
    public void setMaximum(boolean b) throws PropertyVetoException {
        if (isMaximum == b) {
            return;
        }

        Boolean oldValue = isMaximum ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = b ? Boolean.TRUE : Boolean.FALSE;
        fireVetoableChange(IS_MAXIMUM_PROPERTY, oldValue, newValue);
        /* setting isMaximum above the event firing means that
           property listeners that, for some reason, test it will
           get it wrong... See, for example, getNormalBounds() */
        isMaximum = b;
        firePropertyChange(IS_MAXIMUM_PROPERTY, oldValue, newValue);
    }

    /** Returns the title of the <code>JInternalFrame</code>. */
    public String getTitle() {
        return title;
    }

    /** Sets the <code>JInternalFrame</code> title. */
    public void setTitle(String title) {
        String oldValue = this.title;
        this.title = title;
        firePropertyChange(TITLE_PROPERTY, oldValue, title);
    }

    /**
     * Selects or deselects the internal frame
     * if it's showing.
     * A <code>JInternalFrame</code> normally draws its title bar
     * differently if it is
     * the selected frame, which indicates to the user that this
     * internal frame has the focus.
     * When this method changes the state of the
     * internal frame from deselected to selected, it fires an
     * <code>InternalFrameEvent.INTERNAL_FRAME_ACTIVATED</code> event.
     * If the change is from selected to deselected,
     * an <code>InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED</code> event
     * is fired.
     *
     * @exception PropertyVetoException when the attempt to set the
     *            property is vetoed by the <code>JInternalFrame</code>
     */
    public void setSelected(boolean selected) throws PropertyVetoException {
       // The InternalFrame may already be selected, but the focus
       // may be outside it, so restore the focus to the subcomponent
       // which previously had it. See Bug 4302764.
        if (selected && isSelected) {
            restoreSubcomponentFocus();
            return;
        }
        // The internal frame or the desktop icon must be showing to allow
        // selection.  We may deselect even if neither is showing.
        if ((isSelected == selected) || (selected && !isShowing())) {
            return;
        }

        Boolean oldValue = isSelected ? Boolean.TRUE : Boolean.FALSE;
        Boolean newValue = selected ? Boolean.TRUE : Boolean.FALSE;
        fireVetoableChange(IS_SELECTED_PROPERTY, oldValue, newValue);

        /* We don't want to leave focus in the previously selected
           frame, so we have to set it to *something* in case it
           doesn't get set in some other way (as if a user clicked on
           a component that doesn't request focus).  If this call is
           happening because the user clicked on a component that will
           want focus, then it will get transfered there later.

           We test for parent.isShowing() above, because AWT throws a
           NPE if you try to request focus on a lightweight before its
           parent has been made visible */

        if (selected) {
            restoreSubcomponentFocus();
        }

        isSelected = selected;
        firePropertyChange(IS_SELECTED_PROPERTY, oldValue, newValue);
        if (isSelected)
          fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_ACTIVATED);
        else
          fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED);
        repaint();
    }

    /** Returns whether the <code>JInternalFrame</code> is the currently "selected" or active frame. */
    public boolean isSelected() {
        return isSelected;
    }

    /** Sets an image to be displayed in the titlebar of this internal frame. */
    public void setFrameIcon(Icon icon) {
        Icon oldIcon = frameIcon;
        frameIcon = icon;
        firePropertyChange(FRAME_ICON_PROPERTY, oldIcon, icon);
    }

    /** Returns the image displayed in the title bar of this internal frame. */
    public Icon getFrameIcon()  {
        return frameIcon;
    }

    /** Convenience method that moves this component to position 0 if its parent is a <code>JLayeredPane</code>. */
    public void moveToFront() {
        if (getParent() instanceof JLayeredPane) {
            ((JLayeredPane) getParent()).moveToFront(this);
        }
    }

    /** Convenience method that moves this component to position -1 if its parent is a <code>JLayeredPane</code>. */
    public void moveToBack() {
        if (getParent() instanceof JLayeredPane) {
            ((JLayeredPane) getParent()).moveToBack(this);
        }
    }

    /** Convenience method for setting the layer attribute of this component. */
    public void setLayer(Integer layer) {
        if (getParent() != null && getParent() instanceof JLayeredPane) {
            // Normally we want to do this, as it causes the LayeredPane
            // to draw properly.
            JLayeredPane p = (JLayeredPane) getParent();
            p.setLayer(this, layer.intValue(), p.getPosition(this));
        } else {
             // Try to do the right thing
             JLayeredPane.putLayer(this, layer.intValue());
             if (getParent() != null)
                 getParent().repaint(getX(), getY(), getWidth(), getHeight());
        }
    }

    /** Convenience method for setting the layer attribute of this component. */
    public void setLayer(int layer) {
      this.setLayer(Integer.valueOf(layer));
    }

    /** Convenience method for getting the layer attribute of this component. */
    public int getLayer() {
        return JLayeredPane.getLayer(this);
    }

    /** Sets the bounds this frame is restored to when it leaves the maximized state. */
    public void setNormalBounds(Rectangle r) {
        normalBounds = r;
    }

    /**
     * If the <code>JInternalFrame</code> is not in maximized state, returns
     * <code>getBounds()</code>; otherwise, returns the bounds that the
     * <code>JInternalFrame</code> would be restored to.
     */
    public Rectangle getNormalBounds() {

      /* we used to test (!isMaximum) here, but since this
         method is used by the property listener for the
         IS_MAXIMUM_PROPERTY, it ended up getting the wrong
         answer... Since normalBounds get set to null when the
         frame is restored, this should work better */

      if (normalBounds != null) {
        return normalBounds;
      } else {
        return getBounds();
      }
    }

    /** If this <code>JInternalFrame</code> is active, returns the child that has focus. */
    public Component getFocusOwner() {
        if (isSelected()) {
            return lastFocusOwner;
        }
        return null;
    }

    /** Returns the child component of this <code>JInternalFrame</code> that will receive the focus when this frame is selected. */
    public Component getMostRecentFocusOwner() {
        if (isSelected()) {
            return getFocusOwner();
        }
        if (lastFocusOwner != null) {
            return lastFocusOwner;
        }
        FocusTraversalPolicy policy = getFocusTraversalPolicy();
        if (policy instanceof InternalFrameFocusTraversalPolicy) {
            return ((InternalFrameFocusTraversalPolicy) policy).getInitialComponent(this);
        }
        Component toFocus = policy.getDefaultComponent(this);
        if (toFocus != null) {
            return toFocus;
        }
        return getContentPane();
    }

    /** Requests the internal frame to restore focus to the last subcomponent that had focus. */
    public void restoreSubcomponentFocus() {
        Component c = getMostRecentFocusOwner();
        if (c != null) {
            c.requestFocus();
        }
    }

    /** Adds the specified listener to receive internal frame events from this internal frame. */
    public void addInternalFrameListener(InternalFrameListener l) {  // remind: sync ??
      listenerList.add(InternalFrameListener.class, l);
    }

    /** Removes the specified internal frame listener so that it no longer receives internal frame events from this internal frame. */
    public void removeInternalFrameListener(InternalFrameListener l) {  // remind: sync??
      listenerList.remove(InternalFrameListener.class, l);
    }

    /** Returns an array of all the <code>InternalFrameListener</code>s added to this <code>JInternalFrame</code>. */
    public InternalFrameListener[] getInternalFrameListeners() {
        return listenerList.getListeners(InternalFrameListener.class);
    }

    // remind: name ok? all one method ok? need to be synchronized?
    /**
     * Fires an internal frame event.
     *
     * @param id  the type of the event being fired; one of the following:
     * <ul>
     * <li><code>InternalFrameEvent.INTERNAL_FRAME_OPENED</code>
     * <li><code>InternalFrameEvent.INTERNAL_FRAME_CLOSING</code>
     * <li><code>InternalFrameEvent.INTERNAL_FRAME_CLOSED</code>
     * <li><code>InternalFrameEvent.INTERNAL_FRAME_ICONIFIED</code>
     * <li><code>InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED</code>
     * <li><code>InternalFrameEvent.INTERNAL_FRAME_ACTIVATED</code>
     * <li><code>InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED</code>
     * </ul>
     * If the event type is not one of the above, nothing happens.
     */
    protected void fireInternalFrameEvent(int id){
      Object[] listeners = listenerList.getListenerList();
      InternalFrameEvent e = null;
      for (int i = listeners.length -2; i >=0; i -= 2){
        if (listeners[i] == InternalFrameListener.class){
          if (e == null){
            e = new InternalFrameEvent(this, id);
            //      System.out.println("InternalFrameEvent: " + e.paramString());
          }
          switch(e.getID()) {
          case InternalFrameEvent.INTERNAL_FRAME_OPENED:
            ((InternalFrameListener)listeners[i+1]).internalFrameOpened(e);
            break;
          case InternalFrameEvent.INTERNAL_FRAME_CLOSING:
            ((InternalFrameListener)listeners[i+1]).internalFrameClosing(e);
            break;
          case InternalFrameEvent.INTERNAL_FRAME_CLOSED:
            ((InternalFrameListener)listeners[i+1]).internalFrameClosed(e);
            break;
          case InternalFrameEvent.INTERNAL_FRAME_ICONIFIED:
            ((InternalFrameListener)listeners[i+1]).internalFrameIconified(e);
            break;
          case InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED:
            ((InternalFrameListener)listeners[i+1]).internalFrameDeiconified(e);
            break;
          case InternalFrameEvent.INTERNAL_FRAME_ACTIVATED:
            ((InternalFrameListener)listeners[i+1]).internalFrameActivated(e);
            break;
          case InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED:
            ((InternalFrameListener)listeners[i+1]).internalFrameDeactivated(e);
            break;
          default:
            break;
          }
        }
      }
      /* we could do it off the event, but at the moment, that's not how
         I'm implementing it */
      //      if (id == InternalFrameEvent.INTERNAL_FRAME_CLOSING) {
      //          doDefaultCloseAction();
      //      }
    }

    /**
     * Fires an
     * <code>INTERNAL_FRAME_CLOSING</code> event
     * and then performs the action specified by
     * the internal frame's default close operation.
     */
    public void doDefaultCloseAction() {
        fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_CLOSING);
        switch(defaultCloseOperation) {
          case DO_NOTHING_ON_CLOSE:
            break;
          case HIDE_ON_CLOSE:
            setVisible(false);
            if (isSelected())
                try {
                    setSelected(false);
                } catch (PropertyVetoException pve) {}

            /* should this activate the next frame? that's really
               desktopmanager's policy... */
            break;
          case DISPOSE_ON_CLOSE:
              try {
                fireVetoableChange(IS_CLOSED_PROPERTY, Boolean.FALSE,
                                   Boolean.TRUE);
                isClosed = true;
                setVisible(false);
                firePropertyChange(IS_CLOSED_PROPERTY, Boolean.FALSE,
                                   Boolean.TRUE);
                dispose();
              } catch (PropertyVetoException pve) {}
              break;
          default:
              break;
        }
    }

    /**
     * Sets the operation that will happen by default when
     * the user initiates a "close" on this internal frame.
     */
    public void setDefaultCloseOperation(int operation) {
        this.defaultCloseOperation = operation;
    }

    /** Returns the default operation that occurs when the user initiates a "close" on this internal frame. */
    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    /**
     * Causes subcomponents of this <code>JInternalFrame</code>
     * to be laid out at their preferred size.
     */
    public void pack() {
        try {
            if (isIcon()) {
                setIcon(false);
            } else if (isMaximum()) {
                setMaximum(false);
            }
        } catch(PropertyVetoException e) { }
        setSize(getPreferredSize());
        validate();
    }

    /**
     * If the internal frame is not visible,
     * brings the internal frame to the front,
     * makes it visible,
     * and attempts to select it.
     * The first time the internal frame is made visible,
     * this method also fires an <code>INTERNAL_FRAME_OPENED</code> event.
     * This method does nothing if the internal frame is already visible.
     * Invoking this method
     * has the same result as invoking
     * <code>setVisible(true)</code>.
     */
    @SuppressWarnings("deprecation")
    public void show() {
        // bug 4312922
        if (isVisible()) {
            //match the behavior of setVisible(true): do nothing
            return;
        }

        // bug 4149505
        if (!opened) {
          fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_OPENED);
          opened = true;
        }

        /* icon default visibility is false; set it to true so that it shows
           up when user iconifies frame */
        moveToFront();

        super.setVisible(true);

        if (!isSelected()) {
            try {
                setSelected(true);
            } catch (PropertyVetoException pve) {}
        }
    }

    @SuppressWarnings("deprecation")
    public void hide() {
        super.setVisible(false);
    }

    /**
     * 8u's Component.setVisible(true) routes through show(); the shadow Component does
     * not, so the opened event and the selection are driven from here as well.
     */
    public void setVisible(boolean b) {
        if (b && !isVisible() && rootPane != null) {
            show();
        } else {
            super.setVisible(b);
        }
    }

    /**
     * Makes this internal frame
     * invisible, unselected, and closed.
     * If the frame is not already closed,
     * this method fires an
     * <code>INTERNAL_FRAME_CLOSED</code> event.
     */
    public void dispose() {
        if (isVisible()) {
            setVisible(false);
        }
        if (isSelected()) {
            try {
                setSelected(false);
            } catch (PropertyVetoException pve) {}
        }
        if (!isClosed) {
          firePropertyChange(IS_CLOSED_PROPERTY, Boolean.FALSE, Boolean.TRUE);
          isClosed = true;
        }
        fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_CLOSED);
    }

    /** Brings this internal frame to the front. Same as <code>moveToFront</code>. */
    public void toFront() {
        moveToFront();
    }

    /** Sends this internal frame to the back. Same as <code>moveToBack</code>. */
    public void toBack() {
        moveToBack();
    }

    /**
     * Does nothing because <code>JInternalFrame</code>s must always be roots of a focus
     * traversal cycle.
     */
    public final void setFocusCycleRoot(boolean focusCycleRoot) {
    }

    /** Always returns <code>true</code> because all <code>JInternalFrame</code>s must be roots of a focus traversal cycle. */
    public final boolean isFocusCycleRoot() {
        return true;
    }

    /** Always returns <code>null</code> because <code>JInternalFrame</code>s must always be roots of a focus traversal cycle. */
    public final Container getFocusCycleRootAncestor() {
        return null;
    }

    /** Gets the warning string that is displayed with this internal frame. */
    public final String getWarningString() {
        return null;
    }

    /** Returns a string representation of this <code>JInternalFrame</code>. */
    protected String paramString() {
        String rootPaneString = (rootPane != null ?
                                 rootPane.toString() : "");
        String rootPaneCheckingEnabledString = (rootPaneCheckingEnabled ?
                                                "true" : "false");
        String closableString = (closable ? "true" : "false");
        String isClosedString = (isClosed ? "true" : "false");
        String maximizableString = (maximizable ? "true" : "false");
        String isMaximumString = (isMaximum ? "true" : "false");
        String iconableString = (iconable ? "true" : "false");
        String isIconString = (isIcon ? "true" : "false");
        String resizableString = (resizable ? "true" : "false");
        String isSelectedString = (isSelected ? "true" : "false");
        String frameIconString = (frameIcon != null ?
                                  frameIcon.toString() : "");
        String titleString = (title != null ?
                              title : "");
        String defaultCloseOperationString;
        if (defaultCloseOperation == HIDE_ON_CLOSE) {
            defaultCloseOperationString = "HIDE_ON_CLOSE";
        } else if (defaultCloseOperation == DISPOSE_ON_CLOSE) {
            defaultCloseOperationString = "DISPOSE_ON_CLOSE";
        } else if (defaultCloseOperation == DO_NOTHING_ON_CLOSE) {
            defaultCloseOperationString = "DO_NOTHING_ON_CLOSE";
        } else defaultCloseOperationString = "";
        String openedString = (opened ? "true" : "false");

        // The shadow Component has no paramString; the bounds stand in for its prefix.
        return getX() + "," + getY() + "," + getWidth() + "x" + getHeight() +
        ",closable=" + closableString +
        ",defaultCloseOperation=" + defaultCloseOperationString +
        ",frameIcon=" + frameIconString +
        ",iconable=" + iconableString +
        ",isClosed=" + isClosedString +
        ",isIcon=" + isIconString +
        ",isMaximum=" + isMaximumString +
        ",isSelected=" + isSelectedString +
        ",maximizable=" + maximizableString +
        ",opened=" + openedString +
        ",resizable=" + resizableString +
        ",rootPane=" + rootPaneString +
        ",rootPaneCheckingEnabled=" + rootPaneCheckingEnabledString +
        ",title=" + titleString;
    }

    /**
     * This component represents an iconified version of a
     * <code>JInternalFrame</code>.
     *
     * @author David Kloba
     */
    @SuppressWarnings("serial") // Same-version serialization only
    static public class JDesktopIcon extends JComponent implements Accessible
    {
        JInternalFrame internalFrame;

        /**
         * Creates an icon for an internal frame.
         *
         * @param f  the <code>JInternalFrame</code>
         *              for which the icon is created
         */
        public JDesktopIcon(JInternalFrame f) {
            setVisible(false);
            setInternalFrame(f);
            updateUI();
        }

        /**
         * Returns the look-and-feel object that renders this component.
         *
         * @return the <code>DesktopIconUI</code> object that renders
         *              this component
         */
        public DesktopIconUI getUI() {
            return (DesktopIconUI)ui;
        }

        /**
         * Sets the look-and-feel object that renders this component.
         *
         * @param ui  the <code>DesktopIconUI</code> look-and-feel object
         */
        public void setUI(DesktopIconUI ui) {
            super.setUI(ui);
        }

        /**
         * Returns the <code>JInternalFrame</code> that this
         * <code>DesktopIcon</code> is associated with.
         *
         * @return the <code>JInternalFrame</code> with which this icon
         *              is associated
         */
        public JInternalFrame getInternalFrame() {
            return internalFrame;
        }

        /**
         * Sets the <code>JInternalFrame</code> with which this
         * <code>DesktopIcon</code> is associated.
         *
         * @param f  the <code>JInternalFrame</code> with which this icon
         *              is associated
         */
        public void setInternalFrame(JInternalFrame f) {
            internalFrame = f;
        }

        /**
         * Convenience method to ask the icon for the <code>Desktop</code>
         * object it belongs to.
         *
         * @return the <code>JDesktopPane</code> that contains this
         *           icon's internal frame, or <code>null</code> if none found
         */
        public JDesktopPane getDesktopPane() {
            if (getInternalFrame() != null) {
                return getInternalFrame().getDesktopPane();
            }
            return null;
        }

        /**
         * Deliberate no-op, as {@link JInternalFrame#updateUI}: no
         * <code>DesktopIconUI</code> delegate (BasicDesktopIconUI) is ported,
         * so a look and feel's mapping for "DesktopIconUI" cannot load.
         */
        public void updateUI() {
        }

        /**
         * Returns the name of the look-and-feel
         * class that renders this component.
         *
         * @return the string "DesktopIconUI"
         */
        public String getUIClassID() {
            return "DesktopIconUI";
        }

        /**
         * Gets the AccessibleContext associated with this JDesktopIcon.
         *
         * @return an AccessibleJDesktopIcon that serves as the
         *         AccessibleContext of this JDesktopIcon
         */
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJDesktopIcon();
            }
            return accessibleContext;
        }

        /**
         * This class implements accessibility support for the
         * <code>JInternalFrame.JDesktopIcon</code> class.
         */
        protected class AccessibleJDesktopIcon extends AccessibleJComponent {
            /**
             * Gets the role of this object.
             *
             * @return an instance of AccessibleRole describing the role of
             * the object
             */
            public AccessibleRole getAccessibleRole() {
                return AccessibleRole.DESKTOP_ICON;
            }
        }
    }
}
