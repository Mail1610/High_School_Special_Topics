package com.example.myapplication;

import android.util.Log;

public final class LogUtils {

    private LogUtils() {}

    public static void info(String tag, String msg)               { Log.i(tag, msg); }
    public static void error(String tag, String msg)              { Log.e(tag, msg); }
    public static void error(String tag, String msg, Throwable t) { Log.e(tag, msg, t); }
    public static void debug(String tag, String msg)              { Log.d(tag, msg); }
    public static void warn(String tag, String msg)               { Log.w(tag, msg); }
}
