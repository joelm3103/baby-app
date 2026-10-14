package app.babylog.ui

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
    }

    BabyLogTheme(darkTheme = dark) {
        AppScaffold(container)
    }
}
