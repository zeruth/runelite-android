package org.lwjgl.opengl;

import android.opengl.GLES32;

/** Capabilities of the implemented desktop-to-GLES subset, never a spoofed driver version. */
public final class GLCapabilities {
    public final boolean OpenGL31, OpenGL33;
    public final boolean forwardCompatible=true; // GLES has no desktop compatibility point-sprite toggle.
    public final boolean OpenGL21=false, OpenGL40=false, OpenGL43=false, OpenGL45=false;
    public final boolean GL_ARB_buffer_storage=false, GL_ARB_draw_indirect=false, GL_ARB_multi_draw_indirect=false;
    public final boolean GL_ARB_copy_buffer, GL_ARB_map_buffer_range;
    public final boolean GL_ARB_shader_image_load_store=false;
    public final boolean GL_EXT_texture_filter_anisotropic;
    public final long glDebugMessageControl=0, glInvalidateBufferData=0, glTexStorage3D;
    GLCapabilities() {
        int major=GL33C.glGetInteger(GLES32.GL_MAJOR_VERSION), minor=GL33C.glGetInteger(GLES32.GL_MINOR_VERSION);
        OpenGL31=OpenGL33=major>3 || major==3 && minor>=2;
        String extensions=GLES32.glGetString(GLES32.GL_EXTENSIONS);
        GL_EXT_texture_filter_anisotropic=extensions!=null && extensions.contains("GL_EXT_texture_filter_anisotropic");
        GL_ARB_copy_buffer=GL_ARB_map_buffer_range=true;
        glTexStorage3D=1;
    }
    /** No GL context: 117 HD renders through Vulkan, so every GL-specific feature path stays off. */
    private GLCapabilities(boolean vulkan) {
        OpenGL31=OpenGL33=true;
        GL_ARB_copy_buffer=GL_ARB_map_buffer_range=false;
        GL_EXT_texture_filter_anisotropic=false;
        glTexStorage3D=1;
    }
    public static GLCapabilities forVulkan() { return new GLCapabilities(true); }
}
