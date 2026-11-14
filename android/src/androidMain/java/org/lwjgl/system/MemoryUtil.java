package org.lwjgl.system;

import java.nio.*;

/** Explicit native allocations for the renderer's buffers and model cache. */
public final class MemoryUtil {
    static { System.loadLibrary("hdmemory"); }
    private static native long allocate(long bytes);
    private static native ByteBuffer view(long address,int bytes);
    private static native int pointerSize();
    private static native byte[] readNullTerminated(long address);
    public static final int POINTER_SIZE = pointerSize();
    public static native void nmemFree(long address);
    public static native long memAddress0(Buffer buffer);
    public static long nmemAllocChecked(long n) {
        if(n<=0) throw new IllegalArgumentException("Allocation size must be positive");
        long p=allocate(n); if(p==0) throw new OutOfMemoryError("117 HD native allocation: "+n); return p;
    }
    public static ByteBuffer memAlloc(int n) {
        if (n < 0) throw new IllegalArgumentException("Negative allocation size");
        long address = nmemAllocChecked(Math.max(n, 1));
        try {
            ByteBuffer buffer = view(address, n);
            if (buffer == null) throw new OutOfMemoryError("Native buffer view allocation");
            return buffer.order(ByteOrder.nativeOrder());
        } catch (RuntimeException | Error failure) {
            nmemFree(address);
            throw failure;
        }
    }
    public static ByteBuffer memRealloc(ByteBuffer old,int n) {
        ByteBuffer replacement=memAlloc(n);
        if(old!=null) {
            int position=old.position();
            ByteBuffer source=old.duplicate(); source.clear().limit(Math.min(old.capacity(),n));
            replacement.put(source).position(Math.min(position,n));
            memFree(old);
        }
        return replacement;
    }
    public static IntBuffer memAllocInt(int n) { return memAlloc(Math.multiplyExact(n,4)).asIntBuffer(); }
    public static ShortBuffer memAllocShort(int n) { return memAlloc(Math.multiplyExact(n,2)).asShortBuffer(); }
    public static FloatBuffer memAllocFloat(int n) { return memAlloc(Math.multiplyExact(n,4)).asFloatBuffer(); }
    public static IntBuffer memIntBuffer(long p,int n) { return view(p,Math.multiplyExact(n,4)).order(ByteOrder.nativeOrder()).asIntBuffer(); }
    public static FloatBuffer memFloatBuffer(long p,int n) { return view(p,Math.multiplyExact(n,4)).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
    public static void memFree(Buffer buffer) { if(buffer!=null) nmemFree(memAddress0(buffer)); }
    public static long memAddress(Buffer buffer) {
        if (!buffer.isDirect()) throw new IllegalArgumentException("Direct buffer required");
        int size = buffer instanceof ByteBuffer ? 1 : buffer instanceof ShortBuffer || buffer instanceof CharBuffer ? 2
            : buffer instanceof LongBuffer || buffer instanceof DoubleBuffer ? 8 : 4;
        return memAddress0(buffer) + (long) buffer.position() * size;
    }
    public static long memAddress(org.lwjgl.PointerBuffer buffer) { return buffer.address(); }
    private static byte[] bytes(ByteBuffer buffer, int length) {
        if (length < 0 || length > buffer.remaining()) throw new IndexOutOfBoundsException("String length exceeds buffer range");
        byte[] bytes = new byte[length];
        buffer.duplicate().get(bytes);
        return bytes;
    }
    public static String memASCII(ByteBuffer buffer, int length) {
        return new String(bytes(buffer, length), java.nio.charset.StandardCharsets.US_ASCII);
    }
    public static String memUTF8(ByteBuffer buffer, int length) {
        return new String(bytes(buffer, length), java.nio.charset.StandardCharsets.UTF_8);
    }
    public static String memUTF8(long address) {
        if (address == 0) throw new IllegalArgumentException("Null string address");
        return new String(readNullTerminated(address), java.nio.charset.StandardCharsets.UTF_8);
    }
}
