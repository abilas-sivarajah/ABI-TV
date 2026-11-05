import SwiftUI

// Bausteine der neuen Startseite (wie auf PC und TV): Titelbild-Karussell, Info-Blatt,
// LB-Bewertung, Umschalter Übersicht/Genre und Letterboxd-Reihen.

let letterboxdGreen = Color(red: 0x00 / 255.0, green: 0xE0 / 255.0, blue: 0x54 / 255.0)

/// Lädt Details, Bild und (bei Filmen) Letterboxd-Bewertung eines Titels für Titelbild und Info-Blatt.
@MainActor
final class TitlePreviewModel: ObservableObject {
    @Published var detail: AnimeDetail?
    @Published var artwork: TitleArtwork?
    @Published var rating: LetterboxdRatings.Rating?
    private var loadedSlug: String?

    func load(_ card: AnimeCard) async {
        guard loadedSlug != card.cleanSlug else { return }
        loadedSlug = card.cleanSlug
        detail = nil
        artwork = nil
        rating = nil
        let loadedDetail = await TitlePreview.loadDetail(card)
        guard loadedSlug == card.cleanSlug else { return }
        detail = loadedDetail
        let loadedArtwork = await TitlePreview.loadArtwork(card, loadedDetail)
        guard loadedSlug == card.cleanSlug else { return }
        artwork = loadedArtwork
        let loadedRating = await TitlePreview.letterboxdRating(loadedArtwork)
        guard loadedSlug == card.cleanSlug else { return }
        rating = loadedRating
    }
}

/// Kleines Kästchen "LB ★ 3,9" (Letterboxd-Bewertung).
struct LetterboxdChip: View {
    let rating: LetterboxdRatings.Rating
    var large: Bool = false

    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(letterboxdGreen).frame(width: 6, height: 6)
            Text("LB" + (rating.shortLabel.map { " ★ \($0)" } ?? ""))
                .font(.system(size: large ? 12 : 11, weight: .semibold))
                .foregroundColor(AbiTheme.textPrimary)
        }
        .padding(.horizontal, large ? 10 : 8)
        .padding(.vertical, large ? 4 : 3)
        .background(AbiTheme.bgElevated)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(letterboxdGreen.opacity(0.45), lineWidth: 1))
    }
}

/// Graues Kästchen für Eckdaten (Art, Jahr, FSK).
struct InfoChip: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .foregroundColor(AbiTheme.textSecondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(AbiTheme.bgElevated)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.white.opacity(0.1), lineWidth: 1))
    }
}

/// Bild aus dem Netz, solange es lädt (oder fehlt) mit der Hintergrundfarbe.
struct RemoteImage: View {
    let url: String?
    var contentMode: ContentMode = .fill

    var body: some View {
        if let url, let parsed = URL(string: url), !url.isEmpty {
            AsyncImage(url: parsed) { phase in
                switch phase {
                case .success(let image):
                    image.resizable().aspectRatio(contentMode: contentMode)
                default:
                    AbiTheme.bgElevated
                }
            }
        } else {
            AbiTheme.bgElevated
        }
    }
}

/// Weiß gefüllter (Hauptaktion) bzw. halbtransparenter Knopf im Titelbild und Info-Blatt.
struct HeroButton: View {
    let title: String
    let systemImage: String
    var primary: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemImage).font(.system(size: 13, weight: .bold))
                Text(title).font(.system(size: 13, weight: .bold)).lineLimit(1)
            }
            .foregroundColor(primary ? Color.black : AbiTheme.textPrimary)
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .background(primary ? Color.white : Color.white.opacity(0.2))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Titelbild

/// Titelbild-Karussell: bis zu sechs Vorschläge zum Wischen, blättert alle 9 Sekunden weiter.
struct HeroCarousel: View {
    let items: [AnimeCard]
    @ObservedObject var storage: LocalStorage
    let onPlay: (AnimeCard) -> Void
    let onDetail: (AnimeCard) -> Void

    @State private var index = 0

