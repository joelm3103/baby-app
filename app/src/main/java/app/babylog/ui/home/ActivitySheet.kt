package app.babylog.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.cardFor
import app.babylog.ui.theme.labelFor

/**
 * The full-screen logging sheet, straight from the reference app's real screenshots: an activity-tinted title bar
 * with **X · title · Save**, then hairline-divided input rows, and no bottom nav (this draws over it).
 *
 * Two of the owner's three the reference app complaints are fixed by construction here. The sheet is full-screen, so
 * nothing is clipped by the navigation bar; and `BackHandler` is co-located with the surface it
 * dismisses, so system back always closes the sheet. the reference app exits the app instead, which is the bug he
 * reported unprompted.
 */
@Composable
fun ActivitySheet(
    title: String,
    accent: Color,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saveLabel: String = "Save",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BabyLogTheme.colors

    // On the Activity tab the scaffold's own BackHandler is disabled, so this is the only enabled one.
    BackHandler(enabled = true, onBack = onDismiss)

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardFor(accent))
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = colors.labelFor(accent),
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.labelFor(accent),
                )
                TextButton(onClick = onSave, enabled = saveEnabled) {
                    Text(
                        text = saveLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (saveEnabled) colors.labelFor(accent) else colors.textOnCard,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter),
                content = content,
            )
        }
    }
}
