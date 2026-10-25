package app.babylog.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.babylog.domain.EventType
import app.babylog.domain.ReminderKey

/**
 * The only place this app talks to `AlarmManager`.
 *
 * **Inexact on purpose.** `setAndAllowWhileIdle` needs no permission and fires within a few minutes of its
 * target even in Doze; `setExactAndAllowWhileIdle` would need `SCHEDULE_EXACT_ALARM` (a special-access screen
 * the parent would have to visit) or `USE_EXACT_ALARM` (a privilege reserved for alarm clocks and calendars).
 * A feed reminder that arrives at 12:03 instead of 12:00 is not worth either. This is the app's one accepted
 * imprecision and `PLAN.md` records it as a decision rather than an oversight.
 *
 * One alarm per (activity, child) - at most twelve - and `FIXED_TIMES` arms only the next occurrence, so the
 * ceiling holds however many times of day a rule has.
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(key: ReminderKey, atMillis: Long) {
        val manager = alarmManager ?: return
        // RTC_WAKEUP, not ELAPSED_REALTIME: the rule is expressed in local clock terms ("3h after the last
        // feed"), and an elapsed-time alarm would drift out of agreement with the log across a reboot.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(key))
    }

    fun cancel(key: ReminderKey) {
        alarmManager?.cancel(pendingIntent(key)) ?: return
    }

    /**
     * `FLAG_IMMUTABLE` because nothing outside this app may rewrite the intent; `FLAG_UPDATE_CURRENT` so a
     * re-arm replaces the previous target time instead of leaving two alarms for the same reminder.
     */
    private fun pendingIntent(key: ReminderKey): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(key),
        ReminderReceiver.intentFor(context, key.type, key.childId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestCode(key: ReminderKey): Int = key.type.ordinal * 31 + key.childId.hashCode()

    /** Exposed for the tests and for `dumpsys alarm` cross-checks on a phone. */
    companion object {
        fun intentForTest(context: Context, type: EventType, childId: String): Intent =
            ReminderReceiver.intentFor(context, type, childId)
    }
}
