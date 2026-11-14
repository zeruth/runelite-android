/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.beans.PropertyVetoException;

/**
 * A plain implementation of <code>DesktopManager</code>: it moves frames and
 * their desktop icons in and out of the frame's parent, which is normally a
 * <code>JDesktopPane</code>.
 *
 * <p>The shadow has no on-screen XOR painting, so the outline drag mode tracks
 * the pending bounds without drawing the outline and applies them when the drag
 * or resize ends, and the "faster" mode (a desktop blit optimization) moves the
 * frame live; the bounds a frame ends up with are the same as on the JDK.
 *
 * @see JDesktopPane
 * @see JInternalFrame
 * @author David Kloba
 * @author Steve Wilson
 */
@SuppressWarnings("serial") // No Interesting Non-Transient State
public class DefaultDesktopManager implements DesktopManager, java.io.Serializable {
    final static String HAS_BEEN_ICONIFIED_PROPERTY = "wasIconOnce";

    final static int DEFAULT_DRAG_MODE = 0;
    final static int OUTLINE_DRAG_MODE = 1;
    final static int FASTER_DRAG_MODE = 2;

    int dragMode = DEFAULT_DRAG_MODE;

    /** Pending bounds of an outline resize. */
    private transient Rectangle currentBounds = null;
    /** Pending location of an outline drag. */
    private transient Point currentLoc = null;

    /**
     * Normally this method will not be called. If it is, it tries to determine
     * the appropriate parent from the desktopIcon of the frame.
     * Will remove the desktopIcon from its parent if it successfully adds the frame.
     */
    public void openFrame(JInternalFrame f) {
        if (f.getDesktopIcon().getParent() != null) {
            f.getDesktopIcon().getParent().add(f);
            removeIconFor(f);
        }
    }

    /**
     * Removes the frame, and, if necessary, the
     * <code>desktopIcon</code>, from its parent.
     * @param f the <code>JInternalFrame</code> to be removed
     */
    public void closeFrame(JInternalFrame f) {
        JDesktopPane d = f.getDesktopPane();
        if (d == null) {
            return;
        }
        boolean findNext = f.isSelected();
        Container c = f.getParent();
        JInternalFrame nextFrame = null;
        if (findNext) {
            nextFrame = d.getNextFrame(f);
            try {
                f.setSelected(false);
            } catch (PropertyVetoException e2) { }
        }
        if (c != null) {
            c.remove(f); // Removes the focus.
            c.repaint(f.getX(), f.getY(), f.getWidth(), f.getHeight());
        }
        removeIconFor(f);
        if (f.getNormalBounds() != null) {
            f.setNormalBounds(null);
        }
        if (wasIcon(f)) {
            setWasIcon(f, null);
        }
        if (nextFrame != null) {
            try {
                nextFrame.setSelected(true);
            } catch (PropertyVetoException e2) { }
        } else if (findNext && d.getComponentCount() == 0) {
            // It was selected and was the last component on the desktop.
            d.requestFocus();
        }
    }

    /**
     * Resizes the frame to fill its parents bounds.
     * @param f the frame to be resized
     */
    public void maximizeFrame(JInternalFrame f) {
        if (f.isIcon()) {
            try {
                // In turn calls deiconifyFrame in the desktop manager, which
                // resizes the frame to fill the desktop.
                f.setIcon(false);
            } catch (PropertyVetoException e2) {
            }
        } else {
            Container parent = f.getParent();
            // The JDK throws here for a frame outside any container; a frame the
            // shadow drives without a delegate may legitimately have none.
            if (parent == null) {
                return;
            }
            f.setNormalBounds(f.getBounds());
            Rectangle desktopBounds = parent.getBounds();
            setBoundsForFrame(f, 0, 0, desktopBounds.width, desktopBounds.height);
        }

        // Set the maximized frame as selected.
        try {
            f.setSelected(true);
        } catch (PropertyVetoException e2) {
        }
    }

