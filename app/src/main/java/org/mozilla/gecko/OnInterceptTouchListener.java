package org.mozilla.gecko;

import android.view.MotionEvent;
import android.view.View;

public interface OnInterceptTouchListener extends View.OnTouchListener {
    /** Override this method for a chance to consume events before the view or its children */
    boolean onInterceptTouchEvent(View view, MotionEvent event);
}
