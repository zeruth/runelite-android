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
package java.awt;

import java.awt.image.ColorModel;

public final class AlphaComposite implements Composite {
    public static final int CLEAR = 1;
    public static final int SRC = 2;
    public static final int DST = 9;
    public static final int SRC_OVER = 3;
    public static final int DST_OVER = 4;
    public static final int SRC_IN = 5;
    public static final int DST_IN = 6;
    public static final int SRC_OUT = 7;
    public static final int DST_OUT = 8;
    public static final int SRC_ATOP = 10;
    public static final int DST_ATOP = 11;
    public static final int XOR = 12;

    public static final AlphaComposite Clear   = new AlphaComposite(CLEAR, 1f);
    public static final AlphaComposite Src     = new AlphaComposite(SRC, 1f);
    public static final AlphaComposite Dst     = new AlphaComposite(DST, 1f);
    public static final AlphaComposite SrcOver = new AlphaComposite(SRC_OVER, 1f);
    public static final AlphaComposite DstOver = new AlphaComposite(DST_OVER, 1f);
    public static final AlphaComposite SrcIn   = new AlphaComposite(SRC_IN, 1f);
    public static final AlphaComposite DstIn   = new AlphaComposite(DST_IN, 1f);
    public static final AlphaComposite SrcOut  = new AlphaComposite(SRC_OUT, 1f);
    public static final AlphaComposite DstOut  = new AlphaComposite(DST_OUT, 1f);
    public static final AlphaComposite SrcAtop = new AlphaComposite(SRC_ATOP, 1f);
    public static final AlphaComposite DstAtop = new AlphaComposite(DST_ATOP, 1f);
    public static final AlphaComposite Xor     = new AlphaComposite(XOR, 1f);

    private final int rule;
    private final float alpha;

    private AlphaComposite(int rule, float alpha) {
        this.rule = rule;
        this.alpha = alpha;
    }

    public static AlphaComposite getInstance(int rule) {
        switch (rule) {
            case CLEAR: return Clear;
            case SRC:   return Src;
            case DST:   return Dst;
            case SRC_OVER: return SrcOver;
            case DST_OVER: return DstOver;
            case SRC_IN: return SrcIn;
            case DST_IN: return DstIn;
            case SRC_OUT: return SrcOut;
            case DST_OUT: return DstOut;
            case SRC_ATOP: return SrcAtop;
            case DST_ATOP: return DstAtop;
            case XOR: return Xor;
            default: throw new IllegalArgumentException("Unknown rule: " + rule);
        }
    }

    public static AlphaComposite getInstance(int rule, float alpha) {
        getInstance(rule);
        if (!(alpha >= 0f && alpha <= 1f)) throw new IllegalArgumentException("alpha out of range: " + alpha);
        return new AlphaComposite(rule, alpha);
    }

    public int getRule()      { return rule; }
    public float getAlpha()   { return alpha; }
    public AlphaComposite derive(float newAlpha) { return getInstance(rule, newAlpha); }
    public AlphaComposite derive(int newRule) { return getInstance(newRule, alpha); }

    @Override
    public CompositeContext createContext(ColorModel srcCM, ColorModel dstCM, RenderingHints hints) {
        return new CompositeContext(){
            public void dispose(){}
            public void compose(java.awt.image.Raster src,java.awt.image.Raster dst,java.awt.image.WritableRaster out){
                int w=Math.min(src.getWidth(),Math.min(dst.getWidth(),out.getWidth())),h=Math.min(src.getHeight(),Math.min(dst.getHeight(),out.getHeight()));
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                    int sp=srcCM.getRGB(src.getDataElements(src.getMinX()+x,src.getMinY()+y,null)),dp=dstCM.getRGB(dst.getDataElements(dst.getMinX()+x,dst.getMinY()+y,null));
                    double sa=(sp>>>24)/255.0*alpha,da=(dp>>>24)/255.0,fs=1,fd=1-sa;
                    switch(rule){
                        case CLEAR:fs=fd=0;break;case SRC:fd=0;break;case DST:fs=0;fd=1;break;case DST_OVER:fs=1-da;fd=1;break;
                        case SRC_IN:fs=da;fd=0;break;case DST_IN:fs=0;fd=sa;break;case SRC_OUT:fs=1-da;fd=0;break;case DST_OUT:fs=0;fd=1-sa;break;
                        case SRC_ATOP:fs=da;fd=1-sa;break;case DST_ATOP:fs=1-da;fd=sa;break;case XOR:fs=1-da;fd=1-sa;break;
                    }
                    double oa=sa*fs+da*fd;int pixel=Math.max(0,Math.min(255,(int)Math.round(oa*255)))<<24;
                    if(oa>0)for(int shift=0;shift<=16;shift+=8)pixel|=Math.max(0,Math.min(255,(int)Math.round((((sp>>>shift)&255)*sa*fs+((dp>>>shift)&255)*da*fd)/oa)))<<shift;
                    out.setDataElements(out.getMinX()+x,out.getMinY()+y,dstCM.getDataElements(pixel,null));
                }
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AlphaComposite) {
            AlphaComposite a = (AlphaComposite) obj;
            return rule == a.rule && alpha == a.alpha;
        }
        return false;
    }

    @Override public int hashCode() { return rule * 31 + Float.floatToIntBits(alpha); }
}
