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

import java.awt.event.KeyEvent;
import java.util.Set;

/**
 * The default KeyboardFocusManager: KeyEventDispatchers, then the target
 * component (focus traversal keys first, then its key listeners), then
 * KeyEventPostProcessors and MenuShortcut handling.
 *
 * <p>Focus changes complete synchronously here (see
 * {@link KeyboardFocusManager}), so no KeyEvent ever waits for a pending
 * focus transfer: the type-ahead methods are deliberate no-ops.
 *
 * @since 1.4
 */
public class DefaultKeyboardFocusManager extends KeyboardFocusManager {

    // Set when a traversal key's KEY_PRESSED was consumed, so its KEY_TYPED is swallowed too.
    private boolean consumeNextKeyTyped;

    public DefaultKeyboardFocusManager() {
    }

    /**
     * Dispatches KeyEvents through the dispatcher chain and then
     * {@link #dispatchKeyEvent}. Focus and window events are delivered
     * directly by the focus change itself and are not handled here.
     *
     * @return true if the event was dispatched
     */
    public boolean dispatchEvent(AWTEvent e) {
        if (!(e instanceof KeyEvent)) return false;
        KeyEvent ke = (KeyEvent) e;
        if (ke.getSource() == null) return true;
        if (dispatchToKeyEventDispatchers(ke)) return true;
        return dispatchKeyEvent(ke);
    }

    /**
     * Delivers the event to its target when the target may own focus and the
     * event is unconsumed, then runs the post-processors and
     * {@link #postProcessKeyEvent}. Always returns true: the event is fully
     * dispatched.
     */
    public boolean dispatchKeyEvent(KeyEvent e) {
        Component target = e.getComponent();
        if (target != null && target.isShowing() && target.isEnabled() && target.isVisible()
                && target.isFocusable() && !e.isConsumed()) {
            redispatchEvent(target, e);
        }
        if (!runKeyEventPostProcessors(e)) postProcessKeyEvent(e);
        return true;
    }

    /**
     * Handles MenuShortcuts of the frame containing the event's target.
     * There is no native menu bar on Android, so no shortcut is installed
     * for this to fire; the method only ever returns true.
     */
    public boolean postProcessKeyEvent(KeyEvent e) {
        return true;
    }

    /**
     * Consumes and acts on focus traversal keys for the focused component:
     * forward, backward, up-cycle, and (for a focus cycle root) down-cycle.
     * The KEY_TYPED and KEY_RELEASED of a traversal key are consumed too.
     */
    public void processKeyEvent(Component focusedComponent, KeyEvent e) {
        if (consumeProcessedKeyEvent(e)) return;
        // KEY_TYPED events cannot be focus traversal keys.
        if (e.getID() == KeyEvent.KEY_TYPED) return;
        if (focusedComponent.getFocusTraversalKeysEnabled() && !e.isConsumed()) {
            AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStrokeForEvent(e);
            AWTKeyStroke oppStroke = AWTKeyStroke.getAWTKeyStroke(stroke.getKeyCode(), stroke.getModifiers(),
                !stroke.isOnKeyRelease());

            Set<AWTKeyStroke> toTest = focusedComponent.getFocusTraversalKeys(FORWARD_TRAVERSAL_KEYS);
            boolean contains = toTest.contains(stroke);
            boolean containsOpp = toTest.contains(oppStroke);
            if (contains || containsOpp) {
                consumeTraversalKey(e);
                if (contains) focusNextComponent(focusedComponent);
                return;
            } else if (e.getID() == KeyEvent.KEY_PRESSED) {
                consumeNextKeyTyped = false;
            }

            toTest = focusedComponent.getFocusTraversalKeys(BACKWARD_TRAVERSAL_KEYS);
            contains = toTest.contains(stroke);
            containsOpp = toTest.contains(oppStroke);
            if (contains || containsOpp) {
                consumeTraversalKey(e);
                if (contains) focusPreviousComponent(focusedComponent);
                return;
            }

            toTest = focusedComponent.getFocusTraversalKeys(UP_CYCLE_TRAVERSAL_KEYS);
            contains = toTest.contains(stroke);
            containsOpp = toTest.contains(oppStroke);
            if (contains || containsOpp) {
                consumeTraversalKey(e);
                if (contains) upFocusCycle(focusedComponent);
                return;
            }

            if (!(focusedComponent instanceof Container && ((Container) focusedComponent).isFocusCycleRoot())) return;

            toTest = focusedComponent.getFocusTraversalKeys(DOWN_CYCLE_TRAVERSAL_KEYS);
            contains = toTest.contains(stroke);
            containsOpp = toTest.contains(oppStroke);
            if (contains || containsOpp) {
                consumeTraversalKey(e);
                if (contains) downFocusCycle((Container) focusedComponent);
            }
        }
    }

    private void consumeTraversalKey(KeyEvent e) {
        e.consume();
        consumeNextKeyTyped = e.getID() == KeyEvent.KEY_PRESSED && !e.isActionKey();
    }

    private boolean consumeProcessedKeyEvent(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_TYPED && consumeNextKeyTyped) {
            e.consume();
            consumeNextKeyTyped = false;
            return true;
        }
        return false;
    }

    /** Deliberate no-op: focus transfers complete synchronously, so no KeyEvent is ever held back. */
    protected synchronized void enqueueKeyEvents(long after, Component untilFocused) {
    }

    /** Deliberate no-op: nothing is ever enqueued (see {@link #enqueueKeyEvents}). */
    protected synchronized void dequeueKeyEvents(long after, Component untilFocused) {
    }

    /** Deliberate no-op: nothing is ever enqueued (see {@link #enqueueKeyEvents}). */
    protected synchronized void discardKeyEvents(Component comp) {
    }

    /** Focuses the Component after aComponent in its focus cycle. */
    public void focusNextComponent(Component aComponent) {
        if (aComponent != null) aComponent.transferFocus();
    }

    /** Focuses the Component before aComponent in its focus cycle. */
    public void focusPreviousComponent(Component aComponent) {
        if (aComponent != null) aComponent.transferFocusBackward();
    }

    /** Moves focus up one focus traversal cycle from aComponent. */
    public void upFocusCycle(Component aComponent) {
        if (aComponent != null) aComponent.transferFocusUpCycle();
    }

    /** Moves focus down one focus traversal cycle into aContainer, if it is a focus cycle root. */
    public void downFocusCycle(Container aContainer) {
        if (aContainer != null && aContainer.isFocusCycleRoot()) aContainer.transferFocusDownCycle();
    }
}
