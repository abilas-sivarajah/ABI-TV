import SwiftUI
import AVKit
import AVFoundation

public struct ActivePlaybackItem: Identifiable, Equatable {
    public let id = UUID()
    public let animeSlug: String
    public let seasonLabel: String
    public let episodeNumber: Int
    public let episodePath: String
    public let language: LanguageOption
    public let title: String
    public let episodeTitle: String
    public let coverUrl: String
    public let liveChannel: LiveTvChannel?

    public var isLiveStream: Bool { liveChannel != nil }

    public init(
        animeSlug: String,
        seasonLabel: String,
        episodeNumber: Int,
        episodePath: String,
        language: LanguageOption,
        title: String,
        episodeTitle: String,
        coverUrl: String,
        liveChannel: LiveTvChannel? = nil
    ) {
        self.animeSlug = animeSlug
        self.seasonLabel = seasonLabel
        self.episodeNumber = episodeNumber
        self.episodePath = episodePath
        self.language = language
        self.title = title
        self.episodeTitle = episodeTitle
        self.coverUrl = coverUrl
        self.liveChannel = liveChannel
    }
}

public struct PlayerView: View {
    public let item: ActivePlaybackItem
    public var onBack: () -> Void
    public var onOpenCastSheet: () -> Void

    @ObservedObject private var storage = LocalStorage.shared
    @ObservedObject private var castManager = IosCastManager.shared

    @State private var isLoading: Bool = true
    @State private var statusText: String = "Lade Hoster..."
    @State private var errorMessage: String? = nil

    @State private var resolvedStream: ResolvedStream? = nil
    @State private var activeRedirectPath: String = ""
    @State private var failedRedirectPaths: Set<String> = []
    @State private var liveRetryCount: Int = 0
    @State private var reloadTrigger: Int = 0

    @State private var player: AVPlayer? = nil
    @State private var timeObserverToken: Any? = nil
    @State private var statusObserver: NSKeyValueObservation? = nil
    @State private var didInitialSeek: Bool = false

    public init(
        item: ActivePlaybackItem,
        onBack: @escaping () -> Void,
        onOpenCastSheet: @escaping () -> Void
    ) {
        self.item = item
        self.onBack = onBack
        self.onOpenCastSheet = onOpenCastSheet
    }

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let player = player, errorMessage == nil {
                SystemVideoPlayerRepresentable(player: player)
                    .ignoresSafeArea()
            }

            if isLoading && errorMessage == nil {
                VStack(spacing: 14) {
                    ProgressView()
                        .tint(AbiTheme.accentCyan)
                        .scaleEffect(1.3)
                    Text(statusText)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(AbiTheme.textPrimary)
                    Text("\(item.title) • \(item.episodeTitle)")
                        .font(.system(size: 12))
                        .foregroundColor(AbiTheme.textSecondary)
                }
                .padding(24)
                .background(AbiTheme.bgSurface.opacity(0.9))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            if let errorMessage = errorMessage {
                VStack(spacing: 12) {
                    Text("⚠️ Stream-Fehler")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(AbiTheme.errorRed)

                    Text(errorMessage)
                        .font(.system(size: 13))
                        .foregroundColor(AbiTheme.textSecondary)
                        .multilineTextAlignment(.center)

                    HStack(spacing: 12) {
                        Button(action: {
                            saveProgressAndClose()
                        }) {
                            Text("Zurück")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(AbiTheme.textPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(AbiTheme.bgElevated)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .buttonStyle(.plain)

                        Button(action: {
                            failedRedirectPaths.removeAll()
                            liveRetryCount = 0
                            reloadTrigger += 1
                        }) {
                            Text("Erneut versuchen")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(AbiTheme.onAccent)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(AbiTheme.accentCyan)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 4)
                }
                .padding(24)
                .background(AbiTheme.bgSurface)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .padding(24)
            }

            VStack {
                HStack(spacing: 12) {
                    Button {
                        saveProgressAndClose()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 14, weight: .bold))
                            Text("Zurück")
                                .font(.system(size: 13, weight: .semibold))
                        }
                        .foregroundColor(AbiTheme.textPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.65))
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(AbiTheme.textPrimary)
                            .lineLimit(1)

                        HStack(spacing: 6) {
                            if item.isLiveStream {
                                Text("● LIVE")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(AbiTheme.liveRed)
                            }
                            Text(resolvedStream != nil ? "\(item.episodeTitle) • \(resolvedStream!.hosterName)" : item.episodeTitle)
                                .font(.system(size: 11))
                                .foregroundColor(AbiTheme.textSecondary)
                                .lineLimit(1)
                        }
                    }

                    Spacer()

                    Button {
                        handOffToTv()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "tv")
                                .font(.system(size: 12, weight: .bold))
                            Text("Auf TV")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(AbiTheme.accentCyan)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.65))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(AbiTheme.accentCyan.opacity(0.6), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)

