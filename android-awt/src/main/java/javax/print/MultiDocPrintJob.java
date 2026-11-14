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

import javax.print.attribute.PrintRequestAttributeSet;

/**
 * Obtained from a MultiDocPrintService, a MultiDocPrintJob can
 * print a specified collection of documents as a single print job with
 * a set of job attributes.
 */
public interface MultiDocPrintJob extends DocPrintJob {

    /**
     * Print a MultiDoc with a set of job attributes.
     * @param multiDoc The documents to be printed. ALL must be a flavor
     * supported by the PrintJob &amp; PrintService.
     * @param attributes The attributes to be applied to this print job.
     *        If this parameter is null then the default attributes are used.
     * @throws PrintException The exception additionally may implement
     *                           an interfaces which more precisely describes
     *                           the cause of the exception
     */
    public void print(MultiDoc multiDoc, PrintRequestAttributeSet attributes)
                throws PrintException;

}
