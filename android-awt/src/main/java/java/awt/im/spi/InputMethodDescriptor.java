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

import java.awt.AWTException;
import java.awt.Image;
import java.util.Locale;

/**
 * Defines methods that provide sufficient information about an input method
 * to enable selection and loading of that input method.
 * The input method itself is only loaded when it is actually used.
 *
 * @since 1.3
 */

public interface InputMethodDescriptor {

    /**
     * Returns the locales supported by the corresponding input method.
     * The locale may describe just the language, or may also include
     * country and variant information if needed.
     *
     * @return the locales supported by the corresponding input method
     * @exception AWTException if it can be determined that the input method
     * is inoperable, for example, because of incomplete installation.
     */
    Locale[] getAvailableLocales() throws AWTException;

    /**
     * Returns whether the list of available locales can change
     * at runtime.
     *
     * @return whether the list of available locales can change at runtime
     */
    boolean hasDynamicLocaleList();

    /**
     * Returns the user-visible name of the corresponding
     * input method for the given input locale in the language in which
     * the name will be displayed.
     *
     * @param inputLocale the locale for which text input is supported, or null
     * @param displayLanguage the language in which the name will be displayed
     * @return the user-visible name of the corresponding input method
     * for the given input locale in the language in which the name
     * will be displayed
     */
    String getInputMethodDisplayName(Locale inputLocale, Locale displayLanguage);

    /**
     * Returns an icon for the corresponding input method.
     * The icon may be used by a user interface for selecting input methods.
     *
     * @param inputLocale the locale for which text input is supported, or null
     * @return an icon for the corresponding input method, or null
     */
    Image getInputMethodIcon(Locale inputLocale);

    /**
     * Creates a new instance of the corresponding input method.
     *
     * @return a new instance of the corresponding input method
     * @exception Exception any exception that may occur while creating the
     * input method instance
     */
    InputMethod createInputMethod() throws Exception;
}
