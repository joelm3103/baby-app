package app.babylog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.babylog.ui.theme.BabyLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BabyLogTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Phase 5 replaces this with HomeScreen. Kept intentionally empty so that
                    // Phase 1 verifies the toolchain, not the UI.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("BabyLog", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
