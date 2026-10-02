package org.lwjgl.opengl;
public class GL43 extends GL41C {
    public static void glMultiDrawArraysIndirect(int m,long o,int count,int stride) {
        for(int i=0;i<count;i++) glDrawArraysIndirect(m,o+(long)i*(stride==0?16:stride));
    }
}
