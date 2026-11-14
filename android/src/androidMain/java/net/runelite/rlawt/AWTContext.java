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

	/** EGL swap interval. Returns the value actually applied.
	 *
	 *  Android's EGL has no adaptive vsync: a negative interval is clamped to 0. Reporting
	 *  the request back (-1) told 117 HD that adaptive sync was active, so it set no fps
	 *  target, and with no vsync either the GPU ran flat out into thermal throttling. Apply
	 *  and report 0 instead; 117 HD then caps at its fps target, as GPU (GLES) does when
	 *  unlocked. */
	public int setSwapInterval(int interval)
	{
		int applied = Math.max(0, interval);
		GlesHost.get().setSwapInterval(applied);
		return applied;
	}

    // These desktop OpenCL-sharing handles are not EGL handles and cannot be substituted.
    public long getCGLShareGroup() { throw desktopHandle("CGL share group"); }
    public long getGLContext() { throw desktopHandle("desktop GL context"); }
    public long getGLXDisplay() { throw desktopHandle("GLX display"); }
    public long getWGLHDC() { throw desktopHandle("WGL device context"); }
    private static UnsupportedOperationException desktopHandle(String handle) {
        return new UnsupportedOperationException(handle + " is unavailable on Android EGL; desktop OpenCL sharing is unsupported");
    }

	public void destroy() { /* GlesHost lives for the process; nothing to tear down here */ }
}
