package org.libreoffice.application;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.multidex.MultiDexApplication;

import org.libreoffice.BuildConfig;
import org.libreoffice.manager.LocaleHelper;
import org.libreoffice.manager.AssetsManager;

public class LibreOfficeApplication extends MultiDexApplication {

    private static final String ASSETS_EXTRACTED_PREFS_KEY = "ASSETS_EXTRACTED";
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

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (sPrefs.getInt(ASSETS_EXTRACTED_PREFS_KEY, 0) != BuildConfig.VERSION_CODE) {
            if(AssetsManager.copyFromAssets(getAssets(), "unpack", getApplicationInfo().dataDir)) {
                sPrefs.edit().putInt(ASSETS_EXTRACTED_PREFS_KEY, BuildConfig.VERSION_CODE).apply();
            }
        }
    }
}
