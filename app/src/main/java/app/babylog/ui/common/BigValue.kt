package app.babylog.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

/**
 * The largest element on any card: value big, unit small. Never truncates the number — it drops the
 * unit instead, because a half-visible quantity is worse than no unit at all.
 */
@Composable
fun BigValue(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (unit.isNotBlank()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                maxLines = 1,
            )
        }
    }
}
