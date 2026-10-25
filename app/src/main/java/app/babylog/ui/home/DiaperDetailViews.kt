package app.babylog.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.babylog.domain.DiaperColor
import app.babylog.domain.DiaperKind
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.DiaperSwatch
import app.babylog.ui.theme.label
import app.babylog.ui.theme.swatch

/**
 * A dirty nappy's colour, drawn as the two things the same idea needs: a row of tap targets in the sheet,
 * and a bullet on a card row.
 *
 * Both are the SAME painter with the SAME ring, deliberately. The owner's instruction was that a row shows
 * a small circle "just like the selections", so the card cannot drift into being a different-looking mark —
 * one function, two sizes.
 *
 * The circle carries no text label. A word under a 36dp circle doubles the row's height to repeat what the
 * circle already says, and the six values are the colours' own names. Accessibility is carried instead by
 * `contentDescription` and an explicit `selected` semantic, so the row is navigable with TalkBack even
 * though the sighted reading is colour alone.
 */
@Composable
private fun ColorCircle(
    color: DiaperColor,
    diameter: Dp,
    ring: Color,
    selectionRing: Color?,
) {
    Canvas(modifier = Modifier.size(diameter)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // The fill gives way to the OUTER ring when one is drawn, and shrinks by only half a hairline when
        // it is not: an 8dp bullet inset by 2dp would be a 4dp dot, which is a dot that has gone missing.
        val inset = if (selectionRing != null) DiaperSwatch.selectionRing.toPx() else DiaperSwatch.hairline.toPx() / 2f
        val fillRadius = (r - inset).coerceAtLeast(0.5f)
        drawCircle(color = color.swatch(), radius = fillRadius, center = center)
        drawCircle(
            color = ring,
            radius = fillRadius,
            center = center,
            style = Stroke(width = DiaperSwatch.hairline.toPx()),
        )
        if (selectionRing != null) {
            drawCircle(
                color = selectionRing,
                radius = r - DiaperSwatch.selectionRing.toPx() / 2f,
                center = center,
                style = Stroke(width = DiaperSwatch.selectionRing.toPx()),
            )
        }
    }
}

/**
 * The sheet's colour selector: six circles, multi-select, in a `FlowRow` so a narrower phone wraps rather
 * than clipping.
 *
 * The tap target is a 44dp box around a 36dp circle — the touch minimum without spreading the visible
 * marks apart — and selection adds a ring rather than changing the diameter, so ticking a second colour
 * never shifts the ones beside it (the same rule the temperature bands share one geometry for).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaperColorRow(
    selected: Set<DiaperColor>,
    accent: Color,
    onToggle: (DiaperColor) -> Unit,
    modifier: Modifier = Modifier,
    options: List<DiaperColor> = DiaperColor.entries,
) {
    val colors = BabyLogTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Colour",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(DiaperSwatch.gap),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val isSelected = option in selected
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = { onToggle(option) }, role = Role.Checkbox)
                        .semantics {
                            contentDescription = option.label()
                            // `this.selected`: the parameter of the same name shadows the semantic
                            // property, and assigning the parameter is what the compiler complains about.
                            this.selected = isSelected
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ColorCircle(
                        color = option,
                        diameter = DiaperSwatch.size,
                        ring = DiaperSwatch.ringFor(colors),
                        selectionRing = if (isSelected) accent else null,
                    )
                }
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

/**
 * The colour of a logged change, replayed on a card row — the same mark at 8dp.
 *
 * Rendered as a group with one description ("Yellow, Brown") rather than six separate accessible dots:
 * on a card this is a glance, not a control, and six focus stops per row would make the list unusable with
 * TalkBack.
 */
@Composable
fun DiaperColorDots(
    colors: List<DiaperColor>,
    modifier: Modifier = Modifier,
    size: Dp = DiaperSwatch.dot,
) {
    if (colors.isEmpty()) return
    val tokens = BabyLogTheme.colors
    Row(
        modifier = modifier.semantics {
            contentDescription = colors.joinToString(", ") { it.label() }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        colors.forEach { color ->
            ColorCircle(
                color = color,
                diameter = size,
                ring = DiaperSwatch.ringFor(tokens),
                selectionRing = null,
            )
        }
    }
}

/**
 * The two answers: Wet and Dirty, each an independent toggle.
 *
 * Not [ChipRow] — that asks one question with one answer, which is exactly the model this replaces. Each
 * chip is its own `ChoiceChip`-styled toggle, so both can read as selected at once, and
 * [DiaperKind.toggle] refuses to let the last one be switched off.
 */
@Composable
fun DiaperContentsRow(
    kind: DiaperKind,
    accent: Color,
    onToggle: (tapWet: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BabyLogTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Contents",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(
                label = "Wet",
                selected = kind.wet,
                accent = accent,
                onClick = { onToggle(true) },
            )
            ChoiceChip(
                label = "Dirty",
                selected = kind.dirty,
                accent = accent,
                onClick = { onToggle(false) },
            )
        }
        HorizontalDivider(color = colors.divider)
    }
}
