import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing: keystore.properties (local, git-ignored) or environment variables (CI).
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signing(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signing("storeFile", "KEYSTORE_FILE")

android {
    namespace = "com.telegramreader.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.telegramreader.app"
        minSdk = 26
        targetSdk = 35
        // Overridable from CI: ./gradlew -PversionName=1.2.3 -PversionCode=42
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = project.findProperty("versionName") as String? ?: "0.1.0"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signing("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "KEY_ALIAS")
                keyPassword = signing("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    // One APK per ABI (arm64 ≈ 30 MB) plus a universal one, instead of a single 100 MB APK.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            // Falls back to the debug key when no release keystore is configured (local builds).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // TDLib's native library is large; keep it uncompressed so it can be mmapped directly from the APK.
        jniLibs.useLegacyPackaging = false
    }
}

dependencies {
    // Prebuilt TDLib (org.drinkless.tdlib.Client / TdApi + libtdjni.so). See scripts/fetch-tdlib.sh.
    implementation(files("libs/tdlib.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
