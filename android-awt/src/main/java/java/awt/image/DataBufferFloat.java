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
package java.awt.image;

/** {@code float} samples; the integer accessors truncate toward zero, as the JDK's do. */
public final class DataBufferFloat extends DataBuffer {
    private final float[][] data;

    public DataBufferFloat(int size) { this(size, 1); }
    public DataBufferFloat(int size, int numBanks) { super(TYPE_FLOAT, size, numBanks); data = new float[numBanks][size]; }
    public DataBufferFloat(float[] dataArray, int size) { this(dataArray, size, 0); }
    public DataBufferFloat(float[] dataArray, int size, int offset) { super(TYPE_FLOAT, size, 1, offset); data = new float[][]{dataArray}; }
    public DataBufferFloat(float[][] dataArray, int size) { super(TYPE_FLOAT, size, dataArray.length); data = dataArray.clone(); }
    public DataBufferFloat(float[][] dataArray, int size, int[] offsets) { super(TYPE_FLOAT, size, dataArray.length, offsets); data = dataArray.clone(); }

    public float[] getData() { return data[0]; }
    public float[] getData(int bank) { return data[bank]; }
    public float[][] getBankData() { return data.clone(); }

    public int getElem(int i) { return (int) data[0][i + offset]; }
    public int getElem(int bank, int i) { return (int) data[bank][i + offsets[bank]]; }
    public void setElem(int i, int val) { data[0][i + offset] = (float) val; }
    public void setElem(int bank, int i, int val) { data[bank][i + offsets[bank]] = (float) val; }
    public float getElemFloat(int i) { return data[0][i + offset]; }
    public float getElemFloat(int bank, int i) { return data[bank][i + offsets[bank]]; }
    public void setElemFloat(int i, float val) { data[0][i + offset] = val; }
    public void setElemFloat(int bank, int i, float val) { data[bank][i + offsets[bank]] = val; }
    public double getElemDouble(int i) { return data[0][i + offset]; }
    public double getElemDouble(int bank, int i) { return data[bank][i + offsets[bank]]; }
    public void setElemDouble(int i, double val) { data[0][i + offset] = (float) val; }
    public void setElemDouble(int bank, int i, double val) { data[bank][i + offsets[bank]] = (float) val; }
}
