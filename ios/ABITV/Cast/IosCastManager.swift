import Foundation
import Combine
import Darwin
#if canImport(UIKit)
import UIKit
#endif

public struct CastDevice: Codable, Hashable, Identifiable, Sendable {
    public let name: String
    public let hostAddress: String
    public var port: Int = 8765
    public var model: String = ""
    public var tvId: String = ""
    public var `protocol`: Int = 0

    public var id: String { "\(hostAddress):\(port)" }

    public init(
        name: String,
        hostAddress: String,
        port: Int = 8765,
        model: String = "",
        tvId: String = "",
        protocolVersion: Int = 0
    ) {
        self.name = name
        self.hostAddress = hostAddress
        self.port = port
        self.model = model
        self.tvId = tvId
        self.protocol = protocolVersion
    }
}

public struct TvPlaybackStatus: Equatable, Sendable {
    public var isPlaying: Bool = false
    public var title: String = ""
    public var episodeTitle: String = ""
    public var seasonLabel: String = ""
    public var episodeNumber: Int = 0
    public var positionMs: Int64 = 0
    public var durationMs: Int64 = 0

    public init(
        isPlaying: Bool = false,
        title: String = "",
        episodeTitle: String = "",
        seasonLabel: String = "",
        episodeNumber: Int = 0,
        positionMs: Int64 = 0,
        durationMs: Int64 = 0
    ) {
        self.isPlaying = isPlaying
        self.title = title
        self.episodeTitle = episodeTitle
        self.seasonLabel = seasonLabel
        self.episodeNumber = episodeNumber
        self.positionMs = positionMs
        self.durationMs = durationMs
    }
}

public struct PairingUiState: Equatable, Sendable {
    public let device: CastDevice
    public var isBusy: Bool = false
    public var error: String?

    public init(device: CastDevice, isBusy: Bool = false, error: String? = nil) {
        self.device = device
        self.isBusy = isBusy
        self.error = error
    }
}

/// Steuert die Verbindung zwischen iPhone/iPad und dem ABI-TV-Empfänger (Fire TV / Android TV / PC).
/// Entspricht 1:1 `MobileCastManager.kt` auf Android.
@MainActor
public final class IosCastManager: ObservableObject {
    public static let shared = IosCastManager()

    public nonisolated static let defaultPort = 8765
    public nonisolated static let udpDiscoveryPort: UInt16 = 8766
    public nonisolated static let protocolLiveTv = 3

    private static let prefsSuite = "aniworld_mobile_cast"
    private static let keyLastIp = "last_tv_ip"
    /// Hat der Nutzer selbst "Trennen" gewählt? Dann nicht von allein wieder verbinden.
    private static let keyManuallyDisconnected = "cast_manually_disconnected"
    private static let keyLastName = "last_tv_name"
    private static let keyLastPort = "last_tv_port"
    private static let keyLastId = "last_tv_id"
    private static let keyLastProtocol = "last_tv_protocol"
    private static let keyTokenPrefix = "token_"

    @Published public private(set) var discoveredDevices: [CastDevice] = []
    @Published public private(set) var connectedDevice: CastDevice?
    @Published public private(set) var tvPlaybackStatus: TvPlaybackStatus?
    @Published public private(set) var isSearching: Bool = false
    @Published public private(set) var pairing: PairingUiState?
    @Published public var bannerMessage: String?

    private let prefs: UserDefaults
    private let fastSession: URLSession
    private let probeSession: URLSession
    private var statusPollTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?

    private var clientName: String {
        #if canImport(UIKit)
        let name = UIDevice.current.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? "iPhone" : name
        #else
        return "iPhone"
        #endif
    }

    private init() {
        self.prefs = UserDefaults(suiteName: Self.prefsSuite) ?? .standard

        let fastCfg = URLSessionConfiguration.ephemeral
        fastCfg.timeoutIntervalForRequest = 2.5
        fastCfg.timeoutIntervalForResource = 2.5
        self.fastSession = URLSession(configuration: fastCfg)

        let probeCfg = URLSessionConfiguration.ephemeral
        probeCfg.timeoutIntervalForRequest = 0.65
        probeCfg.timeoutIntervalForResource = 0.65
        self.probeSession = URLSession(configuration: probeCfg)
    }

