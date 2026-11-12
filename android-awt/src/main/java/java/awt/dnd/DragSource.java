/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Toolkit;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.awt.impl.TouchDragGestureRecognizer;

/**
 * The <code>DragSource</code> is the entity responsible
 * for the initiation of the Drag
 * and Drop operation, and may be used in a number of scenarios:
 * <UL>
 * <LI>1 default instance per JVM for the lifetime of that JVM.
 * <LI>1 instance per class of potential Drag Initiator object (e.g
 * TextField). [implementation dependent]
 * <LI>1 per instance of a particular
 * <code>Component</code>, or application specific
 * object associated with a <code>Component</code>
 * instance in the GUI. [implementation dependent]
 * <LI>Some other arbitrary association. [implementation dependent]
 *</UL>
 *
 * Android: drags are in-process. {@link #startDrag} opens a drag session on the EDT
 * that follows the mouse events of the source component (the Android host sends a
 * finger's events there too) and resolves {@link DropTarget}s by screen position;
 * the host draws the drag image ({@code net.runelite.awt.impl.DragDropHost}).
 *
 * @since 1.2
 */
public class DragSource implements Serializable {

    private static final long serialVersionUID = 6236096958971414066L;

    private static Cursor load(String name) {
        try {
            return Cursor.getSystemCustomCursor(name);
        } catch (Exception e) {
            return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        }
    }

    /** The default <code>Cursor</code> to use with a copy operation indicating that a drop is currently allowed. */
    public static final Cursor DefaultCopyDrop = load("DnD.Cursor.CopyDrop");

    /** The default <code>Cursor</code> to use with a move operation indicating that a drop is currently allowed. */
    public static final Cursor DefaultMoveDrop = load("DnD.Cursor.MoveDrop");

    /** The default <code>Cursor</code> to use with a link operation indicating that a drop is currently allowed. */
    public static final Cursor DefaultLinkDrop = load("DnD.Cursor.LinkDrop");

    /** The default <code>Cursor</code> to use with a copy operation indicating that a drop is currently not allowed. */
    public static final Cursor DefaultCopyNoDrop = load("DnD.Cursor.CopyNoDrop");

    /** The default <code>Cursor</code> to use with a move operation indicating that a drop is currently not allowed. */
    public static final Cursor DefaultMoveNoDrop = load("DnD.Cursor.MoveNoDrop");

    /** The default <code>Cursor</code> to use with a link operation indicating that a drop is currently not allowed. */
    public static final Cursor DefaultLinkNoDrop = load("DnD.Cursor.LinkNoDrop");

    private static final DragSource dflt = new DragSource();

    /** Internal constants for serialization. */
    static final String dragSourceListenerK = "dragSourceL";
    static final String dragSourceMotionListenerK = "dragSourceMotionL";

    /**
     * Gets the <code>DragSource</code> object associated with
     * the underlying platform.
     *
     * @return the platform DragSource
     */
    public static DragSource getDefaultDragSource() {
        return dflt;
    }

    /**
     * Reports
     * whether or not drag
     * <code>Image</code> support
     * is available on the underlying platform.
     * Android: the host draws the image next to the finger, so it is supported.
     *
     * @return if the Drag Image support is available on this platform
     */
    public static boolean isDragImageSupported() {
        return true;
    }

    /** Creates a new <code>DragSource</code>. */
    public DragSource() {
    }

