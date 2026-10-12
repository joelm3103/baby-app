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

    // First-run only: the table is empty, so seed the two boys. Everything seeded is editable.
    LaunchedEffect(Unit) { container.children.seedIfEmpty() }

    BabyLogTheme(darkTheme = dark) {
        AppScaffold(container)
    }
}
