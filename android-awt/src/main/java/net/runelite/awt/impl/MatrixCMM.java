/*
 * Copyright (c) 2026, Tyler Bochard
 * All rights reserved.
 *
 * Part of android-awt, the Android shadow of the desktop JDK surface used by
 * runelite-android. Original work of this project, not derived from OpenJDK;
 * licensed under the BSD 2-Clause License (see the LICENSE file at the
 * repository root). Files ported from OpenJDK carry the OpenJDK header instead;
 * see android-awt/LICENSE.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.awt.impl;

import java.awt.color.CMMException;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.LinkedHashMap;
import java.util.Map;

import sun.java2d.cmm.ColorTransform;
import sun.java2d.cmm.PCMM;
import sun.java2d.cmm.Profile;

/**
 * The colour management module of the Android shadow: desktop plugs lcms in
 * behind {@code sun.java2d.cmm.PCMM}; Android has no native CMM, so this class
 * fills the same interface in Java.
 * <p>
 * What is exact: matrix/TRC RGB profiles (sRGB, linear RGB, Adobe RGB, any
 * display profile with {@code rXYZ/gXYZ/bXYZ} colorants and {@code curv} or
 * {@code para} tone curves), gray profiles with a {@code kTRC}, the CIEXYZ
 * connection space and CIELAB.  What is approximated: every other profile type.
 * LUT-based transforms ({@code A2B0/B2A0}, {@code mAB/mBA}) are not evaluated;
 * an RGB profile without colorants is treated as sRGB, a gray profile without a
 * TRC as linear gray, CMYK/CMY/YCbCr/HSV/HLS data are converted with the naive
 * textbook formulas through sRGB, and the PhotoYCC space of {@code CS_PYCC}
 * uses the Kodak encoding equations over Rec.709 primaries.  Rendering intents
 * are ignored: every transform is media-relative colorimetric, which is what
 * the matrix form of a profile expresses anyway.  Colour space types with no
 * formula ({@code TYPE_2CLR}..{@code TYPE_FCLR}, Luv, Yxy) raise a
 * {@link CMMException} naming the type.
 * <p>
 * The five standard profiles of {@link ColorSpace#getInstance(int)} are built
 * here as ICC byte streams ({@link #standardProfile(String)}) so that
 * {@link ICC_Profile#getData()} and the tag accessors see the same data a
 * desktop {@code .pf} file would carry.
 */
public final class MatrixCMM implements PCMM {

    public static final MatrixCMM INSTANCE = new MatrixCMM();

    static final double D50_X = 0.9642, D50_Y = 1.0, D50_Z = 0.8249;
    /** Maximum of the 16-bit PCS XYZ encoding (u1Fixed15: 0x8000 is 1.0). */
    static final double ALMOST_TWO = 1.0 + 32767.0 / 32768.0;

    private static final int SIG_ACSP = 0x61637370;
    private static final int SIG_XYZ_TYPE = 0x58595A20;
    private static final int SIG_CURV = 0x63757276;
    private static final int SIG_PARA = 0x70617261;
    private static final int SIG_DESC = 0x64657363;
    private static final int SIG_MLUC = 0x6D6C7563;
    private static final int SIG_TEXT = 0x74657874;

    /* Colour space signatures beyond the ones ICC_Profile exposes by name. */
    private static final int SIG_RGB = 0x52474220, SIG_GRAY = 0x47524159, SIG_XYZ = 0x58595A20,
        SIG_LAB = 0x4C616220, SIG_CMYK = 0x434D594B, SIG_CMY = 0x434D5920, SIG_YCBCR = 0x59436272,
        SIG_HSV = 0x48535620, SIG_HLS = 0x484C5320, SIG_3CLR = 0x33434C52;

    private MatrixCMM() {
    }

    // ------------------------------------------------------------------ profiles

    /** A parsed profile: the 128-byte header and the tag table, both editable through setData. */
    static final class IccProfile extends Profile {
        final byte[] header;
        final LinkedHashMap<Integer, byte[]> tags;

        IccProfile(byte[] header, LinkedHashMap<Integer, byte[]> tags) {
            super(0L);
            this.header = header;
            this.tags = tags;
        }

        int size() {
            int size = 128 + 4 + 12 * tags.size();
            for (byte[] tag : tags.values()) {
                size += (tag.length + 3) & ~3;
            }
            return size;
        }

        byte[] serialize() {
            byte[] out = new byte[size()];
            System.arraycopy(header, 0, out, 0, 128);
            putInt(out, 0, out.length);
            putInt(out, 128, tags.size());
            int entry = 132, data = 132 + 12 * tags.size();
            for (Map.Entry<Integer, byte[]> tag : tags.entrySet()) {
                putInt(out, entry, tag.getKey());
                putInt(out, entry + 4, data);
                putInt(out, entry + 8, tag.getValue().length);
                System.arraycopy(tag.getValue(), 0, out, data, tag.getValue().length);
                data += (tag.getValue().length + 3) & ~3;
                entry += 12;
            }
            return out;
        }

        int colorSpaceSignature() {
            return getInt(header, ICC_Profile.icHdrColorSpace);
        }
    }

    @Override
    public Profile loadProfile(byte[] data) {
        if (data == null || data.length < 132 || getInt(data, ICC_Profile.icHdrMagic) != SIG_ACSP) {
            throw new CMMException("Invalid ICC Profile Data");
        }
        int count = getInt(data, 128);
        if (count < 0 || 132 + 12L * count > data.length) {
            throw new CMMException("Invalid ICC Profile Data: tag table");
        }
        byte[] header = new byte[128];
        System.arraycopy(data, 0, header, 0, 128);
        LinkedHashMap<Integer, byte[]> tags = new LinkedHashMap<Integer, byte[]>();
        for (int i = 0; i < count; i++) {
            int entry = 132 + 12 * i;
            int sig = getInt(data, entry), offset = getInt(data, entry + 4), size = getInt(data, entry + 8);
            if (offset < 0 || size < 0 || (long) offset + size > data.length) {
                throw new CMMException("Invalid ICC Profile Data: tag " + signature(sig));
            }
            byte[] tag = new byte[size];
            System.arraycopy(data, offset, tag, 0, size);
            tags.put(sig, tag);
        }
        return new IccProfile(header, tags);
    }

