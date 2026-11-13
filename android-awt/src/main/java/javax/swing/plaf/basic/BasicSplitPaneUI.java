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
package javax.swing.plaf.basic;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.SplitPaneUI;

/**
 * The Basic split pane delegate: the JDK's divider bounds and a {@link BasicSplitPaneDivider} child
 * the user drags.  The pane keeps its own layout (divider location, resize weight, one-touch
 * collapse); the geometry methods read only the pane they are given, so {@link JSplitPane} answers
 * {@code getMinimumDividerLocation}/{@code getMaximumDividerLocation} through them when no UI is set.
 */
public class BasicSplitPaneUI extends SplitPaneUI {
    protected static final String NON_CONTINUOUS_DIVIDER = "nonContinuousDivider";
    protected static int KEYBOARD_DIVIDER_MOVE_OFFSET = 3;

    protected JSplitPane splitPane;
    protected BasicSplitPaneDivider divider;
    protected int dividerSize;
    protected PropertyChangeListener propertyChangeListener;
    protected int beginDragDividerLocation;

    private boolean continuousLayout = true;
    private boolean resetting;

    public BasicSplitPaneUI() {}

    public static ComponentUI createUI(JComponent x) { return new BasicSplitPaneUI(); }

    @Override public void installUI(JComponent c) {
        splitPane = (JSplitPane) c;
        dividerSize = splitPane.getDividerSize();
        continuousLayout = splitPane.isContinuousLayout();
        divider = createDefaultDivider();
        divider.setBasicSplitPaneUI(this);
        divider.setDividerSize(dividerSize);
        splitPane.add(divider, JSplitPane.DIVIDER);
        propertyChangeListener = createPropertyChangeListener();
        splitPane.addPropertyChangeListener(propertyChangeListener);
        splitPane.revalidate();
    }

    @Override public void uninstallUI(JComponent c) {
        if (splitPane == null) return;
        splitPane.removePropertyChangeListener(propertyChangeListener);
        if (divider != null) {
            splitPane.remove(divider);
            divider.setBasicSplitPaneUI(null);
        }
        splitPane.revalidate();
        propertyChangeListener = null;
        divider = null;
        splitPane = null;
    }

    protected PropertyChangeListener createPropertyChangeListener() {
        return new PropertyChangeListener() {
            @Override public void propertyChange(PropertyChangeEvent e) {
                String name = e.getPropertyName();
                if (JSplitPane.DIVIDER_SIZE_PROPERTY.equals(name)) {
                    dividerSize = splitPane.getDividerSize();
                    if (divider != null) divider.setDividerSize(dividerSize);
                } else if (JSplitPane.CONTINUOUS_LAYOUT_PROPERTY.equals(name)) {
                    continuousLayout = splitPane.isContinuousLayout();
                } else if (JSplitPane.ONE_TOUCH_EXPANDABLE_PROPERTY.equals(name)
                        || JSplitPane.ORIENTATION_PROPERTY.equals(name)) {
                    if (divider != null) divider.oneTouchExpandableChanged();
                }
            }
        };
    }

    public BasicSplitPaneDivider createDefaultDivider() { return new BasicSplitPaneDivider(this); }

    public BasicSplitPaneDivider getDivider() { return divider; }

    public JSplitPane getSplitPane() { return splitPane; }

    public int getOrientation() { return splitPane.getOrientation(); }

    public boolean isContinuousLayout() { return continuousLayout; }

    public void setContinuousLayout(boolean b) { continuousLayout = b; }

    /** The JDK's Basic divider border is one pixel wide. */
    public int getDividerBorderSize() { return 1; }

    /**
     * The pane owns its preferred-size state: {@link JSplitPane#resetToPreferredSizes()} clears it and
     * then messages this delegate, so a direct call here routes through the pane once.
     */
    @Override public void resetToPreferredSizes(JSplitPane jc) {
        if (resetting) {
            jc.revalidate();
            return;
        }
        resetting = true;
        try {
            jc.resetToPreferredSizes();
        } finally {
            resetting = false;
        }
    }

    @Override public void setDividerLocation(JSplitPane jc, int location) {
        jc.revalidate();
        jc.repaint();
    }

    @Override public int getDividerLocation(JSplitPane jc) { return jc.getDividerLocation(); }

    /** The JDK's bound: the left/top component's minimum size plus the leading inset, 0 without one. */
    @Override public int getMinimumDividerLocation(JSplitPane jc) {
        int minLoc = 0;
        Component leftC = jc.getLeftComponent();
        if (leftC != null && leftC.isVisible()) {
            boolean horizontal = jc.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
            Insets insets = jc.getInsets();
            Dimension minSize = leftC.getMinimumSize();
            minLoc = horizontal ? minSize.width : minSize.height;
            if (insets != null) minLoc += horizontal ? insets.left : insets.top;
        }
        return minLoc;
    }

    /**
     * The JDK's bound: the pane's size less the right/bottom component's minimum size, the divider and
     * the trailing inset, never below the minimum.  (The JDK subtracts {@code insets.top} for a vertical
     * split rather than {@code insets.bottom}; kept, since callers see that number on the desktop.)
     */
    @Override public int getMaximumDividerLocation(JSplitPane jc) {
        int maxLoc = 0;
        Component rightC = jc.getRightComponent();
        if (rightC != null) {
            boolean horizontal = jc.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
            Insets insets = jc.getInsets();
            Dimension minSize = rightC.isVisible() ? rightC.getMinimumSize() : new Dimension(0, 0);
            maxLoc = horizontal ? jc.getWidth() - minSize.width : jc.getHeight() - minSize.height;
            maxLoc -= jc.getDividerSize();
            if (insets != null) maxLoc -= horizontal ? insets.right : insets.top;
        }
        return Math.max(getMinimumDividerLocation(jc), maxLoc);
    }

    /**
     * Deliberately paints nothing: the JDK draws the non-continuous drag indicator here, and the
     * divider child always relayouts while it is dragged, so there is no indicator to draw.
     */
    @Override public void finishedPaintingChildren(JSplitPane jc, Graphics g) {}
}
