/*
 * Copyright (c) 2000, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Container;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.FocusTraversalPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

/**
 * A FocusTraversalPolicy that determines traversal order by sorting the
 * Components of a focus traversal cycle based on a given Comparator. Portions
 * of the Component hierarchy that are not visible and displayable will not be
 * included.
 * <p>
 * By default, SortingFocusTraversalPolicy implicitly transfers focus down-
 * cycle. That is, during normal focus traversal, the Component
 * traversed after a focus cycle root will be the focus-cycle-root's default
 * Component to focus. This behavior can be disabled using the
 * <code>setImplicitDownCycleTraversal</code> method.
 * <p>
 * Android: the JDK's superclass is InternalFrameFocusTraversalPolicy, which needs
 * JInternalFrame; the shadow has neither yet, so this extends FocusTraversalPolicy.
 *
 * @author David Mendenhall
 *
 * @see java.util.Comparator
 * @since 1.4
 */
public class SortingFocusTraversalPolicy extends FocusTraversalPolicy {
    private Comparator<? super Component> comparator;
    private boolean implicitDownCycleTraversal = true;

    /*
     * We suppose to use getFocusTraversalCycle & getComponentIndex methods in order
     * to divide the policy into two parts:
     * 1) Making the focus traversal cycle.
     * 2) Traversing the cycle.
     * The 1st point assumes producing a list of components representing the focus
     * traversal cycle. The two methods mentioned above should implement this logic.
     * The 2nd point assumes implementing the common concepts of operating on the
     * cycle: traversing back and forth, retrieving the initial/default/first/last
     * component. These concepts are described in the AWT Focus Spec and they are
     * applied to the FocusTraversalPolicy in general.
     * Thus, a descendant of this policy may wish to not reimplement the logic of
     * the 2nd point but just override the implementation of the 1st one.
     * A striking example of such a descendant is the javax.swing.plaf.synth.SynthFocusTraversalPolicy.
     */
    private transient Container cachedRoot;
    private transient List<Component> cachedCycle;

    // Delegate most of our fitness test to Default so that we only have to
    // code the algorithm once.
    private static final SwingDefaultFocusTraversalPolicy fitnessTestPolicy = new SwingDefaultFocusTraversalPolicy();

    private static final int FORWARD_TRAVERSAL = 0;
    private static final int BACKWARD_TRAVERSAL = 1;

    /**
     * Constructs a SortingFocusTraversalPolicy without a Comparator.
     * Subclasses must set the Comparator using <code>setComparator</code>
     * before installing this FocusTraversalPolicy on a focus cycle root or
     * KeyboardFocusManager.
     */
    protected SortingFocusTraversalPolicy() {
    }

    /**
     * Constructs a SortingFocusTraversalPolicy with the specified Comparator.
     */
    public SortingFocusTraversalPolicy(Comparator<? super Component> comparator) {
        this.comparator = comparator;
    }

    private List<Component> getFocusTraversalCycle(Container aContainer) {
        List<Component> cycle = new ArrayList<Component>();
        enumerateAndSortCycle(aContainer, cycle);
        return cycle;
    }

    private int getComponentIndex(List<Component> cycle, Component aComponent) {
        int index;
        try {
            index = Collections.binarySearch(cycle, aComponent, comparator);
        } catch (ClassCastException e) {
            return -1;
        }
        if (index < 0) {
            // Fix for 5070991.
            // A workaround for a transitivity problem caused by ROW_TOLERANCE,
            // because of that the component may be missed in the binary search.
            // Try to search it again directly.
            index = cycle.indexOf(aComponent);
        }
        return index;
    }

    private void enumerateAndSortCycle(Container focusCycleRoot, List<Component> cycle) {
        if (focusCycleRoot.isShowing()) {
            enumerateCycle(focusCycleRoot, cycle);
            legacySort(cycle, comparator);
        }
    }

