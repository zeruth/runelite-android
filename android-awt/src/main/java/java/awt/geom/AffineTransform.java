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

import java.io.Serializable;

/**
 * Minimal AffineTransform. Stores the six matrix coefficients; provides the operations
 * RuneLite actually uses (mostly identity + translate/scale on Graphics2D).
 */
public class AffineTransform implements Cloneable, Serializable {
    private static final long serialVersionUID = 1330973210523860834L;

    public static final int TYPE_IDENTITY = 0;
    public static final int TYPE_TRANSLATION = 1;
    public static final int TYPE_UNIFORM_SCALE = 2;
    public static final int TYPE_GENERAL_SCALE = 4;
    public static final int TYPE_FLIP = 64;

    private double m00 = 1, m10, m01, m11 = 1, m02, m12;

    public AffineTransform() {
    }

    public AffineTransform(AffineTransform other) {
        this.m00 = other.m00; this.m10 = other.m10;
        this.m01 = other.m01; this.m11 = other.m11;
        this.m02 = other.m02; this.m12 = other.m12;
    }

    public AffineTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        this.m00 = m00; this.m10 = m10;
        this.m01 = m01; this.m11 = m11;
        this.m02 = m02; this.m12 = m12;
    }

    public AffineTransform(float m00, float m10, float m01, float m11, float m02, float m12) {
        this((double)m00, m10, m01, m11, m02, m12);
    }
    public AffineTransform(double[] flatmatrix) {
        this(flatmatrix[0], flatmatrix[1], flatmatrix[2], flatmatrix[3], flatmatrix.length > 5 ? flatmatrix[4] : 0, flatmatrix.length > 5 ? flatmatrix[5] : 0);
    }

    public AffineTransform(float[] flatmatrix) {
        m00 = flatmatrix[0]; m10 = flatmatrix[1];
        m01 = flatmatrix[2]; m11 = flatmatrix[3];
        if (flatmatrix.length > 5) { m02 = flatmatrix[4]; m12 = flatmatrix[5]; }
    }

    public static AffineTransform getTranslateInstance(double tx, double ty) {
        AffineTransform at = new AffineTransform();
        at.m02 = tx; at.m12 = ty;
        return at;
    }

    public static AffineTransform getScaleInstance(double sx, double sy) {
        AffineTransform at = new AffineTransform();
        at.m00 = sx; at.m11 = sy;
        return at;
    }

    public void setToIdentity() {
        m00 = 1; m11 = 1; m10 = 0; m01 = 0; m02 = 0; m12 = 0;
    }

    public void setToTranslation(double tx, double ty) {
        m00 = 1; m11 = 1; m10 = 0; m01 = 0; m02 = tx; m12 = ty;
    }

    public void setToScale(double sx, double sy) {
        m00 = sx; m11 = sy; m10 = 0; m01 = 0; m02 = 0; m12 = 0;
    }

    public void translate(double tx, double ty) {
        m02 += tx * m00 + ty * m01;
        m12 += tx * m10 + ty * m11;
    }

    public void scale(double sx, double sy) {
        m00 *= sx; m10 *= sx;
        m01 *= sy; m11 *= sy;
    }

    public void rotate(double theta) {
        double sin = Math.sin(theta), cos = Math.cos(theta);
        double n00 = m00 * cos + m01 * sin;
        double n01 = m01 * cos - m00 * sin;
        double n10 = m10 * cos + m11 * sin;
        double n11 = m11 * cos - m10 * sin;
        m00 = n00; m01 = n01; m10 = n10; m11 = n11;
    }

    public void rotate(double theta, double anchorX, double anchorY) {
        translate(anchorX, anchorY);
        rotate(theta);
        translate(-anchorX, -anchorY);
    }

    public void rotate(double vecX, double vecY, double anchorX, double anchorY) {
        rotate(Math.atan2(vecY, vecX), anchorX, anchorY);
    }

    public static AffineTransform getRotateInstance(double theta) {
        AffineTransform at = new AffineTransform();
        at.rotate(theta);
        return at;
    }

    public static AffineTransform getRotateInstance(double theta, double anchorX, double anchorY) {
        AffineTransform at = new AffineTransform();
        at.rotate(theta, anchorX, anchorY);
        return at;
    }

    public double getTranslateX() { return m02; }
    public double getTranslateY() { return m12; }
    public double getScaleX()     { return m00; }
    public double getScaleY()     { return m11; }
    public double getShearX()     { return m01; }
    public double getShearY()     { return m10; }

    public int getType() {
        boolean trans = m02 != 0 || m12 != 0;
        boolean scaled = m00 != 1 || m11 != 1 || m01 != 0 || m10 != 0;
        if (!trans && !scaled) return TYPE_IDENTITY;
        if (trans && !scaled) return TYPE_TRANSLATION;
        return TYPE_GENERAL_SCALE | (trans ? TYPE_TRANSLATION : 0);
    }

    public boolean isIdentity() {
        return getType() == TYPE_IDENTITY;
    }

    public void getMatrix(double[] flatmatrix) {
        flatmatrix[0] = m00; flatmatrix[1] = m10;
        flatmatrix[2] = m01; flatmatrix[3] = m11;
        if (flatmatrix.length > 5) { flatmatrix[4] = m02; flatmatrix[5] = m12; }
    }

    public void setTransform(AffineTransform other) {
        this.m00 = other.m00; this.m10 = other.m10;
        this.m01 = other.m01; this.m11 = other.m11;
        this.m02 = other.m02; this.m12 = other.m12;
    }

    /** Standard JDK overload — set all six matrix elements in place. Used by callers
     *  (e.g. BufferedImageGraphics2D's snapshot/restore path) that want to avoid the
     *  allocation a `new AffineTransform(...)` would incur on the hot paint path. */
    public void setTransform(double m00, double m10, double m01, double m11, double m02, double m12) {
        this.m00 = m00; this.m10 = m10;
        this.m01 = m01; this.m11 = m11;
        this.m02 = m02; this.m12 = m12;
    }

    /** Standard AWT API: transform {@code numPts} 2D points from {@code srcPts[srcOff..]}
     *  into {@code dstPts[dstOff..]}. Aliasing src and dst is allowed. */
    public void transform(float[] srcPts, int srcOff, float[] dstPts, int dstOff, int numPts) {
        if (srcPts == dstPts && dstOff > srcOff && dstOff < srcOff + 2 * numPts) {
            System.arraycopy(srcPts, srcOff, dstPts, dstOff, 2 * numPts);
            srcOff = dstOff;
        }
        for (int i = 0; i < numPts; i++) {
            double x = srcPts[srcOff++];
            double y = srcPts[srcOff++];
            dstPts[dstOff++] = (float) (m00 * x + m01 * y + m02);
            dstPts[dstOff++] = (float) (m10 * x + m11 * y + m12);
        }
    }

    public void transform(double[] srcPts, int srcOff, double[] dstPts, int dstOff, int numPts) {
        if (srcPts == dstPts && dstOff > srcOff && dstOff < srcOff + 2 * numPts) {
            System.arraycopy(srcPts, srcOff, dstPts, dstOff, 2 * numPts);
            srcOff = dstOff;
        }
        for (int i = 0; i < numPts; i++) {
            double x = srcPts[srcOff++];
            double y = srcPts[srcOff++];
            dstPts[dstOff++] = m00 * x + m01 * y + m02;
            dstPts[dstOff++] = m10 * x + m11 * y + m12;
        }
    }

    public AffineTransform createInverse() throws NoninvertibleTransformException {
        double det=getDeterminant();
        if(Math.abs(det)<=Double.MIN_VALUE)throw new NoninvertibleTransformException("determinant is zero");
        return new AffineTransform(m11/det,-m10/det,-m01/det,m00/det,(m01*m12-m11*m02)/det,(m10*m02-m00*m12)/det);
    }
    public void invert() throws NoninvertibleTransformException {setTransform(createInverse());}
    public Point2D inverseTransform(Point2D src,Point2D dst) throws NoninvertibleTransformException {return createInverse().transform(src,dst);}
    public void inverseTransform(double[]src,int so,double[]dst,int d,int count) throws NoninvertibleTransformException {createInverse().transform(src,so,dst,d,count);}
    public double getDeterminant() {
        return m00 * m11 - m01 * m10;
    }

    public void shear(double shx, double shy) {
        double n00 = m00 + m01 * shy, n01 = m01 + m00 * shx;
        double n10 = m10 + m11 * shy, n11 = m11 + m10 * shx;
        m00 = n00; m01 = n01; m10 = n10; m11 = n11;
    }

    /** this = this x tx (tx applied first). */
    public void concatenate(AffineTransform tx) {
        double n00 = m00 * tx.m00 + m01 * tx.m10;
        double n01 = m00 * tx.m01 + m01 * tx.m11;
        double n02 = m00 * tx.m02 + m01 * tx.m12 + m02;
        double n10 = m10 * tx.m00 + m11 * tx.m10;
        double n11 = m10 * tx.m01 + m11 * tx.m11;
        double n12 = m10 * tx.m02 + m11 * tx.m12 + m12;
        m00 = n00; m01 = n01; m02 = n02; m10 = n10; m11 = n11; m12 = n12;
    }

    /** this = tx x this (tx applied last). */
    public void preConcatenate(AffineTransform tx) {
        AffineTransform r = new AffineTransform(tx);
        r.concatenate(this);
        setTransform(r);
    }

    public Point2D transform(Point2D src, Point2D dst) {
        double x = src.getX(), y = src.getY();
        if (dst == null) dst = new Point2D.Double();
        dst.setLocation(m00 * x + m01 * y + m02, m10 * x + m11 * y + m12);
        return dst;
    }

    public Point2D deltaTransform(Point2D src, Point2D dst) {
        double x = src.getX(), y = src.getY();
        if (dst == null) dst = new Point2D.Double();
        dst.setLocation(m00 * x + m01 * y, m10 * x + m11 * y);
        return dst;
    }

    public java.awt.Shape createTransformedShape(java.awt.Shape s) {
        if (s == null) return null;
        Path2D.Double p = new Path2D.Double();
        PathIterator it = s.getPathIterator(this);
        if (it != null) {
            p.setWindingRule(it.getWindingRule());
            double[] c = new double[6];
            while (!it.isDone()) {
                switch (it.currentSegment(c)) {
                    case PathIterator.SEG_MOVETO: p.moveTo(c[0], c[1]); break;
                    case PathIterator.SEG_LINETO: p.lineTo(c[0], c[1]); break;
                    case PathIterator.SEG_QUADTO: p.quadTo(c[0], c[1], c[2], c[3]); break;
                    case PathIterator.SEG_CUBICTO: p.curveTo(c[0], c[1], c[2], c[3], c[4], c[5]); break;
                    case PathIterator.SEG_CLOSE: p.closePath(); break;
                    default: break;
                }
                it.next();
            }
        }
        return p;
    }

    @Override
    public Object clone() {
        return new AffineTransform(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AffineTransform) {
            AffineTransform a = (AffineTransform) obj;
            return m00 == a.m00 && m01 == a.m01 && m02 == a.m02
                && m10 == a.m10 && m11 == a.m11 && m12 == a.m12;
        }
        return false;
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(m00);
        bits = bits * 31 + Double.doubleToLongBits(m01);
        bits = bits * 31 + Double.doubleToLongBits(m02);
        bits = bits * 31 + Double.doubleToLongBits(m10);
        bits = bits * 31 + Double.doubleToLongBits(m11);
        bits = bits * 31 + Double.doubleToLongBits(m12);
        return (int) bits ^ (int) (bits >> 32);
    }
}