    var body: some View {
        TabView(selection: $index) {
            ForEach(Array(items.enumerated()), id: \.element.cleanSlug) { position, card in
                HeroCard(
                    card: card,
                    isBookmarked: storage.isInWatchlist(card.slug),
                    onPlay: { onPlay(card) },
                    onToggleWatchlist: { _ = storage.toggleWatchlist(card) },
                    onDetail: { onDetail(card) }
                )
                .tag(position)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .always))
        .frame(height: 430)
        .task(id: items.map(\.cleanSlug)) {
            index = 0
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: TitlePreview.heroRotateSeconds * 1_000_000_000)
                if Task.isCancelled || items.isEmpty { break }
                withAnimation(.easeInOut(duration: 0.5)) { index = (index + 1) % items.count }
            }
        }
    }
}

/// Ein Vorschlag im Titelbild: breites Bild, darüber Art, Titel (oder Logo), Eckdaten, Knöpfe.
struct HeroCard: View {
    let card: AnimeCard
    let isBookmarked: Bool
    let onPlay: () -> Void
    let onToggleWatchlist: () -> Void
    let onDetail: () -> Void

    @StateObject private var model = TitlePreviewModel()

    private var backdrop: String? {
        model.artwork?.backdropUrl ?? model.detail?.backdropUrl.nilIfEmpty
    }

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            GeometryReader { geo in
                Group {
                    if let backdrop {
                        RemoteImage(url: backdrop)
                    } else {
                        RemoteImage(url: card.coverUrl).blur(radius: 24).opacity(0.7)
                    }
                }
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()
            }

            LinearGradient(
                stops: [
                    .init(color: .clear, location: 0.25),
                    .init(color: AbiTheme.bgPrimary.opacity(0.75), location: 0.6),
                    .init(color: AbiTheme.bgPrimary, location: 1)
                ],
                startPoint: .top,
                endPoint: .bottom
            )

            VStack(alignment: .leading, spacing: 7) {
                Text(TitlePreview.heroLabel(card, model.detail))
                    .font(.system(size: 10, weight: .bold))
                    .kerning(1.2)
                    .foregroundColor(AbiTheme.accentCyan)
                    .lineLimit(1)

                let title = model.detail?.title.nilIfEmpty ?? card.title
                if let logo = model.artwork?.logoUrl {
                    RemoteImage(url: logo, contentMode: .fit)
                        .frame(maxWidth: 230, maxHeight: 70, alignment: .leading)
                        .accessibilityLabel(title)
                } else {
                    Text(title)
                        .font(.system(size: 26, weight: .heavy))
                        .foregroundColor(AbiTheme.textPrimary)
                        .lineLimit(2)
                }

                let meta = TitlePreview.metaLine(card, model.detail)
                if !meta.isEmpty || model.rating?.average != nil {
                    HStack(spacing: 8) {
                        if !meta.isEmpty {
                            Text(meta.joined(separator: "  •  "))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(AbiTheme.textPrimary.opacity(0.9))
                                .lineLimit(1)
                        }
                        if let rating = model.rating, rating.average != nil {
                            LetterboxdChip(rating: rating)
                        }
                    }
                }

                if let description = model.detail?.description.nilIfEmpty {
                    Text(description)
                        .font(.system(size: 12))
                        .foregroundColor(AbiTheme.textPrimary.opacity(0.8))
                        .lineLimit(2)
                }

                HStack(spacing: 8) {
                    HeroButton(title: "Abspielen", systemImage: "play.fill", primary: true, action: onPlay)
                    HeroButton(title: "Watchlist", systemImage: isBookmarked ? "checkmark" : "plus", action: onToggleWatchlist)
                    HeroButton(title: "Details", systemImage: "info.circle", action: onDetail)
                }
                .padding(.top, 4)
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 34)
        }
        .clipped()
        .contentShape(Rectangle())
        .task(id: card.cleanSlug) { await model.load(card) }
    }
}

// MARK: - Info-Blatt

/// Info-Blatt zu einem Titel (wie die Info-Kachel am PC): Poster, Eckdaten, LB, Genre, Beschreibung, Knöpfe.
struct TitleInfoSheet: View {
    let card: AnimeCard
    @ObservedObject var storage: LocalStorage
    let onPlay: () -> Void
    let onDetail: () -> Void
    let onClose: () -> Void

