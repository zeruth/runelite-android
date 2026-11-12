/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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
package javax.swing;

import java.awt.Component;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.TooManyListenersException;
import javax.swing.plaf.UIResource;
import javax.swing.text.JTextComponent;
import net.runelite.awt.impl.SwingDragSupport;

/**
 * This class is used to handle the transfer of a <code>Transferable</code>
 * to and from Swing components.  The <code>Transferable</code> is used to
 * represent data that is exchanged via a cut, copy, or paste
 * to/from a clipboard.  It is also used in drag-and-drop operations
 * to represent a drag from a component, and a drop to a component.
 * Swing provides functionality that automatically supports cut, copy,
 * and paste keyboard bindings that use the functionality provided by
 * an implementation of this class.  Swing also provides functionality
 * that automatically supports drag and drop that uses the functionality
 * provided by an implementation of this class.  The Swing developer can
 * concentrate on specifying the semantics of a transfer primarily by setting
 * the <code>transferHandler</code> property on a Swing component.
 * <p>
 * This class is implemented to provide a default behavior of transferring
 * a component property simply by specifying the name of the property in
 * the constructor.  For example, to transfer the foreground color from
 * one component to another either via the clipboard or a drag and drop operation
 * a <code>TransferHandler</code> can be constructed with the string "foreground".
 * <p>
 * Android: drags are in-process (see {@code java.awt.dnd.DragSource}); the
 * JavaBeans property is found by its public getter and setter, as the
 * {@code Introspector} would for a plain bean.
 *
 * @author Timothy Prinzing
 * @author Shannon Hickey
 * @since 1.4
 */
@SuppressWarnings("serial")
public class TransferHandler implements Serializable {

    /** An <code>int</code> representing no transfer action. */
    public static final int NONE = DnDConstants.ACTION_NONE;

    /** An <code>int</code> representing a &quot;copy&quot; transfer action. */
    public static final int COPY = DnDConstants.ACTION_COPY;

    /** An <code>int</code> representing a &quot;move&quot; transfer action. */
    public static final int MOVE = DnDConstants.ACTION_MOVE;

    /** An <code>int</code> representing a source action capability of either &quot;copy&quot; or &quot;move&quot;. */
    public static final int COPY_OR_MOVE = DnDConstants.ACTION_COPY_OR_MOVE;

    /**
     * An <code>int</code> representing a &quot;link&quot; transfer action.
     * @since 1.6
     */
    public static final int LINK = DnDConstants.ACTION_LINK;

    /**
     * Represents a location where dropped data should be inserted.
     * This is a base class that only encapsulates a point.
     * Components supporting drop may provide subclasses of this
     * containing more information.
     *
     * @since 1.6
     */
    public static class DropLocation {
        private final Point dropPoint;

        /**
         * Constructs a drop location for the given point.
         *
         * @param dropPoint the drop point, representing the mouse's
         *        current location within the component.
         * @throws IllegalArgumentException if the point
         *         is <code>null</code>
         */
        protected DropLocation(Point dropPoint) {
            if (dropPoint == null) {
                throw new IllegalArgumentException("Point cannot be null");
            }
            this.dropPoint = new Point(dropPoint);
        }

        /**
         * Returns the drop point, representing the mouse's
         * current location within the component.
         *
         * @return the drop point.
         */
        public final Point getDropPoint() {
            return new Point(dropPoint);
        }

        /**
         * Returns a string representation of this drop location.
         *
         * @return a string representation of this drop location
         */
        public String toString() {
            return getClass().getName() + "[dropPoint=" + dropPoint + "]";
        }
    }

    /**
     * This class encapsulates all relevant details of a clipboard
     * or drag and drop transfer, and also allows for customizing
     * aspects of the drag and drop experience.
     *
     * @since 1.6
     */
    public static final class TransferSupport {
        private boolean isDrop;
        private Component component;

        private boolean showDropLocationIsSet;
        private boolean showDropLocation;

        private int dropAction = -1;

        /** The source is a {@code DropTargetDragEvent} or {@code DropTargetDropEvent} for drops, and a {@code Transferable} otherwise. */
        private Object source;

        private DropLocation dropLocation;

        private TransferSupport(Component component, DropTargetEvent event) {
            isDrop = true;
            setDNDVariables(component, event);
        }

