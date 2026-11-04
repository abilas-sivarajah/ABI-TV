import XCTest
@testable import ABITVCore

final class ABITVTests: XCTestCase {

    // MARK: - Models & Slugs

    func testAnimeCardCleanSlugAndContentType() {
        let anime = AnimeCard(
            slug: "/anime/stream/solo-leveling/staffel-1/episode-2",
            title: "Solo Leveling",
            coverUrl: ""
        )
        XCTAssertEqual(anime.cleanSlug, "solo-leveling")
        XCTAssertFalse(anime.isFilmpalast)
        XCTAssertFalse(anime.isSto)
        XCTAssertFalse(anime.isHdFilme)

        let movie = AnimeCard(
            slug: "filmpalast:/stream/dune-part-two",
            title: "Dune: Part Two",
            coverUrl: ""
        )
        XCTAssertEqual(movie.cleanSlug, "filmpalast:/stream/dune-part-two")
        XCTAssertTrue(movie.isFilmpalast)

        let series = AnimeCard(
            slug: "sto:breaking-bad",
            title: "Breaking Bad",
            coverUrl: ""
        )
        XCTAssertEqual(series.cleanSlug, "sto:breaking-bad")
        XCTAssertTrue(series.isSto)

        let hdMovie = AnimeCard(
            slug: "hdfilme:/filme/interstellar-123.html",
            title: "Interstellar",
            coverUrl: ""
        )
        XCTAssertEqual(hdMovie.cleanSlug, "hdfilme:/filme/interstellar-123.html")
        XCTAssertTrue(hdMovie.isHdFilme)
    }

    func testSerienStreamRefParsing() {
        XCTAssertTrue(SerienStreamRef.matches("sto:the-rookie"))
        XCTAssertEqual(SerienStreamRef.bareSlug("sto:the-rookie"), "the-rookie")

        XCTAssertTrue(SerienStreamRef.matches("https://s.to/serie/dark/staffel-1"))
        XCTAssertEqual(SerienStreamRef.bareSlug("https://s.to/serie/dark/staffel-1"), "dark")

        XCTAssertTrue(SerienStreamRef.matches("/serie/the-boys/staffel-2"))
        XCTAssertEqual(SerienStreamRef.bareSlug("/serie/the-boys/staffel-2"), "the-boys")
    }

    // MARK: - HtmlParser

    func testHtmlParserDecodeEntitiesAndStripTags() {
        let raw = "<div>Ich &amp; Du &ndash; &#x27;Test&#39; &quot;Anime&quot;</div>"
        let stripped = HtmlParser.stripTagsAndDecode(raw)
        XCTAssertEqual(stripped, "Ich & Du – 'Test' \"Anime\"")
    }

    func testHtmlParserAttrAndBlocks() {
        let html = """
        <article class="liste"><a href="/stream/test" title="Test &amp; Co">Link</a></article>
        """
        let blocks = HtmlParser.blocks(in: html, tag: "article")
        XCTAssertEqual(blocks.count, 1)
        XCTAssertEqual(HtmlParser.attr(blocks[0], "title"), "Test & Co")
    }

    // MARK: - AniWorld Scraper HTML Parsing

