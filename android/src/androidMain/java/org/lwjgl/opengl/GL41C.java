package org.lwjgl.opengl;
import android.opengl.GLES32;
import java.nio.ByteBuffer;
public class GL41C extends GL40 {
    public static void glGetProgramBinary(int p,int[] len,int[] fmt,ByteBuffer b) { GLES32.glGetProgramBinary(p,b.remaining(),len,0,fmt,0,b); }
}
