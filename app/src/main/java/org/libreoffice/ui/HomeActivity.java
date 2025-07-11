package org.libreoffice.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.libreoffice.R;
import org.libreoffice.application.CustomConstant;
import org.libreoffice.application.TheApplication;
import org.libreoffice.data.RecentFile;
import org.libreoffice.utils.FileUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements View.OnClickListener{
    public enum DocumentType {WRITER, CALC, IMPRESS, DRAW, PDF, INVALID}
    public static final String EXPLORER_PREFS_KEY = "EXPLORER_PREFS";
    private static final String RECENT_DOCUMENTS_KEY = "RECENT_DOCUMENT_URIS";
    private static final String RECENT_DOCUMENTS_DELIMITER = " ";
    public static final String NEW_DOC_TYPE_KEY = "NEW_DOC_TYPE_KEY";
    public static final String NEW_WRITER_STRING_KEY = "private:factory/swriter";
    public static final String NEW_IMPRESS_STRING_KEY = "private:factory/simpress";
    public static final String NEW_CALC_STRING_KEY = "private:factory/scalc";
    public static final String NEW_DRAW_STRING_KEY = "private:factory/sdraw";

    // keep this in sync with 'AndroidManifext.xml'
    private static final String[] SUPPORTED_MIME_TYPES = {
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.graphics",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.text-flat-xml",
            "application/vnd.oasis.opendocument.graphics-flat-xml",
            "application/vnd.oasis.opendocument.presentation-flat-xml",
            "application/vnd.oasis.opendocument.spreadsheet-flat-xml",
            "application/vnd.oasis.opendocument.text-template",
            "application/vnd.oasis.opendocument.spreadsheet-template",
            "application/vnd.oasis.opendocument.graphics-template",
            "application/vnd.oasis.opendocument.presentation-template",
            "application/rtf",
            "text/rtf",
            "application/msword",
            "application/vnd.ms-powerpoint",
            "application/vnd.ms-excel",
            "application/vnd.visio",
            "application/vnd.visio.xml",
            "application/x-mspublisher",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "application/vnd.openxmlformats-officedocument.presentationml.template",
            "text/csv",
            "text/comma-separated-values",
            "application/vnd.ms-works",
            "application/vnd.apple.keynote",
            "application/x-abiword",
            "application/x-pagemaker",
            "image/x-emf",
            "image/x-svm",
            "image/x-wmf",
            "image/svg+xml",
            "image/gif",
            "image/jpg",
            "image/jpeg",
            "image/png",
            "application/vnd.visio2013",
            "application/octet-stream",
            "application/vnd.ms-visio.drawing",
            "text/plain",
            "application/pdf",
    };

    private static final int REQUEST_CODE_OPEN_FILECHOOSER = 1;
    private static final int PERMISSION_WRITE_EXTERNAL_STORAGE = 0;
    private Animation fabOpenAnimation;
    private Animation fabCloseAnimation;
    private boolean isFabMenuOpen = false;
    private FloatingActionButton editFAB;
    private FloatingActionButton writerFAB;
    private FloatingActionButton drawFAB;
    private FloatingActionButton impressFAB;
    private FloatingActionButton calcFAB;
    private LinearLayout drawLayout;
    private LinearLayout writerLayout;
    private LinearLayout impressLayout;
    private LinearLayout calcLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fabOpenAnimation = AnimationUtils.loadAnimation(this, R.anim.fab_open);
        fabCloseAnimation = AnimationUtils.loadAnimation(this, R.anim.fab_close);
    }

    @Override
    protected void onStart() {
        super.onStart();
        createUI();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
    }

    public void createUI() {
        editFAB = findViewById(R.id.editFAB);
        editFAB.setOnClickListener(this);
        final boolean bEditingEnabled = TheApplication.getSPManager().getBoolean(CustomConstant.ENABLE_EXPERIMENTAL_PREFS_KEY, false);
        editFAB.setVisibility(bEditingEnabled ? View.VISIBLE : View.INVISIBLE);
        impressFAB = findViewById(R.id.newImpressFAB);
        impressFAB.setOnClickListener(this);
        writerFAB = findViewById(R.id.newWriterFAB);
        writerFAB.setOnClickListener(this);
        calcFAB = findViewById(R.id.newCalcFAB);
        calcFAB.setOnClickListener(this);
        drawFAB = findViewById(R.id.newDrawFAB);
        drawFAB.setOnClickListener(this);
        writerLayout = findViewById(R.id.writerLayout);
        impressLayout = findViewById(R.id.impressLayout);
        calcLayout = findViewById(R.id.calcLayout);
        drawLayout = findViewById(R.id.drawLayout);
        TextView openFileView = findViewById(R.id.open_file_button);
        openFileView.setOnClickListener(this);
        RecyclerView recentRecyclerView = findViewById(R.id.list_recent);
        SharedPreferences prefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        String recentPref = prefs.getString(RECENT_DOCUMENTS_KEY, "");
        String[] recentFileStrings = recentPref.split(RECENT_DOCUMENTS_DELIMITER);
        final List<RecentFile> recentFiles = new ArrayList<>();
        for (String recentFileString : recentFileStrings) {
            Uri uri = Uri.parse(recentFileString);
            String filename = FileUtilities.retrieveDisplayNameForDocumentUri(getContentResolver(), uri);
            if (!filename.isEmpty()) {
                recentFiles.add(new RecentFile(uri, filename));
            }
        }
        recentRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        RecentFilesAdapter adapter = new RecentFilesAdapter(this, recentFiles, (v, position) -> {
            openDocument(recentFiles.get(position).getUri());
        });
        recentRecyclerView.setAdapter(adapter);
    }

    private void expandFabMenu() {
        ViewCompat.animate(editFAB).rotation(45.0F).withLayer().setDuration(300).setInterpolator(new OvershootInterpolator(10.0F)).start();
        drawLayout.startAnimation(fabOpenAnimation);
        impressLayout.startAnimation(fabOpenAnimation);
        writerLayout.startAnimation(fabOpenAnimation);
        calcLayout.startAnimation(fabOpenAnimation);
        writerFAB.setClickable(true);
        impressFAB.setClickable(true);
        drawFAB.setClickable(true);
        calcFAB.setClickable(true);
        isFabMenuOpen = true;
    }

    private void collapseFabMenu() {
        ViewCompat.animate(editFAB).rotation(0.0F).withLayer().setDuration(300).setInterpolator(new OvershootInterpolator(10.0F)).start();
        writerLayout.startAnimation(fabCloseAnimation);
        impressLayout.startAnimation(fabCloseAnimation);
        drawLayout.startAnimation(fabCloseAnimation);
        calcLayout.startAnimation(fabCloseAnimation);
        writerFAB.setClickable(false);
        impressFAB.setClickable(false);
        drawFAB.setClickable(false);
        calcFAB.setClickable(false);
        isFabMenuOpen = false;
    }

    @Override
    public void onBackPressed() {
        if (isFabMenuOpen) {
            collapseFabMenu();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_FILECHOOSER && resultCode == RESULT_OK) {
            final Uri fileUri = data.getData();
            openDocument(fileUri);
        }
    }

    private void showSystemFilePickerAndOpenFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, SUPPORTED_MIME_TYPES);
        try {
            startActivityForResult(intent, REQUEST_CODE_OPEN_FILECHOOSER);
        } catch (ActivityNotFoundException ignored) {}
    }

    public void openDocument(final Uri documentUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, documentUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        addDocumentToRecents(documentUri);
        String packageName = getApplicationContext().getPackageName();
        ComponentName componentName = new ComponentName(packageName, MainActivity.class.getName());
        intent.setComponent(componentName);
        startActivity(intent);
    }

    private void loadNewDocument(DocumentType docType) {
        final String newDocumentType;
        if (docType == DocumentType.WRITER) {
            newDocumentType = NEW_WRITER_STRING_KEY;
        } else if (docType == DocumentType.CALC) {
            newDocumentType = NEW_CALC_STRING_KEY;
        } else if (docType == DocumentType.IMPRESS) {
            newDocumentType = NEW_IMPRESS_STRING_KEY;
        } else if (docType == DocumentType.DRAW) {
            newDocumentType = NEW_DRAW_STRING_KEY;
        } else {
            return;
        }
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        intent.putExtra(NEW_DOC_TYPE_KEY, newDocumentType);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.view_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_about) {
            AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
            aboutDialogFragment.show(getSupportFragmentManager(), "AboutDialogFragment");
            return true;
        }
        if (itemId == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addDocumentToRecents(Uri fileUri) {
        SharedPreferences prefs = getSharedPreferences(EXPLORER_PREFS_KEY, MODE_PRIVATE);
        getContentResolver().takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        String newRecent = fileUri.toString();
        List<String> recentsList = new ArrayList<>(Arrays.asList(prefs.getString(RECENT_DOCUMENTS_KEY, "").split(RECENT_DOCUMENTS_DELIMITER)));
        recentsList.remove(newRecent);
        recentsList.add(0, newRecent);
        String value = TextUtils.join(RECENT_DOCUMENTS_DELIMITER, recentsList);
        prefs.edit().putString(RECENT_DOCUMENTS_KEY, value).apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            shortcutManager.removeAllDynamicShortcuts();
            ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
            for (String recentDoc : recentsList) {
                Uri docUri = Uri.parse(recentDoc);
                String filename = FileUtilities.retrieveDisplayNameForDocumentUri(getContentResolver(), docUri);
                if (filename.isEmpty()) continue;
                int drawable = switch (FileUtilities.getType(filename)) {
                    case FileUtilities.CALC -> R.drawable.ic_calc;
                    case FileUtilities.DRAWING -> R.drawable.ic_draw;
                    case FileUtilities.IMPRESS -> R.drawable.ic_impress;
                    case FileUtilities.PDF -> R.drawable.ic_pdf;
                    default -> R.drawable.ic_writer;
                };
                Intent intent = new Intent(Intent.ACTION_VIEW, docUri);
                String packageName = this.getApplicationContext().getPackageName();
                ComponentName componentName = new ComponentName(packageName, MainActivity.class.getName());
                intent.setComponent(componentName);
                ShortcutInfo shortcut = new ShortcutInfo.Builder(this, filename)
                        .setShortLabel(filename)
                        .setLongLabel(filename)
                        .setIcon(Icon.createWithResource(this, drawable))
                        .setIntent(intent)
                        .build();
                shortcuts.add(shortcut);
            }
            shortcutManager.setDynamicShortcuts(shortcuts);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.editFAB) {
            if (isFabMenuOpen) collapseFabMenu();
            else expandFabMenu();
        } else if (id == R.id.open_file_button) {
            showSystemFilePickerAndOpenFile();
        } else if (id == R.id.newWriterFAB) {
            loadNewDocument(DocumentType.WRITER);
        } else if (id == R.id.newImpressFAB) {
            loadNewDocument(DocumentType.IMPRESS);
        } else if (id == R.id.newCalcFAB) {
            loadNewDocument(DocumentType.CALC);
        } else if (id == R.id.newDrawFAB) {
            loadNewDocument(DocumentType.DRAW);
        }
    }
}
