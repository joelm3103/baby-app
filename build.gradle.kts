plugins {
    alias(libs.plugins.android.application) apply false
    // No kotlin-android plugin: AGP 9.0+ has built-in Kotlin support and rejects it
    // ("The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin
    // support since AGP 9.0"). Kotlin is compiled by AGP itself.
    alias(libs.plugins.kotlin.compose) apply false
}
