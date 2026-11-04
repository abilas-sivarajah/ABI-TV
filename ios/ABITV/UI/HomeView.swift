import SwiftUI

public enum MobileTab: Int, CaseIterable, Identifiable {
    case home = 0
    case movies = 1
    case series = 2
    case liveTv = 3
    case search = 4
    case watchlist = 5

    public var id: Int { rawValue }

    public var title: String {
        switch self {
        case .home: return "Animes"
        case .movies: return "Filme"
        case .series: return "Serien"
        case .liveTv: return "Live TV"
        case .search: return "Suche"
        case .watchlist: return "Merkliste"
        }
    }

    public var iconName: String {
        switch self {
        case .home: return "sparkles.tv"
        case .movies: return "film"
        case .series: return "tv.inset.filled"
        case .liveTv: return "antenna.radiowaves.left.and.right"
        case .search: return "magnifyingglass"
        case .watchlist: return "bookmark.fill"
        }
    }
}

public struct HomeView: View {
    public var onSelectItem: (AnimeCard) -> Void
    public var onPlayDirect: (_ entry: WatchProgressEntry) -> Void
    public var onPlayLiveChannel: (LiveTvChannel) -> Void
    public var onOpenCastDialog: () -> Void
    public var onTriggerUpdate: (AppRelease) -> Void

    @ObservedObject private var storage = LocalStorage.shared
    @State private var selectedTab: MobileTab = .home

    // Anime Catalog
    @State private var animeSections: [CatalogSection] = []
    @State private var selectedAnimeCategory: String = "OVERVIEW"
    @State private var animeCategoryItems: [AnimeCard] = []
    @State private var isLoadingHome: Bool = true
    @State private var isLoadingAnimeCategory: Bool = false
    @State private var homeError: String? = nil

    // Movies Catalog
    @State private var movieSections: [CatalogSection] = []
    @State private var selectedMovieCategory: String = "OVERVIEW"
    @State private var movieCategoryItems: [AnimeCard] = []
    @State private var moviePage: Int = 1
    @State private var isLoadingMovies: Bool = false
    @State private var isLoadingMoreMovies: Bool = false

    // Series Catalog
    @State private var seriesSections: [CatalogSection] = []
    @State private var selectedSeriesCategory: String = "OVERVIEW"
    @State private var seriesCategoryItems: [AnimeCard] = []
    @State private var isLoadingSeries: Bool = false
    @State private var isLoadingSeriesCategory: Bool = false
    @State private var seriesError: String? = nil

    // Search
    @State private var searchQuery: String = ""
    @State private var searchScope: SearchScope = .all
    @State private var searchResults: [AnimeCard] = []
    @State private var isSearching: Bool = false
    @State private var searchTask: Task<Void, Never>? = nil

