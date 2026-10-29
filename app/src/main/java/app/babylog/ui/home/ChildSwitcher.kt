package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import app.babylog.domain.Child
import app.babylog.domain.ChildSelection
import app.babylog.domain.badgesFor
import app.babylog.ui.common.rememberHaptics
import app.babylog.ui.theme.BabyLogTheme

/**
 * First Twin · Second Twin · Both, in the HEADER (the owner's choice over the mockup's bottom-pinned placement, because
 * the tabs keep the bottom slot). Selected pill is outlined; `Both` is the default and interleaves
 * both children's data.
 *
 * A [FlowRow] of whole pills, not a [Row] of pills with wrapping text. At a 130 % font scale the three
 * pills do not fit a phone's width, and the old arrangement wrapped the *word* instead: the default pill
 * read `Bot` over `h`, which looks like a broken build. Pills are now indivisible (`softWrap = false`,
 * `maxLines = 1`) and the row wraps them as units, so the worst case is a second line rather than a
 * chopped label.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChildSwitcher(
    children: List<Child>,
    selection: ChildSelection,
    onSelect: (ChildSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    val badges = badgesFor(children)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        children.forEach { child ->
            SwitcherPill(
                label = child.name,
                initials = badges.getValue(child.id),
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
    val haptics = rememberHaptics()
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
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Shared with the recent log rows, so the two cannot drift. The centring lives in ChildBadge.
        ChildBadge(initials = initials, selected = selected)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            // A pill's name is one word-pair. Never break it across lines.
            maxLines = 1,
            softWrap = false,
        )
    }
}