    /**
     * The JDK sorts with the legacy merge sort: the layout comparator is not transitive
     * (ROW_TOLERANCE), which TimSort may reject, and the merge sort's result is what users
     * see as the tab order.
     */
    private static void legacySort(List<Component> l, Comparator<? super Component> c) {
        if (c != null && l.size() > 1) {
            Component[] a = l.toArray(new Component[l.size()]);
            mergeSort(a.clone(), a, 0, a.length, 0, c);
            ListIterator<Component> i = l.listIterator();
            for (Component e : a) {
                i.next();
                i.set(e);
            }
        }
    }

    private static void mergeSort(Component[] src, Component[] dest, int low, int high, int off,
                                  Comparator<? super Component> c) {
        int length = high - low;

        // Insertion sort on smallest arrays
        if (length < 7) {
            for (int i = low; i < high; i++) {
                for (int j = i; j > low && c.compare(dest[j - 1], dest[j]) > 0; j--) {
                    Component t = dest[j];
                    dest[j] = dest[j - 1];
                    dest[j - 1] = t;
                }
            }
            return;
        }

        // Recursively sort halves of dest into src
        int destLow = low;
        int destHigh = high;
        low += off;
        high += off;
        int mid = (low + high) >>> 1;
        mergeSort(dest, src, low, mid, -off, c);
        mergeSort(dest, src, mid, high, -off, c);

        // If list is already sorted, just copy from src to dest.  This is an
        // optimization that results in faster sorts for nearly ordered lists.
        if (c.compare(src[mid - 1], src[mid]) <= 0) {
            System.arraycopy(src, low, dest, destLow, length);
            return;
        }

        // Merge sorted halves (now in src) into dest
        for (int i = destLow, p = low, q = mid; i < destHigh; i++) {
            if (q >= high || p < mid && c.compare(src[p], src[q]) <= 0) {
                dest[i] = src[p++];
            } else {
                dest[i] = src[q++];
            }
        }
    }

    private void enumerateCycle(Container container, List<Component> cycle) {
        if (!(container.isVisible() && container.isDisplayable())) {
            return;
        }

        cycle.add(container);

        Component[] components = container.getComponents();
        for (Component comp : components) {
            if (comp instanceof Container) {
                Container cont = (Container) comp;

                if (!cont.isFocusCycleRoot() &&
                    !cont.isFocusTraversalPolicyProvider() &&
                    !((cont instanceof JComponent) && ((JComponent) cont).isManagingFocus())) {
                    enumerateCycle(cont, cycle);
                    continue;
                }
            }
            cycle.add(comp);
        }
    }

    Container getTopmostProvider(Container focusCycleRoot, Component aComponent) {
        Container aCont = aComponent.getParent();
        Container ftp = null;
        while (aCont != focusCycleRoot && aCont != null) {
            if (aCont.isFocusTraversalPolicyProvider()) {
                ftp = aCont;
            }
            aCont = aCont.getParent();
        }
        if (aCont == null) {
            return null;
        }
        return ftp;
    }

    /*
     * Checks if a new focus cycle takes place and returns a Component to traverse focus to.
     * @param comp a possible focus cycle root or policy provider
     * @param traversalDirection the direction of the traversal
     * @return a Component to traverse focus to if {@code comp} is a root or provider
     *         and implicit down-cycle is set, otherwise {@code null}
     */
    private Component getComponentDownCycle(Component comp, int traversalDirection) {
        Component retComp = null;

        if (comp instanceof Container) {
            Container cont = (Container) comp;

            if (cont.isFocusCycleRoot()) {
                if (getImplicitDownCycleTraversal()) {
                    retComp = cont.getFocusTraversalPolicy().getDefaultComponent(cont);
                } else {
                    return null;
                }
            } else if (cont.isFocusTraversalPolicyProvider()) {
                retComp = (traversalDirection == FORWARD_TRAVERSAL ?
                           cont.getFocusTraversalPolicy().getDefaultComponent(cont) :
                           cont.getFocusTraversalPolicy().getLastComponent(cont));
            }
        }
        return retComp;
    }

    private static void checkArguments(Container aContainer, Component aComponent) {
        if (aContainer == null || aComponent == null) {
            throw new IllegalArgumentException("aContainer and aComponent cannot be null");
        }
        if (!aContainer.isFocusTraversalPolicyProvider() && !aContainer.isFocusCycleRoot()) {
            throw new IllegalArgumentException("aContainer should be focus cycle root or focus traversal policy provider");
        } else if (aContainer.isFocusCycleRoot() && !aComponent.isFocusCycleRoot(aContainer)) {
            throw new IllegalArgumentException("aContainer is not a focus cycle root of aComponent");
        }
    }

