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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.StringTokenizer;
import net.runelite.awt.impl.MatrixCMM;

/**
 * A representation of color profile data for device independent and
 * device dependent color spaces based on the International Color
 * Consortium Specification ICC.1:2001-12, File Format for Color Profiles.
 *
 * <p>On Android the profile is held as its ICC byte image; the five standard
 * profiles are built by {@code net.runelite.awt.impl.MatrixCMM} as ICC v2
 * matrix/TRC (or abstract XYZ) profiles with the JDK's colorants. There is no
 * CMM: transforms use the matrix approximation, see {@link ICC_ColorSpace}.
 */
public class ICC_Profile implements Serializable {
    private static final long serialVersionUID = -3938515861990936766L;

    private transient byte[] data;
    /** The {@code ColorSpace.CS_*} of a standard profile, 0 otherwise. */
    private transient int predefined;

    private static ICC_Profile sRGBprofile;
    private static ICC_Profile XYZprofile;
    private static ICC_Profile PYCCprofile;
    private static ICC_Profile GRAYprofile;
    private static ICC_Profile LINEAR_RGBprofile;

    /** Profile class is input. */
    public static final int CLASS_INPUT = 0;
    /** Profile class is display. */
    public static final int CLASS_DISPLAY = 1;
    /** Profile class is output. */
    public static final int CLASS_OUTPUT = 2;
    /** Profile class is device link. */
    public static final int CLASS_DEVICELINK = 3;
    /** Profile class is color space conversion. */
    public static final int CLASS_COLORSPACECONVERSION = 4;
    /** Profile class is abstract. */
    public static final int CLASS_ABSTRACT = 5;
    /** Profile class is named color. */
    public static final int CLASS_NAMEDCOLOR = 6;

    public static final int icSigXYZData        = 0x58595A20;    /* 'XYZ ' */
    public static final int icSigLabData        = 0x4C616220;    /* 'Lab ' */
    public static final int icSigLuvData        = 0x4C757620;    /* 'Luv ' */
    public static final int icSigYCbCrData      = 0x59436272;    /* 'YCbr' */
    public static final int icSigYxyData        = 0x59787920;    /* 'Yxy ' */
    public static final int icSigRgbData        = 0x52474220;    /* 'RGB ' */
    public static final int icSigGrayData       = 0x47524159;    /* 'GRAY' */
    public static final int icSigHsvData        = 0x48535620;    /* 'HSV ' */
    public static final int icSigHlsData        = 0x484C5320;    /* 'HLS ' */
    public static final int icSigCmykData       = 0x434D594B;    /* 'CMYK' */
    public static final int icSigCmyData        = 0x434D5920;    /* 'CMY ' */
    public static final int icSigSpace2CLR      = 0x32434C52;    /* '2CLR' */
    public static final int icSigSpace3CLR      = 0x33434C52;    /* '3CLR' */
    public static final int icSigSpace4CLR      = 0x34434C52;    /* '4CLR' */
    public static final int icSigSpace5CLR      = 0x35434C52;    /* '5CLR' */
    public static final int icSigSpace6CLR      = 0x36434C52;    /* '6CLR' */
    public static final int icSigSpace7CLR      = 0x37434C52;    /* '7CLR' */
    public static final int icSigSpace8CLR      = 0x38434C52;    /* '8CLR' */
    public static final int icSigSpace9CLR      = 0x39434C52;    /* '9CLR' */
    public static final int icSigSpaceACLR      = 0x41434C52;    /* 'ACLR' */
    public static final int icSigSpaceBCLR      = 0x42434C52;    /* 'BCLR' */
    public static final int icSigSpaceCCLR      = 0x43434C52;    /* 'CCLR' */
    public static final int icSigSpaceDCLR      = 0x44434C52;    /* 'DCLR' */
    public static final int icSigSpaceECLR      = 0x45434C52;    /* 'ECLR' */
    public static final int icSigSpaceFCLR      = 0x46434C52;    /* 'FCLR' */

