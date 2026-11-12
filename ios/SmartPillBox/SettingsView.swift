import SwiftUI

/// 「藥盒設定」頁，對應 Android 的 SettingsFragment.java
struct SettingsView: View {
    @State private var host = Prefs.getHost()
    @State private var times = (0..<4).map { Prefs.getSlotTime($0) }
    @State private var message = ""
    @State private var saving = false
    @State private var pickingSlot: Int?
    @State private var pickerDate = Date()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                card {
                    Text("藥盒連線設定").font(.headline)
                    TextField("例如：192.168.1.105", text: $host)
                        .keyboardType(.numbersAndPunctuation)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textFieldStyle(.roundedBorder)
                }
                card {
                    Text("服藥時間設定").font(.headline)
                    ForEach(0..<4, id: \.self) { i in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(Str.slotLabels[i]) 服藥時間")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(AppColor.medicalBlue)
                            // 點擊開時間選擇器，不直接打字，同 Android 的 focusable=false
                            Button { openTimePicker(i) } label: {
                                Text(times[i].isEmpty ? "HH:mm，例如 08:00" : times[i])
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
                Text(message)
                Button(action: save) {
                    Text("儲存設定").frame(maxWidth: .infinity)
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
                .navigationTitle(pickingSlot.map { "\(Str.slotLabels[$0]) 服藥時間" } ?? "")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("取消") { pickingSlot = nil } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("確定") {
                            if let i = pickingSlot {
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
            .background(RoundedRectangle(cornerRadius: 16).fill(Color(.systemBackground)).shadow(radius: 1))
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
        let host = host.trimmingCharacters(in: .whitespaces)
        if !host.isEmpty && !Self.isValidIp(host) {
            message = "IP 格式不正確，請輸入如 192.168.1.105 的格式"
            return
        }
        Prefs.setHost(host)
        if host.isEmpty {
            message = "已儲存 IP 位址"
            return
        }
        let trimmed = times.map { $0.trimmingCharacters(in: .whitespaces) }
        if trimmed.contains(where: \.isEmpty) {
            message = "請填入所有服藥時間後再儲存"
            return
        }
        saving = true
        message = ""
        Task {
            let ok = await PillBoxApi.saveSettings(host: host, slotTimes: trimmed)
            saving = false
            if ok {
                Prefs.setSlotTimes(trimmed)
                message = "設定已儲存並送出至藥盒"
            } else {
                message = "儲存失敗，請確認 IP 是否正確、藥盒是否開機"
            }
        }
    }

    private static func isValidIp(_ ip: String) -> Bool {
        ip.range(of: #"^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$"#,
                 options: .regularExpression) != nil
    }
}