    /**
     * Restores the frame back to its size and position prior
     * to a <code>maximizeFrame</code> call.
     * @param f the <code>JInternalFrame</code> to be restored
     */
    public void minimizeFrame(JInternalFrame f) {
        // If the frame was an icon restore it back to an icon.
        if (f.isIcon()) {
            iconifyFrame(f);
            return;
        }

        if (f.getNormalBounds() != null) {
            Rectangle r = f.getNormalBounds();
            f.setNormalBounds(null);
            try {
                f.setSelected(true);
            } catch (PropertyVetoException e2) { }
            setBoundsForFrame(f, r.x, r.y, r.width, r.height);
        }
    }

    /**
     * Removes the frame from its parent and adds its
     * <code>desktopIcon</code> to the parent.
     * @param f the <code>JInternalFrame</code> to be iconified
     */
    public void iconifyFrame(JInternalFrame f) {
        Container c = f.getParent();
        JDesktopPane d = f.getDesktopPane();
        boolean findNext = f.isSelected();
        JInternalFrame.JDesktopIcon desktopIcon = f.getDesktopIcon();
        if (!wasIcon(f)) {
            Rectangle r = getBoundsForIconOf(f);
            desktopIcon.setBounds(r.x, r.y, r.width, r.height);
            desktopIcon.revalidate();
            setWasIcon(f, Boolean.TRUE);
        }

        if (c == null || d == null) {
            return;
        }

        if (c instanceof JLayeredPane) {
            // The icon sits in the frame's layer.
            JLayeredPane.putLayer(desktopIcon, ((JLayeredPane) c).getLayer(f));
        }
        d.setComponentOrderCheckingEnabled(true);
        c.remove(f);
        c.add(desktopIcon);
        if (findNext) {
            if (d.selectFrame(true) == null) {
                // The icon is the last frame.
                f.restoreSubcomponentFocus();
            }
        }
        c.repaint(f.getX(), f.getY(), f.getWidth(), f.getHeight());
    }

    /**
     * Removes the desktopIcon from its parent and adds its frame
     * to the parent.
     * @param f the <code>JInternalFrame</code> to be de-iconified
     */
    public void deiconifyFrame(JInternalFrame f) {
        JInternalFrame.JDesktopIcon desktopIcon = f.getDesktopIcon();
        Container c = desktopIcon.getParent();
        JDesktopPane d = f.getDesktopPane();
        if (c != null && d != null) {
            c.add(f);
            // A frame restored to the maximized state has to fill the desktop
            // again, which may have been resized in the meantime.
            if (f.isMaximum()) {
                Rectangle desktopBounds = c.getBounds();
                if (f.getWidth() != desktopBounds.width
                        || f.getHeight() != desktopBounds.height) {
                    setBoundsForFrame(f, 0, 0, desktopBounds.width, desktopBounds.height);
                }
            }
            removeIconFor(f);
            if (f.isSelected()) {
                f.moveToFront();
                f.restoreSubcomponentFocus();
            } else {
                try {
                    f.setSelected(true);
                } catch (PropertyVetoException e2) { }
            }
        }
    }

    /**
     * This will activate <b>f</b> moving it to the front. It will
     * set the current active frame's (if any)
     * <code>IS_SELECTED_PROPERTY</code> to <code>false</code>.
     * There can be only one active frame across all Layers.
     * @param f the <code>JInternalFrame</code> to be activated
     */
    public void activateFrame(JInternalFrame f) {
        Container p = f.getParent();
        JDesktopPane d = f.getDesktopPane();
        JInternalFrame currentlyActiveFrame = (d == null) ? null : d.getSelectedFrame();
        if (p == null) {
            // An iconified frame has no parent; its icon does.
            p = f.getDesktopIcon().getParent();
            if (p == null) {
                return;
            }
        }
        if (currentlyActiveFrame == null) {
            if (d != null) {
                d.setSelectedFrame(f);
            }
        } else if (currentlyActiveFrame != f) {
            // Only one frame of the desktop is active at a time.
            if (currentlyActiveFrame.isSelected()) {
                try {
                    currentlyActiveFrame.setSelected(false);
                } catch (PropertyVetoException e2) { }
            }
            if (d != null) {
                d.setSelectedFrame(f);
            }
        }
        f.moveToFront();
    }

    /**
     * Clears the desktop's selected frame when it is <b>f</b>.
     * @param f the <code>JInternalFrame</code> to be deactivated
     */
    public void deactivateFrame(JInternalFrame f) {
        JDesktopPane d = f.getDesktopPane();
        JInternalFrame currentlyActiveFrame = (d == null) ? null : d.getSelectedFrame();
        if (currentlyActiveFrame == f) {
            d.setSelectedFrame(null);
        }
    }

