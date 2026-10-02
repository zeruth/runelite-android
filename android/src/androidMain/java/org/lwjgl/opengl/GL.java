package org.lwjgl.opengl;
public final class GL {
    private static GLCapabilities caps;
    public static GLCapabilities createCapabilities() { return caps=new GLCapabilities(); }
    public static GLCapabilities getCapabilities() { return caps; }
}
