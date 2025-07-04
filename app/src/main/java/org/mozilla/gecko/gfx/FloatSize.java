package org.mozilla.gecko.gfx;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.gecko.util.FloatUtils;

public class FloatSize {
    public final float width, height;

    public FloatSize(FloatSize size) { width = size.width; height = size.height; }
    public FloatSize(IntSize size) { width = size.width; height = size.height; }
    public FloatSize(float aWidth, float aHeight) { width = aWidth; height = aHeight; }

    public FloatSize(JSONObject json) {
        try {
            width = (float)json.getDouble("width");
            height = (float)json.getDouble("height");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() { return "(" + width + "," + height + ")"; }

    public boolean isPositive() {
        return (width > 0 && height > 0);
    }

    public boolean fuzzyEquals(FloatSize size) {
        return (FloatUtils.fuzzyEquals(size.width, width) &&
                FloatUtils.fuzzyEquals(size.height, height));
    }

    public FloatSize scale(float factor) {
        return new FloatSize(width * factor, height * factor);
    }

    /*
     * Returns the size that represents a linear transition between this size and `to` at time `t`,
     * which is on the scale [0, 1).
     */
    public FloatSize interpolate(FloatSize to, float t) {
        return new FloatSize(FloatUtils.interpolate(width, to.width, t),
                FloatUtils.interpolate(height, to.height, t));
    }
}

