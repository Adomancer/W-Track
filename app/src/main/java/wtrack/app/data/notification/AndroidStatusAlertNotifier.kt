package wtrack.app.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import wtrack.app.MainActivity
import wtrack.app.R
import wtrack.app.data.local.preferences.AppSettingsDataStore
import wtrack.app.domain.repository.StatusAlertNotifier

/**
 * Local-only, opt-in status alerts that never include amounts, merchant names, or notification text.
 */
class AndroidStatusAlertNotifier(
    private val context: Context,
    private val settingsStore: AppSettingsDataStore,
) : StatusAlertNotifier {

    override suspend fun notifyNeedsReview(sourceId: String, pendingCount: Int) {
        if (!canPostLocalAlerts()) return
        val count = pendingCount.coerceAtLeast(1)
        postNotification(
            notificationId = NOTIF_ID_NEEDS_REVIEW,
            title = context.getString(R.string.notif_needs_review_title),
            body = context.resources.getQuantityString(R.plurals.notif_needs_review_body, count, count),
        )
    }

    override suspend fun notifyCaptureDisconnected() {
        if (!canPostLocalAlerts()) return
        postNotification(
            notificationId = NOTIF_ID_CAPTURE_DISCONNECTED,
            title = context.getString(R.string.notif_capture_paused_title),
            body = context.getString(R.string.notif_capture_paused_body),
        )
    }

    override suspend fun cancelCaptureDisconnected() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIF_ID_CAPTURE_DISCONNECTED)
        }
    }

    override suspend fun notifyBackupReminderDue() {
        if (!canPostLocalAlerts()) return
        postNotification(
            notificationId = NOTIF_ID_BACKUP_REMINDER,
            title = context.getString(R.string.notif_backup_reminder_title),
            body = context.getString(R.string.notif_backup_reminder_body),
        )
    }

    private suspend fun canPostLocalAlerts(): Boolean {
        val settings = runCatching { settingsStore.current() }.getOrNull() ?: return false
        if (!settings.localAlertsEnabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun postNotification(notificationId: Int, title: String, body: String) {
        ensureChannel()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wtrack_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Ignored if runtime permission is revoked between check and post.
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notif_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "wtrack_status_alerts"
        const val NOTIF_ID_NEEDS_REVIEW = 4101
        const val NOTIF_ID_CAPTURE_DISCONNECTED = 4102
        const val NOTIF_ID_BACKUP_REMINDER = 4103
    }
}
