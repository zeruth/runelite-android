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

public abstract class DataBuffer {
    public static final int TYPE_BYTE      = 0;
    public static final int TYPE_USHORT    = 1;
    public static final int TYPE_SHORT     = 2;
    public static final int TYPE_INT       = 3;
    public static final int TYPE_FLOAT     = 4;
    public static final int TYPE_DOUBLE    = 5;
    public static final int TYPE_UNDEFINED = 32;

    private static final int[] dataTypeSize = {8, 16, 16, 32, 32, 64};

    /** Size in bits of one element of {@code type}, as OpenJDK 8u answers it. */
    public static int getDataTypeSize(int type) {
        if (type < TYPE_BYTE || type > TYPE_DOUBLE) {
            throw new IllegalArgumentException("Unknown data type " + type);
        }
        return dataTypeSize[type];
    }

    protected int dataType;
    protected int banks;
    protected int size;
    protected int offset;
    protected int[] offsets;

    private static final int[] DATA_TYPE_SIZE = {8, 16, 16, 32, 32, 64};

    public static int getDataTypeSize(int type) {
        if (type < TYPE_BYTE || type > TYPE_DOUBLE) {
            throw new IllegalArgumentException("Unknown data type " + type);
        }
        return DATA_TYPE_SIZE[type];
    }

    protected DataBuffer(int dataType, int size) {
        this(dataType, size, 1, 0);
    }

    protected DataBuffer(int dataType, int size, int numBanks) {
        this(dataType, size, numBanks, 0);
    }

    protected DataBuffer(int dataType, int size, int numBanks, int offset) {
        this.dataType = dataType;
        this.banks = numBanks;
        this.size = size;
        this.offset = offset;
        this.offsets = new int[numBanks];
        for (int i = 0; i < numBanks; i++) this.offsets[i] = offset;
    }

    protected DataBuffer(int dataType, int size, int numBanks, int[] offsets) {
        this.dataType = dataType;
        this.banks = numBanks;
        this.size = size;
        this.offset = offsets[0];
        this.offsets = offsets.clone();
    }

    public int getDataType() { return dataType; }
    public int getSize() { return size; }
    public int getOffset() { return offset; }
    public int[] getOffsets() { return offsets.clone(); }
    public int getNumBanks() { return banks; }

    public int getElem(int i) { return getElem(0, i); }
    public abstract int getElem(int bank, int i);

    public void setElem(int i, int val) { setElem(0, i, val); }
    public abstract void setElem(int bank, int i, int val);

    public float getElemFloat(int i) { return getElem(i); }
    public float getElemFloat(int bank, int i) { return getElem(bank, i); }
    public void setElemFloat(int i, float val) { setElem(i, (int) val); }
    public void setElemFloat(int bank, int i, float val) { setElem(bank, i, (int) val); }

    public double getElemDouble(int i) { return getElem(i); }
    public double getElemDouble(int bank, int i) { return getElem(bank, i); }
    public void setElemDouble(int i, double val) { setElem(i, (int) val); }
    public void setElemDouble(int bank, int i, double val) { setElem(bank, i, (int) val); }
}
