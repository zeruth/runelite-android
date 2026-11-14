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
import java.awt.Point;
import java.awt.event.InputEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.TooManyListenersException;

/**
 * The <code>DragGestureRecognizer</code> is an
 * abstract base class for the specification
 * of a platform-dependent listener that can be associated with a particular
 * <code>Component</code> in order to
 * identify platform-dependent drag initiating gestures.
 * <p>
 * The appropriate <code>DragGestureRecognizer</code>
 * subclass instance is obtained from the
 * {@link DragSource} associated with
 * a particular <code>Component</code>, or from the <code>Toolkit</code> object via its
 * <code>createDragGestureRecognizer()</code> method.
 *
 * @since 1.2
 */
public abstract class DragGestureRecognizer implements Serializable {

    private static final long serialVersionUID = 8996673345831063337L;

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used
     * in this Drag and Drop operation, the <code>Component</code>
     * this <code>DragGestureRecognizer</code> should "observe"
     * for drag initiating gestures, the action(s) supported
     * for this Drag and Drop operation, and the
     * <code>DragGestureListener</code> to notify
     * once a drag initiating gesture has been detected.
     *
     * @param ds  the <code>DragSource</code> this
     * <code>DragGestureRecognizer</code>
     * will use to process the Drag and Drop operation
     * @param c the <code>Component</code>
     * this <code>DragGestureRecognizer</code>
     * should "observe" the event stream to,
     * in order to detect a drag initiating gesture.
     * If this value is <code>null</code>, the
     * <code>DragGestureRecognizer</code>
     * is not associated with any <code>Component</code>.
     * @param sa  the set (logical OR) of the
     * <code>DnDConstants</code>
     * that this Drag and Drop operation will support
     * @param dgl the <code>DragGestureRecognizer</code>
     * to notify when a drag gesture is detected
     * @throws IllegalArgumentException
     * if ds is <code>null</code>.
     */
    protected DragGestureRecognizer(DragSource ds, Component c, int sa, DragGestureListener dgl) {
        super();

        if (ds == null) throw new IllegalArgumentException("null DragSource");

        dragSource    = ds;
        component     = c;
        sourceActions = sa & (DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK);

        try {
            if (dgl != null) addDragGestureListener(dgl);
        } catch (TooManyListenersException tmle) {
            // cant happen ...
        }
    }

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used in this
     * Drag and Drop operation, the <code>Component</code> this
     * <code>DragGestureRecognizer</code> should "observe" for drag
     * initiating gestures, and the action(s) supported by this
     * Drag and Drop operation.
     *
     * @param ds  the <code>DragSource</code> this <code>DragGestureRecognizer</code> will use
     * @param c   the <code>Component</code> to observe, or <code>null</code>
     * @param sa the set (logical OR) of the <code>DnDConstants</code> supported
     * @throws IllegalArgumentException if ds is <code>null</code>.
     */
    protected DragGestureRecognizer(DragSource ds, Component c, int sa) {
        this(ds, c, sa, null);
    }

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used
     * in this Drag and Drop
     * operation, and the <code>Component</code> this
     * <code>DragGestureRecognizer</code>
     * should "observe" for drag
     * initiating gestures.
     *
     * @param ds the <code>DragSource</code> this <code>DragGestureRecognizer</code> will use
     * @param c  the <code>Component</code> to observe, or <code>null</code>
     * @throws IllegalArgumentException if ds is <code>null</code>.
     */
    protected DragGestureRecognizer(DragSource ds, Component c) {
        this(ds, c, DnDConstants.ACTION_NONE);
    }

    /**
     * Construct a new <code>DragGestureRecognizer</code>
     * given the <code>DragSource</code> to be used in this
     * Drag and Drop operation.
     *
     * @param ds the <code>DragSource</code> this <code>DragGestureRecognizer</code> will use
     * @throws IllegalArgumentException if ds is <code>null</code>.
     */
    protected DragGestureRecognizer(DragSource ds) {
        this(ds, null);
    }

    /** Register this DragGestureRecognizer's Listeners with the Component. */
    protected abstract void registerListeners();

    /** Unregister this DragGestureRecognizer's Listeners with the Component. */
    protected abstract void unregisterListeners();

    /**
     * This method returns the <code>DragSource</code>
     * this <code>DragGestureRecognizer</code>
     * will use in order to process the Drag and Drop
     * operation.
     *
     * @return the DragSource
     */
    public DragSource getDragSource() { return dragSource; }

