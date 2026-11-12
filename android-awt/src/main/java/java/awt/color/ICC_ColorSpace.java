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

/* ********************************************************************
 **********************************************************************
 **********************************************************************
 *** COPYRIGHT (c) Eastman Kodak Company, 1997                      ***
 *** As  an unpublished  work pursuant to Title 17 of the United    ***
 *** States Code.  All rights reserved.                             ***
 **********************************************************************
 **********************************************************************
 **********************************************************************/
package java.awt.color;

import net.runelite.awt.impl.MatrixCMM;

/**
 * The ICC_ColorSpace class is an implementation of the abstract
 * ColorSpace class.  This representation of
 * device independent and device dependent color spaces is based on the
 * International Color Consortium Specification ICC.1:2001-12, File Format for
 * Color Profiles.
 * <p>
 * Typically, a Color or ColorModel would be associated with an ICC
 * Profile which is either an input, display, or output profile (see
 * the ICC specification).  There are other types of ICC Profiles, e.g.
 * abstract profiles, device link profiles, and named color profiles,
 * which do not contain information appropriate for representing the color
 * space of a color, image, or device (see ICC_Profile).
 * Attempting to create an ICC_ColorSpace object from an inappropriate ICC
 * Profile is an error.
 * <p>
 * The conversion methods use relative colorimetric intent and D50 XYZ as the
 * CIEXYZ connection space, as on desktop.  Android has no CMM: conversions
 * use the matrix approximation of the profile (tone curves and colorant
 * matrix, see {@code net.runelite.awt.impl.MatrixCMM}), which is exact for
 * the standard profiles and for every matrix/TRC profile; a profile described
 * only by lookup tables makes the conversion methods throw
 * {@link CMMException}.
 */
public class ICC_ColorSpace extends ColorSpace {
    static final long serialVersionUID = 3455889114070431483L;

    private ICC_Profile thisProfile;
    private float[] minVal;
    private float[] maxVal;

    /**
     * Constructs a new ICC_ColorSpace from an ICC_Profile object.
     * @param profile the specified ICC_Profile object
     * @exception IllegalArgumentException if profile is inappropriate for
     *            representing a ColorSpace.
     */
    public ICC_ColorSpace(ICC_Profile profile) {
        super(profile.getColorSpaceType(), profile.getNumComponents());
        int profileClass = profile.getProfileClass();
        if ((profileClass != ICC_Profile.CLASS_INPUT)
            && (profileClass != ICC_Profile.CLASS_DISPLAY)
            && (profileClass != ICC_Profile.CLASS_OUTPUT)
            && (profileClass != ICC_Profile.CLASS_COLORSPACECONVERSION)
            && (profileClass != ICC_Profile.CLASS_NAMEDCOLOR)
            && (profileClass != ICC_Profile.CLASS_ABSTRACT)) {
            throw new IllegalArgumentException("Invalid profile type");
        }
        thisProfile = profile;
        setMinMax();
    }

    /**
     * Validate an ICC_ColorSpace read from an object input stream
     */
    private void readObject(java.io.ObjectInputStream s)
        throws ClassNotFoundException, java.io.IOException {
        s.defaultReadObject();
        if (thisProfile == null) {
            thisProfile = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
        }
    }

    /**
     * Returns the ICC_Profile for this ICC_ColorSpace.
     * @return the ICC_Profile for this ICC_ColorSpace.
     */
    public ICC_Profile getProfile() {
        return thisProfile;
    }

    /**
     * Transforms a color value assumed to be in this ColorSpace
     * into a value in the default CS_sRGB color space.
     * <p>
     * This method transforms color values using algorithms designed
     * to produce the best perceptual match between input and output
     * colors.  In order to do colorimetric conversion of color values,
     * you should use the <code>toCIEXYZ</code>
     * method of this color space to first convert from the input
     * color space to the CS_CIEXYZ color space, and then use the
     * <code>fromCIEXYZ</code> method of the CS_sRGB color space to
     * convert from CS_CIEXYZ to the output color space.
     *
     * @param colorvalue a float array with length of at least the number
     *      of components in this ColorSpace
     * @return a float array of length 3
     * @throws ArrayIndexOutOfBoundsException if array length is not
     * at least the number of components in this ColorSpace.
     */
    public float[] toRGB(float[] colorvalue) {
        return MatrixCMM.srgb().fromXYZ(toCIEXYZ(colorvalue));
    }

    /**
     * Transforms a color value assumed to be in the default CS_sRGB
     * color space into this ColorSpace.
     *
     * @param rgbvalue a float array with length of at least 3
     * @return a float array with length equal to the number of
     *       components in this ColorSpace
     * @throws ArrayIndexOutOfBoundsException if array length is not
     * at least 3.
     */
    public float[] fromRGB(float[] rgbvalue) {
        float[] rgb = {clip(rgbvalue[0], 0, 1), clip(rgbvalue[1], 0, 1), clip(rgbvalue[2], 0, 1)};
        return fromCIEXYZ(MatrixCMM.srgb().toXYZ(rgb));
    }

