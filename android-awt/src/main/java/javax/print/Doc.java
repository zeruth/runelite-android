/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;

import javax.print.attribute.DocAttributeSet;


/**
 * Interface Doc specifies the interface for an object that supplies one piece
 * of print data for a Print Job. "Doc" is a short, easy-to-pronounce term
 * that means "a piece of print data." The client passes to the Print Job an
 * object that implements interface Doc, and the Print Job calls methods on
 * that object to obtain the print data.
 */
public interface Doc {

    /**
     * Determines the doc flavor in which this doc object will supply its
     * piece of print data.
     *
     * @return  Doc flavor.
     */
    public DocFlavor getDocFlavor();

    /**
     * Obtains the print data representation object that contains this doc
     * object's piece of print data in the format corresponding to the
     * supported doc flavor.
     *
     * @return  Print data representation object.
     *
     * @exception  IOException
     *     Thrown if the representation class is a stream and there was an I/O
     *     error while constructing the stream.
     */
    public Object getPrintData() throws IOException;

    /**
     * Obtains the set of printing attributes for this doc object. If the
     * returned attribute set includes an instance of a particular attribute
     * <I>X,</I> the printer must use that attribute value for this doc,
     * overriding any value of attribute <I>X</I> in the job's attribute set.
     *
     * @return  Unmodifiable set of printing attributes for this doc, or null
     *          to obtain all attribute values from the job's attribute
     *          set.
     */
    public DocAttributeSet getAttributes();

    /**
     * Obtains a reader for extracting character print data from this doc.
     *
     * @return  Reader for extracting the print data from this doc.
     *
     * @exception  IOException
     *     Thrown if there was an I/O error while creating the reader.
     */
    public Reader getReaderForText() throws IOException;

    /**
     * Obtains an input stream for extracting byte print data from this
     * doc.
     *
     * @return  Input stream for extracting the print data from this doc.
     *
     * @exception  IOException
     *     Thrown if there was an I/O error while creating the input stream.
     */
    public InputStream getStreamForBytes() throws IOException;

}
