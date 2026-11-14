#include <jni.h>
#include <stdint.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>
#include <EGL/egl.h>
#include <GLES3/gl32.h>

/* GL_EXT_disjoint_timer_query constants (not in the GLES 3.2 core headers). */
#ifndef GL_TIME_ELAPSED_EXT
#define GL_TIME_ELAPSED_EXT 0x88BF
#endif
#ifndef GL_QUERY_RESULT_EXT
#define GL_QUERY_RESULT_EXT 0x8866
#endif
#ifndef GL_QUERY_RESULT_AVAILABLE_EXT
#define GL_QUERY_RESULT_AVAILABLE_EXT 0x8867
#endif

/* Scene framebuffer through GL_EXT_multisampled_render_to_texture: the multisampled color
 * and depth live only in tile memory and are resolved into a single-sample texture as each
 * tile is written out. The desktop path (multisampled renderbuffers + glBlitFramebuffer
 * resolve) instead stores every sample to memory and reads it back each frame. */
typedef void (*FramebufferTexture2DMultisampleEXT)(GLenum, GLenum, GLenum, GLuint, GLint, GLsizei);
typedef void (*RenderbufferStorageMultisampleEXT)(GLenum, GLsizei, GLenum, GLsizei, GLsizei);

JNIEXPORT jboolean JNICALL Java_net_runelite_mp_hd_GlesExt_createTileResolvedFbo(JNIEnv *env, jclass cls,
    jint width, jint height, jint samples, jint colorFormat, jint depthFormat, jintArray out) {
    (void)cls;
    static FramebufferTexture2DMultisampleEXT fbTex2DMs;
    static RenderbufferStorageMultisampleEXT rbStorageMs;
    if (!fbTex2DMs) fbTex2DMs = (FramebufferTexture2DMultisampleEXT)eglGetProcAddress("glFramebufferTexture2DMultisampleEXT");
    if (!rbStorageMs) rbStorageMs = (RenderbufferStorageMultisampleEXT)eglGetProcAddress("glRenderbufferStorageMultisampleEXT");
    if (!fbTex2DMs || !rbStorageMs) return JNI_FALSE;

    GLint prevTexture = 0;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prevTexture);
    while (glGetError() != GL_NO_ERROR) {}

    GLuint fbo = 0, tex = 0, rbo = 0;
    glGenFramebuffers(1, &fbo);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);

    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexStorage2D(GL_TEXTURE_2D, 1, (GLenum)colorFormat, width, height);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glBindTexture(GL_TEXTURE_2D, (GLuint)prevTexture);
    fbTex2DMs(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0, samples);

    glGenRenderbuffers(1, &rbo);
    glBindRenderbuffer(GL_RENDERBUFFER, rbo);
    rbStorageMs(GL_RENDERBUFFER, samples, (GLenum)depthFormat, width, height);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, rbo);
    glBindRenderbuffer(GL_RENDERBUFFER, 0);

    GLenum renderStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);

    /* The frame is read back through a second, ordinary framebuffer on the same texture,
     * which holds the resolved image once the scene pass ends. Reading straight from the
     * render-to-texture framebuffer left the scene black on Adreno. */
    GLuint readFbo = 0;
    glGenFramebuffers(1, &readFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, readFbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    GLenum readStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);

    if (glGetError() != GL_NO_ERROR || renderStatus != GL_FRAMEBUFFER_COMPLETE || readStatus != GL_FRAMEBUFFER_COMPLETE) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, &fbo);
        glDeleteFramebuffers(1, &readFbo);
        glDeleteTextures(1, &tex);
        glDeleteRenderbuffers(1, &rbo);
        while (glGetError() != GL_NO_ERROR) {}
        return JNI_FALSE;
    }

    jint ids[4] = { (jint)fbo, (jint)tex, (jint)rbo, (jint)readFbo };
    (*env)->SetIntArrayRegion(env, out, 0, 4, ids);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_org_lwjgl_system_MemoryUtil_allocate(JNIEnv *env, jclass cls, jlong size) {
    (void)env; (void)cls;
    if (size <= 0 || (uint64_t)size > SIZE_MAX) return 0;
    return (jlong)(uintptr_t)malloc((size_t)size);
}
JNIEXPORT void JNICALL Java_org_lwjgl_system_MemoryUtil_nmemFree(JNIEnv *env, jclass cls, jlong ptr) {
    (void)env; (void)cls;
    free((void *)(uintptr_t)ptr);
}
JNIEXPORT jobject JNICALL Java_org_lwjgl_system_MemoryUtil_view(JNIEnv *env, jclass cls, jlong ptr, jint size) {
    (void)cls;
    return (*env)->NewDirectByteBuffer(env, (void *)(uintptr_t)ptr, size);
}
JNIEXPORT jint JNICALL Java_org_lwjgl_system_MemoryUtil_pointerSize(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jint)sizeof(void *);
}
JNIEXPORT jbyteArray JNICALL Java_org_lwjgl_system_MemoryUtil_readNullTerminated(JNIEnv *env, jclass cls, jlong ptr) {
    (void)cls;
    if (ptr == 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), "Null string address");
        return NULL;
    }
    const char *text = (const char *)(uintptr_t)ptr;
    size_t length = strnlen(text, INT_MAX);
    if (length == INT_MAX) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalArgumentException"), "Native string exceeds Java array size");
        return NULL;
    }
    jbyteArray bytes = (*env)->NewByteArray(env, (jsize)length);
    if (bytes != NULL && length != 0) (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)length, (const jbyte *)text);
    return bytes;
}
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_MemoryUtil_memAddress0(JNIEnv *env, jclass cls, jobject buffer) {
    (void)cls;
    return (jlong)(uintptr_t)(*env)->GetDirectBufferAddress(env, buffer);
}
JNIEXPORT void JNICALL Java_org_lwjgl_opengl_GL33C_glTexSubImage2D(JNIEnv *env, jclass cls,
    jint target, jint level, jint x, jint y, jint width, jint height, jint format, jint type, jlong offset) {
    (void)env; (void)cls;
    glTexSubImage2D(target, level, x, y, width, height, format, type, (const void *)(uintptr_t)offset);
}

