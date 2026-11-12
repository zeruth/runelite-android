/*
 * Copyright (c) 1998, Oracle and/or its affiliates. All rights reserved.
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

/* ********************************************************************
 **********************************************************************
 **********************************************************************
 *** COPYRIGHT (c) Eastman Kodak Company, 1997                      ***
 *** As  an unpublished  work pursuant to Title 17 of the United    ***
 *** States Code.  All rights reserved.                             ***
 **********************************************************************
 **********************************************************************
 **********************************************************************/

package java.awt.image.renderable;
import java.awt.image.RenderedImage;
import java.awt.RenderingHints;

/**
 * The RenderedImageFactory interface (often abbreviated RIF) is
 * intended to be implemented by classes that wish to act as factories
 * to produce different renderings, for example by executing a series
 * of BufferedImageOps on a set of sources, depending on a specific
 * set of parameters, properties, and rendering hints.
 */
public interface RenderedImageFactory {

  /**
   * Creates a RenderedImage representing the results of an imaging
   * operation (or chain of operations) for a given ParameterBlock and
   * RenderingHints.  The RIF may also query any source images
   * referenced by the ParameterBlock for their dimensions,
   * SampleModels, properties, etc., as necessary.
   *
   * <p> The create() method can return null if the
   * RenderedImageFactory is not capable of producing output for the
   * given set of source images and parameters.
   *
   * @param paramBlock a ParameterBlock containing sources and parameters
   *        for the RenderedImage to be created.
   * @param hints a RenderingHints object containing hints.
   * @return A RenderedImage containing the desired output.
   */
  RenderedImage create(ParameterBlock paramBlock,
                       RenderingHints hints);
}
