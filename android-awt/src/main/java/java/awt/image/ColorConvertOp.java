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

/**********************************************************************
 **********************************************************************
 **********************************************************************
 *** COPYRIGHT (c) Eastman Kodak Company, 1997                      ***
 *** As  an unpublished  work pursuant to Title 17 of the United    ***
 *** States Code.  All rights reserved.                             ***
 **********************************************************************
 **********************************************************************
 **********************************************************************/
package java.awt.image;

import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * This class performs a pixel-by-pixel color conversion of the data in
 * the source image.  The resulting color values are scaled to the precision
 * of the destination image.  Color conversion can be specified
 * via an array of ColorSpace objects or an array of ICC_Profile objects.
 * <p>
 * If the source is a BufferedImage with premultiplied alpha, the
 * color components are divided by the alpha component before color conversion.
 * If the destination is a BufferedImage with premultiplied alpha, the
 * color components are multiplied by the alpha component after conversion.
 * Rasters are treated as having no alpha channel, i.e. all bands are
 * color bands.
 * <p>
 * If a RenderingHints object is specified in the constructor, the
 * color rendering hint and the dithering hint may be used to control
 * color conversion.
 * <p>
 * Note that Source and Destination may be the same object.
 * <p>
 * On Android there is no CMM: each step converts through D50 XYZ with the
 * matrix approximation of {@link ICC_ColorSpace} (exact for the built-in
 * spaces and for matrix/TRC profiles, {@link java.awt.color.CMMException}
 * for LUT-only profiles), and non-ICC color spaces convert through their
 * own {@code toCIEXYZ}/{@code fromCIEXYZ}.  The rendering hints do not
 * change the result.
 *
 * @see java.awt.RenderingHints#KEY_COLOR_RENDERING
 * @see java.awt.RenderingHints#KEY_DITHERING
 */
public class ColorConvertOp implements BufferedImageOp, RasterOp {
    private final ICC_Profile[] profileList;
    private final ColorSpace[] CSList;
    private final RenderingHints hints;
    private final boolean gotProfiles;

    /**
     * Constructs a new ColorConvertOp which will convert
     * from a source color space to a destination color space.
     * The RenderingHints argument may be null.
     * This Op can be used only with BufferedImages, and will convert
     * directly from the ColorSpace of the source image to that of the
     * destination.  The destination argument of the filter method
     * cannot be specified as null.
     * @param hints the <code>RenderingHints</code> object used to control
     *        the color conversion, or <code>null</code>
     */
    public ColorConvertOp(RenderingHints hints) {
        profileList = new ICC_Profile[0];
        CSList = null;
        gotProfiles = true;
        this.hints = hints;
    }

    /**
     * Constructs a new ColorConvertOp from a ColorSpace object.
     * The RenderingHints argument may be null.  This
     * Op can be used only with BufferedImages, and is primarily useful
     * when the {@link #filter(BufferedImage, BufferedImage) filter}
     * method is invoked with a destination argument of null.
     * In that case, the ColorSpace defines the destination color space
     * for the destination created by the filter method.  Otherwise, the
     * ColorSpace defines an intermediate space to which the source is
     * converted before being converted to the destination space.
     * @param cspace defines the destination <code>ColorSpace</code> or an
     *        intermediate <code>ColorSpace</code>
     * @param hints the <code>RenderingHints</code> object used to control
     *        the color conversion, or <code>null</code>
     * @throws NullPointerException if cspace is null
     */
    public ColorConvertOp(ColorSpace cspace, RenderingHints hints) {
        if (cspace == null) {
            throw new NullPointerException("ColorSpace cannot be null");
        }
        if (cspace instanceof ICC_ColorSpace) {
            profileList = new ICC_Profile[] {((ICC_ColorSpace) cspace).getProfile()};
            CSList = null;
            gotProfiles = true;
        } else {
            profileList = null;
            CSList = new ColorSpace[] {cspace};
            gotProfiles = false;
        }
        this.hints = hints;
    }

