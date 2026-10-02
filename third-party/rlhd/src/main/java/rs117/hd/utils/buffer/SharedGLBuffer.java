package rs117.hd.utils.buffer;

import java.nio.IntBuffer;

public class SharedGLBuffer extends GLBuffer {
	public final int clUsage;

	public long clId;

	public SharedGLBuffer(String name, int target, int glUsage, int clUsage) {
		super(name, target, glUsage);
		this.clUsage = clUsage;
	}

	private void releaseCLBuffer() {
		clId = 0;
	}

	@Override
	public void destroy() {
		releaseCLBuffer();
		super.destroy();
	}

	@Override
	public boolean ensureCapacity(long byteOffset, long numBytes) {
		boolean resized = super.ensureCapacity(byteOffset, numBytes);
		return resized;
	}
}
