/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.image;

import java.util.Hashtable;

/**
 * This class implements a filter for the set of interface methods that
 * are used to deliver data from an ImageProducer to an ImageConsumer.
 * It is meant to be used in conjunction with a FilteredImageSource
 * object to produce filtered versions of existing images.  It is a
 * base class that provides the calls needed to implement a "Null filter"
 * which has no effect on the data being passed through.  Filters should
 * subclass this class and override the methods which deal with the
 * data that needs to be filtered and modify it as necessary.
 *
 * @see FilteredImageSource
 * @see ImageConsumer
 *
 * @author      Jim Graham
 */
public class ImageFilter implements ImageConsumer, Cloneable {
    /**
     * The consumer of the particular image data stream for which this
     * instance of the ImageFilter is filtering data.  It is not
     * initialized during the constructor, but rather during the
     * getFilterInstance() method call when the FilteredImageSource
     * is creating a unique instance of this object for a particular
     * image data stream.
     *
     * @see #getFilterInstance
     * @see ImageConsumer
     */
    protected ImageConsumer consumer;

    /**
     * Returns a unique instance of an ImageFilter object which will
     * actually perform the filtering for the specified ImageConsumer.
     * The default implementation just clones this object.
     *
     * @param ic the specified <code>ImageConsumer</code>
     * @return an <code>ImageFilter</code> used to perform the
     *         filtering for the specified <code>ImageConsumer</code>.
     */
    public ImageFilter getFilterInstance(ImageConsumer ic) {
        ImageFilter instance = (ImageFilter) clone();
        instance.consumer = ic;
        return instance;
    }

    /**
     * Filters the information provided in the setDimensions method
     * of the ImageConsumer interface.
     *
     * @see ImageConsumer#setDimensions
     */
    public void setDimensions(int width, int height) {
        consumer.setDimensions(width, height);
    }

    /**
     * Passes the properties from the source object along after adding a
     * property indicating the stream of filters it has been run through.
     *
     * @param props the properties from the source object
     * @exception NullPointerException if <code>props</code> is null
     */
    public void setProperties(Hashtable<?,?> props) {
        @SuppressWarnings("unchecked")
        Hashtable<Object,Object> p = (Hashtable<Object,Object>) props.clone();
        Object o = p.get("filters");
        if (o == null) {
            p.put("filters", toString());
        } else if (o instanceof String) {
            p.put("filters", ((String) o) + toString());
        }
        consumer.setProperties(p);
    }

    /**
     * Filter the information provided in the setColorModel method
     * of the ImageConsumer interface.
     *
     * @see ImageConsumer#setColorModel
     */
    public void setColorModel(ColorModel model) {
        consumer.setColorModel(model);
    }

    /**
     * Filters the information provided in the setHints method
     * of the ImageConsumer interface.
     *
     * @see ImageConsumer#setHints
     */
    public void setHints(int hints) {
        consumer.setHints(hints);
    }

    /**
     * Filters the information provided in the setPixels method of the
     * ImageConsumer interface which takes an array of bytes.
     *
     * @see ImageConsumer#setPixels
     */
    public void setPixels(int x, int y, int w, int h,
                          ColorModel model, byte pixels[], int off,
                          int scansize) {
        consumer.setPixels(x, y, w, h, model, pixels, off, scansize);
    }

    /**
     * Filters the information provided in the setPixels method of the
     * ImageConsumer interface which takes an array of integers.
     *
     * @see ImageConsumer#setPixels
     */
    public void setPixels(int x, int y, int w, int h,
                          ColorModel model, int pixels[], int off,
                          int scansize) {
        consumer.setPixels(x, y, w, h, model, pixels, off, scansize);
    }

    /**
     * Filters the information provided in the imageComplete method of
     * the ImageConsumer interface.
     *
     * @see ImageConsumer#imageComplete
     */
    public void imageComplete(int status) {
        consumer.imageComplete(status);
    }

    /**
     * Responds to a request for a TopDownLeftRight (TDLR) ordered resend
     * of the pixel data from an <code>ImageConsumer</code>.  The default
     * implementation forwards the request to the source, which is what a
     * filter that does not reorder pixels wants.
     *
     * @param ip the ImageProducer that is feeding this instance of
     * the filter - also the ImageProducer that the request should be
     * forwarded to if necessary
     * @exception NullPointerException if <code>ip</code> is null
     */
    public void resendTopDownLeftRight(ImageProducer ip) {
        ip.requestTopDownLeftRightResend(this);
    }

    /**
     * Clones this object.
     */
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }
}
