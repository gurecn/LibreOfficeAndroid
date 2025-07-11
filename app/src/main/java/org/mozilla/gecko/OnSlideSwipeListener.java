package org.mozilla.gecko;

import android.content.Context;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import org.libreoffice.manager.LOKitShell;
import org.mozilla.gecko.gfx.GeckoLayerClient;
import org.mozilla.gecko.gfx.ImmutableViewportMetrics;

public class OnSlideSwipeListener implements OnTouchListener {

    private final GestureDetector mGestureDetector;
    private final GeckoLayerClient mLayerClient;

    public OnSlideSwipeListener(Context ctx, GeckoLayerClient client){
        mGestureDetector = new GestureDetector(ctx, new GestureListener());
        mLayerClient = client;
    }

    private final class GestureListener extends SimpleOnGestureListener {

        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velX, float velY) {
            // Check if the page is already zoomed-in.
            // Disable swiping gesture if that's the case.
            ImmutableViewportMetrics viewportMetrics = mLayerClient.getViewportMetrics();
            if (viewportMetrics.viewportRectLeft > viewportMetrics.pageRectLeft ||
                    viewportMetrics.viewportRectRight < viewportMetrics.pageRectRight) {
                return false;
            }

            // Otherwise, the page is smaller than viewport, perform swipe
            // gesture.
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD
                            && Math.abs(velX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight();
                        } else {
                            onSwipeLeft();
                        }
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return false;
        }
    }

    public void onSwipeRight() {
        LOKitShell.sendSwipeRightEvent();
    }

    public void onSwipeLeft() {
        LOKitShell.sendSwipeLeftEvent();
    }

    @Override
    public boolean onTouch(View v, MotionEvent me) {
        return mGestureDetector.onTouchEvent(me);
    }
}


