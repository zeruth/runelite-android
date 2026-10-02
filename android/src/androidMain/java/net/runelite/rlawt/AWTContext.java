package net.runelite.rlawt;

import android.opengl.GLES32;
import java.awt.Canvas;
import java.awt.Component;
import net.runelite.client.plugins.gpugles.GlesHost;

/**
 * Android stand-in for {@code net.runelite.rlawt.AWTContext}. On desktop this is a
 * JOGL-bound GL context manager tied to an AWT Canvas; on Android we already have an
 * EGL14 context managed by {@link GlesHost} (used by GpuGlesPlugin) — this shim
 * forwards 117HD's expected entry points to that singleton so HdPlugin and
 * GpuGlesPlugin share the same surface.
 *
 * "Framebuffer 0" semantics translate cleanly: GLES treats the default framebuffer
 * as the EGL surface bound through {@code eglMakeCurrent}, exactly what GlesHost
 * owns.
 */
public class AWTContext
{
	public AWTContext(Canvas ignored) { /* Canvas reference unused on Android */ }
	public AWTContext(Component ignored) { /* Matches the desktop rlawt constructor ABI. */ }

	public static void loadNatives() { /* no JOGL natives to load — EGL is system */ }

	public void configurePixelFormat(int alpha, int depth, int stencil) { /* GlesHost picks the config */ }

	public void createGLContext() { GlesHost.get().makeCurrent(); }

	public boolean makeCurrent() { return GlesHost.get().makeCurrent(); }

	public void swapBuffers() { GlesHost.get().swapBuffers(); }

	/** Default framebuffer = 0 (the EGL surface). The boolean is a desktop hint
	 *  about MSAA-resolve framebuffers we don't manage here. */
	public int getFramebuffer(boolean resolve) { return 0; }

	public int getBufferMode() { return GLES32.GL_BACK; }

	/** EGL swap interval. Returns the value actually applied (we don't have a way
	 *  to probe that on Android, so we trust the caller's request). */
	public int setSwapInterval(int interval)
	{
		GlesHost.get().setSwapInterval(interval);
		return interval;
	}

	public void destroy() { /* GlesHost lives for the process; nothing to tear down here */ }
}
