# 智慧藥盒 Android App

## 專案簡介

這是一款與 ESP32 智慧藥盒搭配使用的 Android 應用程式，透過 Wi-Fi 連線顯示即時服藥狀態、7 天歷史紀錄，並可設定服藥時間。

---

## 專案架構

```
MyApplication/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/myapplication/
│       │   ├── MainActivity.java
│       │   ├── TodayFragment.java
│       │   ├── SettingsFragment.java
│       │   ├── HistoryFragment.java
│       │   ├── PillBoxApi.java
│       │   ├── Prefs.java
│       │   └── LogUtils.java
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── fragment_today.xml
│           │   ├── fragment_settings.xml
│           │   ├── fragment_history.xml
│           │   ├── item_slot_status.xml
│           │   ├── item_slot_time_input.xml
│           │   └── item_history_day.xml
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── themes.xml
│           ├── values-night/
│           │   └── themes.xml
│           ├── menu/
│           │   └── bottom_nav_menu.xml
│           └── xml/
│               ├── backup_rules.xml
│               └── data_extraction_rules.xml
├── app/build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Java 程式邏輯

### `MainActivity.java`
App 啟動的唯一 Activity，負責整體框架。

| 區塊 | 說明 |
|------|------|
| Fragment 初始化 | 一次性建立三個 Fragment（Today / Settings / History），以 `add` + `hide` 方式加入 FragmentManager，切換時只做 `show/hide` 不重建 |
| BottomNavigationView | 選單在 `activity_main.xml` 的 `app:menu` 屬性中宣告；`setOnItemSelectedListener` 負責切換 Fragment 並更新 ActionBar 標題 |
| 狀態恢復 | `savedInstanceState != null` 時，從 FragmentManager 取回原本的 Fragment 實例，並根據目前選中的 tab 更新標題 |

---

### `TodayFragment.java`
「今日服藥狀態」頁，使用 `R.layout.fragment_today`。

| 方法 | 說明 |
|------|------|
| `onViewCreated()` | 綁定「重新整理」按鈕，並在頁面建立時自動呼叫一次 `loadStatus()` |
| `loadStatus()` | 1. 檢查 Wi-Fi 連線；2. 從 `Prefs` 讀取 IP；3. 呼叫 `PillBoxApi.fetchStatus()`；4. 成功後動態填入四個 `item_slot_status` 卡片 |
| `getStateLabel()` | 將 API 回傳的英文狀態碼（WAITING / ALERTING / TAKEN / MISSED）轉為中文 |
| `getStateColor()` | 依狀態碼回傳顯示顏色（灰 / 黃 / 綠 / 紅） |
| `isOnWifi()` | 透過 `ConnectivityManager` 判斷是否已連 Wi-Fi |

---

### `SettingsFragment.java`
「藥盒設定」頁，使用 `R.layout.fragment_settings`。

| 方法 / 區塊 | 說明 |
|------------|------|
| `onViewCreated()` | 預填已儲存的 IP（`editHost`）；動態充氣四個 `item_slot_time_input` 行並預填儲存的時間；設定儲存按鈕監聽器 |
| 時間輸入欄設定 | 每個 `editSlotTime` 設為不可直接聚焦（`focusable=false`），點擊時改由 `openTimePicker()` 開啟時間選擇器 |
| `openTimePicker()` | 開啟 Material 24 小時制時間選擇器，確認後寫回 `editSlotTime` |
| 儲存按鈕邏輯 | 驗證 IP 格式（`isValidIp()`）→ 驗證四個時段非空白 → 按鈕暫時停用（防重複點擊）→ 呼叫 `PillBoxApi.saveSettings()` → 回呼後恢復按鈕並顯示結果訊息 |
| 捲動重置 | `view.postDelayed(() -> scrollTo(0,0), 500)` — 防止 TextInputLayout hint 浮動動畫讓頁面自動下捲遮住標題 |
| `isValidIp()` | 正規表達式驗證 IPv4 格式（每段 0–255） |

---

### `HistoryFragment.java`
「服藥歷史」頁，使用 `R.layout.fragment_history`。

| 方法 / 區塊 | 說明 |
|------------|------|
| `onViewCreated()` | 綁定「重新整理」按鈕，並在頁面建立時自動呼叫一次 `loadHistory()` |
| `loadHistory()` | 1. 從 `Prefs` 讀取 IP；2. 呼叫 `PillBoxApi.fetchHistory()`；3. 成功後對每一天充氣 `item_history_day` 列 |
| 格子繪製邏輯 | 對每天的 `bits` 整數做位元運算（`bit0~3` 對應 4 格），依結果以程式碼動態建立綠色（已服）/ 紅色（未服）`TextView` 色塊 |

---

### `PillBoxApi.java`
與 ESP32 溝通的 HTTP 客戶端，採 **Singleton** 模式。

**內部架構：**

```
PillBoxApi
├── 靜態內部資料類別
│   ├── SlotStatus      — label / time / state
│   ├── StatusResult    — List<SlotStatus> + complianceRate
│   ├── HistoryDay      — date / bits（4-bit 服藥結果）
│   └── HistoryResult   — List<HistoryDay> + complianceRate
│
├── 回呼介面
│   ├── OnStatusResult  — onResult(StatusResult)
│   ├── OnHistoryResult — onResult(HistoryResult)
│   └── OnSettingsResult— onResult(boolean ok)
│
├── HTTP 工具（私有）
│   ├── buildBaseUrl()  — 自動補上 http:// 前綴
│   ├── httpGet()       — GET 請求（逾時 5 秒）
│   ├── httpPostForm()  — POST 請求（application/x-www-form-urlencoded）
│   └── readResponse()  — 讀取回應 body
│
└── 公開 API 方法
    ├── fetchStatus()   — GET /api/status  → OnStatusResult
    ├── fetchHistory()  — GET /api/history → OnHistoryResult
    └── saveSettings()  — POST /api/settings（slot0=HH:mm&slot1=…） → OnSettingsResult
