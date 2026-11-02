import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
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

/**
 * Local-only release signing (Phase 16). Read from `keystore.properties` at the repo root, or from
 * `%USERPROFILE%\keystores\keystore.properties` — the script that creates the keystore writes the
 * second one, so the key and its metadata stay outside the repo and outside the synced folder.
 *
 * Absent on a fresh clone, deliberately: the release signing config is then not created at all and
 * `:app:assembleRelease` produces an unsigned APK, while `:app:testDebugUnitTest` and
 * `:app:assembleDebug` are untouched. A clone without the key can still build and test.
 */
val keystoreProperties = Properties().apply {
    val candidates = listOf(
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), "keystores/keystore.properties"),
    )
    candidates.firstOrNull { it.exists() }?.inputStream()?.use { load(it) }
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
        // Phase 13.1: explicit 12/24-hour and Celsius/Fahrenheit display preferences.
        // Storage stays canonical; this is the next capability rung after 0.9.1.
        // 0.11.0 is Phase 13.2, the four temperature bands — a new capability, so a new MINOR rung.
        // 0.12.0 is Phase 13.3, diaper detail: two independent answers plus colour and consistency,
        // which is a new capability and a third schema version, so another MINOR rung.
        // 0.13.0 is Phase 13.4, activity reminders: local notifications per activity and child, which is
        // a new capability with no schema change.
        // 0.16.0 is Phase 14, polish plus new glyph and metric-chip capabilities.
        // 0.17.0 is Phase 13.5, the growth dataset update channel: a newer WHO dataset can be fetched,
        // checksum-verified and cached without an APK release. versionCode rises to 21.
        // 0.17.1 is Phase 13.5's device pass: the settings identity line is observed through a StateFlow
        // instead of being polled on entry, so an accepted update changes it on the screen that ran it.
        // A fix found by the pass, so a PATCH rung and not a new capability. versionCode rises to 22.
        // 0.18.0 is Phase 15, reset to defaults: two buttons that return the app to first-run state, one
        // local and one that also clears the server. A new capability, so a MINOR rung: versionCode 23.
        // 0.18.1 fixes the three defects Phase 15 shipped with — a drain that stopped at the first slow
        // write, a reset that re-seeded the twins, and an import that merged instead of replacing. The
        // reset now leaves NO children, so it also ships the empty state that state needs. versionCode 24.
        // 0.19.0 closes three gaps that were already recorded rather than opened: a growth dataset a phone
        // has accepted can now be WITHDRAWN (a `withdrawn` flag on the manifest clears the cached copy), the
        // never-logged reminder names the fact instead of saying `Time to log diaper`, and an all-zero volume
        // series draws one gridline rather than a fabricated 0.02-step scale. The withdrawal is a new
        // capability in the update channel, so a MINOR rung: versionCode 25.
        // 1.0.0 is Phase 16, update delivery: a release-signed APK that reaches both phones over the air,
        // with the release certificate's SHA-1 registered on the Google OAuth client. The first release rung,
        // so versionCode 26 — and the first build whose versionCode decides anything other than history,
        // because App Distribution compares it to decide whether a build is newer.
        // 1.1.0 is Phase 17, five `todo.txt` items: a child selector on Trends, `1Y` (weekly buckets) and
        // `All` (monthly, from the first event) ranges, an About card, per-activity hiding, and haptics. A
        // MINOR rung because it adds capabilities, and a version ABOVE the release rather than a 0.x one:
        // the ladder reached 1.0.0, so the next name has to be newer than the release on the phone.
        // versionCode 27.
        // 1.2.0 is Phase 18, the foods catalogue: a published catalogue document sits above the bundled
        // asset, `foods_pending` carries additions between the phones, and local rename/hide opinions stay
        // on-device with proposals for the owner. New capability, so a MINOR rung above 1.1.0: code 28.
        // 1.2.1 is Phase 19, the Settings redo: the eleven settings groups became a category menu with one
        // submenu each, and back now means "up one level" before it means "leave Settings". Polish only —
        // no setting moved between groups, no control changed, no schema change — so a PATCH rung, and code
        // 29 so the pass installs in place over the delivered 1.2.0.
        // 1.3.0 is Phase 20, the tracking changes: a bottle preset becomes a one-tap log on the card (the
        // Diaper pills' path, borrowed), a Both-mode card names its child instead of showing an initial, the
        // reminder sheet stops asking whether to notify and can remove a rule, and a posted reminder is
        // cleared when its activity is logged or its rule goes off. One new capability, so a MINOR rung:
        // code 30.
        versionCode = 30
        versionName = "1.3.0"

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

    signingConfigs {
        // Created only when a key is present, so a clone without one never fails to configure.
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null when no keystore is present: the APK is unsigned, which is the honest thing for a
            // machine that does not hold the family's key. See docs/release-signing.md.
            signingConfig = signingConfigs.findByName("release")
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
