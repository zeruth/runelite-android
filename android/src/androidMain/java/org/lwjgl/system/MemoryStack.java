package org.lwjgl.system;
import java.nio.IntBuffer;
import java.util.ArrayList;
import org.lwjgl.BufferUtils;
public final class MemoryStack implements AutoCloseable {
    private static final ThreadLocal<ArrayList<IntBuffer>> POOL=ThreadLocal.withInitial(ArrayList::new);
    private final ArrayList<IntBuffer> borrowed=new ArrayList<>();
    public static MemoryStack stackPush() { return new MemoryStack(); }
    public IntBuffer mallocInt(int n) {
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
    public void close() { ArrayList<IntBuffer> pool=POOL.get(); for(IntBuffer b:borrowed) if(pool.size()<16) pool.add(b); borrowed.clear(); }
}
