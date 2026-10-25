package app.babylog

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.babylog.data.reminders.ReminderReceiver
import app.babylog.di.SheetRequest
import app.babylog.domain.EventType
import app.babylog.ui.AppRoot

/**
 * The one activity.
 *
 * `singleTop` in the manifest plus [onNewIntent] is what makes a reminder's tap work while the app is already
 * open: without it, tapping the notification would start a SECOND activity over the first, and the sheet would
 * open behind it.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is what makes the inset handling matter, so it stays on: the app draws behind
        // the system bars and the Scaffold pads for them explicitly.
        enableEdgeToEdge()
        val container = (application as BabyLogApp).container
        handleIntent(intent)
        setContent { AppRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * Turns a reminder's tap into a request the UI can act on.
     *
     * Both extras are required: a type with no child would open a sheet for whoever the header happens to show,
     * which is exactly the wrong answer when the notification said "Second Twin".
     */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != ReminderReceiver.ACTION_OPEN) return
        val type = intent.getStringExtra(ReminderReceiver.EXTRA_TYPE)
            ?.let { name -> EventType.entries.firstOrNull { it.name == name } }
            ?: return
        val childId = intent.getStringExtra(ReminderReceiver.EXTRA_CHILD_ID) ?: return
        (application as BabyLogApp).container.requestSheet(SheetRequest(type, childId))
    }
}
