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

/**
 * The <code>DragSourceDropEvent</code> is delivered from the
 * <code>DragSourceContext</code> to the <code>dragDropEnd</code> method of
 * <code>DragSourceListener</code>s registered with that
 * <code>DragSourceContext</code> and with its associated
 * <code>DragSource</code>. It contains sufficient information for the
 * originator of the operation to provide appropriate feedback to the end
 * user when the operation completes.
 *
 * @since 1.2
 */
public class DragSourceDropEvent extends DragSourceEvent {

    private static final long serialVersionUID = -5571321229470821891L;

    /** <code>true</code> if the drop was successful. */
    private boolean dropSuccess;

    /** The drop action. */
    private int dropAction = DnDConstants.ACTION_NONE;

    /**
     * Construct a <code>DragSourceDropEvent</code> for a drop, given the
     * <code>DragSourceContext</code>, the drop action, and a
     * <code>boolean</code> indicating if the drop was successful.
     *
     * @param dsc the <code>DragSourceContext</code> associated with this event.
     * @param action the drop action.
     * @param success a boolean indicating if the drop was successful.
     * @throws IllegalArgumentException if <code>dsc</code> is <code>null</code>.
     */
    public DragSourceDropEvent(DragSourceContext dsc, int action, boolean success) {
        super(dsc);
        dropSuccess = success;
        dropAction = action;
    }

    /**
     * Construct a <code>DragSourceDropEvent</code> for a drop, given the
     * <code>DragSourceContext</code>, the drop action, a <code>boolean</code>
     * indicating if the drop was successful, and coordinates.
     *
     * @param dsc the <code>DragSourceContext</code> associated with this event.
     * @param action the drop action.
     * @param success a boolean indicating if the drop was successful.
     * @param x the horizontal coordinate for the cursor location
     * @param y the vertical coordinate for the cursor location
     * @throws IllegalArgumentException if <code>dsc</code> is <code>null</code>.
     * @since 1.4
     */
    public DragSourceDropEvent(DragSourceContext dsc, int action,
                               boolean success, int x, int y) {
        super(dsc, x, y);
        dropSuccess = success;
        dropAction = action;
    }

    /**
     * Construct a <code>DragSourceDropEvent</code> for a drag that does not
     * result in a drop. The coordinates are not specified.
     *
     * @param dsc the <code>DragSourceContext</code>
     * @throws IllegalArgumentException if <code>dsc</code> is <code>null</code>.
     */
    public DragSourceDropEvent(DragSourceContext dsc) {
        super(dsc);
        dropSuccess = false;
    }

    /**
     * This method returns a <code>boolean</code> indicating if the drop was
     * successful.
     *
     * @return <code>true</code> if the drop target accepted the drop and
     *         successfully performed a drop action
     */
    public boolean getDropSuccess() {
        return dropSuccess;
    }

    /**
     * This method returns an <code>int</code> representing the action
     * performed by the target on the subject of the drop.
     *
     * @return the action performed by the target on the subject of the drop
     *         if the drop target accepted the drop; otherwise
     *         <code>DnDConstants.ACTION_NONE</code>.
     */
    public int getDropAction() {
        return dropAction;
    }
}