    @StateObject private var model = TitlePreviewModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Spacer()
                    // Schließen auch ohne Herunterwischen
                    Button(action: onClose) {
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
                .padding(.bottom, -6)
                HStack(alignment: .top, spacing: 14) {
                    RemoteImage(url: model.detail?.coverUrl.nilIfEmpty ?? card.coverUrl)
                        .frame(width: 110, height: 165)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.white.opacity(0.15), lineWidth: 1))

                    VStack(alignment: .leading, spacing: 8) {
                        let chips = TitlePreview.chips(card, model.detail)
                        FlowChips(chips: chips, rating: chips.first == "Film" ? model.rating : nil)

                        Text(model.detail?.title.nilIfEmpty ?? card.title)
                            .font(.system(size: 19, weight: .heavy))
                            .foregroundColor(AbiTheme.textPrimary)
                            .lineLimit(3)

                        let genres = TitlePreview.genreLine(card, model.detail)
                        if !genres.isEmpty {
                            (Text("Genre: ").foregroundColor(AbiTheme.textSecondary)
                                + Text(genres).foregroundColor(AbiTheme.accentCyan).fontWeight(.semibold))
                                .font(.system(size: 13))
                                .lineLimit(2)
                        }
                    }
                }

                if let description = model.detail?.description.nilIfEmpty {
                    Text(description)
                        .font(.system(size: 13))
                        .foregroundColor(AbiTheme.textSecondary)
                }

                HStack(spacing: 8) {
                    HeroButton(
                        title: storage.getProgressForAnime(card.slug) != nil ? "Weiterschauen" : "Abspielen",
                        systemImage: "play.fill",
                        primary: true,
                        action: onPlay
                    )
                    HeroButton(
                        title: "Watchlist",
                        systemImage: storage.isInWatchlist(card.slug) ? "checkmark" : "plus",
                        action: { _ = storage.toggleWatchlist(card) }
                    )
                    HeroButton(title: "Details", systemImage: "info.circle", action: onDetail)
                }
            }
            .padding(18)
        }
        .background(AbiTheme.bgSurface.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: card.cleanSlug) { await model.load(card) }
    }
}

/// Eckdaten-Kästchen; bei Filmen die LB-Bewertung hinter Art und Jahr.
private struct FlowChips: View {
    let chips: [String]
    let rating: LetterboxdRatings.Rating?

    var body: some View {
        HStack(spacing: 6) {
            ForEach(Array(chips.prefix(2).enumerated()), id: \.offset) { _, chip in InfoChip(text: chip) }
            if let rating, rating.average != nil { LetterboxdChip(rating: rating) }
            ForEach(Array(chips.dropFirst(2).enumerated()), id: \.offset) { _, chip in InfoChip(text: chip) }
        }
    }
}

// MARK: - Umschalter, Überschriften

/// Umschalter "Übersicht & Highlights" / "Genre".
struct CatalogModeSwitcher: View {
    let isOverview: Bool
    let onOverview: () -> Void
    let onGenre: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            pill("Übersicht & Highlights", selected: isOverview, action: onOverview)
            pill("Genre", selected: !isOverview, action: onGenre)
            Spacer()
        }
        .padding(.horizontal, 16)
    }

    private func pill(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13, weight: selected ? .bold : .medium))
                .foregroundColor(selected ? AbiTheme.textPrimary : AbiTheme.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Color.white.opacity(0.14) : Color.clear)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Genres als waagerechte Leiste (auf dem Handy statt der Liste links am PC).
