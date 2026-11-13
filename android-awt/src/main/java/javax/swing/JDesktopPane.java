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

import java.awt.Component;
import java.awt.Container;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.plaf.DesktopPaneUI;
import javax.swing.plaf.UIResource;

/**
 * A container used to create a multiple-document interface or a virtual desktop.
 * <code>JInternalFrame</code> objects are added to it, and its
 * <code>DesktopManager</code> carries out the look and feel's actions on them.
 *
 * <p>No <code>DesktopPaneUI</code> delegate is ported, so the desktop installs a
 * <code>DefaultDesktopManager</code> UI resource itself, the one 8u's
 * BasicDesktopPaneUI would install.
 *
 * @author David Kloba
 */
@SuppressWarnings("serial") // Same-version serialization only
public class JDesktopPane extends JLayeredPane
{
    /**
     * @see #getUIClassID
     */
    private static final String uiClassID = "DesktopPaneUI";

    transient DesktopManager desktopManager;

    private transient JInternalFrame selectedFrame = null;

    /**
     * Indicates that the entire contents of the item being dragged
     * should appear inside the desktop pane.
     */
    public static final int LIVE_DRAG_MODE = 0;

    /**
     * Indicates that an outline only of the item being dragged
     * should appear inside the desktop pane.
     */
    public static final int OUTLINE_DRAG_MODE = 1;

    private int dragMode = LIVE_DRAG_MODE;
    private boolean dragModeSet = false;
    /** The frames in selection-cycling order, rebuilt when the children change. */
    private transient List<JInternalFrame> framesCache;
    private boolean componentOrderChanged = true;
    private boolean componentOrderCheckingEnabled = true;

    /** BasicDesktopPaneUI's manager: a UI resource, so a delegate may replace it. */
    private static class DesktopManagerResource extends DefaultDesktopManager implements UIResource {
    }

    /**
     * Creates a new <code>JDesktopPane</code>.
     */
    public JDesktopPane() {
        setUIProperty("opaque", Boolean.TRUE);
        setFocusCycleRoot(true);
        updateUI();
        if (desktopManager == null) {
            setDesktopManager(new DesktopManagerResource());
        }
    }

    /**
     * Returns the L&amp;F object that renders this component.
     *
     * @return the <code>DesktopPaneUI</code> object that
     *   renders this component
     */
    public DesktopPaneUI getUI() {
        return (DesktopPaneUI) ui;
    }

    /**
     * Sets the L&amp;F object that renders this component.
     *
     * @param ui  the DesktopPaneUI L&amp;F object
     * @see UIDefaults#getUI
     */
    public void setUI(DesktopPaneUI ui) {
        super.setUI(ui);
    }

    /**
     * Deliberate no-op: no <code>DesktopPaneUI</code> delegate (BasicDesktopPaneUI) is
     * ported, so a look and feel's mapping for "DesktopPaneUI" cannot load, as
     * {@link JInternalFrame#updateUI}.
     */
    public void updateUI() {
    }

    /**
     * Sets the "dragging style" used by the desktop pane.
     *
     * @param dragMode the style of drag to use for items in the Desktop
     * @see #LIVE_DRAG_MODE
     * @see #OUTLINE_DRAG_MODE
     */
    public void setDragMode(int dragMode) {
        int oldDragMode = this.dragMode;
        this.dragMode = dragMode;
        firePropertyChange("dragMode", oldDragMode, this.dragMode);
        dragModeSet = true;
    }

    /**
     * Gets the current "dragging style" used by the desktop pane.
     * @return either <code>Live_DRAG_MODE</code> or
     *   <code>OUTLINE_DRAG_MODE</code>
     */
    public int getDragMode() {
        return dragMode;
    }

    /**
     * Returns the <code>DesktopManger</code> that handles
     * desktop-specific UI actions.
     */
    public DesktopManager getDesktopManager() {
        return desktopManager;
    }

    /**
     * Sets the <code>DesktopManger</code> that will handle
     * desktop-specific UI actions. This may be overridden by
     * {@code LookAndFeel}.
     *
     * @param d the <code>DesktopManager</code> to use
     */
    public void setDesktopManager(DesktopManager d) {
        DesktopManager oldValue = desktopManager;
        desktopManager = d;
        firePropertyChange("desktopManager", oldValue, desktopManager);
    }

