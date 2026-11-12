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
package net.runelite.awt.impl;

import java.awt.Component;
import java.awt.event.FocusEvent;

/**
 * The shadow's stand-in for the JDK's {@code sun.awt.RequestFocusController}: the one veto
 * Swing holds over every focus request, which {@code javax.swing.JComponent} installs to run
 * the focus owner's InputVerifier and {@code java.awt.KeyboardFocusManager} consults before a
 * focus change.
 * <p>
 * It lives here rather than on KeyboardFocusManager so that JComponent names no shadow-only
 * member of a java.awt class: the host contract runner then patches the shadow JComponent over
 * the JDK and can call {@link #accept} directly, which is the only way to reach the veto there
 * (the JDK's own Component and KeyboardFocusManager run on the host).
 */
public final class FocusVeto {
    /** Mirrors {@code sun.awt.RequestFocusController#acceptRequestFocus}. */
    public interface Controller {
        boolean acceptRequestFocus(Component from, Component to, boolean temporary,
                                   boolean focusedWindowChangeAllowed, FocusEvent.Cause cause);
    }

    private static volatile Controller controller;

    private FocusVeto() {
    }

    /** Installed once, from JComponent's static initializer, as the JDK does through AWTAccessor. */
    public static void install(Controller newController) {
        controller = newController;
    }

    /** The installed controller, or null while no Swing component has been loaded. */
    public static Controller installed() {
        return controller;
    }

    /**
     * The tail of the JDK's {@code Component.isRequestFocusAccepted}, after its regular checks:
     * no current owner, the owner asking for itself and a window activation always pass; every
     * other request asks the controller whether {@code from} lets focus go to {@code to}.
     */
    public static boolean accept(Component from, Component to, boolean temporary, FocusEvent.Cause cause) {
        if (from == null || from == to || cause == FocusEvent.Cause.ACTIVATION) {
            return true;
        }
        Controller current = controller;
        return current == null || current.acceptRequestFocus(from, to, temporary, true, cause);
    }
}
