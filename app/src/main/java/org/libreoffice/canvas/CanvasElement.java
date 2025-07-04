package org.libreoffice.canvas;

import android.graphics.Canvas;

/**
 * Canvas element is an element (or part) that is drawn canvas and can
 * potentially be interacted with.
 */
public interface CanvasElement {
    /**
     * Called when the element needs to be draw no the canvas. This method
     * should call onDraw when conditions to draw are satisfied.
     *
     * @param canvas - the canvas
     */
    void draw(Canvas canvas);

    /**
     * Hit test - returns true if the object has been hit
     * @param x - x coordinate of the
     * @param y - y coordinate of the
     */
    boolean contains(float x, float y);

    /**
     * Return if element is visible.
     */
    boolean isVisible();

    /**
     * Set element visibility.
     * @param visible - is element visible
     */
    void setVisible(boolean visible);
}
/* vim:set shiftwidth=4 softtabstop=4 expandtab: */