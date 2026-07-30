package com.bond.mail.background

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bond.mail.MailApplication
import com.bond.mail.data.mail.MailLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps user-requested sub-15-minute IMAP polling alive after the activity leaves the screen.
 *
 * There is deliberately a visible, silent notification while this service runs. Without that
 * foreground-service contract Android 12+ is free to suspend one-minute background work, which is
 * why the old WorkManager chain only caught mail after the app returned to the foreground.
 */
class ContinuousMailSyncService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val container
        get() = (application as MailApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intervalMinutes = intent
            ?.getIntExtra(EXTRA_INTERVAL_MINUTES, -1)
            ?.takeIf(WorkScheduler::usesContinuousService)
            ?: container.scheduler.continuousIntervalMinutes()

        if (intervalMinutes == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        promoteToForeground(intervalMinutes)
        holdCpuWhilePolling()
        startPolling(intervalMinutes)
        MailLog.d(MailLog.APP, "continuous sync service active interval=${intervalMinutes}m")
        return START_STICKY
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        serviceScope.cancel()
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun promoteToForeground(intervalMinutes: Int) {
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            container.notifications.continuousSyncNotification(intervalMinutes),
            serviceType,
        )
    }

    @SuppressLint("WakelockTimeout")
    private fun holdCpuWhilePolling() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:continuous-mail-sync",
            )
            .apply {
                setReferenceCounted(false)
                // This is intentionally tied to the visible foreground service rather than a
                // timeout. ColorOS otherwise suspends coroutine timers as soon as the activity
                // leaves the screen, even though Android still reports the process as FGS.
                acquire()
            }
    }

    private fun startPolling(intervalMinutes: Int) {
        if (pollingJob?.isActive == true && activeIntervalMinutes == intervalMinutes) return
        activeIntervalMinutes = intervalMinutes
        pollingJob?.cancel()
        MailLog.d(MailLog.APP, "continuous sync loop scheduling interval=${intervalMinutes}m")
        pollingJob = serviceScope.launch {
            MailLog.d(MailLog.APP, "continuous sync loop started interval=${intervalMinutes}m")
            while (true) {
                delay(intervalMinutes * 60_000L)
                if (container.isAppForeground()) {
                    MailLog.d(MailLog.APP, "continuous sync tick skipped while app is visible")
                    continue
                }
                MailLog.d(MailLog.APP, "continuous sync tick interval=${intervalMinutes}m")
                runCatching {
                    container.syncAllAndNotify(container.backgroundNotificationMode())
                    MailLog.d(MailLog.APP, "continuous sync tick complete")
                }.onFailure { error ->
                    MailLog.e(
                        MailLog.APP,
                        "continuous sync failed cause=${MailLog.causeSummary(error)}",
                        error,
                    )
                }
            }
        }
    }

    companion object {
        private const val EXTRA_INTERVAL_MINUTES = "interval_minutes"
        const val FOREGROUND_NOTIFICATION_ID = 0xB0D

        @Volatile
        private var activeIntervalMinutes: Int? = null

        fun reconcile(context: Context, enabled: Boolean, intervalMinutes: Int) {
            if (enabled && WorkScheduler.usesContinuousService(intervalMinutes)) {
                start(context, intervalMinutes)
            } else {
                stop(context)
            }
        }

        fun start(context: Context, intervalMinutes: Int) {
            if (!WorkScheduler.usesContinuousService(intervalMinutes)) return
            val intent = Intent(context, ContinuousMailSyncService::class.java)
                .putExtra(EXTRA_INTERVAL_MINUTES, intervalMinutes)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { error ->
                    MailLog.e(
                        MailLog.APP,
                        "continuous sync service start failed cause=${MailLog.causeSummary(error)}",
                        error,
                    )
                }
        }

        fun stop(context: Context) {
            activeIntervalMinutes = null
            val serviceIntent = Intent(context, ContinuousMailSyncService::class.java)
            context.stopService(serviceIntent)
        }
    }
}
