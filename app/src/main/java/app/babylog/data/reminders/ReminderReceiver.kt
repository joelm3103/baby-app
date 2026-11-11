package app.babylog.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.babylog.BabyLogApp
import app.babylog.domain.EventType
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

/**
 * Fires when an alarm says a reminder is due, and hands the work to [Reminders].
 *
 * Deliberately thin: everything that can be wrong lives in [Reminders] and is driven by tests, and this class
 * only has to turn an Intent back into an (activity, child) pair. A receiver has about ten seconds of
 * main-thread budget, so the work here is one DataStore read, one indexed query and one `notify()` —
 * short enough that a blocking `runBlocking` is the honest choice: handing off asynchronously would let the
 * process be killed between the alarm and the notification.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(EXTRA_TYPE)
            ?.let { name -> EventType.entries.firstOrNull { it.name == name } }
            ?: return
        val childId = intent.getStringExtra(EXTRA_CHILD_ID) ?: return
        val app = context.applicationContext as? BabyLogApp ?: return
        // Read defensively: an intent armed by a build before this extra existed carries none, and a 0 would
        // claim the reminder was due in 1970. Absent means "unknown", which the text renders as it always did.
        val armedAtMs = intent.getLongExtra(EXTRA_AT, 0L).takeIf { it > 0 }

        runBlocking {
            app.container.reminders.fire(type, childId, ZoneId.systemDefault(), System.currentTimeMillis(), armedAtMs)
        }
    }

    companion object {
        const val ACTION_FIRE = "app.babylog.reminders.FIRE"

        /** The action on a notification's tap intent; the activity reads it to open the right sheet. */
        const val ACTION_OPEN = "app.babylog.reminders.OPEN"

        const val EXTRA_TYPE = "app.babylog.reminders.type"
        const val EXTRA_CHILD_ID = "app.babylog.reminders.childId"

        /** The moment the alarm was armed for, so the notification can say how overdue it is. */
        const val EXTRA_AT = "app.babylog.reminders.armedAt"

        /** The intent the scheduler arms and this receiver receives. */
        fun intentFor(context: Context, type: EventType, childId: String, armedAtMs: Long? = null): Intent =
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_TYPE, type.name)
                putExtra(EXTRA_CHILD_ID, childId)
                // FLAG_UPDATE_CURRENT in the scheduler means a re-arm replaces this value rather than stacking
                // a second alarm with a stale time.
                if (armedAtMs != null) putExtra(EXTRA_AT, armedAtMs)
            }
    }
}

/**
 * Re-arms everything after the alarms have been wiped.
 *
 * Alarms do not survive a reboot or an app update, and a reminder that quietly stops working after the phone
 * restarts is the failure mode nobody notices until a feed is six hours late. Both broadcasts land here.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val relevant = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!relevant) return
        val app = context.applicationContext as? BabyLogApp ?: return
        runBlocking { app.container.reminders.refresh() }
    }
}
