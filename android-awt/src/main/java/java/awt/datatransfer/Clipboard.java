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
package java.awt.datatransfer;

import java.util.ArrayList;
import java.util.List;

public class Clipboard {
    private final String name;
    private Transferable contents;
    private ClipboardOwner owner;
    private final List<FlavorListener> flavorListeners = new ArrayList<>();
    /**
     * The host (Android) clipboard behind the system clipboard. A text-only bridge implements
     * {@code readText}/{@code writeText}; a bridge carrying more flavours (HTML, images, file
     * lists) overrides the three defaults. Reads may throw {@code IllegalStateException} or
     * {@code SecurityException} while the host refuses access (Android 10+ when unfocused); the
     * in-process contents are kept then.
     */
    public interface Bridge {
        String readText();
        void writeText(String text);

        /**
         * A value equal to the one {@link #writeContents} returned while the host still holds
         * what was written, and changing when another app replaces it: an unchanged stamp
         * keeps the in-process {@code Transferable} (and its owner) instead of re-importing.
         */
        default Object readStamp() { return readText(); }

        default Transferable readContents() {
            String text = readText();
            return text == null ? null : new StringSelection(text);
        }

        /** Publishes {@code contents}; returns its stamp, or {@code null} when no flavour could be carried. */
        default Object writeContents(Transferable contents) throws java.io.IOException, UnsupportedFlavorException {
            if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return null;
            String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
            writeText(text);
            return text;
        }
    }
    private volatile Bridge bridge;
    private Object mirroredStamp;
    public void setBridge(Bridge bridge) { synchronized (this) { this.bridge = bridge; } refreshFromBridge(); }
    public synchronized void clearBridge(Bridge expected) { if (bridge == expected) bridge = null; }
    /** Imports the host clipboard when another app changed it; fires lostOwnership and FlavorEvents like a desktop clipboard change. */
    public void refreshFromBridge() {
        Bridge adapter = bridge;
        if (adapter == null) return;
        Object stamp;
        Transferable value;
        try {
            stamp = adapter.readStamp();
            synchronized (this) { if (adapter != bridge || java.util.Objects.equals(stamp, mirroredStamp)) return; }
            value = adapter.readContents();
        } catch (IllegalStateException | SecurityException unavailable) {
            return;
        }
        synchronized (this) { if (adapter != bridge) return; mirroredStamp = stamp; }
        updateContents(value, null);
    }

    public Clipboard(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public void setContents(Transferable contents, ClipboardOwner owner) {
        updateContents(contents, owner);
        Bridge adapter = bridge;
        if (adapter != null && contents != null) {
            // Held across the write so a host change notification that races it waits for the
            // new stamp and does not re-import (and disown) what was just exported.
            synchronized (this) {
                Object stamp;
                try { stamp = adapter.writeContents(contents); }
                catch (java.io.IOException | UnsupportedFlavorException e) { throw new IllegalStateException("Cannot export clipboard contents", e); }
                if (stamp != null) mirroredStamp = stamp;
            }
        }
    }
    private void updateContents(Transferable value, ClipboardOwner newOwner) {
        ClipboardOwner oldOwner;
        Transferable oldContents;
        FlavorListener[] listeners;
        synchronized (this) {
            oldOwner = owner; oldContents = contents;
            java.util.Set<DataFlavor> before = flavors(contents), after = flavors(value);
            contents = value; owner = newOwner;
            listeners = before.equals(after) ? new FlavorListener[0] : getFlavorListeners();
        }
        if (oldOwner != null && oldOwner != newOwner) {
            java.awt.EventQueue.invokeLater(() -> oldOwner.lostOwnership(this, oldContents));
        }
        for (FlavorListener listener : listeners) java.awt.EventQueue.invokeLater(() -> listener.flavorsChanged(new FlavorEvent(this)));
    }
    private static java.util.Set<DataFlavor> flavors(Transferable value) { return value == null ? java.util.Collections.emptySet() : new java.util.HashSet<>(java.util.Arrays.asList(value.getTransferDataFlavors())); }

    public Transferable getContents(Object requestor) {
        refreshFromBridge();
        synchronized (this) { return contents; }
    }

    public DataFlavor[] getAvailableDataFlavors() {
        Transferable t = getContents(null);
        return t == null ? new DataFlavor[0] : t.getTransferDataFlavors();
    }

    public boolean isDataFlavorAvailable(DataFlavor flavor) {
        Transferable t = getContents(null);
        return t != null && t.isDataFlavorSupported(flavor);
    }

    public Object getData(DataFlavor flavor) throws UnsupportedFlavorException, java.io.IOException {
        java.util.Objects.requireNonNull(flavor);
        Transferable value = getContents(null);
        if (value == null) throw new UnsupportedFlavorException(flavor);
        return value.getTransferData(flavor);
    }
    public synchronized void addFlavorListener(FlavorListener listener) { if (listener != null) flavorListeners.add(listener); }
    public synchronized void removeFlavorListener(FlavorListener listener) { flavorListeners.remove(listener); }
    public synchronized FlavorListener[] getFlavorListeners() { return flavorListeners.toArray(new FlavorListener[0]); }
}