    @Override
    public void freeProfile(Profile p) {
        // Nothing is held outside the Java heap.
    }

    @Override
    public int getProfileSize(Profile p) {
        return profile(p).size();
    }

    @Override
    public void getProfileData(Profile p, byte[] data) {
        byte[] bytes = profile(p).serialize();
        System.arraycopy(bytes, 0, data, 0, Math.min(bytes.length, data.length));
    }

    @Override
    public int getTagSize(Profile p, int tagSignature) {
        return tag(profile(p), tagSignature).length;
    }

    @Override
    public void getTagData(Profile p, int tagSignature, byte[] data) {
        byte[] tag = tag(profile(p), tagSignature);
        System.arraycopy(tag, 0, data, 0, Math.min(tag.length, data.length));
    }

    @Override
    public void setTagData(Profile p, int tagSignature, byte[] data) {
        IccProfile profile = profile(p);
        if (tagSignature == ICC_Profile.icSigHead) {
            if (data == null || data.length != 128 || getInt(data, ICC_Profile.icHdrMagic) != SIG_ACSP) {
                throw new IllegalArgumentException("Invalid ICC profile header");
            }
            System.arraycopy(data, 0, profile.header, 0, 128);
            return;
        }
        if (data == null || data.length < 8) {
            throw new IllegalArgumentException("Invalid tag data for " + signature(tagSignature));
        }
        profile.tags.put(tagSignature, data.clone());
    }

    private static IccProfile profile(Profile p) {
        if (!(p instanceof IccProfile)) {
            throw new CMMException("Invalid profile: not loaded by this CMM");
        }
        return (IccProfile) p;
    }

    private static byte[] tag(IccProfile profile, int signature) {
        if (signature == ICC_Profile.icSigHead) {
            return profile.header;
        }
        byte[] tag = profile.tags.get(signature);
        if (tag == null) {
            throw new CMMException("Tag not found: " + signature(signature));
        }
        return tag;
    }

    // ---------------------------------------------------------------- transforms

    @Override
    public ColorTransform createTransform(ICC_Profile profile, int renderType, int transformType) {
        IccProfile loaded = (IccProfile) loadProfile(profile.getData());
        return new Chain(new Model[] {modelOf(loaded)}, new int[] {transformType});
    }

    @Override
    public ColorTransform createTransform(int renderType, ICC_Profile... profiles) {
        if (profiles.length == 0) {
            throw new IllegalArgumentException("No profiles");
        }
        ColorTransform[] transforms = new ColorTransform[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            int which = i == 0 ? ColorTransform.In : i == profiles.length - 1 ? ColorTransform.Out
                : ColorTransform.Simulation;
            transforms[i] = createTransform(profiles[i], renderType, which);
        }
        return transforms.length == 1 ? transforms[0] : createTransform(transforms);
    }

    @Override
    public ColorTransform createTransform(ColorTransform[] transforms) {
        int total = 0;
        for (ColorTransform t : transforms) {
            total += ((Chain) t).models.length;
        }
        Model[] models = new Model[total];
        int[] which = new int[total];
        int i = 0;
        for (ColorTransform t : transforms) {
            Chain chain = (Chain) t;
            System.arraycopy(chain.models, 0, models, i, chain.models.length);
            System.arraycopy(chain.which, 0, which, i, chain.which.length);
            i += chain.models.length;
        }
        return new Chain(models, which);
    }

    /**
     * One profile's mapping between its device values and the D50 XYZ connection
     * space.  Device values are in the colour space's native range: 0..1 for
     * most types, 0..ALMOST_TWO for XYZ, 0..100 / -128..127 for Lab.
     */
    abstract static class Model {
        final int type;
        final int numComponents;

        Model(int type, int numComponents) {
            this.type = type;
            this.numComponents = numComponents;
        }

        abstract void toPCS(float[] dev, float[] xyz);

        abstract void fromPCS(float[] xyz, float[] dev);

        float min(int i) {
            return type == ColorSpace.TYPE_Lab && i > 0 ? -128f : 0f;
        }

        float max(int i) {
            if (type == ColorSpace.TYPE_XYZ) {
                return (float) ALMOST_TWO;
            }
            if (type == ColorSpace.TYPE_Lab) {
                return i == 0 ? 100f : 127f;
            }
            return 1f;
        }
    }

    /** Profiles chained device -> PCS -> ... -> device; a single In or Out element also works alone. */
    static final class Chain implements ColorTransform {
        final Model[] models;
        final int[] which;
        final Model in, out;
        final int numIn, numOut;

        Chain(Model[] models, int[] which) {
            this.models = models;
            this.which = which;
            in = models[0];
            out = models[models.length - 1];
            numIn = which[0] == ColorTransform.Out ? 3 : in.numComponents;
            numOut = models.length == 1 && which[0] == ColorTransform.In ? 3 : out.numComponents;
        }

        float inMin(int i) { return which[0] == ColorTransform.Out ? 0f : in.min(i); }
        float inMax(int i) { return which[0] == ColorTransform.Out ? (float) ALMOST_TWO : in.max(i); }
        float outMin(int i) { return numOut == out.numComponents && models.length > 1 ? out.min(i) : models.length == 1 && which[0] == ColorTransform.In ? 0f : out.min(i); }
        float outMax(int i) { return models.length == 1 && which[0] == ColorTransform.In ? (float) ALMOST_TWO : out.max(i); }

        void convert(float[] dev, float[] result) {
            float[] xyz = new float[3];
            int first = 0;
            if (which[0] == ColorTransform.Out) {
                System.arraycopy(dev, 0, xyz, 0, 3);
            } else {
                models[0].toPCS(dev, xyz);
                first = 1;
            }
            for (int i = first; i < models.length - 1; i++) {
                float[] tmp = new float[models[i].numComponents];
                models[i].fromPCS(xyz, tmp);
                models[i].toPCS(tmp, xyz);
            }
            if (models.length == 1 && which[0] == ColorTransform.In) {
                System.arraycopy(xyz, 0, result, 0, 3);
            } else if (first <= models.length - 1) {
                models[models.length - 1].fromPCS(xyz, result);
            }
        }