    func testAniWorldParseDetailAndEpisodeHosters() {
        let sampleHtml = """
        <html>
        <div class="seriesCoverBox"><img data-src="/public/img/cover/solo-leveling.jpg" /></div>
        <div class="series-title"><h1><span>Solo Leveling</span></h1></div>
        <p class="seri_des" data-full-description="Jinwoo Sung wird zum stärksten Hunter."></p>
        <div class="genres"><ul><li><a href="/genre/action">Action</a></li></ul></div>
        <div id="stream">
            <ul>
                <li><a class="active" href="/anime/stream/solo-leveling/staffel-1">1</a></li>
                <li><a href="/anime/stream/solo-leveling/staffel-2">2</a></li>
            </ul>
        </div>
        <table class="seasonEpisodesList">
            <tbody>
                <tr data-episode-season-id="1">
                    <td class="seasonEpisodeTitle">
                        <a href="/anime/stream/solo-leveling/staffel-1/episode-1">
                            <strong>Der schwächste Jäger</strong>
                            <span>The Weakest Hunter</span>
                        </a>
                    </td>
                    <td class="editFunctions">
                        <img src="/public/img/german.svg" />
                        <img src="/public/img/japanese-german.svg" />
                    </td>
                </tr>
            </tbody>
        </table>
        </html>
        """
        let detail = AniWorldScraper.parseAnimeDetail(
            slug: "solo-leveling",
            currentPath: "/anime/stream/solo-leveling/staffel-1",
            html: sampleHtml
        )
        XCTAssertEqual(detail.title, "Solo Leveling")
        XCTAssertEqual(detail.coverUrl, "https://aniworld.to/public/img/cover/solo-leveling.jpg")
        XCTAssertEqual(detail.description, "Jinwoo Sung wird zum stärksten Hunter.")
        XCTAssertEqual(detail.genres, ["Action"])
        XCTAssertEqual(detail.seasons.count, 2)
        XCTAssertEqual(detail.episodes.count, 1)
        XCTAssertEqual(detail.episodes.first?.titleDe, "Der schwächste Jäger")
        XCTAssertEqual(detail.episodes.first?.languages, [.gerDub, .gerSub])

        let episodeHtml = """
        <li class="col-md-3" data-lang-key="1" data-link-target="/redirect/998877">
            <h4>VOE</h4>
        </li>
        <li class="col-md-3" data-lang-key="3" data-link-target="/redirect/112233">
            <h4>Vidmoly</h4>
        </li>
        """
        let links = AniWorldScraper.parseEpisodeHosters(episodeHtml)
        XCTAssertEqual(links.count, 2)
        XCTAssertEqual(links[0].hosterName, "VOE")
        XCTAssertEqual(links[0].language, .gerDub)
        XCTAssertEqual(links[0].redirectPath, "/redirect/998877")
        XCTAssertEqual(links[1].language, .gerSub)
    }

    // MARK: - Filmpalast Parsing

    func testFilmpalastParseArticlesAndStreamLinks() {
        let html = """
        <article class="liste">
            <a href="https://filmpalast.to/stream/gladiator-ii" title="Gladiator II">
                <img src="/files/movies/gladiator2.jpg" />
            </a>
        </article>
        """
        let articles = FilmpalastScraper.parseArticles(html)
        XCTAssertEqual(articles.count, 1)
        XCTAssertEqual(articles.first?.slug, "filmpalast:/stream/gladiator-ii")

        let detailLinksHtml = """
        <ul class="currentStreamLinks">
            <li>
                <p class="hostName">VOE HD</p>
                <a class="iconPlay" data-player-url="https://voe.sx/e/abc123xyz">Stream</a>
            </li>
        </ul>
        """
        let hosters = FilmpalastScraper.parseStreamLinks(detailLinksHtml)
        XCTAssertEqual(hosters.count, 1)
        XCTAssertEqual(hosters.first?.hosterName, "VOE")
        XCTAssertEqual(hosters.first?.redirectPath, "https://voe.sx/e/abc123xyz")
    }

    // MARK: - Live TV M3U Parser

