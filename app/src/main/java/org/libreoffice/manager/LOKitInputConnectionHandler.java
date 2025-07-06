package org.libreoffice.manager;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.mozilla.gecko.gfx.InputConnectionHandler;

/**
 * Implementation of InputConnectionHandler. When a key event happens it is
 * directed to this class which is then directed further to LOKitThread.
 */
public class LOKitInputConnectionHandler implements InputConnectionHandler {
    private static final String LOGTAG = LOKitInputConnectionHandler.class.getSimpleName();

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return null;
    }

    /**
     * When key pre-Ime happens.
     */
    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        return false;
    }

    /**
     * When key down event happens.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LOKitShell.sendKeyEvent(event);
        return false;
    }

    /**
     * When key long press event happens.
     */
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return false;
    }

    /**
     * When key multiple event happens. Key multiple event is triggered when
     * non-ascii characters are entered on soft keyboard.
     */
    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        LOKitShell.sendKeyEvent(event);
        return false;
    }

    /**
     * When key up event happens.
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        LOKitShell.sendKeyEvent(event);
        return false;
    }
}


