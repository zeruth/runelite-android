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
import java.awt.*;
/**
 * The JDK's OverlayLayout: every child is aligned over the others by its alignmentX/Y,
 * with the {@link SizeRequirements} aligned math. The requests are taken afresh on every
 * call rather than cached until invalidateLayout, because the shadow Container does not
 * call invalidateLayout on every invalidate; the results are those of a fresh JDK cache.
 */
public class OverlayLayout implements LayoutManager2, java.io.Serializable {
    private final Container target;

    public OverlayLayout(Container target) { this.target = target; }

    /** @since 1.6 */
    public final Container getTarget() { return target; }

    public void invalidateLayout(Container target) { checkContainer(target); }
    public void addLayoutComponent(String name, Component comp) { invalidateLayout(comp.getParent()); }
    public void removeLayoutComponent(Component comp) { invalidateLayout(comp.getParent()); }
    public void addLayoutComponent(Component comp, Object constraints) { invalidateLayout(comp.getParent()); }

    public Dimension preferredLayoutSize(Container target) {
        checkContainer(target);
        SizeRequirements[][] r = requests(target);
        return withInsets(target, new Dimension(r[2][0].preferred, r[2][1].preferred));
    }

    public Dimension minimumLayoutSize(Container target) {
        checkContainer(target);
        SizeRequirements[][] r = requests(target);
        return withInsets(target, new Dimension(r[2][0].minimum, r[2][1].minimum));
    }

    public Dimension maximumLayoutSize(Container target) {
        checkContainer(target);
        SizeRequirements[][] r = requests(target);
        return withInsets(target, new Dimension(r[2][0].maximum, r[2][1].maximum));
    }

    public float getLayoutAlignmentX(Container target) {
        checkContainer(target);
        return requests(target)[2][0].alignment;
    }

    public float getLayoutAlignmentY(Container target) {
        checkContainer(target);
        return requests(target)[2][1].alignment;
    }

    public void layoutContainer(Container target) {
        checkContainer(target);
        SizeRequirements[][] r = requests(target);
        int nChildren = target.getComponentCount();
        int[] xOffsets = new int[nChildren];
        int[] xSpans = new int[nChildren];
        int[] yOffsets = new int[nChildren];
        int[] ySpans = new int[nChildren];

        Dimension alloc = target.getSize();
        Insets in = target.getInsets();
        alloc.width -= in.left + in.right;
        alloc.height -= in.top + in.bottom;
        SizeRequirements.calculateAlignedPositions(alloc.width, r[2][0], r[0], xOffsets, xSpans);
        SizeRequirements.calculateAlignedPositions(alloc.height, r[2][1], r[1], yOffsets, ySpans);

        for (int i = 0; i < nChildren; i++) {
            Component c = target.getComponent(i);
            c.setBounds(in.left + xOffsets[i], in.top + yOffsets[i], xSpans[i], ySpans[i]);
        }
    }

    private static Dimension withInsets(Container target, Dimension size) {
        Insets insets = target.getInsets();
        size.width += insets.left + insets.right;
        size.height += insets.top + insets.bottom;
        return size;
    }

    void checkContainer(Container target) {
        if (this.target != target) {
            throw new AWTError("OverlayLayout can't be shared");
        }
    }

    /** {xChildren, yChildren, {xTotal, yTotal}}; unlike BoxLayout, invisible children count. */
    private static SizeRequirements[][] requests(Container target) {
        int n = target.getComponentCount();
        SizeRequirements[] xChildren = new SizeRequirements[n];
        SizeRequirements[] yChildren = new SizeRequirements[n];
        for (int i = 0; i < n; i++) {
            Component c = target.getComponent(i);
            Dimension min = c.getMinimumSize();
            Dimension typ = c.getPreferredSize();
            Dimension max = c.getMaximumSize();
            xChildren[i] = new SizeRequirements(min.width, typ.width, max.width, c.getAlignmentX());
            yChildren[i] = new SizeRequirements(min.height, typ.height, max.height, c.getAlignmentY());
        }
        return new SizeRequirements[][] {xChildren, yChildren, {
            SizeRequirements.getAlignedSizeRequirements(xChildren),
            SizeRequirements.getAlignedSizeRequirements(yChildren)}};
    }
}
