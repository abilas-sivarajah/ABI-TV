import SwiftUI

public struct CastActionButton: View {
    public let onClick: () -> Void
    @ObservedObject private var castManager = IosCastManager.shared

    public init(onClick: @escaping () -> Void) {
        self.onClick = onClick
    }

    public var body: some View {
        Button(action: onClick) {
            Image(systemName: castManager.connectedDevice != nil ? "tv.badge.wifi.fill" : "tv.badge.wifi")
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(castManager.connectedDevice != nil ? AbiTheme.accentCyan : AbiTheme.textPrimary)
                .frame(width: 36, height: 36)
        }
    }
}

public struct MobileAnimeCard: View {
    public let anime: AnimeCard
    public let width: CGFloat
    public let isBookmarked: Bool
    public let onClick: () -> Void

    public init(
        anime: AnimeCard,
        width: CGFloat = 130,
        isBookmarked: Bool = false,
        onClick: @escaping () -> Void
    ) {
        self.anime = anime
        self.width = width
        self.isBookmarked = isBookmarked
        self.onClick = onClick
    }

    public var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 5) {
                ZStack(alignment: .topTrailing) {
                    ZStack(alignment: .bottom) {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(AbiTheme.bgElevated)

                        if let url = URL(string: anime.coverUrl), !anime.coverUrl.isEmpty {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .scaledToFill()
                                default:
                                    Color.clear
                                }
                            }
                        }

                        LinearGradient(
                            colors: [.clear, .black.opacity(0.8)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .frame(height: 48)
                    }
                    .frame(width: width, height: width * 1.5)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.white.opacity(0.08), lineWidth: 0.8)
                    )

                    if isBookmarked {
                        Text("★")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(AbiTheme.accentBlue)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                            .padding(5)
                    }
                }

                Text(anime.title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(AbiTheme.textPrimary)
                    .lineLimit(1)
                    .frame(width: width, alignment: .leading)

                if !anime.genreOrSubtitle.isEmpty {
                    Text(anime.genreOrSubtitle)
                        .font(.system(size: 10))
                        .foregroundColor(AbiTheme.textMuted)
                        .lineLimit(1)
                        .frame(width: width, alignment: .leading)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

public struct MobileContinueCard: View {
    public let entry: WatchProgressEntry
    public let onClick: () -> Void

    public init(entry: WatchProgressEntry, onClick: @escaping () -> Void) {
        self.entry = entry
        self.onClick = onClick
    }

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(AbiTheme.bgElevated)
                    if let url = URL(string: entry.coverUrl), !entry.coverUrl.isEmpty {
                        AsyncImage(url: url) { phase in
                            if case .success(let img) = phase {
                                img.resizable().scaledToFill()
                            }
                        }
                    }
                }
                .frame(width: 50, height: 68)
                .clipShape(RoundedRectangle(cornerRadius: 6))

                VStack(alignment: .leading, spacing: 6) {
                    Text(entry.animeTitle)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(AbiTheme.textPrimary)
                        .lineLimit(1)

                    let sText = entry.seasonLabel.caseInsensitiveCompare("Filme") == .orderedSame
                        ? "Film"
                        : "S\(entry.seasonLabel) E\(entry.episodeNumber)"
                    Text("\(sText) • \(entry.episodeTitle)")
                        .font(.system(size: 11))
                        .foregroundColor(AbiTheme.textSecondary)
                        .lineLimit(1)

                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule()
                                .fill(Color.white.opacity(0.15))
                                .frame(height: 3)
                            Capsule()
                                .fill(AbiTheme.accentCyan)
                                .frame(width: geo.size.width * max(0.05, entry.progressFraction), height: 3)
                        }
                    }
                    .frame(height: 3)
                }
            }
            .padding(8)
            .frame(width: 260, height: 84)
            .background(AbiTheme.bgSurface)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

public struct MiniRemoteBar: View {
    @ObservedObject private var castManager = IosCastManager.shared

    public init() {}

