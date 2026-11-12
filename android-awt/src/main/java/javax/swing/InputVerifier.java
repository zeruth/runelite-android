/*
 * Copyright (c) 1999, 2017, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The purpose of this class is to help clients support smooth focus
 * navigation through GUIs with text fields. Such GUIs often need
 * to ensure that the text entered by the user is valid (for example,
 * that it's in the proper format) before allowing the user to navigate
 * out of the text field. To do this, clients create a subclass of
 * <code>InputVerifier</code> and, using <code>JComponent</code>'s
 * <code>setInputVerifier</code> method,
 * attach an instance of their subclass to the <code>JComponent</code> whose input they
 * want to validate. Before focus is transfered to another Swing component
 * that requests it, the input verifier's <code>shouldYieldFocus</code> method is
 * called.  Focus is transfered only if that method returns <code>true</code>.
 *
 * @since 1.3
 */
public abstract class InputVerifier {

    /**
     * Checks whether the JComponent's input is valid. This method should
     * have no side effects. It returns a boolean indicating the status
     * of the argument's input.
     *
     * @param input the JComponent to verify
     * @return <code>true</code> when valid, <code>false</code> when invalid
     */
    public abstract boolean verify(JComponent input);

    /**
     * Calls <code>verify(input)</code> to ensure that the input is valid.
     * This method can have side effects. In particular, this method
     * is called when the user attempts to advance focus out of the
     * argument component into another Swing component in this window.
     * If this method returns <code>true</code>, then the focus is transfered
     * normally; if it returns <code>false</code>, then the focus remains in
     * the argument component.
     *
     * @param input the JComponent to verify
     * @return <code>true</code> when valid, <code>false</code> when invalid
     * @deprecated use {@link #shouldYieldFocus(JComponent, JComponent)} instead.
     */
    @Deprecated
    public boolean shouldYieldFocus(JComponent input) {
        return verify(input);
    }

    /**
     * Checks whether the target JComponent that will be receiving the focus
     * is ready to accept it. This method should be over-ridden only if it is
     * necessary to validate the target of a focus transfer.
     * This method has no side effects.
     *
     * @param target the JComponent to verify
     * @return <code>true</code> when valid, <code>false</code> when invalid
     * @since 9
     */
    // JDK 9+ (9)
    public boolean verifyTarget(JComponent target) {
        return true;
    }

    /**
     * Is called by Swing if this {@code InputVerifier} is assigned to the
     * {@code source} Swing component to check whether the requested focus
     * transfer from the {@code source} to {@code target} is allowed.
     * The default implementation calls {@link #shouldYieldFocus(JComponent)}
     * on the source and {@link #verifyTarget(JComponent)} on the target.
     *
     * @param source the source JComponent of the focus transfer
     * @param target the target JComponent of the focus transfer
     * @return <code>true</code> when valid, <code>false</code> when invalid
     * @since 9
     */
    // JDK 9+ (9)
    @SuppressWarnings("deprecation")
    public boolean shouldYieldFocus(JComponent source, JComponent target) {
        return shouldYieldFocus(source) && verifyTarget(target);
    }
}
