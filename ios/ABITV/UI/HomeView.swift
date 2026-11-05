import SwiftUI

/// Reiter unten (wie die Leiste am PC/TV); die Suche sitzt als Lupe oben rechts.
public enum MobileTab: Int, CaseIterable, Identifiable {
    case start = 0
    case movies = 1
    case series = 2
    case anime = 3
    case liveTv = 4
    case watchlist = 5
    case search = 6

    public var id: Int { rawValue }

    /// Reiter der unteren Leiste in dieser Reihenfolge.
    public static let barTabs: [MobileTab] = [.start, .movies, .series, .anime, .liveTv, .watchlist]

    public var title: String {
        switch self {
        case .start: return "Start"
        case .movies: return "Filme"
        case .series: return "Serien"
        case .anime: return "Anime"
        case .liveTv: return "Live TV"
        case .watchlist: return "Watchlist"
        case .search: return "Suche"
        }
    }

    public var iconName: String {
        switch self {
        case .start: return "house.fill"
        case .movies: return "film"
        case .series: return "tv"
        case .anime: return "sparkles"
        case .liveTv: return "antenna.radiowaves.left.and.right"
        case .watchlist: return "bookmark.fill"
        case .search: return "magnifyingglass"
        }
    }
}

/// Genres der Genre-Seiten (ohne "Top"/"Neu"/"Beliebt", die stecken schon in der Übersicht).
private let movieGenres = FilmpalastScraper.categories.filter { $0.0.contains("/genre/") }
private let seriesGenres = SerienStreamScraper.categories.filter { $0.0.contains("/genre/") && $0.0 != "/genre/anime" }
private let animeGenres = AniWorldScraper.genreCategories.filter { !["beliebt", "neu", "isekai"].contains($0.0) }

public struct HomeView: View {
    public var onSelectItem: (AnimeCard) -> Void
    public var onPlayDirect: (_ entry: WatchProgressEntry) -> Void
    public var onPlayLiveChannel: (LiveTvChannel) -> Void
    public var onOpenCastDialog: () -> Void
    public var onTriggerUpdate: (AppRelease) -> Void

    @ObservedObject private var storage = LocalStorage.shared
    @ObservedObject private var stoFilter = StoAnimeFilter.shared
    @State private var selectedTab: MobileTab = .start

    // Kataloge (Übersichten)
    @State private var animeSections: [CatalogSection] = []
    @State private var movieSections: [CatalogSection] = []
    @State private var seriesSections: [CatalogSection] = []
    @State private var isLoadingAnime = false
    @State private var isLoadingMovies = false
    @State private var isLoadingSeries = false
    @State private var animeError: String? = nil

    // Genre-Seiten: gewähltes Genre je Reiter (nil = Übersicht)
    @State private var movieGenre: String? = nil
    @State private var seriesGenre: String? = nil
    @State private var animeGenre: String? = nil
    @State private var genreItems: [AnimeCard] = []
    @State private var genreLoadedKey: String? = nil
    @State private var genrePage = 1
    @State private var genreCanLoadMore = true
    @State private var isLoadingGenre = false
    @State private var isLoadingMoreGenre = false

    // Titelbilder (einmal pro Laden ausgewürfelt)
    @State private var startHero: [AnimeCard] = []
    @State private var movieHero: [AnimeCard] = []
    @State private var seriesHero: [AnimeCard] = []
    @State private var animeHero: [AnimeCard] = []

    // Suche
    @State private var searchQuery: String = ""
    @State private var searchScope: SearchScope = .all
    @State private var searchResults: [AnimeCard] = []
    @State private var isSearching: Bool = false
    @State private var searchTask: Task<Void, Never>? = nil
    /// Vorschläge bei leerer Suche – einmal ausgewürfelt, damit sie beim Neuzeichnen nicht wechseln.
    @State private var suggestionList: [AnimeCard] = []

    // Watchlist: Letterboxd
    @State private var letterboxdInput = ""
    @State private var letterboxdInputError: String? = nil
    @State private var popularLists: [LetterboxdLists.ListInfo]? = nil
    @State private var popularListsError: String? = nil
    @State private var featuredVisit = 0

    // Info-Blatt und Einstellungen
    @State private var infoCard: AnimeCard? = nil
    @State private var startingPlayback = false
    @State private var showSettingsSheet: Bool = false
    @State private var isCheckingUpdate: Bool = false
    @State private var updateStatusMessage: String? = nil
    @State private var pendingUpdateRelease: AppRelease? = nil

