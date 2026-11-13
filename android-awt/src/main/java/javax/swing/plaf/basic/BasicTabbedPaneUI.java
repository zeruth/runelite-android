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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.TabbedPaneUI;

/**
 * The Basic tabbed pane delegate with the JDK's tab geometry: tab widths from the title's font
 * metrics, the icon and {@code tabInsets}, heights from the tallest tab, wrapped runs that are
 * normalized, rotated so the selected run touches the content and padded to the full width, and
 * the selected tab grown by {@code selectedTabPadInsets}. {@code SCROLL_TAB_LAYOUT} keeps one run
 * and scrolls it with a pair of arrow cells at the end of the tab area (pressed or wheel-scrolled,
 * sized for a fingertip) instead of the JDK's arrow-button children.
 * <p>
 * {@code RIGHT} placement is the RuneLite sidebar: the pane keeps its own fixed-slot icon strip
 * there, and this delegate answers from the pane rather than computing JDK rectangles.
 */
public class BasicTabbedPaneUI extends TabbedPaneUI implements SwingConstants {
    /** Width of one scroll arrow cell; a fingertip rather than the JDK's 16px button. */
    private static final int SCROLL_CELL = 24;

    protected JTabbedPane tabPane;

    protected Color highlight;
    protected Color lightHighlight;
    protected Color shadow;
    protected Color darkShadow;
    protected Color focus;

    protected int textIconGap;
    protected int tabRunOverlay;

    protected Insets tabInsets;
    protected Insets selectedTabPadInsets;
    protected Insets tabAreaInsets;
    protected Insets contentBorderInsets;

    protected int[] tabRuns = new int[10];
    protected int runCount = 0;
    protected int selectedRun = -1;
    protected Rectangle[] rects = new Rectangle[0];
    protected int maxTabHeight;
    protected int maxTabWidth;

    protected transient Rectangle calcRect = new Rectangle(0, 0, 0, 0);

    private Color selectedColor;
    private MouseAdapter scrollInput;
    /** Pixels the single run of SCROLL_TAB_LAYOUT is shifted towards its start. */
    private int scrollOffset;
    private boolean scrollOverflow;

    public BasicTabbedPaneUI() {}

    public static ComponentUI createUI(JComponent c) { return new BasicTabbedPaneUI(); }

    @Override public void installUI(JComponent c) {
        tabPane = (JTabbedPane) c;
        installDefaults();
        tabPane.setLayout(createLayoutManager());
        installListeners();
    }

    @Override public void uninstallUI(JComponent c) {
        uninstallListeners();
        uninstallDefaults();
        if (tabPane.getLayout() instanceof TabbedPaneLayout) tabPane.setLayout(null);
        tabPane = null;
        rects = new Rectangle[0];
    }

    /** The JDK's Basic values, taken from the look and feel when it defines them. */
    protected void installDefaults() {
        highlight = color("TabbedPane.light", new Color(0xDFDFDF));
        lightHighlight = color("TabbedPane.highlight", Color.WHITE);
        shadow = color("TabbedPane.shadow", new Color(0x808080));
        darkShadow = color("TabbedPane.darkShadow", Color.BLACK);
        focus = color("TabbedPane.focus", Color.BLACK);
        selectedColor = UIManager.getColor("TabbedPane.selected");
        textIconGap = intValue("TabbedPane.textIconGap", 4);
        tabInsets = insets("TabbedPane.tabInsets", new Insets(0, 4, 1, 4));
        selectedTabPadInsets = insets("TabbedPane.selectedTabPadInsets", new Insets(2, 2, 2, 1));
        tabAreaInsets = insets("TabbedPane.tabAreaInsets", new Insets(3, 2, 0, 2));
        contentBorderInsets = insets("TabbedPane.contentBorderInsets", new Insets(2, 2, 3, 3));
        tabRunOverlay = intValue("TabbedPane.tabRunOverlay", 2);
    }

    protected void uninstallDefaults() {
        highlight = lightHighlight = shadow = darkShadow = focus = selectedColor = null;
        tabInsets = selectedTabPadInsets = tabAreaInsets = contentBorderInsets = null;
    }

