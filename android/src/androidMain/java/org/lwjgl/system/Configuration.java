package org.lwjgl.system;
public final class Configuration<T> {
    public static final Configuration<String> SHARED_LIBRARY_EXTRACT_DIRECTORY=new Configuration<>();
    public void set(T value) { /* Android bindings use system GLES; no LWJGL extraction. */ }
}
