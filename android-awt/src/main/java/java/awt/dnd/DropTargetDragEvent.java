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

package java.awt.dnd;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;

/**
 * The <code>DropTargetDragEvent</code> is delivered to a
 * <code>DropTargetListener</code> via its dragEnter() and dragOver() methods.
 * <p>
 * The <code>DropTargetDragEvent</code> reports the <i>source drop actions</i>
 * and the <i>user drop action</i> that reflect the current state of
 * the drag operation.
 *
 * @since 1.2
 */
public class DropTargetDragEvent extends DropTargetEvent {

    private static final long serialVersionUID = -8422265619058953682L;

    /**
     * Construct a <code>DropTargetDragEvent</code> given the
     * <code>DropTargetContext</code> for this operation, the location of the
     * "Drag" <code>Cursor</code>'s hotspot in the <code>Component</code>'s
     * coordinates, the user drop action, and the source drop actions.
     *
     * @param dtc        The DropTargetContext for this operation
     * @param cursorLocn The location of the "Drag" Cursor's
     * hotspot in Component coordinates
     * @param dropAction The user drop action
     * @param srcActions The source drop actions
     *
     * @throws NullPointerException if cursorLocn is null
     * @throws IllegalArgumentException if dropAction is not one of
     *         <code>DnDConstants</code>, or srcActions is not a bitwise
     *         mask of <code>DnDConstants</code>.
     * @throws IllegalArgumentException if dtc is <code>null</code>.
     */
    public DropTargetDragEvent(DropTargetContext dtc, Point cursorLocn, int dropAction, int srcActions) {
        super(dtc);

        if (cursorLocn == null) throw new NullPointerException("cursorLocn");

        if (dropAction != DnDConstants.ACTION_NONE &&
            dropAction != DnDConstants.ACTION_COPY &&
            dropAction != DnDConstants.ACTION_MOVE &&
            dropAction != DnDConstants.ACTION_LINK
        ) throw new IllegalArgumentException("dropAction" + dropAction);

        if ((srcActions & ~(DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK)) != 0) throw new IllegalArgumentException("srcActions");

        location        = cursorLocn;
        actions         = srcActions;
        this.dropAction = dropAction;
    }

    /**
     * This method returns a <code>Point</code> indicating the
     * <code>Cursor</code>'s current location within the
     * <code>Component</code>'s coordinates.
     *
     * @return the current cursor location in <code>Component</code>'s coords.
     */
    public Point getLocation() {
        return location;
    }

    /**
     * This method returns the current <code>DataFlavor</code>s from the
     * <code>DropTargetContext</code>.
     *
     * @return current DataFlavors from the DropTargetContext
     */
    public DataFlavor[] getCurrentDataFlavors() {
        return getDropTargetContext().getCurrentDataFlavors();
    }

    /**
     * This method returns the current <code>DataFlavor</code>s as a
     * <code>java.util.List</code>.
     *
     * @return a <code>java.util.List</code> of the Current <code>DataFlavor</code>s
     */
    public List<DataFlavor> getCurrentDataFlavorsAsList() {
        return getDropTargetContext().getCurrentDataFlavorsAsList();
    }

    /**
     * This method returns a <code>boolean</code> indicating if the specified
     * <code>DataFlavor</code> is supported.
     *
     * @param df the <code>DataFlavor</code> to test
     * @return if a particular DataFlavor is supported
     */
    public boolean isDataFlavorSupported(DataFlavor df) {
        return getDropTargetContext().isDataFlavorSupported(df);
    }

    /**
     * This method returns the source drop actions.
     *
     * @return the source drop actions
     */
    public int getSourceActions() { return actions; }

    /**
     * This method returns the user drop action.
     *
     * @return the user drop action
     */
    public int getDropAction() { return dropAction; }

    /**
     * This method returns the Transferable object that represents
     * the data associated with the current drag operation.
     *
     * @return the Transferable associated with the drag operation
     * @throws InvalidDnDOperationException if the data associated with the drag
     *         operation is not available
     *
     * @since 1.5
     */
    public Transferable getTransferable() {
        return getDropTargetContext().getTransferable();
    }

    /**
     * Accepts the drag.
     *
     * This method should be called from a
     * <code>DropTargetListeners</code> <code>dragEnter</code>,
     * <code>dragOver</code>, and <code>dropActionChanged</code>
     * methods if the implementation wishes to accept an operation
     * from the srcActions other than the one selected by
     * the user as represented by the <code>dropAction</code>.
     *
     * @param dragOperation the operation accepted by the target
     */
    public void acceptDrag(int dragOperation) {
        getDropTargetContext().acceptDrag(dragOperation);
    }

    /** Rejects the drag as a result of examining either the user drop action or the available data flavors. */
    public void rejectDrag() {
        getDropTargetContext().rejectDrag();
    }

    /** The location of the drag cursor's hot spot in Component coordinates. */
    private Point location;

    /** The source drop actions. */
    private int actions;

    /** The user drop action. */
    private int dropAction;
}
