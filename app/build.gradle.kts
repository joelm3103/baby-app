import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android plugin — AGP 9.0+ compiles Kotlin itself (built-in Kotlin).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.babylog"
    // Compose BOM 2026.09.00 ships Compose 1.12.1, whose AAR metadata requires compiling
    // against API 37+. AGP 9.4.1 has compileSdkMinor (verified via javap on gradle-api
    // 9.4.1) and platforms;android-37.2 is installed.
    // targetSdk stays 36 deliberately: compiling against 37.2 does not opt in to new
    // runtime behaviour, and this app has no store requirements to satisfy.
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "app.babylog"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            // Left off until Phase 9 so a crash in the log is readable while building.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        }
    }
}

ksp {
    // Exported schemas are committed so migrations can be tested against real versions.
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // Deliberately NOT jvmToolchain(17): that would make Gradle hunt for a JDK 17
        // installation, and this machine has only Studio's JBR 25.
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room: the local database is the source of truth (see README.md).
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Preferences: units, selected child, theme.
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    // Robolectric runs Room DAO tests on the JVM, so the DAO contract is verified without a device
    // or emulator. That matters because the emulator was deliberately skipped (Phase 0).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
