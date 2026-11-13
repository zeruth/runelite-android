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

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;

public class GradientPaint implements Paint {
    private final float x1, y1, x2, y2;
    private final Color c1, c2;
    private final boolean cyclic;

    public GradientPaint(float x1, float y1, Color c1, float x2, float y2, Color c2) {
        this(x1, y1, c1, x2, y2, c2, false);
    }

    public GradientPaint(float x1, float y1, Color c1, float x2, float y2, Color c2, boolean cyclic) {
        this.x1 = x1; this.y1 = y1; this.c1 = java.util.Objects.requireNonNull(c1); this.x2 = x2; this.y2 = y2; this.c2 = java.util.Objects.requireNonNull(c2); this.cyclic = cyclic;
    }

    public GradientPaint(java.awt.geom.Point2D pt1, Color c1, java.awt.geom.Point2D pt2, Color c2) {
        this((float) pt1.getX(), (float) pt1.getY(), c1, (float) pt2.getX(), (float) pt2.getY(), c2, false);
    }

    public GradientPaint(java.awt.geom.Point2D pt1, Color c1, java.awt.geom.Point2D pt2, Color c2, boolean cyclic) {
        this((float) pt1.getX(), (float) pt1.getY(), c1, (float) pt2.getX(), (float) pt2.getY(), c2, cyclic);
    }

    public Color getColor1() { return c1; }
    public Color getColor2() { return c2; }
    public java.awt.geom.Point2D getPoint1() { return new java.awt.geom.Point2D.Float(x1, y1); }
    public java.awt.geom.Point2D getPoint2() { return new java.awt.geom.Point2D.Float(x2, y2); }
    public boolean isCyclic() { return cyclic; }

    @Override
    public int getTransparency() {
        return (c1.getAlpha() == 0xFF && c2.getAlpha() == 0xFF) ? OPAQUE : TRANSLUCENT;
    }

    @Override
    public PaintContext createContext(ColorModel cm, Rectangle bounds, Rectangle2D userBounds,
                                       AffineTransform xform, RenderingHints hints) {
        final AffineTransform inverse;
        try{inverse=xform.createInverse();}catch(java.awt.geom.NoninvertibleTransformException e){throw new IllegalArgumentException(e);}
        return new PaintContext(){
            private java.awt.image.BufferedImage image;
            public void dispose(){net.runelite.awt.impl.GradientRasterCache.release(image);image=null;}
            public ColorModel getColorModel(){return ColorModel.getRGBdefault();}
            public java.awt.image.Raster getRaster(int x,int y,int w,int h){
                if(image==null||image.getWidth()<w||image.getHeight()<h){
                    net.runelite.awt.impl.GradientRasterCache.release(image);
                    image=net.runelite.awt.impl.GradientRasterCache.acquire(w,h);
                }
                int[] pixels=image.backingArray();int stride=image.getWidth();
                double dx=x2-x1,dy=y2-y1,length=dx*dx+dy*dy;
                double m00=inverse.getScaleX(),m01=inverse.getShearX(),m02=inverse.getTranslateX();
                double m10=inverse.getShearY(),m11=inverse.getScaleY(),m12=inverse.getTranslateY();
                int a=c1.getRGB(),b=c2.getRGB();
                for(int row=0;row<h;row++)for(int col=0;col<w;col++){
                    double px=x+col+0.5,py=y+row+0.5;
                    double ux=m00*px+m01*py+m02,uy=m10*px+m11*py+m12;
                    double t=length==0?0:((ux-x1)*dx+(uy-y1)*dy)/length;
                    if(cyclic){t=t-Math.floor(t/2)*2;if(t>1)t=2-t;}else t=Math.max(0,Math.min(1,t));
                    int pixel=0;for(int shift=0;shift<=24;shift+=8)pixel|=((int)Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;
                    pixels[row*stride+col]=pixel;
                }
                return image.getRaster();
            }
        };
    }
}