    public var body: some View {
        if let connected = castManager.connectedDevice,
           let s = castManager.tvPlaybackStatus,
           !s.title.isEmpty {
            let isLive = s.seasonLabel == "LIVE"
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(AbiTheme.accentCyan.opacity(0.18))
                            .frame(width: 34, height: 34)
                        Image(systemName: "tv")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(AbiTheme.accentCyan)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(s.title)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(AbiTheme.textPrimary)
                            .lineLimit(1)

                        let epText: String = {
                            if isLive {
                                return "🔴 Live auf \(connected.name)"
                            } else if !s.episodeTitle.isEmpty {
                                return "S\(s.seasonLabel) E\(s.episodeNumber): \(s.episodeTitle)"
                            } else {
                                return "Läuft auf \(connected.name)"
                            }
                        }()
                        Text(epText)
                            .font(.system(size: 11))
                            .foregroundColor(AbiTheme.textSecondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    HStack(spacing: 6) {
                        if !isLive {
                            Button {
                                castManager.controlTv("seek_by", arg: -10_000)
                            } label: {
                                Image(systemName: "gobackward.10")
                                    .font(.system(size: 18))
                                    .foregroundColor(AbiTheme.textPrimary)
                                    .frame(width: 34, height: 34)
                            }
                        }

                        Button {
                            castManager.controlTv("toggle")
                        } label: {
                            Image(systemName: s.isPlaying ? "pause.fill" : "play.fill")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(AbiTheme.onAccent)
                                .frame(width: 38, height: 38)
                                .background(AbiTheme.accentCyan)
                                .clipShape(Circle())
                        }

                        if !isLive {
                            Button {
                                castManager.controlTv("seek_by", arg: 10_000)
                            } label: {
                                Image(systemName: "goforward.10")
                                    .font(.system(size: 18))
                                    .foregroundColor(AbiTheme.textPrimary)
                                    .frame(width: 34, height: 34)
                            }
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)

                if !isLive {
                    let progress = s.durationMs > 0
                        ? min(max(Double(s.positionMs) / Double(s.durationMs), 0.0), 1.0)
                        : 0.0
                    ProgressView(value: progress)
                        .tint(AbiTheme.accentCyan)
                }
            }
            .background(AbiTheme.bgSurface)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(AbiTheme.accentCyan.opacity(0.35), lineWidth: 1)
            )
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }
}

public struct CastBottomSheet: View {
    public let onDismiss: () -> Void
    @ObservedObject private var castManager = IosCastManager.shared
    @State private var manualIp = ""
    @State private var showManualInput = false
    @State private var pairingCode = ""
    @State private var pendingConnectId: String?

    public init(onDismiss: @escaping () -> Void) {
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let pairingState = castManager.pairing {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Mit \(pairingState.device.name) koppeln")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(AbiTheme.textPrimary)

                            Text("Am Fernseher erscheint oben rechts ein 6-stelliger Code. Öffne ABI TV auf dem Fernseher und gib den Code hier ein.")
                                .font(.system(size: 12))
                                .foregroundColor(AbiTheme.textSecondary)

                            HStack(spacing: 10) {
                                TextField("123456", text: $pairingCode)
                                    .keyboardType(.numberPad)
                                    .padding(10)
                                    .background(AbiTheme.bgSurface)
                                    .foregroundColor(AbiTheme.textPrimary)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                                    .onChange(of: pairingCode) { val in
                                        pairingCode = String(val.filter { $0.isNumber }.prefix(6))
                                    }

                                Button {
                                    castManager.submitPairingCode(pairingCode)
                                } label: {
                                    Text(pairingState.isBusy ? "..." : "Koppeln")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(AbiTheme.onAccent)
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 10)
                                        .background(AbiTheme.accentCyan)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                                .disabled(pairingCode.count != 6 || pairingState.isBusy)
                            }

                            if let err = pairingState.error {
                                Text(err)
                                    .font(.system(size: 12))
                                    .foregroundColor(AbiTheme.errorRed)
                            }

                            HStack {
                                Spacer()
                                Button("Abbrechen") {
                                    castManager.cancelPairing()
                                }
                                .foregroundColor(AbiTheme.textSecondary)

                                Button("Neuen Code anfordern") {
                                    castManager.requestPairing(pairingState.device)
                                }
                                .foregroundColor(AbiTheme.accentCyan)
                                .disabled(pairingState.isBusy)
                            }
                            .font(.system(size: 13))
                        }
                        .padding(14)
                        .background(AbiTheme.bgElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(AbiTheme.accentCyan, lineWidth: 1.5)
                        )
                    }

