package rs117.hd;

/** Host hooks kept separate from the upstream renderer and desktop AWT. */
public final class AndroidSupport {
    public interface Host {
        boolean makeCurrent();
        int width();
        int height();
        int contextGeneration();
        void stopped();
        void failed();
        /**
         * Creates the multisampled scene framebuffer with tile-memory resolve
         * (GL_EXT_multisampled_render_to_texture), left bound. Returns {fbo, colorTexture,
         * depthRenderbuffer, readFbo}, where readFbo is an ordinary framebuffer on the same
         * texture to read the resolved scene from, or null to fall back to multisampled
         * renderbuffers + blit resolve.
         */
        default int[] createTileResolvedFbo(int width, int height, int samples, int colorFormat, int depthFormat) { return null; }
    }
    public static Host host;
    public static final String GLSL_HEADER = "#version 320 es\n"
        + "precision highp float;\nprecision highp int;\n"
        + "precision highp sampler2D;\nprecision highp sampler3D;\nprecision highp sampler2DArray;\n"
        + "precision highp isampler3D;\nprecision highp isamplerBuffer;\n"
        + "precision highp usampler2DArray;\n";
    private AndroidSupport() {}
    public static String adaptShader(String source) {
        // Mobile texture buffers can be limited to 65,536 texels. Crowded scenes
        // exceed that limit; read the same packed RGB32I data through an SSBO.
        if (source.contains("uniform isamplerBuffer textureFaces;")) {
            source = source.replace("uniform isamplerBuffer textureFaces;",
                "layout(std430, binding = 0) readonly buffer AndroidFaceData { int faceData[]; };\n"
                + "ivec4 fetchFaceData(int index) { int base = index * 3; "
                + "return ivec4(faceData[base], faceData[base + 1], faceData[base + 2], 1); }\n")
                .replace("texelFetch(textureFaces,", "fetchFaceData(");
        }
        return GLSL_HEADER + source.replaceAll("(?m)^#version[^\\r\\n]*", "");
    }
}
