package org.lwjgl.opengl;
import android.opengl.GLES32;
public class GL40 extends GL33C {
    public static void glDrawArraysIndirect(int m,long o) { GLES32.glDrawArraysIndirect(m,Math.toIntExact(o)); }
    public static void glDrawElementsIndirect(int m,int t,long o) { GLES32.glDrawElementsIndirect(m,t,Math.toIntExact(o)); }
}
