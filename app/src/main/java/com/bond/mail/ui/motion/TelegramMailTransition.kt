package com.bond.mail.ui.motion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.PixelCopy
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.coroutines.resume

private const val TELEGRAM_OPEN_DURATION_MS = 260
private const val TELEGRAM_BACK_DURATION_MS = 170
private const val TELEGRAM_CANCEL_DURATION_MS = 150
private const val TELEGRAM_BACK_SCALE_REDUCTION = 0.10f

/*
 * Keep the quick Telegram settle, but distribute more of the travel through the middle of the
 * animation. The previous curve completed most visible movement in under 100 ms on a 120 Hz panel.
 */
private val TelegramOpenEasing = CubicBezierEasing(0.20f, 0.70f, 0.20f, 1f)
private val TelegramBackEasing = CubicBezierEasing(0.20f, 0f, 0f, 1f)

/**
 * Copies the current window asynchronously. A synchronous View.draw has to ask WebView to paint
 * its complete document on the UI thread and produced 350-400 ms return frames on the test phone.
 * PixelCopy keeps that readback in SurfaceFlinger and only resumes Compose when the bitmap is ready.
 */
suspend fun captureMailTransitionSnapshot(view: View): ImageBitmap? {
    if (view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return null
    val activity = view.context.findActivity() ?: return null
    val softwareBitmap = withContext(Dispatchers.Default) {
        runCatching {
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    } ?: return null
    val copySucceeded = suspendCancellableCoroutine { continuation ->
        runCatching {
            PixelCopy.request(
                activity.window,
                softwareBitmap,
                { result ->
                    if (continuation.isActive) {
                        continuation.resume(result == PixelCopy.SUCCESS)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }.onFailure {
            if (continuation.isActive) continuation.resume(false)
        }
    }
    if (!copySucceeded) {
        softwareBitmap.recycle()
        return null
    }
    return withContext(Dispatchers.Default) {
        val hardwareBitmap = runCatching {
            softwareBitmap.copy(Bitmap.Config.HARDWARE, false)
        }.getOrNull()
        if (hardwareBitmap != null) {
            softwareBitmap.recycle()
            hardwareBitmap.asImageBitmap()
        } else {
            softwareBitmap.asImageBitmap()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Telegram-style two-layer mail reader transition with predictive-back progress.
 *
 * The list remains stationary underneath the reader. Predictive back follows the finger and moves
 * one frozen reader bitmap so Chromium does not have to re-rasterize HTML on every gesture frame.
 */
@Composable
fun TelegramMailTransition(
    backgroundSnapshot: ImageBitmap?,
    motionEnabled: Boolean,
    onBackCommitted: () -> Unit,
    content: @Composable (
        requestBack: () -> Unit,
        reportContentReady: () -> Unit,
    ) -> Unit,
) {
    BondBackTransition(
        backgroundSnapshot = backgroundSnapshot,
        motionEnabled = motionEnabled,
        animateOpening = true,
        contentReadyInitially = false,
        freezeContentOnBack = true,
        onBackCommitted = onBackCommitted,
        content = content,
    )
}

/**
 * Predictive-back container for lightweight Compose destinations.
 *
 * It intentionally has no opening animation. The previous destination is only a frozen preview
 * underneath the live current page while a back gesture is active, so revealing a back-stack entry
 * cannot restart an entry animation and produce the old page-switching flash.
 */
@Composable
fun BondBackScreen(
    backgroundSnapshot: ImageBitmap?,
    motionEnabled: Boolean,
    onBackCommitted: () -> Unit,
    content: @Composable (requestBack: () -> Unit) -> Unit,
) {
    BondBackTransition(
        backgroundSnapshot = backgroundSnapshot,
        motionEnabled = motionEnabled,
        animateOpening = false,
        contentReadyInitially = true,
        freezeContentOnBack = false,
        onBackCommitted = onBackCommitted,
    ) { requestBack, _ ->
        content(requestBack)
    }
}

@Composable
private fun BondBackTransition(
    backgroundSnapshot: ImageBitmap?,
    motionEnabled: Boolean,
    animateOpening: Boolean,
    contentReadyInitially: Boolean,
    freezeContentOnBack: Boolean,
    onBackCommitted: () -> Unit,
    content: @Composable (
        requestBack: () -> Unit,
        reportContentReady: () -> Unit,
    ) -> Unit,
) {
    val hostView = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val latestOnBackCommitted by rememberUpdatedState(onBackCommitted)
    val openingProgress = remember {
        Animatable(
            if (animateOpening && motionEnabled && backgroundSnapshot != null) 0f else 1f,
        )
    }
    var contentReady by remember { mutableStateOf(contentReadyInitially) }
    val backProgress = remember { Animatable(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var frozenReader by remember { mutableStateOf<ImageBitmap?>(null) }
    var backIsFinishing by remember { mutableStateOf(false) }
    val maximumCornerPx = with(density) { 28.dp.toPx() }

    LaunchedEffect(animateOpening, motionEnabled, backgroundSnapshot, contentReady) {
        if (!animateOpening || !motionEnabled || backgroundSnapshot == null) {
            openingProgress.snapTo(1f)
            return@LaunchedEffect
        }
        if (!contentReady || openingProgress.value >= 1f) return@LaunchedEffect

        // onPageCommitVisible means Chromium has submitted the document, but Compose still needs
        // one frame to remove its loading layer. Keep the fully prepared reader just off-screen
        // for that frame, then animate only stable pixels over the stationary list snapshot.
        withFrameNanos { }
        openingProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = TELEGRAM_OPEN_DURATION_MS,
                easing = TelegramOpenEasing,
            ),
        )
    }

    suspend fun freezeReader() {
        if (freezeContentOnBack && frozenReader == null) {
            frozenReader = captureMailTransitionSnapshot(hostView)
        }
    }

    suspend fun finishBack() {
        if (backIsFinishing) return
        backIsFinishing = true
        val readerFullyOpened = openingProgress.value >= 0.999f
        if (motionEnabled && backgroundSnapshot != null && readerFullyOpened) {
            freezeReader()
            val remaining = (1f - backProgress.value).coerceIn(0f, 1f)
            backProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (TELEGRAM_BACK_DURATION_MS * remaining)
                        .roundToInt()
                        .coerceAtLeast(1),
                    easing = TelegramBackEasing,
                ),
            )
        }
        // The visual endpoint is already on screen. Commit now instead of retaining the outgoing
        // gesture layer for another frame, which made the first scroll on the revealed page miss.
        latestOnBackCommitted()
    }

    val requestBack: () -> Unit = {
        if (!backIsFinishing) {
            scope.launch {
                // Capture after pressed-state/dialog pixels have returned to their resting frame.
                withFrameNanos { }
                finishBack()
            }
        }
    }
    val reportContentReady: () -> Unit = {
        if (!contentReady) contentReady = true
    }

    PredictiveBackHandler(enabled = true) { events ->
        if (backIsFinishing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        if (!motionEnabled || backgroundSnapshot == null) {
            events.collect { }
            finishBack()
            return@PredictiveBackHandler
        }

        freezeReader()
        try {
            events.collect { event ->
                backEdge = event.swipeEdge
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            finishBack()
        } catch (_: CancellationException) {
            backProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = TELEGRAM_CANCEL_DURATION_MS,
                    easing = TelegramBackEasing,
                ),
            )
            frozenReader = null
            backIsFinishing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        backgroundSnapshot?.let { snapshot ->
            Image(
                bitmap = snapshot,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val frozen = frozenReader
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val interactiveProgress = backProgress.value.coerceIn(0f, 1f)
                    val cardProgress = 1f - (1f - interactiveProgress) *
                        (1f - interactiveProgress) *
                        (1f - interactiveProgress)
                    val direction = if (backEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                    translationX = if (interactiveProgress > 0f || backIsFinishing) {
                        direction * size.width * interactiveProgress
                    } else {
                        size.width * (1f - openingProgress.value)
                    }
                    val backScale = 1f - TELEGRAM_BACK_SCALE_REDUCTION * cardProgress
                    scaleX = backScale
                    scaleY = backScale
                    transformOrigin = TransformOrigin.Center
                    val cornerPx = maximumCornerPx * cardProgress
                    shape = RoundedCornerShape(CornerSize(cornerPx))
                    clip = cornerPx > 0.5f
                    shadowElevation = maximumCornerPx * cardProgress
                },
        ) {
            Box(Modifier.fillMaxSize()) { content(requestBack, reportContentReady) }
            frozen?.let { snapshot ->
                Image(
                    bitmap = snapshot,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
