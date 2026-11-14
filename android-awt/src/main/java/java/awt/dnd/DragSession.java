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
package java.awt.dnd;

import java.awt.Component;
import java.awt.Image;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import net.runelite.awt.impl.DragDropHost;

/**
 * The in-process drag loop that stands in for the desktop's native one. It follows
 * the mouse events the host keeps sending to the source component after the
 * gesture (the press that started it is still held), resolves the drop target
 * under the pointer and plays the DropTarget / DragSourceContext protocol the way
 * the desktop peers do. Everything runs on the EDT.
 */
final class DragSession implements DragDropHost.Session, MouseListener, MouseMotionListener {

    private static DragSession active;

    static synchronized boolean isActive() {
        return active != null;
    }

    static void start(DragSourceContext dsc, Image image, Point offset) {
        DragSession s = new DragSession(dsc, image, offset);
        synchronized (DragSession.class) {
            if (active != null) throw new InvalidDnDOperationException("Drag and drop in progress");
            active = s;
        }
        Component c = dsc.getComponent();
        c.addMouseListener(s);
        c.addMouseMotionListener(s);
        // The pointer is where the gesture's last event left it, past the threshold.
        DragGestureEvent trigger = dsc.getTrigger();
        Object[] events = trigger.toArray();
        Point p = trigger.getDragOrigin();
        int mods = 0;
        if (events.length > 0 && events[events.length - 1] instanceof MouseEvent) {
            MouseEvent last = (MouseEvent) events[events.length - 1];
            p = last.getPoint();
            mods = last.getModifiersEx();
        }
        s.move(DragDropHost.toScreen(c, p.x, p.y), mods);
    }

    private final DragSourceContext dsc;
    private final Image image;
    private final Point offset;
    private final int sourceActions;

    private Point location = new Point();
    private int modifiers;
    private int userAction;
    private DropTarget target;
    private int targetAction = DnDConstants.ACTION_NONE;
    private boolean sourceInside;
    private boolean dropping;
    private boolean dropAccepted;
    private int dropAction = DnDConstants.ACTION_NONE;
    private boolean ended;

    private DragSession(DragSourceContext dsc, Image image, Point offset) {
        this.dsc = dsc;
        this.image = image;
        this.offset = offset == null ? new Point() : new Point(offset);
        this.sourceActions = dsc.getSourceActions();
    }

    Transferable transferable() {
        return dsc.getTransferable();
    }

    // DropTargetContext callbacks

    void acceptDrag(int op) {
        targetAction = op & sourceActions;
    }

    void rejectDrag() {
        targetAction = DnDConstants.ACTION_NONE;
    }

    void acceptDrop(int op) {
        if (!dropping) throw new InvalidDnDOperationException("not a drop");
        dropAccepted = true;
        dropAction = op & sourceActions;
    }

    void rejectDrop() {
        if (!dropping) throw new InvalidDnDOperationException("not a drop");
        dropAccepted = false;
        dropAction = DnDConstants.ACTION_NONE;
        // Desktop completes a rejected drop at once, unsuccessful.
        dropComplete(false);
    }

    void dropComplete(boolean success) {
        if (!dropping) throw new InvalidDnDOperationException("No Drop pending");
        dropping = false;
        DropTarget dt = target;
        if (dt != null) dt.getDropTargetContext().session = null;
        target = null;
        end(success ? dropAction : DnDConstants.ACTION_NONE, success);
    }

    // DragDropHost.Session

    public Image getDragImage() { return image; }
    public Point getDragImageOffset() { return new Point(offset); }
    public Point getLocation() { return new Point(location); }
    public Component getSourceComponent() { return dsc.getComponent(); }
    public boolean isDropAccepted() { return target != null && targetAction != DnDConstants.ACTION_NONE; }

    public void cancel() {
        if (ended || dropping) return;
        try {
            exitTarget();
        } finally {
            end(DnDConstants.ACTION_NONE, false);
        }
    }

    // The pointer

    public void mouseDragged(MouseEvent e) {
        if (ended || dropping) return;
        move(DragDropHost.toScreen(e.getComponent(), e.getX(), e.getY()), e.getModifiersEx());
    }

