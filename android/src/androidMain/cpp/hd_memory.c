#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <GLES3/gl32.h>

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
JNIEXPORT jlong JNICALL Java_org_lwjgl_system_MemoryUtil_memAddress0(JNIEnv *env, jclass cls, jobject buffer) {
    (void)cls;
    return (jlong)(uintptr_t)(*env)->GetDirectBufferAddress(env, buffer);
}
JNIEXPORT void JNICALL Java_org_lwjgl_opengl_GL33C_glTexSubImage2D(JNIEnv *env, jclass cls,
    jint target, jint level, jint x, jint y, jint width, jint height, jint format, jint type, jlong offset) {
    (void)env; (void)cls;
    glTexSubImage2D(target, level, x, y, width, height, format, type, (const void *)(uintptr_t)offset);
}
