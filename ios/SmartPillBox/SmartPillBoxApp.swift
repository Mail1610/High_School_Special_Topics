import SwiftUI

/// App 進入點與底部分頁，對應 Android 的 MainActivity.java + BottomNavigationView
@main
struct SmartPillBoxApp: App {
    var body: some Scene {
        WindowGroup {
            TabView {
                NavigationStack { TodayView().navigationTitle("今日服藥狀態") }
                    .tabItem { Label("今日", systemImage: "pills") }
                NavigationStack { SettingsView().navigationTitle("藥盒設定") }
                    .tabItem { Label("設定", systemImage: "gearshape") }
                NavigationStack { HistoryView().navigationTitle("服藥歷史") }
                    .tabItem { Label("歷史", systemImage: "calendar") }
            }
            .tint(AppColor.medicalBlue)
            .preferredColorScheme(.light) // 同 Android：強制淺色，不進入深色模式
        }
    }
}
