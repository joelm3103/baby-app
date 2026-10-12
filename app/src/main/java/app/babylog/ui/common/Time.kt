package app.babylog.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.babylog.domain.elapsed
import app.babylog.domain.relativeTime
import kotlinx.coroutines.delay

/**
 * A ticking clock for the UI. Ticks every second only while a timer is actually running; otherwise
 * once every 20 seconds, because a per-second recomposition of the whole screen for a label that says
 * "2h ago" is a battery bug, not a detail.
 */
@Composable
fun rememberNow(anyRunning: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(anyRunning) {
        val interval = if (anyRunning) 1_000L else 20_000L
        while (true) {
            now = System.currentTimeMillis()
            delay(interval)
        }
    }
    return now
}

/** "2h 5m ago" — the app's most-read string. */
@Composable
fun RelativeTimeText(then: Long, now: Long, modifier: Modifier = Modifier) {
    Text(
        text = relativeTime(now = now, then = then),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Live count-up while an event is in progress. */
@Composable
fun LiveTimerText(since: Long, now: Long, modifier: Modifier = Modifier) {
    Text(
        text = elapsed(now - since),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
