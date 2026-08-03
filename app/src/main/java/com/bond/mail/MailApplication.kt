package com.bond.mail

import android.app.Application
import android.content.ComponentCallbacks2
import com.bond.mail.background.FcmRegistrationStore
import com.bond.mail.data.mail.MailLog
import com.bond.mail.ui.components.MailWebViewPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class MailApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        FcmRegistrationStore.register(this)
        // Chromium construction is synchronous. Pay that cost while the system splash is still
        // covering startup, never on the user's first message tap. A missing or updating system
        // WebView must not make the whole mail app unable to launch.
        runCatching { MailWebViewPool.prewarm(this) }
            .onFailure { error ->
                MailLog.w(
                    MailLog.WEB,
                    "webview prewarm skipped cause=${MailLog.causeSummary(error)}",
                    error,
                )
            }

        // WorkManager's periodic registration is not needed for the first frame. Queue it off the
        // main thread so a cold launch is spent on local mail data and Compose only.
        applicationScope.launch {
            val settings = container.settings.settings.first()
            container.scheduler.scheduleBackgroundSync(
                enabled = true,
                intervalMinutes = settings.syncMinutes,
            )
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            MailWebViewPool.destroy()
        }
    }
}