    public func initialize() {
        if let lastIp = prefs.string(forKey: Self.keyLastIp), !lastIp.isEmpty {
            let port = prefs.object(forKey: Self.keyLastPort) != nil ? prefs.integer(forKey: Self.keyLastPort) : Self.defaultPort
            let dev = CastDevice(
                name: prefs.string(forKey: Self.keyLastName) ?? "Fire TV Stick",
                hostAddress: lastIp,
                port: port,
                tvId: prefs.string(forKey: Self.keyLastId) ?? "",
                protocolVersion: prefs.integer(forKey: Self.keyLastProtocol)
            )
            if isUsable(dev) {
                connectedDevice = dev
                addDiscovered(dev)
                startStatusPolling(dev)
            }
        }
        startDiscovery(autoConnect: true)
    }

    public func startDiscovery(autoConnect: Bool = true) {
        searchTask?.cancel()
        isSearching = true

        searchTask = Task { [weak self] in
            guard let self else { return }
            if let saved = self.connectedDevice {
                _ = await self.checkHttpDevice(ip: saved.hostAddress, port: saved.port)
            }

            async let udpTask: Void = self.sendUdpBroadcasts()
            async let subnetTask: Void = self.scanLocalSubnet()
            _ = await (udpTask, subnetTask)

            if !Task.isCancelled {
                self.isSearching = false
                // Nach "Trennen" nur verbinden, wenn der Nutzer selbst ein Gerät auswählt
                let manuallyDisconnected = self.prefs.bool(forKey: Self.keyManuallyDisconnected)
                if autoConnect && !manuallyDisconnected && self.connectedDevice == nil {
                    if let usable = self.discoveredDevices.first(where: { self.isUsable($0) }) {
                        self.finishConnect(usable)
                    }
                }
            }
        }
    }

    public func connect(_ device: CastDevice) {
        // Bewusst gewählt: automatisches Verbinden ist wieder erlaubt
        prefs.set(false, forKey: Self.keyManuallyDisconnected)
        Task {
            guard let info = await probe(device) else {
                showMessage("\(device.name) antwortet nicht. Ist ABI TV auf dem Fernseher geöffnet?")
                return
            }
            if info.protocol >= 2, tokenFor(info) != nil, !(await isTokenAccepted(info)) {
                prefs.removeObject(forKey: Self.keyTokenPrefix + info.tvId)
            }
            if isUsable(info) {
                finishConnect(info)
            } else {
                requestPairing(info)
            }
        }
    }

    public func requestPairing(_ device: CastDevice) {
        pairing = PairingUiState(device: device, isBusy: true)
        let name = clientName
        Task {
            guard let url = URL(string: "http://\(device.hostAddress):\(device.port)/pair/start") else { return }
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try? JSONSerialization.data(withJSONObject: ["clientName": name])

            do {
                let (_, resp) = try await fastSession.data(for: req)
                let code = (resp as? HTTPURLResponse)?.statusCode ?? 500
                if (200..<300).contains(code) {
                    pairing = PairingUiState(device: device)
                } else if code == 429 {
                    pairing = PairingUiState(
                        device: device,
                        error: "Zu viele falsche Codes. Bitte 30 Sekunden warten und dann einen neuen Code anfordern."
                    )
                } else {
                    pairing = PairingUiState(device: device, error: "Der Fernseher hat die Kopplung abgelehnt (\(code)).")
                }
            } catch {
                pairing = PairingUiState(device: device, error: "Fernseher nicht erreichbar.")
            }
        }
    }