                    if let dev = castManager.connectedDevice {
                        HStack {
                            Image(systemName: "tv.fill")
                                .foregroundColor(AbiTheme.onAccent)
                                .frame(width: 36, height: 36)
                                .background(AbiTheme.accentCyan)
                                .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 2) {
                                HStack(spacing: 6) {
                                    Text(dev.name)
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(AbiTheme.textPrimary)
                                    Circle()
                                        .fill(AbiTheme.accentEmerald)
                                        .frame(width: 8, height: 8)
                                }
                                Text("\(dev.hostAddress):\(dev.port)")
                                    .font(.system(size: 12))
                                    .foregroundColor(AbiTheme.textSecondary)
                            }

                            Spacer()

                            Button("Trennen") {
                                castManager.disconnect()
                            }
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(AbiTheme.errorRed)
                        }
                        .padding(14)
                        .background(AbiTheme.accentCyan.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(AbiTheme.accentCyan, lineWidth: 1.5)
                        )
                    }

                    HStack {
                        Text("VERFÜGBARE GERÄTE IM WLAN")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(AbiTheme.textMuted)
                        Spacer()
                        Button {
                            castManager.startDiscovery()
                        } label: {
                            if castManager.isSearching {
                                ProgressView()
                                    .tint(AbiTheme.accentCyan)
                            } else {
                                Image(systemName: "arrow.clockwise")
                                    .foregroundColor(AbiTheme.accentCyan)
                            }
                        }
                        .disabled(castManager.isSearching)
                    }

