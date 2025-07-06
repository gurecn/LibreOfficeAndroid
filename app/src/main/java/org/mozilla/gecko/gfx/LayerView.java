package org.mozilla.gecko.gfx;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import org.libreoffice.data.LOEvent;
import org.libreoffice.manager.LOKitShell;
import org.libreoffice.ui.LibreOfficeMainActivity;
import org.mozilla.gecko.OnInterceptTouchListener;
import org.mozilla.gecko.OnSlideSwipeListener;

/**
 * A view rendered by the layer compositor.
 *
 * This view delegates to LayerRenderer to actually do the drawing. Its role is largely that of a
 * mediator between the LayerRenderer and the LayerController.
 */
public class LayerView extends FrameLayout {
    private static final String LOGTAG = LayerView.class.getName();

    private GeckoLayerClient mLayerClient;
    private PanZoomController mPanZoomController;
    private final GLController mGLController;
    private InputConnectionHandler mInputConnectionHandler;
    private LayerRenderer mRenderer;

    private final SurfaceView mSurfaceView;

    private Listener mListener;
    private OnInterceptTouchListener mTouchIntercepter;
    private final LibreOfficeMainActivity mContext;

    public LayerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = (LibreOfficeMainActivity) context;

        mSurfaceView = new SurfaceView(context);
        addView(mSurfaceView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(new SurfaceListener());
            holder.setFormat(PixelFormat.RGB_565);

        mGLController = new GLController(this);
    }

    void connect(GeckoLayerClient layerClient) {
        mLayerClient = layerClient;
        mPanZoomController = mLayerClient.getPanZoomController();
        mRenderer = new LayerRenderer(this);
        mInputConnectionHandler = null;

        setFocusable(true);
        setFocusableInTouchMode(true);

        createGLThread();
        setOnTouchListener(new OnSlideSwipeListener(getContext(), mLayerClient));
    }

    public void show() {
        // Fix this if TextureView support is turned back on above
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    public void hide() {
        // Fix this if TextureView support is turned back on above
        mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public void destroy() {
        if (mLayerClient != null) {
            mLayerClient.destroy();
        }
        if (mRenderer != null) {
            mRenderer.destroy();
        }
    }

    public void setTouchIntercepter(final OnInterceptTouchListener touchIntercepter) {
        // this gets run on the gecko thread, but for thread safety we want the assignment
        // on the UI thread.
        post(new Runnable() {
            public void run() {
                mTouchIntercepter = touchIntercepter;
            }
        });
    }

    public void setInputConnectionHandler(InputConnectionHandler inputConnectionHandler) {
        mInputConnectionHandler = inputConnectionHandler;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            requestFocus();
        }

        if (mTouchIntercepter != null && mTouchIntercepter.onInterceptTouchEvent(this, event)) {
            return true;
        }
        if (mPanZoomController != null && mPanZoomController.onTouchEvent(event)) {
            return true;
        }
        return mTouchIntercepter != null && mTouchIntercepter.onTouch(this, event);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return mTouchIntercepter != null && mTouchIntercepter.onTouch(this, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mPanZoomController != null && mPanZoomController.onMotionEvent(event);
    }

    public GeckoLayerClient getLayerClient() { return mLayerClient; }
    public PanZoomController getPanZoomController() { return mPanZoomController; }

    public ImmutableViewportMetrics getViewportMetrics() {
        return mLayerClient.getViewportMetrics();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (mInputConnectionHandler != null)
            return mInputConnectionHandler.onCreateInputConnection(outAttrs);
        return null;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        return mInputConnectionHandler != null && mInputConnectionHandler.onKeyPreIme(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mInputConnectionHandler != null && mInputConnectionHandler.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return mInputConnectionHandler != null && mInputConnectionHandler.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return mInputConnectionHandler != null && mInputConnectionHandler.onKeyMultiple(keyCode, repeatCount, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mInputConnectionHandler != null && mInputConnectionHandler.onKeyUp(keyCode, event);
    }

    public void requestRender() {
        if (mListener != null) {
            mListener.renderRequested();
        }
    }

    public void addLayer(Layer layer) {
        mRenderer.addLayer(layer);
    }

    public void removeLayer(Layer layer) {
        mRenderer.removeLayer(layer);
    }

    public int getMaxTextureSize() {
        return mRenderer.getMaxTextureSize();
    }

    public void setLayerRenderer(LayerRenderer renderer) {
        mRenderer = renderer;
    }

    public LayerRenderer getLayerRenderer() {
        return mRenderer;
    }

    public LayerRenderer getRenderer() {
        return mRenderer;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    Listener getListener() {
        return mListener;
    }

    public GLController getGLController() {
        return mGLController;
    }

    public Bitmap getDrawable(String name) {
        Context context = getContext();
        Resources resources = context.getResources();
        String packageName = context.getPackageName();
        int resourceID = resources.getIdentifier(name, "drawable", packageName);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        return BitmapFactory.decodeResource(context.getResources(), resourceID, options);
    }

    Bitmap getBackgroundPattern() {
        return getDrawable("background");
    }

    Bitmap getShadowPattern() {
        return getDrawable("shadow");
    }

    private void onSizeChanged(int width, int height) {
        mGLController.surfaceChanged(width, height);

        mLayerClient.setViewportSize(new FloatSize(width, height), false);

        if (mListener != null) {
            mListener.surfaceChanged(width, height);
        }

        LOKitShell.sendEvent(new LOEvent(LOEvent.UPDATE_ZOOM_CONSTRAINTS));
    }

    private void onDestroyed() {
        mGLController.surfaceDestroyed();

        if (mListener != null) {
            mListener.compositionPauseRequested();
        }
    }

    public Object getNativeWindow() {
        return mSurfaceView.getHolder();
    }

    public interface Listener {
        void compositorCreated();
        void renderRequested();
        void compositionPauseRequested();
        void surfaceChanged(int width, int height);
    }

    private class SurfaceListener implements SurfaceHolder.Callback {
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                                int height) {
            onSizeChanged(width, height);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            if (mRenderControllerThread != null) {
                mRenderControllerThread.surfaceCreated();
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            onDestroyed();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            mLayerClient.setViewportSize(new FloatSize(right - left, bottom - top), true);
        }
    }

    private RenderControllerThread mRenderControllerThread;

    public synchronized void createGLThread() {
        if (mRenderControllerThread != null) {
            throw new LayerViewException ("createGLThread() called with a GL thread already in place!");
        }

        Log.e(LOGTAG, "### Creating GL thread!");
        mRenderControllerThread = new RenderControllerThread(mGLController);
        mRenderControllerThread.start();
        setListener(mRenderControllerThread);
        notifyAll();
    }

    public synchronized Thread destroyGLThread() {
        // Wait for the GL thread to be started.
        Log.e(LOGTAG, "### Waiting for GL thread to be created...");
        while (mRenderControllerThread == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        Log.e(LOGTAG, "### Destroying GL thread!");
        Thread thread = mRenderControllerThread;
        mRenderControllerThread.shutdown();
        setListener(null);
        mRenderControllerThread = null;
        return thread;
    }

    public static class LayerViewException extends RuntimeException {
        public static final long serialVersionUID = 1L;

        LayerViewException(String e) {
            super(e);
        }
    }
}
