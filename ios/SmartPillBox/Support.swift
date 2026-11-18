import Foundation
import Network
import os
import SwiftUI

/// 對應 Android 的 LogUtils.java：統一的日誌入口
enum LogUtils {
    private static let logger = Logger(subsystem: "com.example.SmartPillBox", category: "app")

    static func info(_ tag: String, _ msg: String)  { logger.info("[\(tag)] \(msg)") }
    static func debug(_ tag: String, _ msg: String) { logger.debug("[\(tag)] \(msg)") }
    static func warn(_ tag: String, _ msg: String)  { logger.warning("[\(tag)] \(msg)") }
    static func error(_ tag: String, _ msg: String, _ err: Error? = nil) {
        logger.error("[\(tag)] \(msg) \(err.map { String(describing: $0) } ?? "")")
    }
}

/// 對應 Android 的 Prefs.java：用同樣的 key 名稱存在 UserDefaults
enum Prefs {
    private static let keyHost = "esp32_host"
    private static let keySlotPrefix = "slot_time_"

    static func getHost() -> String {
        UserDefaults.standard.string(forKey: keyHost) ?? ""
    }

    static func setHost(_ host: String) {
        UserDefaults.standard.set(host.trimmingCharacters(in: .whitespaces), forKey: keyHost)
    }

    static func getSlotTime(_ index: Int) -> String {
        UserDefaults.standard.string(forKey: keySlotPrefix + String(index)) ?? ""
    }

    static func setSlotTimes(_ times: [String]) {
        for (i, t) in times.enumerated() {
            UserDefaults.standard.set(t, forKey: keySlotPrefix + String(i))
        }
    }
}

/// 對應 Android 的 NetworkUtils.java：判斷是否在 Wi-Fi
/// 另外接受有線網路，模擬器在接網路線的 Mac 上才能測試
final class NetworkUtils {
    static let shared = NetworkUtils()
    private let monitor = NWPathMonitor()
    private(set) var isOnWifi = true

    private init() {
        monitor.pathUpdateHandler = { [weak self] path in
            self?.isOnWifi = path.status == .satisfied
                && (path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet))
        }
        monitor.start(queue: DispatchQueue(label: "NetworkUtils"))
    }
}

/// 對應 Android 的 colors.xml
enum AppColor {
    static let medicalBlue = Color(red: 0x19 / 255, green: 0x76 / 255, blue: 0xD2 / 255)
    static let taken       = Color(red: 0x0F / 255, green: 0x9D / 255, blue: 0x58 / 255)
    static let missed      = Color(red: 0xDB / 255, green: 0x44 / 255, blue: 0x37 / 255)
    static let alerting    = Color(red: 0xF4 / 255, green: 0xB4 / 255, blue: 0x00 / 255)
    static let waiting     = Color.gray
}

/// 語言設定，對應 Android 的 AppCompatDelegate.setApplicationLocales（"system" / "zh" / "en"）
enum AppLang {
    static let key = "app_lang"
    static var isEnglish: Bool {
        switch UserDefaults.standard.string(forKey: key) ?? "system" {
        case "en": return true
        case "zh": return false
        default:   return Locale.preferredLanguages.first?.hasPrefix("en") ?? false
        }
    }
}

/// 依目前語言回傳中文或英文字串（對應 values/strings.xml 與 values-en/strings.xml）
func L(_ zh: String, _ en: String) -> String { AppLang.isEnglish ? en : zh }

/// 觸覺回饋，對應 Android 的 HapticFeedbackConstants.CONTEXT_CLICK
enum Haptics {
    static func click() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
}

/// 對應 Android 的 strings.xml
enum Str {
    static var todayNoHost: String { L("尚未設定藥盒 IP，請先至「設定」頁填入", "No pill box IP set yet. Please set it in the Settings tab first.") }
    static var historyNoHost: String { todayNoHost }
    static var errorNoWifi: String { L("您目前不在 Wi-Fi 環境，請連上家中 Wi-Fi 後再試", "You're not on Wi-Fi. Connect to your home Wi-Fi and try again.") }
    static var loading: String { L("載入中…", "Loading…") }
    static var refresh: String { L("重新整理", "Refresh") }
    static var todayLoadFailed: String { L("無法連線到藥盒，請確認手機與藥盒在同一個 WiFi，並在「設定」頁填入正確的 IP", "Cannot reach the pill box. Make sure your phone and the pill box are on the same Wi-Fi, and enter the correct IP in the Settings tab.") }
    static var historyLoadFailed: String { L("無法載入歷史紀錄，請確認連線設定", "Cannot load history. Please check the connection settings.") }
    static func compliance(_ rate: Int) -> String { L("本週遵從率：\(rate)%", "This week's adherence: \(rate)%") }
    static var slotLabels: [String] { AppLang.isEnglish ? ["Morning", "Noon", "Evening", "Bedtime"] : ["早上", "中午", "晚上", "睡前"] }
    static func slotTimeLabel(_ i: Int) -> String { L("\(slotLabels[i]) 服藥時間", "\(slotLabels[i]) medication time") }
}

/// 遵從率卡片，今日頁與歷史頁共用（對應兩個 layout 中相同結構的 MaterialCardView）
struct ComplianceCard: View {
    let text: String
    let rate: Int?
    let onRefresh: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(text).font(.system(size: 26, weight: .bold))
                Spacer()
                Button(Str.refresh, action: onRefresh)
            }
            ProgressView(value: Double(rate ?? 0), total: 100)
                .tint(AppColor.medicalBlue)
                .opacity(rate == nil ? 0 : 1)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemGroupedBackground)).shadow(radius: 2))
    }
}
