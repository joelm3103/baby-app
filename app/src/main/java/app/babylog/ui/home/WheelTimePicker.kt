package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.babylog.R
import app.babylog.domain.TimeInputMode
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

// [TimeInputMode] itself lives in `domain`, because Phase 29 made it a stored preference; what stays here is
// the presentation: the words, the chip label and the drawable.

/** The mode the dialog's one button leads to. A cycle, so no mode is stranded. */
internal fun nextTimeInputMode(mode: TimeInputMode): TimeInputMode = when (mode) {
    TimeInputMode.DIAL -> TimeInputMode.WHEEL
    TimeInputMode.WHEEL -> TimeInputMode.TEXT
    TimeInputMode.TEXT -> TimeInputMode.DIAL
}

/**
 * How a mode is named out loud.
 *
 * The button carries the DESTINATION, not the current state, so a screen reader announcing it says what the
 * tap will do — the same rule the `›` on an input row follows.
 */
internal val TimeInputMode.spoken: String
    get() = when (this) {
        TimeInputMode.DIAL -> "clock face"
        TimeInputMode.WHEEL -> "wheels"
        TimeInputMode.TEXT -> "keyboard"
    }

/**
 * How a mode is named in Settings.
 *
 * Shorter than [spoken] on purpose: a chip is read at a glance and a screen reader is not reading a
 * sentence, so `Clock`/`Wheels`/`Keypad` is the row and `clock face`/`wheels`/`keyboard` is the button.
 */
internal val TimeInputMode.label: String
    get() = when (this) {
        TimeInputMode.DIAL -> "Clock"
        TimeInputMode.WHEEL -> "Wheels"
        TimeInputMode.TEXT -> "Keypad"
    }

/** The mode's own mark, drawn on the dialog's switch button. */
internal fun TimeInputMode.iconRes(): Int = when (this) {
    TimeInputMode.DIAL -> R.drawable.ic_time_mode_dial
    TimeInputMode.WHEEL -> R.drawable.ic_time_mode_wheel
    TimeInputMode.TEXT -> R.drawable.ic_time_mode_text
}

/**
 * The labels down the hours column: `00`–`23`, or `01`–`12`.
 *
 * Twelve-hour labels are NOT `hour % 12`: midnight and noon both read `12`, which is the whole reason
 * [wheelHourIndex] and [unWheelHour] exist rather than the arithmetic being inlined at three call sites.
 */
internal fun wheelHourLabels(is24Hour: Boolean): List<String> =
    if (is24Hour) (0..23).map { "%02d".format(it) } else (1..12).map { "%02d".format(it) }

/** The minute column: every minute, because the app stores an exact minute and a feed's time matters to it. */
internal val WHEEL_MINUTE_LABELS: List<String> = (0..59).map { "%02d".format(it) }

/** Where a 0–23 hour sits in the hours column. */
internal fun wheelHourIndex(hour: Int, is24Hour: Boolean): Int {
    val h = hour.coerceIn(0, 23)
    if (is24Hour) return h
    // 00:xx and 12:xx both read 12; 13:xx reads 1.
    return (if (h % 12 == 0) 12 else h % 12) - 1
}

/** The 0–23 hour a column position means, given which half of the day the AM/PM column is showing. */
internal fun unWheelHour(index: Int, is24Hour: Boolean, isPm: Boolean): Int {
    if (is24Hour) return index.coerceIn(0, 23)
    val twelve = index.coerceIn(0, 11) + 1
    return when {
        // 12 AM is midnight, not noon.
        !isPm && twelve == 12 -> 0
        !isPm -> twelve
        // 12 PM is noon, not 24:00.
        twelve == 12 -> 12
        else -> twelve + 12
    }
}

/** Which half of the day a 0–23 hour is in. */
internal fun isPmHour(hour: Int): Boolean = hour.coerceIn(0, 23) >= 12

/** One visible row of a wheel. Three are shown, so the middle one is the selection. */
private val WHEEL_ROW = 44.dp

/** The band behind the selected row. */
private val WHEEL_BAND = WHEEL_ROW

/** How many rows are visible at once: two greyed neighbours and the selected one. */
private const val WHEEL_ROWS = 3

