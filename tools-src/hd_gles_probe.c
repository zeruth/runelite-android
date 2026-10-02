#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

// Offscreen driver probe: no game input, app restart, or visible window.
static void limit(const char *name, GLenum token) {
    GLint value=0; glGetIntegerv(token,&value); printf("%s=%d\n",name,value);
}
static int render_ui(GLuint program) {
    // Exercise the same native ARGB -> PBO -> RGBA upload -> UI shader path as the app.
    GLuint vao, vbo, texture, pbo;
    const float vertices[]={-1,-1,0,0, 3,-1,2,0, -1,3,0,2};
    glGenVertexArrays(1,&vao); glBindVertexArray(vao);
    glGenBuffers(1,&vbo); glBindBuffer(GL_ARRAY_BUFFER,vbo);
    glBufferData(GL_ARRAY_BUFFER,sizeof(vertices),vertices,GL_STATIC_DRAW);
    glVertexAttribPointer(0,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)0);
    glVertexAttribPointer(1,2,GL_FLOAT,GL_FALSE,4*sizeof(float),(void*)(2*sizeof(float)));
    glEnableVertexAttribArray(0); glEnableVertexAttribArray(1);
    glGenTextures(1,&texture); glBindTexture(GL_TEXTURE_2D,texture);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,2,2,0,GL_RGBA,GL_UNSIGNED_BYTE,NULL);
    const uint32_t argb[]={0xff204080,0xff204080,0xff204080,0xff204080};
    glGenBuffers(1,&pbo); glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);
    glBufferData(GL_PIXEL_UNPACK_BUFFER,sizeof(argb),argb,GL_STREAM_DRAW);
    glTexSubImage2D(GL_TEXTURE_2D,0,0,0,2,2,GL_RGBA,GL_UNSIGNED_BYTE,(void*)0);
    glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
    GLint blocks; glGetProgramiv(program,GL_ACTIVE_UNIFORM_BLOCKS,&blocks);
    GLuint *ubos=calloc((size_t)blocks,sizeof(GLuint)); glGenBuffers(blocks,ubos);
    for(GLint i=0;i<blocks;i++) {
        GLint size; char name[128];
        glGetActiveUniformBlockName(program,(GLuint)i,sizeof(name),NULL,name);
        glGetActiveUniformBlockiv(program,(GLuint)i,GL_UNIFORM_BLOCK_DATA_SIZE,&size);
        void *data=calloc(1,(size_t)size);
        if(strcmp(name,"UBOUI")==0) { int32_t dimensions[]={2,2,16,16}; memcpy(data,dimensions,sizeof(dimensions)); }
        glBindBuffer(GL_UNIFORM_BUFFER,ubos[i]); glBufferData(GL_UNIFORM_BUFFER,size,data,GL_STATIC_DRAW); free(data);
        glUniformBlockBinding(program,(GLuint)i,(GLuint)i); glBindBufferBase(GL_UNIFORM_BUFFER,(GLuint)i,ubos[i]);
    }
    glUseProgram(program); glUniform1i(glGetUniformLocation(program,"uiTexture"),0);
    glViewport(0,0,16,16); glClearColor(1,0,1,1); glClear(GL_COLOR_BUFFER_BIT);
    glDrawArrays(GL_TRIANGLES,0,3);
    unsigned char pixel[4]; glReadPixels(8,8,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
    int ok=abs((int)pixel[0]-0x20)<=1 && abs((int)pixel[1]-0x40)<=1 && abs((int)pixel[2]-0x80)<=1;
    printf("ui_pbo_render_readback=%s rgba=%u,%u,%u,%u\n",ok?"PASS":"FAIL",pixel[0],pixel[1],pixel[2],pixel[3]);
    glDeleteBuffers(blocks,ubos); free(ubos); glDeleteBuffers(1,&pbo); glDeleteBuffers(1,&vbo);
    glDeleteTextures(1,&texture); glDeleteVertexArrays(1,&vao);
    return ok;
}
int main(int argc, char **argv) {
    EGLDisplay display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major,minor;
    if(!eglInitialize(display,&major,&minor)) { fprintf(stderr,"eglInitialize: %x\n",eglGetError()); return 1; }
    EGLint attrs[]={EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};
    EGLConfig config; EGLint count;
    if(!eglChooseConfig(display,attrs,&config,1,&count) || !count) return 2;
    EGLint contextAttrs[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE};
    EGLContext context=eglCreateContext(display,config,EGL_NO_CONTEXT,contextAttrs);
    EGLint surfaceAttrs[]={EGL_WIDTH,16,EGL_HEIGHT,16,EGL_NONE};
    EGLSurface surface=eglCreatePbufferSurface(display,config,surfaceAttrs);
    if(!eglMakeCurrent(display,surface,surface,context)) { fprintf(stderr,"eglMakeCurrent: %x\n",eglGetError()); return 3; }
    printf("renderer=%s\nversion=%s\nshading=%s\n",glGetString(GL_RENDERER),glGetString(GL_VERSION),glGetString(GL_SHADING_LANGUAGE_VERSION));
    const char *extensions=(const char *)glGetString(GL_EXTENSIONS);
    printf("implicit_conversions=%d\ntimer_query=%d\n",strstr(extensions,"GL_EXT_shader_implicit_conversions")!=NULL,strstr(extensions,"GL_EXT_disjoint_timer_query")!=NULL);
    limit("max_uniform_block_bytes",GL_MAX_UNIFORM_BLOCK_SIZE);
    limit("max_texture_size",GL_MAX_TEXTURE_SIZE);
    limit("max_texture_array_layers",GL_MAX_ARRAY_TEXTURE_LAYERS);
    limit("max_texture_buffer_texels",GL_MAX_TEXTURE_BUFFER_SIZE);
    limit("max_vertex_uniform_blocks",GL_MAX_VERTEX_UNIFORM_BLOCKS);
    limit("max_fragment_uniform_blocks",GL_MAX_FRAGMENT_UNIFORM_BLOCKS);
    GLuint buffer; glGenBuffers(1,&buffer); glBindBuffer(GL_ARRAY_BUFFER,buffer); glBufferData(GL_ARRAY_BUFFER,64,NULL,GL_STREAM_DRAW);
    unsigned *mapped=glMapBufferRange(GL_ARRAY_BUFFER,0,64,GL_MAP_WRITE_BIT|GL_MAP_INVALIDATE_BUFFER_BIT|GL_MAP_FLUSH_EXPLICIT_BIT);
    if(!mapped) return 4;
    for(unsigned i=0;i<16;i++) mapped[i]=i*12345;
    glFlushMappedBufferRange(GL_ARRAY_BUFFER,0,64);
    if(!glUnmapBuffer(GL_ARRAY_BUFFER)) return 5;
    mapped=glMapBufferRange(GL_ARRAY_BUFFER,0,64,GL_MAP_READ_BIT);
    if(!mapped) return 6;
    for(unsigned i=0;i<16;i++) if(mapped[i]!=i*12345) return 7;
    glUnmapBuffer(GL_ARRAY_BUFFER);
    GLuint texture; glGenTextures(1,&texture); glBindTexture(GL_TEXTURE_BUFFER,texture);
    glTexBuffer(GL_TEXTURE_BUFFER,GL_RGB32I,buffer);
    printf("rgb32i_texture_buffer_error=%u\n",glGetError());
    glDeleteTextures(1,&texture); glDeleteBuffers(1,&buffer);
    printf("mapped_buffer_roundtrip=PASS\n");
    // Optional expanded shader files: pass vertex/fragment paths in pairs to also link them.
    GLuint program=glCreateProgram();
    for(int i=1;i<argc;i++) {
        FILE *file=fopen(argv[i],"rb"); if(!file) return 8;
        fseek(file,0,SEEK_END); long n=ftell(file); rewind(file);
        char *text=malloc((size_t)n+1); if(!text) return 9;
        if(fread(text,1,(size_t)n,file)!=(size_t)n) return 10;
        fclose(file); text[n]=0;
        GLuint shader=glCreateShader(i%2?GL_VERTEX_SHADER:GL_FRAGMENT_SHADER);
        const char *source=text; glShaderSource(shader,1,&source,NULL); glCompileShader(shader);
        GLint ok; glGetShaderiv(shader,GL_COMPILE_STATUS,&ok);
        if(!ok) { char log[16384]; glGetShaderInfoLog(shader,sizeof(log),NULL,log); fprintf(stderr,"%s:\n%s\n",argv[i],log); return 11; }
        glAttachShader(program,shader); glDeleteShader(shader); free(text);
        printf("shader=%s PASS\n",argv[i]);
    }
    if(argc>1) {
        glLinkProgram(program); GLint ok; glGetProgramiv(program,GL_LINK_STATUS,&ok);
        if(!ok) { char log[16384]; glGetProgramInfoLog(program,sizeof(log),NULL,log); fprintf(stderr,"link:\n%s\n",log); return 12; }
        printf("shader_link=PASS\n");
        if(strstr(argv[1],"_ui_") && !render_ui(program)) return 14;
    }
    glDeleteProgram(program);
    GLenum err=glGetError(); printf("gl_error=%u\n",err);
    eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
    eglDestroySurface(display,surface); eglDestroyContext(display,context); eglTerminate(display);
    return err?13:0;
}
