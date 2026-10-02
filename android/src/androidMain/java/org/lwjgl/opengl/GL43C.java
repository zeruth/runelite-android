package org.lwjgl.opengl;
import android.opengl.GLES32;
public class GL43C extends GL43 {
    public static void glObjectLabel(int t,int n,CharSequence s) { GLES32.glObjectLabel(t,n,s.length(),s.toString()); }
    public static void glPushDebugGroup(int src,int id,CharSequence s) { GLES32.glPushDebugGroup(src,id,s.length(),s.toString()); }
    public static void glDebugMessageControl(int src,int t,int sev,int id,boolean enable) { GLES32.glDebugMessageControl(src,t,sev,1,new int[]{id},0,enable); }
    public static void glDebugMessageControl(int src,int t,int sev,int[] ids,boolean enable) { GLES32.glDebugMessageControl(src,t,sev,ids==null?0:ids.length,ids,0,enable); }
}