        /**
         * Create a <code>TransferSupport</code> with <code>isDrop()</code>
         * <code>false</code> for the given component and
         * <code>Transferable</code>.
         *
         * @param component the target component
         * @param transferable the transferable
         * @throws NullPointerException if either parameter
         *         is <code>null</code>
         */
        public TransferSupport(Component component, Transferable transferable) {
            if (component == null) {
                throw new NullPointerException("component is null");
            }
            if (transferable == null) {
                throw new NullPointerException("transferable is null");
            }
            isDrop = false;
            this.component = component;
            this.source = transferable;
        }

        private void setDNDVariables(Component component, DropTargetEvent event) {
            assert isDrop;

            this.component = component;
            this.source = event;
            dropLocation = null;
            dropAction = -1;
            showDropLocationIsSet = false;

            if (source == null) {
                return;
            }

            Point p = source instanceof DropTargetDragEvent
                    ? ((DropTargetDragEvent) source).getLocation()
                    : ((DropTargetDropEvent) source).getLocation();

            if (component instanceof JTextComponent) {
                dropLocation = SwingDragSupport.textDropLocationForPoint((JTextComponent) component, p);
            } else if (component instanceof JComponent) {
                dropLocation = ((JComponent) component).dropLocationForPoint(p);
            }
            if (dropLocation == null) {
                dropLocation = new DropLocation(p);
            }
        }

        /**
         * Returns whether or not this <code>TransferSupport</code>
         * represents a drop operation.
         *
         * @return whether or not this is a drop operation
         */
        public boolean isDrop() {
            return isDrop;
        }

        /**
         * Returns the target component of this transfer.
         *
         * @return the target component
         */
        public Component getComponent() {
            return component;
        }

        private void assureIsDrop() {
            if (!isDrop) {
                throw new IllegalStateException("Not a drop");
            }
        }

        /**
         * Returns the current (non-{@code null}) drop location for the component,
         * when this {@code TransferSupport} represents a drop.
         *
         * @return the drop location
         * @throws IllegalStateException if this is not a drop
         */
        public DropLocation getDropLocation() {
            assureIsDrop();
            return dropLocation;
        }

        /**
         * Sets whether or not the drop location should be visually indicated
         * for the transfer.
         *
         * @param showDropLocation whether or not to indicate the drop location
         * @throws IllegalStateException if this is not a drop
         */
        public void setShowDropLocation(boolean showDropLocation) {
            assureIsDrop();
            this.showDropLocation = showDropLocation;
            this.showDropLocationIsSet = true;
        }

        /**
         * Sets the drop action for the transfer - which must represent a drop
         * - to the given action, instead of the default user drop action.
         *
         * @param dropAction the drop action
         * @throws IllegalStateException if this is not a drop
         * @throws IllegalArgumentException if an invalid action is specified
         */
        public void setDropAction(int dropAction) {
            assureIsDrop();

            int action = dropAction & getSourceDropActions();

            if (!(action == COPY || action == MOVE || action == LINK)) {
                throw new IllegalArgumentException("unsupported drop action: " + dropAction);
            }

            this.dropAction = dropAction;
        }

        /**
         * Returns the action chosen for the drop, when this
         * <code>TransferSupport</code> represents a drop.
         *
         * @return the action chosen for the drop
         * @throws IllegalStateException if this is not a drop
         */
        public int getDropAction() {
            return dropAction == -1 ? getUserDropAction() : dropAction;
        }

        /**
         * Returns the user drop action for the drop, when this
         * <code>TransferSupport</code> represents a drop.
         *
         * @return the user drop action
         * @throws IllegalStateException if this is not a drop
         */
        public int getUserDropAction() {
            assureIsDrop();

            return (source instanceof DropTargetDragEvent)
                ? ((DropTargetDragEvent) source).getDropAction()
                : ((DropTargetDropEvent) source).getDropAction();
        }

        /**
         * Returns the drag source's supported drop actions, when this
         * <code>TransferSupport</code> represents a drop.
         *
         * @return the drag source's supported drop actions
         * @throws IllegalStateException if this is not a drop
         */
        public int getSourceDropActions() {
            assureIsDrop();

            return (source instanceof DropTargetDragEvent)
                ? ((DropTargetDragEvent) source).getSourceActions()
                : ((DropTargetDropEvent) source).getSourceActions();
        }

