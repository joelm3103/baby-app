package app.babylog.data.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.babylog.MainActivity
import app.babylog.R
import app.babylog.domain.EventType
import app.babylog.domain.ReminderConfig
import app.babylog.domain.TimeFormat
import app.babylog.domain.displayName
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.babylog.ui.theme.DarkColors
import app.babylog.ui.theme.accentFor
import java.time.ZoneId

/**
 * Posts the reminder notification.
 *
 * **One channel, not six.** A channel per activity would put six entries in the system's notification settings
 * for a two-parent app whose activities are already switchable in Settings; what a parent actually wants to
 * silence is one activity, and that is the in-app toggle. The channel is `IMPORTANCE_DEFAULT`, so a reminder
 * makes a sound and does not barge onto the screen the way a call would.
 *
 * **The small icon is a bell, not the activity's glyph.** The glyphs are 1.7dp stroke line-art, which is a
 * faint smudge in a 24dp status bar; the activity is carried by the notification's colour (`accentFor`) and by
 * the title instead.
 */
class ReminderNotifications(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Activity reminders", NotificationManager.IMPORTANCE_DEFAULT)
                .apply {
                    description = "A nudge when an activity has not been logged for a while."
                },
        )
    }

    /** True when the parent has allowed notifications; false is a state Settings explains rather than hides. */
    fun enabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(
        type: EventType,
        childId: String,
        childName: String,
        config: ReminderConfig,
        anchorMs: Long?,
        now: Long,
        zone: ZoneId,
        format: TimeFormat,
        accent: Color,
    ) {
        ensureChannel()
        val title = ReminderText.title(childName, type)
        val text = ReminderText.text(config, type, anchorMs, now, zone, format)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accent.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openSheet(context, type, childId))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(type, childId), notification) }
    }

    /**
     * Tapping a reminder opens that activity's logging sheet for THAT child, not the Activity screen.
     *
     * The intent carries the child id because the app's own child switcher may be pointing at the other twin,
     * and a sheet that logs for the wrong child is worse than no shortcut at all.
     */
    private fun openSheet(context: Context, type: EventType, childId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ReminderReceiver.ACTION_OPEN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderReceiver.EXTRA_TYPE, type.name)
            putExtra(ReminderReceiver.EXTRA_CHILD_ID, childId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId(type, childId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "reminders"

        /**
         * Distinct per (activity, child), so two children due at the same moment are two notifications rather
         * than one silently replacing the other.
         */
        fun notificationId(type: EventType, childId: String): Int =
            type.ordinal * 31 + childId.hashCode()

        /**
         * The activity's own hue for the notification's icon tint.
         *
         * Always the DARK set, whatever theme the app is in: a notification shade is drawn by the system, not
         * by this app, and the bright accents are the ones that survive being tinted onto both a light and a
         * dark shade. This is the same six hues as the cards, so the reminder still identifies the activity.
         */
        fun accentForType(type: EventType): Color = DarkColors.accentFor(type)
    }
}
