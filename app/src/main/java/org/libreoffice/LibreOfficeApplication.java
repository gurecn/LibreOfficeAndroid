package org.libreoffice;

import android.content.Context;
import android.os.Handler;
import androidx.multidex.MultiDexApplication;

public class LibreOfficeApplication extends MultiDexApplication {

    private static Handler mainHandler;

    public LibreOfficeApplication() {
        mainHandler = new Handler();
    }

    public static Handler getMainHandler() {
        return mainHandler;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }
}
