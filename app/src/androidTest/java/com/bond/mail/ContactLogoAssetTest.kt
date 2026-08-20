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
        )

        senders.forEach { (name, address) ->
            assertNotNull("Logo did not load for $name", contactLogoSvgMarkup(context, name, address))
        }
    }
}
