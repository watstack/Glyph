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
        // CI builds count up with the GitHub Actions run number, so each one can install over the last.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = run ?: 2
        versionName = if (run != null) "0.3.$run" else "0.3-local"
    }

    // One permanent key, so updates install over the top instead of needing an uninstall.
    // CI decodes it from the GLYPH_KEYSTORE_B64 secret; without it, builds use the usual throwaway debug key.
    val keystore = System.getenv("GLYPH_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    val glyphSigning = keystore?.let {
        signingConfigs.create("glyph") {
            storeFile = it
            storeType = "PKCS12"
            storePassword = System.getenv("GLYPH_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("GLYPH_KEY_ALIAS") ?: "glyph"
            keyPassword = System.getenv("GLYPH_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            if (glyphSigning != null) signingConfig = glyphSigning
        }
        release {
            isMinifyEnabled = false
            if (glyphSigning != null) signingConfig = glyphSigning
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
    // Real org.json for flight parsing tests; Android's copy is only a stub on the JVM.
    testImplementation("org.json:json:20240303")
}
