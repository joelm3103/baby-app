package app.babylog.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.babylog.di.AppContainer
import app.babylog.domain.EventType
import app.babylog.ui.home.ActivityScreen
import app.babylog.ui.home.BottleSheet
import app.babylog.ui.home.DiaperSheet
import app.babylog.ui.home.HomeViewModel
import app.babylog.ui.home.SolidsSheet
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
import androidx.compose.runtime.collectAsState
import java.time.ZoneId

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
            initializer {
                HomeViewModel(container.events, container.children, container.settings, container.foods)
            }
        }
    }
    val home: HomeViewModel = viewModel(factory = factory)
    val state by home.state.collectAsState()
    val openSheet by home.openSheet.collectAsState()
    val foods by home.foodList.collectAsState()
    val colors = BabyLogTheme.colors

    // Owned here, not inside ActivityScreen, so this shell can react to scrolling. The insets are
    // applied as LazyColumn contentPadding, which means items scroll THROUGH the status-bar region -
    // without the scrim below, a scrolled row collides with the clock and battery icons.
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val scrimAlpha by animateFloatAsState(if (scrolled) 1f else 0f, label = "topScrim")

    BackHandler(enabled = destination != Destination.ACTIVITY) {
        destination = Destination.ACTIVITY
    }

    // The whole shell sits inside a Box so the logging sheet can be drawn, as a SIBLING of the
    // Scaffold, over the bottom nav. A sheet rendered inside the Scaffold's content would sit under
    // the navigation bar, which is exactly the clipping bug this app exists to avoid.
    Box(modifier = Modifier.fillMaxSize()) {
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
        Box(modifier = Modifier.fillMaxSize()) {
            when (destination) {
                Destination.ACTIVITY -> ActivityScreen(
                    state = state,
                    onSelectChild = home::select,
                    onOpenSheet = home::openSheet,
                    onQuickDiaper = home::logDiaper,
                    onStartSleep = home::startSleep,
                    onStop = home::stopSleep,
                    onOpenSettings = { destination = Destination.SETTINGS },
                    contentPadding = padding,
                    listState = listState,
                )
                Destination.HISTORY -> HistoryScreen(padding)
                Destination.TRENDS -> TrendsScreen(padding)
                Destination.SETTINGS -> SettingsScreen(padding)
            }

            // Opaque behind the status bar once the list has moved, so the clock and battery never
            // sit on top of log text. Solid across the bar itself, then faded out below it, so there
            // is no hard seam. Drawn last, so it covers content rather than being covered.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp)
                    .alpha(scrimAlpha)
                    .background(
                        Brush.verticalGradient(
                            0.0f to colors.background,
                            0.65f to colors.background,
                            1.0f to colors.background.copy(alpha = 0f),
                        ),
                    ),
            )
        }
    }

        val sheetType = openSheet
        if (sheetType != null) {
            val sheetNow = remember(sheetType) { System.currentTimeMillis() }
            val zone = remember { ZoneId.systemDefault() }
            val accent = colors.accentFor(sheetType)
            when (sheetType) {
                EventType.BOTTLE -> BottleSheet(
                    accent = accent,
                    units = state.units,
                    zone = zone,
                    now = sheetNow,
                    initialAmountMl = state.lastBottleAmountMl,
                    onDismiss = home::dismissSheet,
                    onSave = home::save,
                )
                EventType.DIAPER -> DiaperSheet(
                    accent = accent,
                    zone = zone,
                    now = sheetNow,
                    onDismiss = home::dismissSheet,
                    onSave = home::save,
                )
                EventType.SOLIDS -> SolidsSheet(
                    accent = accent,
                    zone = zone,
                    now = sheetNow,
                    foods = foods,
                    onAddFood = home::addCustomFood,
                    onDismiss = home::dismissSheet,
                    onSave = home::save,
                )
                // Sleep runs from the card as a timer; Growth and Health are Phases 9-10.
                else -> Unit
            }
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
