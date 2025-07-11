package org.libreoffice.ui;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.Toast;
import org.libreoffice.application.CustomConstant;
import org.libreoffice.application.TheApplication;
import org.libreoffice.callback.ZoomCallback;
import org.libreoffice.data.LOEvent;
import org.libreoffice.manager.LOKitInputConnectionHandler;
import org.libreoffice.manager.LOKitShell;
import org.libreoffice.manager.LOKitThread;
import org.libreoffice.R;
import org.libreoffice.data.SettingsListenerModel;
import org.libreoffice.manager.ToolbarController;
import org.libreoffice.adapter.DocumentPartViewListAdapter;
import org.libreoffice.data.DocumentPartView;
import org.libreoffice.manager.FontController;
import org.libreoffice.manager.FormattingController;
import org.libreoffice.manager.LOKitTileProvider;
import org.libreoffice.overlay.CalcHeadersController;
import org.libreoffice.overlay.DocumentOverlay;
import org.libreoffice.utils.FileUtilities;
import org.mozilla.gecko.gfx.GeckoLayerClient;
import org.mozilla.gecko.gfx.JavaPanZoomController;
import org.mozilla.gecko.gfx.LayerView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Main activity of the LibreOffice App. It is started in the UI thread.
 */
public class MainActivity extends AppCompatActivity implements SettingsListenerModel.OnSettingsPreferenceChangedListener {

    private static final int REQUEST_CODE_SAVEAS = 12345;
    private static final int REQUEST_CODE_EXPORT_TO_PDF = 12346;
    public static LOKitThread loKitThread;
    private GeckoLayerClient mLayerClient;
    private static boolean mIsExperimentalMode;
    private static boolean mIsDeveloperMode;
    private static boolean mbISReadOnlyMode;
    private DrawerLayout mDrawerLayout;
    Toolbar toolbarTop;
    private ListView mDrawerList;
    private final List<DocumentPartView> mDocumentPartView = new ArrayList<DocumentPartView>();
    private DocumentPartViewListAdapter mDocumentPartViewListAdapter;
    private DocumentOverlay mDocumentOverlay;
    private Uri mDocumentUri;
    private File mTempFile = null;
    private File mTempSlideShowFile = null;
    BottomSheetBehavior<LinearLayout> bottomToolbarSheetBehavior;
    BottomSheetBehavior<LinearLayout> toolbarColorPickerBottomSheetBehavior;
    BottomSheetBehavior<LinearLayout> toolbarBackColorPickerBottomSheetBehavior;
    private FormattingController mFormattingController;
    private ToolbarController mToolbarController;
    private FontController mFontController;
    private CalcHeadersController mCalcHeadersController;
    private LOKitTileProvider mTileProvider;
    private String mPassword;
    private boolean mPasswordProtected;
    private boolean mbSkipNextRefresh;

    public GeckoLayerClient getLayerClient() {
        return mLayerClient;
    }

    public static boolean isExperimentalMode() {
        return mIsExperimentalMode;
    }

    public static boolean isDeveloperMode() {
        return mIsDeveloperMode;
    }

