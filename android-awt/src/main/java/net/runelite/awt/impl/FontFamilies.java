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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Name side of the font backend: which family a {@link java.awt.Font} name resolves to,
 * the face names of each family, and the list behind {@code GraphicsEnvironment}. Pure Java
 * on purpose: {@code java.awt.Font} resolves its family through this class, and the host
 * contract tests compile {@code Font} only while it reaches nothing from {@code android.*}.
 * The Typeface side of the same mapping is {@link AwtFontRegistry}.
 *
 * Three kinds of family exist, looked up in this order (case-insensitively, as desktop):
 * <ol>
 * <li>the five logical families, see {@link #androidFamilyFor(String)};</li>
 * <li>families registered by {@code Font.createFont} ({@link #registerFace});</li>
 * <li>Android system families, read from {@code /system/etc/fonts.xml} (and
 *     {@code font_fallback.xml} where the device has it): {@code sans-serif},
 *     {@code serif}, {@code monospace}, {@code sans-serif-condensed}, ...</li>
 * </ol>
 * Any other name resolves to {@code Dialog}, as {@code Font.getFamily()} does on desktop.
 */
public final class FontFamilies {
    private FontFamilies() {}

    private static final String[] LOGICAL = {
        java.awt.Font.DIALOG, java.awt.Font.DIALOG_INPUT, java.awt.Font.SANS_SERIF,
        java.awt.Font.SERIF, java.awt.Font.MONOSPACED
    };
    /** Android family per logical family, same order as {@link #LOGICAL}. DialogInput is
     *  monospaced on desktop too (Courier New on Windows, DejaVu Sans Mono on Linux). */
    private static final String[] ANDROID = {
        "sans-serif", "monospace", "sans-serif", "serif", "monospace"
    };

    /** Registered family to face names, indexed by java.awt.Font style bits:
     *  {fullName, postScriptName} or null for an unregistered slot. */
    private static final ConcurrentHashMap<String, String[][]> REGISTERED = new ConcurrentHashMap<>();

    private static volatile Map<String, String> systemFamilies;

    /** Canonical logical family name ({@code "SansSerif"} for {@code "sansserif"}), or null. */
    public static String logical(String name) {
        if (name == null) return null;
        for (String l : LOGICAL) {
            if (l.equalsIgnoreCase(name)) return l;
        }
        return null;
    }

    /**
     * The Android system family a logical family renders with: Dialog and SansSerif map
     * to {@code sans-serif} (Roboto), Serif to {@code serif} (Noto Serif), Monospaced and
     * DialogInput to {@code monospace} (Droid Sans Mono / Cutive Mono). Null for a name
     * that is not logical.
     */
    public static String androidFamilyFor(String name) {
        String l = logical(name);
        if (l == null) return null;
        for (int i = 0; i < LOGICAL.length; i++) {
            if (LOGICAL[i].equals(l)) return ANDROID[i];
        }
        return null;
    }

    /** Records a face loaded by {@code Font.createFont}. */
    public static void registerFace(String family, int style, String fullName, String psName) {
        synchronized (REGISTERED) {
            String[][] slots = REGISTERED.get(family);
            String[][] next = slots != null ? slots.clone() : new String[4][];
            if (next[style & 3] == null) {
                next[style & 3] = new String[] {fullName, psName};
            }
            REGISTERED.put(family, next);
        }
    }

    /** The registered family key {@code name} names (exact, then case-insensitive), or null. */
    public static String registered(String name) {
        if (name == null) return null;
        if (REGISTERED.containsKey(name)) return name;
        for (String key : REGISTERED.keySet()) {
            if (key.equalsIgnoreCase(name)) return key;
        }
        return null;
    }

    /** Style slots registered for {@code family} (bit i set when style i has its own face). */
    public static int registeredStyles(String family) {
        String[][] slots = family != null ? REGISTERED.get(family) : null;
        int mask = 0;
        if (slots != null) {
            for (int i = 0; i < 4; i++) {
                if (slots[i] != null) mask |= 1 << i;
            }
        }
        return mask;
    }

    /** The system family {@code name} names, case-insensitively, or null. */
    public static String system(String name) {
        if (name == null) return null;
        Map<String, String> families = systemFamilies();
        String hit = families.get(name.toLowerCase(Locale.ENGLISH));
        return hit;
    }

    /** {@code Font.getFamily()} for a font named {@code name}: logical, registered or system
     *  family in that order, else {@code Dialog}. */
    public static String resolve(String name) {
        String found = logical(name);
        if (found == null) found = registered(name);
        if (found == null) found = system(name);
        return found != null ? found : java.awt.Font.DIALOG;
    }

    /**
     * {@code Font.getFontName()}: a logical family names its per-style composite as desktop
     * does ({@code "Dialog.bold"}); a registered face reports the full name from its 'name'
     * table (the closest registered style when the requested one has no face of its own); a
     * system family the desktop physical-font pattern ({@code "serif Bold Italic"}).
     */
    public static String fontName(String name, int style) {
        String family = resolve(name);
        if (logical(family) != null) {
            return family + "." + styleSuffix(style);
        }
        String[] face = face(family, style);
        if (face != null && face[0] != null) return face[0];
        return physicalName(family, style);
    }

    /** {@code Font.getPSName()}: the registered face's PostScript name, a logical family's
     *  composite name, otherwise the full name without spaces. */
    public static String psName(String name, int style) {
        String family = resolve(name);
        if (logical(family) != null) {
            return fontName(family, style);
        }
        String[] face = face(family, style);
        if (face != null && face[1] != null) return face[1];
        return fontName(family, style).replace(" ", "");
    }

    private static String[] face(String family, int style) {
        String[][] slots = REGISTERED.get(family);
        if (slots == null) return null;
        style &= 3;
        if (slots[style] != null) return slots[style];
        if (slots[style & java.awt.Font.BOLD] != null) return slots[style & java.awt.Font.BOLD];
        if (slots[style & java.awt.Font.ITALIC] != null) return slots[style & java.awt.Font.ITALIC];
        for (String[] s : slots) {
            if (s != null) return s;
        }
        return null;
    }

    private static String styleSuffix(int style) {
        switch (style & 3) {
            case java.awt.Font.BOLD: return "bold";
            case java.awt.Font.ITALIC: return "italic";
            case java.awt.Font.BOLD | java.awt.Font.ITALIC: return "bolditalic";
            default: return "plain";
        }
    }

    private static String physicalName(String family, int style) {
        switch (style & 3) {
            case java.awt.Font.BOLD: return family + " Bold";
            case java.awt.Font.ITALIC: return family + " Italic";
            case java.awt.Font.BOLD | java.awt.Font.ITALIC: return family + " Bold Italic";
            default: return family;
        }
    }

    /** Every family {@code GraphicsEnvironment} lists: logical, registered and system,
     *  sorted case-insensitively as desktop sorts them. */
    public static String[] availableFamilies() {
        TreeMap<String, String> names = new TreeMap<>();
        for (String l : LOGICAL) names.put(l.toLowerCase(Locale.ENGLISH), l);
        for (String r : REGISTERED.keySet()) {
            String key = r.toLowerCase(Locale.ENGLISH);
            if (!names.containsKey(key)) names.put(key, r);
        }
        for (String s : systemFamilies().values()) {
            String key = s.toLowerCase(Locale.ENGLISH);
            if (!names.containsKey(key)) names.put(key, s);
        }
        return names.values().toArray(new String[0]);
    }

    /** Registered family names, in no particular order. */
    public static List<String> registeredFamilies() {
        return new ArrayList<>(REGISTERED.keySet());
    }

    /** File name of the upright regular face of a system family (relative to
     *  {@code /system/fonts}), or null. Aliases resolve to their target family. */
    public static String systemFontFile(String family) {
        if (family == null) return null;
        systemFamilies();
        return SYSTEM_FILES.get(family.toLowerCase(Locale.ENGLISH));
    }

    private static final Map<String, String> SYSTEM_FILES = new ConcurrentHashMap<>();
    private static final String[] FONT_CONFIGS = {"/system/etc/fonts.xml", "/system/etc/font_fallback.xml"};
    private static final Pattern FAMILY = Pattern.compile("<family\\b([^>]*)>(.*?)</family>", Pattern.DOTALL);
    private static final Pattern ALIAS = Pattern.compile("<alias\\b([^>]*)/?>");
    private static final Pattern NAME = Pattern.compile("\\bname\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TO = Pattern.compile("\\bto\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern FONT = Pattern.compile("<font\\b([^>]*)>\\s*([^<\\s]+)");

    private static Map<String, String> systemFamilies() {
        Map<String, String> families = systemFamilies;
        if (families != null) return families;
        synchronized (FontFamilies.class) {
            if (systemFamilies != null) return systemFamilies;
            LinkedHashMap<String, String> found = new LinkedHashMap<>();
            for (String config : FONT_CONFIGS) {
                String xml = read(new File(config));
                if (xml != null) parse(xml, found);
            }
            systemFamilies = Collections.unmodifiableMap(found);
            return systemFamilies;
        }
    }

    // fonts.xml: <family name="sans-serif"><font weight="400" style="normal">Roboto-Regular.ttf
    // ...</font></family> and <alias name="sans-serif-medium" to="sans-serif" weight="500"/>.
    // Unnamed families are fallback chains (emoji, CJK) and not selectable by name.
    private static void parse(String xml, Map<String, String> found) {
        Matcher family = FAMILY.matcher(xml);
        while (family.find()) {
            Matcher name = NAME.matcher(family.group(1));
            if (!name.find()) continue;
            String n = name.group(1);
            String key = n.toLowerCase(Locale.ENGLISH);
            if (found.containsKey(key)) continue;
            found.put(key, n);
            String file = null;
            Matcher font = FONT.matcher(family.group(2));
            while (font.find()) {
                String attrs = font.group(1);
                boolean regular = !attrs.contains("italic") && (attrs.contains("\"400\"") || !attrs.contains("weight"));
                if (file == null || regular) file = font.group(2);
                if (regular) break;
            }
            if (file != null) SYSTEM_FILES.put(key, file);
        }
        Matcher alias = ALIAS.matcher(xml);
        while (alias.find()) {
            Matcher name = NAME.matcher(alias.group(1));
            Matcher to = TO.matcher(alias.group(1));
            if (!name.find() || !to.find()) continue;
            String key = name.group(1).toLowerCase(Locale.ENGLISH);
            if (found.containsKey(key)) continue;
            found.put(key, name.group(1));
            String file = SYSTEM_FILES.get(to.group(1).toLowerCase(Locale.ENGLISH));
            if (file != null) SYSTEM_FILES.put(key, file);
        }
    }

    private static String read(File file) {
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
