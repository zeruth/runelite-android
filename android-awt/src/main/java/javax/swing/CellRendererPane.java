/*
<<<<<<< HEAD
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
=======
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
>>>>>>> up
 */
package javax.swing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;

/**
<<<<<<< HEAD
 * This class is inserted in between cell renderers and the components that use them. It just
 * exists to thwart the repaint() and invalidate() methods which would otherwise propagate up
 * the tree when the renderer was configured. It's used by the implementations of JTable,
 * JTree, and JList.
 *
 * <p>The pane is invisible, so {@link RepaintManager} drops repaints raised by a renderer
 * while it is being configured, and it is never a validate root's way up: a renderer stamp
 * cannot schedule a host frame or a layout pass of the list that owns it.
 */
public class CellRendererPane extends Container implements Accessible {
    private static final long serialVersionUID = -6440002366049366575L;

    /** The accessible context of this pane, created on first request. */
    protected AccessibleContext accessibleContext = null;

=======
 * This class is inserted in between cell renderers and the components that
 * use them.  It just exists to thwart the repaint() and invalidate() methods
 * which would otherwise propagate up the tree when the renderer was configured.
 * It's used by the implementations of JTable, JTree, and JList.  For example,
 * here's how CellRendererPane is used in the code the paints each row
 * in a JList:
 * <pre>
 *   cellRendererPane = new CellRendererPane();
 *   ...
 *   Component rendererComponent = renderer.getListCellRendererComponent();
 *   renderer.configureListCellRenderer(dataModel.getElementAt(row), row);
 *   cellRendererPane.paintComponent(g, rendererComponent, this, x, y, w, h);
 * </pre>
 * <p>
 * A renderer component must override isShowing() and unconditionally return
 * true to work correctly because the Swing paint does nothing for components
 * with isShowing false.
 *
 * @author Hans Muller
 */
public class CellRendererPane extends Container implements Accessible {
>>>>>>> up
    /**
     * Construct a CellRendererPane object.
     */
    public CellRendererPane() {
        super();
        setLayout(null);
        setVisible(false);
    }

    /**
<<<<<<< HEAD
     * Overridden to avoid propagating a invalidate up the tree when the cell renderer child
     * is configured.
     */
    @Override
    public void invalidate() {
    }
=======
     * Overridden to avoid propagating a invalidate up the tree when the
     * cell renderer child is configured.
     */
    public void invalidate() { }
>>>>>>> up

    /**
     * Shouldn't be called.
     */
<<<<<<< HEAD
    @Override
    public void paint(Graphics g) {
    }
=======
    public void paint(Graphics g) { }
>>>>>>> up

    /**
     * Shouldn't be called.
     */
<<<<<<< HEAD
    @Override
    public void update(Graphics g) {
    }

    /**
     * If the specified component is already a child of this then we don't bother doing
     * anything - stacking order doesn't matter for cell renderer components (CellRendererPane
     * doesn't paint anyway).
     */
    protected void addImpl(Component x, Object constraints, int index) {
        if (x.getParent() == this) {
            return;
        }
        super.addImpl(x, constraints, index);
    }

