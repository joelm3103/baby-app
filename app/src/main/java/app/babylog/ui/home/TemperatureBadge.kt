package app.babylog.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.babylog.domain.TemperatureBand
import app.babylog.ui.theme.BabyLogTheme
import app.babylog.ui.theme.Radius

/** The word a band carries, as the owner approved it on the mockup. */
fun TemperatureBand.word(): String = when (this) {
    TemperatureBand.LOW -> "Low"
    TemperatureBand.NORMAL -> "Normal"
    TemperatureBand.MILD_FEVER -> "Mild"
    TemperatureBand.FEVER -> "Fever"
}

/**
 * The band marker: a small pill whose SHAPE carries the meaning, not its hue.
 *
 * The first version highlighted a fever by colouring the reading with `feverOnCard`, and the owner could
 * not see it at all. That was fair: a pastel red on a magenta card — beside a label that is itself a
 * lifted magenta — is not a signal, it is a slightly warm grey. A pill is a SHAPE, so it reads at a
 * glance and does not depend on telling two similar hues apart, and the word sits inside it, so the
 * meaning survives for anyone who cannot distinguish the colours at all.
 *
 * Three bands carry a pill and all three share ONE geometry, so a row cannot shift as a reading climbs or
 * falls: FEVER is filled, MILD_FEVER and LOW are outlined (see `design-tokens.md` and the approved
 * mockup). NORMAL has no marker at all — a marker on every reading would be noise, and the reading itself
 * is the signal.
 *
 * Colours are measured, not picked: `onAccent` on a `feverOnCard` fill is 10.39:1 dark / 10.30:1 light,
 * the fill against the Health card's 26% tint is 5.71:1 / 6.75:1, and the outline against that tint holds
 * the same 5.71:1 / 6.75:1 so the pill's edge is visible as well as its word.
 */
@Composable
fun TemperatureBadge(band: TemperatureBand, modifier: Modifier = Modifier) {
    val colors = BabyLogTheme.colors
    val shape = RoundedCornerShape(Radius.pill)
    val filled = band == TemperatureBand.FEVER
    val text = band.word()
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (filled) colors.onAccent else colors.feverOnCard,
        modifier = modifier
            .clip(shape)
            .then(
                if (filled) Modifier.background(colors.feverOnCard)
                else Modifier.border(1.5.dp, colors.feverOnCard, shape),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
