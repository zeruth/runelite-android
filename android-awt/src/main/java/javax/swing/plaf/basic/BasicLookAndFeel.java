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

import java.awt.Color;
import java.awt.SystemColor;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;

public abstract class BasicLookAndFeel extends LookAndFeel {
    @Override
    public UIDefaults getDefaults() {
        UIDefaults table = new UIDefaults(610, 0.75f);
        initClassDefaults(table);
        initSystemColorDefaults(table);
        initComponentDefaults(table);
        return table;
    }

    // The ComponentUI class table and the per-component defaults arrive with
    // the Basic delegates; until then a subclass (FlatLaf) supplies its own.
    protected void initClassDefaults(UIDefaults table) {
        final String basicPackageName = "javax.swing.plaf.basic.";
        // LabelUI is listed because DefaultTreeCellRenderer is a JLabel: under a JDK JLabel
        // (the host contracts) a missing delegate leaves the renderer 0 high, so every row
        // measures 0 wide. The shadow JLabel sizes itself and does not ask for a delegate.
        Object[] uiDefaults = {
                  "LabelUI", basicPackageName + "BasicLabelUI",
             "OptionPaneUI", basicPackageName + "BasicOptionPaneUI",
                   "TreeUI", basicPackageName + "BasicTreeUI",
        };

        table.putDefaults(uiDefaults);
    }

    /** The JDK's Basic system colors, loaded natively when the subclass is a native look and feel. */
    protected void initSystemColorDefaults(UIDefaults table) {
        String[] defaultSystemColors = {
                "desktop", "#005C5C", /* Color of the desktop background */
          "activeCaption", "#000080", /* Color for captions (title bars) when they are active. */
      "activeCaptionText", "#FFFFFF", /* Text color for text in captions (title bars). */
    "activeCaptionBorder", "#C0C0C0", /* Border color for caption (title bar) window borders. */
        "inactiveCaption", "#808080", /* Color for captions (title bars) when not active. */
    "inactiveCaptionText", "#C0C0C0", /* Text color for text in inactive captions (title bars). */
  "inactiveCaptionBorder", "#C0C0C0", /* Border color for inactive caption (title bar) window borders. */
                 "window", "#FFFFFF", /* Default color for the interior of windows */
           "windowBorder", "#000000", /* ??? */
             "windowText", "#000000", /* ??? */
                   "menu", "#C0C0C0", /* Background color for menus */
               "menuText", "#000000", /* Text color for menus  */
                   "text", "#C0C0C0", /* Text background color */
               "textText", "#000000", /* Text foreground color */
          "textHighlight", "#000080", /* Text background color when selected */
      "textHighlightText", "#FFFFFF", /* Text color when selected */
       "textInactiveText", "#808080", /* Text color when disabled */
                "control", "#C0C0C0", /* Default color for controls (buttons, sliders, etc) */
            "controlText", "#000000", /* Default color for text in controls */
       "controlHighlight", "#C0C0C0", /* Specular highlight (opposite of the shadow) */
     "controlLtHighlight", "#FFFFFF", /* Highlight color for controls */
          "controlShadow", "#808080", /* Shadow color for controls */
        "controlDkShadow", "#000000", /* Dark shadow color for controls */
              "scrollbar", "#E0E0E0", /* Scrollbar background (usually the "track") */
                   "info", "#FFFFE1", /* ??? */
               "infoText", "#000000"  /* ??? */
        };

        loadSystemColors(table, defaultSystemColors, isNativeLookAndFeel());
    }

