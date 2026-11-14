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
import java.awt.Image;
import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.event.InputEvent;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The seam between the in-process drag and drop of {@code java.awt.dnd} and the
 * Android views that host Swing trees. There is no native drag loop: a drag lives
 * on the EDT, fed by the mouse events the host already sends to the source
 * component, and the host draws the drag image from {@link Session}.
 *
 * <p>Each host root is its own coordinate space at (0, 0); a host that wants drags
 * to cross into another host's tree registers its root's on-screen origin with
 * {@link #setRootOrigin}, in the same logical pixels its events use.
 */
public final class DragDropHost {
    private DragDropHost() {}

    /** The drag in progress, as the host sees it. Methods are called on the EDT. */
    public interface Session {
        /** The image passed to {@code startDrag}, or null. */
        Image getDragImage();
        /** Offset of the image's origin from the pointer, never null. */
        Point getDragImageOffset();
        /** Pointer location in screen coordinates (see {@link #toScreen}). */
        Point getLocation();
        Component getSourceComponent();
        /** Whether the drop target under the pointer currently accepts the drag. */
        boolean isDropAccepted();
        /** Ends the drag without a drop, as Escape does on desktop. */
        void cancel();
    }

    /** Host callbacks, delivered on the EDT. */
    public interface Listener {
        void dragUpdated(Session session);
        void dragEnded(Session session);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private static final Map<Component, Point> origins =
            Collections.synchronizedMap(new WeakHashMap<Component, Point>());
    private static volatile Session current;
    // EDT only: what kind of pointer produced the mouse events being dispatched now.
    private static boolean touch;
    private static boolean held;

    public static void addListener(Listener l) { if (l != null) listeners.addIfAbsent(l); }
    public static void removeListener(Listener l) { listeners.remove(l); }

    public static Session current() { return current; }

    /** Cancels the drag in progress; returns whether there was one. */
    public static boolean cancel() {
        Session s = current;
        if (s == null) return false;
        s.cancel();
        return true;
    }

    /**
     * Called by the host before it dispatches pointer events: a finger only starts a
     * drag after a long press (a plain swipe scrolls); a mouse uses the desktop
     * threshold alone.
     */
    public static void setPointer(boolean isTouch, boolean longPressed) {
        touch = isTouch;
        held = longPressed;
    }
    public static boolean isTouch() { return touch; }
    public static boolean isLongPressed() { return held; }

    public static void setRootOrigin(Component root, int x, int y) {
        if (root != null) origins.put(root, new Point(x, y));
    }

    public static Component rootOf(Component c) {
        Component root = c;
        while (root != null && root.getParent() != null) root = root.getParent();
        return root;
    }

    /** Component coordinates to the screen space drags are resolved in. */
    public static Point toScreen(Component c, int x, int y) {
        Point p = c.getLocationOnScreen();
        Point origin = origins.get(rootOf(c));
        if (origin != null) { p.x += origin.x; p.y += origin.y; }
        p.x += x;
        p.y += y;
        return p;
    }

    public static Point toLocal(Component c, Point screen) {
        Point zero = toScreen(c, 0, 0);
        return new Point(screen.x - zero.x, screen.y - zero.y);
    }

    /**
     * The user drop action for the gesture modifiers, as the desktop drag loop computes
     * it: Ctrl+Shift links, Ctrl copies, Shift moves, and no modifier picks move, then
     * copy, then link from what the source supports.
     */
    public static int dropActionFor(int modifiersEx, int sourceActions) {
        int action;
        switch (modifiersEx & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)) {
            case InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK:
                action = DnDConstants.ACTION_LINK;
                break;
            case InputEvent.CTRL_DOWN_MASK:
                action = DnDConstants.ACTION_COPY;
                break;
            case InputEvent.SHIFT_DOWN_MASK:
                action = DnDConstants.ACTION_MOVE;
                break;
            default:
                if ((sourceActions & DnDConstants.ACTION_MOVE) != 0) action = DnDConstants.ACTION_MOVE;
                else if ((sourceActions & DnDConstants.ACTION_COPY) != 0) action = DnDConstants.ACTION_COPY;
                else action = sourceActions & DnDConstants.ACTION_LINK;
        }
        return action & sourceActions;
    }

    /** Called by {@code java.awt.dnd} when a drag starts or moves. */
    public static void updated(Session session) {
        current = session;
        for (Listener l : listeners) l.dragUpdated(session);
    }

    /** Called by {@code java.awt.dnd} once the drag has ended, after {@code dragDropEnd}. */
    public static void ended(Session session) {
        if (current == session) current = null;
        for (Listener l : listeners) l.dragEnded(session);
    }
}
