package app.babylog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.babylog.ui.theme.BabyLogTheme

/**
 * Honest placeholders. Each names the phase that replaces it rather than pretending to be a screen —
 * an empty chart with fake axes would be worse than saying what is missing.
 */
@Composable
private fun Placeholder(title: String, phase: String, contentPadding: PaddingValues) {
    val colors = BabyLogTheme.colors
    Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
        Text(
            text = phase,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun HistoryScreen(contentPadding: PaddingValues) =
    Placeholder("History", "1d \u00b7 7d \u00b7 14d totals arrive in Phase 8.", contentPadding)

@Composable
fun TrendsScreen(contentPadding: PaddingValues) =
    Placeholder("Trends", "Charts over time arrive in Phase 11, once there is real data to plot.", contentPadding)

@Composable
fun SettingsScreen(contentPadding: PaddingValues) =
    Placeholder("Settings", "Children, units, appearance and export arrive in Phase 7.", contentPadding)
