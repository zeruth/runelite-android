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
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;

public class BorderFactory {
    private BorderFactory() {}

    public static Border createEmptyBorder() { return new EmptyBorder(0, 0, 0, 0); }
    public static Border createEmptyBorder(int t, int l, int b, int r) { return new EmptyBorder(t, l, b, r); }

    public static Border createLineBorder(Color color) { return new LineBorder(color); }
    public static Border createLineBorder(Color color, int thickness) { return new LineBorder(color, thickness); }
    public static Border createLineBorder(Color color, int thickness, boolean rounded) { return new LineBorder(color, thickness, rounded); }

    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, Color color) {
        return new MatteBorder(top, left, bottom, right, color);
    }
    public static MatteBorder createMatteBorder(int top, int left, int bottom, int right, Icon tile) {
        return new MatteBorder(top, left, bottom, right, tile);
    }

    public static CompoundBorder createCompoundBorder() { return new CompoundBorder(); }
    public static CompoundBorder createCompoundBorder(Border outside, Border inside) { return new CompoundBorder(outside, inside); }

    public static Border createRaisedBevelBorder() { return new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.RAISED); }
    public static Border createLoweredBevelBorder() { return new javax.swing.border.BevelBorder(javax.swing.border.BevelBorder.LOWERED); }
    public static Border createEtchedBorder() { return new javax.swing.border.EtchedBorder(); }
    public static Border createEtchedBorder(Color highlight, Color shadow) { return new javax.swing.border.EtchedBorder(highlight,shadow); }
    public static javax.swing.border.TitledBorder createTitledBorder(String title) { return new javax.swing.border.TitledBorder(title); }
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title) { return new javax.swing.border.TitledBorder(border,title); }
    public static Border createDashedBorder(java.awt.Paint paint) { return new EmptyBorder(1, 1, 1, 1); }
    public static javax.swing.border.TitledBorder createTitledBorder(Border border){return new javax.swing.border.TitledBorder(border);}
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title,int justification,int position){return new javax.swing.border.TitledBorder(border,title,justification,position);}
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title,int justification,int position,java.awt.Font font){return new javax.swing.border.TitledBorder(border,title,justification,position,font);}
    public static javax.swing.border.TitledBorder createTitledBorder(Border border,String title,int justification,int position,java.awt.Font font,Color color){return new javax.swing.border.TitledBorder(border,title,justification,position,font,color);}
    public static Border createEtchedBorder(int type){return new javax.swing.border.EtchedBorder(type);}
    public static Border createEtchedBorder(int type,Color highlight,Color shadow){return new javax.swing.border.EtchedBorder(type,highlight,shadow);}
    public static Border createBevelBorder(int type){return new javax.swing.border.BevelBorder(type);}
}
