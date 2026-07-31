package com.bond.mail.ui.components

import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.WeakHashMap

/**
 * Small detached WebView pool for message details.
 *
 * Chromium construction and the first compositor attachment are both expensive enough to be visible
 * while opening a message. Keeping a small bounded history lets recently viewed messages retain
 * their already-rendered local pages. This removes the placeholder when reopening mail without
 * allowing an unbounded number of WebViews.
 */
internal object MailWebViewPool {
    private val cached = mutableListOf<WebView>()
    private var activeCount: Int = 0
    private val retainedContentKeys = WeakHashMap<WebView, String>()

    // Two retained pages cover the common A -> B -> A path without letting several image-heavy
    // newsletters compete for Chromium tile memory in the same renderer.
    private const val MAX_CACHED_WEB_VIEWS = 2

    fun acquire(context: Context, preferredContentKey: String? = null): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "MailWebViewPool.acquire must run on the main thread"
        }

        val preferredIndex = preferredContentKey
            ?.let { expected -> cached.indexOfFirst { retainedContentKeys[it] == expected } }
            ?: -1
        val blankWarmIndex = cached.indexOfFirst { retainedContentKeys[it] == null }
        val selectedIndex = when {
            preferredIndex >= 0 -> preferredIndex
            blankWarmIndex >= 0 -> blankWarmIndex
            // Preserve retained pages until the bounded recent-history pool is full. Afterwards,
            // recycle the least-recently released page.
            cached.size >= MAX_CACHED_WEB_VIEWS -> 0
            else -> -1
        }
        val view = if (selectedIndex >= 0) {
            cached.removeAt(selectedIndex)
        } else {
            createConfiguredWebView(context)
        }

        activeCount += 1
        (view.parent as? ViewGroup)?.removeView(view)
        (view.context as? MutableContextWrapper)?.baseContext = context
        return view
    }

    /**
     * Initializes Chromium and keeps one detached WebView ready for the first message.
     *
     * Calls from a background dispatcher are reposted to the main thread. Warm-up is skipped while
     * another mail WebView is leased so it never creates a renderer only for speculative work.
     */
    fun prewarm(context: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { prewarm(context.applicationContext) }
            return
        }
        if (activeCount > 0 || cached.size >= MAX_CACHED_WEB_VIEWS) return

        // Fill the complete bounded pool behind the launch screen. Previously only the first
        // message reused a warm WebView; opening a second different message synchronously created
        // another Chromium shell on the tap frame and caused a small but perceptible hitch.
        while (cached.size < MAX_CACHED_WEB_VIEWS) {
            cached += createConfiguredWebView(context.applicationContext).apply {
                settings.blockNetworkLoads = true
                settings.blockNetworkImage = true
                settings.loadsImagesAutomatically = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                // Loading a tiny local document starts the Chromium renderer as well as the Java
                // shell. The detail screen replaces it while the native preview remains visible.
                loadDataWithBaseURL(
                    "https://mail.bond.invalid/",
                    "<html><head><meta name=\"viewport\" content=\"width=device-width\"></head>" +
                        "<body style=\"margin:0;background:transparent\"></body></html>",
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        }
    }

    /** True when at least one detached instance can be acquired without constructing Chromium. */
    fun isWarm(): Boolean = cached.isNotEmpty()

    /** Used for diagnostics and to avoid duplicate warm-up work. */
    fun isReadyOrInUse(): Boolean = cached.isNotEmpty() || activeCount > 0

    /** True when any detached pooled WebView already contains this fully committed document. */
    fun canReuseRetainedContent(contentKey: String): Boolean =
        cached.any { view -> retainedContentKeys[view] == contentKey }

    /** Returns the fully committed local document currently retained by this WebView. */
    fun retainedContentKey(view: WebView): String? = retainedContentKeys[view]

    /** Called only after Chromium confirms the new document is visually committed. */
    fun markContentCommitted(view: WebView, contentKey: String) {
        retainedContentKeys[view] = contentKey
    }

    /** A new load invalidates the previously retained page until visual commit succeeds. */
    fun clearRetainedContent(view: WebView) {
        retainedContentKeys.remove(view)
    }

    fun release(view: WebView) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "MailWebViewPool.release must run on the main thread"
        }
        activeCount = (activeCount - 1).coerceAtLeast(0)
        runCatching {
            view.animate().cancel()
            view.alpha = 1f
            view.translationY = 0f
        }
        runCatching { view.clearFocus() }
        runCatching { view.setOnTouchListener(null) }
        runCatching { view.setOnScrollChangeListener(null) }
        runCatching { view.isNestedScrollingEnabled = false }
        (view.parent as? ViewGroup)?.removeView(view)
        val appContext = view.context.applicationContext
        (view.context as? MutableContextWrapper)?.baseContext = appContext

        cached.remove(view)
        cached += view
        while (cached.size > MAX_CACHED_WEB_VIEWS) {
            destroyDetachedView(cached.removeAt(0))
        }
    }

    /** Remove a WebView whose Chromium renderer has died. Dead instances must never be pooled. */
    fun discard(view: WebView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { discard(view) }
            return
        }
        cached.remove(view)
        retainedContentKeys.remove(view)
        activeCount = (activeCount - 1).coerceAtLeast(0)
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.stopLoading() }
        runCatching { view.setOnTouchListener(null) }
        runCatching { view.setOnScrollChangeListener(null) }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
    }

    /** Destroys detached cached views. A currently displayed mail remains owned by UI. */
    fun destroy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { destroy() }
            return
        }
        cached.toList().forEach(::destroyDetachedView)
        cached.clear()
    }

    private fun destroyDetachedView(view: WebView) {
        retainedContentKeys.remove(view)
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.stopLoading() }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
    }

    private fun createConfiguredWebView(context: Context): WebView =
        WebView(MutableContextWrapper(context.applicationContext)).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.defaultTextEncodingName = "utf-8"
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = WebViewClient()
        }
}
