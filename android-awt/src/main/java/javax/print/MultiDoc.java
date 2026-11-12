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

import java.io.IOException;

/**
 * Interface MultiDoc specifies the interface for an object that supplies more
 * than one piece of print data for a Print Job. "Doc" is a short,
 * easy-to-pronounce term that means "a piece of print data," and a "multidoc"
 * is a group of several docs.
 */
public interface MultiDoc {


    /**
     * Obtain the current doc object.
     *
     * @return  Current doc object.
     *
     * @exception  IOException
     *     Thrown if a error ocurred reading the document.
     */
    public Doc getDoc() throws IOException;

    /**
     * Go to the multidoc object that contains the next doc object in the
     * sequence of doc objects.
     *
     * @return  Multidoc object containing the next doc object, or null if
     * there are no further doc objects.
     *
     * @exception  IOException
     *     Thrown if an error occurred locating the next document
     */
    public MultiDoc next() throws IOException;

}