    public void mouseReleased(MouseEvent e) {
        if (ended || dropping) return;
        move(DragDropHost.toScreen(e.getComponent(), e.getX(), e.getY()), e.getModifiersEx());
        if (ended) return;
        drop();
    }

    public void mouseMoved(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}

    private void move(Point screen, int mods) {
        location = screen;
        int previousUser = userAction;
        boolean changed = mods != modifiers;
        modifiers = mods;
        userAction = DragDropHost.dropActionFor(mods & ~buttonMasks(), sourceActions);
        changed = changed && userAction != previousUser;

        DropTarget over = DropTarget.targetAt(screen);
        if (over != target) {
            exitTarget();
            target = over;
            if (over != null) {
                DropTargetContext ctx = over.getDropTargetContext();
                ctx.session = this;
                // A listener that neither accepts nor rejects keeps the user action.
                targetAction = userAction & over.getDefaultActions();
                over.dragEnter(new DropTargetDragEvent(ctx, local(over), userAction, sourceActions));
            }
        } else if (over != null) {
            DropTargetContext ctx = over.getDropTargetContext();
            DropTargetDragEvent dtde = new DropTargetDragEvent(ctx, local(over), userAction, sourceActions);
            if (changed) over.dropActionChanged(dtde);
            else over.dragOver(dtde);
        }
        if (ended) return;

        boolean accepted = isDropAccepted();
        int x = screen.x, y = screen.y;
        if (accepted) {
            DragSourceDragEvent dsde = new DragSourceDragEvent(dsc, userAction, targetAction, mods, x, y);
            if (!sourceInside) {
                sourceInside = true;
                dsc.dragEnter(dsde);
            } else if (changed) {
                dsc.dropActionChanged(dsde);
            } else {
                dsc.dragOver(dsde);
            }
        } else {
            if (sourceInside) {
                sourceInside = false;
                dsc.dragExit(new DragSourceEvent(dsc, x, y));
            }
            if (changed) {
                dsc.dropActionChanged(new DragSourceDragEvent(dsc, userAction, DnDConstants.ACTION_NONE, mods, x, y));
            }
        }
        dsc.dragMouseMoved(new DragSourceDragEvent(dsc, userAction, targetAction, mods, x, y));
        DragDropHost.updated(this);
    }

    private void drop() {
        DropTarget dt = target;
        if (dt == null || targetAction == DnDConstants.ACTION_NONE) {
            exitTarget();
            end(DnDConstants.ACTION_NONE, false);
            return;
        }
        if (sourceInside) {
            sourceInside = false;
            dsc.dragExit(new DragSourceEvent(dsc, location.x, location.y));
        }
        DropTargetContext ctx = dt.getDropTargetContext();
        ctx.session = this;
        dropping = true;
        dropAccepted = false;
        dropAction = DnDConstants.ACTION_NONE;
        try {
            dt.drop(new DropTargetDropEvent(ctx, local(dt), userAction, sourceActions, true));
        } catch (RuntimeException e) {
            if (dropping) dropComplete(false);
            throw e;
        }
        // An accepted drop may complete later (dropComplete from another task);
        // one that was never accepted is over now.
        if (dropping && !dropAccepted) dropComplete(false);
    }

    private void exitTarget() {
        DropTarget dt = target;
        target = null;
        targetAction = DnDConstants.ACTION_NONE;
        if (dt != null) {
            DropTargetContext ctx = dt.getDropTargetContext();
            try {
                dt.dragExit(new DropTargetEvent(ctx));
            } finally {
                ctx.session = null;
            }
        }
        if (sourceInside) {
            sourceInside = false;
            dsc.dragExit(new DragSourceEvent(dsc, location.x, location.y));
        }
    }

    private void end(int action, boolean success) {
        if (ended) return;
        ended = true;
        Component c = dsc.getComponent();
        c.removeMouseListener(this);
        c.removeMouseMotionListener(this);
        synchronized (DragSession.class) {
            if (active == this) active = null;
        }
        try {
            dsc.dragDropEnd(new DragSourceDropEvent(dsc, action, success, location.x, location.y));
        } finally {
            DragDropHost.ended(this);
        }
    }

    private Point local(DropTarget dt) {
        return DragDropHost.toLocal(dt.getComponent(), location);
    }

    private static int buttonMasks() {
        return InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;
    }
}
