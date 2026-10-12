package app.babylog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.babylog.di.AppContainer
import app.babylog.ui.home.ActivityScreen
import app.babylog.ui.home.HomeViewModel
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import androidx.compose.runtime.collectAsState

enum class Destination(val label: String, val icon: ImageVector) {
    ACTIVITY("Activity", Icons.Filled.Home),
    HISTORY("History", Icons.Filled.DateRange),
    TRENDS("Trends", Icons.Filled.List),
    SETTINGS("Settings", Icons.Filled.List),
}

/**
 * The shell. Two things here are deliberate fixes for bugs the owner hit in the reference app, not styling choices:
 *
 *  1. **Insets.** The `NavigationBar` owns the bottom system inset itself, so the Scaffold's
 *     `contentWindowInsets` excludes the bottom — otherwise the inset is applied twice and content is
 *     pushed off-screen. The content padding then carries the bar height plus a gap, so the last card
 *     scrolls clear of everything. the reference app clips its Child screen here.
 *  2. **Back.** System back returns to Activity from any tab or Settings instead of leaving the app.
 *     the reference app's back exits, which the owner reported unprompted.
 */
@Composable
fun AppScaffold(container: AppContainer) {
    var destination by rememberSaveable { mutableStateOf(Destination.ACTIVITY) }
    val factory = remember {
        viewModelFactory {
            initializer { HomeViewModel(container.events, container.children, container.settings) }
        }
    }
    val home: HomeViewModel = viewModel(factory = factory)
    val state by home.state.collectAsState()
    val colors = BabyLogTheme.colors

    BackHandler(enabled = destination != Destination.ACTIVITY) {
        destination = Destination.ACTIVITY
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = {
            if (destination != Destination.SETTINGS) {
                NavigationBar(containerColor = colors.surface) {
                    Destination.entries
                        .filter { it != Destination.SETTINGS }
                        .forEach { tab ->
                            NavItem(
                                tab = tab,
                                selected = destination == tab,
                                onSelect = { destination = it },
                            )
                        }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    ) { inner ->
        val layoutDirection = LocalLayoutDirection.current
        val padding = PaddingValues(
            start = inner.calculateStartPadding(layoutDirection) + Spacing.gutter,
            end = inner.calculateEndPadding(layoutDirection) + Spacing.gutter,
            top = inner.calculateTopPadding(),
            bottom = inner.calculateBottomPadding() + Spacing.cardGap,
        )
        when (destination) {
            Destination.ACTIVITY -> ActivityScreen(
                state = state,
                onSelectChild = home::select,
                onAction = home::quickLog,
                onQuickDiaper = home::logDiaper,
                onStop = home::stopSleep,
                onOpenSettings = { destination = Destination.SETTINGS },
                contentPadding = padding,
            )
            Destination.HISTORY -> HistoryScreen(padding)
            Destination.TRENDS -> TrendsScreen(padding)
            Destination.SETTINGS -> SettingsScreen(padding)
        }
    }
}

@Composable
private fun RowScope.NavItem(
    tab: Destination,
    selected: Boolean,
    onSelect: (Destination) -> Unit,
) {
    val colors = BabyLogTheme.colors
    NavigationBarItem(
        selected = selected,
        onClick = { onSelect(tab) },
        icon = { Icon(tab.icon, contentDescription = null) },
        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.navActive,
            selectedTextColor = colors.navActive,
            unselectedIconColor = colors.navInactive,
            unselectedTextColor = colors.navInactive,
            indicatorColor = colors.surfaceRaised,
        ),
    )
}
