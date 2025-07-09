package org.libreoffice.application;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.multidex.MultiDexApplication;
import org.libreoffice.BuildConfig;
import org.libreoffice.manager.AssetsManager;

public class TheApplication extends MultiDexApplication {
    private static TheApplication sInstance;
    private static final String ASSETS_EXTRACTED_PREFS_KEY = "ASSETS_EXTRACTED";
    private static Handler mainHandler;
    private static SharedPreferences sPrefs;

    public TheApplication() {
        mainHandler = new Handler();
    }

    public static Handler getMainHandler() {
        return mainHandler;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        if (sPrefs.getInt(ASSETS_EXTRACTED_PREFS_KEY, 0) != BuildConfig.VERSION_CODE) {
            if(AssetsManager.copyFromAssets(getAssets(), "unpack", getApplicationInfo().dataDir)) {
                sPrefs.edit().putInt(ASSETS_EXTRACTED_PREFS_KEY, BuildConfig.VERSION_CODE).apply();
            }
        }
    }

    public static TheApplication getApplication() {
        return sInstance;
    }

    public static Context getContext() {
        return sInstance.getApplicationContext();
    }

    public static SharedPreferences getSPManager() {
        return sPrefs;
    }
}
