package com.bond.mail.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.MainActivity
import com.bond.mail.data.settings.AppSettings
import com.bond.mail.data.settings.MailDisplayMode
import com.bond.mail.data.settings.ThemeMode
import com.bond.mail.ui.i18n.JsonStringsProvider
import com.bond.mail.ui.screens.MailHtmlView
import com.bond.mail.ui.screens.rememberMailHeaderLayout
import com.bond.mail.ui.theme.BondMailTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

class MailDarkModeTest {
    @Test fun actualReaderAppliesNightColorsBeforeRevealAndRestoresDayTheme() {
        val theme = mutableStateOf(ThemeMode.DARK)
        val active = AtomicReference<WebView>()
        val ready = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var installed = false
            val deadline = System.currentTimeMillis() + 20_000
            while (!installed && System.currentTimeMillis() < deadline) {
                scenario.onActivity { installed = MainActivity::class.java.getDeclaredField("contentInstalled")
                    .apply { isAccessible = true }.getBoolean(it) }
                if (!installed) Thread.sleep(50)
            }
            assertTrue(installed)
            scenario.onActivity { activity -> activity.setContent {
                val header = MailWebHeader(subject = "Night readability", senderName = "Test",
                    senderAddress = "test@example.invalid", recipient = "reader@example.invalid", dateLabel = "Today", avatarText = "T")
                BondMailTheme(AppSettings(themeMode = theme.value)) { JsonStringsProvider("en") {
                    MailHtmlView(cacheKey = "night-lifecycle-fixture", html = fixture, header = header,
                        headerLayout = rememberMailHeaderLayout(header.subject), loadImages = false,
                        topContentInset = 64.dp, onTopPullDelta = { 0f }, onTopPullRelease = {},
                        onWebViewChanged = { active.set(it) }, onRemoteImagesChanged = {}, onExternalLink = {},
                        onAttachmentClick = {}, onChromeVisibilityChanged = {}, onContentScrollChanged = {},
                        onRenderStarted = {}, onDocumentReady = { ready.incrementAndGet() },
                        onRenderFailure = { throw AssertionError("Reader failed") },
                        onRendererGone = { throw AssertionError("Renderer lost") }, modifier = Modifier.fillMaxSize())
                } }
            } }
            for ((index, mode) in listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.DARK).withIndex()) {
                instrumentation.runOnMainSync { theme.value = mode }
                val readyBy = System.currentTimeMillis() + 20_000
                while (ready.get() <= index && System.currentTimeMillis() < readyBy) Thread.sleep(50)
                assertTrue("Reader must reveal $mode", ready.get() > index)
                val view = active.get()
                val result = evaluate(view, "return [document.getElementById('bondmail-message-body').dataset.bondmailDarkReady||'',getComputedStyle(document.getElementById('light')).backgroundColor];")
                if (mode == ThemeMode.DARK) {
                    assertEquals("true", result.getString(0))
                    assertEquals(0, evaluate(view, audit).getInt(0))
                } else {
                    assertEquals("", result.getString(0))
                    assertEquals("rgb(255, 255, 255)", result.getString(1))
                }
            }
        }
    }

    /** Explicit opt-in local QA. Read cached HTML only; never fetch, mark read or alter accounts. */
    @Test fun auditCachedMailboxWhenExplicitlyRequested() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("auditCachedMail") == "true")
        val path = instrumentation.targetContext.getDatabasePath("bond_mail.db")
        val samples = mutableListOf<Pair<String, String>>()
        val domains = mutableSetOf<String>()
        android.database.sqlite.SQLiteDatabase.openDatabase(path.path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT senderAddress, bodyHtml FROM messages WHERE bodyLoaded = 1 AND LENGTH(bodyHtml) > 40 ORDER BY receivedAt DESC LIMIT 600", null).use { rows ->
                while (rows.moveToNext() && samples.size < 40) {
                    val sender = rows.getString(0)
                    if (domains.add(sender.substringAfterLast('@'))) samples += sender to rows.getString(1)
                }
            }
        }
        assertTrue("Need locally cached mail", samples.isNotEmpty())
        var textRuns = 0
        val failures = mutableListOf<String>()
        for ((index, sample) in samples.withIndex()) {
            val night = prepare(sample.second, true, sender = sample.first)
            render(night) { view ->
                adapt(view)
                val result = evaluate(view, fullAudit)
                textRuns += result.getInt(1)
                if (result.getJSONArray(0).length() > 0) failures += "sample $index: ${result.getJSONArray(0)}"
                if (sample.first.substringAfterLast('@').endsWith("cathaypacific.com") &&
                    hasCathayPhoneLayout(org.jsoup.Jsoup.parse(sample.second))) {
                    assertEquals(MailDocumentLayout.FLUID, night.layout)
                    assertTrue("Actual Cathay mobile content must fit", evaluate(view,
                        "const b=document.getElementById('bondmail-message-body');return [b.scrollWidth-b.clientWidth];").getDouble(0) <= 1)
                }
            }
            render(prepare(sample.second, false, sender = sample.first)) { view ->
                val original = evaluate(view, "return [document.documentElement.outerHTML];").toString()
                adapt(view)
                assertEquals("Day palette sample $index", original, evaluate(view, "return [document.documentElement.outerHTML];").toString())
            }
        }
        android.util.Log.i("MailDarkAudit", "samples=${samples.size}, textRuns=$textRuns, failures=${failures.size}")
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun resolvedStylesHaveReadableContrastWithoutChangingMediaOrHiddenCopy() = runBlocking {
        for (sender in listOf("test@example.com", "notice@bybit.com", "notice@bochk.com",
            "notice@apple.com", "notice@osl.com", "notice@ifastgb.com", "notice@mail.dito.ph")) {
            render(prepare(fixture, true, sender = sender)) { view ->
                val before = evaluate(view, snapshot)
                adapt(view)
                val result = evaluate(view, audit)
                assertEquals("Unreadable text for $sender: $result", 0, result.getInt(0))
                assertTrue("Main canvas must be dark", result.getDouble(1) < .13)
                assertEquals("Media pixels/geometry and hidden copy must survive", before.getString(0), evaluate(view, snapshot).getString(0))
                val plates = evaluate(view, "return [getComputedStyle(document.getElementById('white-logo')).backgroundColor,getComputedStyle(document.getElementById('black-logo')).backgroundColor,getComputedStyle(document.getElementById('current-color')).fill];")
                assertEquals("rgb(17, 17, 17)", plates.getString(0))
                assertEquals("rgb(255, 255, 255)", plates.getString(1))
                assertEquals("rgb(0, 128, 0)", plates.getString(2))
                assertTrue("Sender span selectors cannot hide the artwork caption", evaluate(view,
                    "return [document.querySelector('#artwork [data-bondmail-dark-text]').getBoundingClientRect().width];").getDouble(0) > 0)
                assertEquals("Script/handler must remain blocked", "undefined", result.getString(2))
                val once = evaluate(view, "return [document.getElementById('bondmail-message-body').outerHTML];")
                adapt(view)
                assertEquals("Second lifecycle callback must be idempotent", once.toString(), evaluate(view, "return [document.getElementById('bondmail-message-body').outerHTML];").toString())
            }
        }
    }

    @Test fun daytimeAndExplicitDisplayModesAreUntouched() = runBlocking {
        for ((night, mode) in listOf(false to MailDisplayMode.AUTO, true to MailDisplayMode.ORIGINAL,
            true to MailDisplayMode.LIGHT, false to MailDisplayMode.LIGHT)) {
            render(prepare(fixture, night, mode)) { view ->
                val before = evaluate(view, "return [document.documentElement.outerHTML];").toString()
                adapt(view)
                assertEquals("$night/$mode", before, evaluate(view, "return [document.documentElement.outerHTML];").toString())
            }
        }
    }

    @Test fun reusedWebViewReturnsToOriginalDayPalette() = runBlocking {
        val day = prepare(fixture, false)
        val night = prepare(fixture, true)
        render(day) { view ->
            val baseline = evaluate(view, "return [getComputedStyle(document.getElementById('plain')).color,getComputedStyle(document.getElementById('light')).backgroundColor];")
            load(view, night); adapt(view)
            assertEquals(0, evaluate(view, audit).getInt(0))
            load(view, day); adapt(view)
            assertEquals(baseline.toString(), evaluate(view, "return [getComputedStyle(document.getElementById('plain')).color,getComputedStyle(document.getElementById('light')).backgroundColor];").toString())
        }
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun render(document: PreparedMailDocument, block: (WebView) -> Unit) {
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                settings.blockNetworkLoads = true
                settings.useWideViewPort = true
                layout(0, 0, 1080, 2400)
            }
        }
        try { load(view, document); block(view) }
        finally { instrumentation.runOnMainSync { view.destroy() } }
    }
    private fun load(view: WebView, document: PreparedMailDocument) {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) { done.countDown() }
            }
            view.loadDataWithBaseURL("https://example.invalid", document.html, "text/html", "UTF-8", null)
        }
        assertTrue(done.await(20, TimeUnit.SECONDS))
    }
    private fun adapt(view: WebView) {
        val done = CountDownLatch(1)
        instrumentation.runOnMainSync { view.applyMailDarkMode { done.countDown() } }
        assertTrue(done.await(10, TimeUnit.SECONDS))
    }
    private fun evaluate(view: WebView, script: String): JSONArray {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        instrumentation.runOnMainSync {
            view.evaluateJavascript("(function(){$script})()") { result.set(it); done.countDown() }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return JSONArray(result.get())
    }
    private suspend fun prepare(html: String, night: Boolean, mode: MailDisplayMode = MailDisplayMode.AUTO,
        sender: String = "test@example.com") = MailWebViewCache.preparedDocument(
        key = "dark-test-${System.nanoTime()}", html = html,
        header = MailWebHeader(displayMode = mode, subject = "Contrast fixture", senderName = "Test", senderAddress = sender,
            recipient = "reader@example.invalid", dateLabel = "Today", avatarText = "T"),
        foregroundCss = if (night) "#eeeeee" else "#202124", backgroundCss = if (night) "#000000" else "#f7f7f7",
        linkCss = "#0b57d0", mutedCss = "#666666", headerSurfaceCss = if (night) "#1c1c1e" else "#ffffff",
        avatarBackgroundCss = "#dde7ff", avatarForegroundCss = "#123456", darkMode = night,
        topContentInsetCssPx = 64, subjectBlockHeightCssPx = 84, subjectFontSizeSp = 24f,
        subjectLineHeightSp = 30f, senderBlockHeightCssPx = 112, viewportWidthCssPx = 360, fontScale = 1f,
    )

    private val snapshot = """
        const ids=['qr','vector','hidden','transparent','zero','preview'];
        return [JSON.stringify(ids.map(id=>{const e=document.getElementById(id),s=getComputedStyle(e);
          return [id,e.textContent,e.getAttribute('src'),e.getAttribute('fill'),e.getBoundingClientRect().width,
            s.display,s.visibility,s.opacity,id==='transparent'?s.webkitTextFillColor:''];}))];
    """.trimIndent()

    private val audit = """
        const rgb=s=>{const n=s.match(/[\d.]+/g).map(Number);return n.length===3?n.concat(1):n};
        const over=(a,b)=>a.slice(0,3).map((v,i)=>v*a[3]+b[i]*(1-a[3])).concat(1);
        const lum=c=>{const n=c.slice(0,3).map(v=>{v/=255;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4)});
          return n[0]*.2126+n[1]*.7152+n[2]*.0722};
        const background=e=>{const c=rgb(getComputedStyle(e).backgroundColor);return c[3]>=1?c:over(c,e.parentElement?background(e.parentElement):[0,0,0,1]);};
        let failures=0;
        document.querySelectorAll('[data-check]').forEach(el=>{
          const e=el.querySelector('[data-bondmail-dark-text]')||el, b=background(e);
          const fg=over(rgb(getComputedStyle(e).webkitTextFillColor),b), x=lum(fg),y=lum(b);
          if((Math.max(x,y)+.05)/(Math.min(x,y)+.05)<4.5) failures++;
        });
        return [failures,lum(background(document.getElementById('bondmail-message-body'))),typeof window.senderRan];
    """.trimIndent()

    private val fixture = """
        <html><head><style>
          .white{background:white!important;color:#111!important}.muted{color:#aaa!important}
          #artwork span{display:none!important}
          @media(prefers-color-scheme:dark){.white{color:white!important}}
        </style></head><body bgcolor='#ffffff'>
        <script>window.senderRan=true</script>
        <div id='light' class='white'><p id='plain' data-check>普通正文 Plain text</p>
          <p data-check class='muted'>浅灰页脚 Muted footer</p><a href='https://example.invalid'><span data-check style='color:navy'>Link</span></a>
          <font data-check color='white'>White on white</font>
          <p data-check style='color:#fff;-webkit-text-fill-color:#010101'>Text fill</p></div>
        <table bgcolor='#ffcc00'><tr><td data-check style='color:#ffff00'>Colored table</td></tr></table>
        <div style='background:#111;color:white'><span data-check>Native dark panel</span></div>
        <div style='background:rgba(255,255,255,.7)'><span data-check style='color:rgba(0,0,0,.2)'>Alpha colors</span></div>
        <a data-check style='background:#7132f5;color:white' href='https://example.invalid'>Call to action</a>
        <div style='background-image:linear-gradient(white,black)'><p id='artwork' data-check style='color:white'>Text over artwork</p></div>
        <div style='opacity:.1'><span data-check>Faint copy</span></div>
        <p id='hidden' style='display:none;color:white'>Preheader</p>
        <p id='transparent' style='color:transparent'>Transparent preheader</p>
        <p id='zero' style='opacity:0'>Invisible preheader</p>
        <p id='preview' style='max-height:0;overflow:hidden'>Clipped preview</p>
        <img id='qr' width='96' height='96' src='cid:test-qr' onerror='window.senderRan=true'>
        <svg width='30' height='30'><path id='vector' fill='#111111' d='M0 0h30v30H0z'/></svg>
        <div style='background:#111'><img id='white-logo' src='cid:white-logo' width='30' height='30'></div>
        <div style='background:white'><img id='black-logo' src='cid:black-logo' width='30' height='30'></div>
        <div style='color:green'><svg width='30' height='30'><path id='current-color' fill='currentColor' d='M0 0h30v30H0z'/></svg></div>
        </body></html>
    """.trimIndent()

    private val fullAudit = """
        const rgb=s=>{const n=s.match(/[\d.]+/g).map(Number);return n.length===3?n.concat(1):n};
        const over=(a,b)=>a.slice(0,3).map((v,i)=>v*a[3]+b[i]*(1-a[3])).concat(1);
        const lum=c=>{const n=c.slice(0,3).map(v=>{v/=255;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4)});
          return n[0]*.2126+n[1]*.7152+n[2]*.0722};
        const bg=e=>{const c=rgb(getComputedStyle(e).backgroundColor);return c[3]>=1?c:over(c,e.parentElement?bg(e.parentElement):[0,0,0,1]);};
        const failures=[];let count=0;
        document.querySelectorAll('#bondmail-message-body *').forEach(e=>{
          if(e.closest('svg,style,script')||![...e.childNodes].some(n=>n.nodeType===3&&n.textContent.trim()))return;
          const s=getComputedStyle(e),r=e.getBoundingClientRect();
          if(!r.width||!r.height||parseFloat(s.fontSize)<1||parseFloat(s.textIndent)<-100)return;
          for(let p=e;p;p=p.parentElement){const t=getComputedStyle(p);if(t.display==='none'||t.visibility!=='visible'||
            Number(t.opacity)===0||(t.maxHeight==='0px'&&t.overflowY==='hidden'))return;}
          const c=rgb(s.webkitTextFillColor);if(c[3]===0)return;
          const b=bg(e),x=lum(over(c,b)),y=lum(b),ratio=(Math.max(x,y)+.05)/(Math.min(x,y)+.05);
          count++;
          if(ratio<4.5)failures.push([e.tagName,ratio.toFixed(2),s.webkitTextFillColor,s.backgroundColor]);
        });
        return [failures,count];
    """.trimIndent()
}
