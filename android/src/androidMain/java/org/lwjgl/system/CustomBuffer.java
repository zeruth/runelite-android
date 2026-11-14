package org.lwjgl.system;

import java.nio.ByteBuffer;

/** Shared buffer state; generic return types preserve LWJGL's erased method descriptors. */
public abstract class CustomBuffer<SELF extends CustomBuffer<SELF>> extends Pointer.Default {
    protected final ByteBuffer buffer;
    private final int elementSize;
    protected CustomBuffer(ByteBuffer buffer, int elementSize) {
        super(MemoryUtil.memAddress0(buffer));
        this.buffer = buffer; this.elementSize = elementSize;
    }
    @SuppressWarnings("unchecked") private SELF self() { return (SELF)this; }
    public int sizeof() { return elementSize; }
    public int capacity() { return buffer.capacity() / elementSize; }
    public int position() { return buffer.position() / elementSize; }
    public SELF position(int position) { buffer.position(Math.multiplyExact(position, elementSize)); return self(); }
    public int limit() { return buffer.limit() / elementSize; }
    public SELF limit(int limit) { buffer.limit(Math.multiplyExact(limit, elementSize)); return self(); }
    public int remaining() { return buffer.remaining() / elementSize; }
    public boolean hasRemaining() { return remaining() != 0; }
    public boolean isReadOnly() { return buffer.isReadOnly(); }
    public SELF clear() { buffer.clear(); return self(); }
    public SELF flip() { buffer.flip(); return self(); }
    public SELF rewind() { buffer.rewind(); return self(); }
    public SELF mark() { buffer.mark(); return self(); }
    public SELF reset() { buffer.reset(); return self(); }
    @Override public long address() { return address + buffer.position(); }
    public long address(int position) {
        if (position < 0 || position > limit()) throw new IndexOutOfBoundsException("buffer index");
        return address + (long)position * elementSize;
    }
    public abstract SELF duplicate();
    public abstract SELF slice();
}
