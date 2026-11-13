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

package java.awt.image;

import java.awt.AlphaComposite;
import java.awt.geom.Rectangle2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;

/**
 * This class implements a convolution from the source
 * to the destination.
 * Convolution using a convolution kernel is a spatial operation that
 * computes the output pixel from an input pixel by multiplying the kernel
 * with the surround of the input pixel.
 * This allows the output pixel to be affected by the immediate neighborhood
 * in a way that can be mathematically specified with a kernel.
 *<p>
 * This class operates with BufferedImage data in which color components are
 * premultiplied with the alpha component.  If the Source BufferedImage has
 * an alpha component, and the color components are not premultiplied with
 * the alpha component, then the data are premultiplied before being
 * convolved.  If the Destination has color components which are not
 * premultiplied, then alpha is divided out before storing into the
 * Destination (if alpha is 0, the color components are set to 0).  If the
 * Destination has no alpha component, then the resulting alpha is discarded
 * after first dividing it out of the color components.
 * <p>
 * Rasters are treated as having no alpha channel.  If the above treatment
 * of the alpha channel in BufferedImages is not desired, it may be avoided
 * by getting the Raster of a source BufferedImage and using the filter method
 * of this class which works with Rasters.
 * <p>
 * If a RenderingHints object is specified in the constructor, the
 * color rendering hint and the dithering hint may be used when color
 * conversion is required.
 *<p>
 * Note that the Source and the Destination may not be the same object.
 * @see Kernel
 * @see java.awt.RenderingHints#KEY_COLOR_RENDERING
 * @see java.awt.RenderingHints#KEY_DITHERING
 */
public class ConvolveOp implements BufferedImageOp, RasterOp {
    Kernel kernel;
    int edgeHint;
    RenderingHints hints;
    /**
     * Edge condition constants.
     */

    /**
     * Pixels at the edge of the destination image are set to zero.  This
     * is the default.
     */

    public static final int EDGE_ZERO_FILL = 0;

    /**
     * Pixels at the edge of the source image are copied to
     * the corresponding pixels in the destination without modification.
     */
    public static final int EDGE_NO_OP     = 1;

    /**
     * Constructs a ConvolveOp given a Kernel, an edge condition, and a
     * RenderingHints object (which may be null).
     * @param kernel the specified <code>Kernel</code>
     * @param edgeCondition the specified edge condition
     * @param hints the specified <code>RenderingHints</code> object
     * @see Kernel
     * @see #EDGE_NO_OP
     * @see #EDGE_ZERO_FILL
     * @see java.awt.RenderingHints
     */
    public ConvolveOp(Kernel kernel, int edgeCondition, RenderingHints hints) {
        this.kernel   = kernel;
        this.edgeHint = edgeCondition;
        this.hints    = hints;
    }

    /**
     * Constructs a ConvolveOp given a Kernel.  The edge condition
     * will be EDGE_ZERO_FILL.
     * @param kernel the specified <code>Kernel</code>
     * @see Kernel
     * @see #EDGE_ZERO_FILL
     */
    public ConvolveOp(Kernel kernel) {
        this.kernel   = kernel;
        this.edgeHint = EDGE_ZERO_FILL;
    }

    /**
     * Returns the edge condition.
     * @return the edge condition of this <code>ConvolveOp</code>.
     * @see #EDGE_NO_OP
     * @see #EDGE_ZERO_FILL
     */
    public int getEdgeCondition() {
        return edgeHint;
    }

    /**
     * Returns the Kernel.
     * @return the <code>Kernel</code> of this <code>ConvolveOp</code>.
     */
    public final Kernel getKernel() {
        return (Kernel) kernel.clone();
    }