/**
 * The rolling-selector time picker, built here rather than hosted from `android.widget.TimePicker`.
 *
 * Material3 has no wheel, and the platform widget in spinner mode is the OS's own chrome: it would ignore
 * BabyLog's tokens, its accent and its light theme, and it is the one control in a sheet that a parent would
 * notice as foreign. Three snapping columns are not much code and they follow the app.
 *
 * Each column is its own list, so hour and minute scroll independently — which is what makes a wheel feel like
 * a wheel rather than a table. Nothing here is a `TimePickerState`: the dialog owns the pair of numbers and
 * hands them both ways, so the wheel and the dial cannot each hold half the truth.
 */
@Composable
internal fun WheelTimePicker(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
    /**
     * The AM/PM column, as the half of the day rather than as an hour.
     *
     * It used to be routed back through `unWheelHour(hourIndex, ...)`, which made the column depend on an
     * hour index the parent had possibly already moved — a loop between two columns, each reporting a value
     * computed from the other. Half of the day is the only thing that column knows; the dialog does the shift.
     */
    onHalfDay: (isPm: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    val hours = wheelHourLabels(is24Hour)
    val hourIndex = wheelHourIndex(hour, is24Hour)
    val pm = isPmHour(hour)

    Box(modifier = modifier.fillMaxWidth()) {
        // The selection band, drawn UNDER the columns so the numbers sit on it rather than being tinted by it.
        // Derived from the text colour rather than a fixed surface token: an AlertDialog's container is
        // Material's to derive, so `surfaceRaised` here measured as nothing at all in one of the two themes.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WHEEL_BAND)
                .clip(RoundedCornerShape(Radius.card))
                .background(colors.textPrimary.copy(alpha = 0.10f)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(WHEEL_ROW * WHEEL_ROWS),
            horizontalArrangement = Arrangement.Center,
        ) {
            WheelColumn(
                label = "Hours",
                values = hours,
                selectedIndex = hourIndex,
                onSelected = { index -> onHour(unWheelHour(index, is24Hour, pm)) },
            )
            WheelColumn(
                label = "Minutes",
                values = WHEEL_MINUTE_LABELS,
                selectedIndex = minute.coerceIn(0, 59),
                onSelected = onMinute,
            )
            // The AM/PM column exists only in 12-hour mode, exactly as the dial's inner ring does: in
            // 24-hour mode the hour column already says which half of the day it is.
            if (!is24Hour) {
                WheelColumn(
                    label = "AM or PM",
                    values = listOf("AM", "PM"),
                    selectedIndex = if (pm) 1 else 0,
                    onSelected = { index -> onHalfDay(index == 1) },
                )
            }
        }
    }
}

/**
 * One snapping column.
 *
 * `firstVisibleItemIndex` is the selection, which is what the vertical padding is for: with a three-row
 * viewport and one row of padding above, the first fully visible item sits on the band. Reading the index
 * back is one-way — the column never scrolls itself to a value it is handed, so a scroll cannot be undone
 * mid-fling by the state it just reported.
 */
@Composable
private fun WheelColumn(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, values.lastIndex),
    )
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val current by remember { derivedStateOf { state.firstVisibleItemIndex.coerceIn(0, values.lastIndex) } }

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .distinctUntilChanged()
            // The first emission is the position this column was CREATED at, which the parent already knows.
            // Reporting it back is not a no-op: for the hour column it can arrive before the list has
            // measured, so it says `00` to a wheel showing midday, and the parent then rebuilds the state
            // from a value nobody chose. Only a move the parent has not seen is worth reporting.
            .drop(1)
            .collect { index -> onSelected(index.coerceIn(0, values.lastIndex)) }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = WHEEL_ROW),
        modifier = modifier
            .width(72.dp)
            .height(WHEEL_ROW * WHEEL_ROWS)
            // One description per column: three scrolling numbers with no label are three anonymous lists.
            .semantics { contentDescription = label },
    ) {
        itemsIndexed(values) { index, value ->
            // A Box, not a padded Text: a Text lays its glyphs out from the top of the row, so the numbers
            // sat high inside the band until this was measured.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(WHEEL_ROW),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    // Weight, not size: a size change reflows the row and makes the wheel jump as it settles.
                    fontWeight = if (index == current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (index == current) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