/* --- GPU timer queries via GL_EXT_disjoint_timer_query --------------------------
 * The GLES 3.2 headers don't declare these (they're an extension), so resolve them once
 * through eglGetProcAddress. The Java side (GL33C) probes support and falls back to
 * no-ops when the extension is absent, so the 117HD FrameTimer overlay degrades instead
 * of crashing the render thread. */
typedef void (*glBeginQueryEXT_fn)(GLenum, GLuint);
typedef void (*glEndQueryEXT_fn)(GLenum);
typedef void (*glGetQueryObjectuivEXT_fn)(GLuint, GLenum, GLuint *);
typedef void (*glGetQueryObjectui64vEXT_fn)(GLuint, GLenum, GLuint64 *);

JNIEXPORT jboolean JNICALL Java_org_lwjgl_opengl_GL33C_timerQuerySupported(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    static int cached = -1;
    if (cached < 0) {
        const char *exts = (const char *)glGetString(GL_EXTENSIONS);
        cached = (exts && strstr(exts, "GL_EXT_disjoint_timer_query")) ? 1 : 0;
    }
    return cached == 1 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_org_lwjgl_opengl_GL33C_timerQueryResolution(JNIEnv *env, jclass cls) {
    (void)cls;
    if (!Java_org_lwjgl_opengl_GL33C_timerQuerySupported(env, cls)) return 0;
    static jlong cached = -1;
    if (cached < 0) {
        GLint v = 0;
        glGetIntegeri_v(GL_TIME_ELAPSED_EXT, 0, &v);
        cached = v > 0 ? v : 0;
    }
    return cached;
}

JNIEXPORT void JNICALL Java_org_lwjgl_opengl_GL33C_beginTimerQuery(JNIEnv *env, jclass cls, jint id) {
    (void)env; (void)cls;
    static glBeginQueryEXT_fn begin;
    if (!begin) begin = (glBeginQueryEXT_fn)eglGetProcAddress("glBeginQueryEXT");
    if (begin && id != 0) begin(GL_TIME_ELAPSED_EXT, (GLuint)id);
}

JNIEXPORT void JNICALL Java_org_lwjgl_opengl_GL33C_endTimerQuery(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    static glEndQueryEXT_fn end;
    if (!end) end = (glEndQueryEXT_fn)eglGetProcAddress("glEndQueryEXT");
    if (end) end(GL_TIME_ELAPSED_EXT);
}

JNIEXPORT jboolean JNICALL Java_org_lwjgl_opengl_GL33C_timerQueryAvailable(JNIEnv *env, jclass cls, jint id) {
    (void)cls;
    if (!Java_org_lwjgl_opengl_GL33C_timerQuerySupported(env, cls)) return JNI_TRUE; /* claim available so the busy-wait exits */
    static glGetQueryObjectuivEXT_fn avail;
    if (!avail) avail = (glGetQueryObjectuivEXT_fn)eglGetProcAddress("glGetQueryObjectuivEXT");
    if (!avail) return JNI_TRUE;
    GLuint v = 0;
    avail((GLuint)id, GL_QUERY_RESULT_AVAILABLE_EXT, &v);
    return v == 1 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL Java_org_lwjgl_opengl_GL33C_timerQueryResult(JNIEnv *env, jclass cls, jint id) {
    (void)cls;
    if (!Java_org_lwjgl_opengl_GL33C_timerQuerySupported(env, cls)) return 0;
    static glGetQueryObjectui64vEXT_fn result;
    if (!result) result = (glGetQueryObjectui64vEXT_fn)eglGetProcAddress("glGetQueryObjectui64vEXT");
    if (!result) return 0;
    GLuint64 v = 0;
    result((GLuint)id, GL_QUERY_RESULT_EXT, &v);
    return (jlong)v;
}
