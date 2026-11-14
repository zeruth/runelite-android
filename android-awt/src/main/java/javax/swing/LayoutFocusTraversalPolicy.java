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
import java.awt.Container;
import java.io.Serializable;
import java.util.Comparator;

/**
 * A SortingFocusTraversalPolicy which sorts Components based on their size,
 * position, and orientation. Based on their size and position, Components are
 * roughly categorized into rows and columns. For a Container with horizontal
 * orientation, columns run left-to-right or right-to-left, and rows run top-
 * to-bottom. For a Container with vertical orientation, columns run top-to-
 * bottom and rows run left-to-right or right-to-left. All columns in a row
 * are fully traversed before proceeding to the next row.
 * <p>
 * Android: the shadow has no ComponentOrientation yet, so every container sorts
 * horizontal, left to right.
 *
 * @author David Mendenhall
 *
 * @see java.awt.ComponentOrientation
 * @since 1.4
 */
@SuppressWarnings("serial")
public class LayoutFocusTraversalPolicy extends SortingFocusTraversalPolicy implements Serializable {

    // Delegate most of our fitness test to Default so that we only have to
    // code the algorithm once.
    private static final SwingDefaultFocusTraversalPolicy fitnessTestPolicy = new SwingDefaultFocusTraversalPolicy();

    /**
     * Constructs a LayoutFocusTraversalPolicy.
     */
    public LayoutFocusTraversalPolicy() {
        super(new LayoutComparator());
    }

    /**
     * Constructs a LayoutFocusTraversalPolicy with the passed in
     * <code>Comparator</code>.
     */
    LayoutFocusTraversalPolicy(Comparator<? super Component> c) {
        super(c);
    }

    /**
     * Returns the Component that should receive the focus after aComponent.
     * aContainer must be a focus cycle root of aComponent or a focus traversal policy provider.
     *
     * @throws IllegalArgumentException if aContainer is not a focus cycle
     *         root of aComponent or a focus traversal policy provider, or if either aContainer or
     *         aComponent is null
     */
    public Component getComponentAfter(Container aContainer, Component aComponent) {
        if (aContainer == null || aComponent == null) {
            throw new IllegalArgumentException("aContainer and aComponent cannot be null");
        }
        return super.getComponentAfter(aContainer, aComponent);
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
        if (aContainer == null || aComponent == null) {
            throw new IllegalArgumentException("aContainer and aComponent cannot be null");
        }
        return super.getComponentBefore(aContainer, aComponent);
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
        return super.getFirstComponent(aContainer);
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
        return super.getLastComponent(aContainer);
    }

    /**
     * Determines whether the specified <code>Component</code>
     * is an acceptable choice as the new focus owner.
     * This method performs the following sequence of operations:
     * <ol>
     * <li>Checks whether <code>aComponent</code> is visible, displayable,
     *     enabled, and focusable.  If any of these properties is
     *     <code>false</code>, this method returns <code>false</code>.
     * <li>If <code>aComponent</code> is an instance of <code>JTable</code>,
     *     returns <code>true</code>.
     * <li>If <code>aComponent</code> is an instance of <code>JComboBox</code>,
     *     then returns the value of
     *     <code>aComponent.getUI().isFocusTraversable(aComponent)</code>.
     * <li>If <code>aComponent</code> is a <code>JComponent</code>
     *     with a <code>JComponent.WHEN_FOCUSED</code>
     *     <code>InputMap</code> that is neither <code>null</code>
     *     nor empty, returns <code>true</code>.
     * <li>Returns the value of
     *     <code>DefaultFocusTraversalPolicy.accept(aComponent)</code>.
     * </ol>
     */
    protected boolean accept(Component aComponent) {
        if (!super.accept(aComponent)) {
            return false;
        } else if (KeyboardManager.isInstanceOf(aComponent, "javax.swing.JTable")) {
            // JTable only has ancestor focus bindings, we thus force it
            // to be focusable by returning true here.
            return true;
        } else if (aComponent instanceof JComboBox) {
            // BasicComboBoxUI.isFocusTraversable: an editable combo hands focus to its editor.
            return !((JComboBox<?>) aComponent).isEditable();
        } else if (aComponent instanceof JComponent) {
            JComponent jComponent = (JComponent) aComponent;
            InputMap inputMap = jComponent.getInputMap(JComponent.WHEN_FOCUSED, false);
            while (inputMap != null && inputMap.size() == 0) {
                inputMap = inputMap.getParent();
            }
            if (inputMap != null) {
                return true;
            }
            // Delegate to the fitnessTestPolicy, this will test for the
            // case where the developer has overriden isFocusTraversable to
            // return true.
        }
        return fitnessTestPolicy.accept(aComponent);
    }
}
