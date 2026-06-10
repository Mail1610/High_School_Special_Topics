package com.example.myapplication

import android.content.Context

/** 儲存使用者設定的 ESP32 IP 位址（手機與藥盒需在同一個 WiFi）。 */
object Prefs {
    private const val FILE = "pillbox_prefs"
    private const val KEY_HOST = "esp32_host"

    fun getHost(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_HOST, "") ?: ""

    fun setHost(context: Context, host: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOST, host.trim())
            .apply()
    }
}
