package com.bond.mail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandMatcherTest {
    @Test
    fun travelMobilityDeliveryAndPaymentBrandsMatch() {
        val cases = listOf(
            Triple("去哪儿旅行", "offers@qunar.com", "qunar"),
            Triple("同程旅行", "notice@ly.com", "tongcheng"),
            Triple("飞常准", "notice@variflight.com", "variflight"),
            Triple("Air China", "notice@airchina.com", "airchina"),
            Triple("飞猪", "noreply@ur.alitrip.com", "fliggy"),
            Triple("Hostelworld", "notice@hostelworld.com", "hostelworld"),
            Triple("Airbnb", "notice@airbnb.com", "airbnb"),
            Triple("Hotels.com", "notice@hotels.com", "hotels.com"),
            Triple("Expedia", "notice@expedia.com", "expedia"),
            Triple("Booking.com", "notice@booking.com", "booking"),
            Triple("Trainline", "notice@trainline.com", "trainline"),
            Triple("Rome2rio", "notice@rome2rio.com", "rome2rio"),
            Triple("Omio", "notice@omio.com", "omio"),
            Triple("Citymapper", "notice@citymapper.com", "citymapper"),
            Triple("Bolt", "notice@bolt.eu", "bolt"),
            Triple("Cabify", "notice@cabify.com", "cabify"),
            Triple("滴滴出行", "notice@didiglobal.com", "didi"),
            Triple("Lyft", "notice@lyft.com", "lyft"),
            Triple("Uber", "notice@uber.com", "uber"),
            Triple("中国邮政", "notice@chinapost.com.cn", "chinapost"),
            Triple("顺丰速运", "notice@sf-express.com", "sfexpress"),
            Triple("支付宝", "notice@alipay.com", "alipay"),
            Triple("Ant Bank（HK）", "bankupdate@notify.antbank.hk", "alipay"),
            Triple("pixiv", "info@pixiv.net", "pixiv"),
            Triple("Plasma One", "updates@plasma.org", "plasmaone"),
            Triple("SafePal", "marketing@safepal.com", "safepal"),
            Triple("钱迹", "notice@qianjiapp.com", "qianji"),
            Triple("网易邮箱账号安全", "safe@service.netease.com", "163.com"),
            Triple("Moovit", "notice@moovit.com", "moovit"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }
}
