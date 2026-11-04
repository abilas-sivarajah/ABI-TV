import SwiftUI

public struct DetailView: View {
    public let animeSlug: String
    public let initialTitle: String
    public let initialCover: String
    public var onBack: () -> Void
    public var onOpenCastSheet: () -> Void
    public var onPlayEpisode: (_ animeSlug: String, _ episode: EpisodeItem, _ language: LanguageOption, _ title: String, _ coverUrl: String) -> Void

    @ObservedObject private var storage = LocalStorage.shared
    @ObservedObject private var castManager = IosCastManager.shared

    @State private var detail: AnimeDetail? = nil
    @State private var isLoading: Bool = true
    @State private var errorMessage: String? = nil

    @State private var selectedSeasonPath: String = ""
    @State private var episodes: [EpisodeItem] = []
    @State private var isLoadingEpisodes: Bool = false

    @State private var selectedLanguage: LanguageOption = LocalStorage.shared.preferredLanguage

    private let isMovie: Bool
    private let isSeries: Bool
    private let isHdFilme: Bool

    public init(
        animeSlug: String,
        initialTitle: String,
        initialCover: String,
        onBack: @escaping () -> Void,
        onOpenCastSheet: @escaping () -> Void = {},
        onPlayEpisode: @escaping (_ animeSlug: String, _ episode: EpisodeItem, _ language: LanguageOption, _ title: String, _ coverUrl: String) -> Void
    ) {
        self.animeSlug = animeSlug
        self.initialTitle = initialTitle
        self.initialCover = initialCover
        self.onBack = onBack
        self.onOpenCastSheet = onOpenCastSheet
        self.onPlayEpisode = onPlayEpisode
        self.isMovie = FilmpalastScraper.isFilmpalastSlug(animeSlug)
        self.isSeries = SerienStreamScraper.isStoSlug(animeSlug)
        self.isHdFilme = HdFilmeScraper.isHdFilmeSlug(animeSlug)
    }