    public func submitPairingCode(_ code: String) {
        guard let state = pairing else { return }
        let device = state.device
        pairing = PairingUiState(device: device, isBusy: true, error: nil)
        let digits = code.filter { $0.isNumber }
        let name = clientName

        Task {
            guard let url = URL(string: "http://\(device.hostAddress):\(device.port)/pair") else { return }
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try? JSONSerialization.data(withJSONObject: ["code": digits, "clientName": name])

            do {
                let (data, resp) = try await fastSession.data(for: req)
                let status = (resp as? HTTPURLResponse)?.statusCode ?? 500
                let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
                if (200..<300).contains(status), let obj {
                    let token = (obj["token"] as? String) ?? ""
                    let tvId = (obj["tvId"] as? String) ?? device.tvId
                    guard !token.isEmpty, !tvId.isEmpty else {
                        pairing = PairingUiState(device: device, error: "Ungültige Antwort vom Fernseher.")
                        return
                    }
                    prefs.set(token, forKey: Self.keyTokenPrefix + tvId)
                    pairing = nil
                    var updated = device
                    updated.tvId = tvId
                    updated.protocol = max(device.protocol, 2)
                    finishConnect(updated)
                    return
                }
                let errText: String
                switch status {
                case 403:
                    let left = (obj?["attemptsLeft"] as? Int) ?? 0
                    errText = "Falscher Code. Noch \(left) \(left == 1 ? "Versuch" : "Versuche")."
                case 410:
                    errText = "Der Code ist abgelaufen. Fordere einen neuen an."
                case 429:
                    errText = "Zu viele falsche Codes. Bitte 30 Sekunden warten."
                default:
                    errText = "Kopplung fehlgeschlagen (\(status))."
                }
                pairing = PairingUiState(device: device, error: errText)
            } catch {
                pairing = PairingUiState(device: device, error: "Fernseher nicht erreichbar.")
            }
        }
    }

    public func cancelPairing() {
        pairing = nil
    }

    public func disconnect() {
        prefs.set(true, forKey: Self.keyManuallyDisconnected)
        connectedDevice = nil
        tvPlaybackStatus = nil
        statusPollTask?.cancel()
        prefs.removeObject(forKey: Self.keyLastIp)
        prefs.removeObject(forKey: Self.keyLastName)
        prefs.removeObject(forKey: Self.keyLastPort)
        prefs.removeObject(forKey: Self.keyLastId)
        prefs.removeObject(forKey: Self.keyLastProtocol)
        showMessage("Verbindung zum TV getrennt")
    }

    public func playLiveOnTv(_ channel: LiveTvChannel) async -> Bool {
        guard var device = connectedDevice else {
            showMessage("Bitte zuerst mit dem Fernseher verbinden.")
            return false
        }

        if device.protocol < Self.protocolLiveTv {
            if let fresh = await probe(device), fresh.protocol >= Self.protocolLiveTv {
                device.protocol = fresh.protocol
                connectedDevice = device
                prefs.set(device.protocol, forKey: Self.keyLastProtocol)
            } else {
                showMessage("Live TV braucht auf dem Fernseher ABI TV 1.0.4 oder neuer.")
                return false
            }
        }

        let payload: [String: Any] = [
            "type": "live",
            "channelName": channel.name,
            "streamUrl": channel.streamUrl,
            "logoUrl": channel.logoUrl,
            "headers": channel.headers
        ]
        guard var req = authorizedRequest(device: device, path: "/play"),
              let body = try? JSONSerialization.data(withJSONObject: payload) else {
            return false
        }
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body

        do {
            let (_, resp) = try await fastSession.data(for: req)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 500
            if (200..<300).contains(code) {
                showMessage("\(channel.name) läuft auf \(device.name)")
                startStatusPolling(device)
                return true
            } else if code == 401 {
                onUnauthorized(device)
                return false
            } else {
                showMessage("Der Fernseher konnte den Sender nicht starten (\(code)).")
                return false
            }
        } catch {
            showMessage("Fehler beim Senden an den TV: \(error.localizedDescription)")
            return false
        }
    }

