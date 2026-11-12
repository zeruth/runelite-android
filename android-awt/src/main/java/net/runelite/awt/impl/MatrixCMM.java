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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The colour management backend of {@code java.awt.color}: the five standard profiles as ICC v2
 * byte images, the ICC container (header, tag table) and the profile-to-PCS transforms.
 *
 * <p>Desktop hands profiles to LittleCMS; Android has no CMM, so a transform is the matrix
 * approximation every matrix/TRC profile describes exactly: per-channel tone curves ({@code curv}
 * gamma or table, {@code para} types 0-4) and the colorant matrix to D50 XYZ, or {@code kTRC}
 * times the D50 white for gray.  XYZ and Lab data spaces convert analytically.  Profiles that are
 * only described by LUTs (A2B0/B2A0, CMYK printers, device links, named colours) have no matrix
 * form and throw {@link CMMException}.  Every conversion uses relative colorimetric intent, which
 * is what desktop's {@code ICC_ColorSpace} asks LittleCMS for.
 */
public final class MatrixCMM {
    private MatrixCMM() {
    }

    public static final int HEADER_SIZE = 128;
    public static final double[] D50 = {0.9642, 1.0, 0.8249};

    private static final int SIG_RGB = sig("RGB "), SIG_GRAY = sig("GRAY"), SIG_XYZ = sig("XYZ "),
        SIG_LAB = sig("Lab "), SIG_3CLR = sig("3CLR");
    private static final int TAG_R = sig("rXYZ"), TAG_G = sig("gXYZ"), TAG_B = sig("bXYZ"),
        TAG_RTRC = sig("rTRC"), TAG_GTRC = sig("gTRC"), TAG_BTRC = sig("bTRC"), TAG_KTRC = sig("kTRC"),
        TAG_WTPT = sig("wtpt"), TAG_DESC = sig("desc"), TAG_CPRT = sig("cprt");
    /**
     * Private tag of the standard PYCC profile: desktop's PYCC.pf is LUT-based, here the Photo YCC
     * encoding is applied on top of the sRGB matrix/TRC it is defined over.
     */
    private static final int TAG_PYCC = sig("rlYC");

    // sRGB colorants adapted to D50 (Bradford), as in the JDK's sRGB.pf and LINEAR_RGB.pf.
    private static final double[][] SRGB_COLORANTS = {
        {0.4360747, 0.2225045, 0.0139322},
        {0.3850649, 0.7168786, 0.0971045},
        {0.1430804, 0.0606169, 0.7141733},
    };

    public static int sig(String s) {
        return s.charAt(0) << 24 | s.charAt(1) << 16 | s.charAt(2) << 8 | s.charAt(3);
    }

    // ---- standard profiles ---------------------------------------------------------------

    private static final byte[][] STANDARD = new byte[5][];

    /** The ICC image of a standard profile ({@code ColorSpace.CS_*}); callers must not modify it. */
    public static synchronized byte[] standardProfile(int colorSpace) {
        int index = colorSpace - ColorSpace.CS_sRGB;
        if (index < 0 || index >= STANDARD.length) {
            throw new IllegalArgumentException("Unknown color space");
        }
        if (STANDARD[index] == null) {
            STANDARD[index] = buildStandard(colorSpace);
        }
        return STANDARD[index];
    }

