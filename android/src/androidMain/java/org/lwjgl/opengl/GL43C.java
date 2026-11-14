package org.lwjgl.opengl;
import android.opengl.GLES32;
public class GL43C extends GL43 {
    public static void glDispatchCompute(int x, int y, int z) { GLES32.glDispatchCompute(x, y, z); }
    public static void glMemoryBarrier(int barriers) { GLES32.glMemoryBarrier(barriers); }
    /** GLES has no invalidate-buffer hint; orphan this buffer's store without changing its binding. */
    public static void glInvalidateBufferData(int buffer) {
        if (!GLES32.glIsBuffer(buffer)) throw new IllegalArgumentException("Not a GL buffer object");
        int previous = glGetInteger(GLES32.GL_COPY_WRITE_BUFFER_BINDING);
        GLES32.glBindBuffer(GLES32.GL_COPY_WRITE_BUFFER, buffer);
        try {
            if (glGetBufferParameteri64(GLES32.GL_COPY_WRITE_BUFFER, GLES32.GL_BUFFER_MAPPED) != 0)
                throw new IllegalStateException("Cannot invalidate a mapped buffer");
            int size = Math.toIntExact(glGetBufferParameteri64(GLES32.GL_COPY_WRITE_BUFFER, GLES32.GL_BUFFER_SIZE));
            int usage = Math.toIntExact(glGetBufferParameteri64(GLES32.GL_COPY_WRITE_BUFFER, GLES32.GL_BUFFER_USAGE));
            GLES32.glBufferData(GLES32.GL_COPY_WRITE_BUFFER, size, null, usage);
        } finally {
            GLES32.glBindBuffer(GLES32.GL_COPY_WRITE_BUFFER, previous);
        }
    }
    public static void glObjectLabel(int t,int n,CharSequence s) { GLES32.glObjectLabel(t,n,s.length(),s.toString()); }
    public static void glPushDebugGroup(int src,int id,CharSequence s) { GLES32.glPushDebugGroup(src,id,s.length(),s.toString()); }
    public static void glDebugMessageControl(int src,int t,int sev,int id,boolean enable) { GLES32.glDebugMessageControl(src,t,sev,1,new int[]{id},0,enable); }
    public static void glDebugMessageControl(int src,int t,int sev,int[] ids,boolean enable) { GLES32.glDebugMessageControl(src,t,sev,ids==null?0:ids.length,ids,0,enable); }
    public static void glInvalidateFramebuffer(int t,int[] attachments) { GLES32.glInvalidateFramebuffer(t,attachments.length,attachments,0); }
}