        /**
         * Returns the data flavors for this transfer.
         *
         * @return the data flavors for this transfer
         */
        public DataFlavor[] getDataFlavors() {
            if (isDrop) {
                if (source instanceof DropTargetDragEvent) {
                    return ((DropTargetDragEvent) source).getCurrentDataFlavors();
                } else {
                    return ((DropTargetDropEvent) source).getCurrentDataFlavors();
                }
            }

            return ((Transferable) source).getTransferDataFlavors();
        }

        /**
         * Returns whether or not the given data flavor is supported.
         *
         * @param df the <code>DataFlavor</code> to test
         * @return whether or not the given flavor is supported.
         */
        public boolean isDataFlavorSupported(DataFlavor df) {
            if (isDrop) {
                if (source instanceof DropTargetDragEvent) {
                    return ((DropTargetDragEvent) source).isDataFlavorSupported(df);
                } else {
                    return ((DropTargetDropEvent) source).isDataFlavorSupported(df);
                }
            }

            return ((Transferable) source).isDataFlavorSupported(df);
        }

        /**
         * Returns the <code>Transferable</code> associated with this transfer.
         *
         * @return the <code>Transferable</code> associated with this transfer
         */
        public Transferable getTransferable() {
            if (isDrop) {
                if (source instanceof DropTargetDragEvent) {
                    return ((DropTargetDragEvent) source).getTransferable();
                } else {
                    return ((DropTargetDropEvent) source).getTransferable();
                }
            }

            return (Transferable) source;
        }
    }

    /**
     * Returns an {@code Action} that performs cut operations to the
     * clipboard.
     *
     * @return an {@code Action} for performing cuts to the clipboard
     */
    public static Action getCutAction() {
        return cutAction;
    }

    /**
     * Returns an {@code Action} that performs copy operations to the
     * clipboard.
     *
     * @return an {@code Action} for performing copies to the clipboard
     */
    public static Action getCopyAction() {
        return copyAction;
    }

    /**
     * Returns an {@code Action} that performs paste operations from the
     * clipboard.
     *
     * @return an {@code Action} for performing pastes from the clipboard
     */
    public static Action getPasteAction() {
        return pasteAction;
    }

    /**
     * Constructs a transfer handler that can transfer a Java Bean property
     * from one component to another via the clipboard or a drag and drop
     * operation.
     *
     * @param property  the name of the property to transfer; this can
     *  be <code>null</code> if there is no property associated with the transfer
     *  handler (a subclass that performs some other kind of transfer, for example)
     */
    public TransferHandler(String property) {
        propertyName = property;
    }

    /**
     * Convenience constructor for subclasses.
     */
    protected TransferHandler() {
        this(null);
    }

    private Image dragImage;
    private Point dragImageOffset;

    /**
     * Sets the drag image parameter.
     *
     * @param img an image to drag
     * @since 1.7
     */
    public void setDragImage(Image img) {
        dragImage = img;
    }

    /**
     * Returns the drag image.
     *
     * @return the drag image, or {@code null}
     * @since 1.7
     */
    public Image getDragImage() {
        return dragImage;
    }

    /**
     * Sets an anchor offset for the image to drag.
     *
     * @param p a {@code Point} object that corresponds
     * to coordinates of an anchor offset of the image
     * relative to the upper left corner of the image
     * @since 1.7
     */
    public void setDragImageOffset(Point p) {
        dragImageOffset = new Point(p);
    }

    /**
     * Returns an anchor offset for the image to drag.
     *
     * @return a {@code Point} object that corresponds
     * to coordinates of an anchor offset of the image
     * relative to the upper left corner of the image.
     * The point {@code (0,0)} returns by default.
     * @since 1.7
     */
    public Point getDragImageOffset() {
        if (dragImageOffset == null) {
            return new Point(0, 0);
        }
        return new Point(dragImageOffset);
    }