    /**
     * Transforms a color value assumed to be in this ColorSpace
     * into the CS_CIEXYZ conversion color space.
     * <p>
     * This method transforms color values using relative colorimetric
     * rendering intent.  The conversion space is D50 XYZ with values in the
     * range 0.0 to 1.0 + (32767.0 / 32768.0), as in the ICC PCS encoding.
     *
     * @param colorvalue a float array with length of at least the number
     *      of components in this ColorSpace
     * @return a float array of length 3
     * @throws ArrayIndexOutOfBoundsException if array length is not
     * at least the number of components in this ColorSpace.
     */
    public float[] toCIEXYZ(float[] colorvalue) {
        int n = getNumComponents();
        float[] c = new float[n];
        for (int i = 0; i < n; i++) {
            c[i] = clip(colorvalue[i], minVal[i], maxVal[i]);
        }
        float[] xyz = MatrixCMM.transform(thisProfile).toXYZ(c);
        float max = 1.0f + (32767.0f / 32768.0f);
        return new float[] {clip(xyz[0], 0, max), clip(xyz[1], 0, max), clip(xyz[2], 0, max)};
    }

    /**
     * Transforms a color value assumed to be in the CS_CIEXYZ conversion
     * color space into this ColorSpace.
     * <p>
     * This method transforms color values using relative colorimetric
     * rendering intent; the result is clipped to this space's component
     * ranges.
     *
     * @param colorvalue a float array with length of at least 3
     * @return a float array with length equal to the number of
     *         components in this ColorSpace
     * @throws ArrayIndexOutOfBoundsException if array length is not
     * at least 3.
     */
    public float[] fromCIEXYZ(float[] colorvalue) {
        float max = 1.0f + (32767.0f / 32768.0f);
        float[] xyz = {clip(colorvalue[0], 0, max), clip(colorvalue[1], 0, max), clip(colorvalue[2], 0, max)};
        float[] c = MatrixCMM.transform(thisProfile).fromXYZ(xyz);
        for (int i = 0; i < c.length; i++) {
            c[i] = clip(c[i], minVal[i], maxVal[i]);
        }
        return c;
    }

    /**
     * Returns the minimum normalized color component value for the
     * specified component.  For TYPE_XYZ spaces, this method returns
     * minimum values of 0.0 for all components.  For TYPE_Lab spaces,
     * this method returns 0.0 for L and -128.0 for a and b components.
     * This is consistent with the encoding of the XYZ and Lab Profile
     * Connection Spaces in the ICC specification.  For all other types, this
     * method returns 0.0 for all components.
     *
     * @param component the component index
     * @return the minimum normalized component value
     * @throws IllegalArgumentException if component is less than 0 or
     *         greater than numComponents - 1
     * @since 1.4
     */
    public float getMinValue(int component) {
        if ((component < 0) || (component > (getNumComponents() - 1))) {
            throw new IllegalArgumentException("Component index out of range: " + component);
        }
        return minVal[component];
    }

    /**
     * Returns the maximum normalized color component value for the
     * specified component.  For TYPE_XYZ spaces, this method returns
     * maximum values of 1.0 + (32767.0 / 32768.0) for all components.
     * For TYPE_Lab spaces,
     * this method returns 100.0 for L and 127.0 for a and b components.
     * This is consistent with the encoding of the XYZ and Lab Profile
     * Connection Spaces in the ICC specification.  For all other types, this
     * method returns 1.0 for all components.
     *
     * @param component the component index
     * @return the maximum normalized component value
     * @throws IllegalArgumentException if component is less than 0 or
     *         greater than numComponents - 1
     * @since 1.4
     */
    public float getMaxValue(int component) {
        if ((component < 0) || (component > (getNumComponents() - 1))) {
            throw new IllegalArgumentException("Component index out of range: " + component);
        }
        return maxVal[component];
    }

    private void setMinMax() {
        int nc = this.getNumComponents();
        int type = this.getType();
        minVal = new float[nc];
        maxVal = new float[nc];
        if (type == ColorSpace.TYPE_Lab) {
            minVal[0] = 0.0f;    // L
            maxVal[0] = 100.0f;
            minVal[1] = -128.0f; // a
            maxVal[1] = 127.0f;
            minVal[2] = -128.0f; // b
            maxVal[2] = 127.0f;
        } else if (type == ColorSpace.TYPE_XYZ) {
            minVal[0] = minVal[1] = minVal[2] = 0.0f; // X, Y, Z
            maxVal[0] = maxVal[1] = maxVal[2] = 1.0f + (32767.0f / 32768.0f);
        } else {
            for (int i = 0; i < nc; i++) {
                minVal[i] = 0.0f;
                maxVal[i] = 1.0f;
            }
        }
    }

    private static float clip(float v, float lo, float hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
