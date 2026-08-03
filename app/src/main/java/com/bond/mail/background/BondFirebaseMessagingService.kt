package com.bond.mail.background

import com.bond.mail.R
import com.bond.mail.data.mail.MailLog
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles foreground and data-only FCM messages.
 *
 * Notification payloads received while the app is backgrounded are displayed by the Firebase SDK
 * on the same BondMail alert channel configured in AndroidManifest.xml.
 */
@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
class BondFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmRegistrationStore.save(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["action"] == ACTION_SYNC) {
            MailLog.d(
                MailLog.APP,
                "FCM scheduled sync received id=${message.messageId.orEmpty()}",
            )
            WorkScheduler(applicationContext).syncFromPush()
            return
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: return
        val key = message.messageId
            ?: message.data["notification_id"]
            ?: "fcm:${System.currentTimeMillis()}:$title:$body"

        MailLog.d(
            MailLog.APP,
            "FCM message received id=${message.messageId.orEmpty()} dataKeys=${message.data.keys.sorted()}",
        )
        MailNotificationManager(this).showCloudMessage(
            title = title,
            body = body,
            notificationKey = key,
        )
    }

    private companion object {
        const val ACTION_SYNC = "sync"
    }
}
