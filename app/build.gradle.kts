import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.sridhar.harbor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sridhar.jellyverse"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "2.21.0"
        // Play Store builds (-Pstore) ship without preloaded IPTV directories – "bring your own playlist" per Play policy.
        buildConfigField("boolean", "PRELOAD_IPTV", if (project.hasProperty("store")) "false" else "true")
        // GitHub builds update themselves from GitHub Releases; Play builds are updated by Play only.
        buildConfigField("boolean", "SELF_UPDATE", if (project.hasProperty("store")) "false" else "true")
        buildConfigField("String", "UPDATE_REPO", "\"simpletoolsindia/jellyverse\"")
        // MediaPipe LLM + JSch are native/JVM heavy; 64-bit ARM covers all modern phones.
        ndk { abiFilters += listOf("arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
    }

    flavorDimensions += "form"
    productFlavors {
        create("phone") {
            dimension = "form"
        }
        create("tv") {
            dimension = "form"
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
            // Budget TVs (e.g. Xiaomi Mi TV 5A) often run a 32-bit userland on 64-bit chips.
            ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Upload key from keystore.properties (Play App Signing re-signs for distribution); debug key as a fallback.
            signingConfig = if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // Self-update permission + FileProvider only in sideload builds.
    if (!project.hasProperty("store")) sourceSets.getByName("main") { res.srcDir("src/sideload/res") }
    if (!project.hasProperty("store")) {
        sourceSets.getByName("debug") { manifest.srcFile("src/sideload/AndroidManifest.xml") }
        sourceSets.getByName("release") { manifest.srcFile("src/sideload/AndroidManifest.xml") }
    }
    lint {
        // Debug lint runs produce a full report; release builds still fail on fatal issues (lintVital).
        abortOnError = false
        checkReleaseBuilds = true
    }
    // Only ship the languages JellyVerse is actually translated into (drops ~80 library locales).
    androidResources { localeFilters += listOf("en", "ta") }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    implementation("com.github.mwiede:jsch:0.2.21")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.webkit:webkit:1.12.1")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    // IPTV sources mix formats: without these, a .mpd / rtsp:// / .ism channel URL crashes DefaultMediaSourceFactory.
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    // Software AC3 / E-AC3 / DTS / TrueHD audio (Jellyfin's build of the Media3 FFmpeg extension): decoding on the
    // device avoids HDMI passthrough, whose audio clock stutters on budget TV boxes and freezes the video with it.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("com.google.android.gms:play-services-cast-framework:22.0.0")
    implementation("androidx.media3:media3-database:$media3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    // TV ↔ phone QR pairing: ZXing draws the TV's QR; Google code scanner reads it on the phone (no camera permission).
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    "tvImplementation"("androidx.tvprovider:tvprovider:1.0.0")

    // On-device LLMs (Qwen3, Gemma 4, Phi-4, …) via Google's LiteRT-LM runtime (.litertlm models)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")

    // ---- Local (JVM) unit tests: src/test ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("com.google.truth:truth:1.4.4")

    // ---- Instrumented Compose UI / end-to-end tests: src/androidTest ----
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("com.google.truth:truth:1.4.4")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

}

// Harbor TV is a pure media player: drop the on-device LLM native libs from the TV build.
androidComponents {
    onVariants(selector().withFlavor("form" to "tv")) { v ->
        v.packaging.jniLibs.excludes.addAll(listOf("**/liblitertlm_jni.so"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}
