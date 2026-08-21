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
            Triple("Spark", "hello@sparkmailapp.com", "spark"),
            Triple("金标联盟", "service@itgsa.com", "itgsa"),
            Triple("Direktorat Jenderal Imigrasi", "no-reply@notif.imigrasi.go.id", "imigrasi"),
            Triple("Kantor Imigrasi Jakarta Selatan", "jakartaselatan@imigrasi.go.id", "imigrasi"),
            Triple("Indonesian Immigration", "notification@evisa.imigrasi.go.id", "imigrasi"),
            Triple("网易邮箱账号安全", "safe@service.netease.com", "163.com"),
            Triple("Moovit", "notice@moovit.com", "moovit"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }

    @Test
    fun extendedOfflineBrandIconsMatchSpecificSenders() {
        val cases = listOf(
            Triple("中国农业银行", "notice@abchina.com", "abchina"),
            Triple("American Express", "alerts@americanexpress.com", "americanexpress"),
            Triple("中国建设银行", "service@ccb.com", "ccb"),
            Triple("中国工商银行", "notice@icbc.com.cn", "icbc"),
            Triple("招商银行", "service@cmbchina.com", "cmb"),
            Triple("BOSS直聘", "notice@zhipin.com", "bosszhipin"),
            Triple("哔哩哔哩", "notice@bilibili.com", "bilibili"),
            Triple("京东", "notice@jd.com", "jd"),
            Triple("美团", "notice@meituan.com", "meituan"),
            Triple("拼多多", "notice@pinduoduo.com", "pinduoduo"),
            Triple("ASML", "news@asml.com", "asml"),
            Triple("TSMC", "news@tsmc.com", "tsmc"),
            Triple("SK Hynix", "news@skhynix.com", "skhynix"),
            Triple("Mercedes-Benz", "news@mercedes-benz.com", "mercedesbenz"),
            Triple("Volkswagen", "news@volkswagen.com", "volkswagen"),
            Triple("S&P Global", "news@spglobal.com", "spglobal"),
            Triple("Western Digital", "news@westerndigital.com", "westerndigital"),
            Triple("智联招聘", "notice@zhaopin.com", "zhaopin"),
            Triple("Adobe", "mail@mail.adobe.com", "adobe"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }
}