    public static final int icSigInputClass       = 0x73636E72;    /* 'scnr' */
    public static final int icSigDisplayClass     = 0x6D6E7472;    /* 'mntr' */
    public static final int icSigOutputClass      = 0x70727472;    /* 'prtr' */
    public static final int icSigLinkClass        = 0x6C696E6B;    /* 'link' */
    public static final int icSigAbstractClass    = 0x61627374;    /* 'abst' */
    public static final int icSigColorSpaceClass  = 0x73706163;    /* 'spac' */
    public static final int icSigNamedColorClass  = 0x6e6d636c;    /* 'nmcl' */

    public static final int icPerceptual            = 0;
    public static final int icRelativeColorimetric  = 1;
    public static final int icMediaRelativeColorimetric = 1;
    public static final int icSaturation            = 2;
    public static final int icAbsoluteColorimetric  = 3;
    public static final int icICCAbsoluteColorimetric = 3;

    public static final int icSigHead      = 0x68656164; /* 'head' - special */
    public static final int icSigAToB0Tag          = 0x41324230;    /* 'A2B0' */
    public static final int icSigAToB1Tag          = 0x41324231;    /* 'A2B1' */
    public static final int icSigAToB2Tag          = 0x41324232;    /* 'A2B2' */
    public static final int icSigBlueColorantTag   = 0x6258595A;    /* 'bXYZ' */
    public static final int icSigBlueMatrixColumnTag = 0x6258595A;  /* 'bXYZ' */
    public static final int icSigBlueTRCTag        = 0x62545243;    /* 'bTRC' */
    public static final int icSigBToA0Tag          = 0x42324130;    /* 'B2A0' */
    public static final int icSigBToA1Tag          = 0x42324131;    /* 'B2A1' */
    public static final int icSigBToA2Tag          = 0x42324132;    /* 'B2A2' */
    public static final int icSigCalibrationDateTimeTag = 0x63616C74; /* 'calt' */
    public static final int icSigCharTargetTag     = 0x74617267;    /* 'targ' */
    public static final int icSigCopyrightTag      = 0x63707274;    /* 'cprt' */
    public static final int icSigCrdInfoTag        = 0x63726469;    /* 'crdi' */
    public static final int icSigDeviceMfgDescTag  = 0x646D6E64;    /* 'dmnd' */
    public static final int icSigDeviceModelDescTag = 0x646D6464;   /* 'dmdd' */
    public static final int icSigDeviceSettingsTag = 0x64657673;    /* 'devs' */
    public static final int icSigGamutTag          = 0x67616D74;    /* 'gamt' */
    public static final int icSigGrayTRCTag        = 0x6b545243;    /* 'kTRC' */
    public static final int icSigGreenColorantTag  = 0x6758595A;    /* 'gXYZ' */
    public static final int icSigGreenMatrixColumnTag = 0x6758595A; /* 'gXYZ' */
    public static final int icSigGreenTRCTag       = 0x67545243;    /* 'gTRC' */
    public static final int icSigLuminanceTag      = 0x6C756d69;    /* 'lumi' */
    public static final int icSigMeasurementTag    = 0x6D656173;    /* 'meas' */
    public static final int icSigMediaBlackPointTag = 0x626B7074;   /* 'bkpt' */
    public static final int icSigMediaWhitePointTag = 0x77747074;   /* 'wtpt' */
    public static final int icSigNamedColor2Tag    = 0x6E636C32;    /* 'ncl2' */
    public static final int icSigOutputResponseTag = 0x72657370;    /* 'resp' */
    public static final int icSigPreview0Tag       = 0x70726530;    /* 'pre0' */
    public static final int icSigPreview1Tag       = 0x70726531;    /* 'pre1' */
    public static final int icSigPreview2Tag       = 0x70726532;    /* 'pre2' */
    public static final int icSigProfileDescriptionTag = 0x64657363; /* 'desc' */
    public static final int icSigProfileSequenceDescTag = 0x70736571; /* 'pseq' */
    public static final int icSigPs2CRD0Tag        = 0x70736430;    /* 'psd0' */
    public static final int icSigPs2CRD1Tag        = 0x70736431;    /* 'psd1' */
    public static final int icSigPs2CRD2Tag        = 0x70736432;    /* 'psd2' */
    public static final int icSigPs2CRD3Tag        = 0x70736433;    /* 'psd3' */
    public static final int icSigPs2CSATag         = 0x70733273;    /* 'ps2s' */
    public static final int icSigPs2RenderingIntentTag = 0x70733269; /* 'ps2i' */
    public static final int icSigRedColorantTag    = 0x7258595A;    /* 'rXYZ' */
    public static final int icSigRedMatrixColumnTag = 0x7258595A;   /* 'rXYZ' */
    public static final int icSigRedTRCTag         = 0x72545243;    /* 'rTRC' */
    public static final int icSigScreeningDescTag  = 0x73637264;    /* 'scrd' */
    public static final int icSigScreeningTag      = 0x7363726E;    /* 'scrn' */
    public static final int icSigTechnologyTag     = 0x74656368;    /* 'tech' */
    public static final int icSigUcrBgTag          = 0x62666420;    /* 'bfd ' */
    public static final int icSigViewingCondDescTag = 0x76756564;   /* 'vued' */
    public static final int icSigViewingConditionsTag = 0x76696577; /* 'view' */
    public static final int icSigChromaticityTag   = 0x6368726d;    /* 'chrm' */
    public static final int icSigChromaticAdaptationTag = 0x63686164; /* 'chad' */
    public static final int icSigColorantOrderTag  = 0x636C726F;    /* 'clro' */
    public static final int icSigColorantTableTag  = 0x636C7274;    /* 'clrt' */

