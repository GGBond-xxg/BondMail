package com.bond.mail.data.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class BrandMatcherTest {
    @Test fun carriersCouriersAndHotelsUseSharedCategoryIcons() {
        val domains = mapOf("dito.ph" to "simcard", "globe.com.ph" to "simcard",
            "smart.com.ph" to "simcard", "gomo.ph" to "simcard", "singtel.com" to "simcard",
            "starhub.com" to "simcard", "simba.sg" to "simcard", "dhl.com" to "expressdelivery",
            "fedex.com" to "expressdelivery", "ups.com" to "expressdelivery",
            "hilton.com" to "hotel", "marriott.com" to "hotel")
        domains.forEach { (domain, key) ->
            assertEquals(domain, key, BrandMatcher.match("Notice", "notice@$domain").key)
            assertEquals(domain, key, BrandMatcher.match("Notice", "notice@mail.$domain").key)
            assertEquals(domain, "unknown", BrandMatcher.match("Notice", "notice@not$domain").key)
            assertEquals(domain, "unknown", BrandMatcher.match("Notice", "notice@$domain.example.org").key)
        }
        mapOf("DITO" to "simcard", "DITO Telecommunity" to "simcard", "GOMO" to "simcard",
            "Smart Communications" to "simcard", "Globe Telecom" to "simcard",
            "Singtel" to "simcard", "StarHub" to "simcard", "SIMBA Telecom" to "simcard",
            "DHL Express" to "expressdelivery", "FedEx" to "expressdelivery", "UPS" to "expressdelivery",
            "Hilton Honors" to "hotel", "Marriott Bonvoy" to "hotel").forEach { (name, key) ->
            assertEquals(name, key, BrandMatcher.match(name, "notice@example.org").key)
        }
    }
    @Test fun categoryBrandsAvoidShortWordCollisionsAndKeepSpecificIcons() {
        listOf("Auditor", "Creditors", "Smart", "Globe", "Simba", "Startups", "Groups").forEach {
            assertEquals(it, "unknown", BrandMatcher.match(it, "notice@example.org", "DITO DHL Hilton").key)
        }
        assertEquals("sfexpress", BrandMatcher.match("顺丰速运", "notice@sf-express.com").key)
        assertEquals("chinamobile", BrandMatcher.match("中国移动", "notice@10086.cn").key)
    }

    @Test fun newBrandsAndServiceCategoriesPreferSpecificMarks() {
        mapOf("MUJI" to "muji", "无印良品" to "muji", "UNIQLO Store" to "uniqlo",
            "优衣库" to "uniqlo", "Decathlon Sports" to "decathlon", "迪卡侬" to "decathlon",
            "Example Hotel" to "hotel", "城市酒店" to "hotel", "Example Parcel" to "expressdelivery",
            "城市快递" to "expressdelivery", "Example Shopping" to "shopping", "城市商城" to "shopping",
            "Example Clothing Store" to "clothes", "NIKE Store" to "nike").forEach { (name, key) ->
            assertEquals(name, key, BrandMatcher.match(name, "info@example.org").key)
        }
        listOf("muji", "uniqlo", "decathlon").forEach {
            assertEquals(it, BrandMatcher.match("Notice", "info@mail.$it.com").key)
            assertEquals("unknown", BrandMatcher.match("Notice", "info@not$it.com").key)
        }
        listOf("Workshop", "Restore", "Mallory", "Hotelling", "Express yourself").forEach {
            assertEquals(it, "unknown", BrandMatcher.match(it, "info@example.org", "Hotel shopping parcel").key)
        }
    }

    @Test fun retailBrandsAndGenericCategories() {
        mapOf("NIKE Sports" to "nike", "New Balance" to "newbalance", "Puma" to "puma",
            "Ralph Lauren" to "ralphlauren", "森马" to "semir", "斯凯奇" to "skechers",
            "亚瑟士" to "asics", "安踏" to "anta", "鸿星尔克" to "erke", "回力" to "warrior",
            "李宁" to "lining", "匹克" to "peaksport", "特步" to "xtep", "贵人鸟" to "guirenniao",
            "Lululemon" to "lululemon", "Under Armour" to "underarmour", "361度" to "361sport",
            "Acer" to "acer", "Adidas" to "adidas", "Alienware" to "alienware", "AT&T" to "att",
            "AutoCAD" to "autocad", "Bata" to "bata", "城市服饰" to "clothes",
            "Example Clothing" to "clothes", "Example Sportswear" to "sports", "中乔体育" to "sports",
            "中国乔丹" to "sports", "Qiaodan" to "sports").forEach { (name, key) ->
            assertEquals(name, key, BrandMatcher.match(name, "news@example.org").key)
        }
    }
    @Test fun shortRetailNamesDoNotMatchOtherWordsOrBody() {
        listOf("Santa", "Racer", "CE", "Order 361", "Air Jordan", "Transportation", "Lining update").forEach {
            assertEquals(it, "unknown", BrandMatcher.match(it, "news@example.org", "Nike sports clothes").key)
        }
        assertEquals("unknown", BrandMatcher.match("Notice", "news@notnike.com").key)
        assertEquals("unknown", BrandMatcher.match("Notice", "news@nike.com.example.org").key)
        assertEquals("nike", BrandMatcher.match("Notice", "news@mail.nike.com").key)
        assertEquals("anta", BrandMatcher.match("Notice", "news@anta.com").key)
        assertEquals("asics", BrandMatcher.match("Notice", "news@asics.com").key)
    }

    @Test fun zaGroupAndMailboxAliasesUseCanonicalBrands() {
        assertEquals("za bank", BrandMatcher.match("Notice", "notice@za.group").key)
        assertEquals("za bank", BrandMatcher.match("Notice", "notice@mail.za.group").key)
        assertEquals("unknown", BrandMatcher.match("Notice", "notice@notza.group").key)
        mapOf("foxmail.com" to "qq.com", "googlemail.com" to "gmail.com", "me.com" to "icloud",
            "mac.com" to "icloud", "hotmail.com" to "outlook.com", "live.com" to "outlook.com",
            "msn.com" to "outlook.com", "outlook.cl" to "outlook.com").forEach { (domain, key) ->
            assertEquals(domain, key, BrandMatcher.match("User", "user@$domain").key)
        }
    }

    @Test fun privyPlasmaMailUsesSubjectWithoutChangingOtherPrivyMail() {
        assertEquals("plasmaone", BrandMatcher.match("no-reply@privy.io", "no-reply@privy.io", "Your login code for Plasma One").key)
        assertEquals("unknown", BrandMatcher.match("no-reply@privy.io", "no-reply@privy.io", "Your login code for another app").key)
        assertEquals("unknown", BrandMatcher.match("Sender", "notice@notprivy.io", "Your login code for Plasma One").key)
        assertEquals("plasmaone", BrandMatcher.match("Plasma", "no-reply@auth.privy.io", "PLASMA login").key)
    }

    @Test fun aletaAdventureUsesGenericBank() {
        assertEquals("bank", BrandMatcher.match("Aleta Adventure", "notice@example.com").key)
    }
    @Test
    fun requestedBrandIconsMatchOfficialSenders() {
        val cases = listOf(
            Triple("Grab", "offers@grab.com", "grab"),
            Triple("前程无忧", "jobs@51job.com", "51job"),
            Triple("WhatsApp", "security@whatsapp.com", "whatsapp"),
            Triple("Discord", "noreply@discord.com", "discord"),
            Triple("CoinMarketCap", "newsletter@coinmarketcap.com", "coinmarketcap"),
            Triple("东方财富", "notice@eastmoney.com", "eastmoney"),
            Triple("拼多多", "notice@pinduoduo.com", "pinduoduo"),
            Triple("比亚迪", "news@byd.com", "byd"),
            Triple("中国移动", "service@10086.cn", "chinamobile"),
            Triple("China Mobile Hong Kong", "service@cmhk.com", "chinamobile"),
            Triple("中国电信", "service@189.cn", "chinatelecom"),
            Triple("中国联通", "service@10010.com", "chinaunicom"),
            Triple("10010", "10010@wo.cn", "chinaunicom"),
            Triple("中际旭创", "ir@zj-innolight.com", "innolight"),
            Triple("中国人寿", "service@e-chinalife.com", "chinalife"),
            Triple("美的集团", "news@midea.com", "midea"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }

    @Test
    fun requestedSocialTelecomFinanceAndTravelBrandsMatch() {
        val cases = listOf(
            Triple("Instagram", "security@mail.instagram.com", "instagram"),
            Triple("Telegram", "abuse@telegram.org", "telegram"),
            Triple("Facebook", "security@facebookmail.com", "facebook"),
            Triple("QuickQ", "cs@js7.io", "quickq"),
            Triple("giffgaff", "no_reply@giffgaff.com", "giffgaff"),
            Triple("Vodafone", "notice@vodafone.co.uk", "vodafone"),
            Triple("HTX", "htxsupport@htx-inc.com", "huobi"),
            Triple("OKX", "noreply@okx.com", "okx"),
            Triple("McDonald's", "press@us.mcd.com", "mcdonalds"),
            Triple("Charles Schwab", "alerts@schwab.com", "charlesschwab"),
            Triple("Firstrade", "service@email-mc.firstrade.com", "firstrade"),
            Triple("Grok", "support@x.ai", "grok"),
            Triple("Holafly", "help@holafly.com", "holafly"),
            Triple("华泰证券", "95597@htsc.com", "huatai"),
            Triple("RedteaGO", "service@redteago.com", "redteago"),
            Triple("酷安", "notice@coolapk.com", "coolapk"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }

    @Test
    fun newCompanyAndGenericBankIconsMatchWithoutOverridingSpecificBanks() {
        val cases = listOf(
            Triple("EastWest Bank", "service@eastwestbanker.com", "eastwestbank"),
            Triple("Logitech", "support@logitech.com", "logitech"),
            Triple("Logitech Privacy", "privacy@logi.com", "logitech"),
            Triple("Example Community Bank", "alerts@example.bank", "bank"),
            Triple("示例银行", "notice@example.org", "bank"),
            Triple("Hang Seng Bank", "notice@example.org", "hang seng"),
            Triple("HSBC", "notice@hsbc.com", "hsbc"),
            Triple("中国工商银行", "notice@icbc.com.cn", "icbc"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }

    @Test
    fun expandedOfflineBrandIconsMatchOfficialSenders() {
        val cases = listOf(
            Triple("Gitee", "notice@gitee.com", "gitee"),
            Triple("GitLab", "noreply@gitlab.com", "gitlab"),
            Triple("GMX", "service@gmx.com", "gmx"),
            Triple("Google", "no-reply@google.com", "google"),
            Triple("Alibaba", "notice@alibaba.com", "alibaba"),
            Triple("Alibaba Cloud", "notice@alibabacloud.com", "alibabacloud"),
            Triple("Ant Group", "notice@antgroup.com", "antgroup"),
            Triple("AOL", "service@aol.com", "aol"),
            Triple("Arc Browser", "team@arc.net", "arc"),
            Triple("Avalanche", "updates@avalabs.org", "avalanche"),
            Triple("百度", "notice@baidu.com", "baidu"),
            Triple("Bento", "hello@bento.me", "bento"),
            Triple("Brave Browser", "notice@brave.com", "brave"),
            Triple("Burton", "news@burton.com", "burton"),
            Triple("Claude", "notice@anthropic.com", "claude"),
            Triple("Cloudflare", "updates@cloudflare.com", "cloudflare"),
            Triple("CMake", "news@cmake.org", "cmake"),
            Triple("CNES", "press@cnes.fr", "cnes"),
            Triple("CNET", "newsletter@cnet.com", "cnet"),
            Triple("CNN", "newsletter@cnn.com", "cnn"),
            Triple("Codex", "notice@openai.com", "codex"),
            Triple("Continente", "news@continente.pt", "continente"),
            Triple("大众点评", "notice@dianping.com", "dazhongdianping"),
            Triple("DeepAI", "hello@deepai.org", "deepai"),
            Triple("DeepSeek", "service@deepseek.com", "deepseek"),
            Triple("Docker", "notice@docker.com", "docker"),
            Triple("Dolby", "news@dolby.com", "dolby"),
            Triple("豆瓣", "notice@douban.com", "douban"),
            Triple("Drupal", "notice@drupal.org", "drupal"),
            Triple("Duolingo", "notice@duolingo.com", "duolingo"),
            Triple("Gemini", "google-gemini-noreply@google.com", "gemini"),
            Triple("LinkedIn", "messages@linkedin.com", "linkedin"),
            Triple("Messenger", "notification@facebookmail.com", "messenger"),
            Triple("MEXC", "dontreply@notification.mexc.link", "mexc"),
            Triple("Microsoft Copilot", "copilot@email.microsoft.com", "microsoftcopilot"),
            Triple("Patreon", "notice@patreon.com", "patreon"),
            Triple("VK", "notice@vk.com", "vk"),
            Triple("WhatsApp", "security@whatsapp.com", "whatsapp"),
            Triple("Xiaomi MiMo", "support-mimo@xiaomi.com", "xiaomimimo"),
            Triple("YouTube", "no-reply@youtube.com", "youtube"),
            Triple("GameBanana", "notice@gamebanana.com", "gamebanana"),
            Triple("Git", "notice@git-scm.com", "git"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }

    @Test
    fun genericExchangeStaysBehindDedicatedBrandsAndRejectsLookalikes() {
        val cases = listOf(
            Triple("KuCoin", "news@kucoin.com", "exchange"),
            Triple("Gemini", "notice@gemini.com", "exchange"),
            Triple("Digital Asset Exchange", "notice@example.org", "exchange"),
            Triple("MEXC", "notice@mexc.com", "mexc"),
            Triple("OKX", "notice@okx.com", "okx"),
            Triple("Binance", "notice@binance.com", "binance"),
            Triple("Microsoft Exchange", "notice@microsoft.com", "microsoft"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
        assertEquals("unknown", BrandMatcher.match("Crypto Notice", "notice@fakekucoin.com").key)
    }

    @Test
    fun airlineAndSimCardFallbacksStayBehindSpecificBrands() {
        val cases = listOf(
            Triple("Singapore Airlines", "notice@singaporeair.com", "airplane"),
            Triple("阿联酋航空", "notice@emirates.com", "airplane"),
            Triple("SoSIM", "service@sosimhk.com", "simcard"),
            Triple("Airalo eSIM", "hello@airalo.com", "simcard"),
            Triple("Airbnb", "notice@airbnb.com", "airbnb"),
            Triple("Air China", "notice@airchina.com", "airchina"),
            Triple("Holafly eSIM", "help@holafly.com", "holafly"),
            Triple("RedteaGO eSIM", "service@redteago.com", "redteago"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }

    @Test
    fun domainRulesDoNotMatchLookalikeDomainSuffixes() {
        assertEquals("unknown", BrandMatcher.match("Invoice", "notice@two.cn").key)
        assertEquals("unknown", BrandMatcher.match("Travel", "notice@notairasia.com").key)
        assertEquals("unknown", BrandMatcher.match("Mobile notice", "notice@fake-sosimhk.com").key)
    }

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
            Triple("和风天气", "support@qweather.com", "weather"),
            Triple("彩云天气", "ai@caiyunapp.com", "weather"),
            Triple("彩云天气开发者", "developer.sg@caiyunapp.com", "weather"),
            Triple("OpenWeather", "info@openweathermap.org", "weather"),
            Triple("华风爱科", "ad-hfaw@weathercn.com", "weather"),
            Triple("华风爱科客服", "service@weathercn.com", "weather"),
            Triple("华风爱科商务", "business@weathercn.com", "weather"),
            Triple("墨迹天气", "AS@moji.com", "weather"),
            Triple("墨迹天气广告合作", "weihua.yang@moji.com", "weather"),
            Triple("墨迹天气 Android 商务", "yuejiao.bai@moji.com", "weather"),
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
            Triple("Amazon Web Services", "no-reply@aws.amazon.com", "amazon"),
            Triple("AWS", "notifications@aws.example", "amazon"),
            Triple("Kraken", "no-reply@email.kraken.com", "kraken"),
        )

        cases.forEach { (name, address, expectedKey) ->
            assertEquals(expectedKey, BrandMatcher.match(name, address).key)
        }
    }
}
