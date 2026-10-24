package app.babylog.ui

import androidx.compose.runtime.CompositionLocalProvider
import app.babylog.domain.TimeFormat
import app.babylog.domain.UnitPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.babylog.di.AppContainer
import app.babylog.domain.ThemeMode
import app.babylog.ui.theme.BabyLogTheme

@Composable
fun AppRoot(container: AppContainer) {
    val themeMode by container.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)
    val timeFormat by container.settings.timeFormat.collectAsState(initial = TimeFormat.TWENTY_FOUR_HOUR)
    val units by container.settings.units.collectAsState(initial = UnitPreferences())
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    // First-run only in effect: seed the two boys ONCE, behind a flag rather than an empty-table check
    // (a parent who removes their children must not find them resurrected on the next launch), and seed
    // the curated food catalogue, which is insert-if-absent and safe to run every time.
    LaunchedEffect(Unit) {
        container.settings.seedChildrenOnce { container.children.seedIfEmpty() }
        container.foods.seedIfNeeded()
        // A phone that was already sharing the log must keep sharing it after a restart: this restores
        // the drain and the inbound listener without a trip to Settings. Inert when signed out.
        container.sync.start()
    }

    BabyLogTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalTimeFormat provides timeFormat, LocalTemperatureUnit provides units.temperature) {
            AppScaffold(container)
        }
    }
}
