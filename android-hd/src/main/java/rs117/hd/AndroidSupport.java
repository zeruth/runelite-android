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
    }
    public static Host host;
    public static final String GLSL_HEADER = "#version 320 es\n"
        + "precision highp float;\nprecision highp int;\n"
        + "precision highp sampler2D;\nprecision highp sampler3D;\nprecision highp sampler2DArray;\n"
        + "precision highp isampler3D;\nprecision highp isamplerBuffer;\n"
        + "precision highp usampler2DArray;\n";
    private AndroidSupport() {}
    public static String adaptShader(String source) {
        return GLSL_HEADER + source.replaceAll("(?m)^#version[^\\r\\n]*", "");
    }
}
