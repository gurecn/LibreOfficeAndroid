package org.libreoffice.utils;


import org.libreoffice.TileProvider;
import org.libreoffice.kit.LibreOfficeKit;
import org.libreoffice.manager.InvalidationHandler;
import org.libreoffice.manager.LOKitTileProvider;
import org.libreoffice.ui.LibreOfficeMainActivity;

/**
 * Create a desired instance of TileProvider.
 */
public class TileProviderFactory {

    private TileProviderFactory() {
    }

    public static void initialize() {
        LibreOfficeKit.initializeLibrary();
    }

    public static TileProvider create(LibreOfficeMainActivity context, InvalidationHandler invalidationHandler, String filename) {
         return new LOKitTileProvider(context, invalidationHandler, filename);
    }
}


