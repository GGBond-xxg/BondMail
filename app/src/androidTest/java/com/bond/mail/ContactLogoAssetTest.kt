package com.bond.mail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.ui.components.contactLogoSvgMarkup
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactLogoAssetTest {
    @Test fun curatedMarksDoNotResolveToOpaqueBackgrounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val examples = listOf("MEXC" to "notice@notification.mexc.link", "iFAST Global Bank" to "notice@ifastgb.com", "Cloudflare" to "notice@cloudflare.com")
        val bitmap = android.graphics.Bitmap.createBitmap(900, 320, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(20, 20, 20))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        examples.forEachIndexed { index, (name, address) ->
            val markup = requireNotNull(contactLogoSvgMarkup(context, name, address))
            assertTrue("$name contains a background circle", !markup.contains("<circle"))
            val doc = org.jsoup.Jsoup.parse(markup, "", org.jsoup.parser.Parser.xmlParser())
            val bounds = doc.selectFirst("svg")!!.attr("viewBox").split(' ').map { it.toFloat() }
            assertTrue("$name unexpectedly fills its square canvas: $bounds", bounds[3] < 500f)
            val cx = index * 300f + 150f
            paint.color = android.graphics.Color.rgb(40, 57, 84)
            canvas.drawCircle(cx, 138f, 108f, paint)
            val scale = 140f / maxOf(bounds[2], bounds[3])
            canvas.save()
            canvas.translate(cx - bounds[2] * scale / 2, 138f - bounds[3] * scale / 2)
            canvas.scale(scale, scale)
            canvas.translate(-bounds[0], -bounds[1])
            paint.color = android.graphics.Color.rgb(45, 130, 250)
            doc.select("path").forEach { path ->
                val native = androidx.core.graphics.PathParser.createPathFromPathData(path.attr("d"))!!
                if (path.attr("fill-rule") == "evenodd") native.fillType = android.graphics.Path.FillType.EVEN_ODD
                canvas.drawPath(native, paint)
            }
            canvas.restore()
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 23f
            paint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText(name, cx, 286f, paint)
        }
        java.io.File(context.getExternalFilesDir(null), "icon-regression-preview.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test
    fun addedBrandLogosLoadFromBundledAssets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val senders = listOf(
            "去哪儿旅行" to "offers@qunar.com",
            "同程旅行" to "notice@ly.com",
            "飞常准" to "notice@variflight.com",
            "Air China" to "notice@airchina.com",
            "飞猪" to "noreply@ur.alitrip.com",
            "Hostelworld" to "notice@hostelworld.com",
            "Airbnb" to "notice@airbnb.com",
            "Hotels.com" to "notice@hotels.com",
            "Expedia" to "notice@expedia.com",
            "Booking.com" to "notice@booking.com",
            "Trainline" to "notice@trainline.com",
            "Rome2rio" to "notice@rome2rio.com",
            "Omio" to "notice@omio.com",
            "Citymapper" to "notice@citymapper.com",
            "Bolt" to "notice@bolt.eu",
            "Cabify" to "notice@cabify.com",
            "滴滴出行" to "notice@didiglobal.com",
            "Lyft" to "notice@lyft.com",
            "Uber" to "notice@uber.com",
            "中国邮政" to "notice@chinapost.com.cn",
            "顺丰速运" to "notice@sf-express.com",
            "支付宝" to "notice@alipay.com",
            "Ant Bank（HK）" to "bankupdate@notify.antbank.hk",
            "pixiv" to "info@pixiv.net",
            "Plasma One" to "updates@plasma.org",
            "SafePal" to "marketing@safepal.com",
            "钱迹" to "notice@qianjiapp.com",
            "Moovit" to "notice@moovit.com",
            "Apple" to "no_reply@apple.com",
            "Amazon" to "shipment-tracking@amazon.com",
            "Amazon Web Services" to "no-reply@aws.amazon.com",
            "Kraken" to "no-reply@email.kraken.com",
            "Grab" to "offers@grab.com",
            "前程无忧" to "jobs@51job.com",
            "WhatsApp" to "security@whatsapp.com",
            "Discord" to "noreply@discord.com",
            "CoinMarketCap" to "newsletter@coinmarketcap.com",
            "东方财富" to "notice@eastmoney.com",
            "拼多多" to "notice@pinduoduo.com",
            "比亚迪" to "news@byd.com",
            "中国移动" to "service@10086.cn",
            "中国电信" to "service@189.cn",
            "10010" to "10010@wo.cn",
            "中际旭创" to "ir@zj-innolight.com",
            "中国人寿" to "service@e-chinalife.com",
            "美的集团" to "news@midea.com",
            "网易邮箱账号安全" to "safe@service.netease.com",
            "Instagram" to "security@mail.instagram.com",
            "Telegram" to "abuse@telegram.org",
            "Facebook" to "security@facebookmail.com",
            "QuickQ" to "cs@js7.io",
            "giffgaff" to "no_reply@giffgaff.com",
            "Vodafone" to "notice@vodafone.co.uk",
            "Singapore Airlines" to "notice@singaporeair.com",
            "SoSIM" to "service@sosimhk.com",
            "HTX" to "htxsupport@htx-inc.com",
            "OKX" to "noreply@okx.com",
            "McDonald's" to "press@us.mcd.com",
            "Charles Schwab" to "alerts@schwab.com",
            "Firstrade" to "service@email-mc.firstrade.com",
            "Grok" to "support@x.ai",
            "Holafly" to "help@holafly.com",
            "华泰证券" to "95597@htsc.com",
            "RedteaGO" to "service@redteago.com",
            "酷安" to "notice@coolapk.com",
            "EastWest Bank" to "service@eastwestbanker.com",
            "Logitech" to "support@logitech.com",
            "Example Community Bank" to "alerts@example.bank",
            "Hang Seng Bank" to "notice@example.org",
            "Gitee" to "notice@gitee.com",
            "GitLab" to "noreply@gitlab.com",
            "GMX" to "service@gmx.com",
            "Google" to "no-reply@google.com",
            "Alibaba" to "notice@alibaba.com",
            "Alibaba Cloud" to "notice@alibabacloud.com",
            "Ant Group" to "notice@antgroup.com",
            "AOL" to "service@aol.com",
            "Arc Browser" to "team@arc.net",
            "Avalanche" to "updates@avalabs.org",
            "百度" to "notice@baidu.com",
            "Bento" to "hello@bento.me",
            "Brave Browser" to "notice@brave.com",
            "Burton" to "news@burton.com",
            "Claude" to "notice@anthropic.com",
            "Cloudflare" to "updates@cloudflare.com",
            "CMake" to "news@cmake.org",
            "CNES" to "press@cnes.fr",
            "CNET" to "newsletter@cnet.com",
            "CNN" to "newsletter@cnn.com",
            "Codex" to "notice@openai.com",
            "Continente" to "news@continente.pt",
            "大众点评" to "notice@dianping.com",
            "DeepAI" to "hello@deepai.org",
            "DeepSeek" to "service@deepseek.com",
            "Docker" to "notice@docker.com",
            "Dolby" to "news@dolby.com",
            "豆瓣" to "notice@douban.com",
            "Drupal" to "notice@drupal.org",
            "Duolingo" to "notice@duolingo.com",
            "Gemini" to "google-gemini-noreply@google.com",
            "LinkedIn" to "messages@linkedin.com",
            "Messenger" to "notification@facebookmail.com",
            "MEXC" to "dontreply@notification.mexc.link",
            "Microsoft Copilot" to "copilot@email.microsoft.com",
            "Patreon" to "notice@patreon.com",
            "VK" to "notice@vk.com",
            "Xiaomi MiMo" to "support-mimo@xiaomi.com",
            "YouTube" to "no-reply@youtube.com",
            "GameBanana" to "notice@gamebanana.com",
            "Git" to "notice@git-scm.com",
            "KuCoin" to "news@kucoin.com",
            "HSBC" to "notice@hsbc.com",
            "Shopee" to "notice@shopee.com",
            "Shopify" to "notice@shopify.com",
            "Zoom" to "notice@zoom.us",
        )

        val missingLogos = senders.mapNotNull { (name, address) ->
            name.takeIf { contactLogoSvgMarkup(context, name, address) == null }
        }
        assertTrue(
            "Logos did not load for: ${missingLogos.joinToString()}",
            missingLogos.isEmpty(),
        )

        val exchangeMarkup = contactLogoSvgMarkup(context, "KuCoin", "news@kucoin.com")
        assertTrue("Generic exchange arrows were not parsed", exchangeMarkup.orEmpty().contains("<polyline"))
    }
}