    /**
     * Causes the Swing drag support to be initiated.  This is called by
     * the various UI implementations in the <code>javax.swing.plaf.basic</code>
     * package if the dragEnabled property is set on the component.
     * This can be called by custom UI
     * implementations to use the Swing drag support.  This method can also be called
     * by a Swing extension written as a subclass of <code>JComponent</code>
     * to take advantage of the Swing drag support.
     * <p>
     * The transfer <em>will not necessarily</em> have been completed at the
     * return of this call (i.e. the call does not block waiting for the drop).
     * The transfer will take place through the Swing implementation of the
     * <code>java.awt.dnd</code> mechanism, requiring no further effort
     * from the developer. The <code>exportDone</code> method will be called
     * when the transfer has completed.
     *
     * @param comp  the component holding the data to be transferred;
     *              provided to enable sharing of <code>TransferHandler</code>s
     * @param e     the event that triggered the transfer
     * @param action the transfer action initially requested;
     *               either {@code COPY}, {@code MOVE} or {@code LINK};
     *               the DnD system may change the action used during the
     *               course of the drag operation
     */
    public void exportAsDrag(JComponent comp, InputEvent e, int action) {
        int srcActions = getSourceActions(comp);

        // only mouse events supported for drag operations
        if (!(e instanceof MouseEvent)
                // only support known actions
                || !(action == COPY || action == MOVE || action == LINK)
                // only support valid source actions
                || (srcActions & action) == 0) {

            action = NONE;
        }

        if (action != NONE) {
            if (recognizer == null) {
                recognizer = new SwingDragGestureRecognizer(new DragHandler());
            }
            recognizer.gestured(comp, (MouseEvent) e, srcActions, action);
        } else {
            exportDone(comp, null, NONE);
        }
    }

    /**
     * Causes a transfer from the given component to the
     * given clipboard.  This method is called by the default cut and
     * copy actions registered in a component's action map.
     * <p>
     * The transfer <em>will</em> have been completed at the
     * return of this call.
     *
     * @param comp  the component holding the data to be transferred;
     *              provided to enable sharing of <code>TransferHandler</code>s
     * @param clip  the clipboard to transfer the data into
     * @param action the transfer action requested; this should
     *  be a value of either <code>COPY</code> or <code>MOVE</code>;
     *  the operation performed is the intersection  of the transfer
     *  capabilities given by getSourceActions and the requested action;
     *  the intersection may result in an action of <code>NONE</code>
     *  if the requested action isn't supported
     * @throws IllegalStateException if the clipboard is currently unavailable
     */
    public void exportToClipboard(JComponent comp, Clipboard clip, int action)
        throws IllegalStateException {

        if ((action == COPY || action == MOVE)
                && (getSourceActions(comp) & action) != 0) {

            Transferable t = createTransferable(comp);
            if (t != null) {
                try {
                    clip.setContents(t, null);
                    exportDone(comp, t, action);
                    return;
                } catch (IllegalStateException ise) {
                    exportDone(comp, t, NONE);
                    throw ise;
                }
            }
        }

        exportDone(comp, null, NONE);
    }

    /**
     * Causes a transfer to occur from a clipboard or a drag and
     * drop operation. The <code>Transferable</code> to be
     * imported and the component to transfer to are contained
     * within the <code>TransferSupport</code>.
     * <p>
     * This method is not called for a drop when {@code canImport}
     * returned {@code false}; it is called for a paste regardless.
     * The default implementation imports into the Java Bean property
     * given to the constructor, when the component is a {@code JComponent}.
     *
     * @param support the object containing the details of
     *        the transfer, not <code>null</code>.
     * @return true if the data was inserted into the component,
     *         false otherwise
     * @throws NullPointerException if <code>support</code> is {@code null}
     * @since 1.6
     */
    public boolean importData(TransferSupport support) {
        return support.getComponent() instanceof JComponent
            ? importData((JComponent) support.getComponent(), support.getTransferable())
            : false;
    }

