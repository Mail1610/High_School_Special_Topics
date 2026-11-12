import SwiftUI

/// 「今日服藥狀態」頁，對應 Android 的 TodayFragment.java
struct TodayView: View {
    private static let refreshInterval: UInt64 = 30_000_000_000 // 30 秒，同 Android 的 REFRESH_INTERVAL_MS

    @State private var complianceText = ""
    @State private var rate: Int?
    @State private var slots: [PillBoxApi.SlotStatus] = []
    @State private var message = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 8) {
                ComplianceCard(text: complianceText, rate: rate) { Task { await loadStatus() } }
                    .padding(.bottom, 4)
                ForEach(Array(slots.enumerated()), id: \.offset) { _, slot in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(slot.label).font(.system(size: 20, weight: .bold))
                            Text(slot.time).font(.system(size: 15)).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(Self.stateLabel(slot.state))
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(Self.stateColor(slot.state))
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 18)
                    .background(RoundedRectangle(cornerRadius: 14).fill(Color(.systemBackground)).shadow(radius: 1))
                }
                Text(message).foregroundStyle(AppColor.missed).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
        }
        // 頁面顯示時每 30 秒自動刷新，離開頁面時自動停止
        .task {
            while !Task.isCancelled {
                await loadStatus()
                try? await Task.sleep(nanoseconds: Self.refreshInterval)
            }
        }
    }

    private func loadStatus() async {
        let host = Prefs.getHost()
        if host.isEmpty {
            showError(Str.todayNoHost)
            return
        }
        if !NetworkUtils.shared.isOnWifi {
            showError(Str.errorNoWifi)
            return
        }
        complianceText = Str.loading
        rate = nil
        message = ""

        guard let result = await PillBoxApi.fetchStatus(host: host) else {
            showError(Str.todayLoadFailed)
            return
        }
        message = ""
        complianceText = Str.compliance(result.complianceRate)
        rate = result.complianceRate
        slots = result.slots
    }

    private func showError(_ text: String) {
        complianceText = ""
        rate = nil
        slots = []
        message = text
    }

    private static func stateLabel(_ state: String) -> String {
        switch state {
        case "ALERTING": return "提醒中"
        case "TAKEN":    return "已服藥"
        case "MISSED":   return "漏服"
        default:         return "等待"
        }
    }

    private static func stateColor(_ state: String) -> Color {
        switch state {
        case "ALERTING": return AppColor.alerting
        case "TAKEN":    return AppColor.taken
        case "MISSED":   return AppColor.missed
        default:         return AppColor.waiting
        }
    }
}
