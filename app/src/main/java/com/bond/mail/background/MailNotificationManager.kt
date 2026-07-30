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
    private val channelId = "new_mail_alerts_v3"
    private val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                channelId,
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
            val backgroundSyncChannel = NotificationChannel(
                BACKGROUND_SYNC_CHANNEL_ID,
                context.getString(R.string.background_sync_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.background_sync_active)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                if (Build.VERSION.SDK_INT >= 33) setBlockable(true)
            }
            manager.createNotificationChannels(listOf(channel, backgroundSyncChannel))
        }
    }

    fun continuousSyncNotification(intervalMinutes: Int): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            ContinuousMailSyncService.FOREGROUND_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, BACKGROUND_SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.background_sync_active))
            .setContentText(
                context.getString(R.string.background_sync_active_detail, intervalMinutes),
            )
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
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
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.senderName.ifBlank { message.senderAddress })
            .setContentText(message.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.subject))
            .setContentIntent(pending)
            .setAutoCancel(true)
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
            .build()
        notificationManager.notify(message.id.hashCode(), notification)
    }

    companion object {
        const val BACKGROUND_SYNC_CHANNEL_ID = "background_mail_sync_v1"
        private const val NOTIFICATION_LIGHT_COLOR = 0xFF0375FD.toInt()
        private val VIBRATION_PATTERN = longArrayOf(0L, 180L, 90L, 180L)
    }
}
