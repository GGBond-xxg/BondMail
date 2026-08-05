package com.bond.mail

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bond.mail.data.mail.MailLog
import com.bond.mail.data.performance.UiPerformanceGate
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.ui.MailApp
import com.bond.mail.ui.collectAsStateWithLifecycleCompat
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.motion.LocalThemeRevealController
import com.bond.mail.ui.motion.ThemeRevealController
import com.bond.mail.ui.theme.BondMailTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : FragmentActivity() {
    private var initialMessageId by mutableStateOf<String?>(null)
    private var externalComposeRequest by mutableStateOf<ExternalComposeRequest?>(null)
    private var externalComposeRequestSequence = 0L
    private var contentInstalled = false
    private var themeRevealController: ThemeRevealController? = null

    @Volatile
    private var firstComposeFrameReady = false

    @Volatile
    private var systemBarsDarkTheme: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val container = (application as MailApplication).container
        val startupThemeHint = container.settings.startupThemeHint()
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !firstComposeFrameReady }
        splash.setOnExitAnimationListener { splashView ->
            // A successful Compose SideEffect means the hierarchy exists, but its buffer may not
            // have reached SurfaceFlinger yet. This is observable during a recorded cold start as
            // one frame containing only windowBackground. Keep the splash overlay for one more
            // display frame so the first app buffer is already underneath before removing it.
            window.decorView.postOnAnimation {
                splashView.remove()
                // Some OEM splash implementations restore the starting theme's system-bar flags
                // while removing their overlay. Re-apply the setting after that hand-off.
                applyLastSystemBarAppearance()
                window.decorView.postOnAnimation(::applyLastSystemBarAppearance)
            }
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        startupThemeHint?.let { applySystemBarAppearance(resolveDarkTheme(it)) }
        acceptIntent(intent)

        useSystemManagedRefreshRate()
        UiPerformanceGate.onForeground()

        // The application has already prepared the detached WebView behind the launch splash.
        // Keep database preload here so the first visible inbox frame still contains local mail.
        lifecycleScope.launch {
            val initialSettings = async {
                runCatching { container.settings.settings.first() }
                    .getOrDefault(AppSettings())
            }
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
            val settings = initialSettings.await()
            container.settings.rememberStartupTheme(settings.themeMode)
            applySystemBarAppearance(resolveDarkTheme(settings))
            installContent(container, settings)
        }

        // Safety valve: a rendering problem must never leave the system splash on screen forever.
        window.decorView.postDelayed({ firstComposeFrameReady = true }, 2_500L)
    }

    override fun onResume() {
        super.onResume()
        useSystemManagedRefreshRate()
        UiPerformanceGate.onForeground()
        applyLastSystemBarAppearance()
    }

    override fun onStart() {
        super.onStart()
        val container = (application as MailApplication).container
        // Mark the app visible before Compose/database warm-up finishes, preventing a background
        // worker from posting an alert underneath the launch splash.
        container.setAppForeground(true)
        container.notifications.clearNewMailNotifications()
    }

    override fun onStop() {
        (application as MailApplication).container.setAppForeground(false)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyLastSystemBarAppearance()
    }

    private fun installContent(container: AppContainer, initialSettings: AppSettings) {
        if (contentInstalled || isFinishing || isDestroyed) return
        contentInstalled = true
        val revealController = ThemeRevealController(
            window = window,
            scope = lifecycleScope,
            applyTheme = container.settings::setTheme,
        )
        themeRevealController = revealController
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycleCompat(initialSettings)
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SideEffect {
                applySystemBarAppearance(darkTheme)
                revealController.onThemeComposed(settings.themeMode)
            }

            BondMailTheme(settings) {
                CompositionLocalProvider(LocalThemeRevealController provides revealController) {
                    JsonStringsProvider(settings.languageCode) {
                        MailApp(
                            container = container,
                            initialMessageId = initialMessageId,
                            externalComposeRequest = externalComposeRequest,
                            onExternalComposeRequestConsumed = { requestId ->
                                if (externalComposeRequest?.requestId == requestId) {
                                    externalComposeRequest = null
                                }
                            },
                            onFirstContentReady = { firstComposeFrameReady = true },
                        )
                    }
                }
            }
        }
    }

    private fun resolveDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    private fun resolveDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> {
            isSystemDarkTheme()
        }
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    private fun isSystemDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    private fun applySystemBarAppearance(darkTheme: Boolean) {
        systemBarsDarkTheme = darkTheme
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    private fun applyLastSystemBarAppearance() {
        systemBarsDarkTheme?.let(::applySystemBarAppearance)
    }

    override fun onDestroy() {
        themeRevealController?.dispose()
        themeRevealController = null
        super.onDestroy()
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
        acceptIntent(intent)
        (application as MailApplication).container.notifications.clearNewMailNotifications()
    }

    private fun acceptIntent(intent: Intent) {
        initialMessageId = intent.getStringExtra("message_id")
        ExternalMailIntentParser.parse(
            intent = intent,
            requestId = ++externalComposeRequestSequence,
        )?.let { request ->
            // Preserve temporary URI grants while the composer and its send worker use attachments.
            if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                request.attachmentUris.forEach { rawUri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            android.net.Uri.parse(rawUri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
            externalComposeRequest = request
        }
    }
}
