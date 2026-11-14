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

package javax.accessibility;

/**
 * This AccessibleSelection interface provides the standard mechanism for an
 * assistive technology to determine what the current selected children are,
 * as well as modify the selection set.
 *
 * @see Accessible
 * @see Accessible#getAccessibleContext
 * @see AccessibleContext
 * @see AccessibleContext#getAccessibleSelection
 *
 * @author      Peter Korn
 * @author      Hans Muller
 * @author      Willie Walker
 */
public interface AccessibleSelection {

    /**
     * Returns the number of Accessible children currently selected.
     * If no children are selected, the return value will be 0.
     */
    public int getAccessibleSelectionCount();

    /**
     * Returns an Accessible representing the specified selected child
     * of the object.  If there isn't a selection, or there are
     * fewer children selected than the integer passed in, the return
     * value will be null.
     */
    public Accessible getAccessibleSelection(int i);

    /** Determines if the current child of this object is selected. */
    public boolean isAccessibleChildSelected(int i);

    /** Adds the specified Accessible child of the object to the object's selection. */
    public void addAccessibleSelection(int i);

    /** Removes the specified child of the object from the object's selection. */
    public void removeAccessibleSelection(int i);

    /** Clears the selection in the object, so that no children in the object are selected. */
    public void clearAccessibleSelection();

    /** Causes every child of the object to be selected if the object supports multiple selections. */
    public void selectAllAccessibleSelection();
}
