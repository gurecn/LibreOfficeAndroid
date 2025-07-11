package org.libreoffice.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.libreoffice.ui.MainActivity;
import org.libreoffice.R;
import org.libreoffice.canvas.AdjustLengthLine;
import org.libreoffice.canvas.CalcSelectionBox;
import org.libreoffice.canvas.Cursor;
import org.libreoffice.canvas.GraphicSelection;
import org.libreoffice.canvas.PageNumberRect;
import org.libreoffice.canvas.SelectionHandle;
import org.libreoffice.canvas.SelectionHandleEnd;
import org.libreoffice.canvas.SelectionHandleMiddle;
import org.libreoffice.canvas.SelectionHandleStart;
import org.mozilla.gecko.gfx.ImmutableViewportMetrics;
import org.mozilla.gecko.gfx.LayerView;
import org.mozilla.gecko.gfx.RectUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Document overlay view is responsible for showing the client drawn overlay
 * elements like cursor, selection and graphic selection, and manipulate them.
 */
public class DocumentOverlayView extends View implements View.OnTouchListener {
    private static final String LOGTAG = DocumentOverlayView.class.getSimpleName();

    private static final int CURSOR_BLINK_TIME = 500;

    private boolean mInitialized = false;

    private List<RectF> mSelections = new ArrayList<RectF>();
    private final List<RectF> mScaledSelections = new ArrayList<RectF>();
    private final Paint mSelectionPaint = new Paint();
    private boolean mSelectionsVisible;

    private GraphicSelection mGraphicSelection;

    private boolean mGraphicSelectionMove = false;

    private LayerView mLayerView;

    private SelectionHandle mHandleMiddle;
    private SelectionHandle mHandleStart;
    private SelectionHandle mHandleEnd;

    private Cursor mCursor;

    private SelectionHandle mDragHandle = null;

    private List<RectF> mPartPageRectangles;
    private PageNumberRect mPageNumberRect;
    private boolean mPageNumberAvailable = false;
    private int previousIndex = 0; // previous page number, used to compare with the current
    private CalcHeadersController mCalcHeadersController;

    private CalcSelectionBox mCalcSelectionBox;
    private boolean mCalcSelectionBoxDragging;
    private AdjustLengthLine mAdjustLengthLine;
    private boolean mAdjustLengthLineDragging;

    public DocumentOverlayView(Context context) {
        super(context);
    }

    public DocumentOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DocumentOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Initialize the selection and cursor view.
     */
    public void initialize(LayerView layerView) {
        if (!mInitialized) {
            setOnTouchListener(this);
            mLayerView = layerView;

            mCursor = new Cursor();
            mCursor.setVisible(false);

            mSelectionPaint.setColor(Color.BLUE);
            mSelectionPaint.setAlpha(50);
            mSelectionsVisible = false;

            mGraphicSelection = new GraphicSelection((MainActivity) getContext());
            mGraphicSelection.setVisible(false);

            postDelayed(cursorAnimation, CURSOR_BLINK_TIME);

            mHandleMiddle = new SelectionHandleMiddle((MainActivity) getContext());
            mHandleStart = new SelectionHandleStart((MainActivity) getContext());
            mHandleEnd = new SelectionHandleEnd((MainActivity) getContext());

            mInitialized = true;
        }
    }

    /**
     * Change the cursor position.
     * @param position - new position of the cursor
     */
    public void changeCursorPosition(RectF position) {
        if (RectUtils.fuzzyEquals(mCursor.mPosition, position)) {
            return;
        }
        mCursor.mPosition = position;

        ImmutableViewportMetrics metrics = mLayerView.getViewportMetrics();
        repositionWithViewport(metrics.viewportRectLeft, metrics.viewportRectTop, metrics.zoomFactor);
    }

    /**
     * Change the text selection rectangles.
     * @param selectionRects - list of text selection rectangles
     */
    public void changeSelections(List<RectF> selectionRects) {
        mSelections = selectionRects;

        ImmutableViewportMetrics metrics = mLayerView.getViewportMetrics();
        repositionWithViewport(metrics.viewportRectLeft, metrics.viewportRectTop, metrics.zoomFactor);
    }