    /**
     * Causes a transfer to a component from a clipboard or a
     * DND drop operation.  The <code>Transferable</code> represents
     * the data to be imported into the component.
     * <p>
     * Note: Swing now calls the newer version of <code>importData</code>
     * that takes a <code>TransferSupport</code>, which in turn calls this
     * method (if the component in the {@code TransferSupport} is a
     * {@code JComponent}).
     *
     * @param comp  the component to receive the transfer;
     *              provided to enable sharing of <code>TransferHandler</code>s
     * @param t     the data to import
     * @return  true if the data was inserted into the component, false otherwise
     */
    public boolean importData(JComponent comp, Transferable t) {
        Method writer = getPropertyWriter(comp);
        if (writer != null) {
            DataFlavor flavor = getPropertyDataFlavor(writer.getParameterTypes()[0], t.getTransferDataFlavors());

            if (flavor != null) {
                try {
                    Object value = t.getTransferData(flavor);
                    writer.invoke(comp, value);
                    return true;
                } catch (Exception ex) {
                    System.err.println("Invocation failed");
                    // invocation code
                }
            }
        }
        return false;
    }

    /**
     * This method is called repeatedly during a drag and drop operation
     * to allow the developer to configure properties of, and to return
     * the acceptability of transfers; with a return value of {@code true}
     * indicating that the transfer represented by the given
     * {@code TransferSupport} (which contains all of the details of the
     * transfer) is acceptable at the current time, and a value of {@code false}
     * rejecting the transfer.
     * <p>
     * The default implementation calls {@code canImport(JComponent, DataFlavor[])}
     * when the component is a {@code JComponent}.
     *
     * @param support the object containing the details of
     *        the transfer, not <code>null</code>.
     * @return <code>true</code> if the import can happen,
     *         <code>false</code> otherwise
     * @throws NullPointerException if <code>support</code> is {@code null}
     * @since 1.6
     */
    public boolean canImport(TransferSupport support) {
        return support.getComponent() instanceof JComponent
            ? canImport((JComponent) support.getComponent(), support.getDataFlavors())
            : false;
    }

