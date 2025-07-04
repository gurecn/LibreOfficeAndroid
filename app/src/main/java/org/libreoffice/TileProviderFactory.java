package org.libreoffice;


import org.libreoffice.kit.LibreOfficeKit;

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

/* vim:set shiftwidth=4 softtabstop=4 expandtab: */
