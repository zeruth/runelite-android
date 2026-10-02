package org.lwjgl.opengl;

import android.opengl.GLES32;
import java.nio.*;
import static android.opengl.GLES32.*;

/** The LWJGL entry points used by 117 HD, implemented by the Android GLES driver. */
public class GL33C extends GlesBindings {
    static { System.loadLibrary("hdmemory"); }
    private static int bytes(Buffer b) {
        int width = b instanceof ByteBuffer ? 1 : b instanceof ShortBuffer ? 2 : b instanceof LongBuffer || b instanceof DoubleBuffer ? 8 : 4;
        return Math.multiplyExact(b.remaining(), width);
    }
    public static int glGenBuffers() { int[] v = new int[1]; GLES32.glGenBuffers(1,v,0); return v[0]; }
    public static int glGenTextures() { int[] v = new int[1]; GLES32.glGenTextures(1,v,0); return v[0]; }
    public static int glGenFramebuffers() { int[] v = new int[1]; GLES32.glGenFramebuffers(1,v,0); return v[0]; }
    public static int glGenRenderbuffers() { int[] v = new int[1]; GLES32.glGenRenderbuffers(1,v,0); return v[0]; }
    public static int glGenVertexArrays() { int[] v = new int[1]; GLES32.glGenVertexArrays(1,v,0); return v[0]; }
    public static void glGenQueries(int[] v) { GLES32.glGenQueries(v.length,v,0); }
    public static void glDeleteBuffers(int v) { GLES32.glDeleteBuffers(1,new int[]{v},0); }
    public static void glDeleteTextures(int v) { GLES32.glDeleteTextures(1,new int[]{v},0); }
    public static void glDeleteFramebuffers(int v) { GLES32.glDeleteFramebuffers(1,new int[]{v},0); }
    public static void glDeleteRenderbuffers(int v) { GLES32.glDeleteRenderbuffers(1,new int[]{v},0); }
    public static void glDeleteVertexArrays(int v) { GLES32.glDeleteVertexArrays(1,new int[]{v},0); }
    public static void glDeleteQueries(int[] v) { GLES32.glDeleteQueries(v.length,v,0); }
    public static int glGetInteger(int p) { int[] v = new int[1]; GLES32.glGetIntegerv(p,v,0); return v[0]; }
    public static float glGetFloat(int p) { float[] v = new float[1]; GLES32.glGetFloatv(p,v,0); return v[0]; }
    public static int glGetShaderi(int s,int p) { int[] v = new int[1]; GLES32.glGetShaderiv(s,p,v,0); return v[0]; }
    public static int glGetProgrami(int s,int p) { int[] v = new int[1]; GLES32.glGetProgramiv(s,p,v,0); return v[0]; }
    public static long glGetBufferParameteri64(int s,int p) { long[] v = new long[1]; GLES32.glGetBufferParameteri64v(s,p,v,0); return v[0]; }
    public static int glGetFramebufferAttachmentParameteri(int t,int a,int p) {
        int[] v=new int[1]; GLES32.glGetFramebufferAttachmentParameteriv(t,a==0x0402?GL_BACK:a,p,v,0); return v[0];
    }
    public static void glGetIntegerv(int p,int[] v) { GLES32.glGetIntegerv(p,v,0); }
    public static void glGetProgramiv(int s,int p,int[] v) { GLES32.glGetProgramiv(s,p,v,0); }
    public static void glGetQueryObjectiv(int s,int p,int[] v) { GLES32.glGetQueryObjectuiv(s,p,v,0); }
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
    public static void glDrawBuffer(int mode) { GLES32.glDrawBuffers(1,new int[]{mode},0); }
    public static void glDrawElements(int m,int n,int t,long o) { GLES32.glDrawElements(m,n,t,Math.toIntExact(o)); }
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
    // GLES multisampling is controlled by framebuffer storage rather than this desktop toggle.
    public static void glEnable(int cap) { if(cap!=0x809D) GLES32.glEnable(cap); }
    public static void glDisable(int cap) { if(cap!=0x809D) GLES32.glDisable(cap); }
    public static void glQueryCounter(int id,int target) { throw new UnsupportedOperationException("Desktop GPU timer queries require a GLES extension adapter"); }
    public static long glGetQueryObjectui64(int id,int p) { throw new UnsupportedOperationException("Desktop GPU timer queries require a GLES extension adapter"); }
}
