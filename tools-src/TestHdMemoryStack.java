import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;

/** Reproduces CommandBuffer's paired allocation and capacity-based reuse. */
public class TestHdMemoryStack {
    public static void main(String[] args) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            stack.mallocInt(64).put(0, 42);
            stack.mallocInt(8).put(0, 42);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer offsets = null, counts = null;
            for (int size : new int[] { 4, 16, 3, 32, 2, 64, 8 }) {
                if (offsets == null || offsets.capacity() < size) {
                    offsets = stack.callocInt(size);
                    counts = stack.callocInt(size);
                    if (offsets.capacity() != size || counts.capacity() != size)
                        throw new AssertionError("Allocation capacity must equal its requested size");
                    for (int i = 0; i < size; i++)
                        if (offsets.get(i) != 0 || counts.get(i) != 0) throw new AssertionError("calloc must zero reused memory");
                }
                for (int i = 0; i < size; i++) { offsets.put(i); counts.put(i + 1); }
                offsets.flip(); counts.flip();
                if (offsets.remaining() != counts.remaining()) throw new AssertionError("Paired draw counts differ");
                offsets.clear(); counts.clear();
            }
        }
        try (MemoryStack outer = MemoryStack.stackPush()) {
            IntBuffer live = outer.mallocInt(4); live.put(0, 123);
            try (MemoryStack inner = MemoryStack.stackPush()) { inner.callocInt(4).put(0, 456); }
            if (live.get(0) != 123) throw new AssertionError("Nested stacks reused a live allocation");
        }
        System.out.println("MemoryStack capacity, calloc, paired draw reuse, and nested ownership: PASS");
    }
}
