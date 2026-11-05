import Foundation

/// Daten für Titelbild und Info-Kachel (wie auf PC und TV): Details und Bilder eines Titels,
/// Beschriftungen und Letterboxd-Bewertung. Die Darstellung liegt in den Views.
public enum TitlePreview {

    /// Anzahl der Vorschläge im Titelbild.
    public static let heroCount = 6
    /// So lange bleibt ein Vorschlag stehen, bevor das Titelbild weiterblättert.
    public static let heroRotateSeconds: UInt64 = 9

    private static let details = DetailStore()

    private actor DetailStore {
        var details: [String: AnimeDetail] = [:]
        var artwork: [String: TitleArtwork] = [:]
        func detail(_ slug: String) -> AnimeDetail? { details[slug] }
        func setDetail(_ slug: String, _ value: AnimeDetail) { details[slug] = value }
        func art(_ slug: String) -> TitleArtwork? { artwork[slug] }
        func setArt(_ slug: String, _ value: TitleArtwork) { artwork[slug] = value }
    }

    /// Details aus derselben Quelle wie die Detailseite; nil bei Netzwerkfehlern.
    public static func loadDetail(_ card: AnimeCard) async -> AnimeDetail? {
        let slug = card.cleanSlug
        if let cached = await details.detail(slug) { return cached }
        let detail: AnimeDetail?
        if FilmpalastScraper.isFilmpalastSlug(slug) {
            detail = try? await FilmpalastScraper.fetchDetail(slug)
        } else if HdFilmeScraper.isHdFilmeSlug(slug) {
            detail = try? await HdFilmeScraper.fetchDetail(slug)
        } else if SerienStreamScraper.isStoSlug(slug) {
            detail = try? await SerienStreamScraper.fetchDetail(slug)
        } else {
            detail = try? await AniWorldScraper.fetchAnimeDetail(slug)
        }
        if let detail { await details.setDetail(slug, detail) }
        return detail
    }

    /// Art des Titels: "Film", "Serie" oder "Anime".
    public static func kindLabel(_ card: AnimeCard, _ detail: AnimeDetail?) -> String {
        if card.isSto { return "Serie" }
        if card.isFilmpalast { return "Film" }
        if card.isHdFilme { return (detail?.seasons.isEmpty == false) ? "Serie" : "Film" }
        return "Anime"
    }

    /// Breites Hintergrundbild (TVmaze, Kitsu/AniList, Letterboxd); bei Filmen mit IMDb-Nummer.
    public static func loadArtwork(_ card: AnimeCard, _ detail: AnimeDetail?) async -> TitleArtwork? {
        let slug = card.cleanSlug
        if let cached = await details.art(slug) { return cached }
        let title = (detail?.title).flatMap { $0.isEmpty ? nil : $0 } ?? card.title
        let year = detail.flatMap { HtmlParser.firstMatch(in: $0.years, pattern: #"((?:19|20)\d{2})"#) }.flatMap(Int.init)
        let kind: KeylessArtwork.Kind
        switch kindLabel(card, detail) {
        case "Anime": kind = .anime
        case "Film": kind = .movie
        default: kind = .tv
        }
        let found = await KeylessArtwork.find(title: title, year: year, kind: kind)
        if let found { await details.setArt(slug, found) }
        return found
    }

    /// Letterboxd-Bewertung eines Films; Serien und Anime: nil.
    public static func letterboxdRating(_ artwork: TitleArtwork?) async -> LetterboxdRatings.Rating? {
        guard let artwork, artwork.isMovie, let imdb = artwork.imdbId else { return nil }
        return await LetterboxdRatings.byImdb(imdb)
    }

    /// Überschrift im Titelbild, z. B. "FILM  ·  FILMPALAST HIGHLIGHT".
    public static func heroLabel(_ card: AnimeCard, _ detail: AnimeDetail?) -> String {
        let source: String
        if card.isFilmpalast { source = "FILMPALAST HIGHLIGHT" }
        else if card.isSto { source = "SERIENSTREAM HIGHLIGHT" }
        else if card.isHdFilme { source = "HD-FILME HIGHLIGHT" }
        else { source = "ANIWORLD EMPFEHLUNG" }
        return kindLabel(card, detail).uppercased() + "  ·  " + source
    }

    /// Untertitel einer Karte nur, wenn er wirklich ein Genre ist ("Serie • S.to" sagt nichts aus).
    public static func realGenre(_ card: AnimeCard) -> String {
        let text = card.genreOrSubtitle.trimmingCharacters(in: .whitespaces)
        let markers = ["S.to", "HD Stream", "HD-Filme", "Deutsch", "Serie •", "Film •"]
        return markers.contains { text.localizedCaseInsensitiveContains($0) } ? "" : text
    }

    /// Eckdaten wie "Action", "Fantasy", "2023", "FSK 12".
    public static func metaLine(_ card: AnimeCard, _ detail: AnimeDetail?) -> [String] {
        var list: [String] = []
        let genres = (detail?.genres ?? []).filter { !$0.isEmpty }.prefix(2)
        if !genres.isEmpty { list += genres } else if !realGenre(card).isEmpty { list.append(realGenre(card)) }
        if let years = detail?.years, !years.isEmpty { list.append(years) }
        if let age = detail?.ageRating, !age.isEmpty { list.append(ageLabel(age)) }
        return list
    }

    /// Eckdaten als kleine Kästchen: Art, Jahr, Bewertung der Seite (nicht bei Filmen), FSK.
    public static func chips(_ card: AnimeCard, _ detail: AnimeDetail?) -> [String] {
        let kind = kindLabel(card, detail)
        var list = [kind]
        if let years = detail?.years, !years.isEmpty { list.append(years) }
        if kind != "Film", let rating = siteRating(detail?.rating) { list.append(rating) }
        if let age = detail?.ageRating, !age.isEmpty { list.append(ageLabel(age)) }
        return list
    }

    /// Genres für die Info-Kachel; ohne Details das Genre der Karte.
    public static func genreLine(_ card: AnimeCard, _ detail: AnimeDetail?) -> String {
        let genres = (detail?.genres ?? []).filter { !$0.isEmpty }.prefix(3)
        return genres.isEmpty ? realGenre(card) : genres.joined(separator: ", ")
    }

    /// Zufällige Titel fürs Titelbild (nur mit Cover, ohne Doppelte).
    public static func randomPicks(_ pool: [AnimeCard], count: Int = heroCount) -> [AnimeCard] {
        var seen = Set<String>()
        let unique = pool.filter { !$0.coverUrl.isEmpty && seen.insert($0.cleanSlug).inserted }
        return Array(unique.shuffled().prefix(count))
    }

    private static func ageLabel(_ raw: String) -> String {
        raw.allSatisfy(\.isNumber) ? "FSK \(raw)" : raw
    }

    private static func siteRating(_ raw: String?) -> String? {
        guard let raw, let number = HtmlParser.firstMatch(in: raw, pattern: #"(\d+(?:[.,]\d+)?)"#) else { return nil }
        return "★ " + number.replacingOccurrences(of: ".", with: ",")
    }
}
