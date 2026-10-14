plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.watstack.glyphweather"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.watstack.glyphweather"
        minSdk = 34
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Nothing's Glyph Matrix SDK, fetched by scripts/fetch-sdk.sh (not committed: licence forbids redistribution).
    implementation(files("libs/glyph-matrix-sdk-2.0.aar"))

    testImplementation("junit:junit:4.13.2")
}
