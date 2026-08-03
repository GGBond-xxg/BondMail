package com.bond.mail.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bond.mail.MainActivity
import com.bond.mail.R
import com.bond.mail.data.db.MessageEntity

class MailNotificationManager(private val context: Context) {
    /*
     * Android keeps a channel's importance and sound after app updates. Devices that installed the
     * old silent channel cannot be repaired by changing its Kotlin declaration, so this release uses
     * a new HIGH-importance channel ID.
     */
    private val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                NEW_MAIL_CHANNEL_ID,
                context.getString(R.string.notifications),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notifications)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                enableLights(true)
                lightColor = NOTIFICATION_LIGHT_COLOR
                setShowBadge(true)
                setSound(defaultSound, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
            // v1.2.0 replaced the permanent foreground service with data-only FCM wakeups.
            // Remove the obsolete channel so upgraded devices no longer show a misleading
            // "background sync" notification category.
            manager.cancel(LEGACY_FOREGROUND_NOTIFICATION_ID)
            manager.deleteNotificationChannel(LEGACY_BACKGROUND_SYNC_CHANNEL_ID)
        }
    }

    fun canPostNotifications(): Boolean {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun show(message: MessageEntity) {
        // Kept in one helper so both the runtime permission and the per-app notification switch
        // are checked immediately before notify(). Lint cannot infer that contract across methods.
        if (!canPostNotifications()) return

        val notificationManager = NotificationManagerCompat.from(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("message_id", message.id)
        }
        val pending = PendingIntent.getActivity(
            context,
            message.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NEW_MAIL_CHANNEL_ID)
            .setSmallIcon(R.drawable.bondmail_notification_monet)
            .setContentTitle(message.senderName.ifBlank { message.senderAddress })
            .setContentText(message.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.subject))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setNumber(1)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSound(defaultSound)
            .setVibrate(VIBRATION_PATTERN)
            .setLights(NOTIFICATION_LIGHT_COLOR, 260, 900)
            .setDefaults(
                NotificationCompat.DEFAULT_SOUND or
                    NotificationCompat.DEFAULT_VIBRATE or
                    NotificationCompat.DEFAULT_LIGHTS,
            )
            .setSilent(false)
            .setOnlyAlertOnce(false)
            .setWhen(message.receivedAt)
            .setShowWhen(true)
            .buildPreferringSmallIcon()
        notificationManager.notify(message.id.hashCode(), notification)
    }

    @SuppressLint("MissingPermission")
    fun showCloudMessage(
        title: String,
        body: String,
        notificationKey: String,
    ) {
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val notificationId = notificationKey.hashCode()
        val pending = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NEW_MAIL_CHANNEL_ID)
            .setSmallIcon(R.drawable.bondmail_notification_monet)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setNumber(1)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSound(defaultSound)
            .setVibrate(VIBRATION_PATTERN)
            .setLights(NOTIFICATION_LIGHT_COLOR, 260, 900)
            .setDefaults(
                NotificationCompat.DEFAULT_SOUND or
                    NotificationCompat.DEFAULT_VIBRATE or
                    NotificationCompat.DEFAULT_LIGHTS,
            )
            .setSilent(false)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .buildPreferringSmallIcon()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Opening BondMail acknowledges every pending new-mail alert. Keep the foreground-service
     * notification intact: it belongs to a separate channel and is required for reliable polling.
     * Removing all alert-channel notifications also clears the launcher badge on Android launchers.
     */
    fun clearNewMailNotifications() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { entry -> entry.notification.channelId in NEW_MAIL_CHANNEL_IDS }
            .forEach { entry -> manager.cancel(entry.tag, entry.id) }
    }

    /**
     * Ask platforms that support this public notification hint to render the dedicated
     * transparent small icon instead of substituting the launcher icon.
     */
    private fun NotificationCompat.Builder.buildPreferringSmallIcon(): Notification {
        val notification = build()
        notification.extras.putBoolean(EXTRA_PREFER_SMALL_ICON, true)
        return notification
    }

    companion object {
        const val NEW_MAIL_CHANNEL_ID = "new_mail_alerts_v3"
        private const val LEGACY_BACKGROUND_SYNC_CHANNEL_ID = "background_mail_sync_v1"
        private const val LEGACY_FOREGROUND_NOTIFICATION_ID = 0xB0D
        private val NEW_MAIL_CHANNEL_IDS = setOf(
            "new_mail_alerts_v1",
            "new_mail_alerts_v2",
            NEW_MAIL_CHANNEL_ID,
        )
        private const val EXTRA_PREFER_SMALL_ICON = "android.app.preferSmallIcon"
        private const val NOTIFICATION_LIGHT_COLOR = 0xFF0375FD.toInt()
        private val VIBRATION_PATTERN = longArrayOf(0L, 180L, 90L, 180L)
    }
}
