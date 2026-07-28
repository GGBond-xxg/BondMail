package com.bond.mail.background

import android.Manifest
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
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun show(message: MessageEntity) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) return

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
        private const val NOTIFICATION_LIGHT_COLOR = 0xFF0375FD.toInt()
        private val VIBRATION_PATTERN = longArrayOf(0L, 180L, 90L, 180L)
    }
}
