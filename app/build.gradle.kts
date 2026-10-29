plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from env vars (set by CI from GitHub secrets, or exported locally).
// Without them, release builds fall back to the debug key.
val keystorePath: String? = System.getenv("UNSCRIBE_KEYSTORE")

android {
    namespace = "au.com.intheevent.unscribe"
    compileSdk = 35

    defaultConfig {
        applicationId = "au.com.intheevent.unscribe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // Personal phone only; keeps the APK small.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("UNSCRIBE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UNSCRIBE_KEY_ALIAS")
                keyPassword = System.getenv("UNSCRIBE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    androidResources {
        // Voice model files must stay uncompressed so sherpa-onnx can read them efficiently.
        noCompress += listOf("onnx")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
