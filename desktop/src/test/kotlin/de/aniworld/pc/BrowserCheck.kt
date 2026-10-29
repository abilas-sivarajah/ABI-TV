package de.aniworld.pc

import de.aniworld.pc.platform.AppDirs
import de.aniworld.pc.platform.ChromiumStreamExtractor
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

/** ./gradlew :desktop:browserCheck --args="<Hoster-URL>" – zeigt, was der unsichtbare Chromium lädt. */
fun main(args: Array<String>) {
    val chromium = ChromiumStreamExtractor(File(AppDirs.cacheDir, "jcef"))
    for (url in args) {
        val start = System.currentTimeMillis()
        val referer = System.getProperty("abi.referer") ?: "https://aniworld.to/"
        val result = runBlocking { chromium.extract(url, referer) }
        println("== $url -> ${result?.first ?: "NICHTS"} (${System.currentTimeMillis() - start} ms)")
    }
    chromium.dispose()
    exitProcess(0)
}