```

所有網路請求在 **背景執行緒（`new Thread`）** 執行，結果透過 `mainHandler.post()` 回到 **UI 主執行緒**呼叫回呼。

---

### `Prefs.java`
`SharedPreferences` 的薄薄包裝，集中管理所有持久化設定。

| 方法 | Key | 說明 |
|------|-----|------|
| `getHost()` / `setHost()` | `esp32_host` | ESP32 的 IP 位址 |
| `getSlotTime()` / `setSlotTimes()` | `slot_time_0` ~ `slot_time_3` | 四個時段的服藥時間（格式 `HH:mm`） |

SharedPreferences 檔名：`pillbox_prefs`（`MODE_PRIVATE`）

---

### `LogUtils.java`
統一的日誌工具類別，包裝 Android `Log`，方便日後替換日誌元件。

| 方法 | 對應 Android Log |
|------|-----------------|
| `info(tag, msg)` | `Log.i` |
| `debug(tag, msg)` | `Log.d` |
| `warn(tag, msg)` | `Log.w` |
| `error(tag, msg)` | `Log.e` |
| `error(tag, msg, throwable)` | `Log.e`（含例外堆疊） |

---

## 版面配置（`res/layout/`）

### `activity_main.xml`
App 的主框架（LinearLayout 垂直排列）。

```
LinearLayout (vertical)
├── FragmentContainerView  id=fragmentContainer  (layout_weight=1，撐滿剩餘空間)
└── BottomNavigationView   id=bottomNav           (app:menu=@menu/bottom_nav_menu)
```

---

### `fragment_today.xml`
今日服藥狀態頁（ScrollView）。

```
ScrollView (fillViewport, background=white)
└── LinearLayout (padding=12dp)
    ├── MaterialCardView (elevation=3dp, cornerRadius=16dp)
    │   └── LinearLayout
    │       ├── LinearLayout (horizontal)
    │       │   ├── TextView  id=textCompliance   (26sp bold，顯示遵從率)
    │       │   └── Button    id=buttonRefresh     (文字按鈕)
    │       └── ProgressBar  id=progressCompliance (10dp高，invisible 預設)
    ├── LinearLayout  id=slotsContainer            (動態填入 item_slot_status)
    └── TextView      id=textMessage               (紅色錯誤訊息)
```

---

### `fragment_settings.xml`
藥盒設定頁（ScrollView，`focusable=true` 用於搶回焦點）。

```
ScrollView (fillViewport, background=white)
└── LinearLayout (padding=12dp, focusable=true, focusableInTouchMode=true)
    ├── MaterialCardView (藥盒連線設定卡)
    │   └── LinearLayout (padding=16dp)
    │       ├── TextView     「藥盒連線設定」區塊標題
    │       └── TextInputLayout (OutlinedBox)
    │           └── TextInputEditText  id=editHost
    ├── MaterialCardView (服藥時間設定卡)
    │   └── LinearLayout (padding=16dp)
    │       ├── TextView     「服藥時間設定」區塊標題
    │       └── LinearLayout id=slotInputsContainer  (動態填入 item_slot_time_input × 4)
    ├── TextView  id=textSettingsMessage  (結果訊息)
    └── Button    id=buttonSave           (儲存設定)
```

---

### `fragment_history.xml`
服藥歷史頁（ScrollView）。

```
ScrollView (fillViewport, background=white)
└── LinearLayout (padding=12dp)
    ├── MaterialCardView (elevation=3dp，遵從率卡)
    │   └── LinearLayout
    │       ├── LinearLayout (horizontal)
    │       │   ├── TextView  id=textHistoryCompliance  (26sp bold)
    │       │   └── Button    id=buttonHistoryRefresh
    │       └── ProgressBar  id=progressHistoryCompliance (invisible 預設)
    ├── MaterialCardView (elevation=2dp，歷史表格卡)
    │   └── LinearLayout
    │       ├── LinearLayout (horizontal，表頭列)
    │       │   ├── TextView 「日期」(weight=1)
    │       │   └── LinearLayout (weight=2，4 欄等分)
    │       │       ├── TextView 「早上」
    │       │       ├── TextView 「中午」
    │       │       ├── TextView 「晚上」
    │       │       └── TextView 「睡前」
    │       └── LinearLayout id=historyDaysContainer (動態填入 item_history_day)
    └── TextView id=textHistoryMessage (紅色錯誤訊息)
