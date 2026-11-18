import SwiftUI

/// App 進入點與底部分頁，對應 Android 的 MainActivity.java + BottomNavigationView
@main
struct SmartPillBoxApp: App {
    @AppStorage(AppLang.key) private var lang = "system"
    @State private var tab = 0

    var body: some Scene {
        WindowGroup {
            TabView(selection: $tab) {
                NavigationStack { TodayView().navigationTitle(L("今日服藥狀態", "Today's Medication")) }
                    .tabItem { Label(L("今日", "Today"), systemImage: "pills") }
                    .tag(0)
                NavigationStack { SettingsView().navigationTitle(L("藥盒設定", "Pill Box Settings")) }
                    .tabItem { Label(L("設定", "Settings"), systemImage: "gearshape") }
                    .tag(1)
                NavigationStack { HistoryView().navigationTitle(L("服藥歷史", "Medication History")) }
                    .tabItem { Label(L("歷史", "History"), systemImage: "calendar") }
                    .tag(2)
            }
            .tint(AppColor.medicalBlue)
            .onChange(of: tab) { _, _ in Haptics.click() } // 同 Android：切換分頁時觸覺回饋
            .id(lang) // 換語言時重建畫面，同 Android AppCompat 重建 Activity
            // 深色模式：不再強制淺色，跟隨系統設定（同 Android values-night）
        }
    }
}
