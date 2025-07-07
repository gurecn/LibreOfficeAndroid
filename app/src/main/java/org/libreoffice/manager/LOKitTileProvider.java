package org.libreoffice.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.view.KeyEvent;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import org.libreoffice.data.LOEvent;
import org.libreoffice.adapter.PDFDocumentAdapter;
import org.libreoffice.R;
import org.libreoffice.TileProvider;
import org.libreoffice.data.DocumentPartView;
import org.libreoffice.kit.DirectBufferAllocator;
import org.libreoffice.kit.Document;
import org.libreoffice.kit.LibreOfficeKit;
import org.libreoffice.kit.Office;
import org.libreoffice.ui.MainActivity;
import org.mozilla.gecko.gfx.BufferedCairoImage;
import org.mozilla.gecko.gfx.CairoImage;
import org.mozilla.gecko.gfx.IntSize;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * LOKit implementation of TileProvider.
 */
public class LOKitTileProvider implements TileProvider {
    private static final int TILE_SIZE = 256;
    private final float mTileWidth;
    private final float mTileHeight;
    private String mInputFile;
    private Office mOffice;
    private Document mDocument;
    private final boolean mIsReady;
    private final MainActivity mContext;
    private final float mDPI;
    private float mWidthTwip;
    private float mHeightTwip;
    private final Document.MessageCallback mMessageCallback;
    private final long objectCreationTime = System.currentTimeMillis();

    /**
     * Initialize LOKit and load the document.
     * @param messageCallback - callback for messages retrieved from LOKit
     * @param input - input path of the document
     */
    public LOKitTileProvider(MainActivity context, InvalidationHandler messageCallback, String input) {
        mContext = context;
        mMessageCallback = messageCallback;
        LibreOfficeKit.init(mContext);
        mOffice = new Office(LibreOfficeKit.getLibreOfficeKitHandle());
        mOffice.setMessageCallback(messageCallback);
        mOffice.setOptionalFeatures(Document.LOK_FEATURE_DOCUMENT_PASSWORD);
        mContext.setTileProvider(this);
        mInputFile = input;
        File fileToBeEncoded = new File(input);
        String encodedFileName = android.net.Uri.encode(fileToBeEncoded.getName());
        mDocument = mOffice.documentLoad((new File(fileToBeEncoded.getParent(),encodedFileName)).getPath());
        if (mDocument == null && !mContext.isPasswordProtected()) {
            mOffice.destroy();
            ByteBuffer handle = LibreOfficeKit.getLibreOfficeKitHandle();
            mOffice = new Office(handle);
            mOffice.setMessageCallback(messageCallback);
            mOffice.setOptionalFeatures(Document.LOK_FEATURE_DOCUMENT_PASSWORD);
            mDocument = mOffice.documentLoad((new File(fileToBeEncoded.getParent(),encodedFileName)).getPath());
        }
        mDPI = LOKitShell.getDpi(mContext);
        mTileWidth = pixelToTwip(TILE_SIZE, mDPI);
        mTileHeight = pixelToTwip(TILE_SIZE, mDPI);

        if (mDocument != null)
            mDocument.initializeForRendering();

        if (checkDocument()) {
            postLoad();
            mIsReady = true;
        } else {
            mIsReady = false;
        }
    }

    /**
     * Triggered after the document is loaded.
     */
    private void postLoad() {
        mDocument.setMessageCallback(mMessageCallback);
        resetParts();
        // Writer documents always have one part, so hide the navigation drawer.
        if (mDocument.getDocumentType() == Document.DOCTYPE_TEXT) {
            mContext.disableNavigationDrawer();
            mContext.getToolbarController().hideItem(R.id.action_parts);
        }
        // Enable headers for Calc documents
        if (mDocument.getDocumentType() == Document.DOCTYPE_SPREADSHEET) {
            mContext.initializeCalcHeaders();
        }
        mDocument.setPart(0);
        setupDocumentFonts();
        LOKitShell.getMainHandler().post(() -> mContext.getDocumentPartViewListAdapter().notifyDataSetChanged());
    }

