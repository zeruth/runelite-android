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

package java.awt.print;

import java.awt.Graphics;


/**
 * The <code>Printable</code> interface is implemented
 * by the <code>print</code> methods of the current
 * page painter, which is called by the printing
 * system to render a page.  When building a
 * {@link Pageable}, pairs of {@link PageFormat}
 * instances and instances that implement
 * this interface are used to describe each page. The
 * instance implementing <code>Printable</code> is called to
 * print the page's graphics.
 *
 * @see java.awt.print.Pageable
 * @see java.awt.print.PageFormat
 * @see java.awt.print.PrinterJob
 */
public interface Printable {

    /**
     * Returned from {@link #print(Graphics, PageFormat, int)}
     * to signify that the requested page was rendered.
     */
    int PAGE_EXISTS = 0;

    /**
     * Returned from <code>print</code> to signify that the
     * <code>pageIndex</code> is too large and that the requested page
     * does not exist.
     */
    int NO_SUCH_PAGE = 1;

    /**
     * Prints the page at the specified index into the specified
     * {@link Graphics} context in the specified
     * format.
     *
     * @param graphics the context into which the page is drawn
     * @param pageFormat the size and orientation of the page being drawn
     * @param pageIndex the zero based index of the page to be drawn
     * @return PAGE_EXISTS if the page is rendered successfully
     *         or NO_SUCH_PAGE if <code>pageIndex</code> specifies a
     *         non-existent page.
     * @exception java.awt.print.PrinterException
     *         thrown when the print job is terminated.
     */
    int print(Graphics graphics, PageFormat pageFormat, int pageIndex)
                 throws PrinterException;

}
