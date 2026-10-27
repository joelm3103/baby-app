package app.babylog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.ui.theme.BabyLogTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val headerDate = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/**
 * The app's identity, per the design contract: the child switcher, the date, then a large bold title
 * with the settings icon on the right.
 *
 * Shared by Activity and History on purpose — the contract says "keep it identical on Activity and
 * History", and the mental model is "who am I looking at", not "which tab". Duplicating it in two
 * screens is how the two drift apart.
 */
@Composable
fun ScreenHeader(
    title: String,
    children: List<Child>,
    selection: ChildSelection,
    onSelectChild: (ChildSelection) -> Unit,
    onOpenSettings: () -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        // Nothing to switch between with no children — and pills for nobody are the kind of empty control
        // that reads as a bug. The empty state on Activity is what explains the situation instead.
        if (children.isNotEmpty()) {
            ChildSwitcher(
                children = children,
                selection = selection,
                onSelect = onSelectChild,
            )
        }
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            // Settings is an icon here rather than a fourth tab: three tabs is the honest count for this
            // app, and Settings is visited twice a year.
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
