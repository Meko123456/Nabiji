import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Release signing comes from keystore.properties (git-ignored) or, failing that, the
// NABIJI_KEYSTORE_* environment variables. With neither, the release build is simply left
// unsigned, so `assembleRelease` still works on a fresh checkout and in CI.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "NABIJI_KEYSTORE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "NABIJI_KEYSTORE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "NABIJI_KEYSTORE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "NABIJI_KEYSTORE_KEY_PASSWORD")
val hasReleaseSigning =
    releaseStoreFile != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "io.github.meko123456.nabiji"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.meko123456.nabiji"
        // Health Connect is part of the framework from Android 14; below that it is a separate
        // app, so 26 keeps the door open while the UI degrades to an "unavailable" state.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed only when credentials are supplied; otherwise an unsigned APK.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.health.connect.client)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    // My own published library — the daily-activity heatmap on the dashboard.
    implementation(libs.heatmap)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
