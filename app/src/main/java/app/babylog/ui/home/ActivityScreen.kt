package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.babylog.ui.common.rememberNow
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerDate = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

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
            // Header: switcher above the date and title. Settings lives behind the icon on the right.
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ChildSwitcher(
                    children = state.children,
                    selection = state.selection,
                    onSelect = onSelectChild,
                )
                Text(
                    text = headerDate.format(today),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    // Settings is an icon here rather than a fourth tab: three tabs is the honest
                    // count for this app, and Settings is visited twice a year.
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = colors.textSecondary,
                        )
                    }
                }
            }
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
