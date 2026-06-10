package com.example.myapplication

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 與 ESP32 智慧藥盒溝通的 HTTP 客戶端。
 *
 * 假設的 API 合約（對應指南第五章 handleApiStatus / handleApiHistory / handleApiSettings，
 * ESP32 韌體需依此格式回傳 JSON，或日後依實際韌體調整本檔）：
 *
 * GET  /api/status
 *   { "slots": [ { "label": "早上", "time": "08:00", "state": "WAITING|ALERTING|TAKEN|MISSED" }, ... (4 格) ],
 *     "complianceRate": 86 }
 *
 * GET  /api/history
 *   { "days": [ { "date": "06/01", "bits": 15 }, ... (7 天，bits 的 bit0~3 對應 4 格，1=已服) ],
 *     "complianceRate": 86 }
 *
 * POST /api/settings   (form-encoded: slot0=08:00&slot1=12:00&slot2=18:00&slot3=22:00)
 *   { "ok": true }
 */
data class SlotStatus(val label: String, val time: String, val state: String)
data class StatusResult(val slots: List<SlotStatus>, val complianceRate: Int)
data class HistoryDay(val date: String, val bits: Int)
data class HistoryResult(val days: List<HistoryDay>, val complianceRate: Int)

object PillBoxApi {
    private const val TIMEOUT_MS = 5000
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun baseUrl(host: String): String {
        val trimmed = host.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "http://$trimmed"
    }

    private fun runAsync(block: () -> Unit) {
        Thread(block).start()
    }

    private fun postToMain(action: () -> Unit) {
        mainHandler.post(action)
    }

    private fun get(host: String, path: String): String {
        val url = URL(baseUrl(host) + path)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                return reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun postForm(host: String, path: String, formBody: String): String {
        val url = URL(baseUrl(host) + path)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            OutputStreamWriter(conn.outputStream).use { it.write(formBody) }
            BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                return reader.readText()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun BufferedReader.readText(): String = readLines().joinToString("\n")

    fun fetchStatus(host: String, onResult: (StatusResult?) -> Unit) {
        runAsync {
            val result = try {
                val json = JSONObject(get(host, "/api/status"))
                val slotsJson = json.getJSONArray("slots")
                val slots = (0 until slotsJson.length()).map { i ->
                    val s = slotsJson.getJSONObject(i)
                    SlotStatus(s.getString("label"), s.getString("time"), s.getString("state"))
                }
                StatusResult(slots, json.optInt("complianceRate", 0))
            } catch (e: Exception) {
                null
            }
            postToMain { onResult(result) }
        }
    }

    fun fetchHistory(host: String, onResult: (HistoryResult?) -> Unit) {
        runAsync {
            val result = try {
                val json = JSONObject(get(host, "/api/history"))
                val daysJson = json.getJSONArray("days")
                val days = (0 until daysJson.length()).map { i ->
                    val d = daysJson.getJSONObject(i)
                    HistoryDay(d.getString("date"), d.getInt("bits"))
                }
                HistoryResult(days, json.optInt("complianceRate", 0))
            } catch (e: Exception) {
                null
            }
            postToMain { onResult(result) }
        }
    }

    fun saveSettings(host: String, slotTimes: List<String>, onResult: (Boolean) -> Unit) {
        runAsync {
            val ok = try {
                val body = slotTimes.mapIndexed { i, time ->
                    "slot$i=" + URLEncoder.encode(time, "UTF-8")
                }.joinToString("&")
                val json = JSONObject(postForm(host, "/api/settings", body))
                json.optBoolean("ok", false)
            } catch (e: Exception) {
                false
            }
            postToMain { onResult(ok) }
        }
    }
}
