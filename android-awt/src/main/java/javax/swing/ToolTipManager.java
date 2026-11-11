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
package javax.swing;

import java.awt.event.MouseAdapter;

public class ToolTipManager extends MouseAdapter {
    private static final ToolTipManager INSTANCE = new ToolTipManager();
    private boolean enabled = true;
    private boolean lightWeightPopupEnabled = true;
    private boolean heavyWeightPopupEnabled = false;
    private int initialDelay = 750;
    private int dismissDelay = 4000;
    private int reshowDelay = 500;

    public static ToolTipManager sharedInstance() { return INSTANCE; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setLightWeightPopupEnabled(boolean aFlag) { this.lightWeightPopupEnabled = aFlag; }
    public boolean isLightWeightPopupEnabled() { return lightWeightPopupEnabled; }
    public boolean isHeavyWeightPopupEnabled() { return heavyWeightPopupEnabled; }
    public void setInitialDelay(int milliseconds) { this.initialDelay = milliseconds; }
    public int getInitialDelay() { return initialDelay; }
    public void setDismissDelay(int milliseconds) { this.dismissDelay = milliseconds; }
    public int getDismissDelay() { return dismissDelay; }
    public void setReshowDelay(int milliseconds) { this.reshowDelay = milliseconds; }
    public int getReshowDelay() { return reshowDelay; }
    // Weak so registering never pins a discarded panel. Desktop registration installs
    // listeners; here the panel host asks instead, to treat these as tooltip owners.
    private final java.util.Map<JComponent, Boolean> registered =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public void registerComponent(JComponent component) { if (component != null) registered.put(component, Boolean.TRUE); }
    public void unregisterComponent(JComponent component) { if (component != null) registered.remove(component); }
    /** Host hook (not JDK API): whether {@link #registerComponent} holds this component. */
    public boolean isRegistered(JComponent component) { return component != null && registered.containsKey(component); }
    public void mouseDragged(java.awt.event.MouseEvent e) {}
}