    public void addPart(){
        int parts = mDocument.getParts();
        if(mDocument.getDocumentType() == Document.DOCTYPE_SPREADSHEET){
            try{
                JSONObject jsonObject = new JSONObject();
                JSONObject values = new JSONObject();
                JSONObject values2 = new JSONObject();
                values.put("type", "long");
                values.put("value", 0); //add to the last
                values2.put("type", "string");
                values2.put("value", "");
                jsonObject.put("Name", values2);
                jsonObject.put("Index", values);
                LOKitShell.sendEvent(new LOEvent(LOEvent.UNO_COMMAND, ".uno:Insert", jsonObject.toString()));
            }catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (mDocument.getDocumentType() == Document.DOCTYPE_PRESENTATION){
            LOKitShell.sendEvent(new LOEvent(LOEvent.UNO_COMMAND, ".uno:InsertPage"));
        }
        String partName = mDocument.getPartName(parts);
        if (partName.isEmpty()) {
            partName = getGenericPartName(parts);
        }
        mDocument.setPart(parts);
        resetDocumentSize();
        final DocumentPartView partView = new DocumentPartView(parts, partName);
        mContext.getDocumentPartView().add(partView);
    }

    public void resetParts(){
        mContext.getDocumentPartView().clear();
        if (mDocument.getDocumentType() != Document.DOCTYPE_TEXT) {
            int parts = mDocument.getParts();
            for (int i = 0; i < parts; i++) {
                String partName = mDocument.getPartName(i);
                if (partName.isEmpty()) {
                    partName = getGenericPartName(i);
                }
                mDocument.setPart(i);
                resetDocumentSize();
                final DocumentPartView partView = new DocumentPartView(i, partName);
                mContext.getDocumentPartView().add(partView);
            }
        }
    }

    public void renamePart(String partName) {
        try{
            for(int i=0; i<mDocument.getParts(); i++){
                if(mContext.getDocumentPartView().get(i).partName.equals(partName)){
                    Toast.makeText(mContext, mContext.getString(R.string.name_already_used), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            JSONObject parameter = new JSONObject();
            JSONObject name = new JSONObject();
            name.put("type", "string");
            name.put("value", partName);
            parameter.put("Name", name);
            if(isPresentation()){
                LOKitShell.sendEvent(new LOEvent(LOEvent.UNO_COMMAND_NOTIFY, ".uno:RenamePage", parameter.toString(),true));
            }else {
                JSONObject index = new JSONObject();
                index.put("type","long");
                index.put("value", getCurrentPartNumber()+1);
                parameter.put("Index", index);
                LOKitShell.sendEvent(new LOEvent(LOEvent.UNO_COMMAND_NOTIFY, ".uno:Name", parameter.toString(),true));
            }
        }catch (JSONException e){
            e.printStackTrace();
        }
    }

    public void removePart() {
        try{
            if (!isSpreadsheet() && !isPresentation()) return;
            if(isPresentation()){
                LOKitShell.sendEvent(new LOEvent(LOEvent.UNO_COMMAND_NOTIFY, ".uno:DeletePage", true));
                return;
            }
            if(getPartsCount() < 2)return;
            JSONObject parameter = new JSONObject();
            JSONObject index = new JSONObject();
            index.put("type","long");
            index.put("value", getCurrentPartNumber()+1);
            parameter.put("Index", index);
            LOKitShell.sendEvent(new LOEvent(LOEvent.UNO_COMMAND_NOTIFY, ".uno:Remove", parameter.toString(),true));
        }catch (JSONException e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean saveDocumentAs(final String filePath, String format, boolean takeOwnership) {
        String options = "";
        if (takeOwnership) {
            options = "TakeOwnership";
        }
        final String newFilePath = "file://" + filePath;
        LOKitShell.showProgressSpinner(mContext);
        mDocument.saveAs(newFilePath, format, options);
        final boolean ok;
        if (!mOffice.getError().isEmpty()){
            ok = true;
            LOKitShell.getMainHandler().post(() -> mContext.showCustomStatusMessage(mContext.getString(R.string.unable_to_save)));
        } else {
            ok = false;
            if (format.equals("svg")) {
                LOKitShell.getMainHandler().post(() -> mContext.startPresentation(newFilePath));
            } else if (takeOwnership) {
                mInputFile = filePath;
            }
        }
        LOKitShell.hideProgressSpinner(mContext);
        return ok;
    }

    @Override
    public boolean saveDocumentAs(final String filePath, boolean takeOwnership) {
        final int docType = mDocument.getDocumentType();
        if (docType == Document.DOCTYPE_TEXT)
            return saveDocumentAs(filePath, "odt", takeOwnership);
        else if (docType == Document.DOCTYPE_SPREADSHEET)
            return saveDocumentAs(filePath, "ods", takeOwnership);
        else if (docType == Document.DOCTYPE_PRESENTATION)
            return saveDocumentAs(filePath, "odp", takeOwnership);
        else if (docType == Document.DOCTYPE_DRAWING)
            return saveDocumentAs(filePath, "odg", takeOwnership);
        return false;
    }

    public void printDocument() {
        String mInputFileName = (new File(mInputFile)).getName();
        String file = mInputFileName.substring(0,(mInputFileName.length()-3))+"pdf";
        String cacheFile = mContext.getExternalCacheDir().getAbsolutePath() + "/" + file;
        mDocument.saveAs("file://"+cacheFile,"pdf","");
        try {
            PrintManager printManager = (PrintManager) mContext.getSystemService(Context.PRINT_SERVICE);
            PrintDocumentAdapter printAdapter = new PDFDocumentAdapter(mContext, cacheFile);
            printManager.print("Document", printAdapter, new PrintAttributes.Builder().build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveDocument(){
        mContext.saveDocument();
    }

    private void setupDocumentFonts() {
        String values = mDocument.getCommandValues(".uno:CharFontName");
        if (values == null || values.isEmpty()) return;
        mContext.getFontController().parseJson(values);
        mContext.getFontController().setupFontViews();
    }

    private String getGenericPartName(int i) {
        if (mDocument == null) return "";
        return switch (mDocument.getDocumentType()) {
            case Document.DOCTYPE_DRAWING, Document.DOCTYPE_TEXT -> mContext.getString(R.string.page) + " " + (i + 1);
            case Document.DOCTYPE_SPREADSHEET -> mContext.getString(R.string.sheet) + " " + (i + 1);
            case Document.DOCTYPE_PRESENTATION -> mContext.getString(R.string.slide) + " " + (i + 1);
            default -> mContext.getString(R.string.part) + " " + (i + 1);
        };
    }

    static float twipToPixel(float input, float dpi) {
        return input / 1440.0f * dpi;
    }

    private static float pixelToTwip(float input, float dpi) {
        return (input / dpi) * 1440.0f;
    }


    /**
     * @see TileProvider#getPartsCount()
     */
    @Override
    public int getPartsCount() {
        return mDocument.getParts();
    }

    /**
     * Wrapper for getPartPageRectangles() JNI function.
     */
    public String getPartPageRectangles() {
        return mDocument.getPartPageRectangles();
    }

    /**
     * Fetch Calc header information.
     */
    public String getCalcHeaders() {
        long nX = 0;
        long nY = 0;
        long nWidth = mDocument.getDocumentWidth();
        long nHeight = mDocument.getDocumentHeight();
        return mDocument.getCommandValues(".uno:ViewRowColumnHeaders?x=" + nX + "&y=" + nY
                + "&width=" + nWidth + "&height=" + nHeight);
    }

    /**
     * @see TileProvider#onSwipeLeft()
     */
    @Override
    public void onSwipeLeft() {
        if (getCurrentPartNumber() < getPartsCount()-1) {
            LOKitShell.sendChangePartEvent(getCurrentPartNumber()+1);
        }
    }

    /**
     * @see TileProvider#onSwipeRight()
     */
    @Override
    public void onSwipeRight() {
        if (getCurrentPartNumber() > 0) {
            LOKitShell.sendChangePartEvent(getCurrentPartNumber()-1);
        }
    }

    private boolean checkDocument() {
        String error = null;
        boolean ret;

        if (mDocument == null || !mOffice.getError().isEmpty()) {
            error = "Cannot open " + mInputFile + ": " + mOffice.getError();
            ret = false;
        } else {
            ret = resetDocumentSize();
            if (!ret) {
                error = "Document returned an invalid size or the document is empty.";
            }
        }

        if (!ret && !mContext.isPasswordProtected()) {
            final String message = error;
            LOKitShell.getMainHandler().post(new Runnable() {
                @Override
                public void run() {
                    mContext.showAlertDialog(message);
                }
            });
        } else if (!ret && mContext.isPasswordProtected()) {
            mContext.finish();
        }

        return ret;
    }

    private boolean resetDocumentSize() {
        mWidthTwip = mDocument.getDocumentWidth();
        mHeightTwip = mDocument.getDocumentHeight();
        return mWidthTwip != 0 && mHeightTwip != 0;
    }

    @Override
    public void setDocumentSize(int pageWidth, int pageHeight){
        mWidthTwip = pageWidth;
        mHeightTwip = pageHeight;
    }

    /**
     * @see TileProvider#getPageWidth()
     */
    @Override
    public int getPageWidth() {
        return (int) twipToPixel(mWidthTwip, mDPI);
    }

    /**
     * @see TileProvider#getPageHeight()
     */
    @Override
    public int getPageHeight() {
        return (int) twipToPixel(mHeightTwip, mDPI);
    }

    /**
     * @see TileProvider#isReady()
     */
    @Override
    public boolean isReady() {
        return mIsReady;
    }

    /**
     * @see TileProvider#createTile(float, float, IntSize, float)
     */
    @Override
    public CairoImage createTile(float x, float y, IntSize tileSize, float zoom) {
        ByteBuffer buffer = DirectBufferAllocator.guardedAllocate(tileSize.width * tileSize.height * 4);
        if (buffer == null)
            return null;

        CairoImage image = new BufferedCairoImage(buffer, tileSize.width, tileSize.height, CairoImage.FORMAT_ARGB32);
        rerenderTile(image, x, y, tileSize, zoom);
        return image;
    }

    /**
     * @see TileProvider#rerenderTile(CairoImage, float, float, IntSize, float)
     */
    @Override
    public void rerenderTile(CairoImage image, float x, float y, IntSize tileSize, float zoom) {
        if (mDocument != null && image.getBuffer() != null) {
            float twipX = pixelToTwip(x, mDPI) / zoom;
            float twipY = pixelToTwip(y, mDPI) / zoom;
            float twipWidth = mTileWidth / zoom;
            float twipHeight = mTileHeight / zoom;
            mDocument.paintTile(image.getBuffer(), tileSize.width, tileSize.height, (int) twipX, (int) twipY, (int) twipWidth, (int) twipHeight);
        }
    }

    /**
     * @see TileProvider#thumbnail(int)
     */
    @Override
    public Bitmap thumbnail(int size) {
        int widthPixel = getPageWidth();
        int heightPixel = getPageHeight();

        if (widthPixel > heightPixel) {
            double ratio = heightPixel / (double) widthPixel;
            widthPixel = size;
            heightPixel = (int) (widthPixel * ratio);
        } else {
            double ratio = widthPixel / (double) heightPixel;
            heightPixel = size;
            widthPixel = (int) (heightPixel * ratio);
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(widthPixel * heightPixel * 4);
        if (mDocument != null) mDocument.paintTile(buffer, widthPixel, heightPixel, 0, 0, (int) mWidthTwip, (int) mHeightTwip);
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(widthPixel, heightPixel, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
        } catch (IllegalArgumentException ignored) {}
        return bitmap;
    }

    /**
     * @see TileProvider#close()
     */
    @Override
    public void close() {
        if (mDocument != null) {
            mDocument.destroy();
            mDocument = null;
        }
    }

    /**
     * @see TileProvider#isDrawing()
     */
    @Override
    public boolean isDrawing() {
        return mDocument != null && mDocument.getDocumentType() == Document.DOCTYPE_DRAWING;
    }

    /**
     * @see TileProvider#isTextDocument()
     */
    @Override
    public boolean isTextDocument() {
        return mDocument != null && mDocument.getDocumentType() == Document.DOCTYPE_TEXT;
    }

    /**
     * @see TileProvider#isSpreadsheet()
     */
    @Override
    public boolean isSpreadsheet() {
        return mDocument != null && mDocument.getDocumentType() == Document.DOCTYPE_SPREADSHEET;
    }

    /**
     * @see TileProvider#isPresentation()
     */
    @Override
    public boolean isPresentation(){
        return mDocument != null && mDocument.getDocumentType() == Document.DOCTYPE_PRESENTATION;
    }

    /**
     * Returns the Unicode character generated by this event or 0.
     */
    private int getCharCode(KeyEvent keyEvent) {
        return switch (keyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER -> 0;
            default -> keyEvent.getUnicodeChar();
        };
    }

    /**
     * Returns the integer code representing the key of the event (non-zero for
     * control keys).
     */
    private int getKeyCode(KeyEvent keyEvent) {
        return switch (keyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_DEL -> 1283;
            case KeyEvent.KEYCODE_ENTER -> 1280;
            default -> 0;
        };
    }

    /**
     * @see TileProvider#sendKeyEvent(KeyEvent)
     */
    @Override
    public void sendKeyEvent(KeyEvent keyEvent) {
        switch (keyEvent.getAction()) {
            case KeyEvent.ACTION_MULTIPLE:
                String keyString = keyEvent.getCharacters();
                for (int i = 0; i < keyString.length(); i++) {
                    int codePoint = keyString.codePointAt(i);
                    mDocument.postKeyEvent(Document.KEY_EVENT_PRESS, codePoint, getKeyCode(keyEvent));
                }
                break;
            case KeyEvent.ACTION_DOWN:
                mDocument.postKeyEvent(Document.KEY_EVENT_PRESS, getCharCode(keyEvent), getKeyCode(keyEvent));
                break;
            case KeyEvent.ACTION_UP:
                mDocument.postKeyEvent(Document.KEY_EVENT_RELEASE, getCharCode(keyEvent), getKeyCode(keyEvent));
                break;
        }
    }

    private void mouseButton(int type, PointF inDocument, int numberOfClicks, float zoomFactor) {
        int x = (int) pixelToTwip(inDocument.x, mDPI);
        int y = (int) pixelToTwip(inDocument.y, mDPI);
        mDocument.setClientZoom(TILE_SIZE, TILE_SIZE, (int) (mTileWidth / zoomFactor), (int) (mTileHeight / zoomFactor));
        mDocument.postMouseEvent(type, x, y, numberOfClicks, Document.MOUSE_BUTTON_LEFT, Document.KEYBOARD_MODIFIER_NONE);
    }

    /**
     * @see TileProvider#mouseButtonDown(PointF, int, float)
     */
    @Override
    public void mouseButtonDown(PointF documentCoordinate, int numberOfClicks, float zoomFactor) {
        mouseButton(Document.MOUSE_EVENT_BUTTON_DOWN, documentCoordinate, numberOfClicks, zoomFactor);
    }

    /**
     * @see TileProvider#mouseButtonUp(PointF, int, float)
     */
    @Override
    public void mouseButtonUp(PointF documentCoordinate, int numberOfClicks, float zoomFactor) {
        mouseButton(Document.MOUSE_EVENT_BUTTON_UP, documentCoordinate, numberOfClicks, zoomFactor);
    }

    /**
     * @param command   UNO command string
     * @param arguments Arguments to UNO command
     */
    @Override
    public void postUnoCommand(String command, String arguments) {
        postUnoCommand(command, arguments, false);
    }

    @Override
    public void postUnoCommand(String command, String arguments, boolean notifyWhenFinished) {
        mDocument.postUnoCommand(command, arguments, notifyWhenFinished);
    }

    private void setTextSelection(int type, PointF documentCoordinate) {
        int x = (int) pixelToTwip(documentCoordinate.x, mDPI);
        int y = (int) pixelToTwip(documentCoordinate.y, mDPI);
        mDocument.setTextSelection(type, x, y);
    }

    /**
     * @see TileProvider#setTextSelectionStart(PointF)
     */
    @Override
    public void setTextSelectionStart(PointF documentCoordinate) {
        setTextSelection(Document.SET_TEXT_SELECTION_START, documentCoordinate);
    }

    /**
     * @see TileProvider#setTextSelectionEnd(PointF)
     */
    @Override
    public void setTextSelectionEnd(PointF documentCoordinate) {
        setTextSelection(Document.SET_TEXT_SELECTION_END, documentCoordinate);
    }

    /**
     * @see TileProvider#setTextSelectionReset(PointF)
     */
    @Override
    public void setTextSelectionReset(PointF documentCoordinate) {
        setTextSelection(Document.SET_TEXT_SELECTION_RESET, documentCoordinate);
    }

    @Override
    public String getTextSelection(String mimeType) {
        return mDocument.getTextSelection(mimeType);
    }

    @Override
    public boolean paste(String mimeType, String data) {
        return mDocument.paste(mimeType, data);
    }

    /**
     * @see TileProvider#setGraphicSelectionStart(PointF)
     */
    @Override
    public void setGraphicSelectionStart(PointF documentCoordinate) {
        setGraphicSelection(Document.SET_GRAPHIC_SELECTION_START, documentCoordinate);
    }

    /**
     * @see TileProvider#setGraphicSelectionEnd(PointF)
     */
    @Override
    public void setGraphicSelectionEnd(PointF documentCoordinate) {
        setGraphicSelection(Document.SET_GRAPHIC_SELECTION_END, documentCoordinate);
    }

    private void setGraphicSelection(int type, PointF documentCoordinate) {
        int x = (int) pixelToTwip(documentCoordinate.x, mDPI);
        int y = (int) pixelToTwip(documentCoordinate.y, mDPI);
        MainActivity.setDocumentChanged(true);
        mDocument.setGraphicSelection(type, x, y);
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    /**
     * @see TileProvider#changePart(int)
     */
    @Override
    public void changePart(int partIndex) {
        if (mDocument == null)
            return;

        mDocument.setPart(partIndex);
        resetDocumentSize();
    }

    /**
     * @see TileProvider#getCurrentPartNumber()
     */
    @Override
    public int getCurrentPartNumber() {
        if (mDocument == null)
            return 0;

        return mDocument.getPart();
    }

    public void setDocumentPassword(String url, String password) {
        mOffice.setDocumentPassword(url, password);
    }

    public Document.MessageCallback getMessageCallback() {
        return mMessageCallback;
    }
}

// vim:set shiftwidth=4 softtabstop=4 expandtab:
