package com.bond.mail

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bond.mail.data.mail.MailLog
import com.bond.mail.data.performance.UiPerformanceGate
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.ui.MailApp
import com.bond.mail.ui.collectAsStateWithLifecycleCompat
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.theme.BondMailTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : FragmentActivity() {
    private var initialMessageId by mutableStateOf<String?>(null)
    private var contentInstalled = false

    @Volatile
    private var firstComposeFrameReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !firstComposeFrameReady }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        initialMessageId = intent.getStringExtra("message_id")
        val container = (application as MailApplication).container

        useSystemManagedRefreshRate()
        UiPerformanceGate.onForeground()

        // Never initialize Chromium before the first inbox frame. MailApp warms one detached
        // WebView only after Compose has drawn and the inbox has remained idle, so cold start stays
        // clean while the first message avoids paying the full Chromium construction cost.
        lifecycleScope.launch {
            withTimeoutOrNull(1_500L) {
                runCatching { container.repository.preloadStartupSnapshot() }
                    .onFailure { error ->
                        MailLog.w(
                            MailLog.PERF,
                            "startup preload failed cause=${MailLog.causeSummary(error)}",
                            error,
                        )
                    }
            }
            installContent(container)
        }

        // Safety valve: a rendering problem must never leave the system splash on screen forever.
        window.decorView.postDelayed({ firstComposeFrameReady = true }, 2_500L)
    }

    override fun onResume() {
        super.onResume()
        useSystemManagedRefreshRate()
        UiPerformanceGate.onForeground()
    }

    private fun installContent(container: AppContainer) {
        if (contentInstalled || isFinishing || isDestroyed) return
        contentInstalled = true
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycleCompat()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
                // SideEffect runs after a successful root composition and before its frame is
                // presented, so removing the splash here cannot reveal an empty DecorView.
                firstComposeFrameReady = true
            }

            BondMailTheme(settings) {
                JsonStringsProvider(settings.languageCode) {
                    MailApp(container, initialMessageId)
                }
            }
        }
    }

    /**
     * Do not force the panel into 120 Hz. On the test phone BondMail needed roughly 11 ms for a
     * typical list frame; forcing an 8.33 ms 120 Hz deadline therefore produced repeated misses and
     * looked less fluid than Thunderbird's stable 90 Hz cadence. Clearing the override lets Android
     * and the OEM compositor select 60/90/120 Hz dynamically for the current workload.
     */
    private fun useSystemManagedRefreshRate() {
        val attributes = window.attributes
        attributes.preferredDisplayModeId = 0
        attributes.preferredRefreshRate = 0f
        window.attributes = attributes

        val targetDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        MailLog.d(
            MailLog.PERF,
            "refresh policy=system current=${targetDisplay?.mode?.refreshRate ?: 0f}Hz supported=" +
                targetDisplay?.supportedModes.orEmpty().joinToString(",") { "${it.refreshRate}" },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialMessageId = intent.getStringExtra("message_id")
    }
}