    /**
     * Constructs a new ColorConvertOp from two ColorSpace objects.
     * The RenderingHints argument may be null.
     * This Op is primarily useful for calling the filter method on
     * Rasters, in which case the two ColorSpaces define the operation
     * to be performed on the Rasters.  In that case, the number of bands
     * in the source Raster must match the number of components in
     * srcCspace, and the number of bands in the destination Raster
     * must match the number of components in dstCspace.  For BufferedImages,
     * the two ColorSpaces define intermediate spaces through which the
     * source is converted before being converted to the destination space.
     * @param srcCspace the source <code>ColorSpace</code>
     * @param dstCspace the destination <code>ColorSpace</code>
     * @param hints the <code>RenderingHints</code> object used to control
     *        the color conversion, or <code>null</code>
     * @throws NullPointerException if either srcCspace or dstCspace is null
     */
    public ColorConvertOp(ColorSpace srcCspace, ColorSpace dstCspace, RenderingHints hints) {
        if ((srcCspace == null) || (dstCspace == null)) {
            throw new NullPointerException("ColorSpaces cannot be null");
        }
        if ((srcCspace instanceof ICC_ColorSpace) && (dstCspace instanceof ICC_ColorSpace)) {
            profileList = new ICC_Profile[] {((ICC_ColorSpace) srcCspace).getProfile(),
                ((ICC_ColorSpace) dstCspace).getProfile()};
            CSList = null;
            gotProfiles = true;
        } else {
            profileList = null;
            CSList = new ColorSpace[] {srcCspace, dstCspace};
            gotProfiles = false;
        }
        this.hints = hints;
    }

    /**
     * Constructs a new ColorConvertOp from an array of ICC_Profiles.
     * The RenderingHints argument may be null.
     * The sequence of profiles may include profiles that represent color
     * spaces, profiles that represent effects, etc.  If the whole sequence
     * does not represent a well-defined color conversion, an exception is
     * thrown.
     * <p>For BufferedImages, if the ColorSpace
     * of the source BufferedImage does not match the requirements of the
     * first profile in the array,
     * the first conversion is to an appropriate ColorSpace.
     * If the requirements of the last profile in the array are not met
     * by the ColorSpace of the destination BufferedImage,
     * the last conversion is to the destination's ColorSpace.
     * <p>For Rasters, the number of bands in the source Raster must match
     * the requirements of the first profile in the array, and the
     * number of bands in the destination Raster must match the requirements
     * of the last profile in the array.  The array must have at least two
     * elements or calling the filter method for Rasters will throw an
     * IllegalArgumentException.
     * @param profiles the array of <code>ICC_Profile</code> objects
     * @param hints the <code>RenderingHints</code> object used to control
     *        the color conversion, or <code>null</code>
     * @exception IllegalArgumentException when the profile sequence does not
     *             specify a well-defined color conversion
     * @exception NullPointerException if profiles is null
     */
    public ColorConvertOp(ICC_Profile[] profiles, RenderingHints hints) {
        if (profiles == null) {
            throw new NullPointerException("Profiles cannot be null");
        }
        gotProfiles = true;
        profileList = new ICC_Profile[profiles.length];
        for (int i1 = 0; i1 < profiles.length; i1++) {
            profileList[i1] = profiles[i1];
        }
        CSList = null;
        this.hints = hints;
    }

    /**
     * Returns the array of ICC_Profiles used to construct this ColorConvertOp.
     * Returns null if the ColorConvertOp was not constructed from such an
     * array.
     * @return the array of <code>ICC_Profile</code> objects of this
     *         <code>ColorConvertOp</code>, or <code>null</code> if this
     *         <code>ColorConvertOp</code> was not constructed with an
     *         array of <code>ICC_Profile</code> objects.
     */
    public final ICC_Profile[] getICC_Profiles() {
        if (gotProfiles) {
            ICC_Profile[] profiles = new ICC_Profile[profileList.length];
            for (int i1 = 0; i1 < profileList.length; i1++) {
                profiles[i1] = profileList[i1];
            }
            return profiles;
        }
        return null;
    }

    /** The op's own spaces in order: profiles wrapped as ICC_ColorSpace, or the ColorSpace list. */
    private List<ColorSpace> opSpaces() {
        List<ColorSpace> spaces = new ArrayList<ColorSpace>();
        if (gotProfiles) {
            for (ICC_Profile profile : profileList) {
                spaces.add(spaceOf(profile));
            }
        } else {
            for (ColorSpace cs : CSList) {
                spaces.add(cs);
            }
        }
        return spaces;
    }

    private static ColorSpace spaceOf(ICC_Profile profile) {
        // The standard profiles map back to the shared spaces so isCS_sRGB and identity checks hold.
        int[] standard = {ColorSpace.CS_sRGB, ColorSpace.CS_LINEAR_RGB, ColorSpace.CS_GRAY, ColorSpace.CS_CIEXYZ,
            ColorSpace.CS_PYCC};
        for (int cs : standard) {
            if (profile == ICC_Profile.getInstance(cs)) {
                return ColorSpace.getInstance(cs);
            }
        }
        return new ICC_ColorSpace(profile);
    }

