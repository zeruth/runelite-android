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
package java.awt.image;
import java.awt.RenderingHints;
import java.awt.geom.*;
/** Inverse-mapped image transform, also usable without an Android drawing surface. */
public class AffineTransformOp implements BufferedImageOp, RasterOp {
    public static final int TYPE_NEAREST_NEIGHBOR=1,TYPE_BILINEAR=2,TYPE_BICUBIC=3;
    private final AffineTransform transform;
    private final int interpolation;
    private final RenderingHints hints;
    public AffineTransformOp(AffineTransform transform,int interpolation){
        if(interpolation<1||interpolation>3)throw new IllegalArgumentException("interpolation");
        if(Math.abs(transform.getDeterminant())<=Double.MIN_VALUE)throw new IllegalArgumentException("noninvertible transform");
        this.transform=new AffineTransform(transform);this.interpolation=interpolation;
        hints=new RenderingHints(RenderingHints.KEY_INTERPOLATION,interpolation==1?RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR:interpolation==2?RenderingHints.VALUE_INTERPOLATION_BILINEAR:RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
    public AffineTransformOp(AffineTransform transform,RenderingHints hints){
        this(transform,hints!=null&&hints.get(RenderingHints.KEY_INTERPOLATION)==RenderingHints.VALUE_INTERPOLATION_BICUBIC?3:hints!=null&&hints.get(RenderingHints.KEY_INTERPOLATION)==RenderingHints.VALUE_INTERPOLATION_BILINEAR?2:1);
        if(hints!=null)this.hints.putAll(hints);
    }
    public int getInterpolationType(){return interpolation;}
    public AffineTransform getTransform(){return new AffineTransform(transform);}
    public BufferedImage filter(BufferedImage src,BufferedImage dst){
        if(src==null)throw new NullPointerException("src");if(src==dst)throw new IllegalArgumentException("source and destination must differ");
        if(dst==null)dst=createCompatibleDestImage(src,null);
        AffineTransform inverse;try{inverse=transform.createInverse();}catch(NoninvertibleTransformException e){throw new IllegalArgumentException(e);}
        Point2D.Double point=new Point2D.Double();
        for(int y=0;y<dst.getHeight();y++)for(int x=0;x<dst.getWidth();x++){
            point.setLocation(x+0.5,y+0.5);inverse.transform(point,point);
            if(point.x<0||point.y<0||point.x>=src.getWidth()||point.y>=src.getHeight())continue;
            dst.setRGB(x,y,sample(src,point.x-0.5,point.y-0.5,interpolation));
        }
        return dst;
    }
    public static int sample(BufferedImage src,double x,double y,int interpolation){
        if(interpolation==1)return src.getRGB(clamp((int)Math.floor(x+0.5),src.getWidth()),clamp((int)Math.floor(y+0.5),src.getHeight()));
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y),radius=interpolation==3?2:1;double[] channels=new double[4];double total=0;
        for(int j=1-radius;j<=radius;j++)for(int i=1-radius;i<=radius;i++){
            double weight=interpolation==3?cubic(x-ix-i)*cubic(y-iy-j):(i==0?1-(x-ix):x-ix)*(j==0?1-(y-iy):y-iy);
            int pixel=src.getRGB(clamp(ix+i,src.getWidth()),clamp(iy+j,src.getHeight()));double alpha=(pixel>>>24)/255.0;
            channels[0]+=weight*(pixel>>>24);channels[1]+=weight*((pixel>>>16)&255)*alpha;channels[2]+=weight*((pixel>>>8)&255)*alpha;channels[3]+=weight*(pixel&255)*alpha;total+=weight;
        }
        int alpha=byteValue(channels[0]/total);if(alpha==0)return 0;
        return alpha<<24|byteValue(channels[1]*255/channels[0])<<16|byteValue(channels[2]*255/channels[0])<<8|byteValue(channels[3]*255/channels[0]);
    }
    private static double cubic(double x){x=Math.abs(x);return x<=1?(1.5*x-2.5)*x*x+1:x<2?((-0.5*x+2.5)*x-4)*x+2:0;}
    private static int byteValue(double v){return Math.max(0,Math.min(255,(int)Math.round(v)));}
    private static int clamp(int v,int max){return Math.max(0,Math.min(max-1,v));}
    /** Band-wise transform of a Raster: nearest or bilinear per sample (bicubic falls back to bilinear), like desktop without alpha handling. */
    public WritableRaster filter(Raster src,WritableRaster dst){
        if(src==null)throw new NullPointerException("src");
        if(dst==null)dst=createCompatibleDestRaster(src);
        else if(src==dst)throw new IllegalArgumentException("src image cannot be the same as the dst image");
        else if(src.getNumBands()!=dst.getNumBands())throw new IllegalArgumentException("Number of src bands ("+src.getNumBands()+") does not match number of dst bands ("+dst.getNumBands()+")");
        AffineTransform inverse;try{inverse=transform.createInverse();}catch(NoninvertibleTransformException e){throw new ImagingOpException("Unable to invert transform "+transform);}
        int bands=src.getNumBands(),sw=src.getWidth(),sh=src.getHeight(),sx0=src.getMinX(),sy0=src.getMinY(),dx0=dst.getMinX(),dy0=dst.getMinY();
        int[] max=new int[bands];for(int b=0;b<bands;b++){int bits=dst.getSampleModel().getSampleSize(b);max[b]=bits>=31?Integer.MAX_VALUE:(1<<bits)-1;}
        Point2D.Double point=new Point2D.Double();
        for(int y=0;y<dst.getHeight();y++)for(int x=0;x<dst.getWidth();x++){
            point.setLocation(x+0.5,y+0.5);inverse.transform(point,point);
            if(point.x<0||point.y<0||point.x>=sw||point.y>=sh)continue;
            for(int b=0;b<bands;b++){
                double v;
                if(interpolation==TYPE_NEAREST_NEIGHBOR)v=src.getSample(sx0+(int)point.x,sy0+(int)point.y,b);
                else{
                    double fx=point.x-0.5,fy=point.y-0.5;int ix=(int)Math.floor(fx),iy=(int)Math.floor(fy);double tx=fx-ix,ty=fy-iy;
                    double p00=src.getSample(sx0+clamp(ix,sw),sy0+clamp(iy,sh),b),p10=src.getSample(sx0+clamp(ix+1,sw),sy0+clamp(iy,sh),b);
                    double p01=src.getSample(sx0+clamp(ix,sw),sy0+clamp(iy+1,sh),b),p11=src.getSample(sx0+clamp(ix+1,sw),sy0+clamp(iy+1,sh),b);
                    v=(p00*(1-tx)+p10*tx)*(1-ty)+(p01*(1-tx)+p11*tx)*ty;
                }
                int s=(int)Math.round(v);dst.setSample(dx0+x,dy0+y,b,s<0?0:s>max[b]?max[b]:s);
            }
        }
        return dst;
    }
    public Rectangle2D getBounds2D(Raster src){
        double[] points={src.getMinX(),src.getMinY(),src.getMinX()+src.getWidth(),src.getMinY(),src.getMinX(),src.getMinY()+src.getHeight(),src.getMinX()+src.getWidth(),src.getMinY()+src.getHeight()};transform.transform(points,0,points,0,4);
        double minX=points[0],minY=points[1],maxX=minX,maxY=minY;
        for(int i=2;i<8;i+=2){minX=Math.min(minX,points[i]);maxX=Math.max(maxX,points[i]);minY=Math.min(minY,points[i+1]);maxY=Math.max(maxY,points[i+1]);}
        return new Rectangle2D.Double(minX,minY,maxX-minX,maxY-minY);
    }
    public WritableRaster createCompatibleDestRaster(Raster src){
        Rectangle2D bounds=getBounds2D(src);int w=(int)Math.ceil(bounds.getMaxX()),h=(int)Math.ceil(bounds.getMaxY());
        if(w<=0||h<=0)throw new IllegalArgumentException("transformed raster has no positive bounds");
        return src.createCompatibleWritableRaster(w,h);
    }
    public Rectangle2D getBounds2D(BufferedImage src){
        double[] points={0,0,src.getWidth(),0,0,src.getHeight(),src.getWidth(),src.getHeight()};transform.transform(points,0,points,0,4);
        double minX=points[0],minY=points[1],maxX=minX,maxY=minY;
        for(int i=2;i<8;i+=2){minX=Math.min(minX,points[i]);maxX=Math.max(maxX,points[i]);minY=Math.min(minY,points[i+1]);maxY=Math.max(maxY,points[i+1]);}
        return new Rectangle2D.Double(minX,minY,maxX-minX,maxY-minY);
    }
    public BufferedImage createCompatibleDestImage(BufferedImage src,ColorModel cm){
        Rectangle2D bounds=getBounds2D(src);int w=(int)Math.ceil(bounds.getMaxX()),h=(int)Math.ceil(bounds.getMaxY());
        if(w<=0||h<=0)throw new IllegalArgumentException("transformed image has no positive bounds");
        return cm==null?new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB):new BufferedImage(cm,cm.createCompatibleWritableRaster(w,h),cm.isAlphaPremultiplied(),null);
    }
    public Point2D getPoint2D(Point2D src,Point2D dst){return transform.transform(src,dst);}
    public RenderingHints getRenderingHints(){return (RenderingHints)hints.clone();}
}
