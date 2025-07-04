package org.mozilla.gecko.gfx;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

public class GLController {
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final String LOGTAG = "GeckoGLController";

    private final LayerView mView;
    private int mGLVersion;
    private int mWidth, mHeight;

    private EGL10 mEGL;
    private EGLDisplay mEGLDisplay;
    private EGLConfig mEGLConfig;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;

    private static final int LOCAL_EGL_OPENGL_ES2_BIT = 4;

    private static final int[] CONFIG_SPEC = {
        EGL10.EGL_RED_SIZE, 5,
        EGL10.EGL_GREEN_SIZE, 6,
        EGL10.EGL_BLUE_SIZE, 5,
        EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
        EGL10.EGL_RENDERABLE_TYPE, LOCAL_EGL_OPENGL_ES2_BIT,
        EGL10.EGL_NONE
    };

    public GLController(LayerView view) {
        mView = view;
        mGLVersion = 2;
    }

    public void setGLVersion(int version) {
        mGLVersion = version;
    }

    /** You must call this on the same thread you intend to use OpenGL on. */
    public void initGLContext() {
        initEGLContext();
        createEGLSurface();
    }

    public void disposeGLContext() {
        if (mEGL == null) {
            return;
        }

        if (!mEGL.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                                 EGL10.EGL_NO_CONTEXT)) {
            throw new GLControllerException("EGL context could not be released! " +
                                            getEGLError());
        }

        if (mEGLSurface != null) {
            if (!mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface)) {
                throw new GLControllerException("EGL surface could not be destroyed! " +
                                                getEGLError());
            }

            mEGLSurface = null;
        }

        if (mEGLContext != null) {
            if (!mEGL.eglDestroyContext(mEGLDisplay, mEGLContext)) {
                throw new GLControllerException("EGL context could not be destroyed! " +
                                                getEGLError());
            }

            mEGLContext = null;
        }
    }

    public GL10 getGL() { return (GL10) mEGLContext.getGL(); }
    public EGLDisplay getEGLDisplay()       { return mEGLDisplay;         }
    public EGLConfig getEGLConfig()         { return mEGLConfig;          }
    public EGLContext getEGLContext()       { return mEGLContext;         }
    public EGLSurface getEGLSurface()       { return mEGLSurface;         }
    public LayerView getView()              { return mView;               }

    public boolean hasSurface() {
        return mEGLSurface != null;
    }

    public boolean swapBuffers() {
        return mEGL.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }

    public synchronized int getWidth() {
        return mWidth;
    }

    public synchronized int getHeight() {
        return mHeight;
    }

    synchronized void surfaceDestroyed() {
        notifyAll();
    }

    synchronized void surfaceChanged(int newWidth, int newHeight) {
        mWidth = newWidth;
        mHeight = newHeight;
        notifyAll();
    }

    private void initEGL() {
        mEGL = (EGL10)EGLContext.getEGL();

        mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new GLControllerException("eglGetDisplay() failed");
        }

        int[] version = new int[2];
        if (!mEGL.eglInitialize(mEGLDisplay, version)) {
            throw new GLControllerException("eglInitialize() failed " + getEGLError());
        }

        mEGLConfig = chooseConfig();
    }

    private void initEGLContext() {
        initEGL();

        int[] attribList = { EGL_CONTEXT_CLIENT_VERSION, mGLVersion, EGL10.EGL_NONE };
        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, mEGLConfig, EGL10.EGL_NO_CONTEXT,
                                            attribList);
        if (mEGLContext == null || mEGLContext == EGL10.EGL_NO_CONTEXT) {
            throw new GLControllerException("createContext() failed " +
                                            getEGLError());
        }

        if (mView.getRenderer() != null) {
            GL10 gl = (GL10) mEGLContext.getGL();
            mView.getRenderer().onSurfaceCreated(gl, mEGLConfig);
            mView.getRenderer().onSurfaceChanged(gl, mWidth, mHeight);
        }
    }

    private EGLConfig chooseConfig() {
        int[] numConfigs = new int[1];
        if (!mEGL.eglChooseConfig(mEGLDisplay, CONFIG_SPEC, null, 0, numConfigs) ||
                numConfigs[0] <= 0) {
            throw new GLControllerException("No available EGL configurations " +
                                            getEGLError());
        }

        EGLConfig[] configs = new EGLConfig[numConfigs[0]];
        if (!mEGL.eglChooseConfig(mEGLDisplay, CONFIG_SPEC, configs, numConfigs[0], numConfigs)) {
            throw new GLControllerException("No EGL configuration for that specification " +
                                            getEGLError());
        }

        // Select the first 565 RGB configuration.
        int[] red = new int[1], green = new int[1], blue = new int[1];
        for (EGLConfig config : configs) {
            mEGL.eglGetConfigAttrib(mEGLDisplay, config, EGL10.EGL_RED_SIZE, red);
            mEGL.eglGetConfigAttrib(mEGLDisplay, config, EGL10.EGL_GREEN_SIZE, green);
            mEGL.eglGetConfigAttrib(mEGLDisplay, config, EGL10.EGL_BLUE_SIZE, blue);
            if (red[0] == 5 && green[0] == 6 && blue[0] == 5) {
                return config;
            }
        }

        // if there's no 565 RGB configuration, select another one that fulfils the specification
        return configs[0];
    }

    private void createEGLSurface() {
        Object window = mView.getNativeWindow();
        mEGLSurface = mEGL.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, window, null);
        if (mEGLSurface == null || mEGLSurface == EGL10.EGL_NO_SURFACE) {
            throw new GLControllerException("EGL window surface could not be created! " +
                                            getEGLError());
        }

        if (!mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new GLControllerException("EGL surface could not be made into the current " +
                                            "surface! " + getEGLError());
        }

        if (mView.getRenderer() != null) {
            GL10 gl = (GL10) mEGLContext.getGL();
            mView.getRenderer().onSurfaceCreated(gl, mEGLConfig);
            mView.getRenderer().onSurfaceChanged(gl, mView.getWidth(), mView.getHeight());
        }
    }

    private String getEGLError() {
        return "Error " + mEGL.eglGetError();
    }

    public static class GLControllerException extends RuntimeException {
        public static final long serialVersionUID = 1L;

        GLControllerException(String e) {
            super(e);
        }
    }
}

