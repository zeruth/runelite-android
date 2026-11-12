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

import java.awt.Insets;
import java.awt.Point;

/**
 * During DnD operations it is possible that a user may wish to drop the
 * subject of the operation on a region of a scrollable GUI control that is
 * not currently visible to the user. A component that implements this
 * interface is scrolled by its {@link DropTarget}'s autoscroller while the
 * drag cursor rests in the autoscroll region, the band between the
 * component's bounds and its {@link #getAutoscrollInsets() insets}.
 *
 * @since 1.2
 */
public interface Autoscroll {

    /**
     * This method returns the <code>Insets</code> describing
     * the autoscrolling region or border relative
     * to the geometry of the implementing Component.
     *
     * @return the Insets
     */
    public Insets getAutoscrollInsets();

    /**
     * notify the <code>Component</code> to autoscroll
     *
     * @param cursorLocn A <code>Point</code> indicating the
     * location of the cursor that triggered this operation.
     */
    public void autoscroll(Point cursorLocn);
}
