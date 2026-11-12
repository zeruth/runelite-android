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

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * A container for {@code JInternalFrame}s, enough for {@code JOptionPane.
 * getDesktopPaneForComponent} and {@code createInternalFrame} to place an
 * option frame in its modal layer. The {@code DesktopManager} and the
 * desktop icons are not ported (the shadow {@code JInternalFrame} has
 * neither), so a frame lays itself out with its own bounds.
 */
public class JDesktopPane extends JLayeredPane {
    private static final long serialVersionUID = 1L;

    private static final String uiClassID = "DesktopPaneUI";

    /** Indicates that the entire contents of the item being dragged should appear inside the desktop pane. */
    public static final int LIVE_DRAG_MODE = 0;
    /** Indicates that an outline only of the item being dragged should appear inside the desktop pane. */
    public static final int OUTLINE_DRAG_MODE = 1;

    private int dragMode = LIVE_DRAG_MODE;
    private transient JInternalFrame selectedFrame;

    public JDesktopPane() {
        setFocusCycleRoot(true);
        setOpaque(true);
    }

    /**
     * Returns the L&amp;F object that renders this component.
     *
     * @return the {@code DesktopPaneUI} object that renders this component
     */
    public javax.swing.plaf.DesktopPaneUI getUI() {
        return (javax.swing.plaf.DesktopPaneUI) ui;
    }

    /**
     * Sets the L&amp;F object that renders this component.
     *
     * @param ui the DesktopPaneUI L&amp;F object
     */
    public void setUI(javax.swing.plaf.DesktopPaneUI ui) {
        super.setUI(ui);
    }

    @Override
    public String getUIClassID() {
        return uiClassID;
    }

    /** Sets the style of dragging used by the desktop pane, {@code LIVE_DRAG_MODE} or {@code OUTLINE_DRAG_MODE}. */
    public void setDragMode(int dragMode) {
        int oldDragMode = this.dragMode;
        this.dragMode = dragMode;
        firePropertyChange("dragMode", oldDragMode, this.dragMode);
    }

    /** Gets the current "dragging style" used by the desktop pane. */
    public int getDragMode() {
        return dragMode;
    }

    /** Returns all {@code JInternalFrames} currently displayed in the desktop. */
    public JInternalFrame[] getAllFrames() {
        return frames(null);
    }

    /** Returns all {@code JInternalFrames} currently displayed in the specified layer of the desktop. */
    public JInternalFrame[] getAllFramesInLayer(int layer) {
        return frames(Integer.valueOf(layer));
    }

    private JInternalFrame[] frames(Integer layer) {
        List<JInternalFrame> result = new ArrayList<JInternalFrame>();
        for (Component child : getComponents()) {
            if (child instanceof JInternalFrame && (layer == null || getLayer(child) == layer.intValue())) {
                result.add((JInternalFrame) child);
            }
        }
        return result.toArray(new JInternalFrame[0]);
    }

    /** Returns the currently active {@code JInternalFrame} in this desktop, or null. */
    public JInternalFrame getSelectedFrame() {
        // A frame removed from the desktop is no longer its selection.
        JInternalFrame frame = selectedFrame;
        return frame != null && frame.getParent() == this ? frame : null;
    }

    /**
     * Sets the currently active {@code JInternalFrame}; called by the frame
     * as it becomes selected, it does not select the frame itself.
     */
    public void setSelectedFrame(JInternalFrame f) {
        selectedFrame = f;
    }
}
