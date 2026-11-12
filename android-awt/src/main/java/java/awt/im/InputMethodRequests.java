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

import java.awt.Rectangle;
import java.awt.font.TextHitInfo;
import java.text.AttributedCharacterIterator;
import java.text.AttributedCharacterIterator.Attribute;

/**
 * InputMethodRequests defines the requests that a text editing component
 * has to handle in order to work with input methods. The component
 * can implement this interface itself or use a separate object that
 * implements it. The object implementing this interface must be returned
 * from the component's getInputMethodRequests method.
 *
 * <p>
 * The text editing component also has to provide an input method event
 * listener.
 *
 * <p>
 * The interface is designed to support one of two input user interfaces:
 * <ul>
 * <li><em>on-the-spot</em> input, where the composed text is displayed as part
 *     of the text component's text body.
 * <li><em>below-the-spot</em> input, where the composed text is displayed in
 *     a separate composition window just below the insertion point where
 *     the text will be inserted when it is committed. Note that, if text is
 *     selected within the component's text body, this text will be replaced by
 *     the committed text upon commitment; therefore it is not considered part
 *     of the context that the text is input into.
 * </ul>
 *
 * @see java.awt.Component#getInputMethodRequests
 * @see java.awt.event.InputMethodListener
 *
 * @author JavaSoft Asia/Pacific
 * @since 1.2
 */

public interface InputMethodRequests {

    /**
     * Gets the location of a specified offset in the current composed text,
     * or of the selection in committed text, in screen coordinates.
     *
     * @param offset the offset within the composed text, if there is composed
     * text; null otherwise
     * @return a rectangle representing the screen location of the offset
     */
    Rectangle getTextLocation(TextHitInfo offset);

    /**
     * Gets the offset within the composed text for the specified absolute x
     * and y coordinates on the screen, or null if the location is outside
     * the composed text.
     *
     * @param x the absolute x coordinate on screen
     * @param y the absolute y coordinate on screen
     * @return a text hit info describing the offset in the composed text.
     */
    TextHitInfo getLocationOffset(int x, int y);

    /**
     * Gets the offset of the insert position in the committed text contained
     * in the text editing component. This is the offset at which characters
     * entered through an input method are inserted. Usually, this is the
     * caret position. The committed text is the text of the component
     * without any composed text.
     *
     * @return the offset of the insert position
     */
    int getInsertPositionOffset();

    /**
     * Gets an iterator providing access to the entire text and attributes
     * contained in the text editing component except for uncommitted
     * text. Uncommitted (composed) text should be ignored for index
     * calculations and should not be made accessible through the iterator.
     *
     * @param beginIndex the index of the first character
     * @param endIndex the index of the character following the last character
     * @param attributes a list of attributes that the input method is
     * interested in
     * @return an iterator providing access to the text and its attributes
     */
    AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                                                 Attribute[] attributes);

    /**
     * Gets the length of the entire text contained in the text
     * editing component except for uncommitted (composed) text.
     *
     * @return the length of the text except for uncommitted text
     */
    int getCommittedTextLength();

    /**
     * Gets the latest committed text from the text editing component and
     * removes it from the component's text body.
     * This is used for the "Undo Commit" feature in some input methods, where
     * the committed text reverts to its previous composed state. The composed
     * text will be sent to the component using an InputMethodEvent.
     *
     * @param attributes a list of attributes that the input method is
     * interested in
     * @return the latest committed text, or null when the "Undo Commit"
     * feature is not supported
     */
    AttributedCharacterIterator cancelLatestCommittedText(Attribute[] attributes);

    /**
     * Gets the currently selected text from the text editing component.
     * This may be used for a variety of purposes.
     * One of them is the "Reconvert" feature in some input methods.
     *
     * @param attributes a list of attributes that the input method is
     * interested in
     * @return the currently selected text
     */
    AttributedCharacterIterator getSelectedText(Attribute[] attributes);
}
