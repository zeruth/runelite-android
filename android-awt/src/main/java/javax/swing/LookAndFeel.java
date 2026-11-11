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

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import javax.swing.border.Border;

public abstract class LookAndFeel {
    public abstract String getName();
    public abstract String getID();
    public abstract String getDescription();
    public abstract boolean isNativeLookAndFeel();
    public abstract boolean isSupportedLookAndFeel();

    public UIDefaults getDefaults() { return new UIDefaults(); }
    public void initialize() {}
    public void uninitialize() {}
    public void provideErrorFeedback(Component component) { java.awt.Toolkit.getDefaultToolkit().beep(); }

    public static void installColors(JComponent c,String defaultBgName,String defaultFgName) { if(c.getBackground()==null||c.getBackground() instanceof javax.swing.plaf.UIResource){java.awt.Color value=UIManager.getColor(defaultBgName);if(value!=null)c.setBackground(value);}if(c.getForeground()==null||c.getForeground() instanceof javax.swing.plaf.UIResource){java.awt.Color value=UIManager.getColor(defaultFgName);if(value!=null)c.setForeground(value);} }
    public static void installColorsAndFont(JComponent c,String defaultBgName,String defaultFgName,String defaultFontName) { installColors(c,defaultBgName,defaultFgName);if(c.getFont()==null||c.getFont() instanceof javax.swing.plaf.UIResource){Font font=UIManager.getFont(defaultFontName);if(font!=null)c.setFont(font);} }
    public static void installBorder(JComponent c,String name) { if(c.getBorder()==null||c.getBorder() instanceof javax.swing.plaf.UIResource)c.setBorder(UIManager.getBorder(name)); }
    public static void installProperty(JComponent c,String name,Object value) { try{String setter="set"+Character.toUpperCase(name.charAt(0))+name.substring(1);for(java.lang.reflect.Method method:c.getClass().getMethods())if(method.getName().equals(setter)&&method.getParameterTypes().length==1){method.invoke(c,value);return;}throw new IllegalArgumentException("Unknown property "+name);}catch(ReflectiveOperationException e){throw new IllegalArgumentException("Cannot set "+name,e);} }
    public static void uninstallBorder(JComponent c) { if(c.getBorder() instanceof javax.swing.plaf.UIResource)c.setBorder(null); }

    @Override public String toString() { return "[" + getDescription() + " - " + getClass().getName() + "]"; }
}
