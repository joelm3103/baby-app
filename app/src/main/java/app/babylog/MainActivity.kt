package app.babylog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.babylog.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is what makes the inset handling matter, so it stays on: the app draws behind
        // the system bars and the Scaffold pads for them explicitly.
        enableEdgeToEdge()
        val container = (application as BabyLogApp).container
        setContent { AppRoot(container) }
    }
}
