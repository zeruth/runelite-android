/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.image.ColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageProducer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.util.Enumeration;
import java.util.Vector;

/**
 * An adapter class that implements ImageProducer to allow the
 * asynchronous production of a RenderableImage.  The size of the
 * ImageConsumer is determined by the scale factor of the usr2dev
 * transform in the RenderContext.  If the RenderContext is null, the
 * default rendering of the RenderableImage is used.  This class
 * implements an asynchronous production that produces the image in
 * one thread at one resolution.  This class may be subclassed to
 * implement versions that will render the image using several
 * threads.  These threads could render either the same image at
 * progressively better quality, or different sections of the image at
 * a single resolution.
 */
public class RenderableImageProducer implements ImageProducer, Runnable {

    /** The RenderableImage source for the producer. */
    RenderableImage rdblImage;

    /** The RenderContext to use for producing the image. */
    RenderContext rc;

    /** A Vector of image consumers. */
    Vector<ImageConsumer> ics = new Vector<>();

    /**
     * Constructs a new RenderableImageProducer from a RenderableImage
     * and a RenderContext.
     *
     * @param rdblImage the RenderableImage to be rendered.
     * @param rc the RenderContext to use for producing the pixels.
     */
    public RenderableImageProducer(RenderableImage rdblImage,
                                   RenderContext rc) {
        this.rdblImage = rdblImage;
        this.rc = rc;
    }

    /**
     * Sets a new RenderContext to use for the next startProduction() call.
     *
     * @param rc the new RenderContext.
     */
    public synchronized void setRenderContext(RenderContext rc) {
        this.rc = rc;
    }

   /**
     * Adds an ImageConsumer to the list of consumers interested in
     * data for this image.
     *
     * @param ic an ImageConsumer to be added to the interest list.
     */
    public synchronized void addConsumer(ImageConsumer ic) {
        if (!ics.contains(ic)) {
            ics.addElement(ic);
        }
    }

    /**
     * Determine if an ImageConsumer is on the list of consumers
     * currently interested in data for this image.
     *
     * @param ic the ImageConsumer to be checked.
     * @return true if the ImageConsumer is on the list; false otherwise.
     */
    public synchronized boolean isConsumer(ImageConsumer ic) {
        return ics.contains(ic);
    }

    /**
     * Remove an ImageConsumer from the list of consumers interested in
     * data for this image.
     *
     * @param ic the ImageConsumer to be removed.
     */
    public synchronized void removeConsumer(ImageConsumer ic) {
        ics.removeElement(ic);
    }

    /**
     * Adds an ImageConsumer to the list of consumers interested in
     * data for this image, and immediately starts delivery of the
     * image data through the ImageConsumer interface.
     *
     * @param ic the ImageConsumer to be added to the list of consumers.
     */
    public synchronized void startProduction(ImageConsumer ic) {
        addConsumer(ic);
        // Need to build a runnable object for the Thread.
        Thread thread = new Thread(this, "RenderableImageProducer Thread");
        thread.start();
    }

    /**
     * Requests that a given ImageConsumer have the image data delivered
     * one more time in top-down, left-right order.
     *
     * @param ic the ImageConsumer requesting the resend.
     */
    public void requestTopDownLeftRightResend(ImageConsumer ic) {
        // Deliberate no-op, as in OpenJDK: run() already delivers
        // TOPDOWNLEFTRIGHT in a single pass, so there is nothing to resend.
    }

    /**
     * The runnable method for this class. This will produce an image using
     * the current RenderableImage and RenderContext and send it to all the
     * ImageConsumer currently registered with this class.
     */
    public void run() {
        // First get the rendered image
        RenderedImage rdrdImage;
        if (rc != null) {
            rdrdImage = rdblImage.createRendering(rc);
        } else {
            rdrdImage = rdblImage.createDefaultRendering();
        }

        // And its ColorModel
        ColorModel colorModel = rdrdImage.getColorModel();
        Raster raster = rdrdImage.getData();
        if (colorModel == null) {
            colorModel = ColorModel.getRGBdefault();
        }
        int minX = raster.getMinX();
        int minY = raster.getMinY();
        int width = raster.getWidth();
        int height = raster.getHeight();

        Enumeration<ImageConsumer> icList;
        ImageConsumer ic;
        // Set up the ImageConsumers
        icList = ics.elements();
        while (icList.hasMoreElements()) {
            ic = icList.nextElement();
            ic.setDimensions(width,height);
            ic.setHints(ImageConsumer.TOPDOWNLEFTRIGHT |
                        ImageConsumer.COMPLETESCANLINES |
                        ImageConsumer.SINGLEPASS |
                        ImageConsumer.SINGLEFRAME);
        }

        // OpenJDK hands consumers the source ColorModel's packed pixel
        // (ColorModel.getDataElement); converting through getRGB and the
        // default RGB model delivers the same colours to any consumer and
        // works for every ColorModel the shadow backs.
        ColorModel rgb = ColorModel.getRGBdefault();
        int pix[] = new int[width];
        Object element = null;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                element = raster.getDataElements(minX + i, minY + j, element);
                pix[i] = colorModel.getRGB(element);
            }
            // Now send the scanline to the Consumers
            icList = ics.elements();
            while (icList.hasMoreElements()) {
                ic = icList.nextElement();
                ic.setPixels(0, j, width, 1, rgb, pix, 0, width);
            }
        }

        // Now tell the consumers we're done.
        icList = ics.elements();
        while (icList.hasMoreElements()) {
            ic = icList.nextElement();
            ic.imageComplete(ImageConsumer.STATICIMAGEDONE);
        }
    }
}
