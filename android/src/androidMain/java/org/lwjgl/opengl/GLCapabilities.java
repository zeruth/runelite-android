package org.lwjgl.opengl;

import android.opengl.GLES32;

/** Capabilities of the implemented desktop-to-GLES subset, never a spoofed driver version. */
public final class GLCapabilities {
    public final boolean OpenGL31, OpenGL33;
    public final boolean OpenGL40=false, OpenGL43=false;
    public final boolean GL_ARB_buffer_storage=false, GL_ARB_draw_indirect=false, GL_ARB_multi_draw_indirect=false;
    public final boolean GL_ARB_copy_buffer=true, GL_ARB_map_buffer_range=true;
    public final boolean GL_ARB_shader_image_load_store=false;
    public final boolean GL_EXT_texture_filter_anisotropic;
    public final long glDebugMessageControl=0, glTexStorage3D=1;
    GLCapabilities() {
        int major=GL33C.glGetInteger(GLES32.GL_MAJOR_VERSION), minor=GL33C.glGetInteger(GLES32.GL_MINOR_VERSION);
        OpenGL31=OpenGL33=major>3 || major==3 && minor>=2;
        String extensions=GLES32.glGetString(GLES32.GL_EXTENSIONS);
        GL_EXT_texture_filter_anisotropic=extensions!=null && extensions.contains("GL_EXT_texture_filter_anisotropic");
    }
}
