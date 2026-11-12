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
package sun.swing;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FocusTraversalPolicy;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.Modifier;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * The part of the JDK's Swing-internal helper class that the ported Basic
 * delegates call. Members arrive with the delegates that need them; the
 * semantics are the JDK 8 ones.
 */
public class SwingUtilities2 {

    /**
     * Client property key for the number of clicks a text component's caret
     * should skip, set when a click that started an edit is forwarded to the
     * editor.
     */
    public static final Object SKIP_CLICK_COUNT = "skipClickCount";

    private SwingUtilities2() {
    }

    /**
     * Where a point lies within a rectangle along one axis.
     */
    public static enum Section {

        /** The leading section (top, or left in left-to-right). */
        LEADING,

        /** The middle section (only when three sections are asked for). */
        MIDDLE,

        /** The trailing section (bottom, or right in left-to-right). */
        TRAILING
    }

    private static Section liesIn(Rectangle rect, Point p, boolean horizontal,
                                  boolean ltr, boolean three) {
        int p0;
        int pComp;
        int length;
        boolean forward;

        if (horizontal) {
            p0 = rect.x;
            pComp = p.x;
            length = rect.width;
            forward = ltr;
        } else {
            p0 = rect.y;
            pComp = p.y;
            length = rect.height;
            forward = true;
        }

        if (three) {
            int boundary = (length >= 30) ? 10 : length / 3;

            if (pComp < p0 + boundary) {
                return forward ? Section.LEADING : Section.TRAILING;
            } else if (pComp >= p0 + length - boundary) {
                return forward ? Section.TRAILING : Section.LEADING;
            }

            return Section.MIDDLE;
        } else {
            int middle = p0 + length / 2;
            if (forward) {
                return pComp >= middle ? Section.TRAILING : Section.LEADING;
            } else {
                return pComp < middle ? Section.TRAILING : Section.LEADING;
            }
        }
    }

    /**
     * The horizontal section of {@code rect} that {@code p} lies in: two
     * halves, or with {@code three} a leading and trailing band of up to ten
     * pixels around a middle; leading is on the right when not {@code ltr}.
     */
    public static Section liesInHorizontal(Rectangle rect, Point p,
                                           boolean ltr, boolean three) {
        return liesIn(rect, p, true, ltr, three);
    }

    /**
     * The vertical section of {@code rect} that {@code p} lies in, as
     * {@link #liesInHorizontal} with the top as leading.
     */
    public static Section liesInVertical(Rectangle rect, Point p,
                                         boolean three) {
        return liesIn(rect, p, false, false, three);
    }

    /**
     * The path of a tree model event, or the root path when the event has
     * none (a structure change of the whole tree).
     */
    public static TreePath getTreePath(TreeModelEvent event, TreeModel model) {
        TreePath path = event.getTreePath();
        if ((path == null) && (model != null)) {
            Object root = model.getRoot();
            if (root != null) {
                path = new TreePath(root);
            }
        }
        return path;
    }

    /**
     * Marks a text editor so its caret skips the clicks that started the
     * edit. The JDK only sets this on a {@code DefaultCaret}; the shadow sets
     * it on any text component and its caret ignores it, which is what the
     * JDK does for a custom caret.
     */
    public static void setSkipClickCount(Component comp, int count) {
        if (comp instanceof JTextComponent) {
            ((JComponent) comp).putClientProperty(SKIP_CLICK_COUNT, count);
        }
    }

    /**
     * Whether a mouse event should be ignored by a list or tree delegate:
     * no component, a disabled one, not the left button, or already consumed.
     */
    public static boolean shouldIgnore(MouseEvent me, JComponent c) {
        return c == null || !c.isEnabled()
                         || !SwingUtilities.isLeftMouseButton(me)
                         || me.isConsumed();
    }

    /**
     * Requests focus for {@code c} if it does not have it. The JDK also
     * checks {@code isRequestFocusEnabled}, which the shadow JComponent does
     * not track (it is always true there).
     */
    public static void adjustFocus(JComponent c) {
        if (!c.hasFocus()) {
            c.requestFocus();
        }
    }

