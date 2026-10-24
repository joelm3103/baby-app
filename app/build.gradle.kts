import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // No kotlin-android plugin — AGP 9.0+ compiles Kotlin itself (built-in Kotlin).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    // NO com.google.gms.google-services deliberately. That plugin hard-fails the build when
    // app/google-services.json is missing, and that file is gitignored because it carries the
    // project's keys — so a fresh clone could not even run the unit tests. The same three values
    // are read from an optional local firebase.properties instead and passed to FirebaseOptions.
}

/**
 * Local-only Firebase configuration. Absent on a fresh clone, and the app then starts as a
 * single-phone logger rather than refusing to launch: see `data/sync/FirebaseSetup.kt`.
 */
val firebaseProperties = Properties().apply {
    val file = rootProject.file("firebase.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun firebaseValue(key: String): String = firebaseProperties.getProperty(key, "").trim()

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
        // Phase 13.1: explicit 12/24-hour and Celsius/Fahrenheit display preferences.
        // Storage stays canonical; this is the next capability rung after 0.9.1.
        // 0.11.0 is Phase 13.2, the four temperature bands — a new capability, so a new MINOR rung.
        versionCode = 15
        versionName = "0.11.0"

        // Empty strings when no local firebase.properties is present, which is exactly how the app
        // tells "this build has no sync configured" apart from "sync is configured, but signed out".
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${firebaseValue("firebase.projectId")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${firebaseValue("firebase.appId")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${firebaseValue("firebase.apiKey")}\"")
        buildConfigField("String", "FIREBASE_WEB_CLIENT_ID", "\"${firebaseValue("firebase.webClientId")}\"")
        // The two uids, comma separated. Empty is allowed: the Firestore rules are the real gate, and
        // this list only lets the app say WHY a third account was refused instead of showing a read error.
        buildConfigField("String", "FIREBASE_ALLOWED_UIDS", "\"${firebaseValue("firebase.allowedUids")}\"")
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
        // For the Firebase values above. AGP 8+ defaults this off.
        buildConfig = true
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

    // Sync (Phase 13). Room stays the source of truth; Firestore is a transport, never a read path.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Google Sign-In as the SECOND sign-in option. Email/password is the primary one precisely
    // because it works whatever key the APK was signed with.
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    // Robolectric runs Room DAO tests on the JVM, so the DAO contract is verified without a device
    // or emulator. That matters because the emulator was deliberately skipped (Phase 0).
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