    // Settings Sheet
    @State private var showSettingsSheet: Bool = false
    @State private var isCheckingUpdate: Bool = false
    @State private var updateStatusMessage: String? = nil

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
                case .home:
                    animeHomeTab
                case .movies:
                    moviesTab
                case .series:
                    seriesTab
                case .liveTv:
                    LiveTvView(onPlayChannel: onPlayLiveChannel)
                case .search:
                    searchTab
                case .watchlist:
                    watchlistTab
                }
            }

            MiniRemoteBar()

            bottomTabBar
        }
        .background(AbiTheme.bgPrimary.ignoresSafeArea())
        .onAppear {
            if animeSections.isEmpty {
                loadAnimeCatalog()
            }
        }
        .onChange(of: selectedTab) { newTab in
            switch newTab {
            case .movies where movieSections.isEmpty && !isLoadingMovies:
                loadMoviesCatalog()
            case .series where seriesSections.isEmpty && !isLoadingSeries:
                loadSeriesCatalog()
            default:
                break
            }
        }
        .sheet(isPresented: $showSettingsSheet) {
            settingsSheetContent
                .presentationDetents([.medium, .large])
        }
    }

    private var topHeaderBar: some View {
        HStack(spacing: 12) {
            HStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(
                            LinearGradient(
                                colors: [AbiTheme.accentBlue, AbiTheme.accentCyan],
                                startPoint: .bottomLeading,
                                endPoint: .topTrailing
                            )
                        )
                        .frame(width: 30, height: 30)
                    Image(systemName: "play.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                }

                Text("ABI-TV")
                    .font(.system(size: 20, weight: .black))
                    .foregroundColor(AbiTheme.accentCyan)
            }

            Spacer()

            CastActionButton(onClick: onOpenCastDialog)

            Button {
                updateStatusMessage = nil
                showSettingsSheet = true
            } label: {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(AbiTheme.textSecondary)
                    .frame(width: 36, height: 36)
                    .background(AbiTheme.bgElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.white.opacity(0.08), lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(AbiTheme.bgPrimary)
    }

    private var bottomTabBar: some View {
        HStack(spacing: 0) {
            ForEach(MobileTab.allCases) { tab in
                let isSelected = selectedTab == tab
                Button {
                    if tab == .search && selectedTab != .search {
                        switch selectedTab {
                        case .movies: searchScope = .movies
                        case .series: searchScope = .series
                        case .home: searchScope = .animes
                        default: searchScope = .all
                        }
                    }
                    selectedTab = tab
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: tab.iconName)
                            .font(.system(size: 16, weight: isSelected ? .bold : .regular))
                            .foregroundColor(isSelected ? AbiTheme.accentCyan : AbiTheme.textMuted)
                        Text(tab.title)
                            .font(.system(size: 10, weight: isSelected ? .bold : .medium))
                            .foregroundColor(isSelected ? AbiTheme.accentCyan : AbiTheme.textMuted)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 6)
        .background(AbiTheme.bgSurface)
        .overlay(
            Rectangle()
                .fill(Color.white.opacity(0.08))
                .frame(height: 1),
            alignment: .top
        )
    }

    // MARK: - Animes Tab

    private var animeHomeTab: some View {
        Group {
            if isLoadingHome {
                loadingStateView(text: "Lade Animes von AniWorld...")
            } else if let error = homeError {
                errorStateView(message: error, onRetry: loadAnimeCatalog)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 20) {
                        let animeContinue = storage.progressList.filter {
                            !FilmpalastScraper.isFilmpalastSlug($0.animeSlug) &&
                            !SerienStreamScraper.isStoSlug($0.animeSlug) &&
                            !HdFilmeScraper.isHdFilmeSlug($0.animeSlug)
                        }
                        if !animeContinue.isEmpty {
                            continueWatchingSection(entries: animeContinue)
                        }

                        let animeChips = [("OVERVIEW", "🔥 Highlights")] + AniWorldScraper.genreCategories.map { ($0.path, $0.label) }
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(animeChips, id: \.0) { path, label in
                                    let selected = selectedAnimeCategory == path
                                    Button {
                                        selectedAnimeCategory = path
                                        if path != "OVERVIEW" {
                                            loadAnimeCategory(path: path)
                                        }
                                    } label: {
                                        Text(label)
                                            .font(.system(size: 12, weight: selected ? .bold : .medium))
                                            .foregroundColor(selected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 8)
                                            .background(selected ? AbiTheme.accentCyan.opacity(0.18) : AbiTheme.bgSurface)
                                            .clipShape(Capsule())
                                            .overlay(
                                                Capsule()
                                                    .stroke(selected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                                            )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 16)
                        }

                        if selectedAnimeCategory == "OVERVIEW" {
                            ForEach(animeSections) { section in
                                catalogRowSection(section: section)
                            }
                        } else if isLoadingAnimeCategory {
                            loadingStateView(text: "Lade Kategorie...")
                                .frame(height: 220)
                        } else {
                            LazyVGrid(
                                columns: [GridItem(.adaptive(minimum: 112), spacing: 12)],
                                spacing: 14
                            ) {
                                ForEach(animeCategoryItems) { item in
                                    MobileAnimeCard(
                                        anime: item,
                                        width: 116,
                                        isBookmarked: storage.isInWatchlist(item.slug),
                                        onClick: { onSelectItem(item) }
                                    )
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                    .padding(.vertical, 12)
                }
                .refreshable {
                    loadAnimeCatalog()
                }
            }
        }
    }

    // MARK: - Movies Tab

    private var moviesTab: some View {
        Group {
            if isLoadingMovies && movieSections.isEmpty && movieCategoryItems.isEmpty {
                loadingStateView(text: "Lade Filme von Filmpalast...")
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 20) {
                        let movieContinue = storage.progressList.filter {
                            FilmpalastScraper.isFilmpalastSlug($0.animeSlug) ||
                            (HdFilmeScraper.isHdFilmeSlug($0.animeSlug) && $0.seasonLabel == "Film")
                        }
                        if !movieContinue.isEmpty {
                            continueWatchingSection(entries: movieContinue)
                        }

                        let movieChips = [("OVERVIEW", "🔥 Highlights")] + FilmpalastScraper.categories
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(movieChips, id: \.0) { path, label in
                                    let selected = selectedMovieCategory == path
                                    Button {
                                        selectedMovieCategory = path
                                        if path != "OVERVIEW" {
                                            loadMovieCategory(path: path, page: 1)
                                        }
                                    } label: {
                                        Text(label)
                                            .font(.system(size: 12, weight: selected ? .bold : .medium))
                                            .foregroundColor(selected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 8)
                                            .background(selected ? AbiTheme.accentCyan.opacity(0.18) : AbiTheme.bgSurface)
                                            .clipShape(Capsule())
                                            .overlay(
                                                Capsule()
                                                    .stroke(selected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                                            )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 16)
                        }

                        if selectedMovieCategory == "OVERVIEW" {
                            ForEach(movieSections) { section in
                                catalogRowSection(section: section)
                            }
                        } else if isLoadingMovies {
                            loadingStateView(text: "Lade Kategorie...")
                                .frame(height: 220)
                        } else if !movieCategoryItems.isEmpty {
                            LazyVGrid(
                                columns: [GridItem(.adaptive(minimum: 112), spacing: 12)],
                                spacing: 14
                            ) {
                                ForEach(movieCategoryItems) { item in
                                    MobileAnimeCard(
                                        anime: item,
                                        width: 116,
                                        isBookmarked: storage.isInWatchlist(item.slug),
                                        onClick: { onSelectItem(item) }
                                    )
                                }
                            }
                            .padding(.horizontal, 16)

                            HStack {
                                Spacer()
                                Button {
                                    loadMovieCategory(path: selectedMovieCategory, page: moviePage + 1)
                                } label: {
                                    HStack(spacing: 8) {
                                        if isLoadingMoreMovies {
                                            ProgressView().tint(AbiTheme.onAccent)
                                        }
                                        Text(isLoadingMoreMovies ? "Lade mehr..." : "Mehr Filme laden")
                                            .font(.system(size: 13, weight: .bold))
                                            .foregroundColor(AbiTheme.onAccent)
                                    }
                                    .padding(.horizontal, 20)
                                    .padding(.vertical, 12)
                                    .background(AbiTheme.accentCyan)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                                .buttonStyle(.plain)
                                .disabled(isLoadingMoreMovies)
                                Spacer()
                            }
                        } else {
                            ForEach(movieSections) { section in
                                catalogRowSection(section: section)
                            }
                        }
                    }
                    .padding(.vertical, 12)
                }
                .refreshable {
                    loadMoviesCatalog()
                }
            }
        }
    }

    // MARK: - Series Tab

    private var seriesTab: some View {
        Group {
            if isLoadingSeries && seriesSections.isEmpty {
                loadingStateView(text: "Lade Serien von S.to...")
            } else if let error = seriesError {
                errorStateView(message: error, onRetry: loadSeriesCatalog)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 20) {
                        let seriesContinue = storage.progressList.filter {
                            SerienStreamScraper.isStoSlug($0.animeSlug) ||
                            (HdFilmeScraper.isHdFilmeSlug($0.animeSlug) && $0.seasonLabel != "Film")
                        }
                        if !seriesContinue.isEmpty {
                            continueWatchingSection(entries: seriesContinue)
                        }

                        let seriesChips = [("OVERVIEW", "🔥 Highlights")] + SerienStreamScraper.categories.map { ($0.path, $0.label) }
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(seriesChips, id: \.0) { path, label in
                                    let selected = selectedSeriesCategory == path
                                    Button {
                                        selectedSeriesCategory = path
                                        if path != "OVERVIEW" {
                                            loadSeriesCategory(path: path)
                                        }
                                    } label: {
                                        Text(label)
                                            .font(.system(size: 12, weight: selected ? .bold : .medium))
                                            .foregroundColor(selected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 8)
                                            .background(selected ? AbiTheme.accentCyan.opacity(0.18) : AbiTheme.bgSurface)
                                            .clipShape(Capsule())
                                            .overlay(
                                                Capsule()
                                                    .stroke(selected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                                            )
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 16)
                        }

                        if selectedSeriesCategory == "OVERVIEW" {
                            ForEach(seriesSections) { section in
                                catalogRowSection(section: section)
                            }
                        } else if isLoadingSeriesCategory {
                            loadingStateView(text: "Lade Serien-Kategorie...")
                                .frame(height: 220)
                        } else {
                            LazyVGrid(
                                columns: [GridItem(.adaptive(minimum: 112), spacing: 12)],
                                spacing: 14
                            ) {
                                ForEach(seriesCategoryItems) { item in
                                    MobileAnimeCard(
                                        anime: item,
                                        width: 116,
                                        isBookmarked: storage.isInWatchlist(item.slug),
                                        onClick: { onSelectItem(item) }
                                    )
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                    .padding(.vertical, 12)
                }
                .refreshable {
                    loadSeriesCatalog()
                }
            }
        }
    }

    // MARK: - Search Tab

    private var searchTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(AbiTheme.textMuted)
                    TextField("Anime, Serie oder Film suchen...", text: $searchQuery)
                        .font(.system(size: 14))
                        .foregroundColor(AbiTheme.textPrimary)
                        .autocorrectionDisabled()
                        .onChange(of: searchQuery) { newVal in
                            triggerSearch(query: newVal, scope: searchScope)
                        }
                    if !searchQuery.isEmpty {
                        Button {
                            searchQuery = ""
                            searchResults = []
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(AbiTheme.textMuted)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(12)
                .background(AbiTheme.bgSurface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.08), lineWidth: 1)
                )

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(SearchScope.allCases) { scope in
                            let selected = searchScope == scope
                            Button {
                                searchScope = scope
                                triggerSearch(query: searchQuery, scope: scope)
                            } label: {
                                Text("\(scope.icon) \(scope.label)")
                                    .font(.system(size: 12, weight: selected ? .bold : .medium))
                                    .foregroundColor(selected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(selected ? AbiTheme.accentCyan.opacity(0.18) : AbiTheme.bgSurface)
                                    .clipShape(Capsule())
                                    .overlay(
                                        Capsule()
                                            .stroke(selected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)

            if isSearching {
                loadingStateView(text: "Suche nach \"\(searchQuery)\"...")
            } else if searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 {
                VStack(spacing: 8) {
                    Spacer()
                    Image(systemName: "sparkle.magnifyingglass")
                        .font(.system(size: 38))
                        .foregroundColor(AbiTheme.textMuted)
                    Text("Gib mindestens 2 Zeichen ein, um AniWorld, S.to, Filmpalast & HD-Filme zu durchsuchen.")
                        .font(.system(size: 13))
                        .foregroundColor(AbiTheme.textMuted)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 36)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
            } else if searchResults.isEmpty {
                VStack(spacing: 8) {
                    Spacer()
                    Text("Keine Treffer für \"\(searchQuery)\" gefunden.")
                        .font(.system(size: 14))
                        .foregroundColor(AbiTheme.textMuted)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 112), spacing: 12)],
                        spacing: 14
                    ) {
                        ForEach(searchResults) { card in
                            MobileAnimeCard(
                                anime: card,
                                width: 116,
                                isBookmarked: storage.isInWatchlist(card.slug),
                                onClick: { onSelectItem(card) }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
    }

    // MARK: - Watchlist Tab

    private var watchlistTab: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 20) {
                if !storage.progressList.isEmpty {
                    continueWatchingSection(entries: storage.progressList)
                }

                VStack(alignment: .leading, spacing: 12) {
                    Text("Meine Merkliste (\(storage.watchlist.count))")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(AbiTheme.textPrimary)
                        .padding(.horizontal, 16)

                    if storage.watchlist.isEmpty {
                        Text("Deine Merkliste ist noch leer. Tippe bei einem Anime, einer Serie oder einem Film auf '+', um ihn hier zu speichern.")
                            .font(.system(size: 13))
                            .foregroundColor(AbiTheme.textMuted)
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                    } else {
                        LazyVGrid(
                            columns: [GridItem(.adaptive(minimum: 112), spacing: 12)],
                            spacing: 14
                        ) {
                            ForEach(storage.watchlist) { card in
                                MobileAnimeCard(
                                    anime: card,
                                    width: 116,
                                    isBookmarked: true,
                                    onClick: { onSelectItem(card) }
                                )
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }
            }
            .padding(.vertical, 14)
        }
    }

    // MARK: - Shared Sections

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

    private func catalogRowSection(section: CatalogSection) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(section.title)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
                .padding(.horizontal, 16)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(section.items) { item in
                        MobileAnimeCard(
                            anime: item,
                            isBookmarked: storage.isInWatchlist(item.slug),
                            onClick: { onSelectItem(item) }
                        )
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
                                showSettingsSheet = false
                                onTriggerUpdate(release)
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

    // MARK: - Data Loading

    private func loadAnimeCatalog() {
        isLoadingHome = true
        homeError = nil
        Task {
            do {
                let sections = try await AniWorldScraper.fetchHomeCatalog()
                await MainActor.run {
                    self.animeSections = sections
                    self.isLoadingHome = false
                }
            } catch {
                await MainActor.run {
                    self.homeError = error.localizedDescription
                    self.isLoadingHome = false
                }
            }
        }
    }

    private func loadAnimeCategory(path: String) {
        isLoadingAnimeCategory = true
        Task {
            let items = (try? await AniWorldScraper.fetchGenrePage(path)) ?? []
            await MainActor.run {
                self.animeCategoryItems = items
                self.isLoadingAnimeCategory = false
            }
        }
    }

    private func loadMoviesCatalog() {
        isLoadingMovies = true
        Task {
            let sections = await FilmpalastScraper.fetchHomeSections()
            let categoryItems: [AnimeCard]
            if selectedMovieCategory != "OVERVIEW" {
                categoryItems = await FilmpalastScraper.fetchCategory(selectedMovieCategory, page: 1)
            } else {
                categoryItems = []
            }
            await MainActor.run {
                self.movieSections = sections
                self.movieCategoryItems = categoryItems
                self.moviePage = 1
                self.isLoadingMovies = false
            }
        }
    }

    private func loadMovieCategory(path: String, page: Int) {
        if page == 1 {
            isLoadingMovies = true
        } else {
            isLoadingMoreMovies = true
        }
        Task {
            let items = await FilmpalastScraper.fetchCategory(path, page: page)
            await MainActor.run {
                if page == 1 {
                    self.movieCategoryItems = items
                    self.moviePage = 1
                    self.isLoadingMovies = false
                } else {
                    var seen = Set(self.movieCategoryItems.map(\.cleanSlug))
                    let fresh = items.filter { seen.insert($0.cleanSlug).inserted }
                    self.movieCategoryItems.append(contentsOf: fresh)
                    self.moviePage = page
                    self.isLoadingMoreMovies = false
                }
            }
        }
    }

    private func loadSeriesCatalog() {
        isLoadingSeries = true
        seriesError = nil
        Task {
            let sections = await SerienStreamScraper.fetchHomeSections()
            await MainActor.run {
                if sections.isEmpty {
                    self.seriesError = "Serien-Katalog konnte nicht geladen werden."
                } else {
                    self.seriesSections = sections
                }
                self.isLoadingSeries = false
            }
        }
    }

    private func loadSeriesCategory(path: String) {
        isLoadingSeriesCategory = true
        Task {
            let items = (try? await SerienStreamScraper.fetchCategory(path)) ?? []
            await MainActor.run {
                self.seriesCategoryItems = items
                self.isLoadingSeriesCategory = false
            }
        }
    }

    private func triggerSearch(query: String, scope: SearchScope) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else {
            searchResults = []
            isSearching = false
            return
        }

        isSearching = true
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }

            var combined: [AnimeCard] = []

            await withTaskGroup(of: [AnimeCard].self) { group in
                if scope == .all || scope == .animes {
                    group.addTask {
                        await AniWorldScraper.searchAnimes(trimmed)
                    }
                }
                if scope == .all || scope == .series {
                    group.addTask {
                        await SerienStreamScraper.searchSeries(trimmed)
                    }
                }
                if scope == .all || scope == .movies {
                    group.addTask {
                        await FilmpalastScraper.searchMovies(trimmed)
                    }
                }
                if scope == .all || scope == .movies || scope == .series {
                    group.addTask {
                        let hdResults = await HdFilmeScraper.searchMovies(trimmed)
                        if scope == .movies {
                            return hdResults.filter { $0.slug.contains("/filme") }
                        } else if scope == .series {
                            return hdResults.filter { $0.slug.contains("/serien") }
                        }
                        return hdResults
                    }
                }

                for await res in group {
                    combined.append(contentsOf: res)
                }
            }

            if Task.isCancelled { return }
            var seen = Set<String>()
            let deduped = combined.filter { seen.insert($0.cleanSlug).inserted }

            await MainActor.run {
                self.searchResults = deduped
                self.isSearching = false
            }
        }
    }
}
