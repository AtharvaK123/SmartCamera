import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// All keys live in server/.env — the one file shared by the server, the website and this app.
// They are compiled into the APK (BuildConfig), so don't publish the APK publicly.
val env = Properties().apply {
    val f = rootProject.file("../server/.env")
    if (f.exists()) f.readLines().forEach { raw ->
        val line = raw.trim()
        if (line.isNotEmpty() && !line.startsWith("#") && "=" in line) {
            val i = line.indexOf('=')
            setProperty(line.substring(0, i).trim(), line.substring(i + 1).trim())
        }
    }
}
fun envField(key: String, default: String = ""): String =
    "\"" + (env.getProperty(key) ?: default).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Mood music: everything in <project>/music/<level>-stress is packed into the app as assets/music/…
val syncMusic = tasks.register<Sync>("syncMusic") {
    from(rootProject.file("../music")) {
        include("*/*.mp3", "*/*.m4a", "*/*.aac", "*/*.ogg", "*/*.wav", "*/*.flac", "*/*.opus")
        into("music")
    }
    into(layout.buildDirectory.dir("generated/musicAssets"))
}

android {
    namespace = "com.hack.smartcam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hack.smartcam"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Modern phones (arm64) + the Android emulator (x86_64). Keeps Presage's native code from bloating the APK.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }

        buildConfigField("String", "GEMINI_API_KEY", envField("GEMINI_API_KEY"))
        buildConfigField("String", "GEMINI_MODEL", envField("GEMINI_MODEL", "gemini-3.8-flash"))
        buildConfigField("String", "ELEVENLABS_API_KEY", envField("ELEVENLABS_API_KEY"))
        buildConfigField("String", "ELEVENLABS_VOICE_ID", envField("ELEVENLABS_VOICE_ID", "21m00Tcm4TlvDq8ikWAM"))
        buildConfigField("String", "PRESAGE_API_KEY", envField("PRESAGE_API_KEY"))
        buildConfigField("String", "DATABASE_URL", envField("DATABASE_URL"))
        buildConfigField("String", "SOLANA_MERCHANT", envField("SOLANA_MERCHANT"))
        buildConfigField("String", "SOLANA_NETWORK", envField("SOLANA_NETWORK", "devnet"))
        buildConfigField("String", "ADMIN_USERNAME", envField("ADMIN_USERNAME", "admin"))
        buildConfigField("String", "ADMIN_PASSWORD", envField("ADMIN_PASSWORD"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/musicAssets"))
    androidResources { noCompress += listOf("flac", "opus") }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/versions/**",
                "META-INF/LICENSE*",
                "META-INF/DEPENDENCIES",
            )
        }
    }
}

// Copy the music before anything reads the assets folder.
tasks.named("preBuild") { dependsOn(syncMusic) }
tasks.matching { it.name.endsWith("Assets") && it.name != "syncMusic" }.configureEach { dependsOn(syncMusic) }

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    // Camera: barcode scanning (CameraX + ML Kit) and Presage vitals
    implementation("androidx.camera:camera-core:1.6.0")
    implementation("androidx.camera:camera-camera2:1.6.0")
    implementation("androidx.camera:camera-lifecycle:1.6.0")
    implementation("androidx.camera:camera-view:1.6.0")
    implementation("com.google.guava:guava:33.4.8-android") // ListenableFuture for CameraX
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.presagetech:smartspectra:3.4.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.postgresql:postgresql:42.7.14")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("com.google.zxing:core:3.5.3")
}
