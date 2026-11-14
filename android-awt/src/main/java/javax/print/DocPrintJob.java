/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

package javax.print;

import javax.print.attribute.PrintJobAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.event.PrintJobAttributeListener;
import javax.print.event.PrintJobListener;

/**
 *
 * This interface represents a print job that can print a specified
 * document with a set of job attributes.  An object implementing
 * this interface is obtained from a print service.
 *
 */

public interface DocPrintJob {

    /**
     * Determines the {@link PrintService} object to which this print job
     * object is bound.
     *
     * @return  <code>PrintService</code> object.
     *
     */
    public PrintService getPrintService();

    /**
     * Obtains this Print Job's set of printing attributes.
     *
     * @return  the print job attributes
     */
     public PrintJobAttributeSet getAttributes();

    /**
     * Registers a listener for event occurring during this print job.
     *
     * @param listener  The object implementing the listener interface
     *
     * @see #removePrintJobListener
     */
    public void addPrintJobListener(PrintJobListener listener);

    /**
     * Removes a listener from this print job.
     *
     * @param listener  The object implementing the listener interface
     *
     * @see #addPrintJobListener
     */
    public void removePrintJobListener(PrintJobListener listener);

    /**
     * Registers a listener for changes in the specified attributes.
     *
     * @param listener  The object implementing the listener interface
     * @param attributes The attributes to listen on, or null to mean
     * all attributes that can change, as determined by the job.
     * @see #removePrintJobAttributeListener
     */
    public void addPrintJobAttributeListener(
                                  PrintJobAttributeListener listener,
                                  PrintJobAttributeSet attributes);

    /**
     * Removes an attribute listener from this print job.
     *
     * @param listener  The object implementing the listener interface
     * @see #addPrintJobAttributeListener
     */
    public void removePrintJobAttributeListener(
                                      PrintJobAttributeListener listener);

    /**
     * Prints a document with the specified job attributes.
     *
     * @param doc       The document to be printed. If must be a flavor
     *                                  supported by this PrintJob.
     *
     * @param attributes The job attributes to be applied to this print job.
     *        If this parameter is null then the default attributes are used.
     * @throws PrintException The exception additionally may implement
     * an interface that more precisely describes the cause of the
     * exception
     */
    public void print(Doc doc, PrintRequestAttributeSet attributes)
          throws PrintException;

}
