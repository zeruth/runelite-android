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

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

public class Rectangle extends Rectangle2D implements Shape, Serializable {
    private static final long serialVersionUID = -4345857070255674764L;

    public int x;
    public int y;
    public int width;
    public int height;

    public Rectangle() {
    }

    public Rectangle(Rectangle r) {
        this(r.x, r.y, r.width, r.height);
    }

    public Rectangle(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public Rectangle(int width, int height) {
        this(0, 0, width, height);
    }

    public Rectangle(Point p, Dimension d) {
        this(p.x, p.y, d.width, d.height);
    }

    public Rectangle(Point p) {
        this(p.x, p.y, 0, 0);
    }

    public Rectangle(Dimension d) {
        this(0, 0, d.width, d.height);
    }

    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public double getWidth() { return width; }
    @Override public double getHeight() { return height; }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(this);
    }

    @Override
    public Rectangle2D getBounds2D() {
        return new Rectangle(this);
    }

    public void setBounds(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.width = w; this.height = h;
    }

    public void setBounds(Rectangle r) {
        setBounds(r.x, r.y, r.width, r.height);
    }

    @Override
    public void setRect(double x, double y, double w, double h) {
        this.x = (int) Math.floor(x);
        this.y = (int) Math.floor(y);
        this.width = (int) Math.ceil(x + w) - this.x;
        this.height = (int) Math.ceil(y + h) - this.y;
    }

    public Point getLocation() {
        return new Point(x, y);
    }

    public void setLocation(int x, int y) {
        this.x = x; this.y = y;
    }

    public void setLocation(Point p) {
        setLocation(p.x, p.y);
    }

    public Dimension getSize() {
        return new Dimension(width, height);
    }

    public void setSize(int width, int height) {
        this.width = width; this.height = height;
    }

    public void setSize(Dimension d) {
        setSize(d.width, d.height);
    }

    public boolean contains(int x, int y) {
        return contains(x, y, 1, 1);
    }

    public boolean contains(Point p) {
        return contains(p.x, p.y);
    }

    public boolean contains(int x, int y, int w, int h) {
        if (isEmpty() || w <= 0 || h <= 0) return false;
        return x >= this.x && y >= this.y
            && x + w <= this.x + this.width
            && y + h <= this.y + this.height;
    }

    public boolean contains(Rectangle r) {
        return contains(r.x, r.y, r.width, r.height);
    }

    public boolean intersects(Rectangle r) {
        return !isEmpty() && r.width > 0 && r.height > 0
            && x + width > r.x && y + height > r.y
            && x < r.x + r.width && y < r.y + r.height;
    }

    public Rectangle intersection(Rectangle r) {
        int x1 = Math.max(x, r.x);
        int y1 = Math.max(y, r.y);
        int x2 = Math.min(x + width, r.x + r.width);
        int y2 = Math.min(y + height, r.y + r.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public Rectangle union(Rectangle r) {
        int x1 = Math.min(x, r.x);
        int y1 = Math.min(y, r.y);
        int x2 = Math.max(x + width, r.x + r.width);
        int y2 = Math.max(y + height, r.y + r.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    public void add(int newx, int newy) {
        int x1 = Math.min(x, newx);
        int y1 = Math.min(y, newy);
        int x2 = Math.max(x + width, newx);
        int y2 = Math.max(y + height, newy);
        this.x = x1; this.y = y1; this.width = x2 - x1; this.height = y2 - y1;
    }

    public void add(Point pt) {
        add(pt.x, pt.y);
    }

    public void add(Rectangle r) {
        Rectangle u = union(r);
        setBounds(u.x, u.y, u.width, u.height);
    }

    public void grow(int h, int v) {
        x -= h; y -= v; width += 2 * h; height += 2 * v;
    }

    public void translate(int dx, int dy) {
        x += dx; y += dy;
    }

    @Override
    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Rectangle) {
            Rectangle r = (Rectangle) obj;
            return x == r.x && y == r.y && width == r.width && height == r.height;
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[x=" + x + ",y=" + y + ",width=" + width + ",height=" + height + "]";
    }
}