    public func playOnTv(
        animeSlug: String,
        animeTitle: String,
        coverUrl: String,
        episodePath: String,
        episodeNumber: Int,
        seasonLabel: String,
        titleDe: String,
        preferredLangKey: Int
    ) async -> Bool {
        var device = connectedDevice
        // Nach "Trennen" nicht selbst irgendein gekoppeltes Gerät (z. B. den PC) nehmen – der Nutzer wählt
        if device == nil && !prefs.bool(forKey: Self.keyManuallyDisconnected) {
            device = discoveredDevices.first(where: { isUsable($0) })
        }
        if device == nil && !prefs.bool(forKey: Self.keyManuallyDisconnected) {
            await scanLocalSubnet()
            device = discoveredDevices.first(where: { isUsable($0) })
        }
        guard let target = device else {
            showMessage("Bitte zuerst mit dem Fernseher verbinden.")
            return false
        }
        if connectedDevice?.id != target.id {
            finishConnect(target)
        }

        showMessage("Wird auf \(target.name) abgespielt...")

        let payload: [String: Any] = [
            "animeSlug": animeSlug,
            "animeTitle": animeTitle,
            "coverUrl": coverUrl,
            "episodePath": episodePath,
            "episodeNumber": episodeNumber,
            "seasonLabel": seasonLabel,
            "titleDe": titleDe,
            "preferredLangKey": preferredLangKey
        ]
        guard var req = authorizedRequest(device: target, path: "/play"),
              let body = try? JSONSerialization.data(withJSONObject: payload) else {
            return false
        }
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body

        do {
            let (_, resp) = try await fastSession.data(for: req)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 500
            if (200..<300).contains(code) {
                startStatusPolling(target)
                return true
            } else if code == 401 {
                onUnauthorized(target)
                return false
            }
            return false
        } catch {
            showMessage("Fehler beim Senden an den TV: \(error.localizedDescription)")
            return false
        }
    }

    public func controlTv(_ action: String, arg: Int64 = 0) {
        guard let device = connectedDevice else { return }
        Task {
            let payload: [String: Any] = ["action": action, "arg": arg]
            guard var req = authorizedRequest(device: device, path: "/control"),
                  let body = try? JSONSerialization.data(withJSONObject: payload) else {
                return
            }
            req.httpMethod = "POST"
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = body
            if let (_, resp) = try? await fastSession.data(for: req),
               (resp as? HTTPURLResponse)?.statusCode == 401 {
                onUnauthorized(device)
            }
        }
    }

    private func finishConnect(_ device: CastDevice) {
        connectedDevice = device
        addDiscovered(device)
        prefs.set(device.hostAddress, forKey: Self.keyLastIp)
        prefs.set(device.name, forKey: Self.keyLastName)
        prefs.set(device.port, forKey: Self.keyLastPort)
        prefs.set(device.tvId, forKey: Self.keyLastId)
        prefs.set(device.protocol, forKey: Self.keyLastProtocol)
        startStatusPolling(device)
        showMessage("Mit \(device.name) verbunden")
    }

    private func onUnauthorized(_ device: CastDevice) {
        if !device.tvId.isEmpty {
            prefs.removeObject(forKey: Self.keyTokenPrefix + device.tvId)
        }
        if connectedDevice?.id == device.id {
            connectedDevice = nil
            tvPlaybackStatus = nil
            statusPollTask?.cancel()
        }
        showMessage("Die Kopplung mit \(device.name) wurde aufgehoben. Bitte neu verbinden.")
    }

    private func isUsable(_ device: CastDevice) -> Bool {
        device.protocol == 1 || (device.protocol >= 2 && tokenFor(device) != nil)
    }

    private func tokenFor(_ device: CastDevice) -> String? {
        guard !device.tvId.isEmpty else { return nil }
        return prefs.string(forKey: Self.keyTokenPrefix + device.tvId)
    }