    /**
     * The JDK's Basic per-component defaults for the delegates the shadow has
     * ported (the Tree section). The JDK's gif node icons are not bundled;
     * DefaultTreeCellRenderer draws no icon when they are absent.
     */
    protected void initComponentDefaults(UIDefaults table) {
        Object dialogPlain12 = new javax.swing.plaf.FontUIResource("Dialog", java.awt.Font.PLAIN, 12);
        Object[] defaults = {
            "Tree.font", dialogPlain12,
            "Tree.background", table.get("window"),
            "Tree.foreground", table.get("textText"),
            "Tree.hash", table.get("controlShadow"),
            "Tree.textForeground", table.get("textText"),
            "Tree.textBackground", table.get("text"),
            "Tree.selectionForeground", table.get("textHighlightText"),
            "Tree.selectionBackground", table.get("textHighlight"),
            "Tree.selectionBorderColor", new ColorUIResource(Color.black),
            "Tree.dropLineColor", table.get("controlShadow"),
            "Tree.leftChildIndent", Integer.valueOf(7),
            "Tree.rightChildIndent", Integer.valueOf(13),
            "Tree.rowHeight", Integer.valueOf(16),
            "Tree.scrollsOnExpand", Boolean.TRUE,
            "Tree.timeFactor", Long.valueOf(1000L),
            "Tree.changeSelectionWithFocus", Boolean.TRUE,
            "Tree.drawsFocusBorderAroundIcon", Boolean.FALSE,
            "Tree.lineTypeDashed", Boolean.FALSE,
            "Tree.paintLines", Boolean.TRUE,

            // OptionPane as in the JDK's Basic table. The error/information/
            // warning/question gif icons are not bundled, so a pane without
            // its own icon shows none. The texts are the JDK's English
            // basic.properties entries, which the shadow does not bundle.
            "OptionPane.font", dialogPlain12,
            "OptionPane.background", table.get("control"),
            "OptionPane.foreground", table.get("controlText"),
            "OptionPane.messageForeground", table.get("controlText"),
            "OptionPane.border", new javax.swing.plaf.BorderUIResource.EmptyBorderUIResource(10, 10, 12, 10),
            "OptionPane.messageAreaBorder", new javax.swing.plaf.BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0),
            "OptionPane.buttonAreaBorder", new javax.swing.plaf.BorderUIResource.EmptyBorderUIResource(6, 0, 0, 0),
            "OptionPane.minimumSize", new javax.swing.plaf.DimensionUIResource(262, 90),
            "OptionPane.windowBindings", new Object[] {"ESCAPE", "close"},
            "OptionPane.yesButtonText", "Yes",
            "OptionPane.noButtonText", "No",
            "OptionPane.okButtonText", "OK",
            "OptionPane.cancelButtonText", "Cancel",
            "OptionPane.titleText", "Select an Option",
            "OptionPane.inputDialogTitle", "Input",
            "OptionPane.messageDialogTitle", "Message",
        };
        table.putDefaults(defaults);
    }

    /**
     * Populates {@code table} with the {@code name-color} pairs in
     * {@code systemColors}: with {@code useNative} the color is the
     * {@code SystemColor} field of that name, otherwise the decoded value
     * (black when it does not decode).
     */
    protected void loadSystemColors(UIDefaults table, String[] systemColors, boolean useNative) {
        if (useNative) {
            for (int i = 0; i < systemColors.length; i += 2) {
                Color color = Color.black;
                try {
                    String name = systemColors[i];
                    color = (Color) (SystemColor.class.getField(name).get(null));
                } catch (Exception e) {
                }
                table.put(systemColors[i], new ColorUIResource(color));
            }
        } else {
            for (int i = 0; i < systemColors.length; i += 2) {
                Color color = Color.black;
                try {
                    color = Color.decode(systemColors[i + 1]);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
                table.put(systemColors[i], new ColorUIResource(color));
            }
        }
    }

    /**
     * Returns {@code ui} when it is a {@code klass}, otherwise null. The JDK
     * also unwraps a {@code MultiLookAndFeel} UI, which the shadow never creates.
     */
    static Object getUIOfType(javax.swing.plaf.ComponentUI ui, Class<?> klass) {
        if (klass.isInstance(ui)) {
            return ui;
        }
        return null;
    }

    @Override public void initialize() {}
    @Override public void uninitialize() {}

    @Override public String getName() { return "Basic"; }
    @Override public String getID() { return "Basic"; }
    @Override public String getDescription() { return "Basic"; }
    @Override public boolean isNativeLookAndFeel() { return false; }
    @Override public boolean isSupportedLookAndFeel() { return true; }
}
