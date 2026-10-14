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

    // First-run only in effect: the children table is empty, so seed the two boys, and the foods
    // table is empty, so seed the curated catalogue. Both seeds are idempotent — the food seed is
    // insert-if-absent, so it also applies a newer bundled asset without touching custom entries.
    LaunchedEffect(Unit) {
        container.children.seedIfEmpty()
        container.foods.seedIfNeeded()
    }

    BabyLogTheme(darkTheme = dark) {
        AppScaffold(container)
    }
}
