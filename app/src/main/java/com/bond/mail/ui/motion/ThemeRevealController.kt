package com.bond.mail.ui.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.Window
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import com.bond.mail.data.settings.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.hypot

/**
 * Compose-friendly adaptation of YenalyLiew/CircularRevealSwitch.
 *
 * The upstream implementation recreates the Activity after changing AppCompat's night mode.
 * BondMail's theme is a DataStore-backed Compose state, so keeping the Activity alive preserves
 * navigation, scroll positions and loaded mail while retaining the same screenshot/reveal effect.
 */
class ThemeRevealController(
    private val window: Window,
    private val scope: CoroutineScope,
    private val applyTheme: suspend (ThemeMode) -> Unit,
) {
    private var animationJob: Job? = null
    private var pendingTarget: ThemeMode? = null
    private var themeApplied = CompletableDeferred<Unit>()
    private var overlay: ImageView? = null
    private var overlayBitmap: Bitmap? = null
    private var contentView: View? = null
    private var revealRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun switchTo(target: ThemeMode, originInWindow: Offset, animationsEnabled: Boolean) {
        if (animationJob?.isActive == true || revealRunning) return
        animationJob = scope.launch {
            if (!animationsEnabled || originInWindow == Offset.Unspecified) {
                applyTheme(target)
                return@launch
            }

            val decor = window.decorView as? ViewGroup
            val content = decor?.findViewById<View>(android.R.id.content)
            if (decor == null || content == null || decor.width == 0 || decor.height == 0) {
                applyTheme(target)
                return@launch
            }

            // View.drawToBitmap renders the entire Compose hierarchy synchronously on the main
            // thread and caused a visible pause before the reveal. PixelCopy performs the capture
            // through the window compositor and resumes this coroutine when the frame is ready.
            val screenshot = captureWindow(decor.width, decor.height)
            if (screenshot == null) {
                applyTheme(target)
                return@launch
            }

            val oldThemeLayer = ImageView(decor.context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(screenshot)
                isClickable = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val contentIndex = decor.indexOfChild(content)
            decor.addView(
                oldThemeLayer,
                contentIndex.coerceAtLeast(0),
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            overlay = oldThemeLayer
            overlayBitmap = screenshot
            contentView = content
            content.visibility = View.INVISIBLE
            pendingTarget = target
            themeApplied = CompletableDeferred()
            var revealStarted = false

            try {
                applyTheme(target)
                val composed = withTimeoutOrNull(1_000L) { themeApplied.await() } != null
                if (!composed || !content.isAttachedToWindow) return@launch

                val location = IntArray(2)
                content.getLocationInWindow(location)
                val centerX = (originInWindow.x - location[0]).toInt()
                    .coerceIn(0, content.width.coerceAtLeast(1))
                val centerY = (originInWindow.y - location[1]).toInt()
                    .coerceIn(0, content.height.coerceAtLeast(1))
                val radius = maxOf(
                    hypot(centerX.toDouble(), centerY.toDouble()),
                    hypot((content.width - centerX).toDouble(), centerY.toDouble()),
                    hypot(centerX.toDouble(), (content.height - centerY).toDouble()),
                    hypot(
                        (content.width - centerX).toDouble(),
                        (content.height - centerY).toDouble(),
                    ),
                ).toFloat()

                content.visibility = View.VISIBLE
                revealRunning = true
                ViewAnimationUtils.createCircularReveal(
                    content,
                    centerX,
                    centerY,
                    0f,
                    radius,
                ).apply {
                    duration = BondMotionDuration.MaximumNormal.toLong()
                    // Same Telegram-like curve used by CircularRevealSwitch.
                    interpolator = PathInterpolator(0.455f, 0.03f, 0.515f, 0.955f)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = clearLayers()
                        override fun onAnimationCancel(animation: Animator) = clearLayers()
                    })
                    start()
                    revealStarted = true
                }
            } finally {
                pendingTarget = null
                if (!revealStarted) clearLayers()
            }
        }
    }

    /** Called from the root SideEffect after the requested theme has actually been composed. */
    fun onThemeComposed(themeMode: ThemeMode) {
        if (pendingTarget == themeMode && !themeApplied.isCompleted) {
            themeApplied.complete(Unit)
        }
    }

    fun dispose() {
        animationJob?.cancel()
        clearLayers()
    }

    private suspend fun captureWindow(width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        return suspendCancellableCoroutine { continuation ->
            val bitmap = runCatching {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }.getOrElse {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            runCatching {
                PixelCopy.request(
                    window,
                    bitmap,
                    { result ->
                        if (continuation.isActive) {
                            continuation.resume(
                                if (result == PixelCopy.SUCCESS) bitmap else null,
                            )
                        } else {
                            bitmap.recycle()
                        }
                        if (result != PixelCopy.SUCCESS && !bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    },
                    mainHandler,
                )
            }.onFailure {
                if (!bitmap.isRecycled) bitmap.recycle()
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun clearLayers() {
        contentView?.visibility = View.VISIBLE
        overlay?.let { image ->
            (image.parent as? ViewGroup)?.removeView(image)
            image.setImageDrawable(null)
        }
        overlayBitmap?.takeUnless(Bitmap::isRecycled)?.recycle()
        overlay = null
        overlayBitmap = null
        contentView = null
        revealRunning = false
    }
}

val LocalThemeRevealController = staticCompositionLocalOf<ThemeRevealController?> { null }
