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

/** Signed 16-bit samples; {@link #getElem} sign-extends, unlike {@link DataBufferUShort}. */
public final class DataBufferShort extends DataBuffer {
    private final short[][] data;

    public DataBufferShort(int size) { this(size, 1); }
    public DataBufferShort(int size, int numBanks) { super(TYPE_SHORT, size, numBanks); data = new short[numBanks][size]; }
    public DataBufferShort(short[] dataArray, int size) { this(dataArray, size, 0); }
    public DataBufferShort(short[] dataArray, int size, int offset) { super(TYPE_SHORT, size, 1, offset); data = new short[][]{dataArray}; }
    public DataBufferShort(short[][] dataArray, int size) { super(TYPE_SHORT, size, dataArray.length); data = dataArray.clone(); }
    public DataBufferShort(short[][] dataArray, int size, int[] offsets) { super(TYPE_SHORT, size, dataArray.length, offsets); data = dataArray.clone(); }

    public short[] getData() { return data[0]; }
    public short[] getData(int bank) { return data[bank]; }
    public short[][] getBankData() { return data.clone(); }

    public int getElem(int i) { return data[0][i + offset]; }
    public int getElem(int bank, int i) { return data[bank][i + offsets[bank]]; }
    public void setElem(int i, int val) { data[0][i + offset] = (short) val; }
    public void setElem(int bank, int i, int val) { data[bank][i + offsets[bank]] = (short) val; }
}