    private static boolean same(ColorSpace a, ColorSpace b) {
        if (a == b) {
            return true;
        }
        return a instanceof ICC_ColorSpace && b instanceof ICC_ColorSpace
            && ((ICC_ColorSpace) a).getProfile() == ((ICC_ColorSpace) b).getProfile();
    }

    /** The conversion steps: consecutive repeats of a space are one step. */
    private static ColorSpace[] chain(ColorSpace first, List<ColorSpace> middle, ColorSpace last) {
        List<ColorSpace> steps = new ArrayList<ColorSpace>();
        if (first != null) {
            steps.add(first);
        }
        for (ColorSpace cs : middle) {
            if (steps.isEmpty() || !same(steps.get(steps.size() - 1), cs)) {
                steps.add(cs);
            }
        }
        if (last != null && (steps.isEmpty() || !same(steps.get(steps.size() - 1), last))) {
            steps.add(last);
        }
        return steps.toArray(new ColorSpace[steps.size()]);
    }

    private static float[] convert(ColorSpace[] steps, float[] components) {
        if (steps.length < 2) {
            return components.clone();
        }
        float[] xyz = steps[0].toCIEXYZ(components);
        for (int i = 1; i < steps.length - 1; i++) {
            xyz = steps[i].toCIEXYZ(steps[i].fromCIEXYZ(xyz));
        }
        return steps[steps.length - 1].fromCIEXYZ(xyz);
    }

