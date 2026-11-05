import SwiftUI
import UIKit

/// Lädt Bilder (Cover, Titelbilder, Senderlogos) über den Netzwerk-Client der App und merkt sie sich.
/// Ersetzt `AsyncImage`: Das bricht Ladevorgänge beim Scrollen ab, versucht es nie erneut und
/// verwirft Adressen mit Leerzeichen oder Umlauten – dann fehlten Bilder.
actor ImageLoader {
    static let shared = ImageLoader()

    private static let memory: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 400
        cache.totalCostLimit = 120 * 1024 * 1024
        return cache
    }()

    private var running: [String: Task<UIImage?, Never>] = [:]

    /// Schon geladenes Bild (ohne Netz), damit beim Wiederauftauchen nichts aufblitzt.
    nonisolated static func cached(_ raw: String?) -> UIImage? {
        guard let key = normalized(raw) else { return nil }
        return memory.object(forKey: key as NSString)
    }

    func load(_ raw: String?) async -> UIImage? {
        guard let key = Self.normalized(raw) else { return nil }
        if let hit = Self.memory.object(forKey: key as NSString) { return hit }
        if let task = running[key] { return await task.value }
        // Eigener Task: Verschwindet die Karte beim Scrollen, lädt das Bild trotzdem fertig
        let task = Task<UIImage?, Never> { await Self.fetch(key) }
        running[key] = task
        let image = await task.value
        running[key] = nil
        if let image {
            let cost = Int(image.size.width * image.size.height * image.scale * image.scale * 4)
            Self.memory.setObject(image, forKey: key as NSString, cost: cost)
        }
        return image
    }

    private nonisolated static func normalized(_ raw: String?) -> String? {
        guard var value = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
        if value.hasPrefix("//") { value = "https:" + value }
        guard value.lowercased().hasPrefix("http") else { return nil }
        return value
    }

    /// Bis zu drei Versuche; manche Bildserver antworten nur mit passendem Referer.
    private static func fetch(_ address: String) async -> UIImage? {
        guard let url = HttpClient.rewriteUrl(address) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8,*/*;q=0.5", forHTTPHeaderField: "Accept")
        if let scheme = url.scheme, let host = url.host {
            request.setValue("\(scheme)://\(host)/", forHTTPHeaderField: "Referer")
        }
        for attempt in 0..<3 {
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: UInt64(attempt) * 800_000_000)
            }
            guard let (data, response) = try? await HttpClient.perform(request) else { continue }
            let status = (response as? HTTPURLResponse)?.statusCode ?? 200
            if status == 404 || status == 410 { return nil }
            guard (200..<300).contains(status), let image = UIImage(data: data) else { continue }
            return image.preparingForDisplay() ?? image
        }
        return nil
    }
}

/// Bild aus dem Netz; `content` bekommt nil, solange es lädt oder wenn es fehlt.
struct CachedImage<Content: View>: View {
    let url: String?
    @ViewBuilder let content: (Image?) -> Content

    /// Geladenes Bild samt Adresse: Wechselt die Adresse, wird das alte Bild nicht mehr gezeigt.
    @State private var loaded: (url: String, image: UIImage)?

    var body: some View {
        let own = loaded.flatMap { $0.url == url ? $0.image : nil }
        let shown = own ?? ImageLoader.cached(url)
        content(shown.map { Image(uiImage: $0) })
            .task(id: url) {
                guard let url, ImageLoader.cached(url) == nil else { return }
                if let image = await ImageLoader.shared.load(url) {
                    loaded = (url, image)
                }
            }
    }
}
