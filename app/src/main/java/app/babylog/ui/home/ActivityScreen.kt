package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.babylog.ui.common.rememberNow
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ActivityScreen(
    state: HomeUiState,
    onSelectChild: (app.babylog.domain.ChildSelection) -> Unit,
    onOpenSheet: (app.babylog.domain.EventType) -> Unit,
    onQuickDiaper: (app.babylog.domain.DiaperKind) -> Unit,
    onStartSleep: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
) {
    val now = rememberNow(anyRunning = state.anyRunning)
    val colors = BabyLogTheme.colors

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        item {
            ScreenHeader(
                title = "Today",
                children = state.children,
                selection = state.selection,
                onSelectChild = onSelectChild,
                onOpenSettings = onOpenSettings,
                today = today,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.children.isEmpty()) {
            item {
                Text(
                    text = "Setting up\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        items(state.cards, key = { it.type.name }) { card ->
            ActivityCard(
                card = card,
                units = state.units,
                childLabel = state.childLabel,
                now = now,
                zone = zone,
                onOpenSheet = onOpenSheet,
                onQuickDiaper = onQuickDiaper,
                onStartSleep = onStartSleep,
                onStop = onStop,
                onSeeAll = {},
            )
        }
    }
}
