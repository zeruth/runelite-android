package org.lwjgl.system;
import java.nio.*;
import org.lwjgl.PointerBuffer;
import java.util.ArrayList;
import org.lwjgl.BufferUtils;
public final class MemoryStack implements AutoCloseable {
    private static final ThreadLocal<ArrayList<IntBuffer>> POOL=ThreadLocal.withInitial(ArrayList::new);
    private final ArrayList<IntBuffer> borrowed=new ArrayList<>();
    private static final ThreadLocal<ArrayList<ByteBuffer>> BYTE_POOL = ThreadLocal.withInitial(ArrayList::new);
    private final ArrayList<ByteBuffer> borrowedBytes = new ArrayList<>();
    private final Thread owner = Thread.currentThread();
    private boolean closed;
    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Stack frame is closed");
        if (Thread.currentThread() != owner) throw new IllegalStateException("Stack frame belongs to another thread");
    }
    public static MemoryStack stackPush() { return new MemoryStack(); }
    public IntBuffer mallocInt(int n) {
        ensureOpen();
        ArrayList<IntBuffer> pool=POOL.get();
        IntBuffer b=null;
        for(int i=0;i<pool.size();i++) if(pool.get(i).capacity()>=n) { b=pool.remove(i); break; }
        if(b==null) b=BufferUtils.createIntBuffer(n);
        b.clear().limit(n);
        borrowed.add(b);
        // LWJGL allocations have exactly the requested capacity. Returning the pooled
        // owner's larger capacity lets callers overestimate a paired buffer's size.
        return b.slice();
    }
    public IntBuffer callocInt(int n) { IntBuffer b=mallocInt(n); for(int i=0;i<n;i++) b.put(i,0); return b; }
    public ByteBuffer malloc(int bytes) {
        ensureOpen();
        if (bytes < 0) throw new IllegalArgumentException("Negative allocation size");
        ArrayList<ByteBuffer> pool = BYTE_POOL.get();
        ByteBuffer buffer = null;
        for (int i = 0; i < pool.size(); i++) if (pool.get(i).capacity() >= bytes) { buffer = pool.remove(i); break; }
        if (buffer == null) buffer = BufferUtils.createByteBuffer(bytes);
        buffer.clear().limit(bytes);
        borrowedBytes.add(buffer);
        return buffer.slice().order(ByteOrder.nativeOrder());
    }
    public ByteBuffer calloc(int bytes) {
        ByteBuffer buffer = malloc(bytes);
        for (int i = 0; i < bytes; i++) buffer.put(i, (byte)0);
        return buffer;
    }
    public LongBuffer mallocLong(int count) { return malloc(Math.multiplyExact(count, Long.BYTES)).asLongBuffer(); }
    public LongBuffer callocLong(int count) {
        LongBuffer buffer = mallocLong(count);
        for (int i = 0; i < count; i++) buffer.put(i, 0L);
        return buffer;
    }
    public PointerBuffer mallocPointer(int count) { return PointerBuffer.create(malloc(Math.multiplyExact(count, MemoryUtil.POINTER_SIZE))); }
    public PointerBuffer callocPointer(int count) {
        PointerBuffer buffer = mallocPointer(count);
        for (int i = 0; i < count; i++) buffer.put(i, 0L);
        return buffer;
    }
    public PointerBuffer pointers(long value) { return mallocPointer(1).put(0, value); }
    public PointerBuffer pointers(long... values) { return mallocPointer(values.length).put(values).flip(); }
    public void close() {
        if (closed) return;
        ensureOpen();
        ArrayList<IntBuffer> pool = POOL.get();
        for (IntBuffer buffer : borrowed) if (pool.size() < 16) pool.add(buffer);
        borrowed.clear();
        ArrayList<ByteBuffer> bytes = BYTE_POOL.get();
        for (ByteBuffer buffer : borrowedBytes) if (bytes.size() < 16) bytes.add(buffer);
        borrowedBytes.clear();
        closed = true;
    }
}
