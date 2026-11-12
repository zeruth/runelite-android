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

import java.awt.Window;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import javax.swing.JFrame;

/**
 * Provides methods that input methods
 * can use to communicate with their client components or to request
 * other services.  This interface is implemented by the input method
 * framework, and input methods call its methods on the instance they
 * receive through
 * {@link java.awt.im.spi.InputMethod#setInputMethodContext}.
 * There should be no other implementors or callers.
 *
 * @since 1.3
 *
 * @author JavaSoft International
 */

public interface InputMethodContext extends InputMethodRequests {

    /**
     * Creates an input method event from the arguments given
     * and dispatches it to the client component. For arguments,
     * see {@link java.awt.event.InputMethodEvent#InputMethodEvent}.
     */
    public void dispatchInputMethodEvent(int id,
                AttributedCharacterIterator text, int committedCharacterCount,
                TextHitInfo caret, TextHitInfo visiblePosition);

    /**
     * Creates a top-level window for use by the input method.
     * The intended behavior of this window is:
     * <ul>
     * <li>it floats above all document windows and dialogs
     * <li>it and all components that it contains do not receive the focus
     * <li>it has lightweight decorations, such as a reduced drag region without title
     * </ul>
     *
     * @param title the title to be displayed in the window's title bar,
     *              if there is such a title bar.
     *              A <code>null</code> value is treated as an empty string, "".
     * @param attachToInputContext whether this window should share the input context
     *              that corresponds to this input method context
     * @return a window with special characteristics for use by input methods
     */
    public Window createInputMethodWindow(String title, boolean attachToInputContext);

    /**
     * Creates a top-level Swing JFrame for use by the input method.
     *
     * @param title the title to be displayed in the window's title bar,
     *              if there is such a title bar.
     *              A <code>null</code> value is treated as an empty string, "".
     * @param attachToInputContext whether this window should share the input context
     *              that corresponds to this input method context
     * @return a JFrame with special characteristics for use by input methods
     *
     * @since 1.4
     */
    public JFrame createInputMethodJFrame(String title, boolean attachToInputContext);

    /**
     * Enables or disables notification of the current client window's
     * location and state for the specified input method.
     *
     * @param inputMethod the input method for which notifications are
     * enabled or disabled
     * @param enable true to enable, false to disable
     */
    public void enableClientWindowNotification(InputMethod inputMethod, boolean enable);
}
