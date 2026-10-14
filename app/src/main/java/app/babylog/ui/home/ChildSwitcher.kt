package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.ui.theme.BabyLogTheme

/**
 * First Twin · Second Twin · Both, in the HEADER (the owner's choice over the mockup's bottom-pinned placement, because
 * the tabs keep the bottom slot). Selected pill is outlined; `Both` is the default and interleaves
 * both children's data.
 */
@Composable
fun ChildSwitcher(
    children: List<Child>,
    selection: ChildSelection,
    onSelect: (ChildSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        children.forEach { child ->
            SwitcherPill(
                label = child.name,
                initials = child.name.take(1).uppercase(),
                selected = selection is ChildSelection.One && selection.childId == child.id,
                onClick = { onSelect(ChildSelection.One(child.id)) },
            )
        }
        if (children.size == 2) {
            SwitcherPill(
                label = "Both",
                initials = children.take(2).joinToString("") { it.name.take(1).uppercase() },
                selected = selection is ChildSelection.Both,
                onClick = { onSelect(ChildSelection.Both) },
            )
        }
    }
}

@Composable
private fun SwitcherPill(
    label: String,
    initials: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = BabyLogTheme.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.surfaceRaised else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (selected) colors.textPrimary else colors.divider,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (selected) colors.textPrimary else colors.divider),
        ) {
            Text(
                text = initials,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) colors.surface else colors.textSecondary,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