    public static final int icHdrSize         = 0;  /* Profile size in bytes */
    public static final int icHdrCmmId        = 4;  /* CMM for this profile */
    public static final int icHdrVersion      = 8;  /* Format version number */
    public static final int icHdrDeviceClass  = 12; /* Type of profile */
    public static final int icHdrColorSpace   = 16; /* Color space of data */
    public static final int icHdrPcs          = 20; /* PCS - XYZ or Lab only */
    public static final int icHdrDate       = 24; /* Date profile was created */
    public static final int icHdrMagic        = 36; /* icMagicNumber */
    public static final int icHdrPlatform     = 40; /* Primary Platform */
    public static final int icHdrFlags        = 44; /* Various bit settings */
    public static final int icHdrManufacturer = 48; /* Device manufacturer */
    public static final int icHdrModel        = 52; /* Device model number */
    public static final int icHdrAttributes   = 56; /* Device attributes */
    public static final int icHdrRenderingIntent = 64; /* Rendering intent */
    public static final int icHdrIlluminant   = 68; /* Profile illuminant */
    public static final int icHdrCreator      = 80; /* Profile creator */
    public static final int icHdrProfileID = 84; /* Profile's ID */

    public static final int icTagType          = 0;    /* tag type signature */
    public static final int icTagReserved      = 4;    /* reserved */
    public static final int icCurveCount       = 8;    /* number of entries */
    public static final int icCurveData        = 12;   /* data */
    public static final int icXYZNumberX       = 8;    /* XYZNumber X */

    ICC_Profile(byte[] data, int predefined) {
        this.data = data;
        this.predefined = predefined;
    }

    /**
     * Constructs an ICC_Profile object corresponding to the data in
     * a byte array.  Throws an IllegalArgumentException if the data
     * does not correspond to a valid ICC Profile.
     * @param data the specified ICC Profile data
     * @return an {@code ICC_Profile} object corresponding to
     *          the data in the specified {@code data} array.
     */
    public static ICC_Profile getInstance(byte[] data) {
        MatrixCMM.validate(data);
        return create(data.clone(), 0);
    }