    private static byte[] buildStandard(int colorSpace) {
        Builder b = new Builder();
        switch (colorSpace) {
            case ColorSpace.CS_sRGB:
            case ColorSpace.CS_LINEAR_RGB:
            case ColorSpace.CS_PYCC: {
                boolean linear = colorSpace == ColorSpace.CS_LINEAR_RGB;
                boolean pycc = colorSpace == ColorSpace.CS_PYCC;
                b.header(pycc ? "spac" : "mntr", pycc ? "3CLR" : "RGB ", "XYZ ");
                b.tag(TAG_DESC, desc(linear ? "Linear RGB" : pycc ? "Kodak Photo YCC" : "sRGB IEC61966-2.1"));
                b.tag(TAG_CPRT, text("No copyright, use freely"));
                b.tag(TAG_WTPT, xyz(D50));
                b.tag(TAG_R, xyz(SRGB_COLORANTS[0]));
                b.tag(TAG_G, xyz(SRGB_COLORANTS[1]));
                b.tag(TAG_B, xyz(SRGB_COLORANTS[2]));
                byte[] curve = linear ? gammaCurve(1.0) : srgbCurve();
                b.tag(TAG_RTRC, curve);
                b.tag(TAG_GTRC, curve);
                b.tag(TAG_BTRC, curve);
                if (pycc) {
                    b.tag(TAG_PYCC, new byte[] {'r', 'l', 'Y', 'C', 0, 0, 0, 0});
                }
                break;
            }
            case ColorSpace.CS_GRAY:
                b.header("mntr", "GRAY", "XYZ ");
                b.tag(TAG_DESC, desc("Linear Gray"));
                b.tag(TAG_CPRT, text("No copyright, use freely"));
                b.tag(TAG_WTPT, xyz(D50));
                b.tag(TAG_KTRC, gammaCurve(1.0));
                break;
            case ColorSpace.CS_CIEXYZ:
                b.header("abst", "XYZ ", "XYZ ");
                b.tag(TAG_DESC, desc("CIE XYZ"));
                b.tag(TAG_CPRT, text("No copyright, use freely"));
                b.tag(TAG_WTPT, xyz(D50));
                break;
            default:
                throw new IllegalArgumentException("Unknown color space");
        }
        return b.build();
    }

    private static final class Builder {
        private final byte[] header = new byte[HEADER_SIZE];
        private final java.util.List<int[]> sigs = new java.util.ArrayList<int[]>();
        private final java.util.List<byte[]> data = new java.util.ArrayList<byte[]>();

        void header(String profileClass, String space, String pcs) {
            putInt(header, 4, sig("none"));
            putInt(header, 8, 0x02100000);
            putInt(header, 12, sig(profileClass));
            putInt(header, 16, sig(space));
            putInt(header, 20, sig(pcs));
            putShort(header, 24, 1998);
            putShort(header, 26, 1);
            putShort(header, 28, 1);
            putInt(header, 36, sig("acsp"));
            putInt(header, 64, 0);
            putInt(header, 68, s15(D50[0]));
            putInt(header, 72, s15(D50[1]));
            putInt(header, 76, s15(D50[2]));
        }

        void tag(int signature, byte[] bytes) {
            sigs.add(new int[] {signature});
            data.add(bytes);
        }

        byte[] build() {
            return assemble(header, sigs, data);
        }
    }

