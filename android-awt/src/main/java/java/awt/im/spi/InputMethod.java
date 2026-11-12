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

package java.awt.im.spi;

import java.util.Locale;
import java.awt.AWTEvent;
import java.awt.Rectangle;
import java.lang.Character.Subset;


/**
 * Defines the interface for an input method that supports complex text input.
 * Input methods traditionally support text input for languages that have
 * more characters than can be represented on a standard-size keyboard,
 * such as Chinese, Japanese, and Korean. However, they may also be used to
 * support phonetic text input for English or character reordering for Thai.
 * <p>
 * Subclasses of InputMethod can be loaded by the input method framework; they
 * can then be selected either through the API
 * ({@link java.awt.im.InputContext#selectInputMethod InputContext.selectInputMethod})
 * or the user interface (the input method selection menu). On Android the
 * system soft keyboard is the input method; this interface exists for code
 * compiled against it.
 *
 * @since 1.3
 *
 * @author JavaSoft International
 */

public interface InputMethod {

    /**
     * Sets the input method context, which is used to dispatch input method
     * events to the client component and to request information from
     * the client component.
     *
     * @param context the input method context for this input method
     * @exception NullPointerException if <code>context</code> is null
     */
    public void setInputMethodContext(InputMethodContext context);

    /**
     * Attempts to set the input locale. If the input method supports the
     * desired locale, it changes its behavior to support input for the locale
     * and returns true.
     * Otherwise, it returns false and does not change its behavior.
     *
     * @param locale locale to input
     * @return whether the specified locale is supported
     * @exception NullPointerException if <code>locale</code> is null
     */
    public boolean setLocale(Locale locale);

    /**
     * Returns the current input locale. Might return null in exceptional cases.
     *
     * @return the current input locale, or null
     */
    public Locale getLocale();

    /**
     * Sets the subsets of the Unicode character set that this input method
     * is allowed to input. Null may be passed in to indicate that all
     * characters are allowed.
     *
     * @param subsets the subsets of the Unicode character set from which
     * characters may be input
     */
    public void setCharacterSubsets(Subset[] subsets);

    /**
     * Enables or disables this input method for composition,
     * depending on the value of the parameter <code>enable</code>.
     *
     * @param enable whether to enable the input method for composition
     * @throws UnsupportedOperationException if this input method does not
     * support the enabling/disabling operation
     */
    public void setCompositionEnabled(boolean enable);

    /**
     * Determines whether this input method is enabled.
     *
     * @return <code>true</code> if this input method is enabled for
     * composition; <code>false</code> otherwise.
     * @throws UnsupportedOperationException if this input method does not
     * support checking whether it is enabled for composition
     */
    public boolean isCompositionEnabled();

    /**
     * Starts the reconversion operation. The input method obtains the
     * text to be reconverted from the current client component using the
     * {@link java.awt.im.InputMethodRequests#getSelectedText InputMethodRequests.getSelectedText}
     * method.
     *
     * @throws UnsupportedOperationException if the input method does not
     * support the reconversion operation.
     */
    public void reconvert();

    /**
     * Dispatches the event to the input method. If input method support is
     * enabled for the focussed component, incoming events of certain types
     * are dispatched to the current input method for this component before
     * they are dispatched to the component's methods or event listeners.
     *
     * @param event the event being dispatched to the input method
     * @exception NullPointerException if <code>event</code> is null
     */
    public void dispatchEvent(AWTEvent event);

    /**
     * Notifies this input method of changes in the client window
     * location or state.
     *
     * @param bounds client window's {@link
     * java.awt.Component#getBounds bounds} on the screen; or null if
     * the client window is iconified or invisible
     */
    public void notifyClientWindowChange(Rectangle bounds);

    /**
     * Activates the input method for immediate input processing.
     */
    public void activate();

    /**
     * Deactivates the input method.
     * The isTemporary argument has the same meaning as in
     * {@link java.awt.event.FocusEvent#isTemporary FocusEvent.isTemporary}.
     *
     * @param isTemporary whether the focus change is temporary
     */
    public void deactivate(boolean isTemporary);

    /**
     * Closes or hides all windows opened by this input method instance or
     * its class.
     */
    public void hideWindows();

    /**
     * Notifies the input method that a client component has been
     * removed from its containment hierarchy, or that input method
     * support has been disabled for the component.
     */
    public void removeNotify();

    /**
     * Ends any input composition that may currently be going on in this
     * context. Depending on the platform and possibly user preferences,
     * this may commit or delete uncommitted text. Any changes to the text
     * are communicated to the active component using an input method event.
     */
    public void endComposition();

    /**
     * Releases the resources used by this input method.
     * In particular, the input method should dispose windows and close files that are no
     * longer needed.
     */
    public void dispose();

    /**
     * Returns a control object from this input method, or null. A
     * control object provides methods that control the behavior of the
     * input method or obtain information from the input method.
     *
     * @return a control object from this input method, or null
     */
    public Object getControlObject();

}
