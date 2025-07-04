package org.mozilla.gecko.gfx;

import android.graphics.PointF;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * ViewportMetrics manages state and contains some utility functions related to
 * the page viewport for the Gecko layer client to use.
 */
public class ViewportMetrics {
    private static final String LOGTAG = "GeckoViewportMetrics";

    private RectF mPageRect;
    private RectF mCssPageRect;
    private RectF mViewportRect;
    private float mZoomFactor;

    public ViewportMetrics(DisplayMetrics metrics) {
        mPageRect = new RectF(0, 0, metrics.widthPixels, metrics.heightPixels);
        mCssPageRect = new RectF(0, 0, metrics.widthPixels, metrics.heightPixels);
        mViewportRect = new RectF(0, 0, metrics.widthPixels, metrics.heightPixels);
        mZoomFactor = 1.0f;
    }

    public ViewportMetrics(ViewportMetrics viewport) {
        mPageRect = new RectF(viewport.getPageRect());
        mCssPageRect = new RectF(viewport.getCssPageRect());
        mViewportRect = new RectF(viewport.getViewport());
        mZoomFactor = viewport.getZoomFactor();
    }

    public ViewportMetrics(ImmutableViewportMetrics viewport) {
        mPageRect = new RectF(viewport.pageRectLeft,
                viewport.pageRectTop,
                viewport.pageRectRight,
                viewport.pageRectBottom);
        mCssPageRect = new RectF(viewport.cssPageRectLeft,
                viewport.cssPageRectTop,
                viewport.cssPageRectRight,
                viewport.cssPageRectBottom);
        mViewportRect = new RectF(viewport.viewportRectLeft,
                viewport.viewportRectTop,
                viewport.viewportRectRight,
                viewport.viewportRectBottom);
        mZoomFactor = viewport.zoomFactor;
    }

    public ViewportMetrics(JSONObject json) throws JSONException {
        float x = (float)json.getDouble("x");
        float y = (float)json.getDouble("y");
        float width = (float)json.getDouble("width");
        float height = (float)json.getDouble("height");
        float pageLeft = (float)json.getDouble("pageLeft");
        float pageTop = (float)json.getDouble("pageTop");
        float pageRight = (float)json.getDouble("pageRight");
        float pageBottom = (float)json.getDouble("pageBottom");
        float cssPageLeft = (float)json.getDouble("cssPageLeft");
        float cssPageTop = (float)json.getDouble("cssPageTop");
        float cssPageRight = (float)json.getDouble("cssPageRight");
        float cssPageBottom = (float)json.getDouble("cssPageBottom");
        float zoom = (float)json.getDouble("zoom");

        mPageRect = new RectF(pageLeft, pageTop, pageRight, pageBottom);
        mCssPageRect = new RectF(cssPageLeft, cssPageTop, cssPageRight, cssPageBottom);
        mViewportRect = new RectF(x, y, x + width, y + height);
        mZoomFactor = zoom;
    }

    public ViewportMetrics(float x, float y, float width, float height,
                           float pageLeft, float pageTop, float pageRight, float pageBottom,
                           float cssPageLeft, float cssPageTop, float cssPageRight, float cssPageBottom,
                           float zoom) {
        mPageRect = new RectF(pageLeft, pageTop, pageRight, pageBottom);
        mCssPageRect = new RectF(cssPageLeft, cssPageTop, cssPageRight, cssPageBottom);
        mViewportRect = new RectF(x, y, x + width, y + height);
        mZoomFactor = zoom;
    }

    public PointF getOrigin() {
        return new PointF(mViewportRect.left, mViewportRect.top);
    }

    public FloatSize getSize() {
        return new FloatSize(mViewportRect.width(), mViewportRect.height());
    }

    public RectF getViewport() {
        return mViewportRect;
    }

    public RectF getCssViewport() {
        return RectUtils.scale(mViewportRect, 1/mZoomFactor);
    }

    public RectF getPageRect() {
        return mPageRect;
    }

    public RectF getCssPageRect() {
        return mCssPageRect;
    }

    public float getZoomFactor() {
        return mZoomFactor;
    }

    public void setPageRect(RectF pageRect, RectF cssPageRect) {
        mPageRect = pageRect;
        mCssPageRect = cssPageRect;
    }

    public void setViewport(RectF viewport) {
        mViewportRect = viewport;
    }

    public void setOrigin(PointF origin) {
        mViewportRect.set(origin.x, origin.y,
                          origin.x + mViewportRect.width(),
                          origin.y + mViewportRect.height());
    }

    public void setSize(FloatSize size) {
        mViewportRect.right = mViewportRect.left + size.width;
        mViewportRect.bottom = mViewportRect.top + size.height;
    }

    public void setZoomFactor(float zoomFactor) {
        mZoomFactor = zoomFactor;
    }

    public String toJSON() {
        // Round off height and width. Since the height and width are the size of the screen, it
        // makes no sense to send non-integer coordinates to Gecko.
        int height = Math.round(mViewportRect.height());
        int width = Math.round(mViewportRect.width());

        String sb = "{ \"x\" : " + mViewportRect.left +
                ", \"y\" : " + mViewportRect.top +
                ", \"width\" : " + width +
                ", \"height\" : " + height +
                ", \"pageLeft\" : " + mPageRect.left +
                ", \"pageTop\" : " + mPageRect.top +
                ", \"pageRight\" : " + mPageRect.right +
                ", \"pageBottom\" : " + mPageRect.bottom +
                ", \"cssPageLeft\" : " + mCssPageRect.left +
                ", \"cssPageTop\" : " + mCssPageRect.top +
                ", \"cssPageRight\" : " + mCssPageRect.right +
                ", \"cssPageBottom\" : " + mCssPageRect.bottom +
                ", \"zoom\" : " + mZoomFactor +
                " }";
        return sb;
    }

    @Override
    public String toString() {
        String buff = "v=" + mViewportRect.toString() +
                " p=" + mPageRect.toString() +
                " c=" + mCssPageRect.toString() +
                " z=" + mZoomFactor;
        return buff;
    }
}
