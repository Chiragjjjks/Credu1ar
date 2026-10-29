plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One source of truth for the version: gradle.properties. CI may override the build number.
val versionNameProp = providers.gradleProperty("VERSION_NAME").get()
val versionCodeProp = providers.gradleProperty("VERSION_CODE").get().toInt()

android {
    namespace = "io.github.chiragjjjks.credu1ar"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.chiragjjjks.credu1ar"
        minSdk = 26
        targetSdk = 37
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: versionCodeProp
        versionName = versionNameProp
    }

    // Release signing comes from environment variables (CI secrets, or ~/.credu1ar/keystore.env locally).
    // Without them the release APK is signed with the debug key so it still installs, but each machine has a
    // different debug key: uninstall before installing a build from another machine.
    val keystorePath = System.getenv("CREDU1AR_KEYSTORE_PATH")
    if (!keystorePath.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("CREDU1AR_STORE_PASSWORD")
            keyAlias = System.getenv("CREDU1AR_KEY_ALIAS")
            keyPassword = System.getenv("CREDU1AR_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core"))

    // Foundation only: no Material theme, no icon fonts, no chart library. Keeps the APK small and the look ours.
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
