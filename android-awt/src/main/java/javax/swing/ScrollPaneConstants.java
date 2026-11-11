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

public interface ScrollPaneConstants {
    String VIEWPORT = "VIEWPORT";
    String VERTICAL_SCROLLBAR = "VERTICAL_SCROLLBAR";
    String HORIZONTAL_SCROLLBAR = "HORIZONTAL_SCROLLBAR";
    String ROW_HEADER = "ROW_HEADER";
    String COLUMN_HEADER = "COLUMN_HEADER";
    String LOWER_LEFT_CORNER = "LOWER_LEFT_CORNER";
    String LOWER_RIGHT_CORNER = "LOWER_RIGHT_CORNER";
    String UPPER_LEFT_CORNER = "UPPER_LEFT_CORNER";
    String UPPER_RIGHT_CORNER = "UPPER_RIGHT_CORNER";
    String LOWER_LEADING_CORNER = "LOWER_LEADING_CORNER";
    String LOWER_TRAILING_CORNER = "LOWER_TRAILING_CORNER";
    String UPPER_LEADING_CORNER = "UPPER_LEADING_CORNER";
    String UPPER_TRAILING_CORNER = "UPPER_TRAILING_CORNER";

    int VERTICAL_SCROLLBAR_AS_NEEDED = 20;
    int VERTICAL_SCROLLBAR_NEVER = 21;
    int VERTICAL_SCROLLBAR_ALWAYS = 22;
    int HORIZONTAL_SCROLLBAR_AS_NEEDED = 30;
    int HORIZONTAL_SCROLLBAR_NEVER = 31;
    int HORIZONTAL_SCROLLBAR_ALWAYS = 32;

    String VERTICAL_SCROLLBAR_POLICY = "VERTICAL_SCROLLBAR_POLICY";
    String HORIZONTAL_SCROLLBAR_POLICY = "HORIZONTAL_SCROLLBAR_POLICY";
    String VIEWPORT_BORDER = "VIEWPORT_BORDER";
    String COLUMN_HEADER_POLICY = "COLUMN_HEADER_POLICY";
    String ROW_HEADER_POLICY = "ROW_HEADER_POLICY";
}