    /**
     * Performs a convolution on BufferedImages.  Each component of the
     * source image will be convolved (including the alpha component, if
     * present).
     * If the color model in the source image is not the same as that
     * in the destination image, the pixels will be converted
     * in the destination.  If the destination image is null,
     * a BufferedImage will be created with the source ColorModel.
     * The IllegalArgumentException may be thrown if the source is the
     * same as the destination.
     * @param src the source <code>BufferedImage</code> to filter
     * @param dst the destination <code>BufferedImage</code> for the
     *        filtered <code>src</code>
     * @return the filtered <code>BufferedImage</code>
     * @throws NullPointerException if <code>src</code> is <code>null</code>
     * @throws IllegalArgumentException if <code>src</code> equals
     *         <code>dst</code>
     * @throws ImagingOpException if <code>src</code> cannot be filtered
     */
    public final BufferedImage filter (BufferedImage src, BufferedImage dst) {
        if (src == null) {
            throw new NullPointerException("src image is null");
        }
        if (src == dst) {
            throw new IllegalArgumentException("src image cannot be the "+
                                               "same as the dst image");
        }

        boolean needToConvert = false;
        ColorModel srcCM = src.getColorModel();
        ColorModel dstCM;
        BufferedImage origDst = dst;

        // Can't convolve an IndexColorModel.  Need to expand it
        if (srcCM instanceof IndexColorModel) {
            IndexColorModel icm = (IndexColorModel) srcCM;
            src = icm.convertToIntDiscrete(src.getRaster(), false);
            srcCM = src.getColorModel();
        }

        if (dst == null) {
            dst = createCompatibleDestImage(src, null);
            dstCM = srcCM;
            origDst = dst;
        }
        else {
            dstCM = dst.getColorModel();
            if (srcCM.getColorSpace().getType() !=
                dstCM.getColorSpace().getType())
            {
                needToConvert = true;
                dst = createCompatibleDestImage(src, null);
                dstCM = dst.getColorModel();
            }
            else if (dstCM instanceof IndexColorModel) {
                dst = createCompatibleDestImage(src, null);
                dstCM = dst.getColorModel();
            }
        }

        // Desktop hands this to libmlib_image, which convolves the raw raster bands
        // (alpha included, nothing premultiplied despite the class comment) and
        // converts the result into a destination of another layout afterwards.
        Raster srcRaster = src.getRaster();
        WritableRaster dstRaster = dst.getRaster();
        if (sameLayout(srcRaster, dstRaster)) {
            if (!convolve(srcRaster, dstRaster)) {
                throw new ImagingOpException ("Unable to convolve src image");
            }
        } else {
            BufferedImage tmp = createCompatibleDestImage(src, null);
            if (!convolve(srcRaster, tmp.getRaster())) {
                throw new ImagingOpException ("Unable to convolve src image");
            }
            java.awt.Graphics2D g = dst.createGraphics();
            try {
                g.setComposite(AlphaComposite.Src);
                g.drawImage(tmp, 0, 0, null);
            } finally {
                g.dispose();
            }
        }

        if (needToConvert || origDst != dst) {
            // Desktop uses ColorConvertOp for a colour space change; the shadow has no
            // CMM, so both conversions go through the image pipeline instead.
            java.awt.Graphics2D g = origDst.createGraphics();
            try {
                if (needToConvert) {
                    g.setComposite(AlphaComposite.Src);
                }
                g.drawImage(dst, 0, 0, null);
            } finally {
                g.dispose();
            }
        }

        return origDst;
    }

    /**
     * Performs a convolution on Rasters.  Each band of the source Raster
     * will be convolved.
     * The source and destination must have the same number of bands.
     * If the destination Raster is null, a new Raster will be created.
     * The IllegalArgumentException may be thrown if the source is
     * the same as the destination.
     * @param src the source <code>Raster</code> to filter
     * @param dst the destination <code>WritableRaster</code> for the
     *        filtered <code>src</code>
     * @return the filtered <code>WritableRaster</code>
     * @throws NullPointerException if <code>src</code> is <code>null</code>
     * @throws ImagingOpException if <code>src</code> and <code>dst</code>
     *         do not have the same number of bands
     * @throws ImagingOpException if <code>src</code> cannot be filtered
     * @throws IllegalArgumentException if <code>src</code> equals
     *         <code>dst</code>
     */
    public final WritableRaster filter (Raster src, WritableRaster dst) {
        if (dst == null) {
            dst = createCompatibleDestRaster(src);
        }
        else if (src == dst) {
            throw new IllegalArgumentException("src image cannot be the "+
                                               "same as the dst image");
        }
        else if (src.getNumBands() != dst.getNumBands()) {
            throw new ImagingOpException("Different number of bands in src "+
                                         " and dst Rasters");
        }

        if (!convolve(src, dst)) {
            throw new ImagingOpException ("Unable to convolve src image");
        }

        return dst;
    }

    /**
     * Creates a zeroed destination image with the correct size and number
     * of bands.  If destCM is null, an appropriate ColorModel will be used.
     * @param src       Source image for the filter operation.
     * @param destCM    ColorModel of the destination.  Can be null.
     * @return a destination <code>BufferedImage</code> with the correct
     *         size and number of bands.
     */
    public BufferedImage createCompatibleDestImage(BufferedImage src,
                                                   ColorModel destCM) {
        BufferedImage image;

        int w = src.getWidth();
        int h = src.getHeight();

        WritableRaster wr = null;

        if (destCM == null) {
            destCM = src.getColorModel();
            // Not much support for ICM
            if (destCM instanceof IndexColorModel) {
                destCM = ColorModel.getRGBdefault();
            } else {
                /* Create destination image as similar to the source
                 *  as it possible...
                 */
                wr = src.getData().createCompatibleWritableRaster(w, h);
            }
        }

        if (wr == null) {
            /* This is the case when destination color model
             * was explicitly specified (and it may be not compatible
             * with source raster structure) or source is indexed image.
             * We should use destination color model to create compatible
             * destination raster here.
             */
            wr = destCM.createCompatibleWritableRaster(w, h);
        }

        image = new BufferedImage (destCM, wr,
                                   destCM.isAlphaPremultiplied(), null);

        return image;
    }