    /** The pointer side of SCROLL_TAB_LAYOUT: a press on an arrow cell or the wheel over the tab area scrolls. */
    protected void installListeners() {
        scrollInput = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (tabPane == null || !tabPane.isEnabled() || e.getButton() != MouseEvent.BUTTON1) return;
                int direction = scrollCellAt(e.getX(), e.getY());
                if (direction != 0) {
                    scrollBy(direction);
                    e.consume();
                    return;
                }
                // The JDK's Handler selects the enabled tab under a press; the RIGHT strip keeps its own input.
                if (paneStrip()) return;
                int index = tabForCoordinate(tabPane, e.getX(), e.getY());
                if (index >= 0 && tabPane.isEnabledAt(index)) tabPane.setSelectedIndex(index);
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (tabPane == null || !scrollOverflow || !inTabArea(e.getX(), e.getY())) return;
                scrollBy(e.getWheelRotation());
                e.consume();
            }
        };
        tabPane.addMouseListener(scrollInput);
        tabPane.addMouseWheelListener(scrollInput);
    }

    protected void uninstallListeners() {
        if (scrollInput != null) {
            tabPane.removeMouseListener(scrollInput);
            tabPane.removeMouseWheelListener(scrollInput);
            scrollInput = null;
        }
    }

    protected LayoutManager createLayoutManager() { return new TabbedPaneLayout(); }

    private static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }

    private static Insets insets(String key, Insets fallback) {
        Insets i = UIManager.getInsets(key);
        return i != null ? (Insets) i.clone() : fallback;
    }

    private static int intValue(String key, int fallback) {
        Object value = UIManager.get(key);
        return value instanceof Integer ? ((Integer) value).intValue() : fallback;
    }

    // --- geometry -----------------------------------------------------------------------------

    /** The pane's own strip answers for RIGHT; every other placement uses the JDK rectangles. */
    private boolean paneStrip() { return tabPane.getTabPlacement() == RIGHT; }

    private boolean scrollable() { return tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT; }

    protected Insets getTabInsets(int tabPlacement, int tabIndex) { return tabInsets; }

    protected Insets getSelectedTabPadInsets(int tabPlacement) { return selectedTabPadInsets; }

    protected Insets getTabAreaInsets(int tabPlacement) { return tabAreaInsets; }

    protected Insets getContentBorderInsets(int tabPlacement) { return contentBorderInsets; }

    protected int getTabRunOverlay(int tabPlacement) { return tabRunOverlay; }

    protected int getTabRunIndent(int tabPlacement, int run) { return 0; }

    protected boolean shouldPadTabRun(int tabPlacement, int run) { return runCount > 1; }

    protected boolean shouldRotateTabRuns(int tabPlacement) { return true; }

    protected Icon getIconForTab(int tabIndex) {
        return !tabPane.isEnabled() || !tabPane.isEnabledAt(tabIndex)
                ? (tabPane.getDisabledIconAt(tabIndex) != null ? tabPane.getDisabledIconAt(tabIndex) : tabPane.getIconAt(tabIndex))
                : tabPane.getIconAt(tabIndex);
    }

    protected FontMetrics getFontMetrics() {
        Font font = tabPane.getFont();
        if (font == null) font = new Font(Font.DIALOG, Font.PLAIN, 12);
        return tabPane.getFontMetrics(font);
    }

    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
        int height;
        Component tabComponent = tabPane.getTabComponentAt(tabIndex);
        if (tabComponent != null) {
            height = tabComponent.getPreferredSize().height;
        } else {
            height = fontHeight;
            Icon icon = getIconForTab(tabIndex);
            if (icon != null) height = Math.max(height, icon.getIconHeight());
        }
        Insets insets = getTabInsets(tabPlacement, tabIndex);
        return height + insets.top + insets.bottom + 2;
    }

    protected int calculateMaxTabHeight(int tabPlacement) {
        int fontHeight = getFontMetrics().getHeight(), result = 0;
        for (int i = 0; i < tabPane.getTabCount(); i++) {
            result = Math.max(calculateTabHeight(tabPlacement, i, fontHeight), result);
        }
        return result;
    }

    protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
        Insets insets = getTabInsets(tabPlacement, tabIndex);
        int width = insets.left + insets.right + 3;
        Component tabComponent = tabPane.getTabComponentAt(tabIndex);
        if (tabComponent != null) {
            width += tabComponent.getPreferredSize().width;
        } else {
            Icon icon = getIconForTab(tabIndex);
            if (icon != null) width += icon.getIconWidth() + textIconGap;
            String title = tabPane.getTitleAt(tabIndex);
            if (title != null) width += metrics.stringWidth(title);
        }
        return width;
    }

    protected int calculateMaxTabWidth(int tabPlacement) {
        FontMetrics metrics = getFontMetrics();
        int result = 0;
        for (int i = 0; i < tabPane.getTabCount(); i++) {
            result = Math.max(calculateTabWidth(tabPlacement, i, metrics), result);
        }
        return result;
    }

    protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
        Insets areaInsets = getTabAreaInsets(tabPlacement);
        int overlay = getTabRunOverlay(tabPlacement);
        return horizRunCount > 0
                ? horizRunCount * (maxTabHeight - overlay) + overlay + areaInsets.top + areaInsets.bottom : 0;
    }

    protected int calculateTabAreaWidth(int tabPlacement, int vertRunCount, int maxTabWidth) {
        Insets areaInsets = getTabAreaInsets(tabPlacement);
        int overlay = getTabRunOverlay(tabPlacement);
        return vertRunCount > 0
                ? vertRunCount * (maxTabWidth - overlay) + overlay + areaInsets.left + areaInsets.right : 0;
    }

    protected int preferredTabAreaHeight(int tabPlacement, int width) {
        int tabCount = tabPane.getTabCount();
        if (tabCount == 0) return 0;
        FontMetrics metrics = getFontMetrics();
        int rows = 1, x = 0;
        for (int i = 0; i < tabCount; i++) {
            int tabWidth = calculateTabWidth(tabPlacement, i, metrics);
            if (!scrollable() && x != 0 && x + tabWidth > width) {
                rows++;
                x = 0;
            }
            x += tabWidth;
        }
        return calculateTabAreaHeight(tabPlacement, rows, calculateMaxTabHeight(tabPlacement));
    }

    protected int preferredTabAreaWidth(int tabPlacement, int height) {
        int tabCount = tabPane.getTabCount();
        if (tabCount == 0) return 0;
        int fontHeight = getFontMetrics().getHeight();
        int columns = 1, y = 0;
        for (int i = 0; i < tabCount; i++) {
            int tabHeight = calculateTabHeight(tabPlacement, i, fontHeight);
            if (!scrollable() && y != 0 && y + tabHeight > height) {
                columns++;
                y = 0;
            }
            y += tabHeight;
        }
        return calculateTabAreaWidth(tabPlacement, columns, calculateMaxTabWidth(tabPlacement));
    }

    protected int lastTabInRun(int tabCount, int run) {
        if (runCount == 1) return tabCount - 1;
        int nextRun = run == runCount - 1 ? 0 : run + 1;
        if (tabRuns[nextRun] == 0) return tabCount - 1;
        return tabRuns[nextRun] - 1;
    }

    protected int getRunForTab(int tabCount, int tabIndex) {
        for (int i = 0; i < runCount; i++) {
            int first = tabRuns[i];
            int last = lastTabInRun(tabCount, i);
            if (tabIndex >= first && tabIndex <= last) return i;
        }
        return 0;
    }

    protected void expandTabRunsArray() {
        int[] grown = new int[tabRuns.length + 10];
        System.arraycopy(tabRuns, 0, grown, 0, runCount);
        tabRuns = grown;
    }

    protected void rotateTabRuns(int tabPlacement, int selectedRun) {
        for (int i = 0; i < selectedRun; i++) {
            int save = tabRuns[0];
            for (int j = 1; j < runCount; j++) tabRuns[j - 1] = tabRuns[j];
            tabRuns[runCount - 1] = save;
        }
    }

    /** The JDK's balancing: move tabs into the last run until it is no shorter than ~1.25 tabs. */
    protected void normalizeTabRuns(int tabPlacement, int tabCount, int start, int max) {
        boolean verticalTabRuns = tabPlacement == LEFT || tabPlacement == RIGHT;
        int run = runCount - 1;
        boolean keepAdjusting = true;
        double weight = 1.25;
        while (keepAdjusting) {
            int last = lastTabInRun(tabCount, run);
            int prevLast = lastTabInRun(tabCount, run - 1);
            int end, prevLastLen;
            if (!verticalTabRuns) {
                end = rects[last].x + rects[last].width;
                prevLastLen = (int) (maxTabWidth * weight);
            } else {
                end = rects[last].y + rects[last].height;
                prevLastLen = (int) (maxTabHeight * weight * 2);
            }
            if (max - end > prevLastLen) {
                tabRuns[run] = prevLast;
                if (!verticalTabRuns) rects[prevLast].x = start;
                else rects[prevLast].y = start;
                for (int i = prevLast + 1; i <= last; i++) {
                    if (!verticalTabRuns) rects[i].x = rects[i - 1].x + rects[i - 1].width;
                    else rects[i].y = rects[i - 1].y + rects[i - 1].height;
                }
            } else if (run == runCount - 1) {
                keepAdjusting = false;
            }
            if (run - 1 > 0) {
                run -= 1;
            } else {
                run = runCount - 1;
                weight += .25;
            }
        }
    }

    /** Stretches the tabs of a run so the run fills the tab area, as the JDK does for wrapped runs. */
    protected void padTabRun(int tabPlacement, int start, int end, int max) {
        Rectangle lastRect = rects[end];
        if (tabPlacement == TOP || tabPlacement == BOTTOM) {
            int runWidth = lastRect.x + lastRect.width - rects[start].x;
            int deltaWidth = max - (lastRect.x + lastRect.width);
            float factor = (float) deltaWidth / (float) runWidth;
            for (int j = start; j <= end; j++) {
                Rectangle pastRect = rects[j];
                if (j > start) pastRect.x = rects[j - 1].x + rects[j - 1].width;
                pastRect.width += Math.round((float) pastRect.width * factor);
            }
            lastRect.width = max - lastRect.x;
        } else {
            int runHeight = lastRect.y + lastRect.height - rects[start].y;
            int deltaHeight = max - (lastRect.y + lastRect.height);
            float factor = (float) deltaHeight / (float) runHeight;
            for (int j = start; j <= end; j++) {
                Rectangle pastRect = rects[j];
                if (j > start) pastRect.y = rects[j - 1].y + rects[j - 1].height;
                pastRect.height += Math.round((float) pastRect.height * factor);
            }
            lastRect.height = max - lastRect.y;
        }
    }

    /** Grows the selected tab by selectedTabPadInsets; in WRAP layout it stays inside the pane's insets. */
    protected void padSelectedTab(int tabPlacement, int selectedIndex) {
        if (selectedIndex < 0 || selectedIndex >= rects.length) return;
        Rectangle selRect = rects[selectedIndex];
        Insets padInsets = getSelectedTabPadInsets(tabPlacement);
        selRect.x -= padInsets.left;
        selRect.width += padInsets.left + padInsets.right;
        selRect.y -= padInsets.top;
        selRect.height += padInsets.top + padInsets.bottom;
        if (!scrollable()) {
            Dimension size = tabPane.getSize();
            Insets insets = tabPane.getInsets();
            if (tabPlacement == LEFT || tabPlacement == RIGHT) {
                int top = insets.top - selRect.y;
                if (top > 0) {
                    selRect.y += top;
                    selRect.height -= top;
                }
                int bottom = selRect.y + selRect.height + insets.bottom - size.height;
                if (bottom > 0) selRect.height -= bottom;
            } else {
                int left = insets.left - selRect.x;
                if (left > 0) {
                    selRect.x += left;
                    selRect.width -= left;
                }
                int right = selRect.x + selRect.width + insets.right - size.width;
                if (right > 0) selRect.width -= right;
            }
        }
    }

    private void ensureRects(int tabCount) {
        if (rects.length != tabCount) {
            Rectangle[] grown = new Rectangle[tabCount];
            for (int i = 0; i < tabCount; i++) grown[i] = i < rects.length ? rects[i] : new Rectangle();
            rects = grown;
        }
    }

    /** Recomputes {@link #rects}, the runs and the selected run from the pane's current state. */
    protected void calculateLayoutInfo() {
        int tabCount = tabPane.getTabCount();
        ensureRects(tabCount);
        calculateTabRects(tabPane.getTabPlacement(), tabCount);
    }

    protected void calculateTabRects(int tabPlacement, int tabCount) {
        FontMetrics metrics = getFontMetrics();
        Dimension size = tabPane.getSize();
        Insets insets = tabPane.getInsets();
        Insets areaInsets = getTabAreaInsets(tabPlacement);
        int fontHeight = metrics.getHeight();
        int selectedIndex = tabPane.getSelectedIndex();
        boolean verticalTabRuns = tabPlacement == LEFT || tabPlacement == RIGHT;
        boolean wrap = !scrollable();
        int returnAt, x, y;
        switch (tabPlacement) {
            case LEFT:
                maxTabWidth = calculateMaxTabWidth(tabPlacement);
                x = insets.left + areaInsets.left;
                y = insets.top + areaInsets.top;
                returnAt = size.height - (insets.bottom + areaInsets.bottom);
                break;
            case RIGHT:
                maxTabWidth = calculateMaxTabWidth(tabPlacement);
                x = size.width - insets.right - areaInsets.right - maxTabWidth;
                y = insets.top + areaInsets.top;
                returnAt = size.height - (insets.bottom + areaInsets.bottom);
                break;
            case BOTTOM:
                maxTabHeight = calculateMaxTabHeight(tabPlacement);
                x = insets.left + areaInsets.left;
                y = size.height - insets.bottom - areaInsets.bottom - maxTabHeight;
                returnAt = size.width - (insets.right + areaInsets.right);
                break;
            case TOP:
            default:
                maxTabHeight = calculateMaxTabHeight(tabPlacement);
                x = insets.left + areaInsets.left;
                y = insets.top + areaInsets.top;
                returnAt = size.width - (insets.right + areaInsets.right);
                break;
        }
        int overlay = getTabRunOverlay(tabPlacement);
        runCount = 0;
        selectedRun = -1;
        scrollOverflow = false;
        if (tabCount == 0) return;

        for (int i = 0; i < tabCount; i++) {
            Rectangle rect = rects[i];
            if (!verticalTabRuns) {
                if (i > 0) {
                    rect.x = rects[i - 1].x + rects[i - 1].width;
                } else {
                    tabRuns[0] = 0;
                    runCount = 1;
                    maxTabWidth = 0;
                    rect.x = x;
                }
                rect.width = calculateTabWidth(tabPlacement, i, metrics);
                maxTabWidth = Math.max(maxTabWidth, rect.width);
                if (wrap && rect.x != x && rect.x + rect.width > returnAt) {
                    if (runCount > tabRuns.length - 1) expandTabRunsArray();
                    tabRuns[runCount] = i;
                    runCount++;
                    rect.x = x;
                }
                rect.y = y;
                rect.height = maxTabHeight;
            } else {
                if (i > 0) {
                    rect.y = rects[i - 1].y + rects[i - 1].height;
                } else {
                    tabRuns[0] = 0;
                    runCount = 1;
                    maxTabHeight = 0;
                    rect.y = y;
                }
                rect.height = calculateTabHeight(tabPlacement, i, fontHeight);
                maxTabHeight = Math.max(maxTabHeight, rect.height);
                if (wrap && rect.y != y && rect.y + rect.height > returnAt) {
                    if (runCount > tabRuns.length - 1) expandTabRunsArray();
                    tabRuns[runCount] = i;
                    runCount++;
                    rect.y = y;
                }
                rect.x = x;
                rect.width = maxTabWidth;
            }
            if (i == selectedIndex) selectedRun = runCount - 1;
        }

        if (runCount > 1) {
            normalizeTabRuns(tabPlacement, tabCount, verticalTabRuns ? y : x, returnAt);
            selectedRun = getRunForTab(tabCount, selectedIndex);
            if (shouldRotateTabRuns(tabPlacement)) rotateTabRuns(tabPlacement, selectedRun);
        }

        for (int i = runCount - 1; i >= 0; i--) {
            int start = tabRuns[i];
            int next = tabRuns[i == runCount - 1 ? 0 : i + 1];
            int end = next != 0 ? next - 1 : tabCount - 1;
            if (!verticalTabRuns) {
                for (int j = start; j <= end; j++) {
                    rects[j].y = y;
                    rects[j].x += getTabRunIndent(tabPlacement, i);
                }
                if (shouldPadTabRun(tabPlacement, i)) padTabRun(tabPlacement, start, end, returnAt);
                if (tabPlacement == BOTTOM) y -= maxTabHeight - overlay;
                else y += maxTabHeight - overlay;
            } else {
                for (int j = start; j <= end; j++) {
                    rects[j].x = x;
                    rects[j].y += getTabRunIndent(tabPlacement, i);
                }
                if (shouldPadTabRun(tabPlacement, i)) padTabRun(tabPlacement, start, end, returnAt);
                if (tabPlacement == RIGHT) x -= maxTabWidth - overlay;
                else x += maxTabWidth - overlay;
            }
        }

        if (!wrap) scrollRun(tabPlacement, tabCount, verticalTabRuns ? returnAt - (insets.top + areaInsets.top) : returnAt - (insets.left + areaInsets.left));
        padSelectedTab(tabPlacement, selectedIndex);

        if (!verticalTabRuns && !tabPane.getComponentOrientation().isLeftToRight()) {
            int rightMargin = size.width - (insets.right + areaInsets.right);
            for (int i = 0; i < tabCount; i++) rects[i].x = rightMargin - rects[i].x - rects[i].width;
        }
    }

    /** SCROLL_TAB_LAYOUT: clamp the offset, shift the run and leave room for the two arrow cells. */
    private void scrollRun(int tabPlacement, int tabCount, int available) {
        boolean vertical = tabPlacement == LEFT || tabPlacement == RIGHT;
        Rectangle last = rects[tabCount - 1], first = rects[0];
        int runLength = vertical ? last.y + last.height - first.y : last.x + last.width - first.x;
        scrollOverflow = runLength > available;
        int visible = scrollOverflow ? Math.max(0, available - 2 * SCROLL_CELL) : available;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, runLength - visible)));
        if (!scrollOverflow) scrollOffset = 0;
        for (int i = 0; i < tabCount; i++) {
            if (vertical) rects[i].y -= scrollOffset;
            else rects[i].x -= scrollOffset;
        }
    }

    /** The visible part of the tab area: where tabs may be hit or painted. */
    private Rectangle tabViewport() {
        int placement = tabPane.getTabPlacement();
        Insets insets = tabPane.getInsets();
        Insets areaInsets = getTabAreaInsets(placement);
        Dimension size = tabPane.getSize();
        Rectangle area = tabAreaBounds();
        if (scrollable() && scrollOverflow) {
            if (placement == LEFT || placement == RIGHT) {
                area.y = insets.top + areaInsets.top;
                area.height = Math.max(0, size.height - insets.bottom - areaInsets.bottom - 2 * SCROLL_CELL - area.y);
            } else {
                area.x = insets.left + areaInsets.left;
                area.width = Math.max(0, size.width - insets.right - areaInsets.right - 2 * SCROLL_CELL - area.x);
            }
        }
        return area;
    }

    /** The band of the pane the tab runs occupy, tab area insets included. */
    private Rectangle tabAreaBounds() {
        int placement = tabPane.getTabPlacement();
        Insets insets = tabPane.getInsets();
        Dimension size = tabPane.getSize();
        int w = size.width - insets.left - insets.right, h = size.height - insets.top - insets.bottom;
        switch (placement) {
            case LEFT: {
                int extent = calculateTabAreaWidth(placement, runCount, maxTabWidth);
                return new Rectangle(insets.left, insets.top, extent, h);
            }
            case RIGHT: {
                int extent = calculateTabAreaWidth(placement, runCount, maxTabWidth);
                return new Rectangle(size.width - insets.right - extent, insets.top, extent, h);
            }
            case BOTTOM: {
                int extent = calculateTabAreaHeight(placement, runCount, maxTabHeight);
                return new Rectangle(insets.left, size.height - insets.bottom - extent, w, extent);
            }
            case TOP:
            default: {
                int extent = calculateTabAreaHeight(placement, runCount, maxTabHeight);
                return new Rectangle(insets.left, insets.top, w, extent);
            }
        }
    }

    private boolean inTabArea(int x, int y) { return tabPane.getTabCount() > 0 && tabAreaBounds().contains(x, y); }

    /** -1 or +1 when (x, y) is on a scroll arrow cell of an overflowing SCROLL_TAB_LAYOUT run. */
    private int scrollCellAt(int x, int y) {
        if (paneStrip() || !scrollable()) return 0;
        ensureCurrentLayout();
        if (!scrollOverflow || !inTabArea(x, y)) return 0;
        Rectangle view = tabViewport();
        boolean vertical = tabPane.getTabPlacement() == LEFT || tabPane.getTabPlacement() == RIGHT;
        int along = vertical ? y : x, start = vertical ? view.y + view.height : view.x + view.width;
        if (along < start) return 0;
        return along < start + SCROLL_CELL ? -1 : along < start + 2 * SCROLL_CELL ? 1 : 0;
    }

    /** Scrolls the run by one tab's worth towards its end (positive) or its start. */
    private void scrollBy(int direction) {
        int step = tabPane.getTabPlacement() == LEFT || tabPane.getTabPlacement() == RIGHT ? maxTabHeight : maxTabWidth;
        scrollOffset = Math.max(0, scrollOffset + direction * Math.max(1, step));
        tabPane.revalidate();
        tabPane.repaint();
    }

    /** The JDK keeps the rectangles current whenever geometry is asked for outside a layout pass. */
    private void ensureCurrentLayout() {
        if (!tabPane.isValid()) tabPane.validate();
        if (!tabPane.isValid() || rects.length != tabPane.getTabCount()) calculateLayoutInfo();
    }

    @Override public int tabForCoordinate(JTabbedPane pane, int x, int y) {
        if (paneStrip()) return pane.indexAtLocation(x, y);
        ensureCurrentLayout();
        if (scrollable() && !tabViewport().contains(x, y)) return -1;
        for (int i = 0; i < pane.getTabCount() && i < rects.length; i++) {
            if (rects[i].contains(x, y)) return i;
        }
        return -1;
    }

    @Override public Rectangle getTabBounds(JTabbedPane pane, int index) {
        if (paneStrip()) return pane.getBoundsAt(index);
        ensureCurrentLayout();
        return getTabBounds(index, new Rectangle());
    }

    @Override public int getTabRunCount(JTabbedPane pane) {
        if (paneStrip()) return pane.getTabRunCount();
        ensureCurrentLayout();
        return runCount;
    }

    /** Copies the bounds of tab {@code tabIndex} into {@code dest} and returns it. */
    protected Rectangle getTabBounds(int tabIndex, Rectangle dest) {
        if (paneStrip()) {
            dest.setBounds(tabPane.getBoundsAt(tabIndex));
            return dest;
        }
        dest.setBounds(rects[tabIndex]);
        return dest;
    }

    /** The JDK's content area: inside the pane's insets, past the tab area and the content border insets. */
    protected Rectangle getContentBounds() {
        int placement = tabPane.getTabPlacement();
        Insets insets = tabPane.getInsets();
        Insets content = getContentBorderInsets(placement);
        Rectangle area = tabAreaBounds();
        boolean vertical = placement == LEFT || placement == RIGHT;
        int tabWidth = vertical ? area.width : 0, tabHeight = vertical ? 0 : area.height;
        int cx = insets.left + content.left + (placement == LEFT ? tabWidth : 0);
        int cy = insets.top + content.top + (placement == TOP ? tabHeight : 0);
        int cw = tabPane.getWidth() - tabWidth - insets.left - insets.right - content.left - content.right;
        int ch = tabPane.getHeight() - tabHeight - insets.top - insets.bottom - content.top - content.bottom;
        return new Rectangle(cx, cy, Math.max(0, cw), Math.max(0, ch));
    }

    // --- painting -----------------------------------------------------------------------------

    @Override public void paint(Graphics g, JComponent c) {
        if (paneStrip() || tabPane.getTabCount() == 0) return;
        ensureCurrentLayout();
        int placement = tabPane.getTabPlacement();
        int selectedIndex = tabPane.getSelectedIndex();
        paintContentBorder(g, placement, selectedIndex);
        paintTabArea(g, placement, selectedIndex);
    }

    protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
        int tabCount = tabPane.getTabCount();
        Graphics clipped = g.create();
        try {
            Rectangle view = tabViewport();
            clipped.clipRect(view.x - 2, view.y - 2, view.width + 4, view.height + 4);
            Rectangle clip = clipped.getClipBounds();
            for (int i = runCount - 1; i >= 0; i--) {
                int start = tabRuns[i];
                int next = tabRuns[i == runCount - 1 ? 0 : i + 1];
                int end = next != 0 ? next - 1 : tabCount - 1;
                for (int j = start; j <= end; j++) {
                    if (j != selectedIndex && (clip == null || rects[j].intersects(clip))) paintTab(clipped, tabPlacement, j);
                }
            }
            if (selectedIndex >= 0 && selectedIndex < rects.length) paintTab(clipped, tabPlacement, selectedIndex);
        } finally {
            clipped.dispose();
        }
        if (scrollable() && scrollOverflow) paintScrollCells(g, tabPlacement);
    }

    protected void paintTab(Graphics g, int tabPlacement, int tabIndex) {
        Rectangle r = rects[tabIndex];
        boolean selected = tabPane.getSelectedIndex() == tabIndex;
        paintTabBackground(g, tabPlacement, tabIndex, r.x, r.y, r.width, r.height, selected);
        paintTabBorder(g, tabPlacement, tabIndex, r.x, r.y, r.width, r.height, selected);
        if (tabPane.getTabComponentAt(tabIndex) != null) return;
        FontMetrics metrics = getFontMetrics();
        Icon icon = getIconForTab(tabIndex);
        String title = tabPane.getTitleAt(tabIndex);
        int textWidth = title == null ? 0 : metrics.stringWidth(title);
        int iconWidth = icon == null ? 0 : icon.getIconWidth() + (textWidth > 0 ? textIconGap : 0);
        int shift = selected ? -1 : 1;
        int x = r.x + (r.width - iconWidth - textWidth) / 2 + (tabPlacement == LEFT || tabPlacement == RIGHT ? shift : 0);
        int centerY = r.y + r.height / 2 + (tabPlacement == TOP ? shift : tabPlacement == BOTTOM ? -shift : 0);
        if (icon != null) icon.paintIcon(tabPane, g, x, centerY - icon.getIconHeight() / 2);
        if (title != null && !title.isEmpty()) paintText(g, tabPlacement, metrics.getFont(), metrics, tabIndex, title,
                new Rectangle(x + iconWidth, centerY - metrics.getHeight() / 2, textWidth, metrics.getHeight()), selected);
    }

    protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex,
                             String title, Rectangle textRect, boolean isSelected) {
        g.setFont(font);
        boolean enabled = tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex);
        g.setColor(enabled ? tabPane.getForegroundAt(tabIndex) : shadow);
        int baseline = textRect.y + metrics.getAscent();
        g.drawString(title, textRect.x, baseline);
        int mnemonic = tabPane.getDisplayedMnemonicIndexAt(tabIndex);
        if (enabled && mnemonic >= 0 && mnemonic < title.length()) {
            int ux = textRect.x + metrics.stringWidth(title.substring(0, mnemonic));
            g.fillRect(ux, baseline + 1, metrics.charWidth(title.charAt(mnemonic)), 1);
        }
    }

    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                      int x, int y, int w, int h, boolean isSelected) {
        g.setColor(!isSelected || selectedColor == null ? tabPane.getBackgroundAt(tabIndex) : selectedColor);
        switch (tabPlacement) {
            case LEFT: g.fillRect(x + 1, y + 1, w - 1, h - 3); break;
            case RIGHT: g.fillRect(x, y + 1, w - 2, h - 3); break;
            case BOTTOM: g.fillRect(x + 1, y, w - 3, h - 1); break;
            case TOP:
            default: g.fillRect(x + 1, y + 1, w - 3, h - 1);
        }
    }

    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                  int x, int y, int w, int h, boolean isSelected) {
        g.setColor(lightHighlight);
        switch (tabPlacement) {
            case LEFT:
                g.drawLine(x + 1, y + h - 2, x + 1, y + h - 2);
                g.drawLine(x, y + 2, x, y + h - 3);
                g.drawLine(x + 1, y + 1, x + 1, y + 1);
                g.drawLine(x + 2, y, x + w - 1, y);
                g.setColor(shadow);
                g.drawLine(x + 2, y + h - 2, x + w - 1, y + h - 2);
                g.setColor(darkShadow);
                g.drawLine(x + 2, y + h - 1, x + w - 1, y + h - 1);
                break;
            case BOTTOM:
                g.drawLine(x, y, x, y + h - 3);
                g.drawLine(x + 1, y + h - 2, x + 1, y + h - 2);
                g.setColor(shadow);
                g.drawLine(x + 2, y + h - 2, x + w - 3, y + h - 2);
                g.drawLine(x + w - 2, y, x + w - 2, y + h - 3);
                g.setColor(darkShadow);
                g.drawLine(x + 2, y + h - 1, x + w - 3, y + h - 1);
                g.drawLine(x + w - 1, y, x + w - 1, y + h - 3);
                break;
            case TOP:
            default:
                g.drawLine(x, y + 2, x, y + h - 1);
                g.drawLine(x + 1, y + 1, x + 1, y + 1);
                g.drawLine(x + 2, y, x + w - 3, y);
                g.setColor(shadow);
                g.drawLine(x + w - 2, y + 2, x + w - 2, y + h - 1);
                g.setColor(darkShadow);
                g.drawLine(x + w - 1, y + 2, x + w - 1, y + h - 1);
                g.drawLine(x + w - 2, y + 1, x + w - 2, y + 1);
        }
    }

    /** The content border, with the gap the selected tab opens into it on the tab side. */
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        Rectangle content = getContentBounds();
        Insets ci = getContentBorderInsets(tabPlacement);
        int x = content.x - ci.left, y = content.y - ci.top;
        int w = content.width + ci.left + ci.right, h = content.height + ci.top + ci.bottom;
        if (w <= 0 || h <= 0) return;
        Rectangle selected = selectedIndex >= 0 && selectedIndex < rects.length ? rects[selectedIndex] : null;
        g.setColor(lightHighlight);
        if (tabPlacement == TOP && selected != null) {
            g.drawLine(x, y, selected.x - 1, y);
            g.drawLine(selected.x + selected.width, y, x + w - 2, y);
        } else {
            g.drawLine(x, y, x + w - 2, y);
        }
        if (tabPlacement == LEFT && selected != null) {
            g.drawLine(x, y, x, selected.y - 1);
            g.drawLine(x, selected.y + selected.height, x, y + h - 2);
        } else {
            g.drawLine(x, y, x, y + h - 2);
        }
        g.setColor(darkShadow);
        if (tabPlacement == BOTTOM && selected != null) {
            g.drawLine(x, y + h - 1, selected.x - 1, y + h - 1);
            g.drawLine(selected.x + selected.width, y + h - 1, x + w - 1, y + h - 1);
        } else {
            g.drawLine(x, y + h - 1, x + w - 1, y + h - 1);
        }
        g.drawLine(x + w - 1, y, x + w - 1, y + h - 1);
    }

    private void paintScrollCells(Graphics g, int tabPlacement) {
        Rectangle view = tabViewport();
        boolean vertical = tabPlacement == LEFT || tabPlacement == RIGHT;
        for (int i = 0; i < 2; i++) {
            int cx = vertical ? view.x : view.x + view.width + i * SCROLL_CELL;
            int cy = vertical ? view.y + view.height + i * SCROLL_CELL : view.y;
            int cw = vertical ? view.width : SCROLL_CELL, ch = vertical ? SCROLL_CELL : view.height;
            g.setColor(tabPane.getBackground());
            g.fillRect(cx, cy, cw, ch);
            g.setColor(darkShadow);
            g.drawRect(cx, cy, cw - 1, ch - 1);
            int mx = cx + cw / 2, my = cy + ch / 2;
            int[] xs, ys;
            if (vertical) {
                xs = new int[] {mx - 4, mx + 4, mx};
                ys = i == 0 ? new int[] {my + 2, my + 2, my - 2} : new int[] {my - 2, my - 2, my + 2};
            } else {
                xs = i == 0 ? new int[] {mx + 2, mx + 2, mx - 2} : new int[] {mx - 2, mx - 2, mx + 2};
                ys = new int[] {my - 4, my + 4, my};
            }
            g.fillPolygon(xs, ys, 3);
        }
    }

    // --- layout -------------------------------------------------------------------------------

    /**
     * The JDK's layout manager: the pages fill the content area and only the selected one shows;
     * tab components sit inside their tab's rectangle within the tab insets.
     */
    public class TabbedPaneLayout implements LayoutManager {
        public void addLayoutComponent(String name, Component comp) {}

        public void removeLayoutComponent(Component comp) {}

        public Dimension preferredLayoutSize(Container parent) { return calculateSize(false); }

        public Dimension minimumLayoutSize(Container parent) { return calculateSize(true); }

        protected Dimension calculateSize(boolean minimum) {
            int tabPlacement = tabPane.getTabPlacement();
            Insets insets = tabPane.getInsets();
            Insets contentInsets = getContentBorderInsets(tabPlacement);
            Insets areaInsets = getTabAreaInsets(tabPlacement);
            int width = 0, height = 0;
            for (int i = 0; i < tabPane.getTabCount(); i++) {
                Component component = tabPane.getComponentAt(i);
                if (component == null) continue;
                Dimension size = minimum ? component.getMinimumSize() : component.getPreferredSize();
                if (size != null) {
                    height = Math.max(size.height, height);
                    width = Math.max(size.width, width);
                }
            }
            if (tabPlacement == LEFT || tabPlacement == RIGHT) {
                height = Math.max(height, calculateMaxTabHeight(tabPlacement));
                width += preferredTabAreaWidth(tabPlacement, height - areaInsets.top - areaInsets.bottom);
            } else {
                width = Math.max(width, calculateMaxTabWidth(tabPlacement));
                height += preferredTabAreaHeight(tabPlacement, width - areaInsets.left - areaInsets.right);
            }
            return new Dimension(width + insets.left + insets.right + contentInsets.left + contentInsets.right,
                    height + insets.bottom + insets.top + contentInsets.top + contentInsets.bottom);
        }

        public void layoutContainer(Container parent) {
            if (paneStrip()) return;
            calculateLayoutInfo();
            Rectangle content = getContentBounds();
            int selected = tabPane.getSelectedIndex();
            for (int i = 0; i < tabPane.getTabCount(); i++) {
                Component page = tabPane.getComponentAt(i);
                if (page != null) {
                    page.setBounds(content);
                    page.setVisible(i == selected);
                }
                Component header = tabPane.getTabComponentAt(i);
                if (header != null) {
                    Rectangle r = rects[i];
                    Insets in = getTabInsets(tabPane.getTabPlacement(), i);
                    Dimension preferred = header.getPreferredSize();
                    int hx = r.x + in.left + (r.width - in.left - in.right - preferred.width) / 2;
                    int hy = r.y + in.top + (r.height - in.top - in.bottom - preferred.height) / 2;
                    header.setBounds(hx, hy, preferred.width, preferred.height);
                    header.setVisible(tabViewport().intersects(r));
                }
            }
        }

        public void calculateLayoutInfo() { BasicTabbedPaneUI.this.calculateLayoutInfo(); }
    }
}
