import java.util.Properties

plugins {
    id("com.android.library")
}

// TMDB-Schlüssel (Bilder fürs Titelbild) – nie im Quellcode: lokal aus local.properties ("tmdb.apiKey=…"),
// im Release-Workflow aus dem GitHub-Secret TMDB_API_KEY. Ohne Schlüssel laufen die Apps ohne TMDB-Bilder.
val tmdbApiKey: String = providers.environmentVariable("TMDB_API_KEY").orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: providers.gradleProperty("tmdbApiKey").orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: rootProject.file("local.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("tmdb.apiKey")?.trim()
    }
    ?: ""

// Android-Teil der Datenschicht (WebView, SharedPreferences, APK-Updater) für TV- und Mobile-App;
// der plattformunabhängige Teil liegt in :shared
android {
    namespace = "de.aniworld.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    // Modelle, Scraper, Stream-Auflösung & Speicher (reiner JVM-Code, auch für die PC-App)
    api(project(":shared"))

    // Typen dieser Bibliotheken tauchen in der öffentlichen API der Datenschicht auf
    api("androidx.core:core-ktx:1.15.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Coil-Standardloader für TV- und Handy-App (Cover über denselben HTTP-Client wie die Scraper)
    implementation("io.coil-kt:coil:2.7.0")
}