    /**
     * Returns the Component that should receive the focus after aComponent.
     * aContainer must be a focus cycle root of aComponent or a focus traversal policy provider.
     * <p>
     * By default, SortingFocusTraversalPolicy implicitly transfers focus down-
     * cycle. That is, during normal focus traversal, the Component
     * traversed after a focus cycle root will be the focus-cycle-root's
     * default Component to focus.
     *
     * @throws IllegalArgumentException if aContainer is not a focus cycle
     *         root of aComponent or a focus traversal policy provider, or if either aContainer or
     *         aComponent is null
     */
    public Component getComponentAfter(Container aContainer, Component aComponent) {
        checkArguments(aContainer, aComponent);

        // Before all the ckecks below we first see if it's an FTP provider or a focus cycle root.
        // If it's the case just go down cycle (if it's set to "implicit").
        Component comp = getComponentDownCycle(aComponent, FORWARD_TRAVERSAL);
        if (comp != null) {
            return comp;
        }

        // See if the component is inside of policy provider.
        Container provider = getTopmostProvider(aContainer, aComponent);
        if (provider != null) {
            // FTP knows how to find component after the given. We don't.
            FocusTraversalPolicy policy = provider.getFocusTraversalPolicy();
            Component afterComp = policy.getComponentAfter(provider, aComponent);

            // Null result means that we overstepped the limit of the FTP's cycle.
            // In that case we must quit the cycle, otherwise return the component found.
            if (afterComp != null) {
                return afterComp;
            }
            aComponent = provider;
        }

        List<Component> cycle = getFocusTraversalCycle(aContainer);

        int index = getComponentIndex(cycle, aComponent);

        if (index < 0) {
            return getFirstComponent(aContainer);
        }

        for (index++; index < cycle.size(); index++) {
            comp = cycle.get(index);
            if (accept(comp)) {
                return comp;
            } else if ((comp = getComponentDownCycle(comp, FORWARD_TRAVERSAL)) != null) {
                return comp;
            }
        }

        if (aContainer.isFocusCycleRoot()) {
            this.cachedRoot = aContainer;
            this.cachedCycle = cycle;

            comp = getFirstComponent(aContainer);

            this.cachedRoot = null;
            this.cachedCycle = null;

            return comp;
        }
        return null;
    }

    /**
     * Returns the Component that should receive the focus before aComponent.
     * aContainer must be a focus cycle root of aComponent or a focus traversal policy provider.
     *
     * @throws IllegalArgumentException if aContainer is not a focus cycle
     *         root of aComponent or a focus traversal policy provider, or if either aContainer or
     *         aComponent is null
     */
    public Component getComponentBefore(Container aContainer, Component aComponent) {
        checkArguments(aContainer, aComponent);

        // See if the component is inside of policy provider.
        Container provider = getTopmostProvider(aContainer, aComponent);
        if (provider != null) {
            // FTP knows how to find component after the given. We don't.
            FocusTraversalPolicy policy = provider.getFocusTraversalPolicy();
            Component beforeComp = policy.getComponentBefore(provider, aComponent);

            // Null result means that we overstepped the limit of the FTP's cycle.
            // In that case we must quit the cycle, otherwise return the component found.
            if (beforeComp != null) {
                return beforeComp;
            }
            aComponent = provider;

            // If the provider is traversable it's returned.
            if (accept(aComponent)) {
                return aComponent;
            }
        }

        List<Component> cycle = getFocusTraversalCycle(aContainer);

        int index = getComponentIndex(cycle, aComponent);

        if (index < 0) {
            return getLastComponent(aContainer);
        }

        Component comp;
        Component tryComp;

        for (index--; index >= 0; index--) {
            comp = cycle.get(index);
            if (comp != aContainer && (tryComp = getComponentDownCycle(comp, BACKWARD_TRAVERSAL)) != null) {
                return tryComp;
            } else if (accept(comp)) {
                return comp;
            }
        }

        if (aContainer.isFocusCycleRoot()) {
            this.cachedRoot = aContainer;
            this.cachedCycle = cycle;

            comp = getLastComponent(aContainer);

            this.cachedRoot = null;
            this.cachedCycle = null;

            return comp;
        }
        return null;
    }

