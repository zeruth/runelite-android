package org.lwjgl.opengl;

import android.opengl.GLES32;
import java.nio.*;
import static android.opengl.GLES32.*;

/** The LWJGL entry points used by 117 HD, implemented by the Android GLES driver. */
public class GL33C extends GlesBindings {
    // Mirror the face texture's backing buffer at the binding used by AndroidSupport.
    private static final ThreadLocal<java.util.Map<Integer, Integer>> faceBuffers =
        ThreadLocal.withInitial(java.util.HashMap::new);

    // Per-thread scratch buffers so the single-value glGet* / glGen* getters don't allocate a
    // fresh array on every call (they run several times per frame during state checks).
    private static final ThreadLocal<int[]> INT1 = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<float[]> FLOAT1 = ThreadLocal.withInitial(() -> new float[1]);
    private static final ThreadLocal<long[]> LONG1 = ThreadLocal.withInitial(() -> new long[1]);
    public static void glTexBuffer(int target, int format, int buffer) {
        GLES32.glTexBuffer(target, format, buffer);
        if (target == GL_TEXTURE_BUFFER && format == GL_RGB32I) {
            faceBuffers.get().put(glGetInteger(GL_TEXTURE_BINDING_BUFFER), buffer);
        }
    }
    public static void glBindTexture(int target, int texture) {
        GLES32.glBindTexture(target, texture);
        if (target == GL_TEXTURE_BUFFER) {
            GLES32.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0,
                faceBuffers.get().getOrDefault(texture, 0));
        }
    }
    static { System.loadLibrary("hdmemory"); }
    private static int bytes(Buffer b) {
        int width = b instanceof ByteBuffer ? 1 : b instanceof ShortBuffer ? 2 : b instanceof LongBuffer || b instanceof DoubleBuffer ? 8 : 4;
        return Math.multiplyExact(b.remaining(), width);
    }
    public static void glBlendFunc(int source, int destination) { GLES32.glBlendFunc(source, destination); }
    public static int glCheckFramebufferStatus(int target) { return GLES32.glCheckFramebufferStatus(target); }
    public static void glUniform1iv(int location, int[] values) { GLES32.glUniform1iv(location, values.length, values, 0); }
    public static void glBufferData(int target, IntBuffer data, int usage) { GLES32.glBufferData(target, bytes(data), data, usage); }
    public static ByteBuffer glMapBuffer(int target, int access) { return glMapBuffer(target, access, null); }
    public static void glTexImage3D(int target, int level, int internalFormat, int width, int height, int depth,
            int border, int format, int type, ShortBuffer data) {
        checkClientPixels(false);
        if (data != null) checkPixelStorage(data, width, height, depth, format, type, false);
        GLES32.glTexImage3D(target, level, internalFormat, width, height, depth, border, format, type, data);
    }
    public static void glTexSubImage3D(int target, int level, int x, int y, int z, int width, int height, int depth,
            int format, int type, ByteBuffer data) {
        checkClientPixels(false);
        checkPixelStorage(data, width, height, depth, format, type, false);
        GLES32.glTexSubImage3D(target, level, x, y, z, width, height, depth, format, type, data);
    }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, int[] pixels) {
        checkClientPixels(true);
        int capacity = Math.multiplyExact(pixels.length, Integer.BYTES);
        long required = pixelStorage(width, height, 1, format, type, true);
        if (required > capacity) throw new IllegalArgumentException("Pixel array is too small for the GL pack state");
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(capacity);
            data.asIntBuffer().put(pixels);
            GLES32.glReadPixels(x, y, width, height, format, type, data);
            data.asIntBuffer().get(pixels);
        }
    }
    private static void checkClientPixels(boolean pack) {
        if (glGetInteger(pack ? 0x88ED : 0x88EF) != 0)
            throw new IllegalStateException("Client pixel buffers require the pixel buffer object to be unbound");
    }
    private static void checkPixelStorage(Buffer data, int width, int height, int depth, int format, int type, boolean pack) {
        if (!data.isDirect()) throw new IllegalArgumentException("Direct pixel buffer required");
        if (pixelStorage(width, height, depth, format, type, pack) > bytes(data))
            throw new IllegalArgumentException("Pixel buffer is too small for the GL pixel store state");
    }
    private static long pixelStorage(int width, int height, int depth, int format, int type, boolean pack) {
        if (width < 0 || height < 0 || depth < 0) throw new IllegalArgumentException("Negative image size");
        if (width == 0 || height == 0 || depth == 0) return 0;
        int pixel = pixelBytes(format, type);
        int alignment = glGetInteger(pack ? 0x0D05 : 0x0CF5);
        int rowLength = glGetInteger(pack ? 0x0D02 : 0x0CF2);
        int skipRows = glGetInteger(pack ? 0x0D03 : 0x0CF3);
        int skipPixels = glGetInteger(pack ? 0x0D04 : 0x0CF4);
        int imageHeight = pack ? height : glGetInteger(0x806E);
        int skipImages = pack ? 0 : glGetInteger(0x806D);
        if (alignment != 1 && alignment != 2 && alignment != 4 && alignment != 8)
            throw new IllegalStateException("Invalid GL pixel alignment or no current context");
        long row = Math.multiplyExact((long)(rowLength == 0 ? width : rowLength), pixel);
        row = Math.multiplyExact((Math.addExact(row, alignment - 1)) / alignment, alignment);
        long image = Math.multiplyExact(row, imageHeight == 0 ? height : imageHeight);
        long images = Math.multiplyExact((long)skipImages + depth - 1, image);
        long rows = Math.multiplyExact((long)skipRows + height - 1, row);
        long last = Math.multiplyExact((long)skipPixels + width, pixel);
        return Math.addExact(Math.addExact(images, rows), last);
    }
    private static int pixelBytes(int format, int type) {
        switch (type) {
            case 0x8363: case 0x8033: case 0x8034: return 2;
            case 0x8368: case 0x8C3B: case 0x8C3E: case 0x84FA: return 4;
            case 0x8DAD: return 8;
        }
        int component;
        switch (type) {
            case 0x1400: case 0x1401: component = 1; break;
            case 0x1402: case 0x1403: case 0x140B: component = 2; break;
            case 0x1404: case 0x1405: case 0x1406: component = 4; break;
            default: throw new UnsupportedOperationException("Unsupported GLES pixel type: " + type);
        }
        int channels;
        switch (format) {
            case 0x1902: case 0x1903: case 0x8D94: channels = 1; break;
            case 0x8227: case 0x8228: channels = 2; break;
            case 0x1907: case 0x8D98: channels = 3; break;
            case 0x1908: case 0x8D99: channels = 4; break;
            default: throw new UnsupportedOperationException("Unsupported GLES pixel format: " + format);
        }
        return component * channels;
    }
    public static int glGenBuffers() { int[] v = INT1.get(); GLES32.glGenBuffers(1,v,0); return v[0]; }
    public static int glGenTextures() { int[] v = INT1.get(); GLES32.glGenTextures(1,v,0); return v[0]; }
    public static int glGenFramebuffers() { int[] v = INT1.get(); GLES32.glGenFramebuffers(1,v,0); return v[0]; }
    public static int glGenRenderbuffers() { int[] v = INT1.get(); GLES32.glGenRenderbuffers(1,v,0); return v[0]; }
    public static int glGenVertexArrays() { int[] v = INT1.get(); GLES32.glGenVertexArrays(1,v,0); return v[0]; }
    public static void glGenQueries(int[] v) { GLES32.glGenQueries(v.length,v,0); }
    public static void glDeleteBuffers(int v) { GLES32.glDeleteBuffers(1,new int[]{v},0); }
    public static void glDeleteTextures(int v) { faceBuffers.get().remove(v); GLES32.glDeleteTextures(1,new int[]{v},0); }
    public static void glDeleteFramebuffers(int v) { GLES32.glDeleteFramebuffers(1,new int[]{v},0); }
    public static void glDeleteRenderbuffers(int v) { GLES32.glDeleteRenderbuffers(1,new int[]{v},0); }
    public static void glDeleteVertexArrays(int v) { GLES32.glDeleteVertexArrays(1,new int[]{v},0); }
    public static void glDeleteQueries(int[] v) { GLES32.glDeleteQueries(v.length,v,0); }
    public static int glGetInteger(int p) { int[] v = INT1.get(); GLES32.glGetIntegerv(p,v,0); return v[0]; }
    public static float glGetFloat(int p) { float[] v = FLOAT1.get(); GLES32.glGetFloatv(p,v,0); return v[0]; }
    public static int glGetShaderi(int s,int p) { int[] v = INT1.get(); GLES32.glGetShaderiv(s,p,v,0); return v[0]; }
    public static int glGetProgrami(int s,int p) { int[] v = INT1.get(); GLES32.glGetProgramiv(s,p,v,0); return v[0]; }
    public static long glGetBufferParameteri64(int s,int p) { long[] v = LONG1.get(); GLES32.glGetBufferParameteri64v(s,p,v,0); return v[0]; }
    public static int glGetFramebufferAttachmentParameteri(int t,int a,int p) {
        int[] v=INT1.get(); GLES32.glGetFramebufferAttachmentParameteriv(t,a==0x0402?GL_BACK:a,p,v,0); return v[0];
    }
    public static void glGetIntegerv(int p,int[] v) { GLES32.glGetIntegerv(p,v,0); }
    public static void glGetProgramiv(int s,int p,int[] v) { GLES32.glGetProgramiv(s,p,v,0); }
    public static void glGetQueryObjectiv(int s,int p,int[] v) {
        if (p == 0x8867) { // GL_QUERY_RESULT_AVAILABLE: route through the native helper so the
            // FrameTimer busy-wait exits when the extension is absent (claim available) instead
            // of spinning on an extension function that never completes.
            v[0] = timerQueryAvailable(s) ? 1 : 0;
            return;
        }
        GLES32.glGetQueryObjectuiv(s,p,v,0);
    }
    public static void glBufferData(int t,long n,int u) { GLES32.glBufferData(t,Math.toIntExact(n),null,u); }
    public static void glBufferData(int t,FloatBuffer b,int u) { GLES32.glBufferData(t,bytes(b),b,u); }
    public static void glBufferSubData(int t,long o,ByteBuffer b) { GLES32.glBufferSubData(t,Math.toIntExact(o),bytes(b),b); }
    public static void glBufferSubData(int t,long o,FloatBuffer b) { GLES32.glBufferSubData(t,Math.toIntExact(o),bytes(b),b); }
    public static void glBufferSubData(int t,long o,IntBuffer b) { GLES32.glBufferSubData(t,Math.toIntExact(o),bytes(b),b); }
    public static void glCopyBufferSubData(int r,int w,long ro,long wo,long n) { GLES32.glCopyBufferSubData(r,w,Math.toIntExact(ro),Math.toIntExact(wo),Math.toIntExact(n)); }
    public static ByteBuffer glMapBufferRange(int t,long o,long n,int f) {
        ByteBuffer b=(ByteBuffer) GLES32.glMapBufferRange(t,Math.toIntExact(o),Math.toIntExact(n),f);
        return b==null?null:b.order(ByteOrder.nativeOrder());
    }
    public static ByteBuffer glMapBufferRange(int t,long o,long n,int f,ByteBuffer old) { return glMapBufferRange(t,o,n,f); }
    public static ByteBuffer glMapBuffer(int t,int access,ByteBuffer old) {
        int flags=access==0x88B8?GL_MAP_READ_BIT:access==0x88B9?GL_MAP_WRITE_BIT:GL_MAP_READ_BIT|GL_MAP_WRITE_BIT;
        return glMapBufferRange(t,0,glGetBufferParameteri64(t,GL_BUFFER_SIZE),flags);
    }
    public static void glFlushMappedBufferRange(int t,long o,long n) { if(n>0) GLES32.glFlushMappedBufferRange(t,Math.toIntExact(o),Math.toIntExact(n)); }
    public static void glClearDepth(double d) { GLES32.glClearDepthf((float)d); }
    public static void glCullFace(int mode) { GLES32.glCullFace(mode); }
    public static void glPolygonOffset(float factor,float units) { GLES32.glPolygonOffset(factor,units); }
    public static void glDrawBuffer(int mode) { GLES32.glDrawBuffers(1,new int[]{mode},0); }
    public static void glDrawElements(int m,int n,int t,long o) { GLES32.glDrawElements(m,n,t,Math.toIntExact(o)); }
    public static void glDrawArraysInstanced(int mode,int first,int count,int instances) { GLES32.glDrawArraysInstanced(mode,first,count,instances); }
    public static void glMultiDrawArrays(int m,IntBuffer first,IntBuffer count) {
        for(int i=0;i<count.remaining();i++) GLES32.glDrawArrays(m,first.get(first.position()+i),count.get(count.position()+i));
    }
    public static void glVertexAttribPointer(int i,int n,int t,boolean norm,int stride,long off) { GLES32.glVertexAttribPointer(i,n,t,norm,stride,Math.toIntExact(off)); }
    public static void glVertexAttribIPointer(int i,int n,int t,int stride,long off) { GLES32.glVertexAttribIPointer(i,n,t,stride,Math.toIntExact(off)); }
    public static int glGetUniformBlockIndex(int p,CharSequence n) { return GLES32.glGetUniformBlockIndex(p,n.toString()); }
    public static int glGetUniformLocation(int p,CharSequence n) { return GLES32.glGetUniformLocation(p,n.toString()); }
    public static void glShaderSource(int s,CharSequence src) { GLES32.glShaderSource(s,src.toString()); }
    public static void glUniform2fv(int l,float[] v) { GLES32.glUniform2fv(l,v.length/2,v,0); }
    public static void glUniform3fv(int l,float[] v) { GLES32.glUniform3fv(l,v.length/3,v,0); }
    public static void glUniform4fv(int l,float[] v) { GLES32.glUniform4fv(l,v.length/4,v,0); }
    public static void glUniform2iv(int l,int[] v) { GLES32.glUniform2iv(l,v.length/2,v,0); }
    public static void glUniform3iv(int l,int[] v) { GLES32.glUniform3iv(l,v.length/3,v,0); }
    public static void glUniform4iv(int l,int[] v) { GLES32.glUniform4iv(l,v.length/4,v,0); }
    public static void glUniformMatrix4fv(int l,boolean tr,float[] v) { GLES32.glUniformMatrix4fv(l,v.length/16,tr,v,0); }
    public static void glTexParameterfv(int t,int p,float[] v) { GLES32.glTexParameterfv(t,p,v,0); }
    public static void glTexImage2D(int t,int l,int in,int w,int h,int b,int f,int ty,long ptr) {
        if(ptr!=0) throw new UnsupportedOperationException("Client-memory texture pointers are not supported");
        GLES32.glTexImage2D(t,l,in,w,h,b,f,ty,null);
    }
    public static void glTexImage3D(int t,int l,int in,int w,int h,int d,int b,int f,int ty,long ptr) {
        if(ptr!=0) throw new UnsupportedOperationException("Client-memory texture pointers are not supported");
        GLES32.glTexImage3D(t,l,in,w,h,d,b,f,ty,(Buffer)null);
    }
    public static native void glTexSubImage2D(int t,int l,int x,int y,int w,int h,int f,int ty,long off);
    public static void glTexSubImage3D(int t,int l,int x,int y,int z,int w,int h,int d,int f,int ty,IntBuffer p) { GLES32.glTexSubImage3D(t,l,x,y,z,w,h,d,f,ty,p); }
    public static void glReadPixels(int x,int y,int w,int h,int f,int t,ByteBuffer p) { GLES32.glReadPixels(x,y,w,h,f,t,p); }
    // GLES always uses shader point sizes and point sprites; multisampling follows FBO storage.
    public static void glEnable(int cap) { if(cap!=0x809D && cap!=0x8642 && cap!=0x8861) GLES32.glEnable(cap); }
    public static void glDisable(int cap) { if(cap!=0x809D && cap!=0x8642 && cap!=0x8861) GLES32.glDisable(cap); }
    // --- GPU timer queries via GL_EXT_disjoint_timer_query -------------------------------
    // The 117HD FrameTimer (performance overlay) issues glQueryCounter(GL_TIMESTAMP) around
    // the render frame and busy-waits glGetQueryObjectiv(GL_QUERY_RESULT_AVAILABLE). On
    // desktop that's core OpenGL 3.3; on GLES the equivalent is this extension, resolved
    // natively in hd_memory.c (the GLES 3.2 Java stubs don't expose it). When the extension
    // is absent the calls are no-ops, so the overlay degrades to a zero/empty GPU reading
    // instead of crashing the render thread (the old code threw UnsupportedOperationException
    // on the first query, which killed the frame).
    private static final int GL_TIME_ELAPSED_EXT = 0x88BF;
    private static final int GL_TIMESTAMP_EXT = 0x8E17;
    private static native boolean timerQuerySupported();
    private static native long timerQueryResolution();
    private static native void beginTimerQuery(int id);
    private static native void endTimerQuery();
    private static native boolean timerQueryAvailable(int id);
    private static native long timerQueryResult(int id);
    public static void glQueryCounter(int id,int target) {
        if (target == GL_TIMESTAMP_EXT || target == GL_TIME_ELAPSED_EXT) beginTimerQuery(id);
    }
    public static void glQueryCounter(int id) { beginTimerQuery(id); }
    public static void glEndQuery(int target) { if (target == GL_TIME_ELAPSED_EXT) endTimerQuery(); }
    public static long glGetQueryObjectui64(int id,int p) { return timerQueryResult(id); }
    /** Nanosecond resolution of the GPU timestamp clock (0 if unsupported/unknown). */
    public static long getTimerQueryResolutionNanos() { return timerQueryResolution(); }
    /** True if GPU timer queries are available on this device. */
    public static boolean isTimerQuerySupported() { return timerQuerySupported(); }

}
