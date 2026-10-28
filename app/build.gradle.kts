import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version kommt aus gradle.properties; der Release-Workflow überschreibt sie mit dem Git-Tag
val appVersionName = providers.gradleProperty("appVersionName").get()
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()

// Fester Release-Schlüssel: in CI über Umgebungsvariablen, lokal optional über keystore.properties
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(env: String, prop: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
val releaseKeystorePath = signingValue("ABI_KEYSTORE_PATH", "storeFile")

android {
    namespace = "de.aniworld.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.aniworld.tv"
        minSdk = 21
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath)
                storePassword = signingValue("ABI_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ABI_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ABI_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ohne Release-Schlüssel (lokale Test-Builds) wird mit dem Debug-Key signiert.
            // Achtung: Updates funktionieren nur, wenn alle Versionen mit demselben Schlüssel signiert sind.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)

    // Compose Core & Material3
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Jetpack Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha12")
    implementation("androidx.tv:tv-material:1.0.1")

    // AndroidX Core & Lifecycle
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Media3 / ExoPlayer
    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    // Networking & Parsing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.google.code.gson:gson:2.11.0")

    // Image Loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