    /**
     * Change the graphic selection rectangle.
     * @param rectangle - new graphic selection rectangle
     */
    public void changeGraphicSelection(RectF rectangle) {
        if (RectUtils.fuzzyEquals(mGraphicSelection.mRectangle, rectangle)) {
            return;
        }

        mGraphicSelection.mRectangle = rectangle;

        ImmutableViewportMetrics metrics = mLayerView.getViewportMetrics();
        repositionWithViewport(metrics.viewportRectLeft, metrics.viewportRectTop, metrics.zoomFactor);
    }

    public void repositionWithViewport(float x, float y, float zoom) {
        RectF rect = convertToScreen(mCursor.mPosition, x, y, zoom);
        mCursor.reposition(rect);

        rect = convertToScreen(mHandleMiddle.mDocumentPosition, x, y, zoom);
        mHandleMiddle.reposition(rect.left, rect.bottom);

        rect = convertToScreen(mHandleStart.mDocumentPosition, x, y, zoom);
        mHandleStart.reposition(rect.left, rect.bottom);

        rect = convertToScreen(mHandleEnd.mDocumentPosition, x, y, zoom);
        mHandleEnd.reposition(rect.left, rect.bottom);

        mScaledSelections.clear();
        for (RectF selection : mSelections) {
            RectF scaledSelection = convertToScreen(selection, x, y, zoom);
            mScaledSelections.add(scaledSelection);
        }

        if (mCalcSelectionBox != null) {
            rect = convertToScreen(mCalcSelectionBox.mDocumentPosition, x, y, zoom);
            mCalcSelectionBox.reposition(rect);
        }

        if (mGraphicSelection != null && mGraphicSelection.mRectangle != null) {
            RectF scaledGraphicSelection = convertToScreen(mGraphicSelection.mRectangle, x, y, zoom);
            mGraphicSelection.reposition(scaledGraphicSelection);
        }

        invalidate();
    }

    /**
     * Convert the input rectangle from document to screen coordinates
     * according to current viewport data (x, y, zoom).
     */
    private static RectF convertToScreen(RectF inputRect, float x, float y, float zoom) {
        RectF rect = RectUtils.scale(inputRect, zoom);
        rect.offset(-x, -y);
        return rect;
    }

    /**
     * Set part page rectangles and initialize a page number rectangle object
     * (canvas element).
     */
    public void setPartPageRectangles (List<RectF> rectangles) {
        mPartPageRectangles = rectangles;
        mPageNumberRect = new PageNumberRect();
        mPageNumberAvailable = true;
    }

    /**
     * Drawing on canvas.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        mCursor.draw(canvas);

        if (mPageNumberAvailable) {
            mPageNumberRect.draw(canvas);
        }

        mHandleMiddle.draw(canvas);
        mHandleStart.draw(canvas);
        mHandleEnd.draw(canvas);

        if (mSelectionsVisible) {
            for (RectF selection : mScaledSelections) {
                canvas.drawRect(selection, mSelectionPaint);
            }
        }

        if (mCalcSelectionBox != null) {
            mCalcSelectionBox.draw(canvas);
        }

        mGraphicSelection.draw(canvas);

        if (mCalcHeadersController != null) {
            mCalcHeadersController.showHeaders();
        }

        if (mAdjustLengthLine != null) {
            mAdjustLengthLine.draw(canvas);
        }
    }

    /**
     * Cursor animation function. Switch the alpha between opaque and fully transparent.
     */
    private final Runnable cursorAnimation = new Runnable() {
        public void run() {
            if (mCursor.isVisible()) {
                mCursor.cycleAlpha();
                invalidate();
            }
            postDelayed(cursorAnimation, CURSOR_BLINK_TIME);
        }
    };

    /**
     * Show the cursor on the view.
     */
    public void showCursor() {
        if (!mCursor.isVisible()) {
            mCursor.setVisible(true);
            invalidate();
        }
    }

    /**
     * Hide the cursor.
     */
    public void hideCursor() {
        if (mCursor.isVisible()) {
            mCursor.setVisible(false);
            invalidate();
        }
    }

