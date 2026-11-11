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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.io.Serializable;

/**
 * A port of the JDK's BoxLayout + SizeRequirements. Along the axis children are tiled:
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

    public BoxLayout(Container target, int axis) {
        if (axis != X_AXIS && axis != Y_AXIS && axis != LINE_AXIS && axis != PAGE_AXIS) {
            throw new IllegalArgumentException("Invalid axis: " + axis);
        }
        this.target = target; this.axis = axis;
    }

    public final int getAxis() { return axis; }
    public final Container getTarget() { return target; }

    @Override public void addLayoutComponent(String name, Component comp) {}
    @Override public void addLayoutComponent(Component comp, Object constraints) {}
    @Override public void removeLayoutComponent(Component comp) {}
    @Override public void invalidateLayout(Container target) { cachedX = cachedY = null; }

    private boolean isXAxis() { return axis == X_AXIS || axis == LINE_AXIS; }

    /** SizeRequirements: one child's (or a total's) min/pref/max along an axis + alignment. */
    private static final class Req {
        int minimum, preferred, maximum;
        float alignment;
        Req(int min, int pref, int max, float a) { minimum = min; preferred = pref; maximum = max; alignment = a; }
    }

    // The JDK caches SizeRequirements until invalidateLayout. Without a cache, measuring a
    // BoxLayout asks every child for min, pref AND max, and a child that is itself a BoxLayout
    // re-measures its whole subtree for each — 3^depth. Loot Lookup's nested result rows froze
    // the EDT for seconds per search. Entries track the target subtree's generation,
    // so detached renderer construction cannot discard otherwise valid measurements.
    private Req[] cachedX, cachedY;
    private long cachedXGen = -1, cachedYGen = -1;
    private Container cachedFor;

    private Req[] requests(Container t, boolean xAxis) {
        long gen = t.subtreeLayoutGeneration();
        if (t == cachedFor) {
            if (xAxis && cachedX != null && cachedXGen == gen) return cachedX;
            if (!xAxis && cachedY != null && cachedYGen == gen) return cachedY;
        } else {
            cachedFor = t;
            cachedX = cachedY = null;
        }
        Req[] r = computeRequests(t, xAxis);
        // Measuring a child may itself invalidate (a wrapping text component sizing itself);
        // only keep the result if the tree stood still while it was taken.
        if (t.subtreeLayoutGeneration() == gen) {
            if (xAxis) { cachedX = r; cachedXGen = gen; } else { cachedY = r; cachedYGen = gen; }
        }
        return r;
    }

    private static Req[] computeRequests(Container t, boolean xAxis) {
        int n = t.getComponentCount();
        Req[] r = new Req[n];
        for (int i = 0; i < n; i++) {
            Component c = t.getComponent(i);
            // Our java.awt.Component has no alignment accessors; AWT's default is CENTER.
            float align = !(c instanceof JComponent) ? 0.5f
                : xAxis ? ((JComponent) c).getAlignmentX() : ((JComponent) c).getAlignmentY();
            if (!c.isVisible()) { r[i] = new Req(0, 0, 0, align); continue; }
            Dimension min = c.getMinimumSize(), pref = c.getPreferredSize(), max = c.getMaximumSize();
            r[i] = xAxis ? new Req(min.width, pref.width, max.width, align)
                         : new Req(min.height, pref.height, max.height, align);
        }
        return r;
    }

    private static int clamp(long v) { return (int) Math.min(v, Integer.MAX_VALUE); }

    private static Req tiledTotal(Req[] children) {
        long min = 0, pref = 0, max = 0;
        for (Req c : children) { min += c.minimum; pref += c.preferred; max += c.maximum; }
        return new Req(clamp(min), clamp(pref), clamp(max), 0.5f);
    }

    private static Req alignedTotal(Req[] children) {
        int ascMin = 0, ascPref = 0, ascMax = 0, descMin = 0, descPref = 0, descMax = 0;
        for (Req r : children) {
            int a = (int) (r.alignment * r.minimum);
            ascMin = Math.max(a, ascMin); descMin = Math.max(r.minimum - a, descMin);
            a = (int) (r.alignment * r.preferred);
            ascPref = Math.max(a, ascPref); descPref = Math.max(r.preferred - a, descPref);
            a = (int) (r.alignment * r.maximum);
            ascMax = Math.max(a, ascMax); descMax = Math.max(r.maximum - a, descMax);
        }
        int min = clamp((long) ascMin + descMin);
        int pref = clamp((long) ascPref + descPref);
        int max = clamp((long) ascMax + descMax);
        float alignment = 0f;
        if (min > 0) alignment = Math.max(0f, Math.min(1f, (float) ascMin / min));
        return new Req(min, pref, max, alignment);
    }

    private static void tiledPositions(int allocated, Req[] children, int[] offsets, int[] spans) {
        long min = 0, pref = 0, max = 0;
        for (Req c : children) { min += c.minimum; pref += c.preferred; max += c.maximum; }
        int total = 0;
        if (allocated >= pref) {
            float totalPlay = Math.min(allocated - pref, max - pref);
            float factor = (max - pref == 0) ? 0f : totalPlay / (max - pref);
            for (int i = 0; i < children.length; i++) {
                Req r = children[i];
                offsets[i] = total;
                float play = factor * ((long) r.maximum - r.preferred);
                spans[i] = clamp((long) r.preferred + (long) play);
                total = clamp((long) total + spans[i]);
            }
        } else {
            float totalPlay = Math.min(pref - allocated, pref - min);
            float factor = (pref - min == 0) ? 0f : totalPlay / (pref - min);
            for (int i = 0; i < children.length; i++) {
                Req r = children[i];
                offsets[i] = total;
                float play = factor * (r.preferred - r.minimum);
                spans[i] = (int) (r.preferred - play);
                total = clamp((long) total + spans[i]);
            }
        }
    }

    private static void alignedPositions(int allocated, Req total, Req[] children, int[] offsets, int[] spans) {
        int totalAscent = (int) (allocated * total.alignment);
        int totalDescent = allocated - totalAscent;
        for (int i = 0; i < children.length; i++) {
            Req r = children[i];
            int maxAscent = (int) (r.maximum * r.alignment);
            int maxDescent = r.maximum - maxAscent;
            int ascent = Math.min(totalAscent, maxAscent);
            int descent = Math.min(totalDescent, maxDescent);
            offsets[i] = totalAscent - ascent;
            spans[i] = clamp((long) ascent + descent);
        }
    }

    /** kind: 0 = minimum, 1 = preferred, 2 = maximum. */
    private Dimension layoutSize(Container t, int kind) {
        Req[] xs = requests(t, true), ys = requests(t, false);
        Req xt = isXAxis() ? tiledTotal(xs) : alignedTotal(xs);
        Req yt = isXAxis() ? alignedTotal(ys) : tiledTotal(ys);
        Insets in = t.getInsets();
        long w = kind == 0 ? xt.minimum : kind == 1 ? xt.preferred : xt.maximum;
        long h = kind == 0 ? yt.minimum : kind == 1 ? yt.preferred : yt.maximum;
        return new Dimension(clamp(w + in.left + in.right), clamp(h + in.top + in.bottom));
    }

    @Override public Dimension preferredLayoutSize(Container t) { return layoutSize(t, 1); }
    @Override public Dimension minimumLayoutSize(Container t) { return layoutSize(t, 0); }
    @Override public Dimension maximumLayoutSize(Container t) { return layoutSize(t, 2); }

    @Override public float getLayoutAlignmentX(Container t) {
        return isXAxis() ? 0.5f : alignedTotal(requests(t, true)).alignment;
    }
    @Override public float getLayoutAlignmentY(Container t) {
        return isXAxis() ? alignedTotal(requests(t, false)).alignment : 0.5f;
    }

    @Override
    public void layoutContainer(Container t) {
        int n = t.getComponentCount();
        if (n == 0) return;
        Insets in = t.getInsets();
        int allocW = Math.max(0, t.getWidth() - in.left - in.right);
        int allocH = Math.max(0, t.getHeight() - in.top - in.bottom);
        int[] xOffsets = new int[n], xSpans = new int[n], yOffsets = new int[n], ySpans = new int[n];

        Req[] xs = requests(t, true);
        if (isXAxis()) {
            tiledPositions(allocW, xs, xOffsets, xSpans);
            Req[] ys = requests(t, false);
            alignedPositions(allocH, alignedTotal(ys), ys, yOffsets, ySpans);
        } else {
            alignedPositions(allocW, alignedTotal(xs), xs, xOffsets, xSpans);
            // Wrapped controls (HTML labels, line-wrapped text) must measure their height at
            // the width they are about to get, so size them across first.
            for (int i = 0; i < n; i++) {
                Component c = t.getComponent(i);
                if (c.isVisible() && c.getWidth() != xSpans[i]) c.setSize(xSpans[i], Math.max(0, c.getHeight()));
            }
            Req[] ys = requests(t, false);
            tiledPositions(allocH, ys, yOffsets, ySpans);
        }
        for (int i = 0; i < n; i++) {
            Component c = t.getComponent(i);
            c.setBounds(clamp((long) in.left + xOffsets[i]), clamp((long) in.top + yOffsets[i]), xSpans[i], ySpans[i]);
        }
    }
}
