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
package java.awt;

import java.util.ArrayList;
import java.util.List;

/**
 * A FocusTraversalPolicy that determines traversal order based on the order
 * of child Components in a Container: a pre-order walk of the hierarchy that
 * does not descend into nested focus cycle roots or focus traversal policy
 * providers, but (with implicit down-cycle traversal) enters them through
 * their own policy.
 *
 * <p>Written only against public Component/Container API, so the same class
 * runs over the shadow tree on Android and over the JDK's own Component on
 * the host contracts.
 *
 * @see DefaultFocusTraversalPolicy
 * @since 1.4
 */
public class ContainerOrderFocusTraversalPolicy extends FocusTraversalPolicy
    implements java.io.Serializable
{
    private static final long serialVersionUID = 486933713763926351L;

    private static final int FORWARD_TRAVERSAL = 0;
    private static final int BACKWARD_TRAVERSAL = 1;

    private boolean implicitDownCycleTraversal = true;

    // Lets getComponentAfter/Before wrap to the first/last component without
    // enumerating the cycle a second time.
    private transient Container cachedRoot;
    private transient List<Component> cachedCycle;

    private List<Component> getFocusTraversalCycle(Container aContainer) {
        List<Component> cycle = new ArrayList<Component>();
        enumerateCycle(aContainer, cycle);
        return cycle;
    }

    private void enumerateCycle(Container container, List<Component> cycle) {
        if (!(container.isVisible() && container.isDisplayable())) {
            return;
        }
        cycle.add(container);
        for (Component comp : container.getComponents()) {
            if (comp instanceof Container) {
                Container cont = (Container) comp;
                if (!cont.isFocusCycleRoot() && !cont.isFocusTraversalPolicyProvider()) {
                    enumerateCycle(cont, cycle);
                    continue;
                }
            }
            cycle.add(comp);
        }
    }

    private Container getTopmostProvider(Container focusCycleRoot, Component aComponent) {
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
                retComp = (traversalDirection == FORWARD_TRAVERSAL
                    ? cont.getFocusTraversalPolicy().getDefaultComponent(cont)
                    : cont.getFocusTraversalPolicy().getLastComponent(cont));
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
     *
     * @throws IllegalArgumentException if aContainer is not a focus cycle
     *         root of aComponent or a focus traversal policy provider, or if
     *         either argument is null
     */
    public Component getComponentAfter(Container aContainer, Component aComponent) {
        checkArguments(aContainer, aComponent);
        synchronized (aContainer.getTreeLock()) {
            if (!(aContainer.isVisible() && aContainer.isDisplayable())) {
                return null;
            }
            // A cycle root or provider is entered before anything else is tried.
            Component comp = getComponentDownCycle(aComponent, FORWARD_TRAVERSAL);
            if (comp != null) {
                return comp;
            }
            Container provider = getTopmostProvider(aContainer, aComponent);
            if (provider != null) {
                // The provider's policy knows its own order; null means its cycle is exhausted.
                Component afterComp = provider.getFocusTraversalPolicy().getComponentAfter(provider, aComponent);
                if (afterComp != null) {
                    return afterComp;
                }
                aComponent = provider;
            }
            List<Component> cycle = getFocusTraversalCycle(aContainer);
            int index = cycle.indexOf(aComponent);
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
        }
        return null;
    }

    /**
     * Returns the Component that should receive the focus before aComponent.
     *
     * @throws IllegalArgumentException if aContainer is not a focus cycle
     *         root of aComponent or a focus traversal policy provider, or if
     *         either argument is null
     */
    public Component getComponentBefore(Container aContainer, Component aComponent) {
        checkArguments(aContainer, aComponent);
        synchronized (aContainer.getTreeLock()) {
            if (!(aContainer.isVisible() && aContainer.isDisplayable())) {
                return null;
            }
            Container provider = getTopmostProvider(aContainer, aComponent);
            if (provider != null) {
                Component beforeComp = provider.getFocusTraversalPolicy().getComponentBefore(provider, aComponent);
                if (beforeComp != null) {
                    return beforeComp;
                }
                aComponent = provider;
                // A traversable provider is itself the component before its first child.
                if (accept(aComponent)) {
                    return aComponent;
                }
            }
            List<Component> cycle = getFocusTraversalCycle(aContainer);
            int index = cycle.indexOf(aComponent);
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
        }
        return null;
    }

    /**
     * Returns the first Component in the traversal cycle.
     *
     * @throws IllegalArgumentException if aContainer is null
     */
    public Component getFirstComponent(Container aContainer) {
        if (aContainer == null) {
            throw new IllegalArgumentException("aContainer cannot be null");
        }
        synchronized (aContainer.getTreeLock()) {
            if (!(aContainer.isVisible() && aContainer.isDisplayable())) {
                return null;
            }
            List<Component> cycle = this.cachedRoot == aContainer ? this.cachedCycle : getFocusTraversalCycle(aContainer);
            for (Component comp : cycle) {
                if (accept(comp)) {
                    return comp;
                } else if (comp != aContainer && (comp = getComponentDownCycle(comp, FORWARD_TRAVERSAL)) != null) {
                    return comp;
                }
            }
        }
        return null;
    }

    /**
     * Returns the last Component in the traversal cycle.
     *
     * @throws IllegalArgumentException if aContainer is null
     */
    public Component getLastComponent(Container aContainer) {
        if (aContainer == null) {
            throw new IllegalArgumentException("aContainer cannot be null");
        }
        synchronized (aContainer.getTreeLock()) {
            if (!(aContainer.isVisible() && aContainer.isDisplayable())) {
                return null;
            }
            List<Component> cycle = this.cachedRoot == aContainer ? this.cachedCycle : getFocusTraversalCycle(aContainer);
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
        }
        return null;
    }

    /** Returns the first Component in the traversal cycle. */
    public Component getDefaultComponent(Container aContainer) {
        return getFirstComponent(aContainer);
    }

    /**
     * Sets whether this policy enters a nested focus cycle root through its
     * default component (true, the default) or treats it as a leaf.
     */
    public void setImplicitDownCycleTraversal(boolean implicitDownCycleTraversal) {
        this.implicitDownCycleTraversal = implicitDownCycleTraversal;
    }

    /** Returns whether this policy transfers focus down-cycle implicitly. */
    public boolean getImplicitDownCycleTraversal() {
        return implicitDownCycleTraversal;
    }

    /**
     * Determines whether a Component may be a new focus owner: it must be
     * visible, displayable, enabled and focusable, and every heavyweight
     * ancestor up to its Window must be enabled (disabling a lightweight
     * Container does not disable its children).
     */
    protected boolean accept(Component aComponent) {
        if (!(aComponent.isEnabled() && aComponent.isDisplayable()
              && aComponent.isVisible() && aComponent.isFocusable())) {
            return false;
        }
        if (!(aComponent instanceof Window)) {
            for (Container enableTest = aComponent.getParent(); enableTest != null; enableTest = enableTest.getParent()) {
                if (!(enableTest.isEnabled() || enableTest.isLightweight())) {
                    return false;
                }
                if (enableTest instanceof Window) {
                    break;
                }
            }
        }
        return true;
    }
}