    /**
     * Requests focus for {@code component}, or for the component inside it
     * that its focus cycle would choose when it is a container; returns the
     * component asked, or null when none was focusable.
     */
    public static Component compositeRequestFocus(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            if (container.isFocusCycleRoot()) {
                FocusTraversalPolicy policy = container.getFocusTraversalPolicy();
                Component comp = policy.getDefaultComponent(container);
                if (comp != null) {
                    comp.requestFocus();
                    return comp;
                }
            }
            Container rootAncestor = container.getFocusCycleRootAncestor();
            if (rootAncestor != null) {
                FocusTraversalPolicy policy = rootAncestor.getFocusTraversalPolicy();
                Component comp = policy.getComponentAfter(rootAncestor, container);
                if (comp != null && SwingUtilities.isDescendingFrom(comp, container)) {
                    comp.requestFocus();
                    return comp;
                }
            }
        }
        if (component.isFocusable()) {
            component.requestFocus();
            return component;
        }
        return null;
    }

    /**
     * Whether {@code p} lies outside the preferred size of the renderer of
     * the cell at {@code row} (column 0 in the model only); a file-list
     * table uses it to ignore presses beside the file name.
     */
    public static boolean pointOutsidePrefSize(JTable table, int row, int column, Point p) {
        if (table.convertColumnIndexToModel(column) != 0 || row == -1) {
            return true;
        }
        TableCellRenderer tcr = table.getCellRenderer(row, column);
        Object value = table.getValueAt(row, column);
        Component cell = tcr.getTableCellRendererComponent(table, value, false,
                false, row, column);
        Dimension itemSize = cell.getPreferredSize();
        Rectangle cellBounds = table.getCellRect(row, column, false);
        cellBounds.width = itemSize.width;
        cellBounds.height = itemSize.height;

        return p.x > cellBounds.x + cellBounds.width ||
                p.y > cellBounds.y + cellBounds.height;
    }

    /**
     * Sets the lead and anchor of {@code model} without changing which
     * indices are selected.
     */
    public static void setLeadAnchorWithoutSelection(ListSelectionModel model,
                                                     int lead, int anchor) {
        if (anchor == -1) {
            anchor = lead;
        }
        if (lead == -1) {
            model.setAnchorSelectionIndex(-1);
            model.setLeadSelectionIndex(-1);
        } else {
            if (model.isSelectedIndex(lead)) {
                model.addSelectionInterval(lead, lead);
            } else {
                model.removeSelectionInterval(lead, lead);
            }
            model.setAnchorSelectionIndex(anchor);
        }
    }

    /**
     * Throws {@code SecurityException} when a security manager is installed
     * and {@code modifiers} are not public.
     */
    public static void checkAccess(int modifiers) {
        if (System.getSecurityManager() != null
                && !Modifier.isPublic(modifiers)) {
            throw new SecurityException("Resource is not accessible");
        }
    }

    /**
     * The model index of the column at {@code viewColumnIndex} of
     * {@code cm}, or {@code viewColumnIndex} when it is negative.
     */
    public static int convertColumnIndexToModel(TableColumnModel cm,
                                                int viewColumnIndex) {
        if (viewColumnIndex < 0) {
            return viewColumnIndex;
        }
        return cm.getColumn(viewColumnIndex).getModelIndex();
    }

    /**
     * The view index in {@code cm} of the column with model index
     * {@code modelColumnIndex}, -1 when it is not shown, or
     * {@code modelColumnIndex} when it is negative.
     */
    public static int convertColumnIndexToView(TableColumnModel cm,
                                               int modelColumnIndex) {
        if (modelColumnIndex < 0) {
            return modelColumnIndex;
        }
        for (int column = 0; column < cm.getColumnCount(); column++) {
            if (cm.getColumn(column).getModelIndex() == modelColumnIndex) {
                return column;
            }
        }
        return -1;
    }
}