    private static byte[] assemble(byte[] header, java.util.List<int[]> sigs, java.util.List<byte[]> data) {
        int offset = HEADER_SIZE + 4 + 12 * sigs.size();
        int[] offsets = new int[sigs.size()];
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = offset;
            offset = (offset + data.get(i).length + 3) & ~3;
        }
        byte[] out = new byte[offset];
        System.arraycopy(header, 0, out, 0, HEADER_SIZE);
        putInt(out, 0, out.length);
        putInt(out, HEADER_SIZE, sigs.size());
        for (int i = 0; i < offsets.length; i++) {
            int entry = HEADER_SIZE + 4 + 12 * i;
            putInt(out, entry, sigs.get(i)[0]);
            putInt(out, entry + 4, offsets[i]);
            putInt(out, entry + 8, data.get(i).length);
            System.arraycopy(data.get(i), 0, out, offsets[i], data.get(i).length);
        }
        return out;
    }

    private static byte[] desc(String text) {
        byte[] ascii = text.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[12 + ascii.length + 1 + 4 + 4 + 2 + 1 + 67];
        putInt(out, 0, sig("desc"));
        putInt(out, 8, ascii.length + 1);
        System.arraycopy(ascii, 0, out, 12, ascii.length);
        return out;
    }

    private static byte[] text(String text) {
        byte[] ascii = text.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[8 + ascii.length + 1];
        putInt(out, 0, sig("text"));
        System.arraycopy(ascii, 0, out, 8, ascii.length);
        return out;
    }

    private static byte[] xyz(double[] v) {
        byte[] out = new byte[20];
        putInt(out, 0, sig("XYZ "));
        for (int i = 0; i < 3; i++) {
            putInt(out, 8 + 4 * i, s15(v[i]));
        }
        return out;
    }

    private static byte[] gammaCurve(double gamma) {
        byte[] out = new byte[14];
        putInt(out, 0, sig("curv"));
        putInt(out, 8, 1);
        putShort(out, 12, (int) Math.round(gamma * 256));
        return out;
    }

    private static byte[] srgbCurve() {
        int count = 1024;
        byte[] out = new byte[12 + 2 * count];
        putInt(out, 0, sig("curv"));
        putInt(out, 8, count);
        for (int i = 0; i < count; i++) {
            double v = i / (double) (count - 1);
            double linear = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
            putShort(out, 12 + 2 * i, (int) Math.round(linear * 65535));
        }
        return out;
    }

    // ---- ICC container ---------------------------------------------------------------------

    /** Validates the container: size, the {@code acsp} magic and a tag table inside the data. */
    public static void validate(byte[] data) {
        if (data == null || data.length < HEADER_SIZE + 4 || getInt(data, 36) != sig("acsp")) {
            throw new IllegalArgumentException("Invalid ICC Profile Data");
        }
        int count = getInt(data, HEADER_SIZE);
        if (count < 0 || HEADER_SIZE + 4 + 12L * count > data.length) {
            throw new IllegalArgumentException("Invalid ICC Profile Data");
        }
        for (int i = 0; i < count; i++) {
            int entry = HEADER_SIZE + 4 + 12 * i;
            long end = (getInt(data, entry + 4) & 0xFFFFFFFFL) + (getInt(data, entry + 8) & 0xFFFFFFFFL);
            if (end > data.length) {
                throw new IllegalArgumentException("Invalid ICC Profile Data");
            }
        }
    }

    /** A copy of the tag's bytes (type signature included), or null when the profile lacks it. */
    public static byte[] tag(byte[] data, int signature) {
        int count = getInt(data, HEADER_SIZE);
        for (int i = 0; i < count; i++) {
            int entry = HEADER_SIZE + 4 + 12 * i;
            if (getInt(data, entry) == signature) {
                int offset = getInt(data, entry + 4), size = getInt(data, entry + 8);
                byte[] out = new byte[size];
                System.arraycopy(data, offset, out, 0, size);
                return out;
            }
        }
        return null;
    }

    /** The profile with the tag replaced or appended; the other tags keep their order. */
    public static byte[] withTag(byte[] data, int signature, byte[] tag) {
        int count = getInt(data, HEADER_SIZE);
        java.util.List<int[]> sigs = new java.util.ArrayList<int[]>();
        java.util.List<byte[]> tags = new java.util.ArrayList<byte[]>();
        boolean replaced = false;
        for (int i = 0; i < count; i++) {
            int s = getInt(data, HEADER_SIZE + 4 + 12 * i);
            sigs.add(new int[] {s});
            if (s == signature) {
                tags.add(tag.clone());
                replaced = true;
            } else {
                tags.add(tag(data, s));
            }
        }
        if (!replaced) {
            sigs.add(new int[] {signature});
            tags.add(tag.clone());
        }
        return assemble(data, sigs, tags);
    }

    /** The profile with a new header; the size field is recomputed. */
    public static byte[] withHeader(byte[] data, byte[] header) {
        if (header.length != HEADER_SIZE) {
            throw new IllegalArgumentException("Wrong header size");
        }
        byte[] out = data.clone();
        System.arraycopy(header, 0, out, 0, HEADER_SIZE);
        putInt(out, 0, out.length);
        return out;
    }

    /** {@code ColorSpace.TYPE_*} of a color space signature (header bytes 16 or 20). */
    public static int colorSpaceType(int signature) {
        String[] names = {"XYZ ", "Lab ", "Luv ", "YCbr", "Yxy ", "RGB ", "GRAY", "HSV ", "HLS ", "CMYK", null,
            "CMY ", "2CLR", "3CLR", "4CLR", "5CLR", "6CLR", "7CLR", "8CLR", "9CLR", "ACLR", "BCLR", "CCLR", "DCLR",
            "ECLR", "FCLR"};
        for (int type = 0; type < names.length; type++) {
            if (names[type] != null && sig(names[type]) == signature) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown color space");
    }

    /** Component count of a {@code ColorSpace.TYPE_*}. */
    public static int componentCount(int type) {
        switch (type) {
            case ColorSpace.TYPE_GRAY:
                return 1;
            case ColorSpace.TYPE_2CLR:
                return 2;
            case ColorSpace.TYPE_CMYK:
            case ColorSpace.TYPE_4CLR:
                return 4;
            default:
                if (type >= ColorSpace.TYPE_5CLR && type <= ColorSpace.TYPE_FCLR) {
                    return type - ColorSpace.TYPE_5CLR + 5;
                }
                return 3;
        }
    }

    // ---- transforms ------------------------------------------------------------------------

    private static volatile Transform srgb;

    /** The sRGB transform every {@code toRGB}/{@code fromRGB} goes through. */
    public static Transform srgb() {
        Transform t = srgb;
        if (t == null) {
            t = srgb = new Transform(standardProfile(ColorSpace.CS_sRGB));
        }
        return t;
    }

    public static Transform transform(byte[] data) {
        return new Transform(data);
    }

    private static final java.util.Map<ICC_Profile, Transform> CACHE = new java.util.WeakHashMap<ICC_Profile, Transform>();

    /** The cached transform of a profile; {@link #invalidate} drops it when the profile changes. */
    public static Transform transform(ICC_Profile profile) {
        synchronized (CACHE) {
            Transform t = CACHE.get(profile);
            if (t == null) {
                t = new Transform(profile.getData());
                CACHE.put(profile, t);
            }
            return t;
        }
    }

    public static void invalidate(ICC_Profile profile) {
        synchronized (CACHE) {
            CACHE.remove(profile);
        }
    }

    /** A profile's device-to-PCS mapping; components are in the color space's own range. */
    public static final class Transform {
        private static final int RGB = 0, GRAY = 1, XYZ = 2, LAB = 3, PYCC = 4;
        private final int kind;
        private final Curve[] curves;
        private final double[] matrix;  // device linear -> XYZ, row major
        private final double[] inverse;

        Transform(byte[] data) {
            int space = getInt(data, 16);
            int profileClass = getInt(data, 12);
            if (profileClass == sig("link") || profileClass == sig("nmcl")) {
                throw new CMMException("No matrix form for device link and named color profiles (no CMM on Android)");
            }
            if (space == SIG_XYZ) {
                kind = XYZ;
                curves = null;
                matrix = inverse = null;
            } else if (space == SIG_LAB) {
                kind = LAB;
                curves = null;
                matrix = inverse = null;
            } else if (space == SIG_GRAY && tag(data, TAG_KTRC) != null) {
                kind = GRAY;
                curves = new Curve[] {new Curve(tag(data, TAG_KTRC))};
                matrix = inverse = null;
            } else if ((space == SIG_RGB || space == SIG_3CLR && tag(data, TAG_PYCC) != null)
                       && tag(data, TAG_R) != null && tag(data, TAG_G) != null && tag(data, TAG_B) != null
                       && tag(data, TAG_RTRC) != null && tag(data, TAG_GTRC) != null && tag(data, TAG_BTRC) != null) {
                kind = space == SIG_RGB ? RGB : PYCC;
                curves = new Curve[] {new Curve(tag(data, TAG_RTRC)), new Curve(tag(data, TAG_GTRC)),
                    new Curve(tag(data, TAG_BTRC))};
                matrix = new double[9];
                int[] columns = {TAG_R, TAG_G, TAG_B};
                for (int c = 0; c < 3; c++) {
                    double[] v = readXYZ(tag(data, columns[c]));
                    for (int r = 0; r < 3; r++) {
                        matrix[r * 3 + c] = v[r];
                    }
                }
                inverse = invert(matrix);
            } else {
                throw new CMMException("Profile has no matrix/TRC form; arbitrary ICC transforms need a CMM, "
                                       + "which Android lacks");
            }
        }

        /** Components (in the space's range) to D50 XYZ. */
        public float[] toXYZ(float[] c) {
            switch (kind) {
                case XYZ:
                    return new float[] {c[0], c[1], c[2]};
                case LAB:
                    return labToXYZ(c);
                case GRAY: {
                    double y = curves[0].eval(c[0]);
                    return new float[] {(float) (y * D50[0]), (float) y, (float) (y * D50[2])};
                }
                default: {
                    double[] encoded = kind == PYCC ? fromYCC(c) : new double[] {c[0], c[1], c[2]};
                    double r = curves[0].eval(encoded[0]), g = curves[1].eval(encoded[1]), b = curves[2].eval(encoded[2]);
                    return new float[] {
                        (float) (matrix[0] * r + matrix[1] * g + matrix[2] * b),
                        (float) (matrix[3] * r + matrix[4] * g + matrix[5] * b),
                        (float) (matrix[6] * r + matrix[7] * g + matrix[8] * b)};
                }
            }
        }

        /** D50 XYZ to components, clipped to the space's range as LittleCMS clips. */
        public float[] fromXYZ(float[] xyz) {
            switch (kind) {
                case XYZ: {
                    float max = 1f + 32767f / 32768f;
                    return new float[] {clip(xyz[0], 0, max), clip(xyz[1], 0, max), clip(xyz[2], 0, max)};
                }
                case LAB:
                    return xyzToLab(xyz);
                case GRAY:
                    return new float[] {(float) curves[0].invert(clip(xyz[1], 0, 1))};
                default: {
                    double[] encoded = new double[3];
                    for (int i = 0; i < 3; i++) {
                        double linear = inverse[i * 3] * xyz[0] + inverse[i * 3 + 1] * xyz[1] + inverse[i * 3 + 2] * xyz[2];
                        encoded[i] = curves[i].invert(clip(linear, 0, 1));
                    }
                    if (kind == PYCC) {
                        encoded = toYCC(encoded);
                    }
                    return new float[] {clip(encoded[0], 0, 1), clip(encoded[1], 0, 1), clip(encoded[2], 0, 1)};
                }
            }
        }
    }

    // Kodak Photo YCC over sRGB-encoded R'G'B': the 8-bit encoding of the PhotoCD spec, normalized.
    private static double[] toYCC(double[] rgb) {
        double y = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2];
        return new double[] {y / 1.402, (111.40 * (rgb[2] - y) + 156) / 255, (135.64 * (rgb[0] - y) + 137) / 255};
    }

    private static double[] fromYCC(float[] c) {
        double y = 1.402 * c[0];
        double b = y + (255 * c[1] - 156) / 111.40;
        double r = y + (255 * c[2] - 137) / 135.64;
        double g = (y - 0.299 * r - 0.114 * b) / 0.587;
        return new double[] {clip(r, 0, 1), clip(g, 0, 1), clip(b, 0, 1)};
    }

    private static float[] labToXYZ(float[] lab) {
        double fy = (lab[0] + 16) / 116.0, fx = fy + lab[1] / 500.0, fz = fy - lab[2] / 200.0;
        return new float[] {(float) (D50[0] * labInverse(fx)), (float) labInverse(fy), (float) (D50[2] * labInverse(fz))};
    }

    private static double labInverse(double t) {
        return t > 6.0 / 29 ? t * t * t : 3 * (6.0 / 29) * (6.0 / 29) * (t - 4.0 / 29);
    }

    private static double labForward(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : t / (3 * (6.0 / 29) * (6.0 / 29)) + 4.0 / 29;
    }

    private static float[] xyzToLab(float[] xyz) {
        double fx = labForward(xyz[0] / D50[0]), fy = labForward(xyz[1]), fz = labForward(xyz[2] / D50[2]);
        return new float[] {clip(116 * fy - 16, 0, 100), clip(500 * (fx - fy), -128, 127), clip(200 * (fy - fz), -128, 127)};
    }

    /** A tone curve: {@code curv} (identity, gamma or table) or {@code para} types 0-4. */
    static final class Curve {
        private final double gamma;     // curv with one entry, para
        private final double[] table;   // curv with a table
        private final double[] p;       // para parameters g a b c d e f

        Curve(byte[] tag) {
            int type = getInt(tag, 0);
            if (type == sig("curv")) {
                int count = getInt(tag, 8);
                p = null;
                if (count == 0) {
                    gamma = 1;
                    table = null;
                } else if (count == 1) {
                    gamma = (((tag[12] & 255) << 8) | (tag[13] & 255)) / 256.0;
                    table = null;
                } else {
                    gamma = 0;
                    table = new double[count];
                    for (int i = 0; i < count; i++) {
                        table[i] = (((tag[12 + 2 * i] & 255) << 8) | (tag[13 + 2 * i] & 255)) / 65535.0;
                    }
                }
            } else if (type == sig("para")) {
                int kind = (getInt(tag, 8) >>> 16);
                int[] counts = {1, 3, 4, 5, 7};
                if (kind > 4) {
                    throw new CMMException("Unknown parametric curve type " + kind);
                }
                double[] v = new double[7];
                for (int i = 0; i < counts[kind]; i++) {
                    v[i] = getInt(tag, 12 + 4 * i) / 65536.0;
                }
                // Normalize every type to type 4: Y = X >= d ? (aX + b)^g + e : cX + f.
                double g = v[0], a = 1, b = 0, c = 0, d = 0, e = 0, f = 0;
                if (kind >= 1) {
                    a = v[1];
                    b = v[2];
                    d = a == 0 ? 0 : -b / a;
                }
                if (kind == 2) {
                    e = f = v[3];
                }
                if (kind >= 3) {
                    c = v[3];
                    d = v[4];
                }
                if (kind == 4) {
                    e = v[5];
                    f = v[6];
                }
                p = new double[] {g, a, b, c, d, e, f};
                gamma = g;
                table = null;
            } else {
                throw new CMMException("Unknown curve type");
            }
        }

        double eval(double x) {
            x = clip(x, 0, 1);
            if (p != null) {
                return x >= p[4] ? Math.pow(Math.max(0, p[1] * x + p[2]), p[0]) + p[5] : p[3] * x + p[6];
            }
            if (table == null) {
                return gamma == 1 ? x : Math.pow(x, gamma);
            }
            double position = x * (table.length - 1);
            int i = Math.min((int) position, table.length - 2);
            return table[i] + (table[i + 1] - table[i]) * (position - i);
        }

        double invert(double y) {
            if (p != null) {
                double threshold = p[3] * p[4] + p[6];
                double x;
                if (y >= threshold || p[3] == 0) {
                    double base = Math.max(0, y - p[5]);
                    x = p[1] == 0 ? 0 : (Math.pow(base, 1 / p[0]) - p[2]) / p[1];
                } else {
                    x = (y - p[6]) / p[3];
                }
                return clip(x, 0, 1);
            }
            if (table == null) {
                return gamma == 1 ? y : Math.pow(y, 1 / gamma);
            }
            // Tables are monotonic (increasing or decreasing); invert the piecewise-linear forward.
            boolean rising = table[table.length - 1] >= table[0];
            int lo = 0, hi = table.length - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (table[mid] <= y == rising) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            double span = table[hi] - table[lo];
            double t = span == 0 ? 0 : clip((y - table[lo]) / span, 0, 1);
            return (lo + t) / (table.length - 1);
        }
    }

    // ---- primitives --------------------------------------------------------------------------

    public static double[] readXYZ(byte[] tag) {
        if (tag == null || tag.length < 20 || getInt(tag, 0) != sig("XYZ ")) {
            throw new IllegalArgumentException("Not an XYZ tag");
        }
        return new double[] {getInt(tag, 8) / 65536.0, getInt(tag, 12) / 65536.0, getInt(tag, 16) / 65536.0};
    }

    private static double[] invert(double[] m) {
        double a = m[0], b = m[1], c = m[2], d = m[3], e = m[4], f = m[5], g = m[6], h = m[7], i = m[8];
        double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        if (det == 0) {
            throw new CMMException("Singular colorant matrix");
        }
        return new double[] {
            (e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det,
            (f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det};
    }

    private static float clip(double v, double lo, double hi) {
        return (float) (v < lo ? lo : v > hi ? hi : v);
    }

    public static int getInt(byte[] b, int offset) {
        return (b[offset] & 255) << 24 | (b[offset + 1] & 255) << 16 | (b[offset + 2] & 255) << 8 | (b[offset + 3] & 255);
    }

    public static void putInt(byte[] b, int offset, int v) {
        b[offset] = (byte) (v >>> 24);
        b[offset + 1] = (byte) (v >>> 16);
        b[offset + 2] = (byte) (v >>> 8);
        b[offset + 3] = (byte) v;
    }

    private static void putShort(byte[] b, int offset, int v) {
        b[offset] = (byte) (v >>> 8);
        b[offset + 1] = (byte) v;
    }

    private static int s15(double v) {
        return (int) Math.round(v * 65536);
    }

    /** Reads a stream to its end (profiles arrive whole from files and streams). */
    public static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
