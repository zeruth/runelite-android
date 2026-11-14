package org.lwjgl;

import java.nio.*;
import org.lwjgl.system.MemoryUtil;

/** Native-width pointers stored in a direct buffer; values are unsigned on a 32-bit ABI. */
public final class PointerBuffer extends org.lwjgl.system.CustomBuffer<PointerBuffer> {
    public static final int POINTER_SIZE = MemoryUtil.POINTER_SIZE;
    private static ByteBuffer aligned(ByteBuffer bytes) {
        if (!bytes.isDirect()) throw new IllegalArgumentException("Direct buffer required");
        ByteBuffer slice = bytes.slice().order(ByteOrder.nativeOrder());
        slice.limit(slice.remaining() / POINTER_SIZE * POINTER_SIZE);
        return slice.slice().order(ByteOrder.nativeOrder());
    }
    private PointerBuffer(ByteBuffer bytes) { super(aligned(bytes), POINTER_SIZE); }
    private PointerBuffer(ByteBuffer bytes, boolean duplicate) { super(bytes, POINTER_SIZE); }
    public static PointerBuffer create(ByteBuffer buffer) { return new PointerBuffer(buffer); }
    public static PointerBuffer allocateDirect(int capacity) {
        return create(BufferUtils.createByteBuffer(Math.multiplyExact(capacity, POINTER_SIZE)));
    }
    @Override public PointerBuffer duplicate() { return new PointerBuffer(buffer.duplicate().order(ByteOrder.nativeOrder()), true); }
    @Override public PointerBuffer slice() { return create(buffer); }
    public long get() { return POINTER_SIZE == 8 ? buffer.getLong() : Integer.toUnsignedLong(buffer.getInt()); }
    public long get(int index) {
        int offset = offset(index);
        return POINTER_SIZE == 8 ? buffer.getLong(offset) : Integer.toUnsignedLong(buffer.getInt(offset));
    }
    public PointerBuffer put(long value) {
        if (POINTER_SIZE == 8) buffer.putLong(value); else buffer.putInt((int)value);
        return this;
    }
    public PointerBuffer put(int index, long value) {
        int offset = offset(index);
        if (POINTER_SIZE == 8) buffer.putLong(offset, value); else buffer.putInt(offset, (int)value);
        return this;
    }
    private int offset(int index) {
        if (index < 0 || index >= limit()) throw new IndexOutOfBoundsException("pointer index");
        return Math.multiplyExact(index, POINTER_SIZE);
    }
    public PointerBuffer put(long[] values) {
        if (values.length > remaining()) throw new BufferOverflowException();
        for (long value : values) put(value);
        return this;
    }
    public PointerBuffer get(long[] values) {
        if (values.length > remaining()) throw new BufferUnderflowException();
        for (int i = 0; i < values.length; i++) values[i] = get();
        return this;
    }
    public PointerBuffer put(PointerBuffer source) {
        if (source == this) throw new IllegalArgumentException("Cannot copy a buffer to itself");
        if (source.remaining() > remaining()) throw new BufferOverflowException();
        long[] values = new long[source.remaining()];
        source.get(values); put(values);
        return this;
    }
}
