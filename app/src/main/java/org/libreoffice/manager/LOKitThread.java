package org.libreoffice.manager;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.KeyEvent;
import org.libreoffice.data.LOEvent;
import org.libreoffice.utils.ThumbnailCreator;
import org.libreoffice.data.TileIdentifier;
import org.libreoffice.TileProvider;
import org.libreoffice.utils.TileProviderFactory;
import org.libreoffice.canvas.SelectionHandle;
import org.libreoffice.ui.MainActivity;
import org.mozilla.gecko.ZoomConstraints;
import org.mozilla.gecko.gfx.CairoImage;
import org.mozilla.gecko.gfx.ComposedTileLayer;
import org.mozilla.gecko.gfx.GeckoLayerClient;
import org.mozilla.gecko.gfx.ImmutableViewportMetrics;
import org.mozilla.gecko.gfx.SubTile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class LOKitThread extends Thread {
    private final LinkedBlockingQueue<LOEvent> mEventQueue = new LinkedBlockingQueue<>();
    private TileProvider mTileProvider;
    private InvalidationHandler mInvalidationHandler;
    private ImmutableViewportMetrics mViewportMetrics;
    private GeckoLayerClient mLayerClient;
    private final MainActivity mContext;

    public LOKitThread(MainActivity context) {
        mContext = context;
        mInvalidationHandler = null;
        TileProviderFactory.initialize();
    }

    /**
     * Starting point of the thread. Processes events that gather in the queue.
     */
    @Override
    public void run() {
        while (true) {
            try {
                LOEvent event = mEventQueue.take();
                processEvent(event);
            } catch (InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    /**
     * Viewport changed, Recheck if tiles need to be added / removed.
     */
    private void tileReevaluationRequest(ComposedTileLayer composedTileLayer) {
        if (mTileProvider == null) {
            return;
        }
        List<SubTile> tiles = new ArrayList<SubTile>();

        mLayerClient.beginDrawing();
        composedTileLayer.addNewTiles(tiles);
        mLayerClient.endDrawing();

        for (SubTile tile : tiles) {
            TileIdentifier tileId = tile.id;
            CairoImage image = mTileProvider.createTile(tileId.x, tileId.y, tileId.size, tileId.zoom);
            mLayerClient.beginDrawing();
            if (image != null) {
                tile.setImage(image);
            }
            mLayerClient.endDrawing();
            mLayerClient.forceRender();
        }

        mLayerClient.beginDrawing();
        composedTileLayer.markTiles();
        composedTileLayer.clearMarkedTiles();
        mLayerClient.endDrawing();
        mLayerClient.forceRender();
    }

    /**
     * Invalidate tiles that intersect the input rect.
     */
    private void tileInvalidation(RectF rect) {
        if (mLayerClient == null || mTileProvider == null) {
            return;
        }

        mLayerClient.beginDrawing();

        List<SubTile> tiles = new ArrayList<SubTile>();
        mLayerClient.invalidateTiles(tiles, rect);

        for (SubTile tile : tiles) {
            CairoImage image = mTileProvider.createTile(tile.id.x, tile.id.y, tile.id.size, tile.id.zoom);
            tile.setImage(image);
            tile.invalidate();
        }
        mLayerClient.endDrawing();
        mLayerClient.forceRender();
    }

    /**
     * Handle the geometry change + draw.
     */
    private void redraw(boolean resetZoomAndPosition) {
        if (mLayerClient == null || mTileProvider == null) {
            // called too early...
            return;
        }

        mLayerClient.setPageRect(0, 0, mTileProvider.getPageWidth(), mTileProvider.getPageHeight());
        mViewportMetrics = mLayerClient.getViewportMetrics();
        mLayerClient.setViewportMetrics(mViewportMetrics);

        if (resetZoomAndPosition) {
            zoomAndRepositionTheDocument();
        }

        mLayerClient.forceRedraw();
        mLayerClient.forceRender();
    }

    /**
     * Reposition the view (zoom and position) when the document is firstly shown. This is document type dependent.
     */
    private void zoomAndRepositionTheDocument() {
        if (mTileProvider.isSpreadsheet()) {
            // Don't do anything for spreadsheets - show at 100%
        } else if (mTileProvider.isTextDocument()) {
            // Always zoom text document to the beginning of the document and centered by width
            float centerY = mViewportMetrics.getCssViewport().centerY();
            mLayerClient.zoomTo(new RectF(0, centerY, mTileProvider.getPageWidth(), centerY));
        } else {
            // Other documents - always show the whole document on the screen,
            // regardless of document shape and orientation.
            if (mViewportMetrics.getViewport().width() < mViewportMetrics.getViewport().height()) {
                mLayerClient.zoomTo(mTileProvider.getPageWidth(), 0);
            } else {
                mLayerClient.zoomTo(0, mTileProvider.getPageHeight());
            }
        }
    }

    /**
     * Invalidate everything + handle the geometry change
     */
    private void refresh(boolean resetZoomAndPosition) {
        mLayerClient.clearAndResetlayers();
        redraw(resetZoomAndPosition);
        updatePartPageRectangles();
        if (mTileProvider != null && mTileProvider.isSpreadsheet()) {
            updateCalcHeaders();
        }
    }

    /**
     * Update part page rectangles which hold positions of each document page.
     * Result is stored in DocumentOverlayView class.
     */
    private void updatePartPageRectangles() {
        if (mTileProvider == null) {
            return;
        }
        String partPageRectString = ((LOKitTileProvider) mTileProvider).getPartPageRectangles();
        List<RectF> partPageRectangles = mInvalidationHandler.convertPayloadToRectangles(partPageRectString);
        mContext.getDocumentOverlay().setPartPageRectangles(partPageRectangles);
    }

    private void updatePageSize(int pageWidth, int pageHeight){
        mTileProvider.setDocumentSize(pageWidth, pageHeight);
        redraw(true);
    }

    private void updateZoomConstraints() {
        if (mTileProvider == null) return;
        mLayerClient = mContext.getLayerClient();
        // Set default zoom to the page width and min zoom so that the whole page is visible
        final float pageHeightZoom = mLayerClient.getViewportMetrics().getHeight() / mTileProvider.getPageHeight();
        final float pageWidthZoom = mLayerClient.getViewportMetrics().getWidth() / mTileProvider.getPageWidth();
        final float minZoom = Math.min(pageWidthZoom, pageHeightZoom);
        mLayerClient.setZoomConstraints(new ZoomConstraints(pageWidthZoom, minZoom, 0f));
    }

    /**
     * Change part of the document.
     */
    private void changePart(int partIndex) {
        LOKitShell.showProgressSpinner(mContext);
        mTileProvider.changePart(partIndex);
        mViewportMetrics = mLayerClient.getViewportMetrics();
        // mLayerClient.setViewportMetrics(mViewportMetrics.scaleTo(0.9f, new PointF()));
        refresh(true);
        LOKitShell.hideProgressSpinner(mContext);
    }

    /**
     * Handle load document event.
     * @param filePath - filePath to where the document is located
     * @return Whether the document has been loaded successfully.
     */
    private boolean loadDocument(String filePath) {
        mLayerClient = mContext.getLayerClient();

        mInvalidationHandler = new InvalidationHandler(mContext);
        mTileProvider = TileProviderFactory.create(mContext, mInvalidationHandler, filePath);

        if (mTileProvider.isReady()) {
            LOKitShell.showProgressSpinner(mContext);
            updateZoomConstraints();
            refresh(true);
            LOKitShell.hideProgressSpinner(mContext);
            return true;
        } else {
            closeDocument();
            return false;
        }
    }

    /**
     * Handle load new document event.
     * @param filePath - filePath to where new document is to be created
     * @param fileType - fileType what type of new document is to be loaded
     */
    private void loadNewDocument(String filePath, String fileType) {
        boolean ok = loadDocument(fileType);
        if (ok) {
            mTileProvider.saveDocumentAs(filePath, true);
        }
    }

    /**
     * Save the currently loaded document.
     */
    private void saveDocumentAs(String filePath, String fileType, boolean bTakeOwnership) {
       if (mTileProvider != null) {
           mTileProvider.saveDocumentAs(filePath, fileType, bTakeOwnership);
       }
    }

    /**
     * Close the currently loaded document.
     */
    private void closeDocument() {
        if (mTileProvider != null) {
            mTileProvider.close();
            mTileProvider = null;
        }
    }

    /**
     * Process the input event.
     */
    private void processEvent(LOEvent event) {
        switch (event.mType) {
            case LOEvent.LOAD:
                loadDocument(event.filePath);
                break;
            case LOEvent.LOAD_NEW:
                loadNewDocument(event.filePath, event.fileType);
                break;
            case LOEvent.SAVE_AS:
                saveDocumentAs(event.filePath, event.fileType, true);
                break;
            case LOEvent.SAVE_COPY_AS:
                saveDocumentAs(event.filePath, event.fileType, false);
                break;
            case LOEvent.CLOSE:
                closeDocument();
                break;
            case LOEvent.SIZE_CHANGED:
                redraw(false);
                break;
            case LOEvent.CHANGE_PART:
                changePart(event.mPartIndex);
                break;
            case LOEvent.TILE_INVALIDATION:
                tileInvalidation(event.mInvalidationRect);
                break;
            case LOEvent.THUMBNAIL:
                createThumbnail(event.mTask);
                break;
            case LOEvent.TOUCH:
                touch(event.mTouchType, event.mDocumentCoordinate);
                break;
            case LOEvent.KEY_EVENT:
                keyEvent(event.mKeyEvent);
                break;
            case LOEvent.TILE_REEVALUATION_REQUEST:
                tileReevaluationRequest(event.mComposedTileLayer);
                break;
            case LOEvent.CHANGE_HANDLE_POSITION:
                changeHandlePosition(event.mHandleType, event.mDocumentCoordinate);
                break;
            case LOEvent.SWIPE_LEFT:
                if (null != mTileProvider) onSwipeLeft();
                break;
            case LOEvent.SWIPE_RIGHT:
                if (null != mTileProvider) onSwipeRight();
                break;
            case LOEvent.NAVIGATION_CLICK:
                mInvalidationHandler.changeStateTo(InvalidationHandler.OverlayState.NONE);
                break;
            case LOEvent.UNO_COMMAND:
                if (null != mTileProvider)
                    mTileProvider.postUnoCommand(event.mString, event.mValue);
                break;
            case LOEvent.UPDATE_PART_PAGE_RECT:
                updatePartPageRectangles();
                break;
            case LOEvent.UPDATE_ZOOM_CONSTRAINTS:
                updateZoomConstraints();
                break;
            case LOEvent.UPDATE_CALC_HEADERS:
                updateCalcHeaders();
                break;
            case LOEvent.UNO_COMMAND_NOTIFY:
                if (null != mTileProvider) mTileProvider.postUnoCommand(event.mString, event.mValue, event.mNotify);
                break;
            case LOEvent.REFRESH:
                refresh(false);
                break;
            case LOEvent.PAGE_SIZE_CHANGED:
                updatePageSize(event.mPageWidth, event.mPageHeight);
                break;
        }
    }

    private void updateCalcHeaders() {
        if (null == mTileProvider) return;
        LOKitTileProvider tileProvider = (LOKitTileProvider)mTileProvider;
        String values = tileProvider.getCalcHeaders();
        mContext.getCalcHeadersController().setHeaders(values);
    }

    /**
     * Request a change of the handle position.
     */
    private void changeHandlePosition(SelectionHandle.HandleType handleType, PointF documentCoordinate) {
        switch (handleType) {
            case MIDDLE:
                mTileProvider.setTextSelectionReset(documentCoordinate);
                break;
            case START:
                mTileProvider.setTextSelectionStart(documentCoordinate);
                break;
            case END:
                mTileProvider.setTextSelectionEnd(documentCoordinate);
                break;
        }
    }

    /**
     * Processes key events.
     */
    private void keyEvent(KeyEvent keyEvent) {
        if (!LOKitShell.isEditingEnabled()) {
            return;
        }
        if (mTileProvider == null) {
            return;
        }
        mInvalidationHandler.keyEvent();
        mTileProvider.sendKeyEvent(keyEvent);
    }

    /**
     * Process swipe left event.
     */
    private void onSwipeLeft() {
        mTileProvider.onSwipeLeft();
    }

    /**
     * Process swipe right event.
     */
    private void onSwipeRight() {
        mTileProvider.onSwipeRight();
    }

    /**
     * Processes touch events.
     */
    private void touch(String touchType, PointF documentCoordinate) {
        if (mTileProvider == null || mViewportMetrics == null) {
            return;
        }

        // to handle hyperlinks, enable single tap even in the Viewer
        boolean editing = LOKitShell.isEditingEnabled();
        float zoomFactor = mViewportMetrics.getZoomFactor();

        if (touchType.equals("LongPress")) {
            mInvalidationHandler.changeStateTo(InvalidationHandler.OverlayState.TRANSITION);
            mTileProvider.mouseButtonDown(documentCoordinate, 1, zoomFactor);
            mTileProvider.mouseButtonUp(documentCoordinate, 1, zoomFactor);
            mTileProvider.mouseButtonDown(documentCoordinate, 2, zoomFactor);
            mTileProvider.mouseButtonUp(documentCoordinate, 2, zoomFactor);
        } else if (touchType.equals("SingleTap")) {
            mInvalidationHandler.changeStateTo(InvalidationHandler.OverlayState.TRANSITION);
            mTileProvider.mouseButtonDown(documentCoordinate, 1, zoomFactor);
            mTileProvider.mouseButtonUp(documentCoordinate, 1, zoomFactor);
        } else if (touchType.equals("GraphicSelectionStart") && editing) {
            mTileProvider.setGraphicSelectionStart(documentCoordinate);
        } else if (touchType.equals("GraphicSelectionEnd") && editing) {
            mTileProvider.setGraphicSelectionEnd(documentCoordinate);
        }
    }

    /**
     * Create thumbnail for the requested document task.
     */
    private void createThumbnail(final ThumbnailCreator.ThumbnailCreationTask task) {
        final Bitmap bitmap = task.getThumbnail(mTileProvider);
        task.applyBitmap(bitmap);
    }

    /**
     * Queue an event.
     */
    public void queueEvent(LOEvent event) {
        mEventQueue.add(event);
    }

    /**
     * Clear all events in the queue (used when document is closed).
     */
    public void clearQueue() {
        mEventQueue.clear();
    }
}


