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

package java.awt.im;

import java.awt.AWTEvent;
import java.awt.Component;
import java.lang.Character.Subset;
import java.util.Locale;

/**
 * Provides methods to control text input facilities such as input
 * methods and keyboard layouts.
 * Two methods handle both input methods and keyboard layouts: selectInputMethod
 * lets a client component select an input method or keyboard layout by locale,
 * getLocale lets a client component obtain the locale of the current input method
 * or keyboard layout.
 * The other methods more specifically support interaction with input methods:
 * They let client components control the behavior of input methods, and
 * dispatch events from the client component to the input method.
 *
 * <p>
 * By default, one InputContext instance is created per Window instance,
 * and this input context is shared by all components within the window's
 * container hierarchy. However, this means that only one text input
 * operation is possible at any one time within a window, and that the
 * text needs to be committed when moving the focus from one text component
 * to another. If this is not desired, text components can create their
 * own input context instances.
 *
 * <p>
 * The methods of this base class are the empty implementations the JDK
 * documents; {@link #getInstance} returns the Android-backed subclass, where
 * the composition itself belongs to the system soft keyboard.
 *
 * @see java.awt.Component#getInputContext
 * @see java.awt.Component#enableInputMethods
 * @author JavaSoft Asia/Pacific
 * @since 1.2
 */

public class InputContext {

    /**
     * Constructs an InputContext.
     * This method is protected so clients cannot instantiate
     * InputContext directly. Input contexts are obtained by
     * calling {@link #getInstance}.
     */
    protected InputContext() {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Returns a new InputContext instance.
     */
    public static InputContext getInstance() {
        return new net.runelite.awt.impl.HostInputContext();
    }

    /**
     * Attempts to select an input method or keyboard layout that
     * supports the given locale, and returns a value indicating whether such
     * an input method or keyboard layout has been successfully selected.
     *
     * @param locale The desired new locale.
     * @return true if the input method or keyboard layout that's active after
     *         this call supports the desired locale.
     * @exception NullPointerException if <code>locale</code> is null
     */
    public boolean selectInputMethod(Locale locale) {
        // real implementation is in net.runelite.awt.impl.HostInputContext
        return false;
    }

    /**
     * Returns the current locale of the current input method or keyboard
     * layout.
     * Returns null if the input context does not have a current input method
     * or keyboard layout or if the current input method's
     * {@link java.awt.im.spi.InputMethod#getLocale()} method returns null.
     *
     * @return the current locale of the current input method or keyboard layout
     * @since 1.3
     */
    public Locale getLocale() {
        // real implementation is in net.runelite.awt.impl.HostInputContext
        return null;
    }

    /**
     * Sets the subsets of the Unicode character set that input methods of this input
     * context should be allowed to input. Null may be passed in to
     * indicate that all characters are allowed.
     *
     * @param subsets The subsets of the Unicode character set from which
     * characters may be input
     */
    public void setCharacterSubsets(Subset[] subsets) {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Enables or disables the current input method for composition,
     * depending on the value of the parameter <code>enable</code>.
     *
     * @param enable whether to enable the current input method for composition
     * @throws UnsupportedOperationException if there is no current input
     * method available or the current input method does not support
     * the enabling/disabling operation
     * @since 1.3
     */
    public void setCompositionEnabled(boolean enable) {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Determines whether the current input method is enabled for composition.
     *
     * @return <code>true</code> if the current input method is enabled for
     * composition; <code>false</code> otherwise
     * @throws UnsupportedOperationException if there is no current input
     * method available or the current input method does not support
     * checking whether it is enabled for composition
     * @since 1.3
     */
    public boolean isCompositionEnabled() {
        // real implementation is in net.runelite.awt.impl.HostInputContext
        return false;
    }

    /**
     * Asks the current input method to reconvert text in the
     * current client component.
     *
     * @throws UnsupportedOperationException if there is no current input
     * method available or the current input method does not support
     * the reconversion operation.
     * @since 1.3
     */
    public void reconvert() {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Dispatches an event to the active input method. Called by AWT.
     * If no input method is available, then the event will never be consumed.
     *
     * @param event The event
     * @exception NullPointerException if <code>event</code> is null
     */
    public void dispatchEvent(AWTEvent event) {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Notifies the input context that a client component has been
     * removed from its containment hierarchy, or that input method
     * support has been disabled for the component.
     *
     * @param client Client component
     * @exception NullPointerException if <code>client</code> is null
     */
    public void removeNotify(Component client) {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Ends any input composition that may currently be going on in this
     * context. Depending on the platform and possibly user preferences,
     * this may commit or delete uncommitted text. Any changes to the text
     * are communicated to the active component using an input method event.
     * If no input methods are available, then this method has no effect.
     */
    public void endComposition() {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Releases the resources used by this input context.
     * Called by AWT for the default input context of each Window.
     * If no input methods are available, then this method
     * has no effect.
     */
    public void dispose() {
        // real implementation is in net.runelite.awt.impl.HostInputContext
    }

    /**
     * Returns a control object from the current input method, or null. A
     * control object provides methods that control the behavior of the
     * input method or obtain information from the input method.
     *
     * @return A control object from the current input method, or null.
     */
    public Object getInputMethodControlObject() {
        // real implementation is in net.runelite.awt.impl.HostInputContext
        return null;
    }

}