    /**
     * Returns the first Component in the traversal cycle. This method is used
     * to determine the next Component to focus when traversal wraps in the
     * forward direction.
     *
     * @throws IllegalArgumentException if aContainer is null
     */
    public Component getFirstComponent(Container aContainer) {
        List<Component> cycle;

        if (aContainer == null) {
            throw new IllegalArgumentException("aContainer cannot be null");
        }

        if (this.cachedRoot == aContainer) {
            cycle = this.cachedCycle;
        } else {
            cycle = getFocusTraversalCycle(aContainer);
        }

        if (cycle.size() == 0) {
            return null;
        }

        for (Component comp : cycle) {
            if (accept(comp)) {
                return comp;
            } else if (comp != aContainer &&
                       (comp = getComponentDownCycle(comp, FORWARD_TRAVERSAL)) != null) {
                return comp;
            }
        }
        return null;
    }

    /**
     * Returns the last Component in the traversal cycle. This method is used
     * to determine the next Component to focus when traversal wraps in the
     * reverse direction.
     *
     * @throws IllegalArgumentException if aContainer is null
     */
    public Component getLastComponent(Container aContainer) {
        List<Component> cycle;

        if (aContainer == null) {
            throw new IllegalArgumentException("aContainer cannot be null");
        }

        if (this.cachedRoot == aContainer) {
            cycle = this.cachedCycle;
        } else {
            cycle = getFocusTraversalCycle(aContainer);
        }

        if (cycle.size() == 0) {
            return null;
        }

        for (int i = cycle.size() - 1; i >= 0; i--) {
            Component comp = cycle.get(i);
            if (accept(comp)) {
                return comp;
            } else if (comp instanceof Container && comp != aContainer) {
                Container cont = (Container) comp;
                if (cont.isFocusTraversalPolicyProvider()) {
                    Component retComp = cont.getFocusTraversalPolicy().getLastComponent(cont);
                    if (retComp != null) {
                        return retComp;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the default Component to focus. This Component will be the first
     * to receive focus when traversing down into a new focus traversal cycle
     * rooted at aContainer. The default implementation of this method
     * returns the same Component as <code>getFirstComponent</code>.
     *
     * @throws IllegalArgumentException if aContainer is null
     */
    public Component getDefaultComponent(Container aContainer) {
        return getFirstComponent(aContainer);
    }

    /**
     * Sets whether this SortingFocusTraversalPolicy transfers focus down-cycle
     * implicitly.
     */
    public void setImplicitDownCycleTraversal(boolean implicitDownCycleTraversal) {
        this.implicitDownCycleTraversal = implicitDownCycleTraversal;
    }

    /**
     * Returns whether this SortingFocusTraversalPolicy transfers focus down-
     * cycle implicitly.
     */
    public boolean getImplicitDownCycleTraversal() {
        return implicitDownCycleTraversal;
    }

    /**
     * Sets the Comparator which will be used to sort the Components in a
     * focus traversal cycle.
     */
    protected void setComparator(Comparator<? super Component> comparator) {
        this.comparator = comparator;
    }

    /**
     * Returns the Comparator which will be used to sort the Components in a
     * focus traversal cycle.
     */
    protected Comparator<? super Component> getComparator() {
        return comparator;
    }

    /**
     * Determines whether a Component is an acceptable choice as the new
     * focus owner. By default, this method will accept a Component if and
     * only if it is visible, displayable, enabled, and focusable.
     */
    protected boolean accept(Component aComponent) {
        return fitnessTestPolicy.accept(aComponent);
    }
}

// Create our own subclass and change accept to public so that we can call
// accept.
@SuppressWarnings("serial")
class SwingDefaultFocusTraversalPolicy extends DefaultFocusTraversalPolicy {
    public boolean accept(Component aComponent) {
        return super.accept(aComponent);
    }
}
