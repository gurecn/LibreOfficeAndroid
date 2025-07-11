package org.libreoffice.utils;

import java.util.Map;
import java.util.HashMap;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

public class FileUtilities {

    private static final String LOGTAG = FileUtilities.class.getSimpleName();

    // These have to be in sync with the file_view_modes resource.
    static final int DOC = 0;
    public static final int CALC = 1;
    public static final int IMPRESS = 2;
    public static final int DRAWING = 3;
    public static final int PDF = 4;

    public static final int UNKNOWN = 10;

    public static final String MIMETYPE_OPENDOCUMENT_TEXT = "application/vnd.oasis.opendocument.text";
    public static final String MIMETYPE_OPENDOCUMENT_SPREADSHEET = "application/vnd.oasis.opendocument.spreadsheet";
    public static final String MIMETYPE_OPENDOCUMENT_PRESENTATION = "application/vnd.oasis.opendocument.presentation";
    public static final String MIMETYPE_OPENDOCUMENT_GRAPHICS = "application/vnd.oasis.opendocument.graphics";
    public static final String MIMETYPE_PDF = "application/pdf";

    private static final Map<String, Integer> mExtnMap = new HashMap<String, Integer>();
    static {
        // Please keep this in sync with AndroidManifest.xml
        // and 'SUPPORTED_MIME_TYPES' in LibreOfficeUIActivity.java

        mExtnMap.put(".pdf", PDF);
        // ODF
        mExtnMap.put(".odt", DOC);
        mExtnMap.put(".odg", DRAWING);
        mExtnMap.put(".odp",  IMPRESS);
        mExtnMap.put(".ods",  CALC);
        mExtnMap.put(".fodt", DOC);
        mExtnMap.put(".fodg", DRAWING);
        mExtnMap.put(".fodp",  IMPRESS);
        mExtnMap.put(".fods",  CALC);

        // ODF templates
        mExtnMap.put(".ott", DOC);
        mExtnMap.put(".otg", DRAWING);
        mExtnMap.put(".otp",  IMPRESS);
        mExtnMap.put(".ots",  CALC);

        // MS
        mExtnMap.put(".rtf",  DOC);
        mExtnMap.put(".doc",  DOC);
        mExtnMap.put(".vsd", DRAWING);
        mExtnMap.put(".vsdx", DRAWING);
        mExtnMap.put(".pub", DRAWING);
        mExtnMap.put(".ppt",  IMPRESS);
        mExtnMap.put(".pps",  IMPRESS);
        mExtnMap.put(".xls",  CALC);

        // MS templates
        mExtnMap.put(".dot",  DOC);
        mExtnMap.put(".pot",  IMPRESS);
        mExtnMap.put(".xlt",  CALC);

        // OOXML
        mExtnMap.put(".docx", DOC);
        mExtnMap.put(".pptx", IMPRESS);
        mExtnMap.put(".ppsx", IMPRESS);
        mExtnMap.put(".xlsx", CALC);

        // OOXML templates
        mExtnMap.put(".dotx", DOC);
        mExtnMap.put(".potx", IMPRESS);
        mExtnMap.put(".xltx", CALC);

        // Other
        mExtnMap.put(".csv",  CALC);
        mExtnMap.put(".wps",  DOC);
        mExtnMap.put(".key",  IMPRESS);
        mExtnMap.put(".abw",  DOC);
        mExtnMap.put(".pmd",  DRAWING);
        mExtnMap.put(".emf",  DRAWING);
        mExtnMap.put(".svm",  DRAWING);
        mExtnMap.put(".wmf",  DRAWING);
        mExtnMap.put(".svg",  DRAWING);
    }

    public static String getExtension(String filename) {
        if (filename == null)
            return "";
        int nExt = filename.lastIndexOf('.');
        if (nExt < 0)
            return "";
        return filename.substring(nExt);
    }

    private static int lookupExtension(String filename) {
        String extn = getExtension(filename);
        if (!mExtnMap.containsKey(extn))
            return UNKNOWN;
        return mExtnMap.get(extn);
    }

    public static int getType(String filename) {
        int type = lookupExtension (filename);
        return type;
    }

    /**
     * Returns whether the passed MIME type is one for a document template.
     */
    public static boolean isTemplateMimeType(final String mimeType) {
        // this works for ODF and OOXML template MIME types
        return mimeType != null && mimeType.endsWith("template");
    }

    public static String stripExtensionFromFileName(final String fileName)
    {
        return fileName.split("\\.[A-Za-z0-9]*$")[0];
    }

    /**
     * Tries to retrieve the display (which should be the document name)
     * for the given URI using the given resolver.
     */
    public static String retrieveDisplayNameForDocumentUri(ContentResolver resolver, Uri docUri) {
        String displayName = "";
        // try to retrieve original file name
        Cursor cursor = null;
        try {
            String[] columns = {OpenableColumns.DISPLAY_NAME};
            cursor = resolver.query(docUri, columns, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
            }
        } catch (SecurityException e) {
            // thrown e.g. when Uri has become invalid, e.g. corresponding file has been deleted
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return displayName;
    }
}


