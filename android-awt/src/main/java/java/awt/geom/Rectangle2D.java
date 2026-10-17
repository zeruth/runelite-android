package java.awt.geom;

import java.awt.Rectangle;
import java.io.Serializable;

public abstract class Rectangle2D extends RectangularShape implements Serializable {
    private static final long serialVersionUID = 1547053856191376818L;

    public static final int OUT_LEFT = 1;
    public static final int OUT_TOP = 2;
    public static final int OUT_RIGHT = 4;
    public static final int OUT_BOTTOM = 8;

    protected Rectangle2D() {
    }

    public abstract double getX();
    public abstract double getY();
    public abstract double getWidth();
    public abstract double getHeight();
    public abstract boolean isEmpty();
    public abstract void setRect(double x, double y, double w, double h);

    @Override
    public void setFrame(double x, double y, double w, double h) {
        setRect(x, y, w, h);
    }

    public double getMinX() { return getX(); }
    public double getMinY() { return getY(); }
    public double getMaxX() { return getX() + getWidth(); }
    public double getMaxY() { return getY() + getHeight(); }
    public double getCenterX() { return getX() + getWidth() / 2.0; }
    public double getCenterY() { return getY() + getHeight() / 2.0; }

    public Rectangle2D getFrame() {
        return getBounds2D();
    }

    public void setRect(Rectangle2D r) {
        setRect(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    @Override
    public boolean contains(double x, double y) {
        double rx = getX();
        double ry = getY();
        return x >= rx && y >= ry && x < rx + getWidth() && y < ry + getHeight();
    }

    @Override
    public boolean contains(double x, double y, double w, double h) {
        if (isEmpty() || w <= 0 || h <= 0) return false;
        double rx = getX(), ry = getY(), rw = getWidth(), rh = getHeight();
        return x >= rx && y >= ry && (x + w) <= rx + rw && (y + h) <= ry + rh;
    }

    @Override
    public boolean intersects(double x, double y, double w, double h) {
        if (isEmpty() || w <= 0 || h <= 0) return false;
        double rx = getX(), ry = getY(), rw = getWidth(), rh = getHeight();
        return x + w > rx && y + h > ry && x < rx + rw && y < ry + rh;
    }

    public int outcode(double x, double y) {
        int out = 0;
        double rx = getX(), ry = getY(), rw = getWidth(), rh = getHeight();
        if (rw <= 0) out |= OUT_LEFT | OUT_RIGHT;
        else if (x < rx) out |= OUT_LEFT;
        else if (x > rx + rw) out |= OUT_RIGHT;
        if (rh <= 0) out |= OUT_TOP | OUT_BOTTOM;
        else if (y < ry) out |= OUT_TOP;
        else if (y > ry + rh) out |= OUT_BOTTOM;
        return out;
    }

    public int outcode(Point2D p) {
        return outcode(p.getX(), p.getY());
    }

    @Override
    public Rectangle getBounds() {
        double x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iw = (int) Math.ceil(x + w) - ix;
        int ih = (int) Math.ceil(y + h) - iy;
        return new Rectangle(ix, iy, iw, ih);
    }

    @Override
    public Rectangle2D getBounds2D() {
        return (Rectangle2D) clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Rectangle2D) {
            Rectangle2D r = (Rectangle2D) obj;
            return getX() == r.getX() && getY() == r.getY()
                && getWidth() == r.getWidth() && getHeight() == r.getHeight();
        }
        return false;
    }

    @Override
    public int hashCode() {
        long bits = java.lang.Double.doubleToLongBits(getX());
        bits += java.lang.Double.doubleToLongBits(getY()) * 37;
        bits += java.lang.Double.doubleToLongBits(getWidth()) * 43;
        bits += java.lang.Double.doubleToLongBits(getHeight()) * 47;
        return (int) bits ^ (int) (bits >> 32);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at) {
        return new RectIterator(this, at);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform at, double flatness) {
        return new RectIterator(this, at);
    }

    /** 5-step walk: MOVE → LINE → LINE → LINE → CLOSE. Without this, Shape's default
     *  getPathIterator returns null and anything that walks a Rectangle as a generic Shape
     *  (Geometry.intersectionPoints, BufferedImageGraphics2D.walkShape, clip-region walkers)
     *  NPEs on the first isDone() call. */
    private static final class RectIterator implements PathIterator {
        private final double x, y, w, h;
        private final AffineTransform at;
        private int index = 0;

        RectIterator(Rectangle2D r, AffineTransform at) {
            this.x = r.getX();
            this.y = r.getY();
            this.w = r.getWidth();
            this.h = r.getHeight();
            this.at = at;
            if (w < 0 || h < 0) index = 5; // degenerate — emit nothing
        }

        @Override public int getWindingRule() { return WIND_NON_ZERO; }
        @Override public boolean isDone() { return index > 4; }
        @Override public void next() { index++; }

        @Override
        public int currentSegment(float[] coords) {
            if (index == 4) return SEG_CLOSE;
            switch (index) {
                case 0: coords[0] = (float) x;       coords[1] = (float) y;       break;
                case 1: coords[0] = (float) (x + w); coords[1] = (float) y;       break;
                case 2: coords[0] = (float) (x + w); coords[1] = (float) (y + h); break;
                case 3: coords[0] = (float) x;       coords[1] = (float) (y + h); break;
                default: throw new java.util.NoSuchElementException("rect done");
            }
            if (at != null) at.transform(coords, 0, coords, 0, 1);
            return index == 0 ? SEG_MOVETO : SEG_LINETO;
        }

        @Override
        public int currentSegment(double[] coords) {
            if (index == 4) return SEG_CLOSE;
            switch (index) {
                case 0: coords[0] = x;     coords[1] = y;     break;
                case 1: coords[0] = x + w; coords[1] = y;     break;
                case 2: coords[0] = x + w; coords[1] = y + h; break;
                case 3: coords[0] = x;     coords[1] = y + h; break;
                default: throw new java.util.NoSuchElementException("rect done");
            }
            if (at != null) at.transform(coords, 0, coords, 0, 1);
            return index == 0 ? SEG_MOVETO : SEG_LINETO;
        }
    }

    public static class Double extends Rectangle2D implements Serializable {
        private static final long serialVersionUID = 7771313791441850493L;
        public double x, y, width, height;
        public Double() {}
        public Double(double x, double y, double w, double h) { setRect(x, y, w, h); }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public boolean isEmpty() { return width <= 0 || height <= 0; }
        public void setRect(double x, double y, double w, double h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }
    }

    public static class Float extends Rectangle2D implements Serializable {
        private static final long serialVersionUID = 3798716824173675777L;
        public float x, y, width, height;
        public Float() {}
        public Float(float x, float y, float w, float h) { setRect(x, y, w, h); }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public boolean isEmpty() { return width <= 0 || height <= 0; }
        public void setRect(double x, double y, double w, double h) {
            this.x = (float) x; this.y = (float) y; this.width = (float) w; this.height = (float) h;
        }
        public void setRect(float x, float y, float w, float h) {
            this.x = x; this.y = y; this.width = w; this.height = h;
        }
    }
}