    private func authorizedRequest(device: CastDevice, path: String) -> URLRequest? {
        guard let url = URL(string: "http://\(device.hostAddress):\(device.port)\(path)") else { return nil }
        var req = URLRequest(url: url)
        if let token = tokenFor(device) {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return req
    }

    private func showMessage(_ text: String) {
        bannerMessage = text
        Task {
            try? await Task.sleep(nanoseconds: 2_800_000_000)
            if bannerMessage == text {
                bannerMessage = nil
            }
        }
    }

    private func startStatusPolling(_ device: CastDevice) {
        statusPollTask?.cancel()
        statusPollTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled && self.connectedDevice?.id == device.id {
                if let req = self.authorizedRequest(device: device, path: "/status"),
                   let (data, resp) = try? await self.fastSession.data(for: req) {
                    let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
                    if code == 401 {
                        self.onUnauthorized(device)
                        break
                    } else if (200..<300).contains(code),
                              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] {
                        self.tvPlaybackStatus = TvPlaybackStatus(
                            isPlaying: (obj["isPlaying"] as? Bool) ?? false,
                            title: (obj["title"] as? String) ?? "",
                            episodeTitle: (obj["episodeTitle"] as? String) ?? "",
                            seasonLabel: (obj["seasonLabel"] as? String) ?? "",
                            episodeNumber: (obj["episodeNumber"] as? Int) ?? 0,
                            positionMs: (obj["positionMs"] as? NSNumber)?.int64Value ?? 0,
                            durationMs: (obj["durationMs"] as? NSNumber)?.int64Value ?? 0
                        )
                    }
                }
                try? await Task.sleep(nanoseconds: 1_200_000_000)
            }
        }
    }

    private func sendUdpBroadcasts() async {
        let localIps = Self.localIPv4Addresses()
        var broadcastIps: Set<String> = ["255.255.255.255"]
        for ip in localIps {
            let parts = ip.components(separatedBy: ".")
            if parts.count == 4 {
                broadcastIps.insert("\(parts[0]).\(parts[1]).\(parts[2]).255")
            }
        }
        let targets = Array(broadcastIps)

        let discoveredPongs = await Task.detached(priority: .utility) { () -> [(ip: String, port: Int, name: String, model: String)] in
            var results: [(ip: String, port: Int, name: String, model: String)] = []
            let fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
            guard fd >= 0 else { return results }
            defer { close(fd) }

            var broadcastEnable: Int32 = 1
            setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &broadcastEnable, socklen_t(MemoryLayout<Int32>.size))

            var tv = timeval(tv_sec: 1, tv_usec: 500_000)
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, socklen_t(MemoryLayout<timeval>.size))

            let msg = Array("ANIWORLD_DISCOVER".utf8)
            for target in targets {
                var addr = sockaddr_in()
                addr.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
                addr.sin_family = sa_family_t(AF_INET)
                addr.sin_port = IosCastManager.udpDiscoveryPort.bigEndian
                inet_pton(AF_INET, target, &addr.sin_addr)

                msg.withUnsafeBytes { ptr in
                    withUnsafePointer(to: &addr) { addrPtr in
                        addrPtr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                            _ = sendto(fd, ptr.baseAddress, ptr.count, 0, saPtr, socklen_t(MemoryLayout<sockaddr_in>.size))
                        }
                    }
                }
            }

            let deadline = Date().addingTimeInterval(2.2)
            var buffer = [UInt8](repeating: 0, count: 512)
            while Date() < deadline {
                var senderAddr = sockaddr_in()
                var senderLen = socklen_t(MemoryLayout<sockaddr_in>.size)
                let recvLen = withUnsafeMutablePointer(to: &senderAddr) { addrPtr in
                    addrPtr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saPtr in
                        recvfrom(fd, &buffer, buffer.count, 0, saPtr, &senderLen)
                    }
                }
                if recvLen <= 0 { break }
                let text = String(bytes: buffer.prefix(recvLen), encoding: .utf8)?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if text.hasPrefix("ANIWORLD_PONG") {
                    var ipBuffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                    var sinAddr = senderAddr.sin_addr
                    inet_ntop(AF_INET, &sinAddr, &ipBuffer, socklen_t(INET_ADDRSTRLEN))
                    let ip = String(cString: ipBuffer)
                    let parts = text.components(separatedBy: ":")
                    let port = parts.count > 1 ? (Int(parts[1]) ?? IosCastManager.defaultPort) : IosCastManager.defaultPort
                    let name = parts.count > 2 ? parts[2] : "Fire TV Stick"
                    let model = parts.count > 3 ? parts[3] : ""
                    if !ip.isEmpty {
                        results.append((ip: ip, port: port, name: name, model: model))
                    }
                }
            }
            return results
        }.value

        for pong in discoveredPongs {
            if !(await checkHttpDevice(ip: pong.ip, port: pong.port)) {
                addDiscovered(CastDevice(name: pong.name, hostAddress: pong.ip, port: pong.port, model: pong.model))
            }
        }
    }

    private func scanLocalSubnet() async {
        let localIps = Self.localIPv4Addresses()
        for localIp in localIps {
            let parts = localIp.components(separatedBy: ".")
            guard parts.count == 4 else { continue }
            let prefix = "\(parts[0]).\(parts[1]).\(parts[2])."

            let ipRange = (1...254).sorted { a, b in
                func rank(_ n: Int) -> Int {
                    if n == 70 { return 0 }
                    if (20...100).contains(n) { return 1 }
                    return 2
                }
                return rank(a) < rank(b)
            }

            let chunkSize = 32
            for start in stride(from: 0, to: ipRange.count, by: chunkSize) {
                if Task.isCancelled { return }
                let chunk = ipRange[start..<min(start + chunkSize, ipRange.count)]
                await withTaskGroup(of: Void.self) { group in
                    for hostNum in chunk {
                        let testIp = "\(prefix)\(hostNum)"
                        group.addTask {
                            _ = await self.checkHttpDevice(ip: testIp, port: Self.defaultPort)
                        }
                    }
                }
            }
        }
    }

    @discardableResult
    public func checkHttpDevice(ip: String, port: Int = defaultPort) async -> Bool {
        guard let device = await probe(CastDevice(name: "Fire TV Stick", hostAddress: ip, port: port)) else {
            return false
        }
        addDiscovered(device)
        return true
    }

    private func probe(_ device: CastDevice) async -> CastDevice? {
        guard let url = URL(string: "http://\(device.hostAddress):\(device.port)/ping") else { return nil }
        guard let (data, resp) = try? await probeSession.data(from: url),
              let http = resp as? HTTPURLResponse,
              (200..<300).contains(http.statusCode),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              (obj["service"] as? String) == "aniworld_tv_cast" else {
            return nil
        }
        return CastDevice(
            name: (obj["name"] as? String) ?? device.name,
            hostAddress: device.hostAddress,
            port: device.port,
            model: (obj["model"] as? String) ?? "",
            tvId: (obj["tvId"] as? String) ?? "",
            protocolVersion: (obj["version"] as? Int) ?? 1
        )
    }

    private func isTokenAccepted(_ device: CastDevice) async -> Bool {
        guard let req = authorizedRequest(device: device, path: "/ping"),
              let (data, _) = try? await probeSession.data(for: req),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return true
        }
        return (obj["paired"] as? Bool) != false
    }

    private func addDiscovered(_ device: CastDevice) {
        if let idx = discoveredDevices.firstIndex(where: { $0.id == device.id }) {
            if device.protocol > 0 && discoveredDevices[idx] != device {
                discoveredDevices[idx] = device
            }
        } else {
            discoveredDevices.append(device)
        }
    }

    private nonisolated static func localIPv4Addresses() -> [String] {
        var addresses: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return addresses }
        defer { freeifaddrs(ifaddr) }

        var ptr: UnsafeMutablePointer<ifaddrs>? = firstAddr
        while let current = ptr {
            let interface = current.pointee
            let flags = Int32(interface.ifa_flags)
            let isUp = (flags & IFF_UP) != 0
            let isLoopback = (flags & IFF_LOOPBACK) != 0
            // Mobilfunk: Dort steht kein Fernseher, und der Subnetz-Scan träfe fremde Geräte beim Netzbetreiber
            let isCellular = String(cString: interface.ifa_name).hasPrefix("pdp_ip")

            if isUp && !isLoopback && !isCellular, let addr = interface.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET) {
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                if getnameinfo(
                    addr,
                    socklen_t(addr.pointee.sa_len),
                    &hostname,
                    socklen_t(hostname.count),
                    nil,
                    0,
                    NI_NUMERICHOST
                ) == 0 {
                    let ip = String(cString: hostname)
                    if !ip.hasPrefix("127.") && !ip.hasPrefix("169.254.") {
                        addresses.append(ip)
                    }
                }
            }
            ptr = interface.ifa_next
        }
        return addresses
    }
}
