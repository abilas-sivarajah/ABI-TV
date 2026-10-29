import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Gleiche Version wie TV und Handy; der Release-Workflow setzt sie aus dem Git-Tag
val appVersionName = providers.gradleProperty("appVersionName").get()

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Video: VLC (libvlc wird mitgeliefert, siehe prepareVlc)
    implementation("uk.co.caprica:vlcj:4.12.1")
    // Unsichtbarer Chromium für Hoster, die nur im Browser ihren Stream verraten (lädt beim ersten Start nach)
    implementation("me.friwi:jcefmaven:152.0.6")
    // Damit das Handy den PC im WLAN findet (wie NSD auf dem Fernseher)
    implementation("org.jmdns:jmdns:3.6.4")
}

// Version als Ressource, damit der Updater weiß, was installiert ist
val generateVersionResource by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/version")
    val version = appVersionName
    inputs.property("version", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("abi-version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version" + System.lineSeparator())
    }
}
sourceSets["main"].resources.srcDir(generateVersionResource)

// ------------------- VLC für den Installer -------------------

val vlcVersion = "3.0.24"
val vlcSha256 = "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323"
val vlcResourcesDir = layout.buildDirectory.dir("vlc-resources")

/**
 * Lädt VLC (nur libvlc + Plugins) in build/vlc-resources/windows/vlc, das als App-Ressource
 * im Installer landet. Ohne Qt-Oberfläche und Visualisierungen, die vlcj nicht braucht.
 */
val prepareVlc by tasks.registering {
    val outDir = vlcResourcesDir.map { it.dir("windows/vlc") }
    outputs.dir(outDir)
    inputs.property("vlcVersion", vlcVersion)
    doLast {
        val target = outDir.get().asFile
        val zip = layout.buildDirectory.file("vlc-download/vlc-$vlcVersion-win64.zip").get().asFile
        if (!zip.exists() || sha256(zip) != vlcSha256) {
            zip.parentFile.mkdirs()
            val url = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
            logger.lifecycle("Lade VLC $vlcVersion ...")
            URI(url).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
            check(sha256(zip) == vlcSha256) { "Prüfsumme des VLC-Downloads stimmt nicht." }
        }
        target.deleteRecursively()
        copy {
            from(zipTree(zip))
            include("vlc-$vlcVersion/libvlc.dll", "vlc-$vlcVersion/libvlccore.dll", "vlc-$vlcVersion/plugins/**")
            exclude("**/plugins/gui/**", "**/plugins/visualization/**", "**/plugins/plugins.dat")
            eachFile { path = path.removePrefix("vlc-$vlcVersion/") }
            includeEmptyDirs = false
            into(target)
        }
    }
}

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { "%02x".format(it) }

tasks.matching { it.name.startsWith("prepareAppResources") }.configureEach { dependsOn(prepareVlc) }

// ------------------- Anwendung & Installer -------------------

compose.desktop {
    application {
        mainClass = "de.aniworld.pc.MainKt"

        // Von JCEF (Chromium) benötigt
        jvmArgs += listOf(
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
            "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
            "-Dfile.encoding=UTF-8"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ABI TV"
            packageVersion = appVersionName
            description = "ABI TV für den PC"
            vendor = "ABI TV"
            appResourcesRootDir.set(vlcResourcesDir)
            // Lieber die ganze Java-Laufzeit mitgeben, als dass JCEF, JNA oder TLS zur Laufzeit ein Modul fehlt
            includeAllModules = true

            windows {
                // Fester Wert, damit jede neue Version die alte ersetzt statt daneben zu landen
                upgradeUuid = "6b3f3d0e-2f4c-4f7a-9a0e-7d8f2b1c5a91"
                perUserInstall = true
                menu = true
                menuGroup = "ABI TV"
                shortcut = true
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}

// ------------------- Prüflauf ohne Oberfläche -------------------

/** ./gradlew :desktop:playbackCheck – Hoster auflösen (inkl. Chromium) und mit dem mitgelieferten VLC anspielen. */
val playbackCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Löst echte Hoster auf und spielt sie stumm mit dem mitgelieferten VLC an."
    dependsOn(prepareVlc)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("de.aniworld.pc.PlaybackCheckKt")
    jvmArgs(
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8"
    )
    systemProperty("compose.application.resources.dir", vlcResourcesDir.get().dir("windows").asFile.absolutePath)
}

val browserCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Lädt Hoster-Seiten im unsichtbaren Chromium und loggt jede Anfrage."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("de.aniworld.pc.BrowserCheckKt")
    jvmArgs(playbackCheck.get().jvmArgs)
    systemProperty("abi.debugBrowser", "true")
    providers.gradleProperty("referer").orNull?.let { systemProperty("abi.referer", it) }
}