struct GenreChipsBar: View {
    let genres: [(String, String)]
    let selected: String?
    let onSelect: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(genres, id: \.0) { path, label in
                    let isSelected = path == selected
                    Button {
                        onSelect(path)
                    } label: {
                        Text(label.drop { !$0.isLetter }.trimmingCharacters(in: .whitespaces))
                            .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                            .foregroundColor(isSelected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(isSelected ? AbiTheme.accentCyan.opacity(0.16) : AbiTheme.bgSurface)
                            .clipShape(Capsule())
                            .overlay(Capsule().stroke(isSelected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
    }
}

/// Überschrift einer Reihe mit Angabe rechts.
struct SectionHeader: View {
    let title: String
    var subtitle: String = ""

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.system(size: 17, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
                .lineLimit(1)
            Spacer(minLength: 8)
            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(AbiTheme.textMuted)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 16)
    }
}

/// Reihe mit Karten; Tippen öffnet das Info-Blatt.
struct CardRow: View {
    let title: String
    let items: [AnimeCard]
    @ObservedObject var storage: LocalStorage
    var subtitle: String? = nil
    let onOpen: (AnimeCard) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: title, subtitle: subtitle ?? "\(items.count) Titel")
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(items) { item in
                        MobileAnimeCard(
                            anime: item,
                            isBookmarked: storage.isInWatchlist(item.slug),
                            onClick: { onOpen(item) }
                        )
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

// MARK: - Letterboxd

/// Geladene Letterboxd-Filme pro App-Start merken (Reiterwechsel lädt nicht neu).
@MainActor
enum LetterboxdSessionCache {
    static var films: [String: [LetterboxdLists.Film]] = [:]
    static var popularLists: [LetterboxdLists.ListInfo]?
}

/// Reihe mit Filmen einer Letterboxd-Liste/-Watchlist: nur die, die es bei Filmpalast gibt.
struct LetterboxdFilmsRow: View {
    let cacheKey: String
    let title: String
    @ObservedObject var storage: LocalStorage
    let load: () async throws -> [LetterboxdLists.Film]
    let onOpen: (AnimeCard) -> Void

    @State private var films: [LetterboxdLists.Film]?
    @State private var matches: [String: AnimeCard] = [:]
    @State private var checked = 0
    @State private var error: String?

    var body: some View {
        let available = uniqueCards((films ?? []).compactMap { matches[$0.slug] })
        let total = films?.count ?? 0
        let status: String = {
            if let error { return error }
            if films == nil { return "Lade Liste …" }
            if total == 0 { return "Die Liste ist leer oder nicht öffentlich." }
            if checked < total { return "\(available.count) von \(total) bei Filmpalast · prüfe \(total - checked) weitere …" }
            return "\(available.count) von \(total) bei Filmpalast verfügbar"
        }()

        Group {
            if !available.isEmpty {
                CardRow(title: title, items: available, storage: storage, subtitle: status, onOpen: onOpen)
            } else {
                VStack(alignment: .leading, spacing: 6) {
                    SectionHeader(title: title)
                    Text(error == nil && total > 0 && checked >= total ? "Keiner dieser Filme ist bei Filmpalast verfügbar." : status)
                        .font(.system(size: 13))
                        .foregroundColor(error != nil ? AbiTheme.errorRed : AbiTheme.textSecondary)
                        .padding(.horizontal, 16)
                }
            }
        }
        .task(id: cacheKey) { await loadAll() }
    }

    private func uniqueCards(_ cards: [AnimeCard]) -> [AnimeCard] {
        var seen = Set<String>()
        return cards.filter { seen.insert($0.cleanSlug).inserted }
    }

    @MainActor private func loadAll() async {
        var list = LetterboxdSessionCache.films[cacheKey]
        if list == nil {
            // Ein Fehlschlag wird einmal kurz danach wiederholt (Letterboxd antwortet nicht immer sofort)
            for attempt in 0..<2 {
                do {
                    let loaded = try await load()
                    LetterboxdSessionCache.films[cacheKey] = loaded
                    list = loaded
                    break
                } catch LetterboxdLists.ListError.userNotFound {
                    error = "Diesen Letterboxd-Benutzer gibt es nicht."
                    return
                } catch {
                    // Abgebrochen, weil die Reihe gerade neu lädt (z. B. neuer Film in der Liste): kein Fehler
                    if Task.isCancelled || error is CancellationError || (error as? URLError)?.code == .cancelled { return }
                    if attempt == 0 {
                        try? await Task.sleep(nanoseconds: 1_500_000_000)
                        continue
                    }
                    // Bereits geladene Filme bleiben stehen; nur ohne sie gibt es einen Hinweis
                    if films == nil { self.error = "Letterboxd nicht erreichbar." }
                    return
                }
            }
        }
        guard let list else { return }
        error = nil
        films = list
        matches = [:]
        checked = 0
        await withTaskGroup(of: (String, AnimeCard?).self) { group in
            for film in list {
                group.addTask { (film.slug, await LetterboxdLists.findOnFilmpalast(film)) }
            }
            for await (slug, card) in group {
                if let card { matches[slug] = card }
                checked += 1
            }
        }
    }
}

extension String {
    /// nil statt eines leeren Textes.
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
