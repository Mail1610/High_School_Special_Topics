package com.example.myapplication;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 與 ESP32 智慧藥盒溝通的 HTTP 客戶端（Singleton 模式）。
 *
 * API 合約：
 *   GET  /api/status  → StatusResult
 *   GET  /api/history → HistoryResult
 *   POST /api/settings (form-encoded: slot0=HH:mm&slot1=...) → ok: true/false
 */
public class PillBoxApi {

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static final PillBoxApi INSTANCE = new PillBoxApi();

    /** 私有建構子：初始化主執行緒 Handler，確保 API 回呼都在 UI 執行緒執行 */
    private PillBoxApi() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /** 回傳 PillBoxApi 的唯一實例（執行緒安全的靜態初始化） */
    public static PillBoxApi getInstance() {
        return INSTANCE;
    }

    // ── 資料模型（靜態內部類別）──────────────────────────────────────────────

    /** 單格藥盒的即時狀態 */
    public static class SlotStatus {
        private final String label;
        private final String time;
        private final String state;

        public SlotStatus(String label, String time, String state) {
            this.label = label;
            this.time  = time;
            this.state = state;
        }

        public String getLabel() { return label; }
        public String getTime()  { return time;  }
        public String getState() { return state; }
    }

    /** /api/status 的完整回傳結果 */
    public static class StatusResult {
        private final List<SlotStatus> slots;
        private final int complianceRate;

        public StatusResult(List<SlotStatus> slots, int complianceRate) {
            this.slots          = slots;
            this.complianceRate = complianceRate;
        }

        public List<SlotStatus> getSlots()         { return slots;          }
        public int              getComplianceRate() { return complianceRate; }
    }

    /** 某一天的服藥紀錄（bits 的 bit0~3 對應 4 格，1=已服） */
    public static class HistoryDay {
        private final String date;
        private final int    bits;

        public HistoryDay(String date, int bits) {
            this.date = date;
            this.bits = bits;
        }

        public String getDate() { return date; }
        public int    getBits() { return bits; }
    }

    /** /api/history 的完整回傳結果 */
    public static class HistoryResult {
        private final List<HistoryDay> days;
        private final int complianceRate;

        public HistoryResult(List<HistoryDay> days, int complianceRate) {
            this.days           = days;
            this.complianceRate = complianceRate;
        }

        public List<HistoryDay> getDays()           { return days;           }
        public int              getComplianceRate()  { return complianceRate; }
    }

    // ── 回呼介面（每個 API 呼叫都有對應介面）──────────────────────────────────

    public interface OnStatusResult {
        void onResult(StatusResult result);
    }

    public interface OnHistoryResult {
        void onResult(HistoryResult result);
    }

    public interface OnSettingsResult {
        void onResult(boolean ok);
    }

    // ── 私有成員與 HTTP 工具 ───────────────────────────────────────────────────

    private static final int TIMEOUT_MS = 5000;
    private final Handler mainHandler;

    /** 若 host 尚未包含 http:// 或 https:// 前綴，自動補上 http:// 後回傳完整 base URL */
    private String buildBaseUrl(String host) {
        String trimmed = host.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "http://" + trimmed;
    }

    /** 對 host + path 發送 HTTP GET 請求，設定逾時後回傳伺服器回應的純文字內容 */
    private String httpGet(String host, String path) throws IOException {
        URL url = new URL(buildBaseUrl(host) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    /** 對 host + path 發送 HTTP POST 請求，傳送 form-encoded body 後回傳伺服器回應的純文字內容 */
    private String httpPostForm(String host, String path, String formBody) throws IOException {
        URL url = new URL(buildBaseUrl(host) + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStreamWriter writer =
                    new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(formBody);
            }
            return readResponse(conn);
        } finally {
            conn.disconnect();
        }
    }

    /** 逐行讀取 HttpURLConnection 的回應 body 並拼接為完整字串後回傳 */
    private String readResponse(HttpURLConnection conn) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    // ── 公開 API 方法 ──────────────────────────────────────────────────────────

    /** 在背景執行緒呼叫 GET /api/status，解析四格狀態與遵從率 JSON 後，於主執行緒回呼結果 */
    public void fetchStatus(String host, OnStatusResult callback) {
        new Thread(() -> {
            StatusResult result = null;
            try {
                JSONObject json = new JSONObject(httpGet(host, "/api/status"));
                JSONArray slotsJson = json.getJSONArray("slots");
                List<SlotStatus> slots = new ArrayList<>();
                for (int i = 0; i < slotsJson.length(); i++) {
                    JSONObject s = slotsJson.getJSONObject(i);
                    slots.add(new SlotStatus(
                            s.getString("label"),
                            s.getString("time"),
                            s.getString("state")));
                }
                result = new StatusResult(slots, json.optInt("complianceRate", 0));
            } catch (IOException | JSONException e) {
                LogUtils.error("PillBoxApi", "fetchStatus failed", e);
            }
            final StatusResult finalResult = result;
            mainHandler.post(() -> callback.onResult(finalResult));
        }).start();
    }

    /** 在背景執行緒呼叫 GET /api/history，解析 7 天服藥記錄與遵從率 JSON 後，於主執行緒回呼結果 */
    public void fetchHistory(String host, OnHistoryResult callback) {
        new Thread(() -> {
            HistoryResult result = null;
            try {
                JSONObject json = new JSONObject(httpGet(host, "/api/history"));
                JSONArray daysJson = json.getJSONArray("days");
                List<HistoryDay> days = new ArrayList<>();
                for (int i = 0; i < daysJson.length(); i++) {
                    JSONObject d = daysJson.getJSONObject(i);
                    days.add(new HistoryDay(d.getString("date"), d.getInt("bits")));
                }
                result = new HistoryResult(days, json.optInt("complianceRate", 0));
            } catch (IOException | JSONException e) {
                LogUtils.error("PillBoxApi", "fetchHistory failed", e);
            }
            final HistoryResult finalResult = result;
            mainHandler.post(() -> callback.onResult(finalResult));
        }).start();
    }

    /** 在背景執行緒以 POST /api/settings 將四個時段的服藥時間傳送至 ESP32，回呼儲存成功或失敗 */
    public void saveSettings(String host, List<String> slotTimes, OnSettingsResult callback) {
        new Thread(() -> {
            boolean ok = false;
            try {
                StringBuilder body = new StringBuilder();
                for (int i = 0; i < slotTimes.size(); i++) {
                    if (i > 0) body.append('&');
                    body.append("slot").append(i).append('=')
                        .append(URLEncoder.encode(slotTimes.get(i), StandardCharsets.UTF_8.name()));
                }
                JSONObject json = new JSONObject(
                        httpPostForm(host, "/api/settings", body.toString()));
                ok = json.optBoolean("ok", false);
            } catch (IOException | JSONException e) {
                LogUtils.error("PillBoxApi", "saveSettings failed", e);
            }
            final boolean finalOk = ok;
            mainHandler.post(() -> callback.onResult(finalOk));
        }).start();
    }
}