    /**
     * Paint a cell renderer component c on graphics object g. Before the component is drawn
     * it's reparented to this (if that's necessary), it's bounds are set to w,h and the
     * graphics object is (effectively) translated to x,y. If it's a JComponent, double
     * buffering is temporarily turned off. After the component is painted it's bounds are
     * reset to -w, -h, 0, 0 so that, if it's the last renderer component painted, it will not
     * start consuming input. The Container p is the component we're actually drawing on,
     * typically it's equal to this.getParent(). If shouldValidate is true the component c
     * will be validated before painted.
=======
    public void update(Graphics g) { }

    /**
     * Paint a cell renderer component c on graphics object g.  Before the component
     * is drawn it's reparented to this (if that's necessary), it's bounds
     * are set to w,h and the graphics object is (effectively) translated to x,y.
     * If it's a JComponent, double buffering is temporarily turned off. After
     * the component is painted it's bounds are reset to -w, -h, 0, 0 so that, if
     * it's the last renderer component painted, it will not start consuming input.
     * The Container p is the component we're actually drawing on, typically it's
     * equal to this.getParent(). If shouldValidate is true the component c will be
     * validated before painted.
>>>>>>> up
     *
     * @param g  the {@code Graphics} object to draw on
     * @param c  the {@code Component} to draw
     * @param p  the {@code Container} component actually drawn on
<<<<<<< HEAD
     * @param x  an int specifying the left side of the area draw in, in pixels, measured from
     *           the left edge of the graphics context
     * @param y  an int specifying the top of the area to draw in, in pixels measured down
     *           from the top edge of the graphics context
     * @param w  an int specifying the width of the area draw in, in pixels
     * @param h  an int specifying the height of the area draw in, in pixels
     * @param shouldValidate if true, component {@code c} will be validated before being
     *           painted
     */
    public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h,
                               boolean shouldValidate) {
=======
     * @param x  an int specifying the left side of the area draw in, in pixels,
     *           measured from the left edge of the graphics context
     * @param y  an int specifying the top of the area to draw in, in pixels
     *           measured down from the top edge of the graphics context
     * @param w  an int specifying the width of the area draw in, in pixels
     * @param h  an int specifying the height of the area draw in, in pixels
     * @param shouldValidate  if true, component {@code c} will be validated
     *                        before being painted
     */
    public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
>>>>>>> up
        if (c == null) {
            if (p != null) {
                Color oldColor = g.getColor();
                g.setColor(p.getBackground());
                g.fillRect(x, y, w, h);
                g.setColor(oldColor);
            }
            return;
        }

<<<<<<< HEAD
        if (c.getParent() != this) {
            addImpl(c, null, -1);
=======
        // The shadow Container has no addImpl hook, so the JDK's "already a child"
        // short-circuit lives here: re-adding would move c to the end of the list.
        if (c.getParent() != this) {
            this.add(c);
>>>>>>> up
        }

        c.setBounds(x, y, w, h);

        if (shouldValidate) {
            c.validate();
        }

        boolean wasDoubleBuffered = false;
        if ((c instanceof JComponent) && ((JComponent) c).isDoubleBuffered()) {
            wasDoubleBuffered = true;
            ((JComponent) c).setDoubleBuffered(false);
        }

        Graphics cg = g.create(x, y, w, h);
        try {
            c.paint(cg);
        } finally {
            cg.dispose();
        }

        if (wasDoubleBuffered && (c instanceof JComponent)) {
            ((JComponent) c).setDoubleBuffered(true);
        }

        c.setBounds(-w, -h, 0, 0);
    }

    /**
     * Calls this.paintComponent(g, c, p, x, y, w, h, false).
     *
     * @param g  the {@code Graphics} object to draw on
     * @param c  the {@code Component} to draw
     * @param p  the {@code Container} component actually drawn on
<<<<<<< HEAD
     * @param x  an int specifying the left side of the area draw in, in pixels
     * @param y  an int specifying the top of the area to draw in, in pixels
=======
     * @param x  an int specifying the left side of the area draw in, in pixels,
     *           measured from the left edge of the graphics context
     * @param y  an int specifying the top of the area to draw in, in pixels
     *           measured down from the top edge of the graphics context
>>>>>>> up
     * @param w  an int specifying the width of the area draw in, in pixels
     * @param h  an int specifying the height of the area draw in, in pixels
     */
    public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h) {
        paintComponent(g, c, p, x, y, w, h, false);
    }

    /**
<<<<<<< HEAD
     * Calls this.paintComponent(g, c, p, r.x, r.y, r.width, r.height) where r is the
     * rectangle.
=======
     * Calls this.paintComponent() with the rectangles x,y,width,height fields.
>>>>>>> up
     *
     * @param g  the {@code Graphics} object to draw on
     * @param c  the {@code Component} to draw
     * @param p  the {@code Container} component actually drawn on
     * @param r  the {@code Rectangle} to draw in
     */
    public void paintComponent(Graphics g, Component c, Container p, Rectangle r) {
        paintComponent(g, c, p, r.x, r.y, r.width, r.height);
    }

<<<<<<< HEAD
    /**
     * Gets the AccessibleContext associated with this CellRendererPane.
     *
     * @return an AccessibleContext that serves as the AccessibleContext of this
     *         CellRendererPane
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleContext() {};
=======
    /** {@code AccessibleContext} associated with this {@code CellRendererPane} */
    protected AccessibleContext accessibleContext = null;

    /**
     * Gets the AccessibleContext associated with this CellRendererPane.
     * The shadow's AccessibleContext has no role model, so this is a plain context
     * rather than the JDK's AccessibleCellRendererPane (role PANEL).
     *
     * @return an AccessibleContext associated with this CellRendererPane
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleContext() { };
>>>>>>> up
        }
        return accessibleContext;
    }
}
