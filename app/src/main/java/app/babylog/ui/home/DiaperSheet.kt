package app.babylog.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.babylog.data.repo.EventDraft
import app.babylog.domain.BabyEvent
import app.babylog.domain.DiaperColor
import app.babylog.domain.DiaperConsistency
import app.babylog.domain.DiaperKind
import app.babylog.domain.EventType
import app.babylog.domain.dateOn
import app.babylog.ui.common.stoolRes
import app.babylog.ui.theme.label
import java.time.Instant
import java.time.ZoneId

/**
 * Diaper: time, Wet and/or Dirty, then — only when dirty — the colour and the consistency, then rash and
 * notes.
 *
 * **Two questions that can both be true.** The sheet used to ask one question with three answers
 * (Wet / Dirty / Mixed); it now asks whether it was wet and whether it was dirty, independently, and
 * [DiaperKind.MIXED] is only how "both" is spelled in storage. So the enum in the database, in a sync
 * document and in an export still means exactly what it always did.
 *
 * The card's quick pills have narrowed to Wet and Dirty — one tap for the two common answers — so saying
 * "both" costs opening this sheet. That was the owner's decision and it is why the pills did not simply
 * gain a third button: the point of the sheet is now the detail, not the answer.
 *
 * Colour and consistency are OPTIONAL and are ABSENT while Dirty is off, not disabled: they describe
 * stool, so a wet-only change never even sees the question, and the save below writes both null rather
 * than letting a stale pick ride along. "Not recorded" and "not damp" are different claims and the app
 * keeps them apart, exactly as it does for [BabyEvent.rash].
 *
 * [initial] is set when a row is being EDITED rather than a new change logged.
 */
@Composable
fun DiaperSheet(
    accent: Color,
    childLabel: String,
    zone: ZoneId,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    initial: BabyEvent? = null,
    /** Offered inside the sheet while editing; null for a new record. */
    onDelete: (() -> Unit)? = null,
) {
    var startedAt by remember { mutableStateOf(initial?.startedAt ?: now) }
    var kind by remember { mutableStateOf(initial?.diaperKind ?: DiaperKind.WET) }
    // null means "not recorded", so an edit must not turn it into a recorded false.
    var rash by remember { mutableStateOf(initial?.rash == true) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var colors by remember { mutableStateOf(initial?.diaperColors.orEmpty().toSet()) }
    var consistencies by remember { mutableStateOf(initial?.diaperConsistency.orEmpty().toSet()) }

    ActivitySheet(
        type = EventType.DIAPER,
        title = if (initial != null) "Edit diaper" else "Diaper",
        subtitle = "for $childLabel",
        onDismiss = onDismiss,
        onDelete = onDelete,
        onSave = {
            onSave(
                EventDraft(
                    type = EventType.DIAPER,
                    startedAt = startedAt,
                    diaperKind = kind,
                    // true only when the toggle is on: null means "not recorded", which keeps the card's
                    // detail line honest instead of printing "· no rash" on every change.
                    rash = if (rash) true else null,
                    // Cleared when the nappy is not dirty, so unticking Dirty cannot smuggle a colour
                    // onto a wet-only row.
                    diaperColors = if (kind.dirty) colors.toList() else emptyList(),
                    diaperConsistency = if (kind.dirty) consistencies.toList() else emptyList(),
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
        // Two independent answers, so two chips rather than ChipRow's one-of-many. `toggle` keeps the last
        // selected one on: "neither" has no honest encoding and a disabled Save would have to explain it.
        DiaperContentsRow(
            kind = kind,
            accent = accent,
            onToggle = { tapWet -> kind = DiaperKind.toggle(kind, tapWet) },
        )
        if (kind.dirty) {
            DiaperColorRow(
                selected = colors,
                accent = accent,
                onToggle = { color -> colors = colors.toggled(color) },
            )
            MultiChipRow(
                label = "Consistency",
                options = DiaperConsistency.entries,
                selected = consistencies,
                accent = accent,
                labelOf = { it.label() },
                onToggle = { c -> consistencies = consistencies.toggled(c) },
                painterOf = { c -> painterResource(c.stoolRes()) },
                iconSize = 16.dp,
            )
        }
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

/** Adding to or removing from a set, without saying the same thing twice at every call site. */
private fun <T> Set<T>.toggled(value: T): Set<T> =
    if (value in this) this - value else this + value