    private static ICC_Profile create(byte[] data, int predefined) {
        int profileClass = MatrixCMM.getInt(data, icHdrDeviceClass);
        int space = MatrixCMM.getInt(data, icHdrColorSpace);
        boolean colorClass = profileClass == icSigInputClass || profileClass == icSigDisplayClass
            || profileClass == icSigOutputClass || profileClass == icSigColorSpaceClass;
        if (colorClass && space == icSigRgbData && hasTags(data, icSigRedColorantTag, icSigGreenColorantTag,
                icSigBlueColorantTag, icSigRedTRCTag, icSigGreenTRCTag, icSigBlueTRCTag, icSigMediaWhitePointTag)) {
            return new ICC_ProfileRGB(data, predefined);
        }
        if (colorClass && space == icSigGrayData && hasTags(data, icSigGrayTRCTag, icSigMediaWhitePointTag)) {
            return new ICC_ProfileGray(data, predefined);
        }
        return new ICC_Profile(data, predefined);
    }

    private static boolean hasTags(byte[] data, int... tags) {
        for (int tag : tags) {
            if (MatrixCMM.tag(data, tag) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Constructs an ICC_Profile corresponding to one of the specific color
     * spaces defined by the ColorSpace class (for example CS_sRGB).
     * Throws an IllegalArgumentException if cspace is not one of the
     * defined color spaces.
     *
     * @param cspace the type of color space to create a profile for.
     * The specified type is one of the color
     * space constants defined in the  <CODE>ColorSpace</CODE> class.
     *
     * @return an <code>ICC_Profile</code> object corresponding to
     *          the specified <code>ColorSpace</code> type.
     */
    public static ICC_Profile getInstance(int cspace) {
        synchronized (ICC_Profile.class) {
            switch (cspace) {
                case ColorSpace.CS_sRGB:
                    if (sRGBprofile == null) {
                        sRGBprofile = standard(cspace);
                    }
                    return sRGBprofile;
                case ColorSpace.CS_CIEXYZ:
                    if (XYZprofile == null) {
                        XYZprofile = standard(cspace);
                    }
                    return XYZprofile;
                case ColorSpace.CS_PYCC:
                    if (PYCCprofile == null) {
                        PYCCprofile = standard(cspace);
                    }
                    return PYCCprofile;
                case ColorSpace.CS_GRAY:
                    if (GRAYprofile == null) {
                        GRAYprofile = standard(cspace);
                    }
                    return GRAYprofile;
                case ColorSpace.CS_LINEAR_RGB:
                    if (LINEAR_RGBprofile == null) {
                        LINEAR_RGBprofile = standard(cspace);
                    }
                    return LINEAR_RGBprofile;
                default:
                    throw new IllegalArgumentException("Unknown color space");
            }
        }
    }

    private static ICC_Profile standard(int cspace) {
        return create(MatrixCMM.standardProfile(cspace).clone(), cspace);
    }

    /**
     * Constructs an ICC_Profile corresponding to the data in a file.
     * fileName may be an absolute or a relative file specification.
     * Relative file names are looked for in several places: first, relative
     * to any directories specified by the java.iccprofile.path property;
     * second, relative to any directories specified by the java.class.path
     * property.  The names of the standard profiles ({@code sRGB.pf},
     * {@code CIEXYZ.pf}, {@code PYCC.pf}, {@code GRAY.pf},
     * {@code LINEAR_RGB.pf}) resolve to the built-in profiles.
     *
     * @param fileName The file that contains the data for the profile.
     * @return an {@code ICC_Profile} object corresponding to
     *          the data in the specified file.
     * @exception IOException If the specified file cannot be opened or
     * an I/O error occurs while reading the file.
     * @exception IllegalArgumentException If the file does not
     * contain valid ICC Profile data.
     */
    public static ICC_Profile getInstance(String fileName) throws IOException {
        File file = locate(fileName);
        if (file == null) {
            int cspace = standardName(fileName);
            if (cspace != 0) {
                return getInstance(MatrixCMM.standardProfile(cspace));
            }
            throw new IOException("Cannot open file " + fileName);
        }
        InputStream in = new FileInputStream(file);
        try {
            return getInstance(in);
        } finally {
            in.close();
        }
    }

    private static int standardName(String name) {
        if ("sRGB.pf".equals(name)) {
            return ColorSpace.CS_sRGB;
        } else if ("CIEXYZ.pf".equals(name)) {
            return ColorSpace.CS_CIEXYZ;
        } else if ("PYCC.pf".equals(name)) {
            return ColorSpace.CS_PYCC;
        } else if ("GRAY.pf".equals(name)) {
            return ColorSpace.CS_GRAY;
        } else if ("LINEAR_RGB.pf".equals(name)) {
            return ColorSpace.CS_LINEAR_RGB;
        }
        return 0;
    }

    private static File locate(String fileName) {
        File f = new File(fileName);
        if (f.isAbsolute()) {
            return f.isFile() ? f : null;
        }
        if (f.isFile()) {
            return f;
        }
        for (String property : new String[] {"java.iccprofile.path", "java.class.path"}) {
            String path = System.getProperty(property);
            if (path == null) {
                continue;
            }
            StringTokenizer st = new StringTokenizer(path, File.pathSeparator);
            while (st.hasMoreTokens()) {
                File candidate = new File(st.nextToken(), fileName);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * Constructs an ICC_Profile corresponding to the data in an InputStream.
     * This method throws an IllegalArgumentException if the stream does not
     * contain valid ICC Profile data.  It throws an IOException if an I/O
     * error occurs while reading the stream.
     * @param s The input stream from which to read the profile data.
     * @return an {@code ICC_Profile} object corresponding to the data in
     *          the specified {@code InputStream}.
     * @exception IOException If an I/O error occurs while reading the stream.
     */
    public static ICC_Profile getInstance(InputStream s) throws IOException {
        byte[] bytes = MatrixCMM.readAll(s);
        MatrixCMM.validate(bytes);
        return create(bytes, 0);
    }

    /**
     * Returns profile major version.
     * @return  The major version of the profile.
     */
    public int getMajorVersion() {
        return data[icHdrVersion] & 255;
    }

    /**
     * Returns profile minor version.
     * @return The minor version of the profile.
     */
    public int getMinorVersion() {
        return data[icHdrVersion + 1] & 255;
    }

    /**
     * Returns the profile class.
     * @return One of the predefined profile class constants.
     */
    public int getProfileClass() {
        switch (MatrixCMM.getInt(data, icHdrDeviceClass)) {
            case icSigInputClass:
                return CLASS_INPUT;
            case icSigDisplayClass:
                return CLASS_DISPLAY;
            case icSigOutputClass:
                return CLASS_OUTPUT;
            case icSigLinkClass:
                return CLASS_DEVICELINK;
            case icSigColorSpaceClass:
                return CLASS_COLORSPACECONVERSION;
            case icSigAbstractClass:
                return CLASS_ABSTRACT;
            case icSigNamedColorClass:
                return CLASS_NAMEDCOLOR;
            default:
                throw new IllegalArgumentException("Unknown profile class");
        }
    }

    /**
     * Returns the color space type.  Returns one of the color space type
     * constants defined by the ColorSpace class.  This is the
     * "input" color space of the profile.
     * @return One of the color space type constants defined in the
     * <CODE>ColorSpace</CODE> class.
     */
    public int getColorSpaceType() {
        return MatrixCMM.colorSpaceType(MatrixCMM.getInt(data, icHdrColorSpace));
    }

    /**
     * Returns the color space type of the Profile Connection Space (PCS).
     * Returns one of the color space type constants defined by the
     * ColorSpace class.  This is the "output" color space of the
     * profile.  For an input, display, or output profile useful
     * for tagging colors or images, this will be either TYPE_XYZ or
     * TYPE_Lab and should be interpreted as the corresponding specific
     * color space defined in the ICC specification.  For a device
     * link profile, this could be any of the color space type constants.
     * @return One of the color space type constants defined in the
     * <CODE>ColorSpace</CODE> class.
     */
    public int getPCSType() {
        return MatrixCMM.colorSpaceType(MatrixCMM.getInt(data, icHdrPcs));
    }

    /**
     * Write this ICC_Profile to a file.
     *
     * @param fileName The file to write the profile data to.
     *
     * @exception IOException If the file cannot be opened for writing
     * or an I/O error occurs while writing to the file.
     */
    public void write(String fileName) throws IOException {
        FileOutputStream out = new FileOutputStream(fileName);
        try {
            write(out);
        } finally {
            out.close();
        }
    }

    /**
     * Write this ICC_Profile to an OutputStream.
     *
     * @param s The stream to write the profile data to.
     *
     * @exception IOException If an I/O error occurs while writing to the
     * stream.
     */
    public void write(OutputStream s) throws IOException {
        s.write(getData());
    }

    /**
     * Returns a byte array corresponding to the data of this ICC_Profile.
     * @return A byte array that contains the profile data.
     * @see #setData(int, byte[])
     */
    public byte[] getData() {
        synchronized (this) {
            return data.clone();
        }
    }

    /**
     * Returns a particular tagged data element from the profile as
     * a byte array.  Elements are identified by signatures
     * as defined in the ICC specification.  The signature
     * icSigHead can be used to get the header.  This method is useful
     * for advanced applets or applications which need to access
     * profile data directly.
     *
     * @param tagSignature The ICC tag signature for the data element you
     * want to get.
     *
     * @return A byte array that contains the tagged data element. Returns
     * <code>null</code> if the specified tag doesn't exist.
     * @see #setData(int, byte[])
     */
    public byte[] getData(int tagSignature) {
        synchronized (this) {
            if (tagSignature == icSigHead) {
                byte[] header = new byte[MatrixCMM.HEADER_SIZE];
                System.arraycopy(data, 0, header, 0, header.length);
                return header;
            }
            return MatrixCMM.tag(data, tagSignature);
        }
    }

    /**
     * Sets a particular tagged data element in the profile from
     * a byte array. The array should contain data in a format, corresponded
     * to the {@code tagSignature} as defined in the ICC specification, section 10.
     * This method is useful for advanced applets or applications which need to
     * access profile data directly.
     *
     * @param tagSignature The ICC tag signature for the data element
     * you want to set.
     * @param tagData the data to set for the specified tag signature
     * @throws IllegalArgumentException if {@code tagSignature} is not a signature
     *         as defined in the ICC specification.
     * @throws IllegalArgumentException if a content of the {@code tagData}
     *         array can not be interpreted as valid tag data, corresponding
     *         to the {@code tagSignature}.
     * @see #getData
     */
    public void setData(int tagSignature, byte[] tagData) {
        if (tagData == null) {
            throw new NullPointerException("tagData");
        }
        synchronized (this) {
            data = tagSignature == icSigHead ? MatrixCMM.withHeader(data, tagData)
                : MatrixCMM.withTag(data, tagSignature, tagData);
        }
        // Transforms are cached per profile; a changed profile must be re-read.
        MatrixCMM.invalidate(this);
    }

    /**
     * Returns the number of color components in the "input" color
     * space of this profile.  For example if the color space type
     * of this profile is TYPE_RGB, then this method will return 3.
     *
     * @return The number of color components in the profile's input
     * color space.
     *
     * @throws ProfileDataException if color space is in the profile
     *         is invalid
     */
    public int getNumComponents() {
        int space = MatrixCMM.getInt(data, icHdrColorSpace);
        try {
            return MatrixCMM.componentCount(MatrixCMM.colorSpaceType(space));
        } catch (IllegalArgumentException e) {
            throw new ProfileDataException("invalid ICC color space");
        }
    }

    /** Returns a float array of length 3 containing the X, Y, and Z components of the mediaWhitePointTag. */
    float[] getMediaWhitePoint() {
        return getXYZTag(icSigMediaWhitePointTag);
    }

    /** Returns a float array of length 3 containing the X, Y, and Z components encoded in an XYZType tag. */
    float[] getXYZTag(int theTagSignature) {
        byte[] tag = getData(theTagSignature);
        if (tag == null) {
            throw new ProfileDataException("ICC profile lacks tag " + Integer.toHexString(theTagSignature));
        }
        double[] v = MatrixCMM.readXYZ(tag);
        return new float[] {(float) v[0], (float) v[1], (float) v[2]};
    }

    /**
     * Returns a gamma value representing a tone reproduction curve (TRC).
     * If the profile represents the TRC as a table rather than a single gamma
     * value, then an exception is thrown.
     */
    float getGamma(int theTagSignature) {
        byte[] tag = getData(theTagSignature);
        if (tag == null) {
            throw new ProfileDataException("ICC profile lacks tag " + Integer.toHexString(theTagSignature));
        }
        if (MatrixCMM.getInt(tag, icTagType) != MatrixCMM.sig("curv") || MatrixCMM.getInt(tag, icCurveCount) != 1) {
            throw new ProfileDataException("TRC is not a gamma");
        }
        int gamma = ((tag[icCurveData] & 0xff) << 8) | (tag[icCurveData + 1] & 0xff);
        return gamma / 256.0f;
    }

    /**
     * Returns the TRC as an array of shorts.  If the profile has
     * specified the TRC as linear (gamma = 1.0) or as a simple gamma
     * value, this method throws an exception.
     */
    short[] getTRC(int theTagSignature) {
        byte[] tag = getData(theTagSignature);
        if (tag == null) {
            throw new ProfileDataException("ICC profile lacks tag " + Integer.toHexString(theTagSignature));
        }
        int count = MatrixCMM.getInt(tag, icCurveCount);
        if (MatrixCMM.getInt(tag, icTagType) != MatrixCMM.sig("curv") || count == 1) {
            throw new ProfileDataException("TRC is not a table");
        }
        short[] trc = new short[count];
        for (int i = 0, index = icCurveData; i < count; i++, index += 2) {
            trc[i] = (short) (((tag[index] & 0xff) << 8) | (tag[index + 1] & 0xff));
        }
        return trc;
    }

    private static final String[] CS_NAMES = {"CS_sRGB", "CS_CIEXYZ", "CS_PYCC", "CS_GRAY", "CS_LINEAR_RGB"};

    /**
     * Writes default fields to the stream, then a string for the standard
     * profile this one is ({@code "CS_sRGB"} etc.) or null, then the profile
     * data (null for a standard profile).
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        String csName = predefined == 0 ? null : CS_NAMES[predefined - ColorSpace.CS_sRGB];
        s.writeObject(csName);
        s.writeObject(csName == null ? getData() : null);
    }

    private transient ICC_Profile resolvedDeserializedProfile;

    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        String csName = (String) s.readObject();
        byte[] bytes = (byte[]) s.readObject();
        int cspace = 0;
        if (csName != null) {
            for (int i = 0; i < CS_NAMES.length; i++) {
                if (CS_NAMES[i].equals(csName)) {
                    cspace = ColorSpace.CS_sRGB + i;
                }
            }
        }
        if (cspace != 0) {
            resolvedDeserializedProfile = getInstance(cspace);
        } else {
            resolvedDeserializedProfile = getInstance(bytes);
        }
    }

    /**
     * Resolves instances being deserialized into instances registered
     * with CMM.
     * @return ICC_Profile object for profile registered with CMM.
     * @throws ObjectStreamException
     *     never thrown, but mandated by the serialization spec.
     * @since 1.3
     */
    protected Object readResolve() throws ObjectStreamException {
        return resolvedDeserializedProfile;
    }
}