    public var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topLeading) {
                AbiTheme.bgPrimary.ignoresSafeArea()

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        heroHeaderSection

                        contentBodySection
                            .padding(.horizontal, 16)
                            .offset(y: -26)
                    }
                    .padding(.bottom, 64)
                }

                HStack {
                    Button(action: onBack) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 16, weight: .bold))
                            Text("Zurück")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        .foregroundColor(AbiTheme.textPrimary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(AbiTheme.bgSurface.opacity(0.88))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(Color.white.opacity(0.12), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    CastActionButton(onClick: onOpenCastSheet)
                }
                .padding(.top, 12)
                .padding(.horizontal, 16)
            }

            MiniRemoteBar()
        }
        .background(AbiTheme.bgPrimary.ignoresSafeArea())
        .onAppear {
            selectedLanguage = storage.preferredLanguage
            if detail == nil {
                loadDetail()
            }
        }
    }

    private var currentCover: String {
        if let cover = detail?.coverUrl, !cover.isEmpty {
            return cover
        }
        return initialCover
    }

    private var currentBackdrop: String {
        if let backdrop = detail?.backdropUrl, !backdrop.isEmpty {
            return backdrop
        }
        return currentCover
    }

    private var currentTitle: String {
        if let title = detail?.title, !title.isEmpty {
            return title
        }
        return initialTitle
    }

    private var availableLanguages: [LanguageOption] {
        var langs = Set<LanguageOption>()
        for ep in episodes {
            for l in ep.languages {
                langs.insert(l)
            }
        }
        if langs.isEmpty {
            return (isMovie || isHdFilme) ? [.gerDub] : LanguageOption.allCases
        }
        return LanguageOption.allCases.filter { langs.contains($0) }
    }

    private var heroHeaderSection: some View {
        ZStack(alignment: .bottomLeading) {
            // Als Overlay auf einer leeren Fläche: Ein direkt eingesetztes Fill-Bild wäre breiter als der
            // Bildschirm und würde die ganze Detailseite mit aufziehen, sodass Inhalte seitlich abgeschnitten sind.
            Color.clear
                .frame(maxWidth: .infinity)
                .frame(height: 290)
                .overlay(
                    AsyncImage(url: URL(string: currentBackdrop)) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .blur(radius: 12)
                                .opacity(0.38)
                        default:
                            AbiTheme.bgSurface
                        }
                    }
                )
                .clipped()

            LinearGradient(
                colors: [
                    AbiTheme.bgPrimary.opacity(0.25),
                    AbiTheme.bgPrimary.opacity(0.65),
                    AbiTheme.bgPrimary
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 290)

            HStack(alignment: .bottom, spacing: 16) {
                AsyncImage(url: URL(string: currentCover)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    default:
                        ZStack {
                            AbiTheme.bgSurface
                            Image(systemName: "film")
                                .foregroundColor(AbiTheme.textMuted)
                        }
                    }
                }
                .frame(width: 112, height: 162)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.12), lineWidth: 1)
                )

                VStack(alignment: .leading, spacing: 6) {
                    if let years = detail?.years, !years.isEmpty {
                        Text(years)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(AbiTheme.accentCyan)
                    }
                    Text(currentTitle)
                        .font(.system(size: 20, weight: .heavy))
                        .foregroundColor(AbiTheme.textPrimary)
                        .lineLimit(3)

                    if let genres = detail?.genres, !genres.isEmpty {
                        Text(genres.prefix(4).joined(separator: " • "))
                            .font(.system(size: 12))
                            .foregroundColor(AbiTheme.textSecondary)
                            .lineLimit(1)
                    }

                    if let rating = detail?.rating, !rating.isEmpty {
                        Text("★ \(rating)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(AbiTheme.accentCyan)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 34)
        }
    }

    private var contentBodySection: some View {
        let savedProgress = storage.getProgressForAnime(animeSlug)
        let inWatchlist = storage.isInWatchlist(animeSlug)
        let isSingleMovieItem = isMovie || (isHdFilme && (detail?.seasons.isEmpty ?? true) && episodes.count <= 1)

        return VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 8) {
                // Dieselbe Nummer in einer anderen Staffel ist eine andere Folge, deshalb Staffel mitprüfen
                let targetEpisode: EpisodeItem? = {
                    guard let prog = savedProgress else { return episodes.first }
                    return episodes.first(where: { $0.path == prog.episodePath })
                        ?? episodes.first(where: { $0.seasonLabel == prog.seasonLabel && $0.number == prog.episodeNumber })
                        ?? episodes.first
                }()

                // Die Beschriftung nennt immer die Folge, die der Button wirklich startet
                let playLabel: String = {
                    if !isSingleMovieItem, let ep = targetEpisode {
                        return "▶ \(EpisodeItem.shortLabel(season: ep.seasonLabel, number: ep.number))"
                    }
                    return "▶ Abspielen"
                }()

                Button {
                    if let ep = targetEpisode {
                        let lang = ep.languages.contains(selectedLanguage)
                            ? selectedLanguage
                            : (ep.languages.first ?? selectedLanguage)
                        onPlayEpisode(detail?.slug ?? animeSlug, ep, lang, currentTitle, currentCover)
                    }
                } label: {
                    Text(playLabel)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(AbiTheme.onAccent)
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(
                            LinearGradient(
                                colors: episodes.isEmpty ? [AbiTheme.bgElevated, AbiTheme.bgElevated] : [AbiTheme.accentBlue, AbiTheme.accentCyan],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(episodes.isEmpty)

                Button {
                    if let ep = targetEpisode {
                        let lang = ep.languages.contains(selectedLanguage)
                            ? selectedLanguage
                            : (ep.languages.first ?? selectedLanguage)
                        let slugToSend = detail?.slug ?? animeSlug
                        Task {
                            let ok = await castManager.playOnTv(
                                animeSlug: slugToSend,
                                animeTitle: currentTitle,
                                coverUrl: currentCover,
                                episodePath: ep.path,
                                episodeNumber: ep.number,
                                seasonLabel: ep.seasonLabel,
                                titleDe: ep.displayTitle,
                                preferredLangKey: lang.langKey
                            )
                            if !ok && castManager.connectedDevice == nil {
                                onOpenCastSheet()
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "tv")
                            .font(.system(size: 13, weight: .bold))
                        Text("Auf TV")
                            .font(.system(size: 13, weight: .bold))
                    }
                    .foregroundColor(episodes.isEmpty ? AbiTheme.textMuted : AbiTheme.accentCyan)
                    .padding(.horizontal, 14)
                    .frame(height: 46)
                    .background(AbiTheme.bgElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(AbiTheme.accentCyan.opacity(0.6), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .disabled(episodes.isEmpty)

                Button {
                    let card = AnimeCard(
                        slug: detail?.slug ?? animeSlug,
                        title: currentTitle,
                        coverUrl: currentCover,
                        genreOrSubtitle: detail?.years ?? "",
                        rating: detail?.rating ?? ""
                    )
                    storage.toggleWatchlist(card)
                } label: {
                    Text(inWatchlist ? "✓" : "+")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(inWatchlist ? AbiTheme.accentCyan : AbiTheme.textPrimary)
                        .frame(width: 46, height: 46)
                        .background(inWatchlist ? AbiTheme.accentCyan.opacity(0.22) : AbiTheme.bgElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(inWatchlist ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }

            if let desc = detail?.description, !desc.isEmpty {
                ExpandableDescriptionView(text: desc)
            }

            if availableLanguages.count > 1 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Sprache")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(AbiTheme.textSecondary)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(availableLanguages, id: \.self) { lang in
                                let selected = lang == selectedLanguage
                                Button {
                                    selectedLanguage = lang
                                    storage.preferredLanguage = lang
                                } label: {
                                    Text(lang.shortLabel)
                                        .font(.system(size: 12, weight: selected ? .bold : .medium))
                                        .foregroundColor(selected ? AbiTheme.textPrimary : AbiTheme.textSecondary)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(selected ? AbiTheme.accentBlue.opacity(0.32) : AbiTheme.bgSurface)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(selected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }

            if let seasons = detail?.seasons, !isMovie && seasons.count > 1 {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Staffel")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(AbiTheme.textSecondary)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(seasons) { season in
                                let selected = season.path == selectedSeasonPath || season.label == selectedSeasonPath
                                Button {
                                    selectedSeasonPath = season.path
                                    loadSeasonEpisodes(season: season)
                                } label: {
                                    Text(season.isMovies ? "Filme" : "Staffel \(season.label)")
                                        .font(.system(size: 13, weight: selected ? .bold : .medium))
                                        .foregroundColor(selected ? AbiTheme.textPrimary : AbiTheme.textSecondary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background(selected ? AbiTheme.accentBlue.opacity(0.35) : AbiTheme.bgSurface)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(selected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }

            Text(isSingleMovieItem ? "Stream" : "Episoden (\(episodes.count))")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
                .padding(.top, 4)

            if isLoading || isLoadingEpisodes {
                HStack {
                    Spacer()
                    ProgressView()
                        .tint(AbiTheme.accentCyan)
                        .padding(.vertical, 28)
                    Spacer()
                }
            } else if let errorMessage = errorMessage {
                Text("Fehler beim Laden: \(errorMessage)")
                    .font(.system(size: 13))
                    .foregroundColor(AbiTheme.errorRed)
                    .padding(.vertical, 16)
            } else {
                VStack(spacing: 8) {
                    ForEach(episodes) { ep in
                        let isWatched = storage.isEpisodeWatched(ep.path)
                        EpisodeRowView(
                            episode: ep,
                            isMovie: isSingleMovieItem,
                            isWatched: isWatched,
                            onClick: {
                                let lang = ep.languages.contains(selectedLanguage)
                                    ? selectedLanguage
                                    : (ep.languages.first ?? selectedLanguage)
                                onPlayEpisode(detail?.slug ?? animeSlug, ep, lang, currentTitle, currentCover)
                            },
                            onCastClick: {
                                let lang = ep.languages.contains(selectedLanguage)
                                    ? selectedLanguage
                                    : (ep.languages.first ?? selectedLanguage)
                                let slugToSend = detail?.slug ?? animeSlug
                                Task {
                                    let ok = await castManager.playOnTv(
                                        animeSlug: slugToSend,
                                        animeTitle: currentTitle,
                                        coverUrl: currentCover,
                                        episodePath: ep.path,
                                        episodeNumber: ep.number,
                                        seasonLabel: ep.seasonLabel,
                                        titleDe: ep.displayTitle,
                                        preferredLangKey: lang.langKey
                                    )
                                    if !ok && castManager.connectedDevice == nil {
                                        onOpenCastSheet()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private func loadDetail() {
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let det: AnimeDetail
                if isMovie {
                    det = try await FilmpalastScraper.fetchDetail(animeSlug)
                } else if isSeries {
                    det = try await SerienStreamScraper.fetchDetail(animeSlug)
                } else if isHdFilme {
                    det = try await HdFilmeScraper.fetchDetail(animeSlug)
                } else {
                    det = try await AniWorldScraper.fetchAnimeDetail(animeSlug)
                }

                await MainActor.run {
                    self.detail = det
                    self.episodes = det.episodes
                    self.selectedSeasonPath = initialSeasonPath(for: det)
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }

    /// Die wirklich geladene Staffel markieren: Bei vielen Serien steht "Filme" zuerst in der Liste,
    /// angezeigt werden aber die Folgen der ersten echten Staffel.
    private func initialSeasonPath(for det: AnimeDetail) -> String {
        if let current = det.seasons.first(where: { $0.path == det.currentSeasonPath }) {
            return current.path
        }
        if let label = det.episodes.first?.seasonLabel,
           let active = det.seasons.first(where: { $0.label == label }) {
            return active.path
        }
        return det.seasons.first(where: { !$0.isMovies })?.path
            ?? det.seasons.first?.path
            ?? det.currentSeasonPath
    }

    private func loadSeasonEpisodes(season: SeasonLink) {
        isLoadingEpisodes = true
        errorMessage = nil
        Task {
            do {
                let det: AnimeDetail
                if isSeries {
                    det = try await SerienStreamScraper.fetchDetail(animeSlug, requestedSeasonPath: season.path)
                } else if isHdFilme {
                    det = try await HdFilmeScraper.fetchDetail(animeSlug, seasonPathOverride: season.path)
                } else {
                    det = try await AniWorldScraper.fetchAnimeDetail(animeSlug, seasonPathOverride: season.path)
                }
                await MainActor.run {
                    // Inzwischen eine andere Staffel gewählt: Diese Antwort ist veraltet
                    guard self.selectedSeasonPath == season.path else { return }
                    self.episodes = det.episodes
                    self.isLoadingEpisodes = false
                }
            } catch {
                await MainActor.run {
                    guard self.selectedSeasonPath == season.path else { return }
                    // Sonst stünden unter der neu gewählten Staffel weiter die Folgen der alten
                    self.errorMessage = error.localizedDescription
                    self.isLoadingEpisodes = false
                }
            }
        }
    }
}

private struct ExpandableDescriptionView: View {
    let text: String
    @State private var expanded: Bool = false

    var body: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                expanded.toggle()
            }
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(text)
                    .font(.system(size: 13))
                    .foregroundColor(AbiTheme.textSecondary)
                    .lineSpacing(3)
                    .lineLimit(expanded ? nil : 3)
                    .multilineTextAlignment(.leading)

                if text.count > 140 {
                    Text(expanded ? "Weniger anzeigen" : "Mehr anzeigen")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(AbiTheme.accentCyan)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
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

private struct EpisodeRowView: View {
    let episode: EpisodeItem
    let isMovie: Bool
    let isWatched: Bool
    let onClick: () -> Void
    let onCastClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(isWatched ? AbiTheme.accentBlue.opacity(0.28) : AbiTheme.bgElevated)
                        .frame(width: 40, height: 40)

                    Text(verbatim: isWatched ? "✓" : (isMovie ? "▶" : String(episode.number)))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(isWatched ? AbiTheme.accentCyan : AbiTheme.textPrimary)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(episode.displayTitle)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(isWatched ? AbiTheme.textSecondary : AbiTheme.textPrimary)
                        .lineLimit(1)

                    if !episode.secondaryTitle.isEmpty {
                        Text(episode.secondaryTitle)
                            .font(.system(size: 11))
                            .foregroundColor(AbiTheme.textMuted)
                            .lineLimit(1)
                    }

                    if !episode.languages.isEmpty {
                        Text(episode.languages.map(\.shortLabel).joined(separator: " • "))
                            .font(.system(size: 10))
                            .foregroundColor(AbiTheme.accentCyan)
                            .lineLimit(1)
                    }
                }

                Spacer()

                Button(action: onCastClick) {
                    Text("TV")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(AbiTheme.accentCyan)
                        .frame(width: 36, height: 36)
                        .background(AbiTheme.bgElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(AbiTheme.accentCyan.opacity(0.5), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)

                Text("▶")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(AbiTheme.accentCyan)
            }
            .padding(12)
            .background(AbiTheme.bgSurface)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isWatched ? AbiTheme.accentCyan.opacity(0.45) : Color.white.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}
