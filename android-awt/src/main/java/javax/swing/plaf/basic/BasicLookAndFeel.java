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

import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;

public abstract class BasicLookAndFeel extends LookAndFeel {
    @Override
    public UIDefaults getDefaults() {
        UIDefaults table = new UIDefaults();
        initClassDefaults(table);
        initSystemColorDefaults(table);
        initComponentDefaults(table);
        return table;
    }

    protected void initClassDefaults(UIDefaults table) {}
    protected void initSystemColorDefaults(UIDefaults table) {}
    protected void initComponentDefaults(UIDefaults table) {}
    protected void loadSystemColors(UIDefaults table, String[] systemColors, boolean useNative) {}

    @Override public void initialize() {}
    @Override public void uninitialize() {}

    @Override public String getName() { return "Basic"; }
    @Override public String getID() { return "Basic"; }
    @Override public String getDescription() { return "Basic"; }
    @Override public boolean isNativeLookAndFeel() { return false; }
    @Override public boolean isSupportedLookAndFeel() { return true; }
}