    /**
     * Start a drag, given the <code>DragGestureEvent</code>
     * that initiated the drag, the initial
     * <code>Cursor</code> to use,
     * the <code>Image</code> to drag,
     * the offset of the <code>Image</code> origin
     * from the hotspot of the <code>Cursor</code> at
     * the instant of the trigger,
     * the <code>Transferable</code> subject data
     * of the drag, the <code>DragSourceListener</code>,
     * and the <code>FlavorMap</code>.
     *
     * @param trigger        the <code>DragGestureEvent</code> that initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param dragImage      the image to drag or {@code null}
     * @param imageOffset    the offset of the <code>Image</code> origin from the hotspot
     *                       of the <code>Cursor</code> at the instant of the trigger
     * @param transferable   the subject data of the drag
     * @param dsl            the <code>DragSourceListener</code>
     * @param flavorMap      the <code>FlavorMap</code> to use, or <code>null</code>
     *
     * @throws InvalidDnDOperationException
     *    if the Drag and Drop
     *    system is unable to initiate a drag operation, or if the user
     *    attempts to start a drag while an existing drag operation
     *    is still executing
     */
    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Image              dragImage,
                          Point              imageOffset,
                          Transferable       transferable,
                          DragSourceListener dsl,
                          FlavorMap          flavorMap) throws InvalidDnDOperationException {
        if (DragSession.isActive()) {
            throw new InvalidDnDOperationException("Drag and drop in progress");
        }
        if (flavorMap != null) this.flavorMap = flavorMap;
        DragSourceContext dsc = createDragSourceContext(trigger, dragCursor, dragImage,
                                                        imageOffset, transferable, dsl);
        if (dsc == null) {
            throw new InvalidDnDOperationException();
        }
        DragSession.start(dsc, dragImage, imageOffset);
    }

    /**
     * Start a drag, given the <code>DragGestureEvent</code>
     * that initiated the drag, the initial
     * <code>Cursor</code> to use,
     * the <code>Transferable</code> subject data
     * of the drag, the <code>DragSourceListener</code>,
     * and the <code>FlavorMap</code>.
     *
     * @param trigger        the <code>DragGestureEvent</code> that initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param transferable   the subject data of the drag
     * @param dsl            the <code>DragSourceListener</code>
     * @param flavorMap      the <code>FlavorMap</code> to use or <code>null</code>
     *
     * @throws InvalidDnDOperationException if a drag cannot be started
     */
    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Transferable       transferable,
                          DragSourceListener dsl,
                          FlavorMap          flavorMap) throws InvalidDnDOperationException {
        startDrag(trigger, dragCursor, null, null, transferable, dsl, flavorMap);
    }

    /**
     * Start a drag, given the <code>DragGestureEvent</code>
     * that initiated the drag, the initial <code>Cursor</code>
     * to use,
     * the <code>Image</code> to drag,
     * the offset of the <code>Image</code> origin
     * from the hotspot of the <code>Cursor</code>
     * at the instant of the trigger,
     * the subject data of the drag, and
     * the <code>DragSourceListener</code>.
     *
     * @param trigger           the <code>DragGestureEvent</code> that initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param dragImage         the <code>Image</code> to drag or <code>null</code>
     * @param dragOffset        the offset of the <code>Image</code> origin from the hotspot
     *                          of the <code>Cursor</code> at the instant of the trigger
     * @param transferable      the subject data of the drag
     * @param dsl               the <code>DragSourceListener</code>
     *
     * @throws InvalidDnDOperationException if a drag cannot be started
     */
    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Image              dragImage,
                          Point              dragOffset,
                          Transferable       transferable,
                          DragSourceListener dsl) throws InvalidDnDOperationException {
        startDrag(trigger, dragCursor, dragImage, dragOffset, transferable, dsl, null);
    }

    /**
     * Start a drag, given <code>DragGestureEvent</code>
     * that initiated the drag, the initial
     * <code>Cursor</code> to
     * use,
     * the <code>Transferable</code> subject data
     * of the drag, and the <code>DragSourceListener</code>.
     *
     * @param trigger           the <code>DragGestureEvent</code> that initiated the drag
     * @param dragCursor     the initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param transferable      the subject data of the drag
     * @param dsl               the <code>DragSourceListener</code>
     *
     * @throws InvalidDnDOperationException if a drag cannot be started
     */
    public void startDrag(DragGestureEvent   trigger,
                          Cursor             dragCursor,
                          Transferable       transferable,
                          DragSourceListener dsl) throws InvalidDnDOperationException {
        startDrag(trigger, dragCursor, null, null, transferable, dsl, null);
    }

    // JDK 9+ (9): jdk17u src/java.desktop/share/classes/java/awt/dnd/DragSource.java
    // (the 8u overload's first parameter is the DragSourceContextPeer this port has no use for)
    /**
     * Creates the {@code DragSourceContext} to handle the current drag
     * operation.
     *
     * @param dgl           The {@code DragGestureEvent} that triggered the drag
     * @param dragCursor     The initial {@code Cursor} for this drag operation
     *                       or {@code null} for the default cursor handling
     * @param dragImage     The {@code Image} to drag (or {@code null})
     * @param imageOffset   The offset of the {@code Image} origin from the
     *                      hotspot of the cursor at the instant of the trigger
     * @param t             The subject data of the drag
     * @param dsl           The {@code DragSourceListener}
     *
     * @return the {@code DragSourceContext}
     *
     * @throws NullPointerException if {@code dscp} is {@code null}
     * @throws NullPointerException if {@code dgl} is {@code null}
     * @throws NullPointerException if {@code dragImage} is not
     *    {@code null} and {@code imageOffset} is {@code null}
     * @throws NullPointerException if {@code t} is {@code null}
     * @throws IllegalArgumentException if the {@code Component}
     *         associated with the trigger event is {@code null}.
     * @throws IllegalArgumentException if the {@code DragSource} for the
     *         trigger event is {@code null}.
     * @throws IllegalArgumentException if the drag action for the
     *         trigger event is {@code DnDConstants.ACTION_NONE}.
     * @throws IllegalArgumentException if the source actions for the
     *         {@code DragGestureRecognizer} associated with the trigger
     *         event are equal to {@code DnDConstants.ACTION_NONE}.
     */
    protected DragSourceContext createDragSourceContext(DragGestureEvent dgl,
                                                        Cursor dragCursor,
                                                        Image dragImage,
                                                        Point imageOffset,
                                                        Transferable t,
                                                        DragSourceListener dsl) {
        return new DragSourceContext(dgl, dragCursor, dragImage, imageOffset, t, dsl);
    }

    /**
     * This method returns the
     * <code>FlavorMap</code> for this <code>DragSource</code>.
     *
     * @return the <code>FlavorMap</code> for this <code>DragSource</code>
     */
    public FlavorMap getFlavorMap() { return flavorMap; }

    /**
     * Creates a new <code>DragGestureRecognizer</code>
     * that implements the specified
     * abstract subclass of
     * <code>DragGestureRecognizer</code>, and
     * sets the specified <code>Component</code>
     * and <code>DragGestureListener</code> on
     * the newly created object.
     *
     * Android: the one platform recognizer is the mouse/touch one; any other class
     * yields <code>null</code>, as an unsupported class does on desktop.
     *
     * @param <T> the type of {@code DragGestureRecognizer} to create
     * @param recognizerAbstractClass the requested abstract type
     * @param actions                 the permitted source drag actions
     * @param c                       the <code>Component</code> target
     * @param dgl        the <code>DragGestureListener</code> to notify
     *
     * @return the new <code>DragGestureRecognizer</code> or <code>null</code>
     *    if the <code>Toolkit.createDragGestureRecognizer</code> method
     *    has no implementation available for
     *    the requested <code>DragGestureRecognizer</code>
     *    subclass and returns <code>null</code>
     */
    public <T extends DragGestureRecognizer> T
        createDragGestureRecognizer(Class<T> recognizerAbstractClass,
                                    Component c, int actions,
                                    DragGestureListener dgl)
    {
        if (recognizerAbstractClass == null) return null;
        if (!recognizerAbstractClass.isAssignableFrom(TouchDragGestureRecognizer.class)) return null;
        return recognizerAbstractClass.cast(new TouchDragGestureRecognizer(this, c, actions, dgl));
    }

    /**
     * Creates a new <code>DragGestureRecognizer</code>
     * that implements the default
     * abstract subclass of <code>DragGestureRecognizer</code>
     * for this <code>DragSource</code>,
     * and sets the specified <code>Component</code>
     * and <code>DragGestureListener</code> on the
     * newly created object.
     *
     * For this <code>DragSource</code>
     * the default is <code>MouseDragGestureRecognizer</code>.
     *
     * @param c       the <code>Component</code> target for the recognizer
     * @param actions the permitted source actions
     * @param dgl     the <code>DragGestureListener</code> to notify
     *
     * @return the new <code>DragGestureRecognizer</code> or <code>null</code>
     *    if the <code>Toolkit.createDragGestureRecognizer</code> method
     *    has no implementation available for
     *    the requested <code>DragGestureRecognizer</code>
     *    subclass and returns <code>null</code>
     */
    public DragGestureRecognizer createDefaultDragGestureRecognizer(Component c, int actions, DragGestureListener dgl) {
        return createDragGestureRecognizer(MouseDragGestureRecognizer.class, c, actions, dgl);
    }

    /**
     * Adds the specified <code>DragSourceListener</code> to this
     * <code>DragSource</code> to receive drag source events during drag
     * operations intiated with this <code>DragSource</code>.
     * If a <code>null</code> listener is specified, no action is taken and no
     * exception is thrown.
     *
     * @param dsl the <code>DragSourceListener</code> to add
     *
     * @since 1.4
     */
    public void addDragSourceListener(DragSourceListener dsl) {
        if (dsl != null) listeners.add(dsl);
    }

    /**
     * Removes the specified <code>DragSourceListener</code> from this
     * <code>DragSource</code>.
     * If a <code>null</code> listener is specified, no action is taken and no
     * exception is thrown.
     *
     * @param dsl the <code>DragSourceListener</code> to remove
     *
     * @since 1.4
     */
    public void removeDragSourceListener(DragSourceListener dsl) {
        if (dsl != null) listeners.remove(dsl);
    }

    /**
     * Gets all the <code>DragSourceListener</code>s
     * registered with this <code>DragSource</code>.
     *
     * @return all of this <code>DragSource</code>'s
     *         <code>DragSourceListener</code>s or an empty array if no
     *         such listeners are currently registered
     *
     * @since 1.4
     */
    public DragSourceListener[] getDragSourceListeners() {
        return listeners.toArray(new DragSourceListener[0]);
    }

    /**
     * Adds the specified <code>DragSourceMotionListener</code> to this
     * <code>DragSource</code> to receive drag motion events during drag
     * operations intiated with this <code>DragSource</code>.
     *
     * @param dsml the <code>DragSourceMotionListener</code> to add
     *
     * @since 1.4
     */
    public void addDragSourceMotionListener(DragSourceMotionListener dsml) {
        if (dsml != null) motionListeners.add(dsml);
    }

    /**
     * Removes the specified <code>DragSourceMotionListener</code> from this
     * <code>DragSource</code>.
     *
     * @param dsml the <code>DragSourceMotionListener</code> to remove
     *
     * @since 1.4
     */
    public void removeDragSourceMotionListener(DragSourceMotionListener dsml) {
        if (dsml != null) motionListeners.remove(dsml);
    }

    /**
     * Gets all of the  <code>DragSourceMotionListener</code>s
     * registered with this <code>DragSource</code>.
     *
     * @return all of this <code>DragSource</code>'s
     *         <code>DragSourceMotionListener</code>s or an empty array if no
     *         such listeners are currently registered
     *
     * @since 1.4
     */
    public DragSourceMotionListener[] getDragSourceMotionListeners() {
        return motionListeners.toArray(new DragSourceMotionListener[0]);
    }

    /**
     * Gets all the objects currently registered as
     * <code><em>Foo</em>Listener</code>s upon this <code>DragSource</code>.
     *
     * @param <T> the type of listener objects
     * @param listenerType the type of listeners requested; this parameter
     *          should specify an interface that descends from
     *          <code>java.util.EventListener</code>
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this
     *          <code>DragSource</code>, or an empty array if no such listeners
     *          have been added
     * @exception ClassCastException if <code>listenerType</code>
     *          doesn't specify a class or interface that implements
     *          <code>java.util.EventListener</code>
     *
     * @since 1.4
     */
    @SuppressWarnings("unchecked")
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        List<T> result = new ArrayList<T>();
        if (listenerType == DragSourceListener.class) {
            for (DragSourceListener l : listeners) result.add(listenerType.cast(l));
        } else if (listenerType == DragSourceMotionListener.class) {
            for (DragSourceMotionListener l : motionListeners) result.add(listenerType.cast(l));
        } else if (!EventListener.class.isAssignableFrom(listenerType)) {
            throw new ClassCastException(listenerType.getName());
        }
        return result.toArray((T[]) java.lang.reflect.Array.newInstance(listenerType, result.size()));
    }

    /** Fires dragEnter to the registered listeners; called by DragSourceContext. */
    void processDragEnter(DragSourceDragEvent dsde) {
        for (DragSourceListener l : listeners) l.dragEnter(dsde);
    }

    void processDragOver(DragSourceDragEvent dsde) {
        for (DragSourceListener l : listeners) l.dragOver(dsde);
    }

    void processDropActionChanged(DragSourceDragEvent dsde) {
        for (DragSourceListener l : listeners) l.dropActionChanged(dsde);
    }

    void processDragExit(DragSourceEvent dse) {
        for (DragSourceListener l : listeners) l.dragExit(dse);
    }

    void processDragDropEnd(DragSourceDropEvent dsde) {
        for (DragSourceListener l : listeners) l.dragDropEnd(dsde);
    }

    void processDragMouseMoved(DragSourceDragEvent dsde) {
        for (DragSourceMotionListener l : motionListeners) l.dragMouseMoved(dsde);
    }

    /**
     * Returns the drag gesture motion threshold. The drag gesture motion threshold
     * defines the recommended behavior for {@link MouseDragGestureRecognizer}s.
     * <p>
     * If the system property <code>awt.dnd.drag.threshold</code> is set to
     * a positive integer, this method returns the value of the system property;
     * otherwise if a pertinent desktop property is available and supported by
     * the implementation of the Java platform, this method returns the value of
     * that property; otherwise this method returns some default value.
     * The pertinent desktop property can be queried using
     * <code>java.awt.Toolkit.getDesktopProperty("DnD.gestureMotionThreshold")</code>.
     *
     * @return the drag gesture motion threshold
     * @see MouseDragGestureRecognizer
     * @since 1.5
     */
    public static int getDragThreshold() {
        int ts = Integer.getInteger("awt.dnd.drag.threshold", 0);
        if (ts > 0) {
            return ts;
        }
        Object value = desktopProperty("DnD.gestureMotionThreshold");
        if (value instanceof Integer && (Integer) value > 0) {
            return (Integer) value;
        }
        return 5;
    }

    // The shadow Toolkit may not declare getDesktopProperty yet; look it up so the
    // threshold follows it as soon as it does, without a compile-time dependency.
    private static Object desktopProperty(String name) {
        try {
            return Toolkit.class.getMethod("getDesktopProperty", String.class)
                    .invoke(Toolkit.getDefaultToolkit(), name);
        } catch (Exception e) {
            return null;
        }
    }

    private transient FlavorMap flavorMap = SystemFlavorMap.getDefaultFlavorMap();

    private final List<DragSourceListener> listeners = new CopyOnWriteArrayList<DragSourceListener>();

    private final List<DragSourceMotionListener> motionListeners = new CopyOnWriteArrayList<DragSourceMotionListener>();
}
