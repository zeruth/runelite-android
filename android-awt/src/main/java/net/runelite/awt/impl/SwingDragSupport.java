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
package net.runelite.awt.impl;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.TransferHandler;
import javax.swing.text.JTextComponent;

/**
 * What the desktop look and feel does for {@code setDragEnabled(true)}: a press
 * on the dragged content (a selected list cell, the text selection) that moves
 * past the drag threshold calls {@link TransferHandler#exportAsDrag}. The gesture
 * is {@link TouchDragGestureRecognizer}, so a finger must long-press first.
 *
 * <p>Also carries the {@code JTextComponent} drop-location hooks, which
 * {@code TransferHandler} cannot reach across packages (desktop uses
 * {@code sun.swing.SwingAccessor} for the same reason).
 */
public final class SwingDragSupport {
    private SwingDragSupport() {}

    /** Implemented by {@code JTextComponent}, registered from its static initializer. */
    public interface TextDrop {
        TransferHandler.DropLocation dropLocationForPoint(JTextComponent c, Point p);
        Object setDropLocation(JTextComponent c, TransferHandler.DropLocation location, Object state, boolean forDrop);
    }

    private static volatile TextDrop textDrop;

    public static void setTextDrop(TextDrop hooks) { textDrop = hooks; }

    public static TransferHandler.DropLocation textDropLocationForPoint(JTextComponent c, Point p) {
        TextDrop hooks = textDrop;
        return hooks == null ? null : hooks.dropLocationForPoint(c, p);
    }

    public static Object setTextDropLocation(JTextComponent c, TransferHandler.DropLocation location,
                                             Object state, boolean forDrop) {
        TextDrop hooks = textDrop;
        return hooks == null ? null : hooks.setDropLocation(c, location, state, forDrop);
    }

    private static final int BUTTONS = InputEvent.BUTTON1_DOWN_MASK
            | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;

    private static final DragGestureListener EXPORT = new DragGestureListener() {
        public void dragGestureRecognized(DragGestureEvent dge) {
            if (!(dge.getComponent() instanceof JComponent)) return;
            JComponent c = (JComponent) dge.getComponent();
            TransferHandler th = c.getTransferHandler();
            if (th == null || !isDragPossible(c, dge.getDragOrigin())) return;
            Object[] events = dge.toArray();
            if (events.length == 0 || !(events[events.length - 1] instanceof MouseEvent)) return;
            MouseEvent last = (MouseEvent) events[events.length - 1];
            int action = DragDropHost.dropActionFor(last.getModifiersEx() & ~BUTTONS, th.getSourceActions(c));
            if (action == DnDConstants.ACTION_NONE) return;
            th.exportAsDrag(c, last, action);
        }
    };

    /**
     * Installs or removes the drag gesture; returns the recognizer to keep (null
     * once disabled). Call with the value the previous call returned.
     */
    public static DragGestureRecognizer setDragEnabled(JComponent c, DragGestureRecognizer current, boolean enabled) {
        if (enabled) {
            if (current != null) return current;
            return new TouchDragGestureRecognizer(DragSource.getDefaultDragSource(), c,
                    DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK, EXPORT);
        }
        if (current != null) current.setComponent(null);
        return null;
    }

    /** The desktop UIs start a drag only from the content being dragged. */
    public static boolean isDragPossible(JComponent c, Point p) {
        if (p == null) return false;
        if (c instanceof JList) {
            JList<?> list = (JList<?>) c;
            int index = list.locationToIndex(p);
            if (index < 0) return false;
            Rectangle cell = list.getCellBounds(index, index);
            return cell != null && cell.contains(p) && list.isSelectedIndex(index);
        }
        if (c instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) c;
            int start = text.getSelectionStart(), end = text.getSelectionEnd();
            if (start == end) return false;
            int pos = text.viewToModel(p);
            return pos >= start && pos < end;
        }
        return true;
    }
}
