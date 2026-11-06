import SwiftUI
import AVKit
import AVFoundation
import Combine

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

/// Player im Streaming-Stil (wie auf PC und TV): eigene Bedienelemente, Sprache und Folgen im Player,
/// "Intro überspringen", "Nächste Folge" und eine Pausen-Ansicht. Hoster sind nicht mehr sichtbar.
public struct PlayerView: View {
    public let item: ActivePlaybackItem
    public var onBack: () -> Void
    public var onOpenCastSheet: () -> Void
    /// Andere Folge oder Sprache: der Player startet mit diesem Auftrag neu.
    public var onSwitch: (ActivePlaybackItem) -> Void

    @ObservedObject private var storage = LocalStorage.shared
    @ObservedObject private var castManager = IosCastManager.shared

    @State private var isLoading: Bool = true
    @State private var statusText: String = "Starte …"
    @State private var errorMessage: String? = nil

    @State private var resolvedStream: ResolvedStream? = nil
    @State private var activeRedirectPath: String = ""
    @State private var failedRedirectPaths: Set<String> = []
    @State private var liveRetryCount: Int = 0
    @State private var reloadTrigger: Int = 0
    @State private var hosterLanguages: [LanguageOption] = []

    @State private var player: AVPlayer? = nil
    /// VLC für IPTV (MPEG-TS) und MKV/AVI; `usesVlc` sagt, welcher Player gerade läuft.
    @State private var vlc = VlcPlayback()
    @State private var usesVlc = false
    @State private var forceVlc = false
    @State private var vlcStartedAt = Date()
    @State private var timeObserverToken: Any? = nil
    @State private var uiTimeObserverToken: Any? = nil
    @State private var statusObserver: NSKeyValueObservation? = nil
    @State private var endObserver: NSObjectProtocol? = nil
    @State private var didInitialSeek: Bool = false

    // Bedienung
    @State private var controlsVisible = true
    @State private var lastInteraction = Date()
    @State private var now = Date()
    @State private var isPlaying = false
    @State private var currentSec: Double = 0
    @State private var durationSec: Double = 0
    @State private var scrubValue: Double? = nil
    @State private var introSkipped = false
    @State private var showEpisodes = false
    @State private var showLanguages = false

    // Staffeln und Folgen der Serie
    @State private var seasons: [SeasonLink] = []
    @State private var seasonEpisodes: [EpisodeItem] = []
    @State private var titleDescription = ""

    private let ticker = Timer.publish(every: 0.5, on: .main, in: .common).autoconnect()

    public init(
        item: ActivePlaybackItem,
        onBack: @escaping () -> Void,
        onOpenCastSheet: @escaping () -> Void,
        onSwitch: @escaping (ActivePlaybackItem) -> Void = { _ in }
    ) {
        self.item = item
        self.onBack = onBack
        self.onOpenCastSheet = onOpenCastSheet
        self.onSwitch = onSwitch
    }

    // MARK: - Ableitungen

    private var isSeries: Bool { !item.isLiveStream && (seasonEpisodes.count > 1 || seasons.count > 1) }

    private var currentSeason: SeasonLink? { seasons.first { $0.label == item.seasonLabel } }

    private var nextEpisode: EpisodeItem? {
        guard let index = seasonEpisodes.firstIndex(where: { $0.path == item.episodePath }),
              index + 1 < seasonEpisodes.count else { return nil }
        return seasonEpisodes[index + 1]
    }

    private var nextSeason: SeasonLink? {
        guard nextEpisode == nil, let current = currentSeason, let index = seasons.firstIndex(of: current) else { return nil }
        return seasons.dropFirst(index + 1).first { !$0.isMovies }
    }

    private var videoReady: Bool { (player != nil || usesVlc) && !isLoading && errorMessage == nil }

    /// Spulen geht bei Folgen/Filmen und bei Filmen aus IPTV-Listen (VLC kennt dann die Länge).
    private var seekable: Bool { !item.isLiveStream || (usesVlc && durationSec > 0) }

    /// "Intro überspringen" in den ersten 5 Minuten, "Nächste Folge" in den letzten 100 Sekunden.
    private var showIntroSkip: Bool {
        isSeries && videoReady && durationSec > 600 && currentSec >= 5 && currentSec <= 300 && !introSkipped
    }

    private var showNextUp: Bool {
        isSeries && videoReady && durationSec > 600 && durationSec - currentSec <= 100 && (nextEpisode != nil || nextSeason != nil)
    }

