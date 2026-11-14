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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.im.InputContext;
import java.lang.Character.Subset;
import java.util.Locale;

/**
 * The input context {@link InputContext#getInstance()} returns. On Android the
 * composition belongs to the system soft keyboard: the app's keyboard bridge turns
 * its composing region into {@link java.awt.event.InputMethodEvent}s for the focused
 * client, so this context never sees raw AWT events and cannot pick or toggle the
 * keyboard. What it can do is ask the bridge to finish the composition and report
 * the keyboard's locale, both through hooks the bridge installs.
 */
public final class HostInputContext extends InputContext {
    private static volatile Runnable endCompositionHandler;
    private static volatile Locale keyboardLocale;

    private Subset[] characterSubsets;

    /** Installed by the keyboard bridge while a soft-keyboard session is open; null otherwise. */
    public static void setEndCompositionHandler(Runnable handler) {
        endCompositionHandler = handler;
    }

    /** The locale of the active soft keyboard subtype, or null to fall back to the default locale. */
    public static void setKeyboardLocale(Locale locale) {
        keyboardLocale = locale;
    }

    public HostInputContext() {
    }

    /** The system owns keyboard selection; this reports whether the active one already fits. */
    @Override public boolean selectInputMethod(Locale locale) {
        if (locale == null) throw new NullPointerException("locale");
        Locale current = getLocale();
        return current.getLanguage().equals(locale.getLanguage());
    }

    @Override public Locale getLocale() {
        Locale locale = keyboardLocale;
        return locale != null ? locale : Locale.getDefault();
    }

    /** Recorded for {@link #getCharacterSubsets()}; Android keyboards take no subset hint. */
    @Override public void setCharacterSubsets(Subset[] subsets) {
        characterSubsets = subsets == null ? null : subsets.clone();
    }

    /** Non-JDK accessor for the subsets last passed to {@link #setCharacterSubsets}. */
    public Subset[] getCharacterSubsets() {
        return characterSubsets == null ? null : characterSubsets.clone();
    }

    @Override public void setCompositionEnabled(boolean enable) {
        // The JDK contract for an input method that cannot be toggled.
        throw new UnsupportedOperationException("InputContext.setCompositionEnabled");
    }

    /** Android keyboards always compose. */
    @Override public boolean isCompositionEnabled() {
        return true;
    }

    @Override public void reconvert() {
        throw new UnsupportedOperationException("InputContext.reconvert");
    }

    /**
     * Deliberate no-op: the soft keyboard receives its input from the system, not from
     * AWT events, so nothing here is ever consumed (the JDK behaviour without an input method).
     */
    @Override public void dispatchEvent(AWTEvent event) {
        if (event == null) throw new NullPointerException("event");
    }

    /** Ends the composition of a client that leaves the hierarchy, as desktop does. */
    @Override public void removeNotify(Component client) {
        if (client == null) throw new NullPointerException("client");
        endComposition();
    }

    /** Commits the soft keyboard's composing text through the bridge, when a session is open. */
    @Override public void endComposition() {
        Runnable handler = endCompositionHandler;
        if (handler != null) handler.run();
    }

    /** Nothing is held per context; the keyboard session is the bridge's. */
    @Override public void dispose() {
    }

    @Override public Object getInputMethodControlObject() {
        return null;
    }
}
