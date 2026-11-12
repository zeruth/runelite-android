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

/**
 * This abstract class is used to serve as a color space tag to identify the
 * specific color space of a Color object or, via a ColorModel object,
 * of an Image, a BufferedImage, or a GraphicsDevice.  It contains
 * methods that transform colors in a specific color space to/from sRGB
 * and to/from a well-defined CIEXYZ color space.
 * <p>
 * For purposes of the methods in this class, colors are represented as
 * arrays of color components represented as floats in a normalized range
 * defined by each ColorSpace.  For many ColorSpaces (e.g. sRGB), this
 * range is 0.0 to 1.0.  However, some ColorSpaces have components whose
 * values have a different range.  Methods are provided to inquire per
 * component minimum and maximum normalized values.
 * <p>
 * The CIEXYZ space used by the {@link #toCIEXYZ(float[]) toCIEXYZ} and
 * {@link #fromCIEXYZ(float[]) fromCIEXYZ} methods is the D50-relative XYZ
 * Profile Connection Space of the ICC specification.  The predefined color
 * spaces are {@link ICC_ColorSpace} instances over the standard profiles.
 *
 * @see ICC_ColorSpace
 */
public abstract class ColorSpace implements java.io.Serializable {

    static final long serialVersionUID = -409452704308689724L;

    private int type;
    private int numComponents;
    private transient String[] compName = null;

    // Cache of singletons for the predefined color spaces.
    private static ColorSpace sRGBspace;
    private static ColorSpace XYZspace;
    private static ColorSpace PYCCspace;
    private static ColorSpace GRAYspace;
    private static ColorSpace LINEAR_RGBspace;

    /** Any of the family of XYZ color spaces. */
    public static final int TYPE_XYZ = 0;
    /** Any of the family of Lab color spaces. */
    public static final int TYPE_Lab = 1;
    /** Any of the family of Luv color spaces. */
    public static final int TYPE_Luv = 2;
    /** Any of the family of YCbCr color spaces. */
    public static final int TYPE_YCbCr = 3;
    /** Any of the family of Yxy color spaces. */
    public static final int TYPE_Yxy = 4;
    /** Any of the family of RGB color spaces. */
    public static final int TYPE_RGB = 5;
    /** Any of the family of GRAY color spaces. */
    public static final int TYPE_GRAY = 6;
    /** Any of the family of HSV color spaces. */
    public static final int TYPE_HSV = 7;
    /** Any of the family of HLS color spaces. */
    public static final int TYPE_HLS = 8;
    /** Any of the family of CMYK color spaces. */
    public static final int TYPE_CMYK = 9;
    /** Any of the family of CMY color spaces. */
    public static final int TYPE_CMY = 11;
    /** Generic 2 component color spaces. */
    public static final int TYPE_2CLR = 12;
    /** Generic 3 component color spaces. */
    public static final int TYPE_3CLR = 13;
    /** Generic 4 component color spaces. */
    public static final int TYPE_4CLR = 14;
    /** Generic 5 component color spaces. */
    public static final int TYPE_5CLR = 15;
    /** Generic 6 component color spaces. */
    public static final int TYPE_6CLR = 16;
    /** Generic 7 component color spaces. */
    public static final int TYPE_7CLR = 17;
    /** Generic 8 component color spaces. */
    public static final int TYPE_8CLR = 18;
    /** Generic 9 component color spaces. */
    public static final int TYPE_9CLR = 19;
    /** Generic 10 component color spaces. */
    public static final int TYPE_ACLR = 20;
    /** Generic 11 component color spaces. */
    public static final int TYPE_BCLR = 21;
    /** Generic 12 component color spaces. */
    public static final int TYPE_CCLR = 22;
    /** Generic 13 component color spaces. */
    public static final int TYPE_DCLR = 23;
    /** Generic 14 component color spaces. */
    public static final int TYPE_ECLR = 24;
    /** Generic 15 component color spaces. */
    public static final int TYPE_FCLR = 25;

