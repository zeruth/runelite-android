package org.lwjgl;
import java.nio.*;
public final class BufferUtils {
    public static ByteBuffer createByteBuffer(int n) { return ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder()); }
    public static IntBuffer createIntBuffer(int n) { return createByteBuffer(Math.multiplyExact(n,4)).asIntBuffer(); }
    public static FloatBuffer createFloatBuffer(int n) { return createByteBuffer(Math.multiplyExact(n,4)).asFloatBuffer(); }
}