    // implements javax.swing.DesktopManager
    public void beginDraggingFrame(JComponent f) {
        setupDragMode(f);
        currentLoc = null;
        if (f instanceof JInternalFrame) {
            ((JInternalFrame) f).isDragging = true;
        }
    }

    private void setupDragMode(JComponent f) {
        JDesktopPane p = getDesktopPane(f);
        Container parent = f.getParent();
        dragMode = DEFAULT_DRAG_MODE;
        if (p != null) {
            // The client property wins over the desktop's drag mode, as on the JDK.
            String mode = (String) p.getClientProperty("JDesktopPane.dragMode");
            if ("outline".equals(mode)) {
                dragMode = OUTLINE_DRAG_MODE;
            } else if ("faster".equals(mode) && f instanceof JInternalFrame
                    && f.isOpaque() && (parent == null || parent.isOpaque())) {
                dragMode = FASTER_DRAG_MODE;
            } else if (p.getDragMode() == JDesktopPane.OUTLINE_DRAG_MODE) {
                dragMode = OUTLINE_DRAG_MODE;
            } else if (p.getDragMode() == JDesktopPane.LIVE_DRAG_MODE
                    && f instanceof JInternalFrame && f.isOpaque()) {
                dragMode = FASTER_DRAG_MODE;
            } else {
                dragMode = DEFAULT_DRAG_MODE;
            }
        }
    }

    /**
     * Moves the visible location of the frame being dragged
     * to the location specified.  The means by which this occurs can vary depending
     * on the dragging algorithm being used.  The actual logical location of the frame
     * might not change until <code>endDraggingFrame</code> is called.
     */
    public void dragFrame(JComponent f, int newX, int newY) {
        if (dragMode == OUTLINE_DRAG_MODE) {
            currentLoc = new Point(newX, newY);
        } else {
            setBoundsForFrame(f, newX, newY, f.getWidth(), f.getHeight());
        }
    }

    // implements javax.swing.DesktopManager
    public void endDraggingFrame(JComponent f) {
        if (dragMode == OUTLINE_DRAG_MODE && currentLoc != null) {
            setBoundsForFrame(f, currentLoc.x, currentLoc.y, f.getWidth(), f.getHeight());
            currentLoc = null;
        }
        if (f instanceof JInternalFrame) {
            ((JInternalFrame) f).isDragging = false;
        }
    }

    // implements javax.swing.DesktopManager
    public void beginResizingFrame(JComponent f, int direction) {
        setupDragMode(f);
        currentBounds = null;
    }

    /**
     * Calls <code>setBoundsForFrame</code> with the new values.
     * @param f the component to be resized
     * @param newX the new x-coordinate
     * @param newY the new y-coordinate
     * @param newWidth the new width
     * @param newHeight the new height
     */
    public void resizeFrame(JComponent f, int newX, int newY, int newWidth, int newHeight) {
        if (dragMode == DEFAULT_DRAG_MODE || dragMode == FASTER_DRAG_MODE) {
            setBoundsForFrame(f, newX, newY, newWidth, newHeight);
        } else {
            currentBounds = new Rectangle(newX, newY, newWidth, newHeight);
        }
    }

    // implements javax.swing.DesktopManager
    public void endResizingFrame(JComponent f) {
        if (dragMode == OUTLINE_DRAG_MODE && currentBounds != null) {
            setBoundsForFrame(f, currentBounds.x, currentBounds.y,
                    currentBounds.width, currentBounds.height);
            currentBounds = null;
        }
    }

    /** This moves the <code>JComponent</code> and repaints the damaged areas. */
    public void setBoundsForFrame(JComponent f, int newX, int newY, int newWidth, int newHeight) {
        f.setBounds(newX, newY, newWidth, newHeight);
        // As the JDK: revalidate so the frame's own layout follows its new size.
        f.revalidate();
    }

