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
package javax.swing;

import java.awt.AWTError;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.io.Serializable;

/**
 * The JDK's BoxLayout over {@link SizeRequirements}. Along the axis children are tiled:
 * a deficit is taken from each in proportion to (preferred - minimum), a surplus handed
 * out in proportion to (maximum - preferred) — that is how glue absorbs slack and how a
 * label with setMinimumSize(1, h) gives way to its row's buttons. Across the axis each
 * child grows up to its maximum and is placed by its alignmentX/Y. Placing everything at
 * preferred size instead let long quest-helper titles push controls off the panel.
 *
 * LINE_AXIS/PAGE_AXIS resolve as for a left-to-right, horizontal orientation.
 */
public class BoxLayout implements LayoutManager2, Serializable {
    private static final long serialVersionUID = 1L;

    public static final int X_AXIS = 0;
    public static final int Y_AXIS = 1;
    public static final int LINE_AXIS = 2;
    public static final int PAGE_AXIS = 3;

    private final int axis;
    private final Container target;

    /**
     * Creates a layout manager that will lay out components along the
     * given axis.
     *
     * @exception AWTError if the value of <code>axis</code> is invalid
     */
    public BoxLayout(Container target, int axis) {
        if (axis != X_AXIS && axis != Y_AXIS && axis != LINE_AXIS && axis != PAGE_AXIS) {
            throw new AWTError("Invalid axis");
        }
        this.axis = axis;
        this.target = target;
    }

    /** @since 1.6 */
    public final Container getTarget() { return target; }

    /** @since 1.6 */
    public final int getAxis() { return axis; }

    /**
     * Indicates that a child has changed its layout related information,
     * and thus any cached calculations should be flushed.
     *
     * @exception AWTError if the target isn't the container specified to the
     *                      BoxLayout constructor
     */
    public synchronized void invalidateLayout(Container target) {
        checkContainer(target);
        xChildren = null;
        yChildren = null;
        xTotal = null;
        yTotal = null;
    }

    /** Not used by this class. */
    public void addLayoutComponent(String name, Component comp) {
        invalidateLayout(comp.getParent());
    }

    /** Not used by this class. */
    public void removeLayoutComponent(Component comp) {
        invalidateLayout(comp.getParent());
    }

    /** Not used by this class. */
    public void addLayoutComponent(Component comp, Object constraints) {
        invalidateLayout(comp.getParent());
    }

    /**
     * Returns the preferred dimensions for this layout, given the components
     * in the specified target container.
     */
    public Dimension preferredLayoutSize(Container target) {
        Dimension size;
        synchronized (this) {
            checkContainer(target);
            checkRequests();
            size = new Dimension(xTotal.preferred, yTotal.preferred);
        }
        return withInsets(target, size);
    }

    /** Returns the minimum dimensions needed to lay out the components contained in the specified target container. */
    public Dimension minimumLayoutSize(Container target) {
        Dimension size;
        synchronized (this) {
            checkContainer(target);
            checkRequests();
            size = new Dimension(xTotal.minimum, yTotal.minimum);
        }
        return withInsets(target, size);
    }

    /** Returns the maximum dimensions the target container can use to lay out the components it contains. */
    public Dimension maximumLayoutSize(Container target) {
        Dimension size;
        synchronized (this) {
            checkContainer(target);
            checkRequests();
            size = new Dimension(xTotal.maximum, yTotal.maximum);
        }
        return withInsets(target, size);
    }

    private static Dimension withInsets(Container target, Dimension size) {
        Insets insets = target.getInsets();
        size.width = (int) Math.min((long) size.width + (long) insets.left + (long) insets.right, Integer.MAX_VALUE);
        size.height = (int) Math.min((long) size.height + (long) insets.top + (long) insets.bottom, Integer.MAX_VALUE);
        return size;
    }

    /** Returns the alignment along the X axis for the container. */
    public synchronized float getLayoutAlignmentX(Container target) {
        checkContainer(target);
        checkRequests();
        return xTotal.alignment;
    }

    /** Returns the alignment along the Y axis for the container. */
    public synchronized float getLayoutAlignmentY(Container target) {
        checkContainer(target);
        checkRequests();
        return yTotal.alignment;
    }