    /**
     * Creates a zeroed destination Raster with the correct size and number
     * of bands, given this source.
     */
    public WritableRaster createCompatibleDestRaster(Raster src) {
        return src.createCompatibleWritableRaster();
    }

    /**
     * Returns the bounding box of the filtered destination image.  Since
     * this is not a geometric operation, the bounding box does not
     * change.
     */
    public final Rectangle2D getBounds2D(BufferedImage src) {
        return getBounds2D(src.getRaster());
    }

    /**
     * Returns the bounding box of the filtered destination Raster.  Since
     * this is not a geometric operation, the bounding box does not
     * change.
     */
    public final Rectangle2D getBounds2D(Raster src) {
        return src.getBounds();
    }

    /**
     * Returns the location of the destination point given a
     * point in the source.  If dstPt is non-null, it will
     * be used to hold the return value.  Since this is not a geometric
     * operation, the srcPt will equal the dstPt.
     */
    public final Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        if (dstPt == null) {
            dstPt = new Point2D.Float();
        }
        dstPt.setLocation(srcPt.getX(), srcPt.getY());

        return dstPt;
    }

    /**
     * Returns the rendering hints for this op.
     */
    public final RenderingHints getRenderingHints() {
        return hints;
    }

    /*
     * The pixel loops below reproduce what desktop gets from libmlib_image
     * (awt_ImagingLib.c convolveBI/convolveRaster, mlib_ImageConvKernelConvert.c,
     * mlib_ImageConv_8nw.c) bit for bit on 8-bit samples: the kernel is rotated by
     * 180 degrees and padded to odd dimensions, quantised to fixed point the way
     * mlib_ImageConvKernelConvert does for MLIB_BYTE, and the per-pixel sum is
     * stored through the same offset-and-shift that truncates towards minus
     * infinity.  Samples wider than 8 bits (mlib's 16-bit paths) use plain
     * floating point with the same truncation and a clamp to the band's range.
     */

    private static boolean sameLayout(Raster a, Raster b) {
        int n = a.getNumBands();
        if (n != b.getNumBands()) return false;
        for (int i = 0; i < n; i++) {
            if (a.getSampleModel().getSampleSize(i) != b.getSampleModel().getSampleSize(i)) return false;
        }
        return true;
    }

    private boolean convolve(Raster src, WritableRaster dst) {
        int kwidth = kernel.getWidth();
        int kheight = kernel.getHeight();
        float[] kern = kernel.getKernelData(null);
        int klen = kern.length;
        int w = ((kwidth & 1) == 0) ? kwidth + 1 : kwidth;
        int h = ((kheight & 1) == 0) ? kheight + 1 : kheight;

        // Flipped, padded copy; medialib correlates, so the flip makes it convolve.
        double[] dkern = new double[w * h];
        float kmax = kern[klen - 1];
        int i = klen - 1;
        for (int y = 0; y < kheight; y++) {
            for (int x = 0; x < kwidth; x++, i--) {
                dkern[y * w + x] = kern[i];
                if (kern[i] > kmax) {
                    kmax = kern[i];
                }
            }
        }
        if (kmax > (1 << 16)) {
            return false;
        }

        int numBands = src.getNumBands();
        if (numBands != dst.getNumBands()) return false;
        int width = src.getWidth();
        int height = src.getHeight();
        if (width != dst.getWidth() || height != dst.getHeight()) return false;
        boolean bytes = true;
        int[] dstMax = new int[numBands];
        for (int b = 0; b < numBands; b++) {
            int bits = dst.getSampleModel().getSampleSize(b);
            dstMax[b] = bits >= 31 ? Integer.MAX_VALUE : (1 << bits) - 1;
            if (bits != 8 || src.getSampleModel().getSampleSize(b) != 8) bytes = false;
        }

        long[] ikern = null;
        double scalef = 0;
        if (bytes) {
            int[] quantised = new int[w * h];
            int scale = quantiseByteKernel(dkern, w * h, quantised);
            if (scale < 0) {
                return false;
            }
            ikern = new long[w * h];
            for (int j = 0; j < quantised.length; j++) ikern[j] = quantised[j];
            scalef = Math.scalb(1.0, 24 - scale);
        }

        int dm = (w - 1) / 2;
        int dn = (h - 1) / 2;
        int sminX = src.getMinX(), sminY = src.getMinY();
        int dminX = dst.getMinX(), dminY = dst.getMinY();
        int x0 = dm, y0 = dn, x1 = width - (w - 1 - dm), y1 = height - (h - 1 - dn);
        boolean interior = width >= w && height >= h;

        for (int b = 0; b < numBands; b++) {
            if (interior) {
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int value;
                        if (bytes) {
                            long sum = 0;
                            for (int j = 0; j < h; j++) {
                                int sy = sminY + y + j - dn;
                                for (int ii = 0; ii < w; ii++) {
                                    long k = ikern[j * w + ii];
                                    if (k != 0) sum += k * src.getSample(sminX + x + ii - dm, sy, b);
                                }
                            }
                            // D2I/FROM_S32 of mlib_ImageConv_8nw.c: offset by -2^31, saturate to
                            // int, shift the top byte down and flip its sign bit.
                            double d = (double) sum * scalef - 2147483648.0;
                            value = (((int) d) >> 24 ^ 128) & 0xff;
                        } else {
                            double sum = 0;
                            for (int j = 0; j < h; j++) {
                                int sy = sminY + y + j - dn;
                                for (int ii = 0; ii < w; ii++) {
                                    double k = dkern[j * w + ii];
                                    if (k != 0) sum += k * src.getSample(sminX + x + ii - dm, sy, b);
                                }
                            }
                            double f = Math.floor(sum);
                            value = f <= 0 ? 0 : f >= dstMax[b] ? dstMax[b] : (int) f;
                        }
                        dst.setSample(dminX + x, dminY + y, b, value);
                    }
                }
            }
            // Edge rows and columns: MLIB_EDGE_DST_FILL_ZERO or MLIB_EDGE_DST_COPY_SRC.
            for (int y = 0; y < height; y++) {
                boolean edgeRow = !interior || y < y0 || y >= y1;
                for (int x = 0; x < width; x++) {
                    if (!edgeRow && x >= x0 && x < x1) {
                        x = x1 - 1;
                        continue;
                    }
                    int value = edgeHint == EDGE_NO_OP ? src.getSample(sminX + x, sminY + y, b) : 0;
                    dst.setSample(dminX + x, dminY + y, b, value);
                }
            }
        }
        return true;
    }

    /**
     * mlib_ImageConvKernelConvert for MLIB_BYTE on a non-SPARC build: returns the
     * scale (the coefficients are {@code ikernel / 2^scale}) or -1 where medialib
     * fails, which the caller turns into an ImagingOpException as desktop does.
     */
    private static int quantiseByteKernel(double[] fkernel, int count, int[] ikernel) {
        double sum_pos = 0, sum_neg = 0;
        for (int i = 0; i < count; i++) {
            if (fkernel[i] > 0) sum_pos += fkernel[i]; else sum_neg -= fkernel[i];
        }
        double sum = (sum_pos > sum_neg) ? sum_pos : sum_neg;
        int scale = Math.getExponent(sum);
        scale++;
        scale = 31 - scale;
        if (scale <= 16) return -1;
        if (scale > 31) scale = 31;

        // mlib_ImageConvVersion: the "C" version keeps full precision, the x86 one
        // rounds the coefficients to scale-8 bits first.
        double dscale = 1.0 / (1 << scale);
        boolean plain = (count * dscale * 32768.0) > 8;
        if (plain) {
            double norm = (1L << scale);
            for (int i = 0; i < count; i++) {
                ikernel[i] = (int) (fkernel[i] * norm);
            }
            return scale;
        }
        int scale1 = 8;
        double norm = (1L << (scale - scale1));
        for (int i = 0; i < count; i++) {
            if (fkernel[i] > 0) ikernel[i] = (int) (fkernel[i] * norm + 0.5);
            else ikernel[i] = (int) (fkernel[i] * norm - 0.5);
        }
        int isum_pos = 0, isum_neg = 0;
        for (int i = 0; i < count; i++) {
            if (ikernel[i] > 0) isum_pos += ikernel[i]; else isum_neg -= ikernel[i];
        }
        int isum = (isum_pos > isum_neg) ? isum_pos : isum_neg;
        boolean overflow = isum >= (1 << (31 - scale1));
        for (int i = 0; i < count; i++) {
            ikernel[i] = overflow ? ((int) (fkernel[i] * norm)) << scale1 : ikernel[i] << scale1;
        }
        return scale;
    }
}
