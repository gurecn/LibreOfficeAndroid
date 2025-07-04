package org.mozilla.gecko.gfx;

import android.graphics.PointF;
import org.mozilla.gecko.ZoomConstraints;

public interface PanZoomTarget {
    ImmutableViewportMetrics getViewportMetrics();
    ZoomConstraints getZoomConstraints();

    void setAnimationTarget(ImmutableViewportMetrics viewport);
    void setViewportMetrics(ImmutableViewportMetrics viewport);
    /** This triggers an (asynchronous) viewport update/redraw. */
    void forceRedraw();

    boolean post(Runnable action);
    Object getLock();
    PointF convertViewPointToLayerPoint(PointF viewPoint);

    boolean isFullScreen();
}