        @Override
        public int getNumInComponents() {
            return numIn;
        }

        @Override
        public int getNumOutComponents() {
            return numOut;
        }

        @Override
        public short[] colorConvert(short[] src, short[] dest) {
            int pixels = src.length / numIn;
            if (dest == null) {
                dest = new short[pixels * numOut];
            }
            float[] dev = new float[numIn], res = new float[numOut];
            for (int p = 0, s = 0, d = 0; p < pixels; p++) {
                for (int i = 0; i < numIn; i++) {
                    dev[i] = (src[s++] & 0xffff) / 65535f * (inMax(i) - inMin(i)) + inMin(i);
                }
                convert(dev, res);
                for (int i = 0; i < numOut; i++) {
                    dest[d++] = (short) quantize((res[i] - outMin(i)) / (outMax(i) - outMin(i)), 65535);
                }
            }
            return dest;
        }

        @Override
        public byte[] colorConvert(byte[] src, byte[] dest) {
            int pixels = src.length / numIn;
            if (dest == null) {
                dest = new byte[pixels * numOut];
            }
            float[] dev = new float[numIn], res = new float[numOut];
            for (int p = 0, s = 0, d = 0; p < pixels; p++) {
                for (int i = 0; i < numIn; i++) {
                    dev[i] = (src[s++] & 0xff) / 255f * (inMax(i) - inMin(i)) + inMin(i);
                }
                convert(dev, res);
                for (int i = 0; i < numOut; i++) {
                    dest[d++] = (byte) quantize((res[i] - outMin(i)) / (outMax(i) - outMin(i)), 255);
                }
            }
            return dest;
        }

