package org.libreoffice.canvas;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;

/*
 * A canvas element on DocumentOverlayView. Shows a rectangle with current page
 * number and total page number inside of it.
 */
public class PageNumberRect extends CommonCanvasElement {
    private String mPageNumberString;
    private final TextPaint mPageNumberRectPaint = new TextPaint();
    private final Paint mBgPaint = new Paint();
    private final Rect mTextBounds = new Rect();
    private final float mBgMargin = 5f;

    public PageNumberRect() {
        mBgPaint.setColor(Color.BLACK);
        mBgPaint.setAlpha(100);
        mPageNumberRectPaint.setColor(Color.WHITE);
    }

    /**
     * Implement hit test here
     *
     * @param x - x coordinate of the
     * @param y - y coordinate of the
     */
    @Override
    public boolean onHitTest(float x, float y) {
        return false;
    }

    /**
     * Called inside draw if the element is visible. Override this method to
     * draw the element on the canvas.
     *
     * @param canvas - the canvas
     */
    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawRect(canvas.getWidth()*0.1f - mBgMargin,
                canvas.getHeight()*0.1f - mTextBounds.height() - mBgMargin,
                mTextBounds.width() + canvas.getWidth()*0.1f + mBgMargin,
                canvas.getHeight()*0.1f + mBgMargin,
                mBgPaint);
        canvas.drawText(mPageNumberString, canvas.getWidth()*0.1f, canvas.getHeight()*0.1f, mPageNumberRectPaint);
    }

    public void setPageNumberString (String pageNumberString) {
        mPageNumberString = pageNumberString;
        mPageNumberRectPaint.getTextBounds(mPageNumberString, 0, mPageNumberString.length(), mTextBounds);
    }
}