    public init(
        onSelectItem: @escaping (AnimeCard) -> Void,
        onPlayDirect: @escaping (_ entry: WatchProgressEntry) -> Void,
        onPlayLiveChannel: @escaping (LiveTvChannel) -> Void,
        onOpenCastDialog: @escaping () -> Void,
        onTriggerUpdate: @escaping (AppRelease) -> Void
    ) {
        self.onSelectItem = onSelectItem
        self.onPlayDirect = onPlayDirect
        self.onPlayLiveChannel = onPlayLiveChannel
        self.onOpenCastDialog = onOpenCastDialog
        self.onTriggerUpdate = onTriggerUpdate
    }

    public var body: some View {
        VStack(spacing: 0) {
            topHeaderBar

            ZStack {
                AbiTheme.bgPrimary.ignoresSafeArea()

                switch selectedTab {
                case .start: startTab
                case .movies: moviesTab
                case .series: seriesTab
                case .anime: animeTab
                case .liveTv: LiveTvView(onPlayChannel: onPlayLiveChannel)
                case .watchlist: watchlistTab
                case .search: searchTab
                }
            }

            MiniRemoteBar()

            bottomTabBar
        }
        .background(AbiTheme.bgPrimary.ignoresSafeArea())
        .onAppear {
            if animeSections.isEmpty && !isLoadingAnime { loadAnimeCatalog() }
            if movieSections.isEmpty && !isLoadingMovies { loadMoviesCatalog() }
            if seriesSections.isEmpty && !isLoadingSeries { loadSeriesCatalog() }
        }
        .task { await StoAnimeFilter.shared.load() }
        .onChange(of: selectedTab) { newTab in
            if newTab == .watchlist { featuredVisit += 1 }
            if newTab == .search { refreshSuggestions() }
        }
        .onChange(of: movieSections.count + seriesSections.count + animeSections.count) { _ in
            // Kataloge nachgeladen: Vorschläge füllen, falls die Suche gerade leer war
            if suggestionList.isEmpty { refreshSuggestions() }
        }
        .sheet(item: $infoCard) { card in
            TitleInfoSheet(
                card: card,
                storage: storage,
                onPlay: { play(card) },
                onDetail: {
                    infoCard = nil
                    onSelectItem(card)
                },
                onClose: { infoCard = nil }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: $showSettingsSheet, onDismiss: {
            // Der Update-Dialog darf erst erscheinen, wenn dieses Sheet ganz geschlossen ist,
            // sonst verwirft iOS die zweite Präsentation
            if let release = pendingUpdateRelease {
                pendingUpdateRelease = nil
                onTriggerUpdate(release)
            }
        }) {
            settingsSheetContent
                .presentationDetents([.medium, .large])
        }
    }

    // MARK: - Kopf und Reiter

    private var topHeaderBar: some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(LinearGradient(colors: [AbiTheme.accentBlue, AbiTheme.accentCyan], startPoint: .bottomLeading, endPoint: .topTrailing))
                        .frame(width: 30, height: 30)
                    Image(systemName: "play.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                }
                Text("ABI TV")
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(AbiTheme.accentCyan)
            }

            Spacer()

            headerIcon("magnifyingglass", selected: selectedTab == .search) {
                if selectedTab != .search {
                    switch selectedTab {
                    case .movies: searchScope = .movies
                    case .series: searchScope = .series
                    case .anime: searchScope = .animes
                    default: searchScope = .all
                    }
                }
                selectedTab = .search
            }

            CastActionButton(onClick: onOpenCastDialog)

            headerIcon("gearshape.fill", selected: false) {
                updateStatusMessage = nil
                showSettingsSheet = true
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(AbiTheme.bgPrimary)
    }

    private func headerIcon(_ name: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: name)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(selected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                .frame(width: 36, height: 36)
                .background(selected ? Color.white.opacity(0.14) : AbiTheme.bgElevated)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.08), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var bottomTabBar: some View {
        HStack(spacing: 0) {
            ForEach(MobileTab.barTabs) { tab in
                let isSelected = selectedTab == tab
                Button {
                    selectedTab = tab
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.iconName)
                            .font(.system(size: 16, weight: isSelected ? .bold : .regular))
                        Text(tab.title)
                            .font(.system(size: 10, weight: isSelected ? .bold : .medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    .foregroundColor(isSelected ? AbiTheme.accentCyan : AbiTheme.textMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 4)
        .background(AbiTheme.bgSurface)
        .overlay(Rectangle().fill(Color.white.opacity(0.08)).frame(height: 1), alignment: .top)
    }

    // MARK: - Startseite

    private var startTab: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(alignment: .leading, spacing: 22) {
                if !startHero.isEmpty {
                    HeroCarousel(items: startHero, storage: storage, onPlay: play, onDetail: onSelectItem)
                }
                if !storage.progressList.isEmpty {
                    continueWatchingSection(entries: storage.progressList)
                }
                if !storage.watchlist.isEmpty {
                    CardRow(title: "★ Deine Watchlist", items: storage.watchlist, storage: storage, onOpen: openInfo)
                }
                if let top = movieSections.first, !top.items.isEmpty {
                    CardRow(title: "🔥 Beliebte Top-Filme auf Filmpalast", items: top.items, storage: storage, onOpen: openInfo)
                }
                let series = (seriesSections.first?.items ?? []).withoutStoAnime(stoFilter.slugs)
                if !series.isEmpty {
                    CardRow(title: "📺 Beliebte Serien auf S.to", items: series, storage: storage, onOpen: openInfo)
                }
                if let anime = animeSections.first, !anime.items.isEmpty {
                    CardRow(title: "✨ Beliebte Anime auf AniWorld", items: anime.items, storage: storage, onOpen: openInfo)
                }
                if movieSections.isEmpty && seriesSections.isEmpty && animeSections.isEmpty {
                    loadingStateView(text: "Lade Startseite …")
                        .frame(height: 300)
                }
            }
            .padding(.bottom, 24)
        }
    }

    // MARK: - Filme, Serien, Anime

    private var moviesTab: some View {
        catalogTab(
            sections: movieSections,
            hero: movieHero,
            isLoading: isLoadingMovies,
            loadingText: "Lade Filme von Filmpalast …",
            genres: movieGenres,
            selectedGenre: movieGenre,
            onSelectGenre: { selectGenre($0, tab: .movies) },
            onOverview: { movieGenre = nil },
            onRetry: loadMoviesCatalog
        )
    }

    private var seriesTab: some View {
        catalogTab(
            sections: seriesSections.map { CatalogSection(id: $0.id, title: $0.title, items: $0.items.withoutStoAnime(stoFilter.slugs)) }
                .filter { !$0.items.isEmpty },
            hero: seriesHero,
            isLoading: isLoadingSeries,
            loadingText: "Lade Serien von S.to …",
            genres: seriesGenres,
            selectedGenre: seriesGenre,
            onSelectGenre: { selectGenre($0, tab: .series) },
            onOverview: { seriesGenre = nil },
            onRetry: loadSeriesCatalog
        )
    }

    private var animeTab: some View {
        catalogTab(
            sections: animeSections,
            hero: animeHero,
            isLoading: isLoadingAnime,
            loadingText: "Lade Animes von AniWorld …",
            genres: animeGenres,
            selectedGenre: animeGenre,
            onSelectGenre: { selectGenre($0, tab: .anime) },
            onOverview: { animeGenre = nil },
            onRetry: loadAnimeCatalog,
            errorText: animeError
        )
    }

    /// Gemeinsamer Aufbau: Übersicht (Titelbild, Umschalter, Reihen) oder Genre-Seite (Umschalter, Genres, Raster).
    private func catalogTab(
        sections: [CatalogSection],
        hero: [AnimeCard],
        isLoading: Bool,
        loadingText: String,
        genres: [(String, String)],
        selectedGenre: String?,
        onSelectGenre: @escaping (String) -> Void,
        onOverview: @escaping () -> Void,
        onRetry: @escaping () -> Void,
        errorText: String? = nil
    ) -> some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(alignment: .leading, spacing: 18) {
                if let genre = selectedGenre {
                    CatalogModeSwitcher(isOverview: false, onOverview: onOverview, onGenre: {})
                        .padding(.top, 12)
                    GenreChipsBar(genres: genres, selected: genre, onSelect: onSelectGenre)
                        // Beim Reiterwechsel das hier gewählte Genre (neu) laden
                        .task(id: genre) { if genreLoadedKey != genre { onSelectGenre(genre) } }
                    genreGrid(key: genre)
                } else if sections.isEmpty {
                    if isLoading {
                        loadingStateView(text: loadingText).frame(height: 360)
                    } else {
                        errorStateView(message: errorText ?? "Keine Titel gefunden. Die Seite ist gerade nicht erreichbar.", onRetry: onRetry)
                            .frame(height: 360)
                    }
                } else {
                    if !hero.isEmpty {
                        HeroCarousel(items: hero, storage: storage, onPlay: play, onDetail: onSelectItem)
                    }
                    CatalogModeSwitcher(isOverview: true, onOverview: {}, onGenre: {
                        if let first = genres.first?.0 { onSelectGenre(first) }
                    })
                    ForEach(sections) { section in
                        CardRow(title: section.title, items: section.items, storage: storage, onOpen: openInfo)
                    }
                }
            }
            .padding(.bottom, 24)
        }
    }

    private func genreGrid(key: String) -> some View {
        Group {
            if isLoadingGenre || genreLoadedKey != key {
                loadingStateView(text: "Lade Genre …").frame(height: 260)
            } else if genreItems.isEmpty {
                Text("Keine Titel gefunden.")
                    .font(.system(size: 13))
                    .foregroundColor(AbiTheme.textSecondary)
                    .padding(.horizontal, 16)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 108), spacing: 12)], spacing: 14) {
                    ForEach(genreItems) { item in
                        MobileAnimeCard(
                            anime: item,
                            width: 108,
                            isBookmarked: storage.isInWatchlist(item.slug),
                            onClick: { openInfo(item) }
                        )
                    }
                }
                .padding(.horizontal, 16)