    /**
     * Indicates whether a component will accept an import of the given
     * set of data flavors prior to actually attempting to import it.
     *
     * @param comp  the component to receive the transfer;
     *              provided to enable sharing of <code>TransferHandler</code>s
     * @param transferFlavors  the data formats available
     * @return  true if the data can be inserted into the component, false otherwise
     */
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        Method writer = getPropertyWriter(comp);
        if (writer != null) {
            DataFlavor flavor = getPropertyDataFlavor(writer.getParameterTypes()[0], transferFlavors);
            if (flavor != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the type of transfer actions supported by the source;
     * any bitwise-OR combination of {@code COPY}, {@code MOVE}
     * and {@code LINK}.
     * <p>
     * This implementation returns {@code COPY} if the transfer handler
     * has been constructed with the name of a readable property of
     * the component, and {@code NONE} otherwise.
     *
     * @param c  the component holding the data to be transferred;
     *           provided to enable sharing of <code>TransferHandler</code>s
     * @return {@code COPY} if the transfer property can be found,
     *          otherwise returns <code>NONE</code>
     */
    public int getSourceActions(JComponent c) {
        return getPropertyReader(c) != null ? COPY : NONE;
    }

    /**
     * Returns an object that establishes the look of a transfer.
     * This implementation returns {@code null}; the drag image is
     * {@link #getDragImage}.
     *
     * @param t  the data to be transferred; this value is expected to have been
     *  created by the <code>createTransferable</code> method
     * @return  <code>null</code>, indicating
     *    there is no default visual representation
     */
    public Icon getVisualRepresentation(Transferable t) {
        return null;
    }

    /**
     * Creates a <code>Transferable</code> to use as the source for
     * a data transfer. Returns the representation of the data to
     * be transferred, or <code>null</code> if the component's
     * property is <code>null</code>
     *
     * @param c  the component holding the data to be transferred;
     *              provided to enable sharing of <code>TransferHandler</code>s
     * @return  the representation of the data to be transferred, or
     *  <code>null</code> if the property associated with <code>c</code>
     *  is <code>null</code>
     */
    protected Transferable createTransferable(JComponent c) {
        Method reader = getPropertyReader(c);
        if (reader != null) {
            return new PropertyTransferable(reader, c);
        }
        return null;
    }

    /**
     * Invoked after data has been exported.  This method should remove
     * the data that was transferred if the action was <code>MOVE</code>.
     * <p>
     * This method is implemented to do nothing since <code>MOVE</code>
     * is not a supported action of this implementation
     * (<code>getSourceActions</code> does not include <code>MOVE</code>).
     *
     * @param source the component that was the source of the data
     * @param data   The data that was transferred or possibly null
     *               if the action is <code>NONE</code>.
     * @param action the actual action that was performed
     */
    protected void exportDone(JComponent source, Transferable data, int action) {
    }

    /** The public no-argument getter for the property, as the Introspector would find it. */
    private Method getPropertyReader(JComponent comp) {
        if (propertyName == null || propertyName.isEmpty()) {
            return null;
        }
        String cap = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        for (String prefix : new String[] { "get", "is" }) {
            try {
                Method m = comp.getClass().getMethod(prefix + cap);
                if (m.getReturnType() != void.class && !Modifier.isStatic(m.getModifiers())
                        && ("get".equals(prefix) || m.getReturnType() == boolean.class)) {
                    return m;
                }
            } catch (NoSuchMethodException e) {
                // try the next prefix
            }
        }
        return null;
    }

    /** The setter matching the readable property's type. */
    private Method getPropertyWriter(JComponent comp) {
        Method reader = getPropertyReader(comp);
        if (reader == null) {
            return null;
        }
        try {
            Method m = comp.getClass().getMethod("set" + reader.getName().substring(
                    reader.getName().startsWith("is") ? 2 : 3), reader.getReturnType());
            return Modifier.isStatic(m.getModifiers()) ? null : m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Fetches the data flavor from the array of possible flavors that
     * has data of the type represented by property type.  Null is
     * returned if there is no match.
     */
    private DataFlavor getPropertyDataFlavor(Class<?> k, DataFlavor[] flavors) {
        for (int i = 0; i < flavors.length; i++) {
            DataFlavor flavor = flavors[i];
            if (isObjectFlavor(flavor) && wrap(k).isAssignableFrom(flavor.getRepresentationClass())) {
                return flavor;
            }
        }
        return null;
    }

    /**
     * The JDK matches {@code application/x-java-jvm-local-objectref} only; the
     * property flavor here is built with {@code DataFlavor(Class, String)}, which is
     * the serialized-object type, so both object types are accepted.
     */
    private static boolean isObjectFlavor(DataFlavor flavor) {
        String mime = flavor.getMimeType().toLowerCase(java.util.Locale.ROOT);
        return mime.startsWith("application/x-java-jvm-local-objectref")
                || mime.startsWith("application/x-java-serialized-object");
    }

    private static Class<?> wrap(Class<?> k) {
        if (!k.isPrimitive()) return k;
        if (k == boolean.class) return Boolean.class;
        if (k == int.class) return Integer.class;
        if (k == long.class) return Long.class;
        if (k == double.class) return Double.class;
        if (k == float.class) return Float.class;
        if (k == short.class) return Short.class;
        if (k == byte.class) return Byte.class;
        return Character.class;
    }

    private String propertyName;
    private static SwingDragGestureRecognizer recognizer = null;

    /**
     * Installs the drop target {@code setTransferHandler} needs, unless the
     * application set its own: desktop's {@code SwingUtilities.installSwingDropTargetAsNecessary}.
     */
    static void installSwingDropTargetAsNecessary(Component c, TransferHandler t) {
        DropTarget dropHandler = c.getDropTarget();
        if (dropHandler == null || dropHandler instanceof UIResource) {
            if (t == null) {
                c.setDropTarget(null);
            } else {
                c.setDropTarget(new SwingDropTarget(c));
            }
        }
    }

    static class PropertyTransferable implements Transferable {

        PropertyTransferable(Method reader, JComponent c) {
            this.reader = reader;
            component = c;
        }

        public DataFlavor[] getTransferDataFlavors() {
            // The JDK offers a jvm-local-objectref flavor; the (Class, String) form
            // carries the property class without a class-loader lookup by name.
            return new DataFlavor[] { new DataFlavor(wrap(reader.getReturnType()), null) };
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            Class<?> propertyType = wrap(reader.getReturnType());
            return isObjectFlavor(flavor) && flavor.getRepresentationClass().isAssignableFrom(propertyType);
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            try {
                return reader.invoke(component);
            } catch (Exception ex) {
                throw new IOException("Property read failed: " + reader.getName());
            }
        }

        JComponent component;
        Method reader;
    }

    /**
     * This is the default drop target for drag and drop operations if
     * one isn't provided by the developer.
     */
    static class SwingDropTarget extends DropTarget implements UIResource {

        SwingDropTarget(Component c) {
            super(c, COPY_OR_MOVE | LINK, null);
            try {
                // addDropTargetListener is overridden
                // we specifically need to add to the superclass
                super.addDropTargetListener(handler);
            } catch (TooManyListenersException tmle) {}
        }

        private final DropHandler handler = new DropHandler();

        public void addDropTargetListener(DropTargetListener dtl) throws TooManyListenersException {
            if (listenerList == null) {
                listenerList = new java.util.ArrayList<DropTargetListener>();
            }
            listenerList.add(dtl);
        }

        public void removeDropTargetListener(DropTargetListener dtl) {
            if (listenerList != null) {
                listenerList.remove(dtl);
            }
        }

        public void dragEnter(DropTargetDragEvent e) {
            super.dragEnter(e);
            for (DropTargetListener l : listeners()) l.dragEnter(e);
        }

        public void dragOver(DropTargetDragEvent e) {
            super.dragOver(e);
            for (DropTargetListener l : listeners()) l.dragOver(e);
        }

        public void dragExit(DropTargetEvent e) {
            super.dragExit(e);
            for (DropTargetListener l : listeners()) l.dragExit(e);
            if (!isActive()) {
                // If the Drop target is inactive the dragExit will not be dispatched to the dtListener,
                // so make sure that we clean up the dtListener anyway.
                handler.cleanup(false);
            }
        }

        public void drop(DropTargetDropEvent e) {
            super.drop(e);
            for (DropTargetListener l : listeners()) l.drop(e);
        }

        public void dropActionChanged(DropTargetDragEvent e) {
            super.dropActionChanged(e);
            for (DropTargetListener l : listeners()) l.dropActionChanged(e);
        }

        private DropTargetListener[] listeners() {
            return listenerList == null ? new DropTargetListener[0]
                    : listenerList.toArray(new DropTargetListener[0]);
        }

        private java.util.List<DropTargetListener> listenerList;
    }

    private static class DropHandler implements DropTargetListener, Serializable {

        private Component component;
        private Object state;
        private final TransferSupport support = new TransferSupport(null, (DropTargetEvent) null);

        private void setComponentDropLocation(TransferSupport support, boolean forDrop) {
            DropLocation dropLocation = (support == null) ? null : support.getDropLocation();

            if (component instanceof JTextComponent) {
                state = SwingDragSupport.setTextDropLocation((JTextComponent) component, dropLocation, state, forDrop);
            } else if (component instanceof JComponent) {
                state = ((JComponent) component).setDropLocation(dropLocation, state, forDrop);
            }
        }

        private void handleDrag(DropTargetDragEvent e) {
            component = e.getDropTargetContext().getComponent();
            TransferHandler importer = component instanceof JComponent
                    ? ((JComponent) component).getTransferHandler() : null;

            if (importer == null) {
                e.rejectDrag();
                setComponentDropLocation(null, false);
                return;
            }

            support.setDNDVariables(component, e);
            boolean canImport = importer.canImport(support);

            if (canImport) {
                e.acceptDrag(support.getDropAction());
            } else {
                e.rejectDrag();
            }

            boolean showLocation = support.showDropLocationIsSet ? support.showDropLocation : canImport;

            setComponentDropLocation(showLocation ? support : null, false);
        }

        public void dragEnter(DropTargetDragEvent e) {
            state = null;
            handleDrag(e);
        }

        public void dragOver(DropTargetDragEvent e) {
            handleDrag(e);
        }

        public void dragExit(DropTargetEvent e) {
            cleanup(false);
        }

        public void drop(DropTargetDropEvent e) {
            component = e.getDropTargetContext().getComponent();
            TransferHandler importer = component instanceof JComponent
                    ? ((JComponent) component).getTransferHandler() : null;

            if (importer == null) {
                e.rejectDrop();
                cleanup(false);
                return;
            }

            support.setDNDVariables(component, e);
            boolean canImport = importer.canImport(support);

            if (canImport) {
                e.acceptDrop(support.getDropAction());

                boolean showLocation = support.showDropLocationIsSet ? support.showDropLocation : canImport;

                setComponentDropLocation(showLocation ? support : null, false);

                boolean success;

                try {
                    success = importer.importData(support);
                } catch (RuntimeException re) {
                    success = false;
                }

                e.dropComplete(success);
                cleanup(success);
            } else {
                e.rejectDrop();
                cleanup(false);
            }
        }

        public void dropActionChanged(DropTargetDragEvent e) {
            // Only dispatched while a drag is over the component.
            if (component == null) {
                return;
            }

            handleDrag(e);
        }

        void cleanup(boolean forDrop) {
            setComponentDropLocation(null, forDrop);
            if (component instanceof JComponent) {
                ((JComponent) component).dndDone();
            }
            component = null;
        }
    }

    /**
     * This is the default drag handler for drag and drop operations that
     * use the <code>TransferHandler</code>.
     */
    private static class DragHandler implements DragGestureListener, DragSourceListener {

        private boolean scrolls;

        public void dragGestureRecognized(DragGestureEvent dge) {
            JComponent c = (JComponent) dge.getComponent();
            TransferHandler th = c.getTransferHandler();
            Transferable t = th.createTransferable(c);
            if (t != null) {
                scrolls = c.getAutoscrolls();
                c.setAutoscrolls(false);
                try {
                    Image im = th.getDragImage();
                    if (im == null) {
                        dge.startDrag(null, t, this);
                    } else {
                        dge.startDrag(null, im, th.getDragImageOffset(), t, this);
                    }
                    return;
                } catch (RuntimeException re) {
                    c.setAutoscrolls(scrolls);
                }
            }

            th.exportDone(c, t, NONE);
        }

        public void dragEnter(DragSourceDragEvent dsde) {
        }

        public void dragOver(DragSourceDragEvent dsde) {
        }

        public void dragExit(DragSourceEvent dsde) {
        }

        public void dragDropEnd(DragSourceDropEvent dsde) {
            DragSourceContext dsc = dsde.getDragSourceContext();
            JComponent c = (JComponent) dsc.getComponent();
            TransferHandler th = c.getTransferHandler();
            if (th != null) {
                if (dsde.getDropSuccess()) {
                    th.exportDone(c, dsc.getTransferable(), dsde.getDropAction());
                } else {
                    th.exportDone(c, dsc.getTransferable(), NONE);
                }
            }
            c.setAutoscrolls(scrolls);
        }

        public void dropActionChanged(DragSourceDragEvent dsde) {
        }
    }

    private static class SwingDragGestureRecognizer extends DragGestureRecognizer {

        SwingDragGestureRecognizer(DragGestureListener dgl) {
            super(DragSource.getDefaultDragSource(), null, NONE, dgl);
        }

        void gestured(JComponent c, MouseEvent e, int srcActions, int action) {
            setComponent(c);
            setSourceActions(srcActions);
            appendEvent(e);
            fireDragGestureRecognized(action, e.getPoint());
        }

        /** register this DragGestureRecognizer's Listeners with the Component */
        protected void registerListeners() {
        }

        /** unregister this DragGestureRecognizer's Listeners with the Component; subclasses must override this method */
        protected void unregisterListeners() {
        }
    }

    static final Action cutAction = new TransferAction("cut");
    static final Action copyAction = new TransferAction("copy");
    static final Action pasteAction = new TransferAction("paste");

    static class TransferAction extends AbstractAction implements UIResource {

        TransferAction(String name) {
            super(name);
        }

        public void actionPerformed(ActionEvent e) {
            Object src = e.getSource();
            if (!(src instanceof JComponent)) {
                return;
            }
            JComponent c = (JComponent) src;
            TransferHandler th = c.getTransferHandler();
            if (th == null) {
                return;
            }
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            String name = (String) getValue(Action.NAME);
            try {
                if ("cut".equals(name)) {
                    th.exportToClipboard(c, clipboard, MOVE);
                } else if ("copy".equals(name)) {
                    th.exportToClipboard(c, clipboard, COPY);
                } else if ("paste".equals(name)) {
                    Transferable trans = clipboard.getContents(null);
                    if (trans != null) {
                        th.importData(new TransferSupport(c, trans));
                    }
                }
            } catch (IllegalStateException ise) {
                // The clipboard is unavailable (the app is in the background); desktop
                // only beeps here, and there is no beep to give.
            }
        }
    }
}
