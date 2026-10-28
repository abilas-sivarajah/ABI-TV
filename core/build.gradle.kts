plugins {
    id("com.android.library")
}

// Gemeinsame Datenschicht (Scraper, Speicher, Modelle, Updater) für TV- und Mobile-App
android {
    namespace = "de.aniworld.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
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
    // Typen dieser Bibliotheken tauchen in der öffentlichen API der Datenschicht auf
    api("androidx.core:core-ktx:1.15.0")
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jsoup:jsoup:1.16.1")
    api("com.google.code.gson:gson:2.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
