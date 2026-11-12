import Foundation

/// 與 ESP32 溝通的 HTTP 客戶端，對應 Android 的 PillBoxApi.java（API 合約見 README）
struct PillBoxApi {
    struct SlotStatus: Decodable {
        let label: String
        let time: String
        let state: String
    }

    struct StatusResult: Decodable {
        let slots: [SlotStatus]
        let complianceRate: Int

        enum CodingKeys: String, CodingKey { case slots, complianceRate }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            slots = try c.decode([SlotStatus].self, forKey: .slots)
            complianceRate = try c.decodeIfPresent(Int.self, forKey: .complianceRate) ?? 0
        }
    }

    struct HistoryDay: Decodable {
        let date: String
        let bits: Int
    }

    struct HistoryResult: Decodable {
        let days: [HistoryDay]
        let complianceRate: Int

        enum CodingKeys: String, CodingKey { case days, complianceRate }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            days = try c.decode([HistoryDay].self, forKey: .days)
            complianceRate = try c.decodeIfPresent(Int.self, forKey: .complianceRate) ?? 0
        }
    }

    private struct SettingsResponse: Decodable {
        let ok: Bool?
    }

    private static let timeout: TimeInterval = 5

    private static func buildBaseUrl(_ host: String) -> String {
        let trimmed = host.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            return trimmed
        }
        return "http://" + trimmed
    }

    private static func request(_ host: String, _ path: String) throws -> URLRequest {
        guard let url = URL(string: buildBaseUrl(host) + path) else { throw URLError(.badURL) }
        var req = URLRequest(url: url)
        req.timeoutInterval = timeout
        return req
    }

    /// GET /api/status，失敗回傳 nil
    static func fetchStatus(host: String) async -> StatusResult? {
        do {
            let (data, _) = try await URLSession.shared.data(for: request(host, "/api/status"))
            return try JSONDecoder().decode(StatusResult.self, from: data)
        } catch {
            LogUtils.error("PillBoxApi", "fetchStatus failed", error)
            return nil
        }
    }

    /// GET /api/history，失敗回傳 nil
    static func fetchHistory(host: String) async -> HistoryResult? {
        do {
            let (data, _) = try await URLSession.shared.data(for: request(host, "/api/history"))
            return try JSONDecoder().decode(HistoryResult.self, from: data)
        } catch {
            LogUtils.error("PillBoxApi", "fetchHistory failed", error)
            return nil
        }
    }

    /// POST /api/settings（slot0=HH:mm&slot1=…），回傳 ESP32 的 ok 值
    static func saveSettings(host: String, slotTimes: [String]) async -> Bool {
        do {
            var req = try request(host, "/api/settings")
            req.httpMethod = "POST"
            req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            req.httpBody = slotTimes.enumerated()
                .map { "slot\($0.offset)=\($0.element.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? $0.element)" }
                .joined(separator: "&")
                .data(using: .utf8)
            let (data, _) = try await URLSession.shared.data(for: req)
            return try JSONDecoder().decode(SettingsResponse.self, from: data).ok ?? false
        } catch {
            LogUtils.error("PillBoxApi", "saveSettings failed", error)
            return false
        }
    }
}