    private boolean isKeyboardOpen = false;
    private boolean isFormattingToolbarOpen = false;
    private boolean isSearchToolbarOpen = false;
    private static boolean isDocumentChanged = false;
    private boolean isUNOCommandsToolbarOpen = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SettingsListenerModel.getInstance().setListener(this);
        updatePreferences();
        setContentView(R.layout.activity_main);
        toolbarTop = findViewById(R.id.toolbar);
        hideBottomToolbar();
        mToolbarController = new ToolbarController(this, toolbarTop);
        mFormattingController = new FormattingController(this);
        toolbarTop.setNavigationOnClickListener(view -> LOKitShell.sendNavigationClickEvent());
        mFontController = new FontController(this);
        loKitThread = new LOKitThread(this);
        loKitThread.start();
        mLayerClient = new GeckoLayerClient(this);
        LayerView layerView = findViewById(R.id.layer_view);
        ZoomCallback callback = new ZoomCallback() {
            @Override
            public void hideSoftKeyboard() {
                getDocumentOverlay().hidePageNumberRect();
            }
            @Override
            public void showPageNumberRect() {
                getDocumentOverlay().showPageNumberRect();
            }
            @Override
            public void hidePageNumberRect() {

            }
        };
        JavaPanZoomController panZoomController =new JavaPanZoomController(callback, mLayerClient, layerView);
        mLayerClient.setView(layerView, panZoomController);
        layerView.setInputConnectionHandler(new LOKitInputConnectionHandler());
        mLayerClient.notifyReady();
        layerView.setOnKeyListener((view, i, keyEvent) -> {
            if(!isReadOnlyMode() && keyEvent.getKeyCode() != KeyEvent.KEYCODE_BACK){
                setDocumentChanged(true);
            }
            return false;
        });
        // create TextCursorLayer
        mDocumentOverlay = new DocumentOverlay(this, layerView);
        mbISReadOnlyMode = !isExperimentalMode();
        final Uri docUri = getIntent().getData();
        if (docUri != null) {
            if (docUri.getScheme().equals(ContentResolver.SCHEME_CONTENT) || docUri.getScheme().equals(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
                final boolean isReadOnlyDoc  = (getIntent().getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0;
                mbISReadOnlyMode = !isExperimentalMode() || isReadOnlyDoc;
                String displayName = FileUtilities.retrieveDisplayNameForDocumentUri(getContentResolver(), docUri);
                toolbarTop.setTitle(displayName);
            } else if (docUri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
                mbISReadOnlyMode = true;
                toolbarTop.setTitle(docUri.getLastPathSegment());
            }
            // create a temporary local copy to work with
            boolean copyOK = copyFileToTemp(docUri) && mTempFile != null;
            if (!copyOK) {
                return;
            }

            // if input doc is a template, a new doc is created and a proper URI to save to
            // will only be available after a "Save As"
            if (isTemplate(docUri)) {
                toolbarTop.setTitle(R.string.default_document_name);
            } else {
                mDocumentUri = docUri;
            }

            LOKitShell.sendLoadEvent(mTempFile.getPath());
        } else if (getIntent().getStringExtra(HomeActivity.NEW_DOC_TYPE_KEY) != null) {
            // New document type string is not null, meaning we want to open a new document
            String newDocumentType = getIntent().getStringExtra(HomeActivity.NEW_DOC_TYPE_KEY);
            // create a temporary local file, will be copied to the actual URI when saving
            loadNewDocument(newDocumentType);
            toolbarTop.setTitle(getString(R.string.default_document_name));
        } else {
            return;
        }
        mbSkipNextRefresh = true;

        mDrawerLayout = findViewById(R.id.drawer_layout);

        if (mDocumentPartViewListAdapter == null) {
            mDrawerList = findViewById(R.id.left_drawer);

            mDocumentPartViewListAdapter = new DocumentPartViewListAdapter(this, R.layout.document_part_list_layout, mDocumentPartView);
            mDrawerList.setAdapter(mDocumentPartViewListAdapter);
            mDrawerList.setOnItemClickListener(new DocumentPartClickListener());
        }

        mToolbarController.setupToolbars();

        TabHost host = findViewById(R.id.toolbarTabHost);
        host.setup();

        TabHost.TabSpec spec = host.newTabSpec(getString(R.string.tabhost_character));
        spec.setContent(R.id.tab_character);
        spec.setIndicator(getString(R.string.tabhost_character));
        host.addTab(spec);

        spec = host.newTabSpec(getString(R.string.tabhost_paragraph));
        spec.setContent(R.id.tab_paragraph);
        spec.setIndicator(getString(R.string.tabhost_paragraph));
        host.addTab(spec);

        spec = host.newTabSpec(getString(R.string.tabhost_insert));
        spec.setContent(R.id.tab_insert);
        spec.setIndicator(getString(R.string.tabhost_insert));
        host.addTab(spec);

        spec = host.newTabSpec(getString(R.string.tabhost_style));
        spec.setContent(R.id.tab_style);
        spec.setIndicator(getString(R.string.tabhost_style));
        host.addTab(spec);

        LinearLayout bottomToolbarLayout = findViewById(R.id.toolbar_bottom);
        LinearLayout toolbarColorPickerLayout = findViewById(R.id.toolbar_color_picker);
        LinearLayout toolbarBackColorPickerLayout = findViewById(R.id.toolbar_back_color_picker);
        bottomToolbarSheetBehavior = BottomSheetBehavior.from(bottomToolbarLayout);
        toolbarColorPickerBottomSheetBehavior = BottomSheetBehavior.from(toolbarColorPickerLayout);
        toolbarBackColorPickerBottomSheetBehavior = BottomSheetBehavior.from(toolbarBackColorPickerLayout);
        bottomToolbarSheetBehavior.setHideable(true);
        toolbarColorPickerBottomSheetBehavior.setHideable(true);
        toolbarBackColorPickerBottomSheetBehavior.setHideable(true);
    }

    private void updatePreferences() {
        mIsExperimentalMode = TheApplication.getSPManager().getBoolean(CustomConstant.ENABLE_EXPERIMENTAL_PREFS_KEY, false);
        mIsDeveloperMode = mIsExperimentalMode && TheApplication.getSPManager().getBoolean(CustomConstant.ENABLE_DEVELOPER_PREFS_KEY, false);
    }

    // Loads a new Document and saves it to a temporary file
    private void loadNewDocument(String newDocumentType) {
        String tempFileName = "LibreOffice_" + UUID.randomUUID().toString();
        mTempFile = new File(this.getCacheDir(), tempFileName);
        LOKitShell.sendNewDocumentLoadEvent(mTempFile.getPath(), newDocumentType);
    }

    public RectF getCurrentCursorPosition() {
        return mDocumentOverlay.getCurrentCursorPosition();
    }

    private boolean copyFileToTemp(Uri documentUri) {
        String suffix = null;
        String intentType = getIntent().getType();
        if ("text/comma-separated-values".equals(intentType) || "text/csv".equals(intentType)) suffix = ".csv";
        try {
            mTempFile = File.createTempFile("LibreOffice", suffix, this.getCacheDir());
            final FileOutputStream outputStream = new FileOutputStream(mTempFile);
            return copyUriToStream(documentUri, outputStream);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Save the document.
     */
    public void saveDocument() {
        Toast.makeText(this, R.string.message_saving, Toast.LENGTH_SHORT).show();
        LOKitShell.sendEvent(new LOEvent(LOEvent.UNO_COMMAND_NOTIFY, ".uno:Save", true));
    }

    /**
     * Open file chooser and save the document to the URI
     * selected there.
     */
    public void saveDocumentAs() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String mimeType = getODFMimeTypeForDocument();
        intent.setType(mimeType);
        if (Build.VERSION.SDK_INT >= 26) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, mDocumentUri);
        }
        startActivityForResult(intent, REQUEST_CODE_SAVEAS);
    }

