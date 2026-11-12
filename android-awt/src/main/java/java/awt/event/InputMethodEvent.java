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
package java.awt.event;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.font.TextHitInfo;
import java.text.AttributedCharacterIterator;
import java.text.CharacterIterator;

/** Composed and committed text from an input method, with the JDK's argument checks. */
public class InputMethodEvent extends AWTEvent {
    private static final long serialVersionUID = 4727190874778922661L;

    public static final int INPUT_METHOD_FIRST = 1100;
    public static final int INPUT_METHOD_TEXT_CHANGED = INPUT_METHOD_FIRST;
    public static final int CARET_POSITION_CHANGED = INPUT_METHOD_FIRST + 1;
    public static final int INPUT_METHOD_LAST = INPUT_METHOD_FIRST + 1;

    long when;
    private transient AttributedCharacterIterator text;
    private transient int committedCharacterCount;
    private transient TextHitInfo caret;
    private transient TextHitInfo visiblePosition;

    public InputMethodEvent(Component source, int id, long when, AttributedCharacterIterator text,
                            int committedCharacterCount, TextHitInfo caret, TextHitInfo visiblePosition) {
        super(source, id);
        if (id < INPUT_METHOD_FIRST || id > INPUT_METHOD_LAST) {
            throw new IllegalArgumentException("id outside of valid range");
        }
        if (id == CARET_POSITION_CHANGED && text != null) {
            throw new IllegalArgumentException("text must be null for CARET_POSITION_CHANGED");
        }
        this.when = when;
        this.text = text;
        int textLength = 0;
        if (text != null) {
            textLength = text.getEndIndex() - text.getBeginIndex();
        }
        if (committedCharacterCount < 0 || committedCharacterCount > textLength) {
            throw new IllegalArgumentException("committedCharacterCount outside of valid range");
        }
        this.committedCharacterCount = committedCharacterCount;
        this.caret = caret;
        this.visiblePosition = visiblePosition;
    }

    public InputMethodEvent(Component source, int id, AttributedCharacterIterator text,
                            int committedCharacterCount, TextHitInfo caret, TextHitInfo visiblePosition) {
        this(source, id, EventQueue.getMostRecentEventTime(), text, committedCharacterCount, caret, visiblePosition);
    }

    public InputMethodEvent(Component source, int id, TextHitInfo caret, TextHitInfo visiblePosition) {
        this(source, id, EventQueue.getMostRecentEventTime(), null, 0, caret, visiblePosition);
    }

    public AttributedCharacterIterator getText() {
        return text;
    }

    public int getCommittedCharacterCount() {
        return committedCharacterCount;
    }

    public TextHitInfo getCaret() {
        return caret;
    }

    public TextHitInfo getVisiblePosition() {
        return visiblePosition;
    }

    @Override
    public void consume() {
        consumed = true;
    }

    @Override
    public boolean isConsumed() {
        return consumed;
    }

    public long getWhen() {
        return when;
    }

    @Override
    public String paramString() {
        String typeStr;
        switch (id) {
            case INPUT_METHOD_TEXT_CHANGED:
                typeStr = "INPUT_METHOD_TEXT_CHANGED";
                break;
            case CARET_POSITION_CHANGED:
                typeStr = "CARET_POSITION_CHANGED";
                break;
            default:
                typeStr = "unknown type";
        }
        String textString;
        if (text == null) {
            textString = "no text";
        } else {
            StringBuilder textBuffer = new StringBuilder("\"");
            int committed = committedCharacterCount;
            char c = text.first();
            while (committed-- > 0) {
                textBuffer.append(c);
                c = text.next();
            }
            textBuffer.append("\" + \"");
            while (c != CharacterIterator.DONE) {
                textBuffer.append(c);
                c = text.next();
            }
            textBuffer.append("\"");
            textString = textBuffer.toString();
        }
        String countString = committedCharacterCount + " characters committed";
        String caretString = caret == null ? "no caret" : "caret: " + caret.toString();
        String visiblePositionString = visiblePosition == null
            ? "no visible position" : "visible position: " + visiblePosition.toString();
        return typeStr + ", " + textString + ", " + countString + ", " + caretString + ", " + visiblePositionString;
    }
}
