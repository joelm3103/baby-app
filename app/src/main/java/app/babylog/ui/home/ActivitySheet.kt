package app.babylog.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import app.babylog.domain.EventType
import app.babylog.ui.common.glyphPainter
import app.babylog.ui.common.rememberHaptics
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Spacing
import app.babylog.ui.theme.accentFor
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
 *
 * The ACTIVITY is a parameter, not just its colour. The sheet covers the card that told the parent which
 * activity they were logging, so the title bar carries that activity's glyph — the one place in the app
 * where the glyph restores information rather than repeating it. Deriving the accent here, rather than
 * taking both, is what stops a caller pairing a Bottle glyph with a Health tint.
 */
@Composable
fun ActivitySheet(
    type: EventType,
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    saveLabel: String = "Save",
    /** Shown under the title bar, e.g. "for both" — a sheet must say who it is about to log for. */
    subtitle: String? = null,
    /**
     * Delete THIS entry, offered inside the sheet when a row is being edited.
     *
     * Long-press on a row already deletes, but a parent who has opened a record to change it should not have to close
     * it and hunt for the row again to remove it. Null for a new record, where there is nothing to delete.
     */
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BabyLogTheme.colors
    val accent: Color = colors.accentFor(type)

    // On the Activity tab the scaffold's own BackHandler is disabled, so this is the only enabled one.
    BackHandler(enabled = true, onBack = onDismiss)

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardFor(accent)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    // Title row, centred as a unit: glyph + gap on the left and an equal spacer on the
                    // right, so the words stay centred on the bar rather than on the space left over.
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = type.glyphPainter(),
                            contentDescription = null,
                            tint = colors.labelFor(accent),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = title,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.labelFor(accent),
                        )
                        Spacer(Modifier.width(22.dp))
                    }
                    // Save commits a record, so it gets the confirm buzz rather than the ordinary tap: the
                    // one press in a sheet whose consequence is written down.
                    val haptics = rememberHaptics()
                    TextButton(
                        onClick = {
                            haptics.confirm()
                            onSave()
                        },
                        enabled = saveEnabled,
                    ) {
                        Text(
                            text = saveLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (saveEnabled) colors.labelFor(accent) else colors.textOnCard,
                        )
                    }
                }
                subtitle?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.labelFor(accent),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter),
            ) {
                content()
                if (onDelete != null) {
                    DangerButton(
                        label = "Delete this entry",
                        onClick = onDelete,
                        modifier = Modifier.padding(top = Spacing.sectionGap, bottom = Spacing.gutter),
                    )
                }
            }
        }
    }
}
