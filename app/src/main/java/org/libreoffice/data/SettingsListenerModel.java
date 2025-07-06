package org.libreoffice.data;

import android.content.SharedPreferences;

public class SettingsListenerModel {

    public interface OnSettingsPreferenceChangedListener {
        void settingsPreferenceChanged(SharedPreferences sharedPreferences, String key);
    }

    private static SettingsListenerModel mInstance;
    private OnSettingsPreferenceChangedListener mListener;
    private SharedPreferences sharedPreferences;
    private String key;

    private SettingsListenerModel() {}

    public static SettingsListenerModel getInstance() {
        if(mInstance == null) {
            mInstance = new SettingsListenerModel();
        }
        return mInstance;
    }

    public void setListener(OnSettingsPreferenceChangedListener listener) {
        mListener = listener;
    }

    public void changePreferenceState(SharedPreferences sharedPreferences, String key) {
        if(mListener != null) {
            this.sharedPreferences = sharedPreferences;
            this.key = key;
            notifyPreferenceChange(sharedPreferences, key);
        }
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public String getKey(){
        return key;
    }

    private void notifyPreferenceChange(SharedPreferences preferences, String key) {
        mListener.settingsPreferenceChanged(preferences, key);
    }
}
