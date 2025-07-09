package org.libreoffice.kit;

import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;

import org.libreoffice.application.TheApplication;

import java.nio.ByteBuffer;

public final class LibreOfficeKit {
    private static AssetManager mgr;

    // private constructor because instantiating would be meaningless
    private LibreOfficeKit() {
    }

    // Trigger library initialization - as this is done automatically by executing the "static" block, this method remains empty. However we need this to manually (at the right time) can force library initialization.
    public static void initializeLibrary() {
    }

    // Trigger initialization on the JNI - LOKit side.
    private static native boolean initializeNative(String dataDir, String cacheDir, String apkFile, AssetManager mgr);

    public static native ByteBuffer getLibreOfficeKitHandle();

    // Wrapper for putenv()
    public static native void putenv(String string);

    // A method that starts a thread to redirect stdout and stderr writes to
    // the Android logging mechanism, or stops the redirection.
    public static native void redirectStdio(boolean state);

    static boolean initializeDone = false;

    // This init() method should be called from the upper Java level of
    // LO-based apps.
    public static synchronized void init() {
        if (initializeDone) {
            return;
        }
        mgr = TheApplication.getContext().getResources().getAssets();
        ApplicationInfo applicationInfo = TheApplication.getContext().getApplicationInfo();
        String dataDir = applicationInfo.dataDir;
        redirectStdio(true);
        String cacheDir = TheApplication.getApplication().getCacheDir().getAbsolutePath();
        String apkFile = TheApplication.getApplication().getPackageResourcePath();
        if (!initializeNative(dataDir, cacheDir, apkFile, mgr)) {
            return;
        }
        initializeDone = true;
    }

    // Now with static loading we always have all native code in one native
    // library which we always call liblo-native-code.so, regardless of the
    // app. The library has already been unpacked into /data/data/<app
    // name>/lib at installation time by the package manager.
    static {
        NativeLibLoader.load();
    }
}

class NativeLibLoader {
        private static boolean done = false;

        protected static synchronized void load() {
            if (done)
                return;
            System.loadLibrary("nspr4");
            System.loadLibrary("plds4");
            System.loadLibrary("plc4");
            System.loadLibrary("nssutil3");
            System.loadLibrary("freebl3");
            System.loadLibrary("sqlite3");
            System.loadLibrary("softokn3");
            System.loadLibrary("nss3");
            System.loadLibrary("nssckbi");
            System.loadLibrary("nssdbm3");
            System.loadLibrary("smime3");
            System.loadLibrary("ssl3");

            System.loadLibrary("c++_shared");
            System.loadLibrary("lo-native-code");
            done = true;
        }
}


