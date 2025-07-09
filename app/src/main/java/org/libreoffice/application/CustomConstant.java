package org.libreoffice.application;

/**
 * 用户自定义常量类
 */

public class CustomConstant {
    private CustomConstant() {}
    public final static String PACKAGE_NAME = TheApplication.getContext().getPackageName();//通过应用传入的context
    public static final String ENABLE_EXPERIMENTAL_PREFS_KEY = "ENABLE_EXPERIMENTAL";
    public static final String ENABLE_DEVELOPER_PREFS_KEY = "ENABLE_DEVELOPER";
}
