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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.lang.reflect.Array;
import java.util.EventListener;

/**
 * The listener fields of the AWT widgets ({@code Button.actionListener}, {@code Checkbox.itemListener},
 * ...) hold one listener, or a chain of them, as {@code AWTEventMulticaster} does on the JDK. The widgets
 * keep the JDK field names and types because the host contract runner patches them over the JDK's
 * classes, which declare those fields next to native {@code initIDs}; this immutable chain is the
 * value those fields hold once a second listener is added. Listeners run in registration order.
 */
public final class ListenerChain implements ActionListener, ItemListener, AdjustmentListener, TextListener {
    private final EventListener[] listeners;

    private ListenerChain(EventListener[] listeners) {
        this.listeners = listeners;
    }

    /** {@code chain} with {@code listener} appended; {@code null} on either side is the identity. */
    @SuppressWarnings("unchecked")
    public static <T extends EventListener> T add(T chain, T listener) {
        if (listener == null) return chain;
        if (chain == null) return listener;
        EventListener[] current = flatten(chain);
        EventListener[] next = new EventListener[current.length + 1];
        System.arraycopy(current, 0, next, 0, current.length);
        next[current.length] = listener;
        return (T) new ListenerChain(next);
    }

    /** {@code chain} without the last registration of {@code listener}, as AWTEventMulticaster.remove. */
    @SuppressWarnings("unchecked")
    public static <T extends EventListener> T remove(T chain, T listener) {
        if (chain == null || listener == null) return chain;
        if (chain == listener) return null;
        if (!(chain instanceof ListenerChain)) return chain;
        EventListener[] current = ((ListenerChain) chain).listeners;
        for (int i = current.length - 1; i >= 0; i--) {
            if (current[i] == listener) {
                if (current.length == 2) return (T) current[1 - i];
                EventListener[] next = new EventListener[current.length - 1];
                System.arraycopy(current, 0, next, 0, i);
                System.arraycopy(current, i + 1, next, i, current.length - i - 1);
                return (T) new ListenerChain(next);
            }
        }
        return chain;
    }

    /** The registered listeners of {@code type} in {@code chain}, as AWTEventMulticaster.getListeners. */
    @SuppressWarnings("unchecked")
    public static <T extends EventListener> T[] toArray(EventListener chain, Class<T> type) {
        if (type == null) throw new NullPointerException("listenerType");
        EventListener[] all = chain == null ? new EventListener[0] : flatten(chain);
        int count = 0;
        for (EventListener listener : all) if (type.isInstance(listener)) count++;
        T[] result = (T[]) Array.newInstance(type, count);
        int index = 0;
        for (EventListener listener : all) if (type.isInstance(listener)) result[index++] = (T) listener;
        return result;
    }

    private static EventListener[] flatten(EventListener chain) {
        return chain instanceof ListenerChain ? ((ListenerChain) chain).listeners : new EventListener[] {chain};
    }

    @Override public void actionPerformed(ActionEvent e) {
        for (EventListener listener : listeners) ((ActionListener) listener).actionPerformed(e);
    }

    @Override public void itemStateChanged(ItemEvent e) {
        for (EventListener listener : listeners) ((ItemListener) listener).itemStateChanged(e);
    }

    @Override public void adjustmentValueChanged(AdjustmentEvent e) {
        for (EventListener listener : listeners) ((AdjustmentListener) listener).adjustmentValueChanged(e);
    }

    @Override public void textValueChanged(TextEvent e) {
        for (EventListener listener : listeners) ((TextListener) listener).textValueChanged(e);
    }
}
