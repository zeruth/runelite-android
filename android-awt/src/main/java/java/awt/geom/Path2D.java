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
package java.awt.geom;

import java.awt.Rectangle;
import java.awt.Shape;

/** Segment-backed path shared by GeneralPath and both Path2D precision variants. */
public abstract class Path2D implements Shape, Cloneable, java.io.Serializable
{
    public static final int WIND_EVEN_ODD = PathIterator.WIND_EVEN_ODD;
    public static final int WIND_NON_ZERO = PathIterator.WIND_NON_ZERO;

    private int windingRule;

    byte[] segTypes = new byte[8];
    double[] segCoords = new double[16];
    int numSegs = 0;
    int numCoords = 0;

    private double minX = java.lang.Double.POSITIVE_INFINITY, minY = java.lang.Double.POSITIVE_INFINITY;
    private double maxX = java.lang.Double.NEGATIVE_INFINITY, maxY = java.lang.Double.NEGATIVE_INFINITY;
    private boolean empty = true;

    protected Path2D(int rule) { setWindingRule(rule); }

    private void ensureSegCapacity(int n) {
        if (numSegs + n > segTypes.length) {
            byte[] nt = new byte[Math.max(segTypes.length * 2, numSegs + n)];
            System.arraycopy(segTypes, 0, nt, 0, numSegs);
            segTypes = nt;
        }
    }
    private void ensureCoordCapacity(int n) {
        if (numCoords + n > segCoords.length) {
            double[] nc = new double[Math.max(segCoords.length * 2, numCoords + n)];
            System.arraycopy(segCoords, 0, nc, 0, numCoords);
            segCoords = nc;
        }
    }
    private void grow(double x, double y) {
        if (empty) { minX = maxX = x; minY = maxY = y; empty = false; return; }
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
    }