    /// Pausen-Ansicht nach 2,5 s Pause ohne Eingabe.
    private var showPauseInfo: Bool {
        videoReady && !isPlaying && now.timeIntervalSince(lastInteraction) > 2.5 && !showEpisodes && !showLanguages
    }

    // MARK: - Oberfläche

    public var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if usesVlc, errorMessage == nil {
                VlcVideoView(playback: vlc)
                    .ignoresSafeArea()
            } else if let player = player, errorMessage == nil {
                PlayerLayerView(player: player)
                    .ignoresSafeArea()
            }

            // Tippen aufs Bild blendet die Bedienung ein/aus
            Color.clear
                .contentShape(Rectangle())
                .ignoresSafeArea()
                .onTapGesture {
                    if showPauseInfo {
                        lastInteraction = Date()
                        controlsVisible = true
                    } else {
                        controlsVisible.toggle()
                        lastInteraction = Date()
                    }
                }

            if isLoading && errorMessage == nil {
                VStack(spacing: 14) {
                    ProgressView()
                        .tint(AbiTheme.accentCyan)
                        .scaleEffect(1.3)
                    Text(statusText)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(AbiTheme.textPrimary)
                }
                .padding(24)
                .background(AbiTheme.bgSurface.opacity(0.9))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            if showPauseInfo {
                pauseInfo
                    .transition(.opacity)
                    .onTapGesture {
                        lastInteraction = Date()
                        if usesVlc { vlc.resume() } else { player?.play() }
                    }
            }

            if controlsVisible && !showPauseInfo && errorMessage == nil {
                controlsOverlay
                    .transition(.opacity)
            }

            if (showIntroSkip || showNextUp) && !showPauseInfo {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        overlayButton
                    }
                }
                .padding(.trailing, 20)
                .padding(.bottom, controlsVisible ? 120 : 40)
            }

            if let errorMessage = errorMessage {
                errorCard(errorMessage)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: controlsVisible)
        .animation(.easeInOut(duration: 0.5), value: showPauseInfo)
        .statusBarHidden(!controlsVisible)
        .task(id: reloadTrigger) {
            await resolveAndStartPlayback()
        }
        .task { await loadSeasons() }
        .onReceive(ticker) { date in
            now = date
            if usesVlc { pollVlc(date) }
            // Bedienung nach 4 Sekunden ohne Eingabe ausblenden, solange abgespielt wird
            if controlsVisible && isPlaying && !showEpisodes && !showLanguages && date.timeIntervalSince(lastInteraction) > 4 {
                controlsVisible = false
            }
        }
        .onDisappear {
            cleanupPlayer()
        }
        .sheet(isPresented: $showEpisodes) {
            EpisodesSheet(
                title: item.title,
                seasons: seasons,
                currentSeason: currentSeason,
                currentEpisodes: seasonEpisodes,
                currentPath: item.episodePath,
                loadSeason: { season in await fetchSeasonEpisodes(season) },
                onClose: { showEpisodes = false },
                onPick: { episode, episodes in
                    showEpisodes = false
                    switchTo(episode: episode)
                    _ = episodes
                }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showLanguages) {
            LanguageSheet(
                languages: hosterLanguages,
                active: resolvedStream?.language,
                onClose: { showLanguages = false },
                onPick: { language in
                    showLanguages = false
                    switchLanguage(language)
                }
            )
            .presentationDetents([.medium])
        }
    }

    private var controlsOverlay: some View {
        ZStack {
            LinearGradient(
                stops: [
                    .init(color: Color.black.opacity(0.7), location: 0),
                    .init(color: .clear, location: 0.3),
                    .init(color: .clear, location: 0.6),
                    .init(color: Color.black.opacity(0.85), location: 1)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            .allowsHitTesting(false)

            VStack(spacing: 0) {
                // Oben: zurück, Titel, auf dem TV abspielen
                HStack(spacing: 12) {
                    Button { saveProgressAndClose() } label: {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 48, height: 48)
                            // Ohne das reagiert ein schlichter Knopf nur auf das gezeichnete Symbol
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.isLiveStream ? "Live TV" : item.title)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Text(episodeLine)
                            .font(.system(size: 12))
                            .foregroundColor(Color.white.opacity(0.8))
                            .lineLimit(1)
                    }

                    Spacer()

                    Button { handOffToTv() } label: {
                        Image(systemName: "tv")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 48, height: 48)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)
                .padding(.top, 6)

                Spacer()

                // Mitte: 10 s zurück, Play/Pause, 10 s vor
                HStack(spacing: 44) {
                    if seekable {
                        centerButton("gobackward.10", size: 30) { seek(by: -10) }
                    }
                    centerButton(isPlaying ? "pause.fill" : "play.fill", size: 44) { togglePause() }
                    if seekable {
                        centerButton("goforward.10", size: 30) { seek(by: 10) }
                    }
                }

                Spacer()

                // Unten: Fortschritt mit Restzeit, darunter Folgen, Sprache, nächste Folge
                VStack(spacing: 6) {
                    if !seekable {
                        HStack(spacing: 6) {
                            Circle().fill(AbiTheme.liveRed).frame(width: 9, height: 9)
                            Text("LIVE").font(.system(size: 13, weight: .bold)).foregroundColor(AbiTheme.liveRed)
                            Spacer()
                        }
                    } else {
                        HStack(spacing: 10) {
                            Slider(
                                value: Binding(
                                    get: { scrubValue ?? currentSec },
                                    set: { scrubValue = $0; lastInteraction = Date() }
                                ),
                                in: 0...max(durationSec, 1),
                                onEditingChanged: { editing in
                                    if !editing, let target = scrubValue {
                                        seek(to: target)
                                        scrubValue = nil
                                    }
                                }
                            )
                            .tint(AbiTheme.accentCyan)
                            .disabled(durationSec <= 0)
                            Text(durationSec > 0 ? formatTime(durationSec - (scrubValue ?? currentSec)) : "--:--")
                                .font(.system(size: 13, weight: .semibold).monospacedDigit())
                                .foregroundColor(.white)
                        }
                    }

                    HStack(spacing: 22) {
                        if isSeries {
                            bottomButton("rectangle.stack", "Folgen") { showEpisodes = true }
                        }
                        if !item.isLiveStream && !hosterLanguages.isEmpty {
                            bottomButton("captions.bubble", "Sprache") { showLanguages = true }
                        }
                        Spacer()
                        if let next = nextEpisode {
                            bottomButton("forward.end", "Nächste Folge") { switchTo(episode: next) }
                        } else if nextSeason != nil {
                            bottomButton("forward.end", "Nächste Staffel") { playNextSeason() }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 10)
            }
        }
    }

    private var episodeLine: String {
        if item.isLiveStream { return item.title }
        if !isSeries { return "Film" }
        let season = item.seasonLabel.lowercased() == "filme" ? "Filme & Specials" : "St. \(item.seasonLabel)"
        let name = item.episodeTitle.hasPrefix("Folge ") ? "" : "  \(item.episodeTitle)"
        return "\(season) · Flg. \(item.episodeNumber)\(name)"
    }

    private func centerButton(_ icon: String, size: CGFloat, action: @escaping () -> Void) -> some View {
        Button {
            action()
            lastInteraction = Date()
        } label: {
            Image(systemName: icon)
                .font(.system(size: size, weight: .bold))
                .foregroundColor(.white)
                .frame(width: size + 30, height: size + 30)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func bottomButton(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button {
            action()
            lastInteraction = Date()
        } label: {
            HStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 15, weight: .semibold))
                Text(label).font(.system(size: 13, weight: .semibold))
            }
            .foregroundColor(.white)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var overlayButton: some View {
        Button {
            if showNextUp {
                if let next = nextEpisode { switchTo(episode: next) } else { playNextSeason() }
            } else {
                seek(by: 85)
                introSkipped = true
            }
            lastInteraction = Date()
        } label: {
            HStack(spacing: 8) {
                Image(systemName: showNextUp ? "forward.end.fill" : "forward.fill")
                Text(showNextUp ? (nextEpisode != nil ? "Nächste Folge" : "Nächste Staffel") : "Intro überspringen")
            }
            .font(.system(size: 15, weight: .bold))
            .foregroundColor(.black)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    private var pauseInfo: some View {
        ZStack(alignment: .bottomTrailing) {
            LinearGradient(
                colors: [Color.black.opacity(0.85), Color.black.opacity(0.5)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 6) {
                Spacer()
                Text("Sie sehen gerade")
                    .font(.system(size: 13))
                    .foregroundColor(AbiTheme.textSecondary)
                Text(item.isLiveStream ? "Live TV" : item.title)
                    .font(.system(size: 30, weight: .heavy))
                    .foregroundColor(.white)
                    .lineLimit(2)
                if isSeries {
                    Text(item.seasonLabel.lowercased() == "filme" ? "Filme & Specials" : "Staffel \(item.seasonLabel)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    Text(item.episodeTitle.hasPrefix("Folge ") || item.episodeTitle.isEmpty
                         ? "Folge \(item.episodeNumber)"
                         : "\(item.episodeTitle): Flg. \(item.episodeNumber)")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.top, 8)
                        .lineLimit(2)
                }
                if !titleDescription.isEmpty && !item.isLiveStream {
                    Text(titleDescription)
                        .font(.system(size: 13))
                        .foregroundColor(Color.white.opacity(0.85))
                        .lineLimit(4)
                }
                Spacer()
            }
            .padding(.horizontal, 28)
            .frame(maxWidth: .infinity, alignment: .leading)

            Text("Angehalten")
                .font(.system(size: 14))
                .foregroundColor(Color.white.opacity(0.85))
                .padding(.trailing, 24)
                .padding(.bottom, 20)
        }
    }

    private func errorCard(_ message: String) -> some View {
        VStack(spacing: 12) {
            Text("Wiedergabefehler")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(AbiTheme.accentCyan)
            Text(message)
                .font(.system(size: 13))
                .foregroundColor(AbiTheme.textSecondary)
                .multilineTextAlignment(.center)
            HStack(spacing: 12) {
                Button { saveProgressAndClose() } label: {
                    Text("Zurück")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(AbiTheme.textPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(AbiTheme.bgElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                // Formate, die das iPhone nicht kann (MKV, AVI): auf Fernseher oder PC weiterschicken
                if let channel = item.liveChannel, LiveTvRepository.unsupportedOnIos(channel.streamUrl) != nil {
                    Button { handOffToTv() } label: {
                        Text("Auf TV")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(AbiTheme.onAccent)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)
                            .background(AbiTheme.accentCyan)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                } else {
                Button {
                    failedRedirectPaths.removeAll()
                    liveRetryCount = 0
                    reloadTrigger += 1
                } label: {
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
            }
            .padding(.top, 4)
        }
        .padding(24)
        .background(AbiTheme.bgSurface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(24)
    }

    // MARK: - Steuerung

    private func togglePause() {
        if usesVlc {
            if vlc.isPlaying { vlc.pause() } else { vlc.resume() }
            isPlaying = !isPlaying
            return
        }
        guard let player else { return }
        if player.timeControlStatus == .paused { player.play() } else { player.pause() }
    }

    private func seek(by seconds: Double) {
        guard seekable else { return }
        let position = usesVlc ? vlc.currentSec : (player.map { CMTimeGetSeconds($0.currentTime()) } ?? 0)
        let target = max(0, position + seconds)
        seek(to: durationSec > 0 ? min(target, durationSec) : target)
    }

    private func seek(to seconds: Double) {
        if usesVlc {
            vlc.seek(to: seconds)
        } else {
            player?.seek(to: CMTime(seconds: seconds, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        }
        currentSec = seconds
    }

    /// Zustand des VLC-Players übernehmen (alle 0,5 s): Start, Zeit, Länge, Fehler.
    private func pollVlc(_ date: Date) {
        guard vlc.isActive else { return }
        if vlc.failed || (vlc.ended && item.isLiveStream) {
            if let stream = resolvedStream { handlePlaybackFailure(for: stream, reason: "VLC-Fehler") }
            return
        }
        if isLoading {
            if vlc.hasStarted {
                isLoading = false
                liveRetryCount = 0
            } else if date.timeIntervalSince(vlcStartedAt) > 25, let stream = resolvedStream {
                handlePlaybackFailure(for: stream, reason: "Zeitüberschreitung")
                return
            }
        }
        isPlaying = vlc.isPlaying
        let position = vlc.currentSec
        if position > 0 { currentSec = position }
        let length = vlc.durationSec
        if length > 0 { durationSec = length }
    }

    /// Andere Folge (auch aus einer anderen Staffel) in der gemerkten Sprache spielen.
    private func switchTo(episode: EpisodeItem) {
        saveCurrentProgress()
        onSwitch(ActivePlaybackItem(
            animeSlug: item.animeSlug,
            seasonLabel: episode.seasonLabel,
            episodeNumber: episode.number,
            episodePath: episode.path,
            language: storage.preferredLanguage,
            title: item.title,
            episodeTitle: episode.displayTitle,
            coverUrl: item.coverUrl
        ))
    }

    /// Sprache wechseln: an derselben Stelle weiter und als Standard für die nächsten Titel merken.
    private func switchLanguage(_ language: LanguageOption) {
        storage.preferredLanguage = language
        guard language != resolvedStream?.language else { return }
        saveCurrentProgress()
        onSwitch(ActivePlaybackItem(
            animeSlug: item.animeSlug,
            seasonLabel: item.seasonLabel,
            episodeNumber: item.episodeNumber,
            episodePath: item.episodePath,
            language: language,
            title: item.title,
            episodeTitle: item.episodeTitle,
            coverUrl: item.coverUrl
        ))
    }

    private func playNextSeason() {
        guard let season = nextSeason else { return }
        Task {
            let episodes = await fetchSeasonEpisodes(season)
            if let first = episodes.first {
                await MainActor.run { switchTo(episode: first) }
            }
        }
    }

    // MARK: - Staffeln

    private func loadSeasons() async {
        guard !item.isLiveStream else { return }
        let card = AnimeCard(slug: item.animeSlug, title: item.title, coverUrl: item.coverUrl)
        guard let detail = await TitlePreview.loadDetail(card) else { return }
        let current = detail.seasons.first { $0.label == item.seasonLabel }
        var episodes = detail.episodes
        if !episodes.contains(where: { $0.path == item.episodePath }), let current {
            episodes = await fetchSeasonEpisodes(current)
        }
        let loadedEpisodes = episodes
        await MainActor.run {
            seasons = detail.seasons
            titleDescription = detail.description
            seasonEpisodes = loadedEpisodes
        }
    }

    private func fetchSeasonEpisodes(_ season: SeasonLink) async -> [EpisodeItem] {
        let slug = item.animeSlug
        if SerienStreamScraper.isStoSlug(slug) {
            return (try? await SerienStreamScraper.fetchDetail(slug, requestedSeasonPath: season.path))?.episodes ?? []
        } else if HdFilmeScraper.isHdFilmeSlug(slug) {
            return (try? await HdFilmeScraper.fetchDetail(slug, seasonPathOverride: season.path))?.episodes ?? []
        } else if FilmpalastScraper.isFilmpalastSlug(slug) {
            return []
        }
        return (try? await AniWorldScraper.fetchAnimeDetail(slug, seasonPathOverride: season.path))?.episodes ?? []
    }

    // MARK: - Auf dem TV abspielen

    private func handOffToTv() {
        // Stelle merken, bevor das iPhone anhält: Fernseher/PC setzen genau dort fort
        let positionMs = usesVlc
            ? Int64(vlc.currentSec * 1000)
            : (player.map { Int64(max(0, CMTimeGetSeconds($0.currentTime())) * 1000) } ?? -1)
        let durationMs = Int64(max(0, durationSec) * 1000)
        saveCurrentProgress()
        player?.pause()
        vlc.pause()
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
                    preferredLangKey: item.language.langKey,
                    positionMs: positionMs,
                    durationMs: durationMs
                )
            }
            if ok {
                await MainActor.run {
                    saveProgressAndClose()
                }
            }
        }
    }

    // MARK: - Stream auflösen und abspielen

    private func resolveAndStartPlayback() async {
        await MainActor.run {
            isLoading = true
            errorMessage = nil
            statusText = "Starte …"
        }

        if let channel = item.liveChannel {
            // IPTV-Sender (rohes MPEG-TS), MKV/AVI und andere Dateien: VLC. HLS-Sender weiter mit AVPlayer.
            if forceVlc || LiveTvRepository.xtreamLiveParts(channel.streamUrl) != nil || !LiveTvRepository.isLikelyHls(channel.streamUrl) {
                let stream = ResolvedStream(
                    streamUrl: channel.streamUrl,
                    isHls: false,
                    hosterName: "Live TV",
                    language: .gerDub,
                    headers: channel.headers
                )
                await MainActor.run {
                    self.resolvedStream = stream
                    self.activeRedirectPath = ""
                    startVlc(with: stream)
                }
                return
            }
            // IPTV-Live (rohes MPEG-TS) erst als HLS versuchen, bei Fehlern abwechselnd die Originaladresse
            let candidates = LiveTvRepository.playbackCandidates(channel.streamUrl)
            let streamUrl = candidates[liveRetryCount % candidates.count]
            let direct = ResolvedStream(
                streamUrl: streamUrl,
                isHls: LiveTvRepository.isLikelyHls(streamUrl),
                hosterName: "Live TV",
                language: .gerDub,
                headers: channel.headers
            )
            await MainActor.run {
                self.resolvedStream = direct
                self.activeRedirectPath = ""
                startAvPlayer(with: direct)
            }
            return
        }

        do {
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

            // Player inzwischen geschlossen oder neu gestartet: nichts mehr anfassen
            if Task.isCancelled { return }

            let languages = Array(Set(hosters.map(\.language))).sorted { $0.rawValue < $1.rawValue }
            await MainActor.run { hosterLanguages = languages }

            if hosters.isEmpty {
                await MainActor.run {
                    errorMessage = "Keine Streams für diese Folge gefunden."
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

            for candidate in sortedCandidates {
                if Task.isCancelled { return }
                let resolved = try? await StreamResolver.resolveHoster(candidate)
                // Beim Schließen des Players schlagen laufende Anfragen fehl (kein Hoster-Fehler),
                // und ein spät aufgelöster Stream darf nicht mehr ohne sichtbaren Player losspielen.
                if Task.isCancelled { return }

                if let stream = resolved {
                    await MainActor.run {
                        self.resolvedStream = stream
                        self.activeRedirectPath = candidate.redirectPath
                        startAvPlayer(with: stream)
                    }
                    return
                } else {
                    failedRedirectPaths.insert(candidate.redirectPath)
                }
            }

            await MainActor.run {
                errorMessage = "Dieser Titel lässt sich gerade nicht abspielen. Bitte später erneut versuchen."
                isLoading = false
            }
        } catch {
            if Task.isCancelled { return }
            await MainActor.run {
                errorMessage = "Dieser Titel lässt sich gerade nicht abspielen. Bitte später erneut versuchen."
                isLoading = false
            }
        }
    }

    @MainActor
    private func startVlc(with stream: ResolvedStream) {
        cleanupPlayer()
        guard let url = URL(string: stream.streamUrl) ?? HttpClient.rewriteUrl(stream.streamUrl) else {
            handlePlaybackFailure(for: stream, reason: "Ungültige Stream-URL")
            return
        }
        #if os(iOS)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        try? AVAudioSession.sharedInstance().setActive(true)
        #endif
        usesVlc = true
        currentSec = 0
        durationSec = 0
        vlcStartedAt = Date()
        statusText = liveRetryCount > 0 ? statusText : "Verbinde …"
        vlc.play(url: url, headers: stream.headers, isLive: !LiveTvTree.isVod(stream.streamUrl))
        lastInteraction = Date()
        controlsVisible = true
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

        // Anzeige: Position, Dauer, läuft/angehalten
        uiTimeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { time in
            let pos = CMTimeGetSeconds(time)
            let dur = CMTimeGetSeconds(avPlayer.currentItem?.duration ?? .zero)
            let playing = avPlayer.timeControlStatus != .paused
            // Der Block ist @Sendable: Zustand des Views nur über die Hauptwarteschlange ändern
            DispatchQueue.main.async {
                if pos.isFinite { self.currentSec = pos }
                if dur.isFinite && dur > 0 { self.durationSec = dur }
                self.isPlaying = playing
            }
        }

        // Ende der Folge: als gesehen merken, nächste Folge bzw. Staffel starten
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { _ in
            DispatchQueue.main.async {
                guard !self.item.isLiveStream else { return }
                self.storage.markEpisodeWatched(self.item.episodePath)
                if let next = self.nextEpisode {
                    self.switchTo(episode: next)
                } else if self.nextSeason != nil {
                    self.playNextSeason()
                }
            }
        }

        if !item.isLiveStream {
            let interval = CMTime(seconds: 5.0, preferredTimescale: 600)
            // Lokale Kopien: Der Block ist @Sendable und darf nicht auf den MainActor-gebundenen View zugreifen
            let progressStore = storage
            let playback = item
            timeObserverToken = avPlayer.addPeriodicTimeObserver(forInterval: interval, queue: .main) { time in
                let posSec = CMTimeGetSeconds(time)
                let durSec = CMTimeGetSeconds(avPlayer.currentItem?.duration ?? .zero)
                if posSec.isFinite && durSec.isFinite && posSec > 0 && durSec > 0 {
                    let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
                    progressStore.saveWatchProgress(
                        WatchProgressEntry(
                            animeSlug: playback.animeSlug,
                            animeTitle: playback.title,
                            coverUrl: playback.coverUrl,
                            seasonLabel: playback.seasonLabel,
                            episodeNumber: playback.episodeNumber,
                            episodeTitle: playback.episodeTitle,
                            episodePath: playback.episodePath,
                            preferredLangKey: playback.language.langKey,
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
        lastInteraction = Date()
        controlsVisible = true
    }

    @MainActor
    private func handlePlaybackFailure(for stream: ResolvedStream, reason: String) {
        if item.isLiveStream {
            // AVPlayer kann den Sender nicht: einmal mit VLC versuchen
            if !usesVlc && !forceVlc {
                forceVlc = true
                statusText = "Starte mit VLC …"
                isLoading = true
                reloadTrigger += 1
                return
            }
            if liveRetryCount < 3 {
                liveRetryCount += 1
                statusText = "Verbinde neu (\(liveRetryCount)/3) …"
                isLoading = true
                Task {
                    try? await Task.sleep(nanoseconds: 1_500_000_000)
                    await MainActor.run {
                        reloadTrigger += 1
                    }
                }
                return
            }
            errorMessage = "Der Sender ist gerade nicht erreichbar."
            isLoading = false
        } else if !activeRedirectPath.isEmpty {
            StreamResolver.invalidateCache(activeRedirectPath)
            failedRedirectPaths.insert(activeRedirectPath)
            statusText = "Starte …"
            isLoading = true
            reloadTrigger += 1
        } else {
            errorMessage = "Wiedergabe fehlgeschlagen. Bitte erneut versuchen."
            isLoading = false
        }
    }

    private func saveCurrentProgress() {
        guard !item.isLiveStream, let avPlayer = player, let currentItem = avPlayer.currentItem else { return }
        let posSec = CMTimeGetSeconds(avPlayer.currentTime())
        let durSec = CMTimeGetSeconds(currentItem.duration)
        guard posSec.isFinite && durSec.isFinite && posSec > 0 && durSec > 0 else { return }
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
                updatedAt: Int64(Date().timeIntervalSince1970 * 1000)
            )
        )
    }

    private func saveProgressAndClose() {
        saveCurrentProgress()
        cleanupPlayer()
        onBack()
    }

    private func cleanupPlayer() {
        statusObserver?.invalidate()
        statusObserver = nil
        if let avPlayer = player {
            if let token = timeObserverToken { avPlayer.removeTimeObserver(token) }
            if let token = uiTimeObserverToken { avPlayer.removeTimeObserver(token) }
        }
        timeObserverToken = nil
        uiTimeObserverToken = nil
        if let observer = endObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        endObserver = nil
        player?.pause()
        player = nil
        vlc.stop()
        usesVlc = false
    }

    private func formatTime(_ seconds: Double) -> String {
        let total = max(0, Int(seconds))
        let h = total / 3600
        let m = (total % 3600) / 60
        let s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%02d:%02d", m, s)
    }
}

// MARK: - Folgen und Sprache

/// Folgen-Liste wie bei Netflix: oben "‹ Staffel N", Tippen darauf zeigt alle Staffeln.
private struct EpisodesSheet: View {
    let title: String
    let seasons: [SeasonLink]
    let currentSeason: SeasonLink?
    let currentEpisodes: [EpisodeItem]
    let currentPath: String
    let loadSeason: (SeasonLink) async -> [EpisodeItem]
    let onClose: () -> Void
    let onPick: (EpisodeItem, [EpisodeItem]) -> Void

    @State private var viewSeason: SeasonLink?
    @State private var episodes: [EpisodeItem] = []
    @State private var choosingSeason = false
    @State private var loading = false

    private func label(_ season: SeasonLink?) -> String {
        guard let season else { return "Folgen" }
        return season.isMovies ? "Filme & Specials" : "Staffel \(season.label)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if choosingSeason {
                HStack {
                    Text(title)
                        .font(.system(size: 20, weight: .heavy))
                        .foregroundColor(AbiTheme.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    SheetCloseButton(action: onClose)
                }
                ScrollView {
                    VStack(spacing: 4) {
                        ForEach(seasons) { season in
                            Button {
                                show(season)
                            } label: {
                                HStack {
                                    Image(systemName: "checkmark")
                                        .opacity(season == viewSeason ? 1 : 0)
                                        .frame(width: 26)
                                    Text(label(season)).font(.system(size: 16, weight: .bold))
                                    Spacer()
                                    if season == currentSeason {
                                        Text("läuft").font(.system(size: 12)).foregroundColor(AbiTheme.accentCyan)
                                    }
                                }
                                .foregroundColor(AbiTheme.textPrimary)
                                .padding(12)
                                .background(season == viewSeason ? Color.white.opacity(0.1) : Color.clear)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            } else {
                HStack {
                    Button {
                        if seasons.count > 1 { choosingSeason = true }
                    } label: {
                        HStack(spacing: 4) {
                            if seasons.count > 1 { Image(systemName: "chevron.left").font(.system(size: 18, weight: .bold)) }
                            Text(label(viewSeason)).font(.system(size: 20, weight: .heavy))
                        }
                        .foregroundColor(AbiTheme.textPrimary)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Spacer()
                    SheetCloseButton(action: onClose)
                }

                if loading {
                    ProgressView().tint(AbiTheme.accentCyan).frame(maxWidth: .infinity).padding(24)
                } else {
                    ScrollViewReader { proxy in
                        ScrollView {
                            VStack(spacing: 6) {
                                ForEach(episodes) { episode in
                                    let isCurrent = episode.path == currentPath
                                    Button {
                                        if !isCurrent { onPick(episode, episodes) }
                                    } label: {
                                        HStack(alignment: .top, spacing: 10) {
                                            Text("\(episode.number)")
                                                .font(.system(size: 17, weight: .bold))
                                                .foregroundColor(isCurrent ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                                                .frame(width: 34, alignment: .leading)
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(episode.displayTitle.isEmpty ? "Folge \(episode.number)" : episode.displayTitle)
                                                    .font(.system(size: 14, weight: isCurrent ? .bold : .medium))
                                                    .foregroundColor(AbiTheme.textPrimary)
                                                    .multilineTextAlignment(.leading)
                                                if isCurrent {
                                                    Text("Läuft gerade").font(.system(size: 12)).foregroundColor(AbiTheme.accentCyan)
                                                }
                                            }
                                            Spacer()
                                        }
                                        .padding(10)
                                        .background(isCurrent ? Color.white.opacity(0.1) : Color.clear)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                    }
                                    .buttonStyle(.plain)
                                    .id(episode.path)
                                }
                            }
                        }
                        .onAppear { proxy.scrollTo(currentPath, anchor: .center) }
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .background(AbiTheme.bgSurface.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            if viewSeason == nil {
                viewSeason = currentSeason
                episodes = currentEpisodes
            }
        }
    }

    private func show(_ season: SeasonLink) {
        choosingSeason = false
        viewSeason = season
        if season == currentSeason {
            episodes = currentEpisodes
            return
        }
        loading = true
        Task {
            let loaded = await loadSeason(season)
            await MainActor.run {
                episodes = loaded
                loading = false
            }
        }
    }
}

/// Sprachen der aktuellen Folge; die gewählte wird auch für die nächsten Titel gemerkt.
private struct LanguageSheet: View {
    let languages: [LanguageOption]
    let active: LanguageOption?
    let onClose: () -> Void
    let onPick: (LanguageOption) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Sprache")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundColor(AbiTheme.textPrimary)
                Spacer()
                SheetCloseButton(action: onClose)
            }
            ForEach(languages, id: \.self) { language in
                Button {
                    onPick(language)
                } label: {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(Color(red: Double((language.badgeColorHex >> 16) & 0xFF) / 255,
                                        green: Double((language.badgeColorHex >> 8) & 0xFF) / 255,
                                        blue: Double(language.badgeColorHex & 0xFF) / 255))
                            .frame(width: 10, height: 10)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(language.shortLabel).font(.system(size: 15, weight: .bold))
                            Text(language.fullLabel).font(.system(size: 12)).foregroundColor(AbiTheme.textSecondary)
                        }
                        Spacer()
                        if language == active {
                            Image(systemName: "checkmark").foregroundColor(AbiTheme.accentCyan)
                        }
                    }
                    .foregroundColor(AbiTheme.textPrimary)
                    .padding(12)
                    .background(language == active ? Color.white.opacity(0.1) : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
            Spacer(minLength: 0)
        }
        .padding(18)
        .background(AbiTheme.bgSurface.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }
}

/// Rundes "X" oben rechts in den Blättern (statt nur Herunterwischen).
private struct SheetCloseButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
                .frame(width: 34, height: 34)
                .background(Color.white.opacity(0.12))
                .clipShape(Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Schließen")
    }
}

// MARK: - Videofläche

#if canImport(UIKit)
/// Nur das Bild (AVPlayerLayer), ohne die Bedienelemente von iOS – die zeichnet der Player selbst.
private struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer

    final class LayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }

    func makeUIView(context: Context) -> LayerView {
        let view = LayerView()
        view.backgroundColor = .black
        view.playerLayer.videoGravity = .resizeAspect
        view.playerLayer.player = player
        return view
    }

    func updateUIView(_ uiView: LayerView, context: Context) {
        if uiView.playerLayer.player !== player {
            uiView.playerLayer.player = player
        }
    }
}
#else
private struct PlayerLayerView: View {
    let player: AVPlayer
    var body: some View {
        VideoPlayer(player: player)
    }
}
#endif
