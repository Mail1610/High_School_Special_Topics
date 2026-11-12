import SwiftUI

/// 「服藥歷史」頁，對應 Android 的 HistoryFragment.java
struct HistoryView: View {
    @State private var complianceText = ""
    @State private var rate: Int?
    @State private var days: [PillBoxApi.HistoryDay] = []
    @State private var message = ""

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                ComplianceCard(text: complianceText, rate: rate) { Task { await loadHistory() } }

                VStack(spacing: 0) {
                    HStack {
                        Text("日期").frame(maxWidth: .infinity, alignment: .leading)
                        HStack(spacing: 4) {
                            ForEach(Str.slotLabels, id: \.self) { Text($0).frame(maxWidth: .infinity) }
                        }
                        .frame(maxWidth: .infinity)
                        .layoutPriority(1)
                    }
                    .font(.system(size: 14, weight: .bold))
                    .padding(.bottom, 8)

                    ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                        HStack {
                            Text(day.date).frame(maxWidth: .infinity, alignment: .leading)
                            HStack(spacing: 4) {
                                // bit0~3 對應 4 格，1 = 已服，0 = 未服
                                ForEach(0..<4, id: \.self) { slot in
                                    let taken = (day.bits >> slot) & 1 == 1
                                    Text(taken ? "服" : "漏")
                                        .font(.system(size: 18))
                                        .foregroundStyle(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 8)
                                        .background(taken ? AppColor.taken : AppColor.missed)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .layoutPriority(1)
                        }
                        .padding(.vertical, 8)
                    }
                }
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 16).fill(Color(.systemBackground)).shadow(radius: 1))

                Text(message).foregroundStyle(AppColor.missed).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(12)
        }
        // 每次切到這一頁時重新載入，同 Android 的 onResume / onHiddenChanged
        .task { await loadHistory() }
    }

    private func loadHistory() async {
        let host = Prefs.getHost()
        if host.isEmpty {
            showError(Str.historyNoHost, clearDays: true)
            return
        }
        if !NetworkUtils.shared.isOnWifi {
            showError(Str.errorNoWifi, clearDays: true)
            return
        }
        complianceText = Str.loading
        rate = nil
        message = ""
        days = []

        guard let result = await PillBoxApi.fetchHistory(host: host) else {
            showError(Str.historyLoadFailed, clearDays: false)
            return
        }
        message = ""
        complianceText = Str.compliance(result.complianceRate)
        rate = result.complianceRate
        days = result.days
    }

    private func showError(_ text: String, clearDays: Bool) {
        complianceText = ""
        rate = nil
        if clearDays { days = [] }
        message = text
    }
}