    /**
     * Calculate and show page number according to current viewport position.
     * In particular, this function compares the middle point of the
     * view port with page rectangles and finds out which page the user
     * is currently on. It does not update the associated canvas element
     * unless there is a change of page number.
     */
    public void showPageNumberRect() {
        if (null == mPartPageRectangles) return;
        PointF midPoint = mLayerView.getLayerClient().convertViewPointToLayerPoint(new PointF(getWidth()/2f, getHeight()/2f));
        int index = previousIndex;
        // search which page the user in currently on. can enhance the search algorithm to binary search if necessary
        for (RectF page : mPartPageRectangles) {
            if (page.top < midPoint.y && midPoint.y < page.bottom) {
                index = mPartPageRectangles.indexOf(page) + 1;
                break;
            }
        }
        // index == 0 applies to non-text document, i.e. don't show page info on non-text docs
        if (index == 0) {
            return;
        }
        // if page rectangle canvas element is not visible or the page number is changed, show
        if (!mPageNumberRect.isVisible() || index != previousIndex) {
            previousIndex = index;
            String pageNumberString = getContext().getString(R.string.page) + " " + index + "/" + mPartPageRectangles.size();
            mPageNumberRect.setPageNumberString(pageNumberString);
            mPageNumberRect.setVisible(true);
            invalidate();
        }
    }

    /**
     * Hide page number rectangle canvas element.
     */
    public void hidePageNumberRect() {
        if (null == mPageNumberRect) return;
        if (mPageNumberRect.isVisible()) {
            mPageNumberRect.setVisible(false);
            invalidate();
        }
    }

    /**
     * Show text selection rectangles.
     */
    public void showSelections() {
        if (!mSelectionsVisible) {
            mSelectionsVisible = true;
            invalidate();
        }
    }

    /**
     * Hide text selection rectangles.
     */
    public void hideSelections() {
        if (mSelectionsVisible) {
            mSelectionsVisible = false;
            invalidate();
        }
    }

    /**
     * Show the graphic selection on the view.
     */
    public void showGraphicSelection() {
        if (!mGraphicSelection.isVisible()) {
            mGraphicSelectionMove = false;
            mGraphicSelection.reset();
            mGraphicSelection.setVisible(true);
            invalidate();
        }
    }

    /**
     * Hide the graphic selection.
     */
    public void hideGraphicSelection() {
        if (mGraphicSelection.isVisible()) {
            mGraphicSelection.setVisible(false);
            invalidate();
        }
    }

    /**
     * Handle the triggered touch event.
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        PointF point = new PointF(event.getX(), event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (mAdjustLengthLine != null && !mAdjustLengthLine.contains(point.x, point.y)) {
                    mAdjustLengthLine.setVisible(false);
                    invalidate();
                }
                if (mGraphicSelection.isVisible()) {
                    // Check if inside graphic selection was hit
                    if (mGraphicSelection.contains(point.x, point.y)) {
                        mGraphicSelectionMove = true;
                        mGraphicSelection.dragStart(point);
                        invalidate();
                        return true;
                    }
                } else {
                    if (mHandleStart.contains(point.x, point.y)) {
                        mHandleStart.dragStart(point);
                        mDragHandle = mHandleStart;
                        return true;
                    } else if (mHandleEnd.contains(point.x, point.y)) {
                        mHandleEnd.dragStart(point);
                        mDragHandle = mHandleEnd;
                        return true;
                    } else if (mHandleMiddle.contains(point.x, point.y)) {
                        mHandleMiddle.dragStart(point);
                        mDragHandle = mHandleMiddle;
                        return true;
                    } else if (mCalcSelectionBox != null &&
                            mCalcSelectionBox.contains(point.x, point.y) &&
                            !mHandleStart.isVisible()) {
                        mCalcSelectionBox.dragStart(point);
                        mCalcSelectionBoxDragging = true;
                        return true;
                    } else if (mAdjustLengthLine != null &&
                            mAdjustLengthLine.contains(point.x, point.y)) {
                        mAdjustLengthLine.dragStart(point);
                        mAdjustLengthLineDragging = true;
                        return true;
                    }
                }
            }
            case MotionEvent.ACTION_UP: {
                if (mGraphicSelection.isVisible() && mGraphicSelectionMove) {
                    mGraphicSelection.dragEnd(point);
                    mGraphicSelectionMove = false;
                    invalidate();
                    return true;
                } else if (mDragHandle != null) {
                    mDragHandle.dragEnd(point);
                    mDragHandle = null;
                } else if (mCalcSelectionBoxDragging) {
                    mCalcSelectionBox.dragEnd(point);
                    mCalcSelectionBoxDragging = false;
                } else if (mAdjustLengthLineDragging) {
                    mAdjustLengthLine.dragEnd(point);
                    mAdjustLengthLineDragging = false;
                    invalidate();
                }
            }
            case MotionEvent.ACTION_MOVE: {
                if (mGraphicSelection.isVisible() && mGraphicSelectionMove) {
                    mGraphicSelection.dragging(point);
                    invalidate();
                    return true;
                } else if (mDragHandle != null) {
                    mDragHandle.dragging(point);
                } else if (mCalcSelectionBoxDragging) {
                    mCalcSelectionBox.dragging(point);
                } else if (mAdjustLengthLineDragging) {
                    mAdjustLengthLine.dragging(point);
                    invalidate();
                }
            }
        }
        return false;
    }

    /**
     * Change the handle document position.
     * @param type - the type of the handle
     * @param position - the new document position
     */
    public void positionHandle(SelectionHandle.HandleType type, RectF position) {
        SelectionHandle handle = getHandleForType(type);
        if (RectUtils.fuzzyEquals(handle.mDocumentPosition, position)) {
            return;
        }

        RectUtils.assign(handle.mDocumentPosition, position);

        ImmutableViewportMetrics metrics = mLayerView.getViewportMetrics();
        repositionWithViewport(metrics.viewportRectLeft, metrics.viewportRectTop, metrics.zoomFactor);
    }

