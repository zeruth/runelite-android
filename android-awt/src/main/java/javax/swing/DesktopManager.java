/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The desktop-wide policy object of a <code>JDesktopPane</code>: the look and
 * feel's internal frame delegate calls it to carry out opening, closing,
 * iconifying, maximizing, activating, dragging and resizing a frame, so a
 * desktop can change how those look without subclassing the frames.
 *
 * @see JDesktopPane
 * @see JInternalFrame
 * @see JInternalFrame.JDesktopIcon
 *
 * @author David Kloba
 */
public interface DesktopManager
{
    /** Displays the frame in the right place, normally by adding it to its desktop. */
    void openFrame(JInternalFrame f);

    /** Removes the frame and, if needed, its icon from the desktop. */
    void closeFrame(JInternalFrame f);

    /** Resizes the frame to fill its parent. */
    void maximizeFrame(JInternalFrame f);

    /** Restores the frame to the size it had before <code>maximizeFrame</code>. */
    void minimizeFrame(JInternalFrame f);

    /** Removes the frame from its parent and shows its desktop icon instead. */
    void iconifyFrame(JInternalFrame f);

    /** Removes the desktop icon from its parent and puts the frame back. */
    void deiconifyFrame(JInternalFrame f);

    /** Called when the frame gains focus or is selected; usually moves it to the front. */
    void activateFrame(JInternalFrame f);

    /** Called when the frame loses focus or is deselected. */
    void deactivateFrame(JInternalFrame f);

    /** Called when the user starts dragging the component (a frame or an icon). */
    void beginDraggingFrame(JComponent f);

    /** Moves the component being dragged to the given location in its parent. */
    void dragFrame(JComponent f, int newX, int newY);

    /** Signals the end of a drag started by <code>beginDraggingFrame</code>. */
    void endDraggingFrame(JComponent f);

    /** Called when the user starts resizing the component from the given edge. */
    void beginResizingFrame(JComponent f, int direction);

    /** Resizes the component being resized to the given bounds. */
    void resizeFrame(JComponent f, int newX, int newY, int newWidth, int newHeight);

    /** Signals the end of a resize started by <code>beginResizingFrame</code>. */
    void endResizingFrame(JComponent f);

    /** A primitive reshape, used by the other methods. */
    void setBoundsForFrame(JComponent f, int newX, int newY, int newWidth, int newHeight);
}