    /**
     * Notification from the <code>UIManager</code> that the L&amp;F has changed.
     * Replaces the current UI object with the latest version from the
     * <code>UIManager</code>.
     *
     * @return the string "DesktopPaneUI"
     * @see JComponent#getUIClassID
     * @see UIDefaults#getUI
     */
    public String getUIClassID() {
        return uiClassID;
    }

    /**
     * Returns all <code>JInternalFrames</code> currently displayed in the
     * desktop. Returns iconified frames as well as expanded frames.
     *
     * @return an array of <code>JInternalFrame</code> objects
     */
    public JInternalFrame[] getAllFrames() {
        return getAllFrames(this).toArray(new JInternalFrame[0]);
    }

    /** Frames and iconified frames in child order, descending into plain containers. */
    private static Collection<JInternalFrame> getAllFrames(Container parent) {
        Collection<JInternalFrame> results = new LinkedHashSet<JInternalFrame>();
        int count = parent.getComponentCount();
        for (int i = 0; i < count; i++) {
            Component next = parent.getComponent(i);
            if (next instanceof JInternalFrame) {
                results.add((JInternalFrame) next);
            } else if (next instanceof JInternalFrame.JDesktopIcon) {
                JInternalFrame frame = ((JInternalFrame.JDesktopIcon) next).getInternalFrame();
                if (frame != null) {
                    results.add(frame);
                }
            } else if (next instanceof Container) {
                results.addAll(getAllFrames((Container) next));
            }
        }
        return results;
    }

    /** Returns the currently active <code>JInternalFrame</code>
      * in this <code>JDesktopPane</code>, or <code>null</code>
      * if no <code>JInternalFrame</code> is currently active.
      *
      * @return the currently active <code>JInternalFrame</code> or
      *   <code>null</code>
      */
    public JInternalFrame getSelectedFrame() {
        return selectedFrame;
    }

    /** Sets the currently active <code>JInternalFrame</code>
     *  in this <code>JDesktopPane</code>. This method is used to bridge
     *  the package gap between JDesktopPane and the platform implementation
     *  code and should not be called directly. To visually select the frame
     *  the client must call JInternalFrame.setSelected(true) to activate
     *  the frame.
     *
     *  @param f the internal frame that's currently selected
     */
    public void setSelectedFrame(JInternalFrame f) {
        selectedFrame = f;
    }

    /**
     * Returns all <code>JInternalFrames</code> currently displayed in the
     * specified layer of the desktop. Returns iconified frames as well
     * expanded frames.
     *
     * @param layer  an int specifying the desktop layer
     * @return an array of <code>JInternalFrame</code> objects
     * @see JLayeredPane
     */
    public JInternalFrame[] getAllFramesInLayer(int layer) {
        Collection<JInternalFrame> allFrames = getAllFrames(this);
        List<JInternalFrame> result = new ArrayList<JInternalFrame>(allFrames.size());
        for (JInternalFrame frame : allFrames) {
            if (frame.getLayer() == layer) {
                result.add(frame);
            }
        }
        return result.toArray(new JInternalFrame[0]);
    }

    /**
     * The frames in the order selection cycles through them: highest layer
     * first, then front to back within a layer.
     */
    private List<JInternalFrame> getFrames() {
        List<JInternalFrame> frames = new ArrayList<JInternalFrame>();
        List<Integer> layers = new ArrayList<Integer>();
        int count = getComponentCount();
        for (int i = 0; i < count; i++) {
            Component c = getComponent(i);
            JInternalFrame frame;
            if (c instanceof JInternalFrame) {
                frame = (JInternalFrame) c;
            } else if (c instanceof JInternalFrame.JDesktopIcon) {
                frame = ((JInternalFrame.JDesktopIcon) c).getInternalFrame();
                if (frame == null) {
                    continue;
                }
            } else {
                continue;
            }
            if (frames.contains(frame)) {
                continue;
            }
            int layer = getLayer(c);
            // Children are kept highest layer first already; the stable insert keeps
            // that order even for a child whose layer was changed behind our back.
            int at = frames.size();
            while (at > 0 && layers.get(at - 1).intValue() < layer) {
                at--;
            }
            frames.add(at, frame);
            layers.add(at, Integer.valueOf(layer));
        }
        return frames;
    }

    private JInternalFrame getNextFrame(JInternalFrame f, boolean forward) {
        verifyFramesCache();
        if (f == null) {
            return getTopInternalFrame();
        }
        int i = framesCache.indexOf(f);
        if (i == -1 || framesCache.size() == 1) {
            // Not found or the only frame.
            return null;
        }
        if (forward) {
            if (++i == framesCache.size()) {
                i = 0;
            }
        } else {
            if (--i == -1) {
                i = framesCache.size() - 1;
            }
        }
        return framesCache.get(i);
    }

