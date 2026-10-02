package org.lwjgl.system;

import java.nio.*;

/** Explicit native allocations for the renderer's buffers and model cache. */
public final class MemoryUtil {
    static { System.loadLibrary("hdmemory"); }
    private static native long allocate(long bytes);
    private static native ByteBuffer view(long address,int bytes);
    public static native void nmemFree(long address);
    public static native long memAddress0(Buffer buffer);
    public static long nmemAllocChecked(long n) {
        if(n<=0) throw new IllegalArgumentException("Allocation size must be positive");
        long p=allocate(n); if(p==0) throw new OutOfMemoryError("117 HD native allocation: "+n); return p;
    }
    public static ByteBuffer memAlloc(int n) { return view(nmemAllocChecked(Math.max(n,1)),n).order(ByteOrder.nativeOrder()); }
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
    public static FloatBuffer memAllocFloat(int n) { return memAlloc(Math.multiplyExact(n,4)).asFloatBuffer(); }
    public static IntBuffer memIntBuffer(long p,int n) { return view(p,Math.multiplyExact(n,4)).order(ByteOrder.nativeOrder()).asIntBuffer(); }
    public static FloatBuffer memFloatBuffer(long p,int n) { return view(p,Math.multiplyExact(n,4)).order(ByteOrder.nativeOrder()).asFloatBuffer(); }
    public static void memFree(Buffer buffer) { if(buffer!=null) nmemFree(memAddress0(buffer)); }
}
