package app.babylog.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.babylog.data.repo.EventDraft
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.domain.dateOn
import java.time.Instant
import java.time.ZoneId

/**
 * Diaper: time, Wet/Dirty/Mixed, rash, notes.
 *
 * The card keeps its in-place Wet/Dirty/Mixed pills, because the common answer is one tap with no
 * sheet at all. This sheet exists for the cases the pills cannot express — a back-dated change, a rash,
 * or a note — so it opens with the same three answers rather than sending the parent back to the card.
 */
@Composable
fun DiaperSheet(
    accent: Color,
    childLabel: String,
    zone: ZoneId,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    var startedAt by remember { mutableStateOf(now) }
    var kind by remember { mutableStateOf(DiaperKind.WET) }
    var rash by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    ActivitySheet(
        title = "Diaper",
        accent = accent,
        subtitle = "for $childLabel",
        onDismiss = onDismiss,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.DIAPER,
                    startedAt = startedAt,
                    diaperKind = kind,
                    // true only when the toggle is on: null means "not recorded", which keeps the card's
                    // detail line honest instead of printing "· no rash" on every change.
                    rash = if (rash) true else null,
                    note = note.trim().ifEmpty { null },
                ),
            )
        },
    ) {
        DateRow(
            label = "Date",
            epochDay = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate().toEpochDay(),
        ) { epochDay -> startedAt = dateOn(startedAt, epochDay, zone) }
        TimeRow(instantMs = startedAt, zone = zone, onChange = { startedAt = it })
        ChipRow(
            label = "Type",
            options = DiaperKind.entries,
            selected = kind,
            accent = accent,
            labelOf = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            onSelect = { kind = it },
        )
        SwitchRow(label = "Rash", checked = rash, accent = accent, onCheckedChange = { rash = it })
        TextFieldRow(
            label = "Notes",
            value = note,
            onValueChange = { note = it },
            placeholder = "Optional",
            singleLine = false,
        )
    }
}
