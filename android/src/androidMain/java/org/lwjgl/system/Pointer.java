package org.lwjgl.system;

/** Address-bearing objects used by the LWJGL buffer APIs. */
public interface Pointer {
    int POINTER_SIZE = MemoryUtil.POINTER_SIZE;
    int POINTER_SHIFT = POINTER_SIZE == 8 ? 3 : 2;
    long address();
    abstract class Default implements Pointer {
        protected long address;
        protected Default(long address) { this.address = address; }
        @Override public long address() { return address; }
    }
}