    JInternalFrame getNextFrame(JInternalFrame f) {
        return getNextFrame(f, true);
    }

    private JInternalFrame getTopInternalFrame() {
        if (framesCache.size() == 0) {
            return null;
        }
        return framesCache.get(0);
    }

    private void updateFramesCache() {
        framesCache = getFrames();
    }

    private void verifyFramesCache() {
        // If the frames cache is dirty, then recalculate the frame list.
        if (componentOrderChanged || framesCache == null) {
            componentOrderChanged = false;
            updateFramesCache();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(Component comp) {
        super.remove(comp);
        updateFramesCache();
    }

    /**
     * Selects the next <code>JInternalFrame</code> in this desktop pane.
     *
     * @param forward a boolean indicating which direction to select in;
     *        <code>true</code> for forward, <code>false</code> for
     *        backward
     * @return the JInternalFrame that was selected or <code>null</code>
     *         if nothing was selected
     */
    public JInternalFrame selectFrame(boolean forward) {
        JInternalFrame selected = getSelectedFrame();
        JInternalFrame frameToSelect = getNextFrame(selected, forward);
        if (frameToSelect == null) {
            return null;
        }
        // Moving the frames must not reorder the cycle being walked.
        setComponentOrderCheckingEnabled(false);
        if (forward && selected != null) {
            // Moves the selected frame to the back so cycling stays round-robin.
            selected.moveToBack();
        }
        try {
            frameToSelect.setSelected(true);
        } catch (PropertyVetoException pve) {}
        setComponentOrderCheckingEnabled(true);
        return frameToSelect;
    }

    /*
     * Sets whether component order checking is enabled.
     * @param enable a boolean value, where <code>true</code> means
     * a change in component order will cause a change in the keyboard
     * navigation order.
     */
    void setComponentOrderCheckingEnabled(boolean enable) {
        componentOrderCheckingEnabled = enable;
    }

    private void noteOrderChange(Component comp) {
        if (componentOrderCheckingEnabled
                && (comp instanceof JInternalFrame || comp instanceof JInternalFrame.JDesktopIcon)) {
            componentOrderChanged = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void addImpl(Component comp, Object constraints, int index) {
        super.addImpl(comp, constraints, index);
        noteOrderChange(comp);
    }

    /**
     * {@inheritDoc}
     */
    public void remove(int index) {
        if (componentOrderCheckingEnabled && index >= 0 && index < getComponentCount()) {
            noteOrderChange(getComponent(index));
        }
        super.remove(index);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAll() {
        if (componentOrderCheckingEnabled) {
            int count = getComponentCount();
            for (int i = 0; i < count; i++) {
                noteOrderChange(getComponent(i));
            }
        }
        super.removeAll();
    }

    /**
     * {@inheritDoc}
     */
    public void setComponentZOrder(Component comp, int index) {
        super.setComponentZOrder(comp, index);
        noteOrderChange(comp);
    }

    /**
     * Returns true to indicate that this component paints every pixel
     * in its range. (In other words, it does not have a transparent
     * background or foreground.)
     *
     * @return true
     * @see JComponent#isOpaque
     */
    public boolean isOpaque() {
        return true;
    }

    void setUIProperty(String propertyName, Object value) {
        if (propertyName == "dragMode") {
            if (!dragModeSet) {
                setDragMode(((Integer) value).intValue());
                dragModeSet = false;
            }
        } else {
            super.setUIProperty(propertyName, value);
        }
    }

    /**
     * Returns a string representation of this <code>JDesktopPane</code>.
     *
     * @return  a string representation of this <code>JDesktopPane</code>
     */
    protected String paramString() {
        String desktopManagerString = (desktopManager != null ?
                                       desktopManager.toString() : "");

        return super.paramString() +
        ",desktopManager=" + desktopManagerString;
    }

    /**
     * Gets the <code>AccessibleContext</code> associated with this
     * <code>JDesktopPane</code>.
     *
     * @return an <code>AccessibleJDesktopPane</code> that serves as the
     *         <code>AccessibleContext</code> of this <code>JDesktopPane</code>
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJDesktopPane();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>JDesktopPane</code> class.
     */
    protected class AccessibleJDesktopPane extends AccessibleJComponent {

        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of the
         * object
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.DESKTOP_PANE;
        }
    }
}