    /**
     * Convenience method to remove the desktopIcon of <b>f</b> is necessary.
     *
     * @param f the {@code JInternalFrame} for which to remove the
     *          {@code desktopIcon}
     */
    protected void removeIconFor(JInternalFrame f) {
        JInternalFrame.JDesktopIcon di = f.getDesktopIcon();
        Container c = di.getParent();
        JDesktopPane d = f.getDesktopPane();
        if (c != null && d != null) {
            c.remove(di);
            c.repaint(di.getX(), di.getY(), di.getWidth(), di.getHeight());
        }
    }

    /**
     * The {@code iconifyFrame()} code calls this to determine the proper bounds
     * for the desktopIcon.
     *
     * @param f the {@code JInternalFrame} of interest
     * @return a {@code Rectangle} containing bounds for the {@code desktopIcon}
     */
    protected Rectangle getBoundsForIconOf(JInternalFrame f) {
        JInternalFrame.JDesktopIcon icon = f.getDesktopIcon();
        Dimension prefSize = icon.getPreferredSize();
        Container c = f.getParent();
        if (c == null) {
            c = f.getDesktopIcon().getParent();
        }
        if (c == null) {
            // Neither the frame nor its icon is placed yet.
            return new Rectangle(0, 0, prefSize.width, prefSize.height);
        }

        Rectangle parentBounds = c.getBounds();
        Component[] components = c.getComponents();
        int w = prefSize.width;
        int h = prefSize.height;

        // Icons fill the bottom row of the desktop from the left, then the rows above it.
        int x = 0;
        int y = parentBounds.height - h;
        while (true) {
            Rectangle candidate = new Rectangle(x, y, w, h);
            JInternalFrame.JDesktopIcon lastIcon = null;
            boolean free = true;
            for (Component component : components) {
                JInternalFrame.JDesktopIcon other;
                if (component instanceof JInternalFrame) {
                    other = ((JInternalFrame) component).getDesktopIcon();
                } else if (component instanceof JInternalFrame.JDesktopIcon) {
                    other = (JInternalFrame.JDesktopIcon) component;
                } else {
                    continue;
                }
                lastIcon = other;
                if (other != icon && candidate.intersects(other.getBounds())) {
                    free = false;
                    break;
                }
            }
            if (free || lastIcon == null) {
                return candidate;
            }
            int step = lastIcon.getBounds().width;
            // A zero-width icon could never move the candidate on.
            x += step > 0 ? step : Math.max(w, 1);
            if (x + w > parentBounds.width) {
                x = 0;
                y -= h;
                if (h <= 0 || y + h < 0) {
                    return candidate;
                }
            }
        }
    }

    /**
     * Stores the bounds of the component just before a maximize call.
     * @param f the component about to be resized
     * @param r the normal bounds to be saved away
     */
    protected void setPreviousBounds(JInternalFrame f, Rectangle r) {
        f.setNormalBounds(r);
    }

    /**
     * Gets the normal bounds of the component prior to the component
     * being maximized.
     * @param f the <code>JInternalFrame</code> of interest
     * @return the normal bounds of the component
     */
    protected Rectangle getPreviousBounds(JInternalFrame f) {
        return f.getNormalBounds();
    }

    /**
     * Sets that the component has been iconized and the bounds of the
     * <code>desktopIcon</code> are valid.
     */
    protected void setWasIcon(JInternalFrame f, Boolean value) {
        if (value != null) {
            f.putClientProperty(HAS_BEEN_ICONIFIED_PROPERTY, value);
        }
    }

    /**
     * Returns <code>true</code> if the component has been iconized
     * and the bounds of the <code>desktopIcon</code> are valid,
     * otherwise returns <code>false</code>.
     *
     * @param f the <code>JInternalFrame</code> of interest
     * @return <code>true</code> if the component has been iconized;
     *    otherwise returns <code>false</code>
     */
    protected boolean wasIcon(JInternalFrame f) {
        return (f.getClientProperty(HAS_BEEN_ICONIFIED_PROPERTY) == Boolean.TRUE);
    }

    JDesktopPane getDesktopPane(JComponent frame) {
        JDesktopPane pane = null;
        Component c = frame.getParent();

        // Find the JDesktopPane
        while (pane == null) {
            if (c instanceof JDesktopPane) {
                pane = (JDesktopPane) c;
            } else if (c == null) {
                break;
            } else {
                c = c.getParent();
            }
        }

        return pane;
    }
}