```

---

### `item_slot_status.xml`
今日頁的單一藥格狀態卡（`MaterialCardView`，`elevation=2dp`）。

```
MaterialCardView (cornerRadius=14dp, marginBottom=8dp)
└── LinearLayout (horizontal, padding=16dp/18dp, gravity=center_vertical)
    ├── LinearLayout (weight=1, vertical)
    │   ├── TextView  id=textSlotLabel  (20sp bold，藥格名稱如「早上」)
    │   └── TextView  id=textSlotTime   (15sp secondary，設定時間如「08:00」)
    └── TextView      id=textSlotState  (18sp bold, minWidth=64dp, gravity=end，狀態如「已服藥」)
```

---

### `item_slot_time_input.xml`
設定頁單一時段輸入行（LinearLayout 垂直，`marginBottom=12dp`）。

```
LinearLayout (vertical, marginBottom=12dp)
├── TextView       id=textSlotInputLabel  (14sp bold primary，如「早上 服藥時間」)
└── TextInputLayout (OutlinedBox, hint=settings_slot_hint)
    └── TextInputEditText  id=editSlotTime  (18sp，focusable=false，點擊開時間選擇器)
```

---

### `item_history_day.xml`
歷史頁單一天的紀錄行（LinearLayout 水平）。

```
LinearLayout (horizontal, paddingTop/Bottom=8dp, gravity=center_vertical)
├── TextView       id=textHistoryDate     (weight=1，日期如「06/01」)
└── LinearLayout   id=historyCellsContainer (weight=2，動態填入 4 個 TextView 色塊)
```

---

## 資源設定

### `res/values/strings.xml`
集中管理所有 UI 文字，分為以下群組：
- Tab / ActionBar 標題（`tab_today`、`title_today` 等）
- 今日頁文字（`today_compliance_format`、`state_*`、`today_load_failed`）
- 設定頁文字（`settings_section_*`、`settings_save_*`、`error_*`）
- 歷史頁文字（`history_compliance_format`、`history_taken`、`history_missed`）
- 時段名稱（`slot_morning`、`slot_noon`、`slot_evening`、`slot_night`）

### `res/values/colors.xml`
| 名稱 | 色碼 | 用途 |
|------|------|------|
| `medical_blue` | `#1976D2` | 主色調（ActionBar、按鈕） |
| `medical_blue_dark` | `#1565C0` | 深藍（Status bar） |
| `medical_teal` | `#00897B` | 次色調 |
| `color_taken` | `#0F9D58` | 已服藥（綠色） |
| `color_missed` | `#DB4437` | 未服藥（紅色） |
| `color_alerting` | `#F4B400` | 提醒中（黃色） |

### `res/values/themes.xml` 與 `res/values-night/themes.xml`
- **繼承**：`Theme.MaterialComponents.Light.DarkActionBar`（亮色主題）
- **夜間覆寫**：`values-night/themes.xml` 同樣使用 Light 主題，強制 App 不進入深色模式
- **`android:forceDarkAllowed="false"`**：防止 MIUI 等系統強制套用深色反色

---

## API 合約（與 ESP32 溝通格式）

```
GET  /api/status
回傳：{
  "slots": [
    { "label": "早上", "time": "08:00", "state": "WAITING" },
    { "label": "中午", "time": "12:00", "state": "TAKEN"   },
    { "label": "晚上", "time": "18:00", "state": "MISSED"  },
    { "label": "睡前", "time": "22:00", "state": "ALERTING"}
  ],
  "complianceRate": 86
}
state 可為：WAITING（等待）| ALERTING（提醒中）| TAKEN（已服）| MISSED（漏服）

GET  /api/history
回傳：{
  "days": [
    { "date": "06/08", "bits": 15 },
    { "date": "06/09", "bits": 13 }
  ],
  "complianceRate": 86
}
bits 為 4-bit 整數，bit0~3 分別對應 4 格，1 = 已服，0 = 未服

POST /api/settings   (Content-Type: application/x-www-form-urlencoded)
傳送：slot0=08:00&slot1=12:00&slot2=18:00&slot3=22:00
回傳：{ "ok": true }
```

---

## 建構設定

| 檔案 | 內容 |
|------|------|
| `app/build.gradle.kts` | compileSdk 36、minSdk 24、targetSdk 36；依賴：AppCompat、Material Components、Core-KTX |
| `build.gradle.kts` | 根專案設定，宣告 AGP 版本與 Kotlin 版本 |
| `settings.gradle.kts` | 模組宣告（`:app`）與 Maven 儲存庫（Google、MavenCentral） |
