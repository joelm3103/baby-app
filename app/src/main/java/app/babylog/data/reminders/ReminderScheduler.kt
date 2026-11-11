package app.babylog.data.reminders

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.PendingIntent
import app.babylog.domain.EventType
import app.babylog.domain.ReminderKey

/**
 * The only place this app talks to `AlarmManager`.
 *
 * **EXACT, and that is a reversal.** The first build used `setAndAllowWhileIdle` with the reasoning that a feed
 * nudge a few minutes late is worth not asking for a special-access screen. The phone disagreed with the premise:
 * `dumpsys alarm` showed the armed alarms carrying `window=+37m47s`, so "a few minutes" could be most of an hour,
 * and the owner reported reminders arriving late. A reminder that fires an hour after it was wanted is not a
 * reminder.
 *
 * So the manifest declares `USE_EXACT_ALARM` (granted at install, and honest for this app: it is a reminder
 * timer, not a mail client) and `SCHEDULE_EXACT_ALARM` for API 31-32, where the first does not exist. When the
 * system still refuses — a parent can revoke exact alarms in Settings — the alarm is armed INEXACTLY rather than
 * dropped: late is better than silent, and the sheet says which it got.
 *
 * One alarm per (activity, child) - at most twelve - and `FIXED_TIMES` arms only the next occurrence, so the
 * ceiling holds however many times of day a rule has.
 */
class ReminderScheduler(
    private val context: Context,
    /**
     * A seam for the one thing Robolectric cannot express: whether the system will allow exact alarms.
     *
     * Null in the app, where the real `canScheduleExactAlarms()` answer is used. The tests pass a lambda, because the
     * alternative is leaving the exact/inexact branch untested and finding out on a phone that reminders are late -
     * which is exactly how this was found.
     */
    private val exactAllowed: (() -> Boolean)? = null,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * True when the system will let this app fire exactly.
     *
     * False only when a parent has revoked the exact-alarm permission, because the manifest asks for the
     * auto-granted one.
     */
    fun canBeExact(): Boolean {
        exactAllowed?.let { return it() }
        val manager = alarmManager ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    fun schedule(key: ReminderKey, atMillis: Long) {
        val manager = alarmManager ?: return
        // RTC_WAKEUP, not ELAPSED_REALTIME: the rule is expressed in local clock terms ("3h after the last
        // feed"), and an elapsed-time alarm would drift out of agreement with the log across a reboot.
        if (canBeExact()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(key, atMillis))
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(key, atMillis))
        }
    }

    fun cancel(key: ReminderKey) {
        alarmManager?.cancel(pendingIntent(key, atMillis = 0L)) ?: return
    }

    /**
     * `FLAG_IMMUTABLE` because nothing outside this app may rewrite the intent; `FLAG_UPDATE_CURRENT` so a
     * re-arm replaces the previous target time instead of leaving two alarms for the same reminder.
     *
     * The armed time rides along as `EXTRA_AT` (Phase 30), so the notification can say when this reminder was
     * due and how overdue it is. Cancelling passes 0, and the extra is then simply absent.
     */
    private fun pendingIntent(key: ReminderKey, atMillis: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(key),
        ReminderReceiver.intentFor(context, key.type, key.childId, atMillis.takeIf { it > 0 }),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestCode(key: ReminderKey): Int = key.type.ordinal * 31 + key.childId.hashCode()

    /** Exposed for the tests and for `dumpsys alarm` cross-checks on a phone. */
    companion object {
        fun intentForTest(context: Context, type: EventType, childId: String): Intent =
            ReminderReceiver.intentFor(context, type, childId)
    }
}
