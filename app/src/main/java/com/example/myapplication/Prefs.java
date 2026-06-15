package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;

/** 儲存使用者設定的 ESP32 IP 位址（手機與藥盒需在同一個 WiFi）。 */
public class Prefs {

    private static final String FILE = "pillbox_prefs";
    private static final String KEY_HOST = "esp32_host";

    private Prefs() {}

    /** 從 SharedPreferences 讀取使用者上次儲存的 ESP32 IP 位址，未設定則回傳空字串 */
    public static String getHost(Context context) {
        String host = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_HOST, "");
        return host != null ? host : "";
    }

    /** 將使用者輸入的 IP 位址（自動去除前後空白）寫入 SharedPreferences 永久保存 */
    public static void setHost(Context context, String host) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_HOST, host.trim())
                .apply();
    }

    private static final String KEY_SLOT_PREFIX = "slot_time_";

    /** 讀取指定時段的儲存時間，未設定回傳空字串 */
    public static String getSlotTime(Context context, int index) {
        String val = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getString(KEY_SLOT_PREFIX + index, "");
        return val != null ? val : "";
    }

    /** 將四個時段的服藥時間批次寫入 SharedPreferences */
    public static void setSlotTimes(Context context, List<String> times) {
        SharedPreferences.Editor editor =
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit();
        for (int i = 0; i < times.size(); i++) {
            editor.putString(KEY_SLOT_PREFIX + i, times.get(i));
        }
        editor.apply();
    }
}
