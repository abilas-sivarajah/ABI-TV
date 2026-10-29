package de.aniworld.pc

import de.aniworld.pc.platform.AppDirs
import de.aniworld.pc.platform.ChromiumStreamExtractor
import de.aniworld.pc.platform.JsonFileStore
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.ResolvedStream
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.data.remote.StreamResolver
import kotlinx.coroutines.runBlocking
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

/**
 * Prüflauf ohne Oberfläche (./gradlew :desktop:playbackCheck):
 * 1. Speicher: Merkliste & Verlauf für Filmpalast-Slugs
 * 2. Hoster auflösen – Filmpalast (Interstellar, inkl. Chromium) und AniWorld
 * 3. Den ersten aufgelösten Stream mit dem mitgelieferten VLC stumm und ohne Fenster anspielen
 */
fun main() {
    var failures = 0
    fun check(ok: Boolean, label: String) {
        println((if (ok) "  OK    " else "  FEHLER ") + label)
        if (!ok) failures++
    }

    println("== Speicher")
    val tmp = Files.createTempDirectory("abi-check").toFile()
    LocalStorage.init(JsonFileStore(File(tmp, "prefs.json")))
    val film = AnimeCard(slug = "filmpalast:/stream/interstellar", title = "Interstellar", coverUrl = "")
    LocalStorage.toggleWatchlist(film)
    check(LocalStorage.isInWatchlist("filmpalast:/stream/interstellar"), "Filmpalast-Film wird als gemerkt erkannt")
    check(!LocalStorage.isInWatchlist("filmpalast:/stream/john-wick"), "anderer Filmpalast-Film nicht")

    val chromium = ChromiumStreamExtractor(File(AppDirs.cacheDir, "jcef"))
    StreamResolver.browserExtractor = chromium

    runBlocking {
        println("== Filmpalast: Interstellar")
        val detail = FilmpalastScraper.fetchDetail("filmpalast:/stream/interstellar")
        val filmHosters = FilmpalastScraper.fetchHostersForFilm(detail.episodes.first().path)
        check(filmHosters.isNotEmpty(), "Hoster gefunden: ${filmHosters.joinToString { it.hosterName }}")
        // Wie der Player: Hoster der Reihe nach, bis VLC wirklich abspielt
        val played = playFirstWorking(filmHosters)
        check(played != null, "VLC spielt Interstellar über ${played ?: "keinen Hoster"}")

        println("== AniWorld: erste Folge aus dem Katalog")
        val slug = AniWorldScraper.fetchHomeCatalog().first().items.first().cleanSlug
        val animeDetail = AniWorldScraper.fetchAnimeDetail(slug)
        val episode = animeDetail.episodes.first()
        val animeHosters = AniWorldScraper.fetchEpisodeHosters(episode.path)
        check(animeHosters.isNotEmpty(), "${animeDetail.title} F${episode.number}: ${animeHosters.size} Hoster")
        val animeStream = resolveFirst(animeHosters)
        check(animeStream != null, "Folge aufgelöst über ${animeStream?.hosterName}")
        animeStream?.let { check(vlcPlays(it), "VLC spielt die Folge (${it.hosterName})") }
    }

    chromium.dispose()
    println(if (failures == 0) "== ALLES OK" else "== $failures FEHLER")
    exitProcess(if (failures == 0) 0 else 1)
}

private suspend fun playFirstWorking(hosters: List<HosterLink>): String? {
    for (hoster in hosters) {
        val stream = runCatching { StreamResolver.resolveHoster(hoster) }.getOrElse {
            println("    ${hoster.hosterName}: ${it.message}")
            null
        } ?: continue
        if (vlcPlays(stream)) return hoster.hosterName
        println("    ${hoster.hosterName}: aufgelöst, aber VLC spielt nicht")
    }
    return null
}

private suspend fun resolveFirst(hosters: List<HosterLink>): ResolvedStream? {
    for (hoster in hosters) {
        val start = System.currentTimeMillis()
        val result = runCatching { StreamResolver.resolveHoster(hoster) }
        val ms = System.currentTimeMillis() - start
        result.onSuccess {
            println("    ${hoster.hosterName}: aufgelöst in $ms ms")
            return it
        }.onFailure {
            println("    ${hoster.hosterName}: ${it.message} ($ms ms)")
        }
    }
    return null
}

private val vlcFactory: MediaPlayerFactory by lazy {
    // Gleiche Suche wie VlcSupport: zuerst der mitgelieferte VLC
    System.getProperty("compose.application.resources.dir")?.let { dir ->
        System.setProperty("jna.library.path", File(dir, "vlc").absolutePath)
    }
    val discovery = NativeDiscovery()
    check(discovery.discover()) { "libvlc nicht gefunden" }
    // Kein Fenster, kein Ton
    MediaPlayerFactory(discovery, "--vout=dummy", "--aout=dummy", "--quiet", "--network-caching=1500")
}

/** Spielt den Stream an und prüft, ob die Zeit innerhalb von 25 s über 3 Sekunden hinaus läuft. */
private fun vlcPlays(stream: ResolvedStream): Boolean {
    val player = vlcFactory.mediaPlayers().newMediaPlayer()
    val time = AtomicLong(0)
    val done = CountDownLatch(1)
    player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            time.set(newTime)
            if (newTime > 3_000) done.countDown()
        }

        override fun error(mediaPlayer: MediaPlayer) = done.countDown()
        override fun finished(mediaPlayer: MediaPlayer) = done.countDown()
    })
    val options = buildList {
        add(":http-user-agent=${stream.headers["User-Agent"] ?: HttpClient.USER_AGENT}")
        stream.headers["Referer"]?.let { add(":http-referrer=$it") }
    }
    player.media().play(stream.streamUrl, *options.toTypedArray())
    done.await(25, TimeUnit.SECONDS)
    val reached = time.get()
    player.controls().stop()
    player.release()
    println("    VLC-Zeit erreicht: ${reached / 1000} s")
    return reached > 3_000
}
