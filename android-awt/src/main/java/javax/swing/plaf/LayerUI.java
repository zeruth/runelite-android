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
package javax.swing.plaf;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import javax.swing.JComponent;
import javax.swing.JLayer;

/**
 * The base class for all {@link JLayer}'s UI delegates: it paints the layer's
 * view through {@link #paint(Graphics, JComponent)}, lays it out, sizes it and
 * receives the events selected by the layer's event mask.
 *
 * @param <V> one of the super types of {@code JLayer}'s view component
 * @since 1.7
 */
public class LayerUI<V extends Component> extends ComponentUI implements Serializable {

    private static final long serialVersionUID = 1L;

    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * Paints the specified component. Subclasses override this to decorate the
     * layer; the default implementation paints the layer (and so its view).
     *
     * @param g the {@code Graphics} context in which to paint
     * @param c the component being painted
     */
    @Override
    public void paint(Graphics g, JComponent c) {
        c.paint(g);
    }

    /**
     * Processes {@code AWTEvent}s for {@code JLayer} and all its descendants,
     * dispatching to the {@code process*Event} method of the event's kind.
     *
     * @param e the event to be dispatched
     * @param l the layer this LayerUI is set to
     */
    public void eventDispatched(AWTEvent e, JLayer<? extends V> l) {
        if (e instanceof FocusEvent) {
            processFocusEvent((FocusEvent) e, l);
        } else if (e instanceof MouseEvent) {
            switch (e.getID()) {
                case MouseEvent.MOUSE_PRESSED:
                case MouseEvent.MOUSE_RELEASED:
                case MouseEvent.MOUSE_CLICKED:
                case MouseEvent.MOUSE_ENTERED:
                case MouseEvent.MOUSE_EXITED:
                    processMouseEvent((MouseEvent) e, l);
                    break;
                case MouseEvent.MOUSE_MOVED:
                case MouseEvent.MOUSE_DRAGGED:
                    processMouseMotionEvent((MouseEvent) e, l);
                    break;
                case MouseEvent.MOUSE_WHEEL:
                    processMouseWheelEvent((MouseWheelEvent) e, l);
                    break;
                default:
                    break;
            }
        } else if (e instanceof KeyEvent) {
            processKeyEvent((KeyEvent) e, l);
        } else if (e instanceof ComponentEvent) {
            processComponentEvent((ComponentEvent) e, l);
        } else if (e instanceof InputMethodEvent) {
            processInputMethodEvent((InputMethodEvent) e, l);
        } else if (e instanceof HierarchyEvent) {
            switch (e.getID()) {
                case HierarchyEvent.HIERARCHY_CHANGED:
                    processHierarchyEvent((HierarchyEvent) e, l);
                    break;
                case HierarchyEvent.ANCESTOR_MOVED:
                case HierarchyEvent.ANCESTOR_RESIZED:
                    processHierarchyBoundsEvent((HierarchyEvent) e, l);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Processes component events occurring on the {@code JLayer} or any of its
     * subcomponents. The default implementation does nothing.
     *
     * @param e the {@code ComponentEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processComponentEvent(ComponentEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes focus events. The default implementation does nothing.
     *
     * @param e the {@code FocusEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processFocusEvent(FocusEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes key events. The default implementation does nothing.
     *
     * @param e the {@code KeyEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processKeyEvent(KeyEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes mouse events. The default implementation does nothing.
     *
     * @param e the {@code MouseEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processMouseEvent(MouseEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes mouse motion events. The default implementation does nothing.
     *
     * @param e the {@code MouseEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processMouseMotionEvent(MouseEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes mouse wheel events. The default implementation does nothing.
     *
     * @param e the {@code MouseWheelEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processMouseWheelEvent(MouseWheelEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes input method events. The default implementation does nothing.
     *
     * @param e the {@code InputMethodEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processInputMethodEvent(InputMethodEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes hierarchy events. The default implementation does nothing.
     *
     * @param e the {@code HierarchyEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processHierarchyEvent(HierarchyEvent e, JLayer<? extends V> l) {
    }

    /**
     * Processes hierarchy bounds events. The default implementation does nothing.
     *
     * @param e the {@code HierarchyEvent} event to be processed
     * @param l the layer this {@code LayerUI} instance is set to
     */
    protected void processHierarchyBoundsEvent(HierarchyEvent e, JLayer<? extends V> l) {
    }

    /**
     * Invoked when {@link JLayer#updateUI()} is called by the {@code JLayer}
     * this {@code LayerUI} is set to.
     *
     * @param l the {@code JLayer} which UI is updated
     */
    public void updateUI(JLayer<? extends V> l) {
    }

    /**
     * Configures the {@code JLayer} this {@code LayerUI} is set to: the layer
     * starts listening to this delegate's property changes.
     *
     * @param c the {@code JLayer} component where this UI delegate is being installed
     */
    @Override
    public void installUI(JComponent c) {
        addPropertyChangeListener((JLayer) c);
    }

    /**
     * Reverses the configuration which was previously set in
     * {@link #installUI(JComponent)}.
     *
     * @param c the component from which this UI delegate is being removed.
     */
    @Override
    public void uninstallUI(JComponent c) {
        removePropertyChangeListener((JLayer) c);
    }

    /**
     * Adds a PropertyChangeListener to the listener list. The same listener
     * object may be added more than once, and will be called as many times as
     * it is added. If {@code listener} is {@code null}, no exception is thrown
     * and no action is taken.
     *
     * @param listener the property change listener to be added
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener from the listener list.
     *
     * @param listener the PropertyChangeListener to be removed
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * Returns an array of all the property change listeners registered on this
     * component.
     *
     * @return all of the {@code PropertyChangeListener}s or an empty array if
     *         no property change listeners are currently registered
     */
    public PropertyChangeListener[] getPropertyChangeListeners() {
        return propertyChangeSupport.getPropertyChangeListeners();
    }

    /**
     * Adds a PropertyChangeListener to the listener list for a specific property.
     *
     * @param propertyName one of the property names listed above
     * @param listener     the property change listener to be added
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a {@code PropertyChangeListener} from the listener list for a
     * specific property.
     *
     * @param propertyName a valid property name
     * @param listener     the PropertyChangeListener to be removed
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    /**
     * Returns an array of all the listeners which have been associated with the
     * named property.
     *
     * @param propertyName the name of the property
     * @return all of the {@code PropertyChangeListener}s associated with the named
     *         property or an empty array if no listeners have been added
     */
    public PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        return propertyChangeSupport.getPropertyChangeListeners(propertyName);
    }

    /**
     * Supports reporting bound property changes. No event is fired if
     * {@code oldValue} and {@code newValue} are equal and non-null.
     *
     * @param propertyName the programmatic name of the property that was changed
     * @param oldValue     the old value of the property
     * @param newValue     the new value of the property
     */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    /**
     * Notifies the {@code LayerUI} when any of its property are changed and
     * enables updating every {@code JLayer} this {@code LayerUI} instance is set to.
     *
     * @param evt the PropertyChangeEvent generated by this {@code LayerUI}
     * @param l   the {@code JLayer} this LayerUI is set to
     */
    public void applyPropertyChange(PropertyChangeEvent evt, JLayer<? extends V> l) {
    }

    /**
     * If the {@code JLayer}'s view component is not {@code null}, this returns
     * the result of the view's baseline, otherwise the default.
     *
     * @param c      {@code JLayer} to return baseline resize behavior for
     * @param width  the width to get the baseline for
     * @param height the height to get the baseline for
     * @return baseline or a value &lt; 0 indicating there is no reasonable baseline
     */
    @Override
    public int getBaseline(JComponent c, int width, int height) {
        // The shadow Component has no getBaseline; a JComponent's baseline is its
        // UI delegate's, which is what JComponent.getBaseline answers on the JDK.
        Component view = ((JLayer) c).getView();
        if (view instanceof JComponent && ((JComponent) view).getUI() != null) {
            return ((JComponent) view).getUI().getBaseline((JComponent) view, width, height);
        }
        return super.getBaseline(c, width, height);
    }

    /**
     * Causes the passed instance of {@code JLayer} to lay out its components:
     * the view and the glass pane both fill the layer.
     *
     * @param l the {@code JLayer} component where this UI delegate is being installed
     */
    public void doLayout(JLayer<? extends V> l) {
        Component view = l.getView();
        if (view != null) {
            view.setBounds(0, 0, l.getWidth(), l.getHeight());
        }
        Component glassPane = l.getGlassPane();
        if (glassPane != null) {
            glassPane.setBounds(0, 0, l.getWidth(), l.getHeight());
        }
    }

    /**
     * If the {@code JLayer}'s view component is not {@code null}, this returns
     * the result of the view's {@code getPreferredSize()} method, otherwise the
     * default implementation is used.
     *
     * @param c {@code JLayer} to return preferred size for
     * @return preferred size for the passed {@code JLayer}
     */
    @Override
    public Dimension getPreferredSize(JComponent c) {
        Component view = ((JLayer) c).getView();
        if (view != null) {
            return view.getPreferredSize();
        }
        return super.getPreferredSize(c);
    }

    /**
     * If the {@code JLayer}'s view component is not {@code null}, this returns
     * the result of the view's {@code getMinimumSize()} method, otherwise the
     * default implementation is used.
     *
     * @param c {@code JLayer} to return minimum size for
     * @return minimum size for the passed {@code JLayer}
     */
    @Override
    public Dimension getMinimumSize(JComponent c) {
        Component view = ((JLayer) c).getView();
        if (view != null) {
            return view.getMinimumSize();
        }
        return super.getMinimumSize(c);
    }

    /**
     * If the {@code JLayer}'s view component is not {@code null}, this returns
     * the result of the view's {@code getMaximumSize()} method, otherwise the
     * default implementation is used.
     *
     * @param c {@code JLayer} to return maximum size for
     * @return maximum size for the passed {@code JLayer}
     */
    @Override
    public Dimension getMaximumSize(JComponent c) {
        Component view = ((JLayer) c).getView();
        if (view != null) {
            return view.getMaximumSize();
        }
        return super.getMaximumSize(c);
    }

    /**
     * Paints the specified region in the {@code JLayer} this {@code LayerUI} is
     * set to. The shadow has no synchronous RepaintManager paint, so the region
     * is repainted on the host's next frame.
     *
     * @param x the x value of the region to be painted
     * @param y the y value of the region to be painted
     * @param width the width of the region to be painted
     * @param height the height of the region to be painted
     * @param l the layer to paint
     * @since 1.7
     */
    public void paintImmediately(int x, int y, int width, int height, JLayer<? extends V> l) {
        l.repaint(x, y, width, height);
    }
}
