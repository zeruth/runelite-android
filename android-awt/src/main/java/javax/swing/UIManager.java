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

import java.awt.Color;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;

public class UIManager {
    public static class LookAndFeelInfo {
        private final String name;
        private final String className;
        public LookAndFeelInfo(String name, String className) { this.name = name; this.className = className; }
        public String getName() { return name; }
        public String getClassName() { return className; }
    }

    private static final UIDefaults defaults = new UIDefaults();
    private static LookAndFeel laf;
    private static UIDefaults lafDefaults=new UIDefaults();
    private static final java.beans.PropertyChangeSupport changes=new java.beans.PropertyChangeSupport(UIManager.class);
    private static final java.util.List<LookAndFeelInfo> installed=new java.util.ArrayList<>();
    private static final java.util.List<LookAndFeel> auxiliary=new java.util.ArrayList<>();

    public static LookAndFeelInfo[] getInstalledLookAndFeels() { return installed.toArray(new LookAndFeelInfo[0]); }
    public static void setInstalledLookAndFeels(LookAndFeelInfo[] infos) { installed.clear();java.util.Collections.addAll(installed,infos); }
    public static void installLookAndFeel(LookAndFeelInfo info) { installed.add(info); }
    public static void installLookAndFeel(String name, String className) { installLookAndFeel(new LookAndFeelInfo(name,className)); }
    public static LookAndFeel getLookAndFeel() { return laf; }
    public static void setLookAndFeel(LookAndFeel value) {
        if (value != null && !value.isSupportedLookAndFeel())
            throw new IllegalArgumentException("Unsupported look and feel");
        LookAndFeel old = laf;
        // Android renders controls and menus itself. Desktop theme initialization
        // installs native event hooks/UI delegates that this renderer does not use.
        laf = value;
        changes.firePropertyChange("lookAndFeel", old, value);
    }
    public static void setLookAndFeel(String className) throws Exception { setLookAndFeel((LookAndFeel)Class.forName(className).getDeclaredConstructor().newInstance()); }
    /** Default for the compatibility renderer, which paints controls without desktop UI delegates. */
    public static final class CompatLookAndFeel extends javax.swing.plaf.basic.BasicLookAndFeel {}
    public static String getSystemLookAndFeelClassName() { return getCrossPlatformLookAndFeelClassName(); }
    public static String getCrossPlatformLookAndFeelClassName() { return CompatLookAndFeel.class.getName(); }
    public static UIDefaults getDefaults() { return defaults; }
    public static UIDefaults getLookAndFeelDefaults() { return lafDefaults; }

    public static Object get(Object key) { Object value=defaults.get(key);return value==null?lafDefaults.get(key):value; }
    public static Object get(Object key, java.util.Locale l) { return defaults.get(key); }
    public static Object put(Object key, Object value) { return defaults.put(key, value); }
    public static Font getFont(Object key) { Object value=get(key);return value instanceof Font?(Font)value:null; }
    public static Font getFont(Object key, java.util.Locale l) { return defaults.getFont(key); }
    public static Color getColor(Object key) { Object value=get(key);return value instanceof Color?(Color)value:null; }
    public static Color getColor(Object key, java.util.Locale l) { return defaults.getColor(key); }
    public static Icon getIcon(Object key) { Object value=get(key);return value instanceof Icon?(Icon)value:null; }
    public static java.awt.Dimension getDimension(Object key) { Object value=get(key);return value instanceof java.awt.Dimension?(java.awt.Dimension)value:null; }
    public static java.awt.Insets getInsets(Object key) { Object value=get(key);return value instanceof java.awt.Insets?(java.awt.Insets)value:null; }
    public static Border getBorder(Object key) { Object value=get(key);return value instanceof Border?(Border)value:null; }
    public static String getString(Object key) { Object value=get(key);return value instanceof String?(String)value:null; }
    public static int getInt(Object key) { Object value=get(key);return value instanceof Integer?(Integer)value:0; }
    public static boolean getBoolean(Object key) { return Boolean.TRUE.equals(get(key)); }
    public static ComponentUI getUI(JComponent target) { ComponentUI ui=defaults.getUI(target);return ui==null?lafDefaults.getUI(target):ui; }

    public static void addPropertyChangeListener(PropertyChangeListener l) { changes.addPropertyChangeListener(l); }
    public static void removePropertyChangeListener(PropertyChangeListener l) { changes.removePropertyChangeListener(l); }
    public static PropertyChangeListener[] getPropertyChangeListeners() { return changes.getPropertyChangeListeners(); }
    public static void addAuxiliaryLookAndFeel(LookAndFeel laf) { if(laf!=null&&!auxiliary.contains(laf))auxiliary.add(laf); }
    public static boolean removeAuxiliaryLookAndFeel(LookAndFeel laf) { return auxiliary.remove(laf); }
    public static LookAndFeel[] getAuxiliaryLookAndFeels() { return auxiliary.toArray(new LookAndFeel[0]); }
}
