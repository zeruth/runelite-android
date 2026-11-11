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
package java.awt.color;

import java.io.Serializable;

public abstract class ColorSpace implements Serializable {
    private static final long serialVersionUID = -409452704308689724L;

    public static final int TYPE_XYZ = 0;
    public static final int TYPE_Lab = 1;
    public static final int TYPE_Luv = 2;
    public static final int TYPE_YCbCr = 3;
    public static final int TYPE_Yxy = 4;
    public static final int TYPE_RGB = 5;
    public static final int TYPE_GRAY = 6;
    public static final int TYPE_HSV = 7;
    public static final int TYPE_HLS = 8;
    public static final int TYPE_CMYK = 9;
    public static final int TYPE_CMY = 11;
    public static final int TYPE_2CLR = 12;
    public static final int TYPE_3CLR = 13;
    public static final int TYPE_4CLR = 14;
    public static final int TYPE_5CLR = 15;
    public static final int TYPE_6CLR = 16;
    public static final int TYPE_7CLR = 17;
    public static final int TYPE_8CLR = 18;
    public static final int TYPE_9CLR = 19;
    public static final int TYPE_ACLR = 20;
    public static final int TYPE_BCLR = 21;
    public static final int TYPE_CCLR = 22;
    public static final int TYPE_DCLR = 23;
    public static final int TYPE_ECLR = 24;
    public static final int TYPE_FCLR = 25;

    public static final int CS_sRGB = 1000;
    public static final int CS_LINEAR_RGB = 1004;
    public static final int CS_CIEXYZ = 1001;
    public static final int CS_PYCC = 1002;
    public static final int CS_GRAY = 1003;

    private final int type;
    private final int numComponents;

    protected ColorSpace(int type, int numComponents) {
        this.type = type;
        this.numComponents = numComponents;
    }

    public static ColorSpace getInstance(int colorspace) {
        switch (colorspace) {
            case CS_sRGB:
                return SRGB;
            case CS_LINEAR_RGB:
                return LINEAR;
            case CS_GRAY:
                return GRAY;
            case CS_CIEXYZ:
                return CIEXYZ;
            case CS_PYCC:
                throw new IllegalArgumentException("Photo YCC profiles are not available");
            default:
                throw new IllegalArgumentException("Unknown color space: " + colorspace);
        }
    }

    public int getType() {
        return type;
    }

    public int getNumComponents() {
        return numComponents;
    }

    public boolean isCS_sRGB() {
        return this == SRGB;
    }

    public String getName(int idx) {
        if (idx < 0 || idx >= numComponents) {
            throw new IllegalArgumentException("Component index out of range: " + idx);
        }
        return "Unnamed";
    }

    public float getMinValue(int component) {
        if (component < 0 || component >= numComponents) {
            throw new IllegalArgumentException("Component index out of range: " + component);
        }
        return 0f;
    }

    public float getMaxValue(int component) {
        if (component < 0 || component >= numComponents) {
            throw new IllegalArgumentException("Component index out of range: " + component);
        }
        return 1f;
    }

    public abstract float[] toRGB(float[] colorvalue);
    public abstract float[] fromRGB(float[] rgbvalue);
    public abstract float[] toCIEXYZ(float[] colorvalue);
    public abstract float[] fromCIEXYZ(float[] colorvalue);

    private static final ColorSpace SRGB=new SimpleColorSpace(TYPE_RGB,3,false);
    private static final ColorSpace LINEAR=new SimpleColorSpace(TYPE_RGB,3,true);
    private static final ColorSpace GRAY=new SimpleColorSpace(TYPE_GRAY,1,true);
    private static final ColorSpace CIEXYZ=new SimpleColorSpace(TYPE_XYZ,3,true);
    private static double linear(double v){return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);}
    private static float encoded(double v){return (float)Math.max(0,Math.min(1,v<=0.0031308?12.92*v:1.055*Math.pow(v,1/2.4)-0.055));}
    private static final class SimpleColorSpace extends ColorSpace{
        private final boolean linear;
        SimpleColorSpace(int type,int count,boolean linear){super(type,count);this.linear=linear;}
        public float[] toRGB(float[] v){
            if(getType()==TYPE_GRAY)return new float[]{encoded(v[0]),encoded(v[0]),encoded(v[0])};
            if(getType()==TYPE_XYZ)return new float[]{encoded(3.1338561*v[0]-1.6168667*v[1]-0.4906146*v[2]),encoded(-0.9787684*v[0]+1.9161415*v[1]+0.0334540*v[2]),encoded(0.0719453*v[0]-0.2289914*v[1]+1.4052427*v[2])};
            return linear?new float[]{encoded(v[0]),encoded(v[1]),encoded(v[2])}:v.clone();
        }
        public float[] fromRGB(float[] v){
            if(getType()==TYPE_XYZ)return SRGB.toCIEXYZ(v);
            double r=linear(v[0]),g=linear(v[1]),b=linear(v[2]);
            if(getType()==TYPE_GRAY)return new float[]{(float)(0.2225045*r+0.7168786*g+0.0606169*b)};
            return linear?new float[]{(float)r,(float)g,(float)b}:v.clone();
        }
        public float[] toCIEXYZ(float[] v){
            if(getType()==TYPE_XYZ)return v.clone();
            if(getType()==TYPE_GRAY)return new float[]{0.9642f*v[0],v[0],0.8249f*v[0]};
            double r=linear?v[0]:linear(v[0]),g=linear?v[1]:linear(v[1]),b=linear?v[2]:linear(v[2]);
            return new float[]{(float)(0.4360747*r+0.3850649*g+0.1430804*b),(float)(0.2225045*r+0.7168786*g+0.0606169*b),(float)(0.0139322*r+0.0971045*g+0.7141733*b)};
        }
        public float[] fromCIEXYZ(float[] v){if(getType()==TYPE_XYZ)return v.clone();if(getType()==TYPE_GRAY)return new float[]{v[1]};return fromRGB(CIEXYZ.toRGB(v));}
    }
}
