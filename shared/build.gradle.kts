import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Reiner JVM-Code (Modelle, Scraper, Stream-Auflösung, Speicher, Kopplung), den
// TV- und Handy-App über :core sowie die PC-App direkt nutzen
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
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jsoup:jsoup:1.16.1")
    api("com.google.code.gson:gson:2.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
