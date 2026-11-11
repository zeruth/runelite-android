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
package java.awt.font;

import java.text.AttributedCharacterIterator;

/** Standard text attribute keys shared by font derivation and attributed rendering. */
public final class TextAttribute extends AttributedCharacterIterator.Attribute {
    private static final long serialVersionUID = 7744112784117861702L;

    protected TextAttribute(String name) { super(name); }

    public static final TextAttribute FAMILY = new TextAttribute("family");
    public static final TextAttribute WEIGHT = new TextAttribute("weight");
    public static final TextAttribute WIDTH = new TextAttribute("width");
    public static final TextAttribute POSTURE = new TextAttribute("posture");
    public static final TextAttribute SIZE = new TextAttribute("size");
    public static final TextAttribute TRANSFORM = new TextAttribute("transform");
    public static final TextAttribute SUPERSCRIPT = new TextAttribute("superscript");
    public static final TextAttribute FONT = new TextAttribute("font");
    public static final TextAttribute CHAR_REPLACEMENT = new TextAttribute("char_replacement");

    public static final TextAttribute FOREGROUND = new TextAttribute("foreground");
    public static final TextAttribute BACKGROUND = new TextAttribute("background");
    public static final TextAttribute UNDERLINE = new TextAttribute("underline");
    public static final TextAttribute STRIKETHROUGH = new TextAttribute("strikethrough");

    public static final TextAttribute RUN_DIRECTION = new TextAttribute("run_direction");
    public static final TextAttribute BIDI_EMBEDDING = new TextAttribute("bidi_embedding");
    public static final TextAttribute JUSTIFICATION = new TextAttribute("justification");

    public static final TextAttribute INPUT_METHOD_HIGHLIGHT = new TextAttribute("input method highlight");
    public static final TextAttribute INPUT_METHOD_UNDERLINE = new TextAttribute("input method underline");

    public static final TextAttribute SWAP_COLORS = new TextAttribute("swap_colors");
    public static final TextAttribute NUMERIC_SHAPING = new TextAttribute("numeric_shaping");
    public static final TextAttribute KERNING = new TextAttribute("kerning");
    public static final TextAttribute LIGATURES = new TextAttribute("ligatures");
    public static final TextAttribute TRACKING = new TextAttribute("tracking");

    // Public field types are part of the desktop bytecode ABI.
    public static final Float WEIGHT_EXTRA_LIGHT = 0.5f;
    public static final Float WEIGHT_LIGHT = 0.75f;
    public static final Float WEIGHT_DEMILIGHT = 0.875f;
    public static final Float WEIGHT_REGULAR = 1.0f;
    public static final Float WEIGHT_SEMIBOLD = 1.25f;
    public static final Float WEIGHT_MEDIUM = 1.5f;
    public static final Float WEIGHT_DEMIBOLD = 1.75f;
    public static final Float WEIGHT_BOLD = 2.0f;
    public static final Float WEIGHT_HEAVY = 2.25f;
    public static final Float WEIGHT_EXTRABOLD = 2.5f;
    public static final Float WEIGHT_ULTRABOLD = 2.75f;

    public static final Float WIDTH_CONDENSED = 0.75f;
    public static final Float WIDTH_SEMI_CONDENSED = 0.875f;
    public static final Float WIDTH_REGULAR = 1.0f;
    public static final Float WIDTH_SEMI_EXTENDED = 1.25f;
    public static final Float WIDTH_EXTENDED = 1.5f;

    public static final Float POSTURE_REGULAR = 0.0f;
    public static final Float POSTURE_OBLIQUE = 0.20f;

    public static final Integer SUPERSCRIPT_SUPER = 1;
    public static final Integer SUPERSCRIPT_SUB = -1;

    public static final Integer UNDERLINE_ON = 0;
    public static final Integer UNDERLINE_LOW_ONE_PIXEL = 1;
    public static final Integer UNDERLINE_LOW_TWO_PIXEL = 2;
    public static final Integer UNDERLINE_LOW_DOTTED = 3;
    public static final Integer UNDERLINE_LOW_GRAY = 4;
    public static final Integer UNDERLINE_LOW_DASHED = 5;

    public static final Boolean STRIKETHROUGH_ON = true;

    public static final Boolean SWAP_COLORS_ON = true;

    public static final Integer LIGATURES_ON = 1;

    public static final Integer KERNING_ON = 1;
    public static final Boolean RUN_DIRECTION_LTR = false;
    public static final Boolean RUN_DIRECTION_RTL = true;
    public static final Float JUSTIFICATION_FULL = 1f;
    public static final Float JUSTIFICATION_NONE = 0f;
    public static final Float TRACKING_TIGHT = -0.04f;
    public static final Float TRACKING_LOOSE = 0.04f;
}
