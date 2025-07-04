package org.mozilla.gecko;

public final class ZoomConstraints {
    private final float mDefaultZoom;
    private final float mMinZoom;
    private final float mMaxZoom;

    public ZoomConstraints(float defaultZoom, float minZoom, float maxZoom) {
        mDefaultZoom = defaultZoom;
        mMinZoom = minZoom;
        mMaxZoom = maxZoom;
    }

    public float getDefaultZoom() {
        return mDefaultZoom;
    }

    public float getMinZoom() {
        return mMinZoom;
    }

    public float getMaxZoom() {
        return mMaxZoom;
    }
}