    public final void moveTo(double x, double y) {
        if(this instanceof Float){x=(float)x;y=(float)y;}
        if(numSegs>0 && segTypes[numSegs-1]==PathIterator.SEG_MOVETO){
            segCoords[numCoords-2]=x;segCoords[numCoords-1]=y;recomputeBounds();return;
        }
        ensureSegCapacity(1); ensureCoordCapacity(2);
        segTypes[numSegs++] = (byte) PathIterator.SEG_MOVETO;
        segCoords[numCoords++] = x; segCoords[numCoords++] = y;
        grow(x, y);
    }
    public final void lineTo(double x, double y) {
        if(this instanceof Float){x=(float)x;y=(float)y;}
        requireMove();
        ensureSegCapacity(1); ensureCoordCapacity(2);
        segTypes[numSegs++] = (byte) PathIterator.SEG_LINETO;
        segCoords[numCoords++] = x; segCoords[numCoords++] = y;
        grow(x, y);
    }
    private void requireMove(){if(numSegs==0)throw new IllegalPathStateException("missing initial moveto");}
    public final void quadTo(double x1, double y1, double x2, double y2) {
        if(this instanceof Float){x1=(float)x1;y1=(float)y1;x2=(float)x2;y2=(float)y2;}
        requireMove();
        ensureSegCapacity(1); ensureCoordCapacity(4);
        segTypes[numSegs++] = (byte) PathIterator.SEG_QUADTO;
        segCoords[numCoords++] = x1; segCoords[numCoords++] = y1;
        segCoords[numCoords++] = x2; segCoords[numCoords++] = y2;
        grow(x1, y1); grow(x2, y2);
    }
    public final void curveTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        if(this instanceof Float){x1=(float)x1;y1=(float)y1;x2=(float)x2;y2=(float)y2;x3=(float)x3;y3=(float)y3;}
        requireMove();
        ensureSegCapacity(1); ensureCoordCapacity(6);
        segTypes[numSegs++] = (byte) PathIterator.SEG_CUBICTO;
        segCoords[numCoords++] = x1; segCoords[numCoords++] = y1;
        segCoords[numCoords++] = x2; segCoords[numCoords++] = y2;
        segCoords[numCoords++] = x3; segCoords[numCoords++] = y3;
        grow(x1, y1); grow(x2, y2); grow(x3, y3);
    }
    public final void closePath() {
        requireMove();
        if(segTypes[numSegs-1]==PathIterator.SEG_CLOSE)return;
        ensureSegCapacity(1);
        segTypes[numSegs++] = (byte) PathIterator.SEG_CLOSE;
    }

    public final int getWindingRule() { return windingRule; }
    public final void setWindingRule(int rule) { if (rule != WIND_EVEN_ODD && rule != WIND_NON_ZERO) throw new IllegalArgumentException("winding rule"); this.windingRule = rule; }

    public final void reset() {
        numSegs = 0; numCoords = 0; empty = true;
        minX = minY = java.lang.Double.POSITIVE_INFINITY;
        maxX = maxY = java.lang.Double.NEGATIVE_INFINITY;
    }

    @Override
    public Rectangle getBounds() {
        if (empty) return new Rectangle();
        return new Rectangle((int) Math.floor(minX), (int) Math.floor(minY),
            (int)Math.ceil(maxX)-(int)Math.floor(minX), (int)Math.ceil(maxY)-(int)Math.floor(minY));
    }

    @Override
    public Rectangle2D getBounds2D() {
        if (empty) return new Rectangle2D.Double();
        return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
    }

    @Override public boolean contains(double x,double y) {return PathGeometry.contains(getPathIterator(null),x,y);}
    @Override public boolean contains(double x,double y,double w,double h) {return PathGeometry.rectangle(this,x,y,w,h,true);}
    @Override public boolean intersects(double x,double y,double w,double h) {return PathGeometry.rectangle(this,x,y,w,h,false);}
    @Override public PathIterator getPathIterator(AffineTransform at,double flatness) {return new FlatteningPathIterator(getPathIterator(at),flatness);}
    public final Point2D getCurrentPoint() {
        if(numSegs==0)return null;
        double x=0,y=0,sx=0,sy=0;int c=0;
        for(int i=0;i<numSegs;i++)switch(segTypes[i]){
            case PathIterator.SEG_MOVETO:x=sx=segCoords[c++];y=sy=segCoords[c++];break;
            case PathIterator.SEG_LINETO:x=segCoords[c++];y=segCoords[c++];break;
            case PathIterator.SEG_QUADTO:c+=2;x=segCoords[c++];y=segCoords[c++];break;
            case PathIterator.SEG_CUBICTO:c+=4;x=segCoords[c++];y=segCoords[c++];break;
            case PathIterator.SEG_CLOSE:x=sx;y=sy;break;
        }
        return new Point2D.Double(x,y);
    }
    public final void append(Shape s,boolean connect){append(s.getPathIterator(null),connect);}
    public final void append(PathIterator it,boolean connect){
        double[]c=new double[6];
        while(!it.isDone()){
            switch(it.currentSegment(c)){
                case PathIterator.SEG_MOVETO:
                    Point2D end=getCurrentPoint();
                    if(connect&&end!=null){if(end.getX()!=c[0]||end.getY()!=c[1])lineTo(c[0],c[1]);}
                    else moveTo(c[0],c[1]);break;
                case PathIterator.SEG_LINETO:lineTo(c[0],c[1]);break;
                case PathIterator.SEG_QUADTO:quadTo(c[0],c[1],c[2],c[3]);break;
                case PathIterator.SEG_CUBICTO:curveTo(c[0],c[1],c[2],c[3],c[4],c[5]);break;
                case PathIterator.SEG_CLOSE:closePath();break;
            }
            connect=false;it.next();
        }
    }
    public final void transform(AffineTransform at){
        at.transform(segCoords,0,segCoords,0,numCoords/2);
        if(this instanceof Float)for(int i=0;i<numCoords;i++)segCoords[i]=(float)segCoords[i];
        recomputeBounds();
    }
    private void recomputeBounds(){empty=true;for(int i=0;i<numCoords;i+=2)grow(segCoords[i],segCoords[i+1]);}
    public final Shape createTransformedShape(AffineTransform at){Path2D p=(Path2D)clone();if(at!=null)p.transform(at);return p;}
    public static boolean contains(PathIterator it,double x,double y){return PathGeometry.contains(it,x,y);}
    public static boolean contains(PathIterator it,Point2D p){return contains(it,p.getX(),p.getY());}
    public static boolean contains(PathIterator it,double x,double y,double w,double h){Path2D p=new Double(it.getWindingRule());p.append(it,false);return p.contains(x,y,w,h);}
    public static boolean intersects(PathIterator it,double x,double y,double w,double h){Path2D p=new Double(it.getWindingRule());p.append(it,false);return p.intersects(x,y,w,h);}

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return new Iter(this, at);
    }

    @Override
    public Object clone() {
        try {
            Path2D p = (Path2D) super.clone();
            p.segTypes = segTypes.clone();
            p.segCoords = segCoords.clone();
            return p;
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public static class Double extends Path2D {
        public Double() { super(WIND_NON_ZERO); }
        public Double(int rule){super(rule);}
        public Double(int rule,int capacity){this(rule);if(capacity<0)throw new NegativeArraySizeException();}
        public Double(Shape s){this(s,null);}
        public Double(Shape s,AffineTransform at){super(s.getPathIterator(null).getWindingRule());append(s.getPathIterator(at),false);}
    }

    public static class Float extends Path2D {
        public void moveTo(float x,float y) { super.moveTo(x,y); }
        public void lineTo(float x,float y) { super.lineTo(x,y); }
        public Float() { super(WIND_NON_ZERO); }
        public Float(int rule){super(rule);}
        public Float(int rule,int capacity){this(rule);if(capacity<0)throw new NegativeArraySizeException();}
        public Float(Shape s){this(s,null);}
        public Float(Shape s,AffineTransform at){super(s.getPathIterator(null).getWindingRule());append(s.getPathIterator(at),false);}
        public void quadTo(float a,float b,float c,float d){super.quadTo(a,b,c,d);}
        public void curveTo(float a,float b,float c,float d,float e,float f){super.curveTo(a,b,c,d,e,f);}
    }

    private static final class Iter implements PathIterator {
        private final Path2D p;
        private final AffineTransform at;
        private int seg = 0, coord = 0;

        Iter(Path2D p, AffineTransform at) { this.p = p; this.at = at; }

        private static int sizeOf(int t) {
            switch (t) {
                case SEG_MOVETO: case SEG_LINETO: return 2;
                case SEG_QUADTO: return 4;
                case SEG_CUBICTO: return 6;
                default: return 0;
            }
        }

        @Override public int getWindingRule() { return p.getWindingRule(); }
        @Override public boolean isDone() { return seg >= p.numSegs; }

        @Override public void next() {
            if (isDone()) return;
            coord += sizeOf(p.segTypes[seg]);
            seg++;
        }

        @Override public int currentSegment(float[] coords) {
            if(isDone())throw new java.util.NoSuchElementException();
            int t = p.segTypes[seg];
            int n = sizeOf(t);
            for (int i = 0; i < n; i++) coords[i] = (float) p.segCoords[coord + i];
            if (at != null && n > 0) at.transform(coords, 0, coords, 0, n / 2);
            return t;
        }

        @Override public int currentSegment(double[] coords) {
            if(isDone())throw new java.util.NoSuchElementException();
            int t = p.segTypes[seg];
            int n = sizeOf(t);
            System.arraycopy(p.segCoords, coord, coords, 0, n);
            if (at != null && n > 0) at.transform(coords, 0, coords, 0, n / 2);
            return t;
        }
    }
}
