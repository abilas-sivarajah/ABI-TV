import Foundation

public struct AppRelease: Codable, Hashable, Sendable {
    public let tagName: String
    public let versionName: String
    public let title: String
    public let changelog: String
    public let downloadUrl: String
    public let assetName: String
    public let fileSize: Int64
    public let sha256: String?

    public init(
        tagName: String,
        versionName: String,
        title: String,
        changelog: String,
        downloadUrl: String,
        assetName: String,
        fileSize: Int64,
        sha256: String?
    ) {
        self.tagName = tagName
        self.versionName = versionName
        self.title = title
        self.changelog = changelog
        self.downloadUrl = downloadUrl
        self.assetName = assetName
        self.fileSize = fileSize
        self.sha256 = sha256
    }

    public var sideStoreInstallUrl: URL? {
        guard let encoded = downloadUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return nil
        }
        return URL(string: "sidestore://install?url=\(encoded)")
    }

    public var altStoreInstallUrl: URL? {
        guard let encoded = downloadUrl.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            return nil
        }
        return URL(string: "altstore://install?url=\(encoded)")
    }
}

public enum UpdateCheckResult: Sendable {
    case available(AppRelease)
    case upToDate
    case failed(String)
}

public enum UpdateUtils {

    /// Entfernt Markdown-Syntax und GitHub-Boilerplate, damit die Notizen gut lesbar sind.
    public static func cleanChangelog(_ raw: String) -> String {
        let lines = raw.components(separatedBy: .newlines)
            .map { $0.replacingOccurrences(of: #"\s+$"#, with: "", options: .regularExpression) }
            .filter { line in
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                return !trimmed.lowercased().hasPrefix("**full changelog**") && !trimmed.hasPrefix("<!--")
            }
            .map { line -> String in
                var s = line
                s = s.replacingOccurrences(of: #"^\s*#{1,6}\s*"#, with: "", options: .regularExpression)
                s = s.replacingOccurrences(of: #"^\s*[-*+]\s+"#, with: "• ", options: .regularExpression)
                s = s.replacingOccurrences(of: #"\[([^\]]+)\]\([^)]+\)"#, with: "$1", options: .regularExpression)
                s = s.replacingOccurrences(of: #" by @\S+ in https?://\S+"#, with: "", options: .regularExpression)
                s = s.replacingOccurrences(of: "**", with: "")
                s = s.replacingOccurrences(of: "__", with: "")
                s = s.replacingOccurrences(of: "`", with: "")
                return s
            }
            .joined(separator: "\n")
            .replacingOccurrences(of: #"\n{3,}"#, with: "\n\n", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        return lines.isEmpty ? "Allgemeine Verbesserungen und Fehlerbehebungen." : lines
    }

    /// Vergleicht zwei semantische Versionen (z. B. "1.2.0" mit "1.1.9").
    public static func isNewerVersion(remote: String, current: String) -> Bool {
        let remoteParts = remote.components(separatedBy: ".").map { part -> Int in
            let digits = part.prefix { $0.isNumber }
            return Int(digits) ?? 0
        }
        let currentParts = current.components(separatedBy: ".").map { part -> Int in
            let digits = part.prefix { $0.isNumber }
            return Int(digits) ?? 0
        }
        let maxLen = max(remoteParts.count, currentParts.count)
        for i in 0..<maxLen {
            let r = i < remoteParts.count ? remoteParts[i] : 0
            let c = i < currentParts.count ? currentParts[i] : 0
            if r > c { return true }
            if r < c { return false }
        }
        return false
    }
}

public enum UpdateManager {

    private static let userAgent = "ABI-TV-Updater"
    private static let snoozeDurationMs: Int64 = 24 * 60 * 60 * 1000
    public static let assetName = "ABI_iOS.ipa"
    public static let defaultRepo = "abilas-sivarajah/ABI-TV"

    public static func getCurrentVersionName() -> String {
        let raw = (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String) ?? "1.0.14"
        return raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"^[vV]"#, with: "", options: .regularExpression)
    }

    public static func getTargetRepo() -> String {
        let configured = LocalStorage.shared.githubRepo
        if !configured.isEmpty && !configured.contains("YOUR_GITHUB_USER") {
            return configured
        }
        return defaultRepo
    }

    public static func checkForUpdate(forceCheck: Bool = false) async -> UpdateCheckResult {
        let repo = getTargetRepo()
        guard !repo.isEmpty, repo.contains("/"),
              let url = URL(string: "https://api.github.com/repos/\(repo)/releases/latest") else {
            return .failed("Kein gültiges GitHub-Repo konfiguriert (\(repo)).")
        }

        var req = URLRequest(url: url)
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")

        let data: Data
        let httpResponse: HTTPURLResponse
        do {
            let (d, resp) = try await HttpClient.shared.session.data(for: req)
            guard let http = resp as? HTTPURLResponse else {
                return .failed("Ungültige Antwort von GitHub.")
            }
            data = d
            httpResponse = http
        } catch {
            return .failed("Keine Verbindung zu GitHub. Bitte Internetverbindung prüfen.")
        }

        guard (200..<300).contains(httpResponse.statusCode) else {
            switch httpResponse.statusCode {
            case 404: return .failed("Noch kein Release auf GitHub veröffentlicht.")
            case 403, 429: return .failed("GitHub-Anfragelimit erreicht. Bitte später erneut versuchen.")
            default: return .failed("GitHub antwortet nicht wie erwartet (HTTP \(httpResponse.statusCode)).")
            }
        }

        guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let tagName = json["tag_name"] as? String, !tagName.isEmpty else {
            return .failed("Antwort von GitHub konnte nicht gelesen werden.")
        }

        let rawTitle = (json["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let title = rawTitle.isEmpty ? tagName : rawTitle
        let changelog = UpdateUtils.cleanChangelog((json["body"] as? String) ?? "")

        let remoteVersion = tagName
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"^[vV]"#, with: "", options: .regularExpression)
        let currentVersion = getCurrentVersionName()

        guard UpdateUtils.isNewerVersion(remote: remoteVersion, current: currentVersion) else {
            return .upToDate
        }

        if !forceCheck && isSnoozed(tagName) {
            return .upToDate
        }

        let assets = (json["assets"] as? [[String: Any]]) ?? []
        let matchedAsset = assets.first {
            (($0["name"] as? String) ?? "").caseInsensitiveCompare(assetName) == .orderedSame
        } ?? assets.first {
            (($0["name"] as? String) ?? "").lowercased().hasSuffix(".ipa")
        }

        guard let asset = matchedAsset,
              let downloadUrl = asset["browser_download_url"] as? String,
              let name = asset["name"] as? String else {
            return .failed("Release \(tagName) enthält noch keine iOS-IPA.")
        }

        let fileSize = (asset["size"] as? NSNumber)?.int64Value ?? 0
        var sha256: String?
        if let digest = asset["digest"] as? String,
           digest.lowercased().hasPrefix("sha256:") {
            sha256 = String(digest.dropFirst("sha256:".count)).lowercased()
        }

        return .available(
            AppRelease(
                tagName: tagName,
                versionName: remoteVersion,
                title: title,
                changelog: changelog,
                downloadUrl: downloadUrl,
                assetName: name,
                fileSize: fileSize,
                sha256: sha256
            )
        )
    }

    public static func snooze(_ release: AppRelease) {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        LocalStorage.shared.ignoredUpdateTag = release.tagName
        LocalStorage.shared.updateSnoozedUntil = nowMs + snoozeDurationMs
    }

    private static func isSnoozed(_ tagName: String) -> Bool {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        return LocalStorage.shared.ignoredUpdateTag == tagName && nowMs < LocalStorage.shared.updateSnoozedUntil
    }
}