    /**
     * ColorConverts the source BufferedImage.
     * If the destination image is null,
     * a BufferedImage will be created with an appropriate ColorModel.
     * @param src the source <code>BufferedImage</code> to be converted
     * @param dest the destination <code>BufferedImage</code>,
     *        or <code>null</code>
     * @return <code>dest</code> color converted from <code>src</code>
     *         or a new, converted <code>BufferedImage</code>
     *         if <code>dest</code> is <code>null</code>
     * @exception IllegalArgumentException if dest is null and this op was
     *             constructed using the constructor which takes only a
     *             RenderingHints argument, since the operation is ill defined.
     */
    public final BufferedImage filter(BufferedImage src, BufferedImage dest) {
        if (dest == null) {
            dest = createCompatibleDestImage(src, null);
        } else if (src.getWidth() != dest.getWidth() || src.getHeight() != dest.getHeight()) {
            throw new IllegalArgumentException("Width or height of BufferedImages do not match");
        }
        ImageAccess in = new ImageAccess(src), out = new ImageAccess(dest);
        ColorSpace[] steps = chain(in.space, opSpaces(), out.space);
        int w = src.getWidth(), h = src.getHeight();
        float[] pixel = new float[in.colors + 1];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                in.read(x, y, pixel);
                float[] color = new float[in.colors];
                System.arraycopy(pixel, 0, color, 0, in.colors);
                out.write(x, y, convert(steps, color), pixel[in.colors]);
            }
        }
        return dest;
    }

    /**
     * Reads and writes the pixels of an image as color components in its
     * color space (un-premultiplied) plus a 0..1 alpha.  Images that are not
     * component images (packed and indexed) are sRGB by their ColorModel.
     */
    private static final class ImageAccess {
        final BufferedImage image;
        final WritableRaster raster;
        final ColorSpace space;
        final boolean component;
        final int colors;
        final boolean alpha;
        final boolean premultiplied;
        final double[] maxSample;
        final float[] min;
        final float[] range;

        ImageAccess(BufferedImage image) {
            this.image = image;
            this.raster = image.getRaster();
            ColorModel cm = image.getColorModel();
            component = cm instanceof ComponentColorModel;
            space = component ? cm.getColorSpace() : ColorSpace.getInstance(ColorSpace.CS_sRGB);
            colors = space.getNumComponents();
            alpha = cm.hasAlpha();
            premultiplied = component && cm.isAlphaPremultiplied();
            int bands = colors + (alpha ? 1 : 0);
            maxSample = new double[bands];
            min = new float[colors];
            range = new float[colors];
            if (component) {
                for (int b = 0; b < bands; b++) {
                    maxSample[b] = (1L << cm.getComponentSize(b)) - 1;
                }
            }
            for (int i = 0; i < colors; i++) {
                min[i] = space.getMinValue(i);
                range[i] = space.getMaxValue(i) - min[i];
            }
        }

        void read(int x, int y, float[] out) {
            if (!component) {
                int argb = image.getRGB(x, y);
                out[0] = (argb >>> 16 & 255) / 255f;
                out[1] = (argb >>> 8 & 255) / 255f;
                out[2] = (argb & 255) / 255f;
                out[3] = alpha ? (argb >>> 24) / 255f : 1f;
                return;
            }
            float a = alpha ? (float) (raster.getSample(x, y, colors) / maxSample[colors]) : 1f;
            for (int i = 0; i < colors; i++) {
                double v = raster.getSample(x, y, i) / maxSample[i];
                if (premultiplied) {
                    v = a == 0 ? 0 : Math.min(1, v / a);
                }
                out[i] = (float) (min[i] + v * range[i]);
            }
            out[colors] = a;
        }

        void write(int x, int y, float[] color, float a) {
            if (!component) {
                int argb = (alpha ? Math.round(a * 255) : 255) << 24 | channel(color[0]) << 16
                    | channel(color[1]) << 8 | channel(color[2]);
                image.setRGB(x, y, argb);
                return;
            }
            for (int i = 0; i < colors; i++) {
                double v = range[i] == 0 ? 0 : (color[i] - min[i]) / range[i];
                v = Math.max(0, Math.min(1, v));
                if (premultiplied) {
                    v *= a;
                }
                raster.setSample(x, y, i, (int) (v * maxSample[i] + 0.5));
            }
            if (alpha) {
                raster.setSample(x, y, colors, (int) (a * maxSample[colors] + 0.5));
            }
        }

        private static int channel(float v) {
            return (int) (Math.max(0, Math.min(1, v)) * 255 + 0.5f);
        }
    }

    /**
     * ColorConverts the image data in the source Raster.
     * If the destination Raster is null, a new Raster will be created.
     * The number of bands in the source and destination Rasters must
     * meet the requirements explained above.  The constructor used to
     * create this ColorConvertOp must have provided enough information
     * to define both source and destination color spaces.  See above.
     * Otherwise, an exception is thrown.
     * @param src the source <code>Raster</code> to be converted
     * @param dest the destination <code>WritableRaster</code>,
     *        or <code>null</code>
     * @return <code>dest</code> color converted from <code>src</code>
     *         or a new, converted <code>WritableRaster</code>
     *         if <code>dest</code> is <code>null</code>
     * @exception IllegalArgumentException if the number of source or
     *             destination bands is incorrect, the source or destination
     *             color spaces are undefined, or this op was constructed
     *             with one of the constructors that applies only to
     *             operations on BufferedImages.
     */
    public final WritableRaster filter(Raster src, WritableRaster dest) {
        List<ColorSpace> spaces = opSpaces();
        if (spaces.size() < 2) {
            throw new IllegalArgumentException("Source or Destination ColorSpace is undefined");
        }
        ColorSpace srcCS = spaces.get(0), dstCS = spaces.get(spaces.size() - 1);
        if (src.getNumBands() != srcCS.getNumComponents()) {
            throw new IllegalArgumentException("Numbers of source Raster bands and source color space components do not match");
        }
        if (dest == null) {
            dest = createCompatibleDestRaster(src);
        } else {
            if (src.getHeight() != dest.getHeight() || src.getWidth() != dest.getWidth()) {
                throw new IllegalArgumentException("Width or height of Rasters do not match");
            }
            if (dest.getNumBands() != dstCS.getNumComponents()) {
                throw new IllegalArgumentException("Numbers of destination Raster bands and destination color space components do not match");
            }
        }
        ColorSpace[] steps = chain(null, spaces, null);
        int srcBands = src.getNumBands(), dstBands = dest.getNumBands();
        double[] srcMax = new double[srcBands], dstMax = new double[dstBands];
        for (int b = 0; b < srcBands; b++) {
            srcMax[b] = (1L << src.getSampleModel().getSampleSize(b)) - 1;
        }
        for (int b = 0; b < dstBands; b++) {
            dstMax[b] = (1L << dest.getSampleModel().getSampleSize(b)) - 1;
        }
        int w = src.getWidth(), h = src.getHeight();
        int sx = src.getMinX(), sy = src.getMinY(), dx = dest.getMinX(), dy = dest.getMinY();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] color = new float[srcBands];
                for (int b = 0; b < srcBands; b++) {
                    float lo = srcCS.getMinValue(b), hi = srcCS.getMaxValue(b);
                    color[b] = (float) (lo + src.getSample(sx + x, sy + y, b) / srcMax[b] * (hi - lo));
                }
                float[] result = convert(steps, color);
                for (int b = 0; b < dstBands; b++) {
                    float lo = dstCS.getMinValue(b), hi = dstCS.getMaxValue(b);
                    double v = Math.max(0, Math.min(1, (result[b] - lo) / (hi - lo)));
                    dest.setSample(dx + x, dy + y, b, (int) (v * dstMax[b] + 0.5));
                }
            }
        }
        return dest;
    }

    /**
     * Returns the bounding box of the destination, given this source.
     * Note that this will be the same as the the bounding box of the
     * source.
     * @param src the source <code>BufferedImage</code>
     * @return a <code>Rectangle2D</code> that is the bounding box
     *         of the destination, given the specified <code>src</code>
     */
    public final Rectangle2D getBounds2D(BufferedImage src) {
        return getBounds2D(src.getRaster());
    }

    /**
     * Returns the bounding box of the destination, given this source.
     * Note that this will be the same as the the bounding box of the
     * source.
     * @param src the source <code>Raster</code>
     * @return a <code>Rectangle2D</code> that is the bounding box
     *         of the destination, given the specified <code>src</code>
     */
    public final Rectangle2D getBounds2D(Raster src) {
        return src.getBounds();
    }

    /**
     * Creates a zeroed destination image with the correct size and number of
     * bands, given this source.
     * @param src       Source image for the filter operation.
     * @param destCM    ColorModel of the destination.  If null, an
     *                  appropriate ColorModel will be used.
     * @return a <code>BufferedImage</code> with the correct size and
     * number of bands from the specified <code>src</code>.
     * @throws IllegalArgumentException if <code>destCM</code> is
     *         <code>null</code> and this <code>ColorConvertOp</code> was
     *         created without any <code>ICC_Profile</code> or
     *         <code>ColorSpace</code> defined for the destination
     */
    public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
        if (destCM == null) {
            List<ColorSpace> spaces = opSpaces();
            if (spaces.isEmpty()) {
                throw new IllegalArgumentException("Destination ColorSpace is undefined");
            }
            ColorSpace cs = spaces.get(spaces.size() - 1);
            boolean alpha = src.getColorModel().hasAlpha();
            int[] bits = new int[cs.getNumComponents() + (alpha ? 1 : 0)];
            java.util.Arrays.fill(bits, 8);
            destCM = new ComponentColorModel(cs, bits, alpha, false,
                                             alpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE,
                                             DataBuffer.TYPE_BYTE);
        }
        return new BufferedImage(destCM, destCM.createCompatibleWritableRaster(src.getWidth(), src.getHeight()),
                                 destCM.isAlphaPremultiplied(), null);
    }

    /**
     * Creates a zeroed destination Raster with the correct size and number of
     * bands, given this source.
     * @param src the specified <code>Raster</code>
     * @return a <code>WritableRaster</code> with the correct size and number
     *         of bands from the specified <code>src</code>
     * @throws IllegalArgumentException if this <code>ColorConvertOp</code>
     *         was created without sufficient information to define the
     *         <code>dst</code> and <code>src</code> color spaces
     */
    public WritableRaster createCompatibleDestRaster(Raster src) {
        List<ColorSpace> spaces = opSpaces();
        if (spaces.size() < 2) {
            throw new IllegalArgumentException("Destination ColorSpace is undefined");
        }
        ColorSpace cs = spaces.get(spaces.size() - 1);
        ComponentColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        return cm.createCompatibleWritableRaster(src.getWidth(), src.getHeight());
    }

    /**
     * Returns the location of the destination point given a
     * point in the source.  If <code>dstPt</code> is non-null,
     * it will be used to hold the return value.  Note that
     * for this class, the destination point will be the same
     * as the source point.
     * @param srcPt the specified source <code>Point2D</code>
     * @param dstPt the destination <code>Point2D</code>
     * @return <code>dstPt</code> after setting its location to be
     *         the same as <code>srcPt</code>
     */
    public final Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        if (dstPt == null) {
            dstPt = new Point2D.Float();
        }
        dstPt.setLocation(srcPt.getX(), srcPt.getY());
        return dstPt;
    }

    /**
     * Returns the rendering hints used by this op.
     * @return the <code>RenderingHints</code> object of this
     *         <code>ColorConvertOp</code>
     */
    public final RenderingHints getRenderingHints() {
        return hints;
    }
}
