package org.mozilla.gecko.gfx;

import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;
import org.libreoffice.LibreOfficeMainActivity;

interface PanZoomController {

    class Factory {
        static PanZoomController create(LibreOfficeMainActivity context, PanZoomTarget target, View view) {
            return new JavaPanZoomController(context, target, view);
        }
    }

    void destroy();

    boolean onTouchEvent(MotionEvent event);
    boolean onMotionEvent(MotionEvent event);
    void notifyDefaultActionPrevented(boolean prevented);

    boolean getRedrawHint();
    PointF getVelocityVector();

    void pageRectUpdated();
    void abortPanning();
    void abortAnimation();

    void setOverScrollMode(int overscrollMode);
    int getOverScrollMode();
}