    /**
     * Hide the handle.
     * @param type - type of the handle
     */
    public void hideHandle(SelectionHandle.HandleType type) {
        SelectionHandle handle = getHandleForType(type);
        if (handle.isVisible()) {
            handle.setVisible(false);
            invalidate();
        }
    }

    /**
     * Show the handle.
     * @param type - type of the handle
     */
    public void showHandle(SelectionHandle.HandleType type) {
        SelectionHandle handle = getHandleForType(type);
        if (!handle.isVisible()) {
            handle.setVisible(true);
            invalidate();
        }
    }

    /**
     * Returns the handle instance for the input type.
     */
    private SelectionHandle getHandleForType(SelectionHandle.HandleType type) {
        switch(type) {
            case START:
                return mHandleStart;
            case END:
                return mHandleEnd;
            case MIDDLE:
                return mHandleMiddle;
        }
        return null;
    }

    public RectF getCurrentCursorPosition() {
        return mCursor.mPosition;
    }

    public void setCalcHeadersController(CalcHeadersController calcHeadersController) {
        mCalcHeadersController = calcHeadersController;
        mCalcSelectionBox = new CalcSelectionBox((MainActivity) getContext());
    }

    public void showCellSelection(RectF cellCursorRect) {
        if (mCalcHeadersController == null || mCalcSelectionBox == null) return;
        if (RectUtils.fuzzyEquals(mCalcSelectionBox.mDocumentPosition, cellCursorRect)) {
            return;
        }

        // show selection on main GL view (i.e. in the document)
        RectUtils.assign(mCalcSelectionBox.mDocumentPosition, cellCursorRect);
        mCalcSelectionBox.setVisible(true);

        ImmutableViewportMetrics metrics = mLayerView.getViewportMetrics();
        repositionWithViewport(metrics.viewportRectLeft, metrics.viewportRectTop, metrics.zoomFactor);

        // show selection on headers
        if (!mCalcHeadersController.pendingRowOrColumnSelectionToShowUp()) {
            showHeaderSelection(cellCursorRect);
        } else {
            mCalcHeadersController.setPendingRowOrColumnSelectionToShowUp(false);
        }
    }

    public void showHeaderSelection(RectF rect) {
        if (mCalcHeadersController == null) return;
        mCalcHeadersController.showHeaderSelection(rect);
    }

    public void showAdjustLengthLine(boolean isRow, final CalcHeadersView view) {
        mAdjustLengthLine = new AdjustLengthLine((MainActivity) getContext(), view, isRow, getWidth(), getHeight());
        ImmutableViewportMetrics metrics = mLayerView.getViewportMetrics();
        RectF position = convertToScreen(mCalcSelectionBox.mDocumentPosition, metrics.viewportRectLeft, metrics.viewportRectTop, metrics.zoomFactor);
        mAdjustLengthLine.setScreenRect(position);
        mAdjustLengthLine.setVisible(true);
        invalidate();
    }
}


