/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.cmm;

import java.awt.color.ColorSpace;

import net.runelite.awt.impl.MatrixCMM;

/**
 * Lookup of the colour management module.  Desktop loads lcms through a
 * {@code CMMServiceProvider}; Android has no native CMM, so the module is
 * always {@link MatrixCMM}, a Java implementation that evaluates matrix/TRC
 * profiles exactly and approximates every other profile type (see its
 * class comment).
 */
public class CMSManager {
    public static ColorSpace GRAYspace;       // These two fields allow access
    public static ColorSpace LINEAR_RGBspace; // to java.awt.color.ColorSpace
                                              // private fields from other
                                              // packages.  The fields are set
                                              // by java.awt.color.ColorSpace
                                              // and read by
                                              // java.awt.image.ColorModel.

    private static PCMM cmmImpl = null;

    public static synchronized PCMM getModule() {
        if (cmmImpl != null) {
            return cmmImpl;
        }
        cmmImpl = MatrixCMM.INSTANCE;
        return cmmImpl;
    }

    static synchronized boolean canCreateModule() {
        return (cmmImpl == null);
    }
}