    /**
     * This method returns the <code>Component</code>
     * that is to be "observed" by the
     * <code>DragGestureRecognizer</code>
     * for drag initiating gestures.
     *
     * @return The Component this DragGestureRecognizer
     * is associated with
     */
    public synchronized Component getComponent() { return component; }

    /**
     * set the Component that the DragGestureRecognizer is associated with
     *
     * registerListeners() and unregisterListeners() are called as a side
     * effect as appropriate.
     *
     * @param c The <code>Component</code> or <code>null</code>
     */
    public synchronized void setComponent(Component c) {
        if (component != null && dragGestureListener != null)
            unregisterListeners();

        component = c;

        if (component != null && dragGestureListener != null)
            registerListeners();
    }

    /**
     * This method returns an int representing the
     * type of action(s) this Drag and Drop
     * operation will support.
     *
     * @return the currently permitted source action(s)
     */
    public synchronized int getSourceActions() { return sourceActions; }

    /**
     * This method sets the permitted source drag action(s)
     * for this Drag and Drop operation.
     *
     * @param actions the permitted source drag action(s)
     */
    public synchronized void setSourceActions(int actions) {
        sourceActions = actions & (DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK);
    }

    /**
     * This method returns the first event in the
     * series of events that initiated
     * the Drag and Drop operation.
     *
     * @return the initial event that triggered the drag gesture
     */
    public InputEvent getTriggerEvent() { return events.isEmpty() ? null : events.get(0); }

    /** Reset the Recognizer, if its currently recognizing a gesture, ignore it. */
    public void resetRecognizer() { events.clear(); }

    /**
     * Register a new <code>DragGestureListener</code>.
     *
     * @param dgl the <code>DragGestureListener</code> to register
     * with this <code>DragGestureRecognizer</code>.
     *
     * @throws java.util.TooManyListenersException if a
     * <code>DragGestureListener</code> has already been added.
     */
    public synchronized void addDragGestureListener(DragGestureListener dgl) throws TooManyListenersException {
        if (dragGestureListener != null)
            throw new TooManyListenersException();
        else {
            dragGestureListener = dgl;

            if (component != null) registerListeners();
        }
    }

    /**
     * unregister the current DragGestureListener
     *
     * @param dgl the <code>DragGestureListener</code> to unregister
     * from this <code>DragGestureRecognizer</code>
     *
     * @throws IllegalArgumentException if
     * dgl is not (equal to) the currently registered <code>DragGestureListener</code>.
     */
    public synchronized void removeDragGestureListener(DragGestureListener dgl) {
        if (dragGestureListener == null || !dragGestureListener.equals(dgl))
            throw new IllegalArgumentException();
        else {
            dragGestureListener = null;

            if (component != null) unregisterListeners();
        }
    }

    /**
     * Notify the DragGestureListener that a Drag and Drop initiating
     * gesture has occurred. Then reset the state of the Recognizer.
     *
     * @param dragAction The action initially selected by the users gesture
     * @param p          The point (in Component coords) where the gesture originated
     */
    protected synchronized void fireDragGestureRecognized(int dragAction, Point p) {
        try {
            if (dragGestureListener != null) {
                dragGestureListener.dragGestureRecognized(new DragGestureEvent(this, dragAction, p, events));
            }
        } finally {
            events.clear();
        }
    }

    /**
     * Listeners registered on the Component by this Recognizer shall record
     * all Events that are recognized as part of the series of Events that go
     * to comprise a Drag and Drop initiating gesture via this API.
     *
     * @param awtie the <code>InputEvent</code>
     * to add to this <code>DragGestureRecognizer</code>'s
     * internal array of events. Note that <code>null</code>
     * is not a valid value, and will be ignored.
     */
    protected synchronized void appendEvent(InputEvent awtie) {
        events.add(awtie);
    }

    /**
     * The <code>DragSource</code>
     * associated with this
     * <code>DragGestureRecognizer</code>.
     */
    protected DragSource          dragSource;

    /**
     * The <code>Component</code>
     * associated with this <code>DragGestureRecognizer</code>.
     */
    protected Component           component;

    /**
     * The <code>DragGestureListener</code>
     * associated with this <code>DragGestureRecognizer</code>.
     */
    protected transient DragGestureListener dragGestureListener;

    /**
     * An <code>int</code> representing
     * the type(s) of action(s) used
     * in this Drag and Drop operation.
     */
    protected int  sourceActions;

    /**
     * The list of events (in order) that
     * the <code>DragGestureRecognizer</code>
     * "recognized" as a "gesture" that triggers a drag.
     */
    protected ArrayList<InputEvent> events = new ArrayList<InputEvent>(1);
}
