import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.omnifind.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.omnifind.app"
        minSdk = 28                       // Android 9: the Mi A1
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // The Mi A1 is arm64. Add "armeabi-v7a" only for a phone whose `adb shell getprop ro.product.cpu.abi`
            // says so. (x86_64 is for the emulator, which is fine for UI work but far slower than the phone at this.)
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                // Always optimized: a debug llama.cpp is about 10x slower, too slow to try anything with.
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // The beta's own key (signing/, not in git: back it up). Every later version must be signed with it, or phones
    // with the beta refuse the update. Without it, release builds fall back to the debug key, for local tries only.
    val keystore = rootProject.file("signing/keystore.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("roborazzi.output", "build/screens")
            }
        }
    }

    packaging {
        jniLibs { useLegacyPackaging = false }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // SQLite with FTS5 and JSON1 built in (Android 9's own SQLite doesn't reliably have FTS5)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.work.runtime.ktx)
    // PDF text (Android's PdfRenderer only draws pages; it can't read their text before Android 15)
    implementation(libs.pdfbox.android)
    // Photos: EXIF orientation and dates; thumbnails in results
    implementation(libs.androidx.exifinterface)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    // Screens rendered on the Mac at the mockups' size, to compare with them (ScreenshotTest)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
}

/** The beta build, as testers get it: dist/OmniFind.apk (release, signed with the beta key, arm64). */
tasks.register<Copy>("betaApk") {
    group = "distribution"
    description = "Builds the release APK and puts it at dist/OmniFind.apk."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(rootProject.layout.projectDirectory.dir("dist"))
    rename { "OmniFind.apk" }
}
