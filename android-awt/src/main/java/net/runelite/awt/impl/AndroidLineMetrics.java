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
public final class AndroidLineMetrics extends java.awt.font.LineMetrics {
    private final android.graphics.Paint.FontMetrics metrics;private final int count;private final float size;
    public AndroidLineMetrics(java.awt.Font font,int count){metrics=BufferedImageFontMetrics.paintFor(font).getFontMetrics();this.count=count;size=font.getSize2D();}
    public int getNumChars(){return count;}public float getAscent(){return -metrics.ascent;}public float getDescent(){return metrics.descent;}public float getLeading(){return metrics.leading;}public float getHeight(){return getAscent()+getDescent()+getLeading();}
    public int getBaselineIndex(){return 0;}public float[] getBaselineOffsets(){return new float[]{0,(metrics.ascent+metrics.descent)/2,metrics.ascent};}
    public float getStrikethroughOffset(){return -size*0.3f;}public float getStrikethroughThickness(){return Math.max(1,size/16);}
    public float getUnderlineOffset(){return metrics.descent/2;}public float getUnderlineThickness(){return Math.max(1,size/16);}
}
