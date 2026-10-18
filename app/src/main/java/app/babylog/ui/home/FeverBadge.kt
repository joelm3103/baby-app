package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius

/**
 * The fever marker: a small filled pill, not just a tinted number.
 *
 * The first version highlighted a fever by colouring the reading with `feverOnCard`, and the owner could
 * not see it at all. That was fair: a pastel red on a magenta card — beside a label that is itself a
 * lifted magenta — is not a signal, it is a slightly warm grey. A pill is a SHAPE, so it reads at a
 * glance and does not depend on telling two similar hues apart, and the word sits inside it, so the
 * meaning survives for anyone who cannot distinguish the colours at all.
 *
 * Colours are measured, not picked: `onAccent` on a `feverOnCard` fill is 10.39:1 dark / 10.30:1 light,
 * and the fill against the Health card's 26% tint is 5.71:1 / 6.75:1 — so the pill is legible AND its
 * edge is visible against the card. Both ratios are recorded in `design-tokens.md`.
 */
@Composable
fun FeverBadge(modifier: Modifier = Modifier) {
    val colors = BabyLogTheme.colors
    Text(
        text = "Fever",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = colors.onAccent,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.feverOnCard)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