    /** The sRGB color space defined at http://www.w3.org/pub/WWW/Graphics/Color/sRGB.html. */
    public static final int CS_sRGB = 1000;
    /** A built-in linear RGB color space.  This space is based on the same RGB primaries as CS_sRGB, but has a linear tone reproduction curve. */
    public static final int CS_LINEAR_RGB = 1004;
    /** The CIEXYZ conversion color space defined above. */
    public static final int CS_CIEXYZ = 1001;
    /** The Photo YCC conversion color space. */
    public static final int CS_PYCC = 1002;
    /** The built-in linear gray scale color space. */
    public static final int CS_GRAY = 1003;

    /**
     * Constructs a ColorSpace object given a color space type
     * and the number of components.
     * @param type one of the <CODE>ColorSpace</CODE> type constants
     * @param numcomponents the number of components in the color space
     */
    protected ColorSpace(int type, int numcomponents) {
        this.type = type;
        this.numComponents = numcomponents;
    }

    /**
     * Returns a ColorSpace representing one of the specific
     * predefined color spaces.
     * @param colorspace a specific color space identified by one of
     *        the predefined class constants (e.g. CS_sRGB, CS_LINEAR_RGB,
     *        CS_CIEXYZ, CS_GRAY, or CS_PYCC)
     * @return the requested <CODE>ColorSpace</CODE> object
     */
    public static ColorSpace getInstance(int colorspace) {
        ColorSpace theColorSpace;

        synchronized (ColorSpace.class) {
            switch (colorspace) {
                case CS_sRGB:
                    if (sRGBspace == null) {
                        sRGBspace = new ICC_ColorSpace(ICC_Profile.getInstance(CS_sRGB));
                    }
                    theColorSpace = sRGBspace;
                    break;
                case CS_CIEXYZ:
                    if (XYZspace == null) {
                        XYZspace = new ICC_ColorSpace(ICC_Profile.getInstance(CS_CIEXYZ));
                    }
                    theColorSpace = XYZspace;
                    break;
                case CS_PYCC:
                    if (PYCCspace == null) {
                        PYCCspace = new ICC_ColorSpace(ICC_Profile.getInstance(CS_PYCC));
                    }
                    theColorSpace = PYCCspace;
                    break;
                case CS_GRAY:
                    if (GRAYspace == null) {
                        GRAYspace = new ICC_ColorSpace(ICC_Profile.getInstance(CS_GRAY));
                    }
                    theColorSpace = GRAYspace;
                    break;
                case CS_LINEAR_RGB:
                    if (LINEAR_RGBspace == null) {
                        LINEAR_RGBspace = new ICC_ColorSpace(ICC_Profile.getInstance(CS_LINEAR_RGB));
                    }
                    theColorSpace = LINEAR_RGBspace;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown color space");
            }
        }
        return theColorSpace;
    }

    /**
     * Returns true if the ColorSpace is CS_sRGB.
     * @return <CODE>true</CODE> if this is a <CODE>CS_sRGB</CODE> color
     *         space, <code>false</code> if it is not
     */
    public boolean isCS_sRGB() {
        // REMIND - make sure we know sRGBspace exists already
        return (this == sRGBspace);
    }

    /**
     * Transforms a color value assumed to be in this ColorSpace
     * into a value in the default CS_sRGB color space.
     *
     * @param colorvalue a float array with length of at least the number
     *        of components in this ColorSpace
     * @return a float array of length 3
     * @throws ArrayIndexOutOfBoundsException if array length is not
     *         at least the number of components in this ColorSpace
     */
    public abstract float[] toRGB(float[] colorvalue);

    /**
     * Transforms a color value assumed to be in the default CS_sRGB
     * color space into this ColorSpace.
     *
     * @param rgbvalue a float array with length of at least 3
     * @return a float array with length equal to the number of
     *         components in this ColorSpace
     * @throws ArrayIndexOutOfBoundsException if array length is not
     *         at least 3
     */
    public abstract float[] fromRGB(float[] rgbvalue);

    /**
     * Transforms a color value assumed to be in this ColorSpace
     * into the CS_CIEXYZ conversion color space.
     *
     * @param colorvalue a float array with length of at least the number
     *        of components in this ColorSpace
     * @return a float array of length 3
     * @throws ArrayIndexOutOfBoundsException if array length is not
     *         at least the number of components in this ColorSpace.
     */
    public abstract float[] toCIEXYZ(float[] colorvalue);

