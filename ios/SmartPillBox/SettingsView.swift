import SwiftUI

/// 「藥盒設定」頁，對應 Android 的 SettingsFragment.java
struct SettingsView: View {
    @State private var host = Prefs.getHost()
    @State private var times = (0..<4).map { Prefs.getSlotTime($0) }
    @State private var message = ""
    @State private var saving = false
    @State private var pickingSlot: Int?
    @State private var pickerDate = Date()
    @AppStorage(AppLang.key) private var lang = "system"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                card {
                    Text(L("藥盒連線設定", "Pill box connection")).font(.headline)
                    TextField(L("例如：192.168.1.105", "e.g. 192.168.1.105"), text: $host)
                        .keyboardType(.numbersAndPunctuation)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                }
                card {
                    Text(L("服藥時間設定", "Medication times")).font(.headline)
                    ForEach(0..<4, id: \.self) { i in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(Str.slotTimeLabel(i))
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(AppColor.medicalBlue)
                            // 點擊開時間選擇器，不直接打字，同 Android 的 focusable=false
                            Button { openTimePicker(i) } label: {
                                Text(times[i].isEmpty ? L("HH:mm，例如 08:00", "HH:mm, e.g. 08:00") : times[i])
                                    .font(.system(size: 18))
                                    .foregroundStyle(times[i].isEmpty ? .secondary : .primary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(10)
                                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.gray.opacity(0.5)))
                            }
                        }
                        .padding(.bottom, 4)
                    }
                }
                card {
                    Text(L("語言", "Language")).font(.headline)
                    // 對應 Android 的 MaterialButtonToggleGroup：跟隨系統 / 中文 / English，點選即時套用
                    Picker("", selection: $lang) {
                        Text(L("跟隨系統", "System")).tag("system")
                        Text("中文").tag("zh")
                        Text("English").tag("en")
                    }
                    .pickerStyle(.segmented)
                }
                Text(message)
                Button(action: save) {
                    Text(L("儲存設定", "Save settings")).frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(AppColor.medicalBlue)
                .disabled(saving)
            }
            .padding(12)
        }
        .sheet(isPresented: Binding(get: { pickingSlot != nil }, set: { if !$0 { pickingSlot = nil } })) {
            timePickerSheet
        }
    }

    private var timePickerSheet: some View {
        NavigationStack {
            DatePicker("", selection: $pickerDate, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "en_GB")) // 24 小時制
                .navigationTitle(pickingSlot.map { Str.slotTimeLabel($0) } ?? "")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(L("取消", "Cancel")) { pickingSlot = nil } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(L("確定", "OK")) {
                            if let i = pickingSlot {
                                Haptics.click()
                                let c = Calendar.current.dateComponents([.hour, .minute], from: pickerDate)
                                times[i] = String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
                            }
                            pickingSlot = nil
                        }
                    }
                }
        }
        .presentationDetents([.medium])
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12, content: content)
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemGroupedBackground)).shadow(radius: 1))
    }

    private func openTimePicker(_ index: Int) {
        // 預設 08:00，若已有 HH:mm 就帶入
        var hour = 8, minute = 0
        let current = times[index]
        if current.range(of: #"^\d{2}:\d{2}$"#, options: .regularExpression) != nil {
            hour = Int(current.prefix(2)) ?? 8
            minute = Int(current.suffix(2)) ?? 0
        }
        pickerDate = Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
        pickingSlot = index
    }

    private func save() {
        Haptics.click()
        let host = host.trimmingCharacters(in: .whitespaces)
        if !host.isEmpty && !Self.isValidIp(host) {
            message = L("IP 格式不正確，請輸入如 192.168.1.105 的格式", "Invalid IP format. Enter something like 192.168.1.105.")
            return
        }
        Prefs.setHost(host)
        if host.isEmpty {
            message = L("已儲存 IP 位址", "IP address saved")
            return
        }
        let trimmed = times.map { $0.trimmingCharacters(in: .whitespaces) }
        if trimmed.contains(where: \.isEmpty) {
            message = L("請填入所有服藥時間後再儲存", "Please fill in all medication times before saving.")
            return
        }
        saving = true
        message = ""
        Task {
            let ok = await PillBoxApi.saveSettings(host: host, slotTimes: trimmed)
            saving = false
            if ok {
                Prefs.setSlotTimes(trimmed)
                message = L("設定已儲存並送出至藥盒", "Settings saved and sent to the pill box")
            } else {
                message = L("儲存失敗，請確認 IP 是否正確、藥盒是否開機", "Save failed. Check the IP and make sure the pill box is powered on.")
            }
        }
    }

    private static func isValidIp(_ ip: String) -> Bool {
        ip.range(of: #"^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$"#,
                 options: .regularExpression) != nil
    }
}
