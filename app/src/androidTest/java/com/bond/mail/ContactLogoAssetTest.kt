package com.bond.mail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bond.mail.ui.components.contactLogoSvgMarkup
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactLogoAssetTest {
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
            "中国联通" to "service@10010.com",
            "中际旭创" to "ir@zj-innolight.com",
            "中国人寿" to "service@e-chinalife.com",
            "美的集团" to "news@midea.com",
            "网易邮箱账号安全" to "safe@service.netease.com",
        )

        senders.forEach { (name, address) ->
            assertNotNull("Logo did not load for $name", contactLogoSvgMarkup(context, name, address))
        }
    }
}
