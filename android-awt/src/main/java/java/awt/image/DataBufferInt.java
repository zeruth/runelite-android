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

public final class DataBufferInt extends DataBuffer {
    int[] data;
    int[][] bankdata;

    public DataBufferInt(int size) {
        super(TYPE_INT, size);
        data = new int[size];
        bankdata = new int[1][];
        bankdata[0] = data;
    }

    public DataBufferInt(int size, int numBanks) {
        super(TYPE_INT, size, numBanks);
        bankdata = new int[numBanks][];
        for (int i = 0; i < numBanks; i++) {
            bankdata[i] = new int[size];
        }
        data = bankdata[0];
    }

    public DataBufferInt(int[] dataArray, int size) {
        super(TYPE_INT, size);
        if (dataArray.length < size) {
            throw new IllegalArgumentException("dataArray.length (" + dataArray.length + ") < size (" + size + ")");
        }
        data = dataArray;
        bankdata = new int[1][];
        bankdata[0] = data;
    }

    public DataBufferInt(int[] dataArray, int size, int offset) {
        super(TYPE_INT, size, 1, offset);
        data = dataArray;
        bankdata = new int[1][];
        bankdata[0] = data;
    }

    public DataBufferInt(int[][] dataArray, int size) {
        super(TYPE_INT, size, dataArray.length);
        bankdata = dataArray.clone();
        data = bankdata[0];
    }

    public DataBufferInt(int[][] dataArray, int size, int[] offsets) {
        super(TYPE_INT, size, dataArray.length, offsets);
        bankdata = dataArray.clone();
        data = bankdata[0];
    }

    public int[] getData() {
        return data;
    }

    public int[] getData(int bank) {
        return bankdata[bank];
    }

    public int[][] getBankData() {
        return bankdata.clone();
    }

    @Override
    public int getElem(int i) {
        return data[i + offset];
    }

    @Override
    public int getElem(int bank, int i) {
        return bankdata[bank][i + offsets[bank]];
    }

    @Override
    public void setElem(int i, int val) {
        data[i + offset] = val;
    }

    @Override
    public void setElem(int bank, int i, int val) {
        bankdata[bank][i + offsets[bank]] = val;
    }
}