    /**
     * Transforms a color value assumed to be in the CS_CIEXYZ conversion
     * color space into this ColorSpace.
     *
     * @param colorvalue a float array with length of at least 3
     * @return a float array with length equal to the number of
     *         components in this ColorSpace
     * @throws ArrayIndexOutOfBoundsException if array length is not
     *         at least 3
     */
    public abstract float[] fromCIEXYZ(float[] colorvalue);

    /**
     * Returns the color space type of this ColorSpace (for example
     * TYPE_RGB, TYPE_XYZ, ...).
     * @return the type constant that represents the type of this
     *         <CODE>ColorSpace</CODE>
     */
    public int getType() {
        return type;
    }

    /**
     * Returns the number of components of this ColorSpace.
     * @return The number of components in this <CODE>ColorSpace</CODE>.
     */
    public int getNumComponents() {
        return numComponents;
    }

    /**
     * Returns the name of the component given the component index.
     *
     * @param idx the component index
     * @return the name of the component at the specified index
     * @throws IllegalArgumentException if <code>idx</code> is
     *         less than 0 or greater than numComponents - 1
     */
    public String getName(int idx) {
        /* REMIND - handle common cases here */
        if ((idx < 0) || (idx > numComponents - 1)) {
            throw new IllegalArgumentException("Component index out of range: " + idx);
        }

        if (compName == null) {
            switch (type) {
                case ColorSpace.TYPE_XYZ:
                    compName = new String[] {"X", "Y", "Z"};
                    break;
                case ColorSpace.TYPE_Lab:
                    compName = new String[] {"L", "a", "b"};
                    break;
                case ColorSpace.TYPE_Luv:
                    compName = new String[] {"L", "u", "v"};
                    break;
                case ColorSpace.TYPE_YCbCr:
                    compName = new String[] {"Y", "Cb", "Cr"};
                    break;
                case ColorSpace.TYPE_Yxy:
                    compName = new String[] {"Y", "x", "y"};
                    break;
                case ColorSpace.TYPE_RGB:
                    compName = new String[] {"Red", "Green", "Blue"};
                    break;
                case ColorSpace.TYPE_GRAY:
                    compName = new String[] {"Gray"};
                    break;
                case ColorSpace.TYPE_HSV:
                    compName = new String[] {"Hue", "Saturation", "Value"};
                    break;
                case ColorSpace.TYPE_HLS:
                    compName = new String[] {"Hue", "Lightness", "Saturation"};
                    break;
                case ColorSpace.TYPE_CMYK:
                    compName = new String[] {"Cyan", "Magenta", "Yellow", "Black"};
                    break;
                case ColorSpace.TYPE_CMY:
                    compName = new String[] {"Cyan", "Magenta", "Yellow"};
                    break;
                default:
                    String[] tmp = new String[numComponents];
                    for (int i = 0; i < tmp.length; i++) {
                        tmp[i] = "Unnamed color component(" + i + ")";
                    }
                    compName = tmp;
            }
        }
        return compName[idx];
    }

    /**
     * Returns the minimum normalized color component value for the
     * specified component.  The default implementation in this abstract
     * class returns 0.0 for all components.  Subclasses should override
     * this method if necessary.
     *
     * @param component the component index
     * @return the minimum normalized component value
     * @throws IllegalArgumentException if component is less than 0 or
     *         greater than numComponents - 1
     * @since 1.4
     */
    public float getMinValue(int component) {
        if ((component < 0) || (component > numComponents - 1)) {
            throw new IllegalArgumentException("Component index out of range: " + component);
        }
        return 0.0f;
    }

    /**
     * Returns the maximum normalized color component value for the
     * specified component.  The default implementation in this abstract
     * class returns 1.0 for all components.  Subclasses should override
     * this method if necessary.
     *
     * @param component the component index
     * @return the maximum normalized component value
     * @throws IllegalArgumentException if component is less than 0 or
     *         greater than numComponents - 1
     * @since 1.4
     */
    public float getMaxValue(int component) {
        if ((component < 0) || (component > numComponents - 1)) {
            throw new IllegalArgumentException("Component index out of range: " + component);
        }
        return 1.0f;
    }

    /* Returns true if cspace is the XYZspace. */
    static boolean isCS_CIEXYZ(ColorSpace cspace) {
        return (cspace == XYZspace);
    }
}
