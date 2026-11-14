/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.swing.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.Serializable;

/**
 * A class that implements an empty border with no size.
 * This provides a convenient base class from which other border
 * classes can be easily derived.
 *
 * @author David Kloba
 */
@SuppressWarnings("serial")
public abstract class AbstractBorder implements Border, Serializable
{
    /**
     * This default implementation does no painting.
     */
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    }

    /**
     * This default implementation returns a new {@link Insets} object
     * that is initialized by the {@link #getBorderInsets(Component,Insets)}
     * method.
     * By default the {@code top}, {@code left}, {@code bottom},
     * and {@code right} fields are set to {@code 0}.
     */
    public Insets getBorderInsets(Component c)       {
        return getBorderInsets(c, new Insets(0, 0, 0, 0));
    }

    /**
     * Reinitializes the insets parameter with this Border's current Insets.
     *
     * @param c the component for which this border insets value applies
     * @param insets the object to be reinitialized
     * @return the <code>insets</code> object
     */
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = insets.top = insets.right = insets.bottom = 0;
        return insets;
    }

    /**
     * This default implementation returns false.
     */
    public boolean isBorderOpaque() { return false; }

    /**
     * This convenience method calls the static method.
     */
    public Rectangle getInteriorRectangle(Component c, int x, int y, int width, int height) {
        return getInteriorRectangle(c, this, x, y, width, height);
    }

    /**
     * Returns a rectangle using the arguments minus the
     * insets of the border. This is useful for determining the area
     * that components should draw in that will not intersect the border.
     */
    public static Rectangle getInteriorRectangle(Component c, Border b, int x, int y, int width, int height) {
        Insets insets;
        if(b != null)
            insets = b.getBorderInsets(c);
        else
            insets = new Insets(0, 0, 0, 0);
        return new Rectangle(x + insets.left,
                                    y + insets.top,
                                    width - insets.right - insets.left,
                                    height - insets.top - insets.bottom);
    }

    /**
     * Returns the baseline.  A return value less than 0 indicates the border
     * does not have a reasonable baseline.
     * <p>
     * The default implementation returns -1.  Subclasses that support
     * baseline should override appropriately.
     *
     * @throws NullPointerException if {@code c} is {@code null}
     * @throws IllegalArgumentException if width or height is &lt; 0
     * @since 1.6
     */
    public int getBaseline(Component c, int width, int height) {
        if (c == null) {
            throw new NullPointerException(
                    "Must supply non-null component");
        }
        if (width < 0) {
            throw new IllegalArgumentException("Width must be >= 0");
        }
        if (height < 0) {
            throw new IllegalArgumentException("Height must be >= 0");
        }
        return -1;
    }

    /**
     * Returns an enum indicating how the baseline of a component
     * changes as the size changes.
     * <p>
     * The default implementation returns
     * <code>BaselineResizeBehavior.OTHER</code>.
     *
     * @throws NullPointerException if {@code c} is {@code null}
     * @since 1.6
     */
    public Component.BaselineResizeBehavior getBaselineResizeBehavior(
            Component c) {
        if (c == null) {
            throw new NullPointerException("Must supply non-null component");
        }
        return Component.BaselineResizeBehavior.OTHER;
    }

    /*
     * Convenience function for determining ComponentOrientation.
     * Helps us avoid having Munge directives throughout the code.
     */
    static boolean isLeftToRight( Component c ) {
        return c.getComponentOrientation().isLeftToRight();
    }

}