        @Override
        public void colorConvert(BufferedImage src, BufferedImage dst) {
            ColorModel scm = src.getColorModel(), dcm = dst.getColorModel();
            Raster sr = src.getRaster();
            WritableRaster dr = dst.getRaster();
            int w = src.getWidth(), h = src.getHeight();
            int sn = scm.getNumColorComponents(), dn = dcm.getNumColorComponents();
            boolean sAlpha = scm.hasAlpha(), dAlpha = dcm.hasAlpha();
            float[] norm = null, dev = new float[numIn], res = new float[numOut];
            float[] dnorm = new float[dn + (dAlpha ? 1 : 0)];
            Object sp = null, dp = null;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    sp = sr.getDataElements(sr.getMinX() + x, sr.getMinY() + y, sp);
                    norm = scm.getNormalizedComponents(sp, norm, 0);
                    for (int i = 0; i < numIn && i < sn; i++) {
                        dev[i] = norm[i];
                    }
                    convert(dev, res);
                    for (int i = 0; i < dn && i < numOut; i++) {
                        dnorm[i] = Math.max(outMin(i), Math.min(outMax(i), res[i]));
                    }
                    if (dAlpha) {
                        dnorm[dn] = sAlpha ? norm[sn] : 1f;
                    }
                    dp = dcm.getDataElements(dnorm, 0, dp);
                    dr.setDataElements(dr.getMinX() + x, dr.getMinY() + y, dp);
                }
            }
        }

        @Override
        public void colorConvert(Raster src, WritableRaster dst) {
            float[] sMin = new float[numIn], sMax = new float[numIn], dMin = new float[numOut], dMax = new float[numOut];
            for (int i = 0; i < numIn; i++) {
                sMin[i] = inMin(i);
                sMax[i] = inMax(i);
            }
            for (int i = 0; i < numOut; i++) {
                dMin[i] = outMin(i);
                dMax[i] = outMax(i);
            }
            colorConvert(src, dst, sMin, sMax, dMin, dMax);
        }

        @Override
        public void colorConvert(Raster src, WritableRaster dst, float[] srcMinVal, float[] srcMaxVal,
                                 float[] dstMinVal, float[] dstMaxVal) {
            // The shadow's rasters carry byte, ushort, short and int samples only, so every
            // sample is an integer scaled by its band size (SHORT by 32767, as ColorConvertOp does).
            SampleModel ssm = src.getSampleModel(), dsm = dst.getSampleModel();
            int w = src.getWidth(), h = src.getHeight();
            float[] sScale = new float[numIn], dScale = new float[numOut];
            for (int i = 0; i < numIn; i++) {
                sScale[i] = ssm.getTransferType() == DataBuffer.TYPE_SHORT ? 32767f : (float) ((1L << ssm.getSampleSize(i)) - 1);
            }
            for (int i = 0; i < numOut; i++) {
                dScale[i] = dsm.getTransferType() == DataBuffer.TYPE_SHORT ? 32767f : (float) ((1L << dsm.getSampleSize(i)) - 1);
            }
            float[] dev = new float[numIn], res = new float[numOut];
            for (int y = 0; y < h; y++) {
                int ys = src.getMinY() + y, yd = dst.getMinY() + y;
                for (int x = 0; x < w; x++) {
                    int xs = src.getMinX() + x, xd = dst.getMinX() + x;
                    for (int i = 0; i < numIn; i++) {
                        dev[i] = src.getSample(xs, ys, i) / sScale[i] * (srcMaxVal[i] - srcMinVal[i]) + srcMinVal[i];
                    }
                    convert(dev, res);
                    for (int i = 0; i < numOut; i++) {
                        float v = (res[i] - dstMinVal[i]) / (dstMaxVal[i] - dstMinVal[i]);
                        dst.setSample(xd, yd, i, quantize(v, (int) dScale[i]));
                    }
                }
            }
        }
    }

    static int quantize(float unit, int max) {
        if (!(unit > 0f)) {
            return 0;
        }
        if (unit >= 1f) {
            return max;
        }
        return (int) (unit * max + 0.5f);
    }

    // -------------------------------------------------------------------- models

    static Model modelOf(IccProfile profile) {
        int space = profile.colorSpaceSignature();
        switch (space) {
            case SIG_RGB:
                if (has(profile, ICC_Profile.icSigRedColorantTag) && has(profile, ICC_Profile.icSigGreenColorantTag)
                    && has(profile, ICC_Profile.icSigBlueColorantTag) && has(profile, ICC_Profile.icSigRedTRCTag)
                    && has(profile, ICC_Profile.icSigGreenTRCTag) && has(profile, ICC_Profile.icSigBlueTRCTag)) {
                    return new MatrixTrc(
                        new Curve[] {Curve.parse(profile.tags.get(ICC_Profile.icSigRedTRCTag)),
                                     Curve.parse(profile.tags.get(ICC_Profile.icSigGreenTRCTag)),
                                     Curve.parse(profile.tags.get(ICC_Profile.icSigBlueTRCTag))},
                        colorants(profile));
                }
                return SRGB; // LUT-based RGB profile: approximated by sRGB
            case SIG_GRAY:
                return new GrayTrc(has(profile, ICC_Profile.icSigGrayTRCTag)
                    ? Curve.parse(profile.tags.get(ICC_Profile.icSigGrayTRCTag)) : Curve.LINEAR);
            case SIG_XYZ:
                return new Identity();
            case SIG_LAB:
                return new Lab();
            case SIG_CMYK:
                return new Cmyk();
            case SIG_CMY:
                return new Cmy();
            case SIG_YCBCR:
                return new YCbCr();
            case SIG_HSV:
                return new Hsv();
            case SIG_HLS:
                return new Hls();
            case SIG_3CLR:
                if (description(profile).contains("YCC")) {
                    return new Pycc();
                }
                break;
            default:
                break;
        }
        throw new CMMException("MatrixCMM: no transform for colour space '" + signature(space)
            + "' (LUT-based ICC profiles are not evaluated on Android)");
    }

    private static boolean has(IccProfile profile, int tag) {
        return profile.tags.containsKey(tag);
    }

    private static double[] colorants(IccProfile profile) {
        double[] m = new double[9];
        int[] tags = {ICC_Profile.icSigRedColorantTag, ICC_Profile.icSigGreenColorantTag, ICC_Profile.icSigBlueColorantTag};
        for (int c = 0; c < 3; c++) {
            byte[] tag = profile.tags.get(tags[c]);
            if (tag.length < 20) {
                throw new CMMException("MatrixCMM: short colorant tag");
            }
            for (int row = 0; row < 3; row++) {
                m[row * 3 + c] = getInt(tag, 8 + 4 * row) / 65536.0;
            }
        }
        return m;
    }

    static String description(IccProfile profile) {
        byte[] tag = profile.tags.get(ICC_Profile.icSigProfileDescriptionTag);
        if (tag == null || tag.length < 12) {
            return "";
        }
        try {
            int type = getInt(tag, 0);
            if (type == SIG_DESC) {
                int count = getInt(tag, 8);
                return new String(tag, 12, Math.max(0, Math.min(count - 1, tag.length - 12)), "US-ASCII");
            }
            if (type == SIG_MLUC && tag.length >= 28) {
                int length = getInt(tag, 20), offset = getInt(tag, 24);
                return new String(tag, offset, Math.min(length, tag.length - offset), "UTF-16BE");
            }
            if (type == SIG_TEXT) {
                return new String(tag, 8, tag.length - 8, "US-ASCII").trim();
            }
        } catch (java.io.UnsupportedEncodingException e) {
            // ASCII and UTF-16 are mandatory charsets.
        } catch (RuntimeException e) {
            // A malformed description is not worth failing the transform over.
        }
        return "";
    }

    /** An ICC tone reproduction curve: gamma, sampled table or parametric. */
    static final class Curve {
        static final Curve LINEAR = new Curve(1.0, null, -1, null);
        final double gamma;
        final short[] table;
        final int paraType;
        final double[] para;

        Curve(double gamma, short[] table, int paraType, double[] para) {
            this.gamma = gamma;
            this.table = table;
            this.paraType = paraType;
            this.para = para;
        }

        static Curve parse(byte[] tag) {
            if (tag == null || tag.length < 12) {
                throw new CMMException("MatrixCMM: short curve tag");
            }
            int type = getInt(tag, 0);
            if (type == SIG_CURV) {
                int count = getInt(tag, ICC_Profile.icCurveCount);
                if (count == 0) {
                    return LINEAR;
                }
                if (count == 1) {
                    return new Curve((getShort(tag, ICC_Profile.icCurveData) & 0xffff) / 256.0, null, -1, null);
                }
                if (count < 0 || 12 + 2L * count > tag.length) {
                    throw new CMMException("MatrixCMM: curve table out of bounds");
                }
                short[] table = new short[count];
                for (int i = 0; i < count; i++) {
                    table[i] = getShort(tag, 12 + 2 * i);
                }
                return new Curve(0, table, -1, null);
            }
            if (type == SIG_PARA) {
                int function = getShort(tag, 8) & 0xffff;
                int[] counts = {1, 3, 4, 5, 7};
                if (function >= counts.length || 12 + 4 * counts[function] > tag.length) {
                    throw new CMMException("MatrixCMM: unsupported parametric curve " + function);
                }
                double[] para = new double[counts[function]];
                for (int i = 0; i < para.length; i++) {
                    para[i] = getInt(tag, 12 + 4 * i) / 65536.0;
                }
                return new Curve(0, null, function, para);
            }
            throw new CMMException("MatrixCMM: unknown curve type '" + signature(type) + "'");
        }

        /** Device value in 0..1 to linear light in 0..1. */
        double eval(double x) {
            x = x < 0 ? 0 : x > 1 ? 1 : x;
            if (table != null) {
                double pos = x * (table.length - 1);
                int i = (int) pos;
                if (i >= table.length - 1) {
                    return (table[table.length - 1] & 0xffff) / 65535.0;
                }
                double a = (table[i] & 0xffff) / 65535.0, b = (table[i + 1] & 0xffff) / 65535.0;
                return a + (b - a) * (pos - i);
            }
            if (para != null) {
                double g = para[0];
                switch (paraType) {
                    case 0:
                        return Math.pow(x, g);
                    case 1:
                        return x >= -para[2] / para[1] ? Math.pow(para[1] * x + para[2], g) : 0;
                    case 2:
                        return x >= -para[2] / para[1] ? Math.pow(para[1] * x + para[2], g) + para[3] : para[3];
                    case 3:
                        return x >= para[4] ? Math.pow(para[1] * x + para[2], g) : para[3] * x;
                    default:
                        return x >= para[4] ? Math.pow(para[1] * x + para[2], g) + para[5] : para[3] * x + para[6];
                }
            }
            return gamma == 1.0 ? x : Math.pow(x, gamma);
        }

        /** Linear light in 0..1 to the device value in 0..1; tables and parametric curves are inverted numerically. */
        double inverse(double y) {
            y = y < 0 ? 0 : y > 1 ? 1 : y;
            if (table == null && para == null) {
                return gamma == 1.0 ? y : Math.pow(y, 1.0 / gamma);
            }
            if (table != null) {
                int lo = 0, hi = table.length - 1;
                int target = (int) (y * 65535.0 + 0.5);
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if ((table[mid] & 0xffff) < target) {
                        lo = mid + 1;
                    } else {
                        hi = mid;
                    }
                }
                if (lo == 0) {
                    return 0;
                }
                double a = (table[lo - 1] & 0xffff), b = (table[lo] & 0xffff);
                double frac = b == a ? 0 : (target - a) / (b - a);
                return (lo - 1 + frac) / (table.length - 1);
            }
            double lo = 0, hi = 1;
            for (int i = 0; i < 40; i++) {
                double mid = (lo + hi) / 2;
                if (eval(mid) < y) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            return (lo + hi) / 2;
        }
    }

    /** RGB device values linearised by three curves and mapped to XYZ by the colorant matrix. */
    static final class MatrixTrc extends Model {
        final Curve[] curves;
        final double[] m, inv;

        MatrixTrc(Curve[] curves, double[] m) {
            super(ColorSpace.TYPE_RGB, 3);
            this.curves = curves;
            this.m = m;
            this.inv = invert(m);
        }

        void linearToPCS(double r, double g, double b, float[] xyz) {
            xyz[0] = (float) (m[0] * r + m[1] * g + m[2] * b);
            xyz[1] = (float) (m[3] * r + m[4] * g + m[5] * b);
            xyz[2] = (float) (m[6] * r + m[7] * g + m[8] * b);
        }

        void pcsToLinear(float[] xyz, double[] rgb) {
            for (int row = 0; row < 3; row++) {
                double v = inv[row * 3] * xyz[0] + inv[row * 3 + 1] * xyz[1] + inv[row * 3 + 2] * xyz[2];
                rgb[row] = v < 0 ? 0 : v > 1 ? 1 : v;
            }
        }

        @Override
        void toPCS(float[] dev, float[] xyz) {
            linearToPCS(curves[0].eval(dev[0]), curves[1].eval(dev[1]), curves[2].eval(dev[2]), xyz);
        }

        @Override
        void fromPCS(float[] xyz, float[] dev) {
            double[] rgb = new double[3];
            pcsToLinear(xyz, rgb);
            for (int i = 0; i < 3; i++) {
                dev[i] = (float) curves[i].inverse(rgb[i]);
            }
        }
    }

    static final class GrayTrc extends Model {
        final Curve curve;

        GrayTrc(Curve curve) {
            super(ColorSpace.TYPE_GRAY, 1);
            this.curve = curve;
        }

        @Override
        void toPCS(float[] dev, float[] xyz) {
            double y = curve.eval(dev[0]);
            xyz[0] = (float) (D50_X * y);
            xyz[1] = (float) y;
            xyz[2] = (float) (D50_Z * y);
        }

        @Override
        void fromPCS(float[] xyz, float[] dev) {
            dev[0] = (float) curve.inverse(xyz[1]);
        }
    }

    static final class Identity extends Model {
        Identity() {
            super(ColorSpace.TYPE_XYZ, 3);
        }

        @Override
        void toPCS(float[] dev, float[] xyz) {
            System.arraycopy(dev, 0, xyz, 0, 3);
        }

        @Override
        void fromPCS(float[] xyz, float[] dev) {
            for (int i = 0; i < 3; i++) {
                dev[i] = (float) Math.max(0, Math.min(ALMOST_TWO, xyz[i]));
            }
        }
    }

    static final class Lab extends Model {
        static final double EPSILON = 216.0 / 24389.0, KAPPA = 24389.0 / 27.0;

        Lab() {
            super(ColorSpace.TYPE_Lab, 3);
        }

        private static double f(double t) {
            return t > EPSILON ? Math.cbrt(t) : (KAPPA * t + 16) / 116;
        }

        private static double fInverse(double v) {
            double cube = v * v * v;
            return cube > EPSILON ? cube : (116 * v - 16) / KAPPA;
        }

        @Override
        void toPCS(float[] dev, float[] xyz) {
            double fy = (dev[0] + 16) / 116, fx = fy + dev[1] / 500, fz = fy - dev[2] / 200;
            xyz[0] = (float) (D50_X * fInverse(fx));
            xyz[1] = (float) (dev[0] > KAPPA * EPSILON ? fy * fy * fy : dev[0] / KAPPA);
            xyz[2] = (float) (D50_Z * fInverse(fz));
        }

        @Override
        void fromPCS(float[] xyz, float[] dev) {
            double fx = f(xyz[0] / D50_X), fy = f(xyz[1] / D50_Y), fz = f(xyz[2] / D50_Z);
            dev[0] = (float) Math.max(0, Math.min(100, 116 * fy - 16));
            dev[1] = (float) Math.max(-128, Math.min(127, 500 * (fx - fy)));
            dev[2] = (float) Math.max(-128, Math.min(127, 200 * (fy - fz)));
        }
    }

    /** Spaces with no colorimetric definition of their own: converted through linear sRGB primaries. */
    abstract static class ViaRgb extends Model {
        ViaRgb(int type, int numComponents) {
            super(type, numComponents);
        }

        /** Device values to linear RGB (sRGB/Rec.709 primaries), each 0..1. */
        abstract void toLinearRgb(float[] dev, double[] rgb);

        abstract void fromLinearRgb(double[] rgb, float[] dev);

        @Override
        void toPCS(float[] dev, float[] xyz) {
            double[] rgb = new double[3];
            toLinearRgb(dev, rgb);
            SRGB.linearToPCS(rgb[0], rgb[1], rgb[2], xyz);
        }

        @Override
        void fromPCS(float[] xyz, float[] dev) {
            double[] rgb = new double[3];
            SRGB.pcsToLinear(xyz, rgb);
            fromLinearRgb(rgb, dev);
        }

        static double encode(double linear) {
            return SRGB.curves[0].inverse(linear);
        }

        static double decode(double encoded) {
            return SRGB.curves[0].eval(encoded);
        }

        static float clamp01(double v) {
            return (float) (v < 0 ? 0 : v > 1 ? 1 : v);
        }
    }

    static final class Cmyk extends ViaRgb {
        Cmyk() {
            super(ColorSpace.TYPE_CMYK, 4);
        }

        @Override
        void toLinearRgb(float[] dev, double[] rgb) {
            for (int i = 0; i < 3; i++) {
                rgb[i] = decode((1 - dev[i]) * (1 - dev[3]));
            }
        }

        @Override
        void fromLinearRgb(double[] rgb, float[] dev) {
            double r = encode(rgb[0]), g = encode(rgb[1]), b = encode(rgb[2]);
            double k = 1 - Math.max(r, Math.max(g, b));
            dev[3] = clamp01(k);
            if (k >= 1) {
                dev[0] = dev[1] = dev[2] = 0f;
                return;
            }
            dev[0] = clamp01((1 - r - k) / (1 - k));
            dev[1] = clamp01((1 - g - k) / (1 - k));
            dev[2] = clamp01((1 - b - k) / (1 - k));
        }
    }

    static final class Cmy extends ViaRgb {
        Cmy() {
            super(ColorSpace.TYPE_CMY, 3);
        }

        @Override
        void toLinearRgb(float[] dev, double[] rgb) {
            for (int i = 0; i < 3; i++) {
                rgb[i] = decode(1 - dev[i]);
            }
        }

        @Override
        void fromLinearRgb(double[] rgb, float[] dev) {
            for (int i = 0; i < 3; i++) {
                dev[i] = clamp01(1 - encode(rgb[i]));
            }
        }
    }

    /** ITU-R BT.601 full-range YCbCr over sRGB-encoded components. */
    static final class YCbCr extends ViaRgb {
        YCbCr() {
            super(ColorSpace.TYPE_YCbCr, 3);
        }

        @Override
        void toLinearRgb(float[] dev, double[] rgb) {
            double y = dev[0], cb = dev[1] - 0.5, cr = dev[2] - 0.5;
            rgb[0] = decode(y + 1.402 * cr);
            rgb[1] = decode(y - 0.344136 * cb - 0.714136 * cr);
            rgb[2] = decode(y + 1.772 * cb);
        }

        @Override
        void fromLinearRgb(double[] rgb, float[] dev) {
            double r = encode(rgb[0]), g = encode(rgb[1]), b = encode(rgb[2]);
            dev[0] = clamp01(0.299 * r + 0.587 * g + 0.114 * b);
            dev[1] = clamp01(-0.168736 * r - 0.331264 * g + 0.5 * b + 0.5);
            dev[2] = clamp01(0.5 * r - 0.418688 * g - 0.081312 * b + 0.5);
        }
    }

    static final class Hsv extends ViaRgb {
        Hsv() {
            super(ColorSpace.TYPE_HSV, 3);
        }

        @Override
        void toLinearRgb(float[] dev, double[] rgb) {
            int packed = java.awt.Color.HSBtoRGB(dev[0], dev[1], dev[2]);
            rgb[0] = decode((packed >> 16 & 255) / 255.0);
            rgb[1] = decode((packed >> 8 & 255) / 255.0);
            rgb[2] = decode((packed & 255) / 255.0);
        }

        @Override
        void fromLinearRgb(double[] rgb, float[] dev) {
            float[] hsb = java.awt.Color.RGBtoHSB((int) Math.round(encode(rgb[0]) * 255),
                (int) Math.round(encode(rgb[1]) * 255), (int) Math.round(encode(rgb[2]) * 255), null);
            dev[0] = hsb[0];
            dev[1] = hsb[1];
            dev[2] = hsb[2];
        }
    }

    static final class Hls extends ViaRgb {
        Hls() {
            super(ColorSpace.TYPE_HLS, 3);
        }

        private static double hue(double p, double q, double t) {
            t = t < 0 ? t + 1 : t > 1 ? t - 1 : t;
            if (t < 1.0 / 6) {
                return p + (q - p) * 6 * t;
            }
            if (t < 0.5) {
                return q;
            }
            if (t < 2.0 / 3) {
                return p + (q - p) * (2.0 / 3 - t) * 6;
            }
            return p;
        }

        @Override
        void toLinearRgb(float[] dev, double[] rgb) {
            double h = dev[0], l = dev[1], s = dev[2];
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
            rgb[0] = decode(hue(p, q, h + 1.0 / 3));
            rgb[1] = decode(hue(p, q, h));
            rgb[2] = decode(hue(p, q, h - 1.0 / 3));
        }

        @Override
        void fromLinearRgb(double[] rgb, float[] dev) {
            double r = encode(rgb[0]), g = encode(rgb[1]), b = encode(rgb[2]);
            double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
            double l = (max + min) / 2, h = 0, s = 0;
            if (max != min) {
                double d = max - min;
                s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
                if (max == r) {
                    h = (g - b) / d + (g < b ? 6 : 0);
                } else if (max == g) {
                    h = (b - r) / d + 2;
                } else {
                    h = (r - g) / d + 4;
                }
                h /= 6;
            }
            dev[0] = clamp01(h);
            dev[1] = clamp01(l);
            dev[2] = clamp01(s);
        }
    }

    /**
     * Kodak PhotoYCC as encoded by Photo CD: luma and two chroma channels over
     * Rec.709 non-linear primaries, 8-bit scaled.  The real PYCC.pf is a
     * lookup-table profile; this is its textbook definition.
     */
    static final class Pycc extends ViaRgb {
        Pycc() {
            super(ColorSpace.TYPE_3CLR, 3);
        }

        private static double rec709ToLinear(double v) {
            v = v < 0 ? 0 : v > 1 ? 1 : v;
            return v < 0.081 ? v / 4.5 : Math.pow((v + 0.099) / 1.099, 1 / 0.45);
        }

        private static double linearToRec709(double v) {
            v = v < 0 ? 0 : v > 1 ? 1 : v;
            return v < 0.018 ? 4.5 * v : 1.099 * Math.pow(v, 0.45) - 0.099;
        }

        @Override
        void toLinearRgb(float[] dev, double[] rgb) {
            double luma = 1.3584 * dev[0] * 255, c1 = 2.2179 * (dev[1] * 255 - 156), c2 = 1.8215 * (dev[2] * 255 - 137);
            rgb[0] = rec709ToLinear((luma + c2) / 255);
            rgb[1] = rec709ToLinear((luma - 0.194 * c1 - 0.509 * c2) / 255);
            rgb[2] = rec709ToLinear((luma + c1) / 255);
        }

        @Override
        void fromLinearRgb(double[] rgb, float[] dev) {
            double r = linearToRec709(rgb[0]) * 255, g = linearToRec709(rgb[1]) * 255, b = linearToRec709(rgb[2]) * 255;
            double luma = 0.299 * r + 0.587 * g + 0.114 * b;
            dev[0] = clamp01(luma / 1.3584 / 255);
            dev[1] = clamp01(((b - luma) / 2.2179 + 156) / 255);
            dev[2] = clamp01(((r - luma) / 1.8215 + 137) / 255);
        }
    }

    static double[] invert(double[] m) {
        double a = m[0], b = m[1], c = m[2], d = m[3], e = m[4], f = m[5], g = m[6], h = m[7], i = m[8];
        double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        if (Math.abs(det) < 1e-12) {
            throw new CMMException("MatrixCMM: singular colorant matrix");
        }
        return new double[] {
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det};
    }

    // --------------------------------------------------------- standard profiles

    /* sRGB colorants adapted to D50 (Bradford), the s15Fixed16 values of the IEC 61966-2.1 profile. */
    private static final int[] SRGB_RED = {0x00006FA2, 0x000038F5, 0x00000390};
    private static final int[] SRGB_GREEN = {0x00006299, 0x0000B785, 0x000018DA};
    private static final int[] SRGB_BLUE = {0x000024A0, 0x00000F84, 0x0000B6CF};
    private static final int[] D50 = {0x0000F6D6, 0x00010000, 0x0000D32D};

    static final MatrixTrc SRGB = new MatrixTrc(
        new Curve[] {srgbCurve(), srgbCurve(), srgbCurve()},
        new double[] {SRGB_RED[0] / 65536.0, SRGB_GREEN[0] / 65536.0, SRGB_BLUE[0] / 65536.0,
                      SRGB_RED[1] / 65536.0, SRGB_GREEN[1] / 65536.0, SRGB_BLUE[1] / 65536.0,
                      SRGB_RED[2] / 65536.0, SRGB_GREEN[2] / 65536.0, SRGB_BLUE[2] / 65536.0});

    private static Curve srgbCurve() {
        return new Curve(0, srgbTable(), -1, null);
    }

    /** The sRGB electro-optical transfer function sampled at 1024 points, as the desktop sRGB.pf carries it. */
    private static short[] srgbTable() {
        short[] table = new short[1024];
        for (int i = 0; i < table.length; i++) {
            double v = i / 1023.0;
            double linear = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
            table[i] = (short) (int) (linear * 65535 + 0.5);
        }
        return table;
    }

    private static final Map<String, byte[]> STANDARD = new LinkedHashMap<String, byte[]>();

    /**
     * The ICC byte stream of one of the built-in profiles ({@code sRGB.pf},
     * {@code LINEAR_RGB.pf}, {@code GRAY.pf}, {@code CIEXYZ.pf}, {@code PYCC.pf}),
     * or {@code null} for any other name.
     */
    public static synchronized byte[] standardProfile(String name) {
        if (STANDARD.isEmpty()) {
            STANDARD.put("sRGB.pf", buildRgb("sRGB IEC61966-2.1", srgbTable()));
            STANDARD.put("LINEAR_RGB.pf", buildRgb("Linear RGB (sRGB primaries, gamma 1.0)", null));
            STANDARD.put("GRAY.pf", buildGray());
            STANDARD.put("CIEXYZ.pf", buildXyz());
            STANDARD.put("PYCC.pf", buildPycc());
        }
        byte[] data = STANDARD.get(name);
        return data == null ? null : data.clone();
    }

    private static byte[] header(int deviceClass, int colorSpace) {
        byte[] h = new byte[128];
        putInt(h, ICC_Profile.icHdrVersion, 0x02100000);
        putInt(h, ICC_Profile.icHdrDeviceClass, deviceClass);
        putInt(h, ICC_Profile.icHdrColorSpace, colorSpace);
        putInt(h, ICC_Profile.icHdrPcs, SIG_XYZ);
        putInt(h, ICC_Profile.icHdrMagic, SIG_ACSP);
        putInt(h, ICC_Profile.icHdrIlluminant, D50[0]);
        putInt(h, ICC_Profile.icHdrIlluminant + 4, D50[1]);
        putInt(h, ICC_Profile.icHdrIlluminant + 8, D50[2]);
        return h;
    }

    private static byte[] buildRgb(String description, short[] curve) {
        LinkedHashMap<Integer, byte[]> tags = new LinkedHashMap<Integer, byte[]>();
        tags.put(ICC_Profile.icSigProfileDescriptionTag, descTag(description));
        tags.put(ICC_Profile.icSigCopyrightTag, textTag("No copyright, use freely"));
        tags.put(ICC_Profile.icSigMediaWhitePointTag, xyzTag(D50));
        tags.put(ICC_Profile.icSigMediaBlackPointTag, xyzTag(new int[3]));
        tags.put(ICC_Profile.icSigRedColorantTag, xyzTag(SRGB_RED));
        tags.put(ICC_Profile.icSigGreenColorantTag, xyzTag(SRGB_GREEN));
        tags.put(ICC_Profile.icSigBlueColorantTag, xyzTag(SRGB_BLUE));
        byte[] trc = curve == null ? gammaTag(1.0) : curveTag(curve);
        tags.put(ICC_Profile.icSigRedTRCTag, trc);
        tags.put(ICC_Profile.icSigGreenTRCTag, trc);
        tags.put(ICC_Profile.icSigBlueTRCTag, trc);
        return new IccProfile(header(ICC_Profile.icSigDisplayClass, SIG_RGB), tags).serialize();
    }

    private static byte[] buildGray() {
        LinkedHashMap<Integer, byte[]> tags = new LinkedHashMap<Integer, byte[]>();
        tags.put(ICC_Profile.icSigProfileDescriptionTag, descTag("Linear Gray (gamma 1.0)"));
        tags.put(ICC_Profile.icSigCopyrightTag, textTag("No copyright, use freely"));
        tags.put(ICC_Profile.icSigMediaWhitePointTag, xyzTag(D50));
        tags.put(ICC_Profile.icSigGrayTRCTag, gammaTag(1.0));
        return new IccProfile(header(ICC_Profile.icSigDisplayClass, SIG_GRAY), tags).serialize();
    }

    private static byte[] buildXyz() {
        LinkedHashMap<Integer, byte[]> tags = new LinkedHashMap<Integer, byte[]>();
        tags.put(ICC_Profile.icSigProfileDescriptionTag, descTag("CIEXYZ (D50 profile connection space)"));
        tags.put(ICC_Profile.icSigCopyrightTag, textTag("No copyright, use freely"));
        tags.put(ICC_Profile.icSigMediaWhitePointTag, xyzTag(D50));
        return new IccProfile(header(ICC_Profile.icSigAbstractClass, SIG_XYZ), tags).serialize();
    }

    private static byte[] buildPycc() {
        LinkedHashMap<Integer, byte[]> tags = new LinkedHashMap<Integer, byte[]>();
        tags.put(ICC_Profile.icSigProfileDescriptionTag, descTag("Kodak PhotoYCC (matrix approximation)"));
        tags.put(ICC_Profile.icSigCopyrightTag, textTag("No copyright, use freely"));
        tags.put(ICC_Profile.icSigMediaWhitePointTag, xyzTag(D50));
        return new IccProfile(header(ICC_Profile.icSigColorSpaceClass, SIG_3CLR), tags).serialize();
    }

    private static byte[] descTag(String text) {
        byte[] ascii = ascii(text);
        byte[] tag = new byte[12 + ascii.length + 1 + 8 + 3 + 67];
        putInt(tag, 0, SIG_DESC);
        putInt(tag, 8, ascii.length + 1);
        System.arraycopy(ascii, 0, tag, 12, ascii.length);
        return tag;
    }

    private static byte[] textTag(String text) {
        byte[] ascii = ascii(text);
        byte[] tag = new byte[8 + ascii.length + 1];
        putInt(tag, 0, SIG_TEXT);
        System.arraycopy(ascii, 0, tag, 8, ascii.length);
        return tag;
    }

    private static byte[] xyzTag(int[] fixed) {
        byte[] tag = new byte[20];
        putInt(tag, 0, SIG_XYZ_TYPE);
        for (int i = 0; i < 3; i++) {
            putInt(tag, 8 + 4 * i, fixed[i]);
        }
        return tag;
    }

    private static byte[] gammaTag(double gamma) {
        byte[] tag = new byte[14];
        putInt(tag, 0, SIG_CURV);
        putInt(tag, ICC_Profile.icCurveCount, 1);
        putShort(tag, ICC_Profile.icCurveData, (short) (int) (gamma * 256 + 0.5));
        return tag;
    }

    private static byte[] curveTag(short[] table) {
        byte[] tag = new byte[12 + 2 * table.length];
        putInt(tag, 0, SIG_CURV);
        putInt(tag, ICC_Profile.icCurveCount, table.length);
        for (int i = 0; i < table.length; i++) {
            putShort(tag, 12 + 2 * i, table[i]);
        }
        return tag;
    }

    private static byte[] ascii(String text) {
        byte[] out = new byte[text.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) text.charAt(i);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    static int getInt(byte[] array, int index) {
        return (array[index] & 0xff) << 24 | (array[index + 1] & 0xff) << 16
            | (array[index + 2] & 0xff) << 8 | (array[index + 3] & 0xff);
    }

    static short getShort(byte[] array, int index) {
        return (short) ((array[index] & 0xff) << 8 | (array[index + 1] & 0xff));
    }

    static void putInt(byte[] array, int index, int value) {
        array[index] = (byte) (value >> 24);
        array[index + 1] = (byte) (value >> 16);
        array[index + 2] = (byte) (value >> 8);
        array[index + 3] = (byte) value;
    }

    static void putShort(byte[] array, int index, short value) {
        array[index] = (byte) (value >> 8);
        array[index + 1] = (byte) value;
    }

    static String signature(int sig) {
        return new String(new char[] {(char) (sig >>> 24 & 0xff), (char) (sig >>> 16 & 0xff),
            (char) (sig >>> 8 & 0xff), (char) (sig & 0xff)});
    }
}