    /**
     * Called by the AWT when the specified container needs to be laid out.
     *
     * @exception AWTError  if the target isn't the container specified to the
     *                      BoxLayout constructor
     */
    public void layoutContainer(Container target) {
        checkContainer(target);
        int nChildren = target.getComponentCount();
        int[] xOffsets = new int[nChildren];
        int[] xSpans = new int[nChildren];
        int[] yOffsets = new int[nChildren];
        int[] ySpans = new int[nChildren];

        Dimension alloc = target.getSize();
        Insets in = target.getInsets();
        alloc.width -= in.left + in.right;
        alloc.height -= in.top + in.bottom;

        // There is no ComponentOrientation here: LINE_AXIS and PAGE_AXIS resolve as for a
        // left-to-right, horizontal container, and ltr is always true.
        int absoluteAxis = resolveAxis(axis);
        synchronized (this) {
            checkRequests();
            if (absoluteAxis == X_AXIS) {
                SizeRequirements.calculateTiledPositions(alloc.width, xTotal, xChildren, xOffsets, xSpans, true);
                SizeRequirements.calculateAlignedPositions(alloc.height, yTotal, yChildren, yOffsets, ySpans);
            } else {
                SizeRequirements.calculateAlignedPositions(alloc.width, xTotal, xChildren, xOffsets, xSpans, true);
                // Wrapped controls (HTML labels, line-wrapped text) must measure their height
                // at the width they are about to get: size them across first and, if that
                // changed any measurement, take the vertical requests again.
                long before = generation(target);
                for (int i = 0; i < nChildren; i++) {
                    Component c = target.getComponent(i);
                    if (c.isVisible() && c.getWidth() != xSpans[i]) {
                        c.setSize(xSpans[i], Math.max(0, c.getHeight()));
                    }
                }
                if (before != NO_GENERATION && generation(target) != before) {
                    xChildren = null;
                    checkRequests();
                }
                SizeRequirements.calculateTiledPositions(alloc.height, yTotal, yChildren, yOffsets, ySpans);
            }
        }

        for (int i = 0; i < nChildren; i++) {
            Component c = target.getComponent(i);
            c.setBounds((int) Math.min((long) in.left + (long) xOffsets[i], Integer.MAX_VALUE),
                        (int) Math.min((long) in.top + (long) yOffsets[i], Integer.MAX_VALUE),
                        xSpans[i], ySpans[i]);
        }
    }

    void checkContainer(Container target) {
        if (this.target != target) {
            throw new AWTError("BoxLayout can't be shared");
        }
    }

    void checkRequests() {
        // The JDK keeps the requests until invalidateLayout; the shadow Container does not
        // call invalidateLayout on every invalidate, so the target subtree's generation
        // also expires them. Without a cache, measuring a BoxLayout asks every child for
        // min, pref and max, and a nested BoxLayout re-measures its subtree for each:
        // 3^depth (Loot Lookup's nested result rows froze the EDT for seconds per search).
        long gen = generation(target);
        if (gen != NO_GENERATION && gen != cachedGeneration) {
            xChildren = null;
        }
        if (xChildren == null || yChildren == null) {
            int n = target.getComponentCount();
            SizeRequirements[] xs = new SizeRequirements[n];
            SizeRequirements[] ys = new SizeRequirements[n];
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (!c.isVisible()) {
                    xs[i] = new SizeRequirements(0, 0, 0, c.getAlignmentX());
                    ys[i] = new SizeRequirements(0, 0, 0, c.getAlignmentY());
                    continue;
                }
                Dimension min = c.getMinimumSize();
                Dimension typ = c.getPreferredSize();
                Dimension max = c.getMaximumSize();
                xs[i] = new SizeRequirements(min.width, typ.width, max.width, c.getAlignmentX());
                ys[i] = new SizeRequirements(min.height, typ.height, max.height, c.getAlignmentY());
            }
            xChildren = xs;
            yChildren = ys;
            if (resolveAxis(axis) == X_AXIS) {
                xTotal = SizeRequirements.getTiledSizeRequirements(xChildren);
                yTotal = SizeRequirements.getAlignedSizeRequirements(yChildren);
            } else {
                xTotal = SizeRequirements.getAlignedSizeRequirements(xChildren);
                yTotal = SizeRequirements.getTiledSizeRequirements(yChildren);
            }
            // Measuring a child may itself invalidate (a wrapping text component sizing
            // itself); keep the result only if the tree stood still while it was taken.
            long after = generation(target);
            cachedGeneration = after == gen ? gen : STALE;
        }
    }

    private static int resolveAxis(int axis) {
        return axis == LINE_AXIS ? X_AXIS : axis == PAGE_AXIS ? Y_AXIS : axis;
    }

    private static final long NO_GENERATION = LayoutGeneration.NONE;
    private static final long STALE = Long.MAX_VALUE;

    private static long generation(Container target) {
        return LayoutGeneration.of(target);
    }

    private transient SizeRequirements[] xChildren;
    private transient SizeRequirements[] yChildren;
    private transient SizeRequirements xTotal;
    private transient SizeRequirements yTotal;
    private transient long cachedGeneration = NO_GENERATION;
}
