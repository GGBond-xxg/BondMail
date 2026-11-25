package com.bond.mail.ui.components

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.bond.mail.R

/** Page chrome stays at 1x. Only the sanitized message body receives a CSS zoom. */
internal fun WebView.configureMailZoom(
    layout: MailDocumentLayout,
    textZoomPercent: Int,
    bodyTopCssPx: Int,
    onZoom: () -> Unit,
) {
    // Prepared documents put script-src 'none' before all sender markup. This enables only
    // host evaluateJavascript calls, never sender scripts/handlers, and exposes no JS bridge.
    settings.javaScriptEnabled = true
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = false
    settings.layoutAlgorithm = if (layout == MailDocumentLayout.DESKTOP_SCALED)
        WebSettings.LayoutAlgorithm.NORMAL else WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
    settings.textZoom = textZoomPercent
    val controller = mailBodyZoomController() ?: MailBodyZoomController(this).also {
        setTag(R.id.mail_body_zoom_controller, it)
    }
    controller.bodyTopCssPx = bodyTopCssPx
    controller.onZoom = onZoom
}

internal fun WebView.mailBodyZoomController() = getTag(R.id.mail_body_zoom_controller) as? MailBodyZoomController
internal fun WebView.zoomMailBodyBy(factor: Float) { mailBodyZoomController()?.changeBy(factor) }
internal fun WebView.resetMailZoom() { mailBodyZoomController()?.reset() }

internal class MailBodyZoomController(private val view: WebView) {
    var bodyTopCssPx = 0
    var onZoom: () -> Unit = {}
    var factor by mutableFloatStateOf(1f)
        private set
    private var focusX = -1f
    private var focusY = -1f
    private var scheduled = false
    private var generation = 0
    private val detector = ScaleGestureDetector(view.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean =
                (detector.focusY + view.scrollY) / view.resources.displayMetrics.density >= bodyTopCssPx
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                changeBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        }).apply { isQuickScaleEnabled = false }

    fun onTouch(event: MotionEvent) { detector.onTouchEvent(event) }

    fun changeBy(multiplier: Float, x: Float = -1f, y: Float = -1f) {
        if (!multiplier.isFinite() || multiplier <= 0f) return
        factor = (factor * multiplier).coerceIn(.25f, 5f)
        focusX = x
        focusY = y
        schedule()
    }

    fun reset() {
        factor = 1f
        focusX = -1f
        focusY = -1f
        schedule()
    }

    /** A different document gets its own zoom, including when a pooled WebView is reused. */
    fun documentChanged() { generation++; factor = 1f; scheduled = false }

    private fun schedule() {
        onZoom()
        if (scheduled) return
        scheduled = true
        val expectedGeneration = generation
        view.postOnAnimation {
            if (generation != expectedGeneration) return@postOnAnimation
            scheduled = false
            val density = view.resources.displayMetrics.density
            // Only finite host numbers enter this fixed script. No mail text is evaluated.
            val zoom = factor
            val x = focusX / density
            val y = focusY / density
            view.evaluateJavascript("""
                (function(){
                  const b=document.getElementById('bondmail-message-body');
                  const clip=document.getElementById('bondmail-body-clip');
                  if(!b||!clip)return;
                  const old=Number(b.dataset.readerZoom)||1;
                  const width=Number(b.dataset.readerWidth)||b.getBoundingClientRect().width/old;
                  const before=b.getBoundingClientRect();
                  const fx=$x>=0?$x:clip.clientWidth/2;
                  const fy=before.top>=0?before.top:($y>=0?$y:innerHeight/2);
                  const logicalY=Math.max(0,(fy-before.top)/old);
                  const logicalX=(clip.scrollLeft+fx)/old;
                  b.dataset.readerWidth=String(width);
                  b.dataset.readerZoom=String($zoom);
                  b.style.setProperty('width',width+'px','important');
                  b.style.setProperty('max-width','none','important');
                  b.style.setProperty('zoom',String($zoom),'important');
                  b.style.setProperty('overflow','visible','important');
                  // Keep the paper/card full width when only its contents become smaller.
                  clip.style.setProperty('background-color',getComputedStyle(b).backgroundColor,'important');
                  clip.style.setProperty('overflow-x','auto','important');
                  clip.style.setProperty('overflow-y','hidden','important');
                  clip.style.setProperty('overflow-anchor','none','important');
                  clip.scrollLeft=$zoom===1?0:Math.max(0,logicalX*$zoom-fx);
                  scrollBy(0,b.getBoundingClientRect().top+logicalY*$zoom-fy);
                })();
            """.trimIndent(), null)
        }
    }
}
