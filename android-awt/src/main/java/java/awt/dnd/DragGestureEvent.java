/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.awt.dnd;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

/**
 * A <code>DragGestureEvent</code> is passed to <code>DragGestureListener</code>'s
 * dragGestureRecognized() method when a particular
 * <code>DragGestureRecognizer</code> detects that a platform dependent drag
 * initiating gesture has occurred on the <code>Component</code> that it is
 * tracking.
 *
 * @since 1.2
 */
public class DragGestureEvent extends EventObject {

    private static final long serialVersionUID = 9080172649166731306L;

    /**
     * Constructs a <code>DragGestureEvent</code> object given by the
     * <code>DragGestureRecognizer</code> instance firing this event,
     * an {@code act} parameter representing the user's preferred action,
     * an {@code ori} parameter indicating the origin of the drag,
     * and a {@code List} of events that comprise the gesture.
     *
     * @param dgr The <code>DragGestureRecognizer</code> firing this event
     * @param act The user's preferred action
     * @param ori The origin of the drag
     * @param evs The <code>List</code> of events that comprise the gesture
     * @throws IllegalArgumentException if any parameter equals {@code null}
     * @throws IllegalArgumentException if the act parameter does not comply with
     *                                  the values given in {@link DnDConstants}
     */
    public DragGestureEvent(DragGestureRecognizer dgr, int act, Point ori,
                            List<? extends InputEvent> evs)
    {
        super(dgr);

        if ((component = dgr.getComponent()) == null)
            throw new IllegalArgumentException("null component");
        if ((dragSource = dgr.getDragSource()) == null)
            throw new IllegalArgumentException("null DragSource");

        if (evs == null || evs.isEmpty())
            throw new IllegalArgumentException("null or empty list of events");

        if (act != DnDConstants.ACTION_COPY &&
            act != DnDConstants.ACTION_MOVE &&
            act != DnDConstants.ACTION_LINK)
            throw new IllegalArgumentException("bad action");

        if (ori == null) throw new IllegalArgumentException("null origin");

        events     = evs;
        action     = act;
        origin     = ori;
    }

    /**
     * Returns the source as a <code>DragGestureRecognizer</code>.
     *
     * @return the source as a <code>DragGestureRecognizer</code>
     */
    public DragGestureRecognizer getSourceAsDragGestureRecognizer() {
        return (DragGestureRecognizer)getSource();
    }

    /**
     * Returns the <code>Component</code> associated
     * with this <code>DragGestureEvent</code>.
     *
     * @return the Component
     */
    public Component getComponent() { return component; }

    /**
     * Returns the <code>DragSource</code>.
     *
     * @return the <code>DragSource</code>
     */
    public DragSource getDragSource() { return dragSource; }

    /**
     * Returns a <code>Point</code> in the coordinates
     * of the <code>Component</code> over which the drag originated.
     *
     * @return the Point where the drag originated in Component coords.
     */
    public Point getDragOrigin() {
        return origin;
    }

    /**
     * Returns an <code>Iterator</code> for the events
     * comprising the gesture.
     *
     * @return an Iterator for the events comprising the gesture
     */
    @SuppressWarnings("unchecked")
    public Iterator<InputEvent> iterator() { return (Iterator<InputEvent>) events.iterator(); }

    /**
     * Returns an <code>Object</code> array of the
     * events comprising the drag gesture.
     *
     * @return an array of the events comprising the gesture
     */
    public Object[] toArray() { return events.toArray(); }

    /**
     * Returns an array of the events comprising the drag gesture.
     *
     * @param array the array of <code>EventObject</code> sub(types)
     * @return an array of the events comprising the gesture
     */
    @SuppressWarnings("unchecked")
    public Object[] toArray(Object[] array) { return events.toArray(array); }

    /**
     * Returns an <code>int</code> representing the
     * action selected by the user.
     *
     * @return the action selected by the user
     */
    public int getDragAction() { return action; }

    /**
     * Returns the initial event that triggered the gesture.
     *
     * @return the first "triggering" event in the sequence of the gesture
     */
    public InputEvent getTriggerEvent() {
        return getSourceAsDragGestureRecognizer().getTriggerEvent();
    }

    /**
     * Starts the drag operation given the <code>Cursor</code> for this drag
     * operation and the <code>Transferable</code> representing the source data
     * for this drag operation.
     *
     * @param dragCursor     The initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param transferable The <code>Transferable</code> representing the source
     *                     data for this drag operation.
     * @throws InvalidDnDOperationException if the Drag and Drop
     *         system is unable to initiate a drag operation, or if the user
     *         attempts to start a drag while an existing drag operation is
     *         still executing.
     */
    public void startDrag(Cursor dragCursor, Transferable transferable)
      throws InvalidDnDOperationException {
        dragSource.startDrag(this, dragCursor, transferable, null);
    }

    /**
     * Starts the drag given the initial <code>Cursor</code> to display,
     * the <code>Transferable</code> object,
     * and the <code>DragSourceListener</code> to use.
     *
     * @param dragCursor     The initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param transferable The source's Transferable
     * @param dsl          The source's DragSourceListener
     * @throws InvalidDnDOperationException if
     * the Drag and Drop system is unable to
     * initiate a drag operation, or if the user
     * attempts to start a drag while an existing
     * drag operation is still executing.
     */
    public void startDrag(Cursor dragCursor, Transferable transferable, DragSourceListener dsl) throws InvalidDnDOperationException {
        dragSource.startDrag(this, dragCursor, transferable, dsl);
    }

    /**
     * Start the drag given the initial <code>Cursor</code> to display,
     * a drag <code>Image</code>, the offset of
     * the <code>Image</code>,
     * the <code>Transferable</code> object, and
     * the <code>DragSourceListener</code> to use.
     *
     * @param dragCursor     The initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param dragImage    The source's dragImage
     * @param imageOffset  The dragImage's offset
     * @param transferable The source's Transferable
     * @param dsl          The source's DragSourceListener
     * @throws InvalidDnDOperationException if
     * the Drag and Drop system is unable to
     * initiate a drag operation, or if the user
     * attempts to start a drag while an existing
     * drag operation is still executing.
     */
    public void startDrag(Cursor dragCursor, Image dragImage, Point imageOffset, Transferable transferable, DragSourceListener dsl) throws InvalidDnDOperationException {
        dragSource.startDrag(this,  dragCursor, dragImage, imageOffset, transferable, dsl);
    }

    private transient List<? extends InputEvent> events;

    /** The DragSource associated with this DragGestureEvent. */
    private DragSource dragSource;

    /** The Component associated with this DragGestureEvent. */
    private Component  component;

    /** The origin of the drag. */
    private Point      origin;

    /** The user's preferred action. */
    private int        action;
}
