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
package javax.swing.plaf.basic;

import java.awt.event.MouseEvent;

/**
 * The JDK's drag gesture recognizer for the list, table and tree delegates.
 * The JDK arms a drag only when the pressed component has a
 * {@code TransferHandler} offering a source action for the modifiers; the
 * shadow has no {@code TransferHandler}, so no press ever arms one and the
 * answers are the JDK's unarmed ones: a press is not consumed, a drag does
 * not start, and a release returns no deferred press. The delegates then
 * handle selection on press exactly as the JDK does without a handler.
 */
class DragRecognitionSupport {

    /**
     * Called before a drag starts, so the delegate can finish the
     * selection that the deferred press would have made.
     */
    public static interface BeforeDrag {
        public void dragStarting(MouseEvent me);
    }

    private DragRecognitionSupport() {
    }

    /**
     * Returns whether the press armed a drag recognition; never, with no
     * transfer handler to drag from.
     */
    public static boolean mousePressed(MouseEvent me) {
        return false;
    }

    /**
     * Returns the press that was deferred for drag recognition, or null;
     * always null, since no press is deferred.
     */
    public static MouseEvent mouseReleased(MouseEvent me) {
        return null;
    }

    /**
     * Returns whether the drag was consumed by the recognizer; never, since
     * nothing is armed.
     */
    public static boolean mouseDragged(MouseEvent me, BeforeDrag bd) {
        return false;
    }
}
