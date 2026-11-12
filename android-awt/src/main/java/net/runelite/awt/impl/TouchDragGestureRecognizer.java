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

import java.awt.Component;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.MouseDragGestureRecognizer;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * The platform recognizer {@code DragSource.createDefaultDragGestureRecognizer} returns,
 * after the desktop ones: a button press followed by motion past
 * {@link DragSource#getDragThreshold()}. On a touchscreen the press must also have been
 * a long press ({@link DragDropHost#isLongPressed()}), since a plain swipe scrolls the panel.
 */
public class TouchDragGestureRecognizer extends MouseDragGestureRecognizer {
    private static final long serialVersionUID = 1L;
    private static final int BUTTONS = InputEvent.BUTTON1_DOWN_MASK
            | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;

    private transient Point origin;

    public TouchDragGestureRecognizer(DragSource ds, Component c, int act, DragGestureListener dgl) {
        super(ds, c, act, dgl);
    }

    /** The user action for the event, or ACTION_NONE when it cannot start a drag. */
    protected int mapDragOperationFromModifiers(MouseEvent e) {
        int mods = e.getModifiersEx();
        int buttons = mods & BUTTONS;
        if (buttons != InputEvent.BUTTON1_DOWN_MASK && buttons != InputEvent.BUTTON2_DOWN_MASK
                && buttons != InputEvent.BUTTON3_DOWN_MASK) {
            return DnDConstants.ACTION_NONE;
        }
        return DragDropHost.dropActionFor(mods, getSourceActions());
    }

    @Override
    public void mousePressed(MouseEvent e) {
        events.clear();
        origin = null;
        if (mapDragOperationFromModifiers(e) != DnDConstants.ACTION_NONE) {
            appendEvent(e);
            origin = e.getPoint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        events.clear();
        origin = null;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (origin == null || events.isEmpty()) return;
        int action = mapDragOperationFromModifiers(e);
        if (action == DnDConstants.ACTION_NONE) return;
        if (DragDropHost.isTouch() && !DragDropHost.isLongPressed()) {
            // The finger is scrolling; this press can no longer become a drag.
            events.clear();
            origin = null;
            return;
        }
        appendEvent(e);
        int threshold = DragSource.getDragThreshold();
        if (Math.abs(origin.x - e.getX()) > threshold || Math.abs(origin.y - e.getY()) > threshold) {
            Point p = origin;
            origin = null;
            fireDragGestureRecognized(action, p);
        }
    }
}