                Spacer()
            }
        }
        .task(id: reloadTrigger) {
            await resolveAndStartPlayback()
        }
        .onDisappear {
            cleanupPlayer()
        }
    }

    private func handOffToTv() {
        player?.pause()
        guard castManager.connectedDevice != nil else {
            castManager.startDiscovery()
            onOpenCastSheet()
            return
        }
        Task {
            let ok: Bool
            if let channel = item.liveChannel {
                ok = await castManager.playLiveOnTv(channel)
            } else {
                ok = await castManager.playOnTv(
                    animeSlug: item.animeSlug,
                    animeTitle: item.title,
                    coverUrl: item.coverUrl,
                    episodePath: item.episodePath,
                    episodeNumber: item.episodeNumber,
                    seasonLabel: item.seasonLabel,
                    titleDe: item.episodeTitle,
                    preferredLangKey: item.language.langKey
                )
            }
            if ok {
                await MainActor.run {
                    saveProgressAndClose()
                }
            }
        }
    }

    private func resolveAndStartPlayback() async {
        await MainActor.run {
            isLoading = true
            errorMessage = nil
        }

        if let channel = item.liveChannel {
            let direct = ResolvedStream(
                streamUrl: channel.streamUrl,
                isHls: LiveTvRepository.isLikelyHls(channel.streamUrl),
                hosterName: "Live TV",
                language: .gerDub,
                headers: channel.headers
            )
            await MainActor.run {
                self.resolvedStream = direct
                self.activeRedirectPath = ""
                self.statusText = "Verbinde Live-Stream..."
                startAvPlayer(with: direct)
            }
            return
        }

        do {
            await MainActor.run {
                statusText = "Suche verfügbare Hoster..."
            }
            let hosters: [HosterLink]
            if FilmpalastScraper.isFilmpalastSlug(item.animeSlug) || FilmpalastScraper.isFilmpalastEpisodePath(item.episodePath) {
                hosters = await FilmpalastScraper.fetchHostersForFilm(item.episodePath)
            } else if SerienStreamScraper.isStoSlug(item.animeSlug) || item.episodePath.hasPrefix("sto_") {
                hosters = try await SerienStreamScraper.fetchEpisodeHosters(item.episodePath)
            } else if HdFilmeScraper.isHdFilmeSlug(item.animeSlug) || item.episodePath.hasPrefix("hdfilme_") {
                hosters = await HdFilmeScraper.fetchHostersForEpisode(item.episodePath)
            } else {
                hosters = try await AniWorldScraper.fetchEpisodeHosters(item.episodePath)
            }

            if hosters.isEmpty {
                await MainActor.run {
                    errorMessage = "Keine Streams für diese Episode gefunden."
                    isLoading = false
                }
                return
            }

            let candidates = hosters.filter { !failedRedirectPaths.contains($0.redirectPath) }
            let sortedCandidates = candidates.sorted { a, b in
                let aPref = (a.language == item.language) ? 0 : 1
                let bPref = (b.language == item.language) ? 0 : 1
                return aPref < bPref
            }

            if sortedCandidates.isEmpty {
                await MainActor.run {
                    errorMessage = "Alle verfügbaren Hoster (\(hosters.count)) konnten nicht geladen werden."
                    isLoading = false
                }
                return
            }

            for candidate in sortedCandidates {
                await MainActor.run {
                    statusText = "Extrahiere Stream von \(candidate.hosterName)..."
                }
                if let stream = try? await StreamResolver.resolveHoster(candidate) {
                    await MainActor.run {
                        self.resolvedStream = stream
                        self.activeRedirectPath = candidate.redirectPath
                        self.statusText = "Verbinde mit \(stream.hosterName)..."
                        startAvPlayer(with: stream)
                    }
                    return
                } else {
                    failedRedirectPaths.insert(candidate.redirectPath)
                }
            }

            await MainActor.run {
                let triedNames = sortedCandidates.map(\.hosterName).uniqued().joined(separator: ", ")
                errorMessage = "Stream konnte von keinem Hoster (\(triedNames)) aufgelöst werden."
                isLoading = false
            }
        } catch {
            await MainActor.run {
                errorMessage = "Fehler: \(error.localizedDescription)"
                isLoading = false
            }
        }
    }

    @MainActor
    private func startAvPlayer(with stream: ResolvedStream) {
        cleanupPlayer()

        guard let url = URL(string: stream.streamUrl) else {
            handlePlaybackFailure(for: stream, reason: "Ungültige Stream-URL")
            return
        }

        #if os(iOS)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        #endif

        var assetOptions: [String: Any] = [:]
        if !stream.headers.isEmpty {
            assetOptions["AVURLAssetHTTPHeaderFieldsKey"] = stream.headers
        }
        let asset = AVURLAsset(url: url, options: assetOptions)
        let playerItem = AVPlayerItem(asset: asset)
        let avPlayer = AVPlayer(playerItem: playerItem)
        avPlayer.automaticallyWaitsToMinimizeStalling = true
        didInitialSeek = false

        let savedPosMs: Int64 = item.isLiveStream ? 0 : storage.getSavedPositionForEpisode(item.episodePath)

        statusObserver = playerItem.observe(\.status, options: [.new, .initial]) { observedItem, _ in
            DispatchQueue.main.async {
                switch observedItem.status {
                case .readyToPlay:
                    self.isLoading = false
                    self.liveRetryCount = 0
                    if !self.didInitialSeek {
                        self.didInitialSeek = true
                        if savedPosMs > 3000 {
                            let targetTime = CMTime(seconds: Double(savedPosMs) / 1000.0, preferredTimescale: 600)
                            avPlayer.seek(to: targetTime, toleranceBefore: .zero, toleranceAfter: .zero) { _ in
                                avPlayer.play()
                            }
                        } else {
                            avPlayer.play()
                        }
                    }
                case .failed:
                    let reason = observedItem.error?.localizedDescription ?? "Unbekannter Wiedergabefehler"
                    self.handlePlaybackFailure(for: stream, reason: reason)
                default:
                    break
                }
            }
        }

        if !item.isLiveStream {
            let interval = CMTime(seconds: 5.0, preferredTimescale: 600)
            timeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
                let posSec = CMTimeGetSeconds(time)
                let durSec = CMTimeGetSeconds(avPlayer.currentItem?.duration ?? .zero)
                if posSec.isFinite && durSec.isFinite && posSec > 0 && durSec > 0 {
                    let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                    storage.saveWatchProgress(
                        WatchProgressEntry(
                            animeSlug: item.animeSlug,
                            animeTitle: item.title,
                            coverUrl: item.coverUrl,
                            seasonLabel: item.seasonLabel,
                            episodeNumber: item.episodeNumber,
                            episodeTitle: item.episodeTitle,
                            episodePath: item.episodePath,
                            preferredLangKey: item.language.langKey,
                            positionMs: Int64(posSec * 1000),
                            durationMs: Int64(durSec * 1000),
                            updatedAt: nowMs
                        )
                    )
                }
            }
        }

        self.player = avPlayer
        avPlayer.play()
    }

    @MainActor
    private func handlePlaybackFailure(for stream: ResolvedStream, reason: String) {
        if item.isLiveStream {
            if liveRetryCount < 3 {
                liveRetryCount += 1
                statusText = "Signal unterbrochen – verbinde neu (\(liveRetryCount)/3)..."
                isLoading = true
                Task {
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    await MainActor.run {
                        reloadTrigger += 1
                    }
                }
                return
            }
            errorMessage = "Live-Stream nicht erreichbar (\(reason))."
            isLoading = false
        } else if !activeRedirectPath.isEmpty {
            StreamResolver.invalidateCache(activeRedirectPath)
            failedRedirectPaths.insert(activeRedirectPath)
            statusText = "\(stream.hosterName) fehlgeschlagen – wechsle Hoster..."
            isLoading = true
            reloadTrigger += 1
        } else {
            errorMessage = "Wiedergabe fehlgeschlagen: \(reason)"
            isLoading = false
        }
    }

    private func saveProgressAndClose() {
        if !item.isLiveStream, let avPlayer = player, let currentItem = avPlayer.currentItem {
            let posSec = CMTimeGetSeconds(avPlayer.currentTime())
            let durSec = CMTimeGetSeconds(currentItem.duration)
            if posSec.isFinite && durSec.isFinite && posSec > 0 && durSec > 0 {
                let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                storage.saveWatchProgress(
                    WatchProgressEntry(
                        animeSlug: item.animeSlug,
                        animeTitle: item.title,
                        coverUrl: item.coverUrl,
                        seasonLabel: item.seasonLabel,
                        episodeNumber: item.episodeNumber,
                        episodeTitle: item.episodeTitle,
                        episodePath: item.episodePath,
                        preferredLangKey: item.language.langKey,
                        positionMs: Int64(posSec * 1000),
                        durationMs: Int64(durSec * 1000),
                        updatedAt: nowMs
                    )
                )
            }
        }
        cleanupPlayer()
        onBack()
    }

    private func cleanupPlayer() {
        statusObserver?.invalidate()
        statusObserver = nil
        if let token = timeObserverToken, let avPlayer = player {
            avPlayer.removeTimeObserver(token)
        }
        timeObserverToken = nil
        player?.pause()
        player = nil
    }
}

private extension Array where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

#if canImport(UIKit)
private struct SystemVideoPlayerRepresentable: UIViewControllerRepresentable {
    let player: AVPlayer

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.showsPlaybackControls = true
        controller.allowsPictureInPicturePlayback = true
        controller.videoGravity = .resizeAspect
        return controller
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        if uiViewController.player !== player {
            uiViewController.player = player
        }
    }
}
#else
private struct SystemVideoPlayerRepresentable: View {
    let player: AVPlayer
    var body: some View {
        VideoPlayer(player: player)
    }
}
#endif
