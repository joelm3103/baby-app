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
import app.babylog.domain.BabyEvent
import app.babylog.domain.badgesFor
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
    onOpenVaccine: () -> Unit,
    onQuickDiaper: (app.babylog.domain.DiaperKind) -> Unit,
    onStartSleep: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onSeeAll: (app.babylog.domain.EventType) -> Unit,
    onEditEntry: (BabyEvent) -> Unit,
    onDeleteEntry: (BabyEvent) -> Unit,
    /** The armed reminders, soonest first. Empty means the card is not drawn at all. */
    upcoming: List<app.babylog.domain.UpcomingReminder> = emptyList(),
    /** Tapping a row opens that activity's reminder sheet, for the child the row names. */
    onOpenReminder: (app.babylog.domain.EventType, childId: String?) -> Unit = { _, _ -> },
    /** Each card's reminders, one chip per child in scope, keyed by activity. */
    reminderChips: Map<app.babylog.domain.EventType, List<app.babylog.domain.ReminderChip>> = emptyMap(),
    /** The card's `⋮` menu. Growth and Sleep pass nothing, so they have no menu. */
    onReminderAction: (app.babylog.domain.EventType, ReminderAction, childId: String?) -> Unit =
        { _, _, _ -> },
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

        // Above the cards, because it answers the question the screen is opened with: what is coming.
        if (upcoming.isNotEmpty()) {
            item(key = "upcoming-reminders") {
                UpcomingRemindersCard(
                    reminders = upcoming,
                    onOpen = onOpenReminder,
                    zone = zone,
                )
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
                childInitials = badgesFor(state.children),
                childNames = state.children.associate { it.id to it.name },
                childIds = state.childIds,
                // The initial is worth its space only when a row could belong to either child.
                showChildBadge = state.childIds.size > 1,
                now = now,
                zone = zone,
                onOpenSheet = onOpenSheet,
                onOpenVaccine = onOpenVaccine,
                onQuickDiaper = onQuickDiaper,
                onStartSleep = onStartSleep,
                onStop = onStop,
                onSeeAll = { onSeeAll(card.type) },
                onEditEntry = onEditEntry,
                onDeleteEntry = onDeleteEntry,
                reminderChips = reminderChips[card.type].orEmpty(),
                // Only the four reminder-capable cards get a menu at all.
                onReminderAction = if (card.type in app.babylog.domain.REMINDER_TYPES) {
                    { action, childId -> onReminderAction(card.type, action, childId) }
                } else {
                    null
                },
            )
        }
    }
}