    func testLiveTvParseM3u() {
        let m3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="ARD.de" tvg-logo="https://ard.de/logo.png",Das Erste HD
        #EXTVLCOPT:http-user-agent=CustomAgent/1.0
        #EXTVLCOPT:http-referrer=https://ard.de/
        https://mcdn.daserste.de/daserste/de/master.m3u8
        """
        let channels = LiveTvRepository.parseM3u(m3u)
        XCTAssertEqual(channels.count, 1)
        XCTAssertEqual(channels.first?.name, "Das Erste HD")
        XCTAssertEqual(channels.first?.headers["User-Agent"], "CustomAgent/1.0")
        XCTAssertEqual(channels.first?.headers["Referer"], "https://ard.de/")
    }

    // MARK: - StreamResolver Decoders

    func testStreamResolverVoeDecodeAndJsUnpack() {
        // Build a synthetic VOE JSON array payload:
        // Decode order in StreamResolver.decodeVoePayload:
        // JSON ["<encoded>"] -> rot13 -> strip junk -> base64Decode -> shift -3 -> reverse -> base64Decode -> {"source": "..."}
        let rawJson = #"{"source":"https://cdn.voe.sx/hls/master.m3u8"}"#
        let step1Base64 = Data(rawJson.utf8).base64EncodedString()
        let step2Reversed = String(step1Base64.reversed())
        let step3Shifted = String(step2Reversed.unicodeScalars.map { Character(UnicodeScalar($0.value + 3)!) })
        let step4Base64 = Data(step3Shifted.utf8).base64EncodedString()
        let step5Rot13 = StreamResolver.rot13(step4Base64)
        let jsonArrayStr = "[\"\(step5Rot13)\"]"

        let decoded = StreamResolver.decodeVoePayload(jsonArrayStr)
        XCTAssertEqual(decoded, "https://cdn.voe.sx/hls/master.m3u8")

        // JS unpacker test
        let unpacked = StreamResolver.unpackJs(
            p: #"file:"https://0.1.2/3.m3u8""#,
            radix: 10,
            count: 4,
            keywords: ["cdn", "vidmoly", "to", "master"]
        )
        XCTAssertTrue(unpacked.contains("https://cdn.vidmoly.to/master.m3u8"))
    }

    func testStreamResolverDeadLinkDetection() {
        XCTAssertTrue(StreamResolver.isDeadLinkPage("<html><title>404 - File was deleted</title></html>"))
        XCTAssertFalse(StreamResolver.isDeadLinkPage("<html><title>VOE Player</title><video src='https://cdn.site/video.mp4'></video></html>"))
    }

    // MARK: - UpdateUtils Version & Changelog

    func testUpdateUtilsVersionComparisonAndChangelog() {
        XCTAssertTrue(UpdateUtils.isNewerVersion(remote: "1.22.0", current: "1.21.9"))
        XCTAssertFalse(UpdateUtils.isNewerVersion(remote: "1.22.0", current: "1.22.0"))
        XCTAssertFalse(UpdateUtils.isNewerVersion(remote: "1.20.5", current: "1.22.0"))

        let rawNotes = """
        ## What's Changed
        * Fix VOE stream extractor by @abi in https://github.com/abi/pull/12
        * Add iOS support

        **Full Changelog**: https://github.com/abi/compare/v1.21.0...v1.22.0
        """
        let cleaned = UpdateUtils.cleanChangelog(rawNotes)
        XCTAssertTrue(cleaned.contains("• Fix VOE stream extractor"))
        XCTAssertTrue(cleaned.contains("• Add iOS support"))
        XCTAssertFalse(cleaned.contains("Full Changelog"))
    }

    // MARK: - LocalStorage Watchlist & Progress

    func testLocalStorageWatchlistAndProgress() {
        let storage = LocalStorage(store: InMemoryKeyValueStore())

        let card = AnimeCard(slug: "one-piece", title: "One Piece", coverUrl: "https://aniworld.to/op.jpg")
        XCTAssertFalse(storage.isInWatchlist("one-piece"))

        let added = storage.toggleWatchlist(card)
        XCTAssertTrue(added)
        XCTAssertTrue(storage.isInWatchlist("one-piece"))
        XCTAssertEqual(storage.watchlist.count, 1)

        let epPath = "/anime/stream/one-piece/staffel-1/episode-5"
        let entryHalf = WatchProgressEntry(
            animeSlug: "one-piece",
            animeTitle: "One Piece",
            coverUrl: "https://aniworld.to/op.jpg",
            seasonLabel: "1",
            episodeNumber: 5,
            episodeTitle: "König der Piraten",
            episodePath: epPath,
            preferredLangKey: 1,
            positionMs: 600_000,
            durationMs: 1_200_000,
            updatedAt: 1_000
        )
        storage.saveWatchProgress(entryHalf)
        XCTAssertEqual(storage.progressList.count, 1)
        XCTAssertEqual(storage.getSavedPositionForEpisode(epPath), 600_000)
        XCTAssertFalse(storage.isEpisodeWatched(epPath))

        let entryDone = WatchProgressEntry(
            animeSlug: "one-piece",
            animeTitle: "One Piece",
            coverUrl: "https://aniworld.to/op.jpg",
            seasonLabel: "1",
            episodeNumber: 5,
            episodeTitle: "König der Piraten",
            episodePath: epPath,
            preferredLangKey: 1,
            positionMs: 1_150_000,
            durationMs: 1_200_000,
            updatedAt: 2_000
        )
        storage.saveWatchProgress(entryDone)
        XCTAssertTrue(storage.isEpisodeWatched(epPath))
        // >93% progress resets saved resume position to 0 so re-watching starts from the beginning
        XCTAssertEqual(storage.getSavedPositionForEpisode(epPath), 0)
    }

    // MARK: - HttpClient URL Rewriting

    func testHttpClientDroploadUrlRewrite() {
        let rewritten = HttpClient.rewriteUrl("https://dropload.io/e/abc123xyz")
        XCTAssertEqual(rewritten?.absoluteString, "https://dr0pstream.com/e/abc123xyz")

        let rewrittenWww = HttpClient.rewriteUrl("https://www.dropload.io/embed-9876543210.html")
        XCTAssertEqual(rewrittenWww?.absoluteString, "https://dr0pstream.com/embed-9876543210.html")
    }

    // MARK: - SerienStream (S.to) Detail & Hoster Parsing

    func testSerienStreamParseDetailAndEpisodeHosters() {
        let html = """
        <html>
        <h1>The Rookie</h1>
        <p class="series-description">John Nolan fängt als ältester Rookie beim LAPD an.</p>
        <img data-src="/media/images/channel/the-rookie.jpg" />
        <a href="/serie/the-rookie/staffel-1">1</a>
        <a href="/serie/the-rookie/staffel-2">2</a>
        <tr class="episode-row" onclick="window.location='/serie/the-rookie/staffel-1/episode-1'">
            <td class="episode-number-cell">1</td>
            <td class="episode-title-ger">Pilot</td>
            <td class="episode-title-eng">Pilot Episode</td>
        </tr>
        </html>
        """
        let detail = SerienStreamScraper.parseDetail(
            cleanSlug: "the-rookie",
            currentUrl: "https://s.to/serie/the-rookie/staffel-1",
            html: html
        )
        XCTAssertEqual(detail.slug, "sto:the-rookie")
        XCTAssertEqual(detail.title, "The Rookie")
        XCTAssertEqual(detail.seasons.count, 2)
        XCTAssertEqual(detail.episodes.count, 1)
        XCTAssertEqual(detail.episodes.first?.titleDe, "Pilot")
        XCTAssertEqual(detail.episodes.first?.path, "sto_the-rookie_s1_e1_/serie/the-rookie/staffel-1/episode-1")

        let hosterHtml = """
        <button class="link-box" data-play-url="/redirect/554433" data-provider-name="VOE" data-language-id="1"></button>
        <button class="link-box" data-play-url="/redirect/667788" data-provider-name="Vidmoly" data-language-id="2"></button>
        """
        let hosters = SerienStreamScraper.parseEpisodeHosters(hosterHtml)
        XCTAssertEqual(hosters.count, 2)
        XCTAssertEqual(hosters[0].hosterName, "VOE")
        XCTAssertEqual(hosters[0].language, .gerDub)
        XCTAssertEqual(hosters[1].language, .engSub)
    }

    // MARK: - HdFilme DeVideoSRC Multi-Season Series Parsing

    func testHdFilmeDeVideoSrcMultiSeasonIsolation() {
        let serialHtml = """
        <div class="_stab" data-season="1">Staffel 1</div>
        <div class="_stab" data-season="2">Staffel 2</div>
        <div class="_grp" data-season="1">
            <div class="_ep" data-episode="1">
                <div class="_ept">Folge Eins</div>
                <div class="_epd">Beschreibung S1E1</div>
            </div>
            <div class="_ep" data-episode="2">
                <div class="_ept">Folge Zwei</div>
                <div class="_epd">Beschreibung S1E2</div>
            </div>
        </div>
        <div class="_grp" data-season="2">
            <div class="_ep" data-episode="1">
                <div class="_ept">Staffel 2 Start</div>
                <div class="_epd">Beschreibung S2E1</div>
            </div>
        </div>
        """
        let season1 = HdFilmeScraper.parseDeVideoSrcSeriesHtml(
            slug: "hdfilme:/serien/test-serie.html",
            title: "Test Serie",
            desc: "Test",
            coverUrl: "",
            backdropUrl: "",
            imdbId: "tt1234567",
            fallbackEmbed: nil,
            selectedSeasonStr: "1",
            serialHtml: serialHtml
        )
        XCTAssertEqual(season1.seasons.count, 2)
        XCTAssertEqual(season1.episodes.count, 2)
        XCTAssertEqual(season1.episodes[0].titleDe, "Folge Eins")
        XCTAssertEqual(season1.episodes[0].titleEn, "Beschreibung S1E1")
        XCTAssertEqual(season1.episodes[1].titleDe, "Folge Zwei")

        let season2 = HdFilmeScraper.parseDeVideoSrcSeriesHtml(
            slug: "hdfilme:/serien/test-serie.html",
            title: "Test Serie",
            desc: "Test",
            coverUrl: "",
            backdropUrl: "",
            imdbId: "tt1234567",
            fallbackEmbed: nil,
            selectedSeasonStr: "2",
            serialHtml: serialHtml
        )
        XCTAssertEqual(season2.episodes.count, 1)
        XCTAssertEqual(season2.episodes[0].titleDe, "Staffel 2 Start")
        XCTAssertEqual(season2.episodes[0].titleEn, "Beschreibung S2E1")
        XCTAssertEqual(season2.episodes[0].path, "hdfilme_tv:tt1234567:2:1")
    }

    func testHdFilmeEpisodeWithExtraClassesAndNestedMarkup() {
        let serialHtml = """
        <style>._grp{display:none} ._ep{color:red} ._ept{font-weight:bold}</style>
        <div class="_stab active" data-season="1">Staffel 1</div>
        <div class="box _grp" data-season="1">
            <div class="_ep is-new" data-episode="7">
                <div><div class="_ept"><span>Mit</span> Span</div></div>
                <div class="_epd">Text</div>
            </div>
        </div>
        """
        let detail = HdFilmeScraper.parseDeVideoSrcSeriesHtml(
            slug: "hdfilme:/serien/x.html",
            title: "X",
            desc: "",
            coverUrl: "",
            backdropUrl: "",
            imdbId: "tt1",
            fallbackEmbed: nil,
            selectedSeasonStr: nil,
            serialHtml: serialHtml,
            genres: ["Drama"]
        )
        XCTAssertEqual(detail.genres, ["Drama"])
        XCTAssertEqual(detail.currentSeasonPath, "1")
        XCTAssertEqual(detail.episodes.count, 1)
        XCTAssertEqual(detail.episodes.first?.number, 7)
        XCTAssertEqual(detail.episodes.first?.titleDe, "Mit Span")
        XCTAssertEqual(detail.episodes.first?.titleEn, "Text")
    }

    func testHdFilmeCategoryUrlKeepsLeadingSlashOnLaterPages() {
        XCTAssertEqual(HdFilmeScraper.categoryUrl("/action", page: 1), "https://hd-filme.one/action/")
        XCTAssertEqual(HdFilmeScraper.categoryUrl("/action", page: 2), "https://hd-filme.one/action/page/2/")
        XCTAssertEqual(HdFilmeScraper.categoryUrl("action/", page: 3), "https://hd-filme.one/action/page/3/")
    }

    func testHdFilmeParseMovieCardsFindsLinkInsideAndBesidePoster() {
        let html = """
        <div class="card"><a href="https://hd-filme.one/filme/interstellar-1.html" title="Interstellar"><img data-src="/uploads/thumb/300x450/a.jpg"></a></div>
        <div class="card"><div class="poster"><img data-src="/uploads/thumb/300x450/b.jpg" alt="Dune hdfilme stream online poster"></div><a href="/serien/dune-2.html">Dune</a></div>
        <a href="/filme/platzhalter.html"><img src="/templates/default-poster.svg" class="default-poster"></a>
        <a href="/filme/logo.html"><img src="/templates/logo.png"></a>
        <img data-src="/uploads/thumb/300x450/ohne-link.jpg">
        """
        let cards = HdFilmeScraper.parseMovieCards(html)
        XCTAssertEqual(cards.map(\.slug), ["hdfilme:/filme/interstellar-1.html", "hdfilme:/serien/dune-2.html"])
        XCTAssertEqual(cards.map(\.title), ["Interstellar", "Dune"])
        XCTAssertEqual(cards.first?.coverUrl, "https://hd-filme.one/uploads/thumb/300x450/a.jpg")
    }

    // MARK: - Regressionen HTML-Parser

    func testHtmlParserAttrDoesNotMatchPrefixedAttributes() {
        let tag = #"<img data-src="/lazy.jpg" src="/real.jpg" data-title="falsch" title="richtig">"#
        XCTAssertEqual(HtmlParser.attr(tag, "src"), "/real.jpg")
        XCTAssertEqual(HtmlParser.attr(tag, "title"), "richtig")
        XCTAssertEqual(HtmlParser.attr(#"<img data-src="/lazy.jpg">"#, "src"), "")
        XCTAssertEqual(HtmlParser.attr("<div data-season=3 class=x>", "data-season"), "3")
    }

    func testHtmlParserDecodesAmpersandOnlyOnce() {
        XCTAssertEqual(HtmlParser.decodeEntities("&amp;lt;b&amp;gt;"), "&lt;b&gt;")
        XCTAssertEqual(HtmlParser.decodeEntities("Tom &amp; Jerry &#39;s"), "Tom & Jerry 's")
    }

    func testHtmlParserElementsMatchExactClassWithNestedTags() {
        let html = """
        <div class="row"><div class="_ep"><div><div class="_ept">Titel</div></div></div><div class='a _ep b'>Zwei</div></div>
        <img class="_ep" src="/x.png"><p class="_epd">Nein</p>
        """
        let found = HtmlParser.elements(in: html, withClass: "_ep")
        XCTAssertEqual(found.count, 3)
        XCTAssertEqual(found[0].text, "Titel")
        XCTAssertEqual(found[1].text, "Zwei")
        XCTAssertEqual(found[2].html, #"<img class="_ep" src="/x.png">"#)
    }

    // MARK: - Regressionen Scraper

    func testAniWorldHomeSectionsStayInsideTheirElements() {
        let html = """
        <div class="carousel animeCarousel"><h2>Neu</h2><div><a href="/anime/stream/a"><img data-src="/a.jpg"><h3>A</h3></a></div></div>
        <div class="carousel"><h2>Beliebt</h2><a href="/anime/stream/b"><img data-src="/b.jpg"><h3>B</h3></a></div>
        <div class="seriesListContainer"><a href="/anime/stream/c"><img data-src="/c.jpg"><h3>C</h3></a></div>
        <footer><a href="/anime/stream/d"><img data-src="/d.jpg"><h3>D</h3></a></footer>
        """
        let sections = AniWorldScraper.parseHomeSections(html)
        XCTAssertEqual(sections.map(\.title), ["Neu", "Beliebt", "👀 Das sehen andere Nutzer gerade"])
        XCTAssertEqual(sections.map { $0.items.map(\.slug) }, [["a"], ["b"], ["c"]])
    }

    func testSerienStreamRowsKeepTheirOwnPathWhenOnclickPrecedesClass() {
        let html = """
        <h1>X</h1>
        <table>
        <tr onclick="window.location='/serie/x/staffel-1/episode-1'" class="episode-row"><td class="episode-number-cell">1</td></tr>
        <tr onclick="window.location='/serie/x/staffel-1/episode-2'" class="episode-row aktiv"><td class="episode-number-cell">2</td></tr>
        </table>
        """
        let detail = SerienStreamScraper.parseDetail(
            cleanSlug: "x",
            currentUrl: "https://serienstream.to/serie/x/staffel-1",
            html: html
        )
        XCTAssertEqual(detail.episodes.map(\.path), [
            "sto_x_s1_e1_/serie/x/staffel-1/episode-1",
            "sto_x_s1_e2_/serie/x/staffel-1/episode-2"
        ])
    }

    func testFilmpalastDetailReadsOnlyTheMoviesGenresAndYear() {
        let html = """
        <ul class="menu"><li><a href="/search/genre/Horror">Horror</a></li></ul>
        <em itemprop="name">Dune</em>
        <ul>
            <li><p>Kategorien, Genre</p><a href="/search/genre/Action">Action</a><a href="/search/genre/Englisch">Englisch</a><a href="/search/genre/Sci-Fi">Sci-Fi</a></li>
            <li><p>Ver&ouml;ffentlicht: <b>2021</b></p></li>
        </ul>
        """
        let detail = FilmpalastScraper.parseDetail(path: "/stream/dune", html: html)
        XCTAssertEqual(detail.title, "Dune")
        XCTAssertEqual(detail.genres, ["Action", "Sci-Fi"])
        XCTAssertEqual(detail.years, "2021")
    }

    func testFilmpalastArticlesAreDeduplicated() {
        let article = #"<article><a href="/stream/x" title="X"><img src="/files/movies/x.jpg"></a></article>"#
        XCTAssertEqual(FilmpalastScraper.parseArticles(article + article).count, 1)
    }

    // MARK: - Regressionen Hilfsfunktionen

    func testEpisodeShortLabelHasNoSeasonForMovies() {
        XCTAssertEqual(EpisodeItem.shortLabel(season: "2", number: 5), "S2 E5")
        XCTAssertEqual(EpisodeItem.shortLabel(season: "Film", number: 1), "Film")
        XCTAssertEqual(EpisodeItem.shortLabel(season: "Filme", number: 3), "Film")
    }

    func testPercentEncodeStrictEncodesUmlautsAndPathSeparators() {
        XCTAssertEqual(HttpClient.percentEncodeStrict("AC/DC & Köln"), "AC%2FDC%20%26%20K%C3%B6ln")
    }

    func testHttpClientRewriteUrlAcceptsUnencodedAddresses() {
        XCTAssertNotNil(HttpClient.rewriteUrl("https://example.com/Köln Film.html"))
    }
}