                if genreCanLoadMore {
                    HStack {
                        Spacer()
                        Button {
                            loadMoreGenre()
                        } label: {
                            HStack(spacing: 8) {
                                if isLoadingMoreGenre { ProgressView().tint(AbiTheme.onAccent) }
                                Text(isLoadingMoreGenre ? "Lade mehr …" : "Mehr laden")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(AbiTheme.onAccent)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(AbiTheme.accentCyan)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .disabled(isLoadingMoreGenre)
                        Spacer()
                    }
                }
            }
        }
    }

    // MARK: - Watchlist

    private var watchlistTab: some View {
        ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(alignment: .leading, spacing: 22) {
                if !storage.progressList.isEmpty {
                    continueWatchingSection(entries: storage.progressList)
                }

                if storage.watchlist.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        SectionHeader(title: "★ Meine Watchlist")
                        Text("Noch leer – füge Titel über „+ Watchlist“ hinzu.")
                            .font(.system(size: 13))
                            .foregroundColor(AbiTheme.textSecondary)
                            .padding(.horizontal, 16)
                    }
                } else {
                    CardRow(title: "★ Meine Watchlist", items: storage.watchlist, storage: storage, onOpen: openInfo)
                }

                letterboxdUserSection

                ForEach(storage.letterboxdUsers, id: \.self) { user in
                    LetterboxdFilmsRow(
                        cacheKey: "user:\(user)",
                        title: "🎬 Watchlist von @\(user)",
                        storage: storage,
                        load: { try await LetterboxdLists.watchlist(user: user, maxPages: 3) },
                        onOpen: openInfo
                    )
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("Interessante Letterboxd-Listen")
                        .font(.system(size: 19, weight: .heavy))
                        .foregroundColor(AbiTheme.textPrimary)
                    Text(popularListsError ?? (popularLists == nil ? "Lade beliebte Listen …" : "Beliebte Listen der Letterboxd-Community – nur Filme, die es hier gibt."))
                        .font(.system(size: 12))
                        .foregroundColor(popularListsError != nil ? AbiTheme.errorRed : AbiTheme.textSecondary)
                }
                .padding(.horizontal, 16)

                // Eigene Empfehlungsliste immer zuerst; bei jedem Öffnen des Reiters frisch geladen
                LetterboxdFilmsRow(
                    cacheKey: "featured#\(featuredVisit)",
                    title: "⭐ \(LetterboxdLists.FeaturedList.name)",
                    storage: storage,
                    load: { try await LetterboxdLists.listFilms(path: LetterboxdLists.FeaturedList.path, maxPages: 2) },
                    onOpen: openInfo
                )

                ForEach(popularLists ?? [], id: \.path) { list in
                    LetterboxdFilmsRow(
                        cacheKey: "list:\(list.path)",
                        title: "📋 \(list.name) · von \(list.owner)",
                        storage: storage,
                        load: { Array(try await LetterboxdLists.listFilms(path: list.path, maxPages: 1).prefix(60)) },
                        onOpen: openInfo
                    )
                }
            }
            .padding(.vertical, 14)
        }
        .task { await loadPopularLists() }
    }

    private var letterboxdUserSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Circle().fill(letterboxdGreen).frame(width: 9, height: 9)
                Text("Letterboxd-Watchlists")
                    .font(.system(size: 19, weight: .heavy))
                    .foregroundColor(AbiTheme.textPrimary)
            }
            Text("Gib deinen oder einen anderen Letterboxd-Benutzernamen ein. Angezeigt werden die Filme der Watchlist, die es bei Filmpalast gibt.")
                .font(.system(size: 12))
                .foregroundColor(AbiTheme.textSecondary)
            HStack(spacing: 8) {
                TextField("Benutzername, z. B. dave", text: $letterboxdInput)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .onSubmit(addLetterboxdUser)
                    .font(.system(size: 14))
                    .foregroundColor(AbiTheme.textPrimary)
                    .padding(10)
                    .background(AbiTheme.bgSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.12), lineWidth: 1))
                Button(action: addLetterboxdUser) {
                    Text("Hinzufügen")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(AbiTheme.onAccent)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 11)
                        .background(AbiTheme.accentCyan)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
            if let error = letterboxdInputError {
                Text(error).font(.system(size: 12)).foregroundColor(AbiTheme.errorRed)
            }
            if !storage.letterboxdUsers.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(storage.letterboxdUsers, id: \.self) { user in
                            Button {
                                storage.removeLetterboxdUser(user)
                            } label: {
                                Text("@\(user)  ✕")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(AbiTheme.textPrimary)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(AbiTheme.bgElevated)
                                    .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
    }

    private func addLetterboxdUser() {
        guard let name = LetterboxdLists.normalizeUsername(letterboxdInput) else {
            letterboxdInputError = "Bitte einen Letterboxd-Benutzernamen eingeben (z. B. dave) oder den Profil-Link."
            return
        }
        storage.addLetterboxdUser(name)
        letterboxdInput = ""
        letterboxdInputError = nil
    }

    @MainActor
    private func loadPopularLists() async {
        if popularLists != nil { return }
        if let cached = LetterboxdSessionCache.popularLists {
            popularLists = cached
            return
        }
        do {
            // Eine mehr laden, falls die eigene Empfehlungsliste darunter ist (sie steht ohnehin oben)
            let lists = try await LetterboxdLists.popularLists(limit: 5)
                .filter { $0.path != LetterboxdLists.FeaturedList.path }
            let shown = Array(lists.prefix(4))
            LetterboxdSessionCache.popularLists = shown
            popularLists = shown
        } catch {
            popularListsError = "Letterboxd-Listen nicht erreichbar."
        }
    }

    // MARK: - Suche

    private var searchTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").foregroundColor(AbiTheme.textMuted)
                TextField("Film, Serie oder Anime suchen …", text: $searchQuery)
                    .font(.system(size: 14))
                    .foregroundColor(AbiTheme.textPrimary)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .onChange(of: searchQuery) { newValue in triggerSearch(query: newValue, scope: searchScope) }
                if !searchQuery.isEmpty {
                    Button {
                        searchQuery = ""
                        searchResults = []
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundColor(AbiTheme.textMuted)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(12)
            .background(AbiTheme.bgSurface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.08), lineWidth: 1))
            .padding(.horizontal, 16)
            .padding(.top, 12)

            // Filter unter dem Suchfeld: Filme · Serien · Anime · Alle
            HStack(spacing: 6) {
                ForEach([(SearchScope.movies, "Filme"), (SearchScope.series, "Serien"), (SearchScope.animes, "Anime"), (SearchScope.all, "Alle")], id: \.0) { scope, label in
                    let selected = searchScope == scope
                    Button {
                        searchScope = scope
                        refreshSuggestions()
                        triggerSearch(query: searchQuery, scope: scope)
                    } label: {
                        Text(label)
                            .font(.system(size: 13, weight: selected ? .bold : .medium))
                            .foregroundColor(selected ? AbiTheme.textPrimary : AbiTheme.textSecondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(selected ? Color.white.opacity(0.14) : Color.clear)
                            .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            }
            .padding(.horizontal, 12)

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 12) {
                    let trimmed = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                    if isSearching {
                        loadingStateView(text: "Suche nach „\(trimmed)“ …").frame(height: 240)
                    } else if trimmed.count < 2 {
                        // Leere Suche: zufällige Titel des gewählten Bereichs zum Stöbern
                        Text(suggestionHeader)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(AbiTheme.textPrimary)
                            .padding(.horizontal, 16)
                        resultGrid(suggestionList)
                    } else if searchResults.isEmpty {
                        Text("Keine Treffer für „\(trimmed)“.")
                            .font(.system(size: 14))
                            .foregroundColor(AbiTheme.textMuted)
                            .padding(.horizontal, 16)
                    } else {
                        Text("\(searchResults.count) Treffer für „\(trimmed)“")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(AbiTheme.textPrimary)
                            .padding(.horizontal, 16)
                        resultGrid(searchResults)
                    }
                }
                .padding(.bottom, 24)
            }
        }
    }

    private var suggestionHeader: String {
        switch searchScope {
        case .movies: return "Filme entdecken"
        case .series: return "Serien entdecken"
        case .animes: return "Anime entdecken"
        default: return "Entdecken"
        }
    }

    /// Neue zufällige Vorschläge (beim Öffnen der Suche und beim Wechsel des Filters).
    private func refreshSuggestions() {
        suggestionList = makeSuggestions()
    }

    /// Zufällige Vorschläge bei leerer Suche (aus den geladenen Übersichten).
    private func makeSuggestions() -> [AnimeCard] {
        let movies = movieSections.flatMap(\.items).shuffled()
        let series = seriesSections.flatMap(\.items).withoutStoAnime(stoFilter.slugs).shuffled()
        let anime = animeSections.flatMap(\.items).shuffled()
        var seen = Set<String>()
        let picked: [AnimeCard]
        switch searchScope {
        case .movies: picked = movies
        case .series: picked = series
        case .animes: picked = anime
        default:
            picked = (0..<12).flatMap { i in [movies, series, anime].compactMap { $0.indices.contains(i) ? $0[i] : nil } }
        }
        return Array(picked.filter { !$0.coverUrl.isEmpty && seen.insert($0.cleanSlug).inserted }.prefix(36))
    }

    private func resultGrid(_ items: [AnimeCard]) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 108), spacing: 12)], spacing: 14) {
            ForEach(items) { card in
                MobileAnimeCard(
                    anime: card,
                    width: 108,
                    isBookmarked: storage.isInWatchlist(card.slug),
                    onClick: { openInfo(card) }
                )
            }
        }
        .padding(.horizontal, 16)
    }

    private func triggerSearch(query: String, scope: SearchScope) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            searchResults = []
            isSearching = false
            return
        }
        let animeSlugs = stoFilter.slugs
        isSearching = true
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }

            // Seiten können nur "Titel enthält" – darum mit Teilbegriffen abfragen und danach
            // selbst nach Wortanfängen filtern: "sp" → "Marvel's Spider-Man", "spiderman" → "Spider-Man"
            let queries = TitleSearch.siteQueries(trimmed)
            var combined: [AnimeCard] = []
            await withTaskGroup(of: [AnimeCard].self) { group in
                for q in queries {
                    if scope == .all || scope == .animes { group.addTask { await AniWorldScraper.searchAnimes(q) } }
                    if scope == .all || scope == .series { group.addTask { await SerienStreamScraper.searchSeries(q) } }
                    if scope == .all || scope == .movies { group.addTask { await FilmpalastScraper.searchMovies(q) } }
                }
                for await result in group { combined.append(contentsOf: result) }
            }
            if Task.isCancelled { return }
            // Filmpalast führt einzelne Serienfolgen als "Film" ("Spider-Noir S01E08") – weglassen
            let cleaned = combined.filter { card in
                !card.title.localizedCaseInsensitiveContains("*ENGLISH*") &&
                    HtmlParser.firstMatch(in: card.title, pattern: #"\b(S\d{1,2}E\d{1,3})\b"#) == nil
            }.withoutStoAnime(animeSlugs)
            let ranked = Array(TitleSearch.filterAndRank(cleaned, query: trimmed).prefix(60))
            await MainActor.run {
                self.searchResults = ranked
                self.isSearching = false
            }
        }
    }

    // MARK: - Abspielen, Info-Blatt

    private func openInfo(_ card: AnimeCard) {
        infoCard = card
    }

    /// "Abspielen": zuletzt geschaute Folge fortsetzen, sonst die erste Folge in der bevorzugten Sprache.
    private func play(_ card: AnimeCard) {
        if let entry = storage.getProgressForAnime(card.slug) {
            infoCard = nil
            onPlayDirect(entry)
            return
        }
        guard !startingPlayback else { return }
        startingPlayback = true
        let language = storage.preferredLanguage
        Task {
            let detail = await TitlePreview.loadDetail(card)
            let episodes = detail?.episodes ?? []
            let episode = episodes.first { $0.languages.contains(language) } ?? episodes.first
            await MainActor.run {
                startingPlayback = false
                infoCard = nil
                guard let detail, let episode else {
                    // Keine Folge gefunden: Detailseite zur Auswahl öffnen
                    onSelectItem(card)
                    return
                }
                onPlayDirect(WatchProgressEntry(
                    animeSlug: card.slug,
                    animeTitle: detail.title.isEmpty ? card.title : detail.title,
                    coverUrl: detail.coverUrl.isEmpty ? card.coverUrl : detail.coverUrl,
                    seasonLabel: episode.seasonLabel,
                    episodeNumber: episode.number,
                    episodeTitle: episode.displayTitle,
                    episodePath: episode.path,
                    preferredLangKey: language.langKey,
                    positionMs: 0,
                    durationMs: 0,
                    updatedAt: Int64(Date().timeIntervalSince1970 * 1000)
                ))
            }
        }
    }

    // MARK: - Laden

    private func loadAnimeCatalog() {
        isLoadingAnime = true
        animeError = nil
        Task {
            let result: Result<[CatalogSection], Error>
            do { result = .success(try await AniWorldScraper.fetchHomeCatalog()) } catch { result = .failure(error) }
            await MainActor.run {
                switch result {
                case .success(let sections):
                    animeSections = sections
                    if sections.isEmpty { animeError = "AniWorld hat keine Inhalte geliefert. Die Seite ist vielleicht gerade nicht erreichbar." }
                case .failure(let error):
                    animeError = error.localizedDescription
                }
                isLoadingAnime = false
                rebuildHeroes()
            }
        }
    }

    private func loadMoviesCatalog() {
        isLoadingMovies = true
        Task {
            let sections = await FilmpalastScraper.fetchHomeSections()
            await MainActor.run {
                movieSections = sections
                isLoadingMovies = false
                rebuildHeroes()
            }
        }
    }

    private func loadSeriesCatalog() {
        isLoadingSeries = true
        Task {
            let sections = await SerienStreamScraper.fetchHomeSections()
            await MainActor.run {
                seriesSections = sections
                isLoadingSeries = false
                rebuildHeroes()
            }
        }
    }

    /// Je Bereich 6 zufällige Titel; auf der Startseite abwechselnd Film, Serie, Anime.
    private func rebuildHeroes() {
        let movies = movieSections.flatMap(\.items)
        let series = seriesSections.flatMap(\.items).withoutStoAnime(stoFilter.slugs)
        let anime = animeSections.flatMap(\.items)
        if movieHero.isEmpty || movieHero.count < TitlePreview.heroCount { movieHero = TitlePreview.randomPicks(movies) }
        if seriesHero.isEmpty || seriesHero.count < TitlePreview.heroCount { seriesHero = TitlePreview.randomPicks(series) }
        if animeHero.isEmpty || animeHero.count < TitlePreview.heroCount { animeHero = TitlePreview.randomPicks(anime) }
        let m = TitlePreview.randomPicks(movies, count: 2)
        let s = TitlePreview.randomPicks(series, count: 2)
        let a = TitlePreview.randomPicks(anime, count: 2)
        var mixed: [AnimeCard] = []
        for i in 0..<2 {
            for list in [m, s, a] where list.indices.contains(i) { mixed.append(list[i]) }
        }
        if startHero.count < TitlePreview.heroCount { startHero = mixed }
    }

    private func selectGenre(_ path: String, tab: MobileTab) {
        switch tab {
        case .movies: movieGenre = path
        case .series: seriesGenre = path
        case .anime: animeGenre = path
        default: return
        }
        if genreLoadedKey == path && !genreItems.isEmpty && !isLoadingGenre { return }
        isLoadingGenre = true
        genreItems = []
        genrePage = 1
        genreCanLoadMore = true
        genreLoadedKey = path
        Task {
            let items = await fetchGenre(path, tab: tab, page: 1)
            await MainActor.run {
                guard genreLoadedKey == path else { return }
                genreItems = uniqueCards(items)
                genreCanLoadMore = !items.isEmpty
                isLoadingGenre = false
            }
        }
    }

    private func loadMoreGenre() {
        guard let path = genreLoadedKey, !isLoadingMoreGenre else { return }
        let tab = selectedTab
        let next = genrePage + 1
        isLoadingMoreGenre = true
        Task {
            let items = await fetchGenre(path, tab: tab, page: next)
            await MainActor.run {
                isLoadingMoreGenre = false
                guard genreLoadedKey == path else { return }
                var seen = Set(genreItems.map(\.cleanSlug))
                let fresh = items.filter { seen.insert($0.cleanSlug).inserted }
                genreItems += fresh
                genrePage = next
                genreCanLoadMore = !fresh.isEmpty
            }
        }
    }

    private func fetchGenre(_ path: String, tab: MobileTab, page: Int) async -> [AnimeCard] {
        switch tab {
        case .movies: return await FilmpalastScraper.fetchCategory(path, page: page)
        case .series:
            let items = (try? await SerienStreamScraper.fetchCategory(path, page: page)) ?? []
            return items.withoutStoAnime(StoAnimeFilter.shared.slugs)
        case .anime: return (try? await AniWorldScraper.fetchCategoryOrGenre(path, page: page)) ?? []
        default: return []
        }
    }

    private func uniqueCards(_ cards: [AnimeCard]) -> [AnimeCard] {
        var seen = Set<String>()
        return cards.filter { seen.insert($0.cleanSlug).inserted }
    }

    // MARK: - Gemeinsame Teile


    private func continueWatchingSection(entries: [WatchProgressEntry]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Weiterschauen")
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(entries) { entry in
                        MobileContinueCard(
                            entry: entry,
                            onClick: { onPlayDirect(entry) }
                        )
                        .contextMenu {
                            Button(role: .destructive) {
                                storage.removeFromContinueWatching(entry.animeSlug)
                            } label: {
                                Label("Aus Weiterschauen entfernen", systemImage: "trash")
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }


    private func loadingStateView(text: String) -> some View {
        VStack(spacing: 14) {
            Spacer()
            ProgressView()
                .tint(AbiTheme.accentCyan)
                .scaleEffect(1.2)
            Text(text)
                .font(.system(size: 13))
                .foregroundColor(AbiTheme.textSecondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }

    private func errorStateView(message: String, onRetry: @escaping () -> Void) -> some View {
        VStack(spacing: 12) {
            Spacer()
            Text("Fehler beim Laden")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(AbiTheme.errorRed)
            Text(message)
                .font(.system(size: 13))
                .foregroundColor(AbiTheme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 28)
            Button(action: onRetry) {
                Text("Erneut versuchen")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(AbiTheme.onAccent)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(AbiTheme.accentCyan)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }


    private var settingsSheetContent: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Einstellungen & Info")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(AbiTheme.textPrimary)
                Spacer()
                Button("Fertig") {
                    showSettingsSheet = false
                }
                .foregroundColor(AbiTheme.accentCyan)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Bevorzugte Sprache (Animes)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(AbiTheme.textSecondary)

                ForEach(LanguageOption.allCases, id: \.self) { lang in
                    let selected = storage.preferredLanguage == lang
                    Button {
                        storage.preferredLanguage = lang
                    } label: {
                        HStack {
                            Text(lang.fullLabel)
                                .font(.system(size: 14, weight: selected ? .bold : .regular))
                                .foregroundColor(AbiTheme.textPrimary)
                            Spacer()
                            if selected {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(AbiTheme.accentCyan)
                            }
                        }
                        .padding(12)
                        .background(selected ? AbiTheme.accentCyan.opacity(0.18) : AbiTheme.bgSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                }
            }

            Divider().background(Color.white.opacity(0.1))

            VStack(alignment: .leading, spacing: 8) {
                Text("ABI-TV iOS • Version v\(UpdateManager.getCurrentVersionName())")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(AbiTheme.textSecondary)

                Text("DNS-over-HTTPS (Cloudflare 1.1.1.1) aktiv zur Umgehung von CUII-Netzsperren.")
                    .font(.system(size: 11))
                    .foregroundColor(AbiTheme.textMuted)

                Button {
                    isCheckingUpdate = true
                    updateStatusMessage = nil
                    Task {
                        let result = await UpdateManager.checkForUpdate(forceCheck: true)
                        await MainActor.run {
                            isCheckingUpdate = false
                            switch result {
                            case .available(let release):
                                pendingUpdateRelease = release
                                showSettingsSheet = false
                            case .upToDate:
                                updateStatusMessage = "Du nutzt bereits die aktuellste Version (v\(UpdateManager.getCurrentVersionName()))."
                            case .failed(let msg):
                                updateStatusMessage = msg
                            }
                        }
                    }
                } label: {
                    HStack {
                        if isCheckingUpdate {
                            ProgressView().tint(AbiTheme.onAccent)
                        }
                        Text(isCheckingUpdate ? "Prüfe auf GitHub..." : "Nach Updates suchen")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(AbiTheme.onAccent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(AbiTheme.accentCyan)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
                .disabled(isCheckingUpdate)

                if let msg = updateStatusMessage {
                    Text(msg)
                        .font(.system(size: 12))
                        .foregroundColor(AbiTheme.accentEmerald)
                }
            }

            Spacer()
        }
        .padding(20)
        .background(AbiTheme.bgElevated.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }


}