    /**
     * Saves the document under the given URI using ODF format
     * and uses that URI from now on for all operations.
     * @param newUri URI to save the document and use from now on.
     */
    private void saveDocumentAs(Uri newUri) {
        mDocumentUri = newUri;
        mTileProvider.saveDocumentAs(mTempFile.getPath(), true);
        saveFileToOriginalSource();
        String displayName = FileUtilities.retrieveDisplayNameForDocumentUri(getContentResolver(), mDocumentUri);
        toolbarTop.setTitle(displayName);
        mbISReadOnlyMode = !isExperimentalMode();
        getToolbarController().setupToolbars();
    }

    public void exportToPDF() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(FileUtilities.MIMETYPE_PDF);
        if (Build.VERSION.SDK_INT >= 26) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, mDocumentUri);
        }
        final String displayName = toolbarTop.getTitle().toString();
        final String suggestedFileName = FileUtilities.stripExtensionFromFileName(displayName) + ".pdf";
        intent.putExtra(Intent.EXTRA_TITLE, suggestedFileName);
        startActivityForResult(intent, REQUEST_CODE_EXPORT_TO_PDF);
    }

    private void exportToPDF(final Uri uri) {
        boolean exportOK = false;
        File tempFile = null;
        try {
            tempFile = File.createTempFile("LibreOffice_", ".pdf");
            mTileProvider.saveDocumentAs(tempFile.getAbsolutePath(),"pdf", false);
            try {
                FileInputStream inputStream = new FileInputStream(tempFile);
                exportOK = copyStreamToUri(inputStream, uri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        final int msgId = exportOK ? R.string.pdf_export_finished : R.string.unable_to_export_pdf;
        LOKitShell.getMainHandler().post(() -> showCustomStatusMessage(getString(msgId)));
    }

    /**
     * Returns the ODF MIME type that can be used for the current document,
     * regardless of whether the document is an ODF Document or not
     * (e.g. returns FileUtilities.MIMETYPE_OPENDOCUMENT_TEXT for a DOCX file).
     * @return MIME type, or empty string, if no appropriate MIME type could be found.
     */
    private String getODFMimeTypeForDocument() {
        if (mTileProvider.isTextDocument())
            return FileUtilities.MIMETYPE_OPENDOCUMENT_TEXT;
        else if (mTileProvider.isSpreadsheet())
            return FileUtilities.MIMETYPE_OPENDOCUMENT_SPREADSHEET;
        else if (mTileProvider.isPresentation())
            return FileUtilities.MIMETYPE_OPENDOCUMENT_PRESENTATION;
        else if (mTileProvider.isDrawing())
            return FileUtilities.MIMETYPE_OPENDOCUMENT_GRAPHICS;
        else {
            return "";
        }
    }

    /**
     * Returns whether the MIME type for the URI is considered one for a document template.
     */
    private boolean isTemplate(final Uri documentUri) {
        final String mimeType = getContentResolver().getType(documentUri);
        return FileUtilities.isTemplateMimeType(mimeType);
    }

    public void saveFileToOriginalSource() {
        if (mTempFile == null || mDocumentUri == null || !mDocumentUri.getScheme().equals(ContentResolver.SCHEME_CONTENT))
            return;

        boolean copyOK = false;
        try {
            final FileInputStream inputStream = new FileInputStream(mTempFile);
            copyOK = copyStreamToUri(inputStream, mDocumentUri);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (copyOK) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.message_saved, Toast.LENGTH_SHORT).show());
            setDocumentChanged(false);
        } else {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.message_saving_failed, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePreferences();
        if (mToolbarController.getEditModeStatus() && isExperimentalMode()) {
            mToolbarController.switchToEditMode();
        } else {
            mToolbarController.switchToViewMode();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mbSkipNextRefresh) {
            LOKitShell.sendEvent(new LOEvent(LOEvent.REFRESH));
        }
        mbSkipNextRefresh = false;
    }

    @Override
    protected void onStop() {
        hideSoftKeyboardDirect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LOKitShell.sendCloseEvent();
        mLayerClient.destroy();
        super.onDestroy();

        if (isFinishing()) { // Not an orientation change
            if (mTempFile != null) {
                // noinspection ResultOfMethodCallIgnored
                mTempFile.delete();
            }
            if (mTempSlideShowFile != null && mTempSlideShowFile.exists()) {
                // noinspection ResultOfMethodCallIgnored
                mTempSlideShowFile.delete();
            }
        }
    }
    @Override
    public void onBackPressed() {
        if (!isDocumentChanged) {
            super.onBackPressed();
            return;
        }

        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which){
                case DialogInterface.BUTTON_POSITIVE:
                    mTileProvider.saveDocument();
                    isDocumentChanged=false;
                    onBackPressed();
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    isDocumentChanged=false;
                    onBackPressed();
                    break;
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.save_alert_dialog_title)
                .setPositiveButton(R.string.save_document, dialogClickListener)
                .setNegativeButton(R.string.action_cancel, dialogClickListener)
                .setNeutralButton(R.string.no_save_document, dialogClickListener)
                .show();

    }

    public List<DocumentPartView> getDocumentPartView() {
        return mDocumentPartView;
    }

    public void disableNavigationDrawer() {
        // Only the original thread that created mDrawerLayout should touch its views.
        LOKitShell.getMainHandler().post(new Runnable() {
            @Override
            public void run() {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mDrawerList);
            }
        });
    }

    public DocumentPartViewListAdapter getDocumentPartViewListAdapter() {
        return mDocumentPartViewListAdapter;
    }

    /**
     * Show software keyboard.
     * Force the request on main thread.
     */
    public void showSoftKeyboard() {

        LOKitShell.getMainHandler().post(() -> {
            if(!isKeyboardOpen) showSoftKeyboardDirect();
            else hideSoftKeyboardDirect();
        });

    }

    private void showSoftKeyboardDirect() {
        LayerView layerView = findViewById(R.id.layer_view);
        if (layerView.requestFocus()) {
            InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.showSoftInput(layerView, InputMethodManager.SHOW_FORCED);
        }
        isKeyboardOpen=true;
        isSearchToolbarOpen=false;
        isFormattingToolbarOpen=false;
        isUNOCommandsToolbarOpen=false;
        hideBottomToolbar();
    }

    public void showSoftKeyboardOrFormattingToolbar() {
        LOKitShell.getMainHandler().post(() -> {
            if (findViewById(R.id.toolbar_bottom).getVisibility() != View.VISIBLE
                    && findViewById(R.id.toolbar_color_picker).getVisibility() != View.VISIBLE) {
                showSoftKeyboardDirect();
            }
        });
    }

    /**
     * Hides software keyboard on UI thread.
     */
    public void hideSoftKeyboard() {
        LOKitShell.getMainHandler().post(() -> hideSoftKeyboardDirect());
    }

    /**
     * Hides software keyboard.
     */
    private void hideSoftKeyboardDirect() {
        if (getCurrentFocus() != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            isKeyboardOpen=false;
        }
    }

    public void showBottomToolbar() {
        LOKitShell.getMainHandler().post(() -> bottomToolbarSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED));
    }

    public void hideBottomToolbar() {
        LOKitShell.getMainHandler().post(() -> {
            bottomToolbarSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            toolbarColorPickerBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            toolbarBackColorPickerBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            findViewById(R.id.search_toolbar).setVisibility(View.GONE);
            findViewById(R.id.UNO_commands_toolbar).setVisibility(View.GONE);
            isFormattingToolbarOpen=false;
            isSearchToolbarOpen=false;
            isUNOCommandsToolbarOpen=false;
        });
    }

    public void showFormattingToolbar() {
        LOKitShell.getMainHandler().post(() -> {
            if (isFormattingToolbarOpen) {
                hideFormattingToolbar();
            } else {
                showBottomToolbar();
                findViewById(R.id.search_toolbar).setVisibility(View.GONE);
                findViewById(R.id.formatting_toolbar).setVisibility(View.VISIBLE);
                findViewById(R.id.search_toolbar).setVisibility(View.GONE);
                findViewById(R.id.UNO_commands_toolbar).setVisibility(View.GONE);
                hideSoftKeyboardDirect();
                isSearchToolbarOpen=false;
                isFormattingToolbarOpen=true;
                isUNOCommandsToolbarOpen=false;
            }

        });
    }

    public void hideFormattingToolbar() {
        LOKitShell.getMainHandler().post(() -> hideBottomToolbar());
    }

    public void showSearchToolbar() {
        LOKitShell.getMainHandler().post(() -> {
            if (isSearchToolbarOpen) {
                hideSearchToolbar();
            } else {
                showBottomToolbar();
                findViewById(R.id.formatting_toolbar).setVisibility(View.GONE);
                toolbarColorPickerBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                toolbarBackColorPickerBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                findViewById(R.id.search_toolbar).setVisibility(View.VISIBLE);
                findViewById(R.id.UNO_commands_toolbar).setVisibility(View.GONE);
                hideSoftKeyboardDirect();
                isFormattingToolbarOpen=false;
                isSearchToolbarOpen=true;
                isUNOCommandsToolbarOpen=false;
            }
        });
    }

    public void hideSearchToolbar() {
        LOKitShell.getMainHandler().post(this::hideBottomToolbar);
    }

    public void showUNOCommandsToolbar() {
        LOKitShell.getMainHandler().post(() -> {
            if(isUNOCommandsToolbarOpen){
                hideUNOCommandsToolbar();
            }else{
                showBottomToolbar();
                findViewById(R.id.formatting_toolbar).setVisibility(View.GONE);
                findViewById(R.id.search_toolbar).setVisibility(View.GONE);
                findViewById(R.id.UNO_commands_toolbar).setVisibility(View.VISIBLE);
                hideSoftKeyboardDirect();
                isFormattingToolbarOpen=false;
                isSearchToolbarOpen=false;
                isUNOCommandsToolbarOpen=true;
            }
        });
    }

    public void hideUNOCommandsToolbar() {
        LOKitShell.getMainHandler().post(this::hideBottomToolbar);
    }

    public void showProgressSpinner() {
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
    }

    public void hideProgressSpinner() {
        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
    }

    public void showAlertDialog(String message) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilder.setTitle(R.string.error);
        alertDialogBuilder.setMessage(message);
        alertDialogBuilder.setNeutralButton(R.string.alert_ok, (dialog, id) -> finish());
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public DocumentOverlay getDocumentOverlay() {
        return mDocumentOverlay;
    }

    public CalcHeadersController getCalcHeadersController() {
        return mCalcHeadersController;
    }

    public ToolbarController getToolbarController() {
        return mToolbarController;
    }

    public FontController getFontController() {
        return mFontController;
    }

    public FormattingController getFormattingController() {
        return mFormattingController;
    }

    public void openDrawer() {
        mDrawerLayout.openDrawer(mDrawerList);
        hideBottomToolbar();
    }

    public void showAbout() {
        AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
        aboutDialogFragment.show(getSupportFragmentManager(), "AboutDialogFragment");
    }

    public void addPart(){
        mTileProvider.addPart();
        mDocumentPartViewListAdapter.notifyDataSetChanged();
        setDocumentChanged(true);
    }

    public void renamePart(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.enter_part_name);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton(R.string.alert_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mTileProvider.renamePart( input.getText().toString());
            }
        });
        builder.setNegativeButton(R.string.alert_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    public void deletePart() {
        mTileProvider.removePart();
    }

    public void showSettings() {
        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
    }

    public boolean isDrawerEnabled() {
        boolean isDrawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
        boolean isDrawerLocked = mDrawerLayout.getDrawerLockMode(mDrawerList) != DrawerLayout.LOCK_MODE_UNLOCKED;
        return !isDrawerOpen && !isDrawerLocked;
    }

    @Override
    public void settingsPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.matches(CustomConstant.ENABLE_EXPERIMENTAL_PREFS_KEY)) {
            mIsExperimentalMode = sharedPreferences.getBoolean(CustomConstant.ENABLE_EXPERIMENTAL_PREFS_KEY, false);
        }
    }

    public void promptForPassword() {
        PasswordDialogFragment passwordDialogFragment = new PasswordDialogFragment();
        passwordDialogFragment.setLOMainActivity(this);
        passwordDialogFragment.show(getSupportFragmentManager(), "PasswordDialogFragment");
    }
    public void setPassword() {
        mTileProvider.setDocumentPassword("file://" + mTempFile.getPath(), mPassword);
    }
    public void setTileProvider(LOKitTileProvider loKitTileProvider) {
        mTileProvider = loKitTileProvider;
    }

    public LOKitTileProvider getTileProvider() {
        return mTileProvider;
    }

    public void savePassword(String pwd) {
        mPassword = pwd;
        synchronized (mTileProvider.getMessageCallback()) {
            mTileProvider.getMessageCallback().notifyAll();
        }
    }

    public void setPasswordProtected(boolean b) {
        mPasswordProtected = b;
    }

    public boolean isPasswordProtected() {
        return mPasswordProtected;
    }

    public void initializeCalcHeaders() {
        mCalcHeadersController = new CalcHeadersController(this, mLayerClient.getView());
        mCalcHeadersController.setupHeaderPopupView();
        LOKitShell.getMainHandler().post(() -> {
            findViewById(R.id.calc_header_top_left).setVisibility(View.VISIBLE);
            findViewById(R.id.calc_header_row).setVisibility(View.VISIBLE);
            findViewById(R.id.calc_header_column).setVisibility(View.VISIBLE);
            findViewById(R.id.calc_address).setVisibility(View.VISIBLE);
            findViewById(R.id.calc_formula).setVisibility(View.VISIBLE);
        });
    }

    public static boolean isReadOnlyMode() {
        return mbISReadOnlyMode;
    }

    public boolean hasLocationForSave() {
        return mDocumentUri != null;
    }

    public static void setDocumentChanged (boolean changed) {
        isDocumentChanged = changed;
    }

    private class DocumentPartClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            DocumentPartView partView = mDocumentPartViewListAdapter.getItem(position);
            LOKitShell.sendChangePartEvent(partView.partIndex);
            mDrawerLayout.closeDrawer(mDrawerList);
        }
    }



    /**
     * Copies everything from the given input stream to the given output stream
     * and closes both streams in the end.
     * @return Whether copy operation was successful.
     */
    private boolean copyStream(InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] buffer = new byte[4096];
            int readBytes = inputStream.read(buffer);
            while (readBytes != -1) {
                outputStream.write(buffer, 0, readBytes);
                readBytes = inputStream.read(buffer);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Copies everything from the given Uri to the given OutputStream
     * and closes the OutputStream in the end.
     * The copy operation runs in a separate thread, but the method only returns
     * after the thread has finished its execution.
     * This can be used to copy in a blocking way when network access is involved,
     * which is not allowed from the main thread, but that may happen when an underlying
     * DocumentsProvider (like the NextCloud one) does network access.
     */
    private boolean copyUriToStream(final Uri inputUri, final OutputStream outputStream) {
        class CopyThread extends Thread {
            /** Whether copy operation was successful. */
            private boolean result = false;

            @Override
            public void run() {
                final ContentResolver contentResolver = getContentResolver();
                try {
                    InputStream inputStream = contentResolver.openInputStream(inputUri);
                    result = copyStream(inputStream, outputStream);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        CopyThread copyThread = new CopyThread();
        copyThread.start();
        try {
            copyThread.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        return copyThread.result;
    }

    /**
     * Copies everything from the given InputStream to the given URI and closes the
     * InputStream in the end.
     * @see MainActivity#copyUriToStream(Uri, OutputStream)
     *      which does the same thing the other way around.
     */
    private boolean copyStreamToUri(final InputStream inputStream, final Uri outputUri) {
        class CopyThread extends Thread {
            private boolean result = false;
            @Override
            public void run() {
                final ContentResolver contentResolver = getContentResolver();
                try {
                    OutputStream outputStream = contentResolver.openOutputStream(outputUri);
                    result = copyStream(inputStream, outputStream);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        CopyThread copyThread = new CopyThread();
        copyThread.start();
        try {
            copyThread.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
        return copyThread.result;
    }

    public void showCustomStatusMessage(String message){
        Snackbar.make(mDrawerLayout, message, Snackbar.LENGTH_LONG).show();
    }

    public void preparePresentation() {
        if (getExternalCacheDir() != null) {
            String tempPath = getExternalCacheDir().getPath() + "/" + mTempFile.getName() + ".svg";
            mTempSlideShowFile = new File(tempPath);
            if (mTempSlideShowFile.exists() && !isDocumentChanged) {
                startPresentation("file://" + tempPath);
            } else {
                LOKitShell.sendSaveCopyAsEvent(tempPath, "svg");
            }
        }
    }

    public void startPresentation(String tempPath) {
        Intent intent = new Intent(this, PresentationActivity.class);
        intent.setData(Uri.parse(tempPath));
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAVEAS && resultCode == RESULT_OK) {
            final Uri fileUri = data.getData();
            saveDocumentAs(fileUri);
        } else if (requestCode == REQUEST_CODE_EXPORT_TO_PDF && resultCode == RESULT_OK) {
            final Uri fileUri = data.getData();
            exportToPDF(fileUri);
        } else {
            mFormattingController.handleActivityResult(requestCode, resultCode, data);
            hideBottomToolbar();
        }
    }
}