                    if castManager.discoveredDevices.isEmpty {
                        VStack(spacing: 6) {
                            Text(castManager.isSearching ? "Suche nach ABI TV im WLAN..." : "Kein Fernseher gefunden")
                                .font(.system(size: 14))
                                .foregroundColor(AbiTheme.textSecondary)
                            Text("Stelle sicher, dass ABI TV auf dem Fire TV Stick oder PC geöffnet ist und beide Geräte im selben WLAN sind.")
                                .font(.system(size: 12))
                                .foregroundColor(AbiTheme.textMuted)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(20)
                        .background(AbiTheme.bgElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    } else {
                        VStack(spacing: 8) {
                            ForEach(castManager.discoveredDevices) { dev in
                                let isCurrent = castManager.connectedDevice?.id == dev.id
                                Button {
                                    pendingConnectId = dev.id
                                    castManager.connect(dev)
                                } label: {
                                    HStack {
                                        Image(systemName: "tv")
                                            .foregroundColor(AbiTheme.accentCyan)
                                            .font(.system(size: 20))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(dev.name)
                                                .font(.system(size: 14, weight: .semibold))
                                                .foregroundColor(AbiTheme.textPrimary)
                                            Text(dev.hostAddress)
                                                .font(.system(size: 12))
                                                .foregroundColor(AbiTheme.textMuted)
                                        }
                                        Spacer()
                                        if isCurrent {
                                            Image(systemName: "checkmark")
                                                .foregroundColor(AbiTheme.accentCyan)
                                        }
                                    }
                                    .padding(12)
                                    .background(isCurrent ? AbiTheme.accentCyan.opacity(0.15) : AbiTheme.bgElevated)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    if !showManualInput {
                        Button("+ TV-IP-Adresse manuell eingeben") {
                            showManualInput = true
                        }
                        .font(.system(size: 13))
                        .foregroundColor(AbiTheme.accentCyan)
                        .frame(maxWidth: .infinity)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("IP-Adresse des Fire TV Sticks / PCs:")
                                .font(.system(size: 12))
                                .foregroundColor(AbiTheme.textSecondary)
                            HStack(spacing: 10) {
                                TextField("z. B. 192.168.178.70", text: $manualIp)
                                    .keyboardType(.numbersAndPunctuation)
                                    .padding(10)
                                    .background(AbiTheme.bgSurface)
                                    .foregroundColor(AbiTheme.textPrimary)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                Button("OK") {
                                    let trimmed = manualIp.trimmingCharacters(in: .whitespacesAndNewlines)
                                    guard !trimmed.isEmpty else { return }
                                    let dev = CastDevice(name: "Fire TV", hostAddress: trimmed, port: 8765)
                                    pendingConnectId = dev.id
                                    castManager.connect(dev)
                                }
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(AbiTheme.onAccent)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(AbiTheme.accentCyan)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                        .padding(14)
                        .background(AbiTheme.bgElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
                .padding(20)
            }
            .background(AbiTheme.bgSurface.ignoresSafeArea())
            .navigationTitle("Mit Fernseher verbinden")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Fertig") {
                        castManager.cancelPairing()
                        onDismiss()
                    }
                    .foregroundColor(AbiTheme.accentCyan)
                }
            }
            .onChange(of: castManager.connectedDevice?.id) { newId in
                if let pending = pendingConnectId, newId == pending {
                    onDismiss()
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

public struct MobileUpdateSheet: View {
    public let release: AppRelease
    public let onDismiss: () -> Void
    @Environment(\.openURL) private var openURL

    public init(release: AppRelease, onDismiss: @escaping () -> Void) {
        self.release = release
        self.onDismiss = onDismiss
    }

    public var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("🚀 Neues Update verfügbar!")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(AbiTheme.textPrimary)

                    let sizeMb = release.fileSize > 0
                        ? String(format: " · %.1f MB", Double(release.fileSize) / (1024.0 * 1024.0))
                        : ""
                    Text("Version \(release.versionName)\(sizeMb) · installiert: \(UpdateManager.getCurrentVersionName())")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(AbiTheme.accentCyan)
                }

                Text("Was ist neu:")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(AbiTheme.textSecondary)

                ScrollView {
                    Text(release.changelog)
                        .font(.system(size: 13))
                        .foregroundColor(AbiTheme.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                }
                .frame(maxHeight: 220)
                .background(AbiTheme.bgSurface)
                .clipShape(RoundedRectangle(cornerRadius: 10))

                VStack(spacing: 10) {
                    if let url = URL(string: release.downloadUrl) {
                        Button {
                            openURL(url)
                        } label: {
                            Text("⬇️ ABI_iOS.ipa herunterladen")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(AbiTheme.onAccent)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(AbiTheme.accentCyan)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    }

                    HStack(spacing: 10) {
                        if let sideStoreUrl = release.sideStoreInstallUrl {
                            Button {
                                openURL(sideStoreUrl)
                            } label: {
                                Text("In SideStore öffnen")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(AbiTheme.textPrimary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(AbiTheme.bgSurface)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }

                        if let altStoreUrl = release.altStoreInstallUrl {
                            Button {
                                openURL(altStoreUrl)
                            } label: {
                                Text("In AltStore öffnen")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(AbiTheme.textPrimary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(AbiTheme.bgSurface)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                        }
                    }

                    Button {
                        UpdateManager.snooze(release)
                        onDismiss()
                    } label: {
                        Text("Später erinnern")
                            .font(.system(size: 13))
                            .foregroundColor(AbiTheme.textSecondary)
                            .padding(.top, 4)
                    }
                }
                Spacer()
            }
            .padding(20)
            .background(AbiTheme.bgElevated.ignoresSafeArea())
        }
        .preferredColorScheme(.dark)
    }
}
