package com.bond.mail.data.mail

import java.util.LinkedHashMap

/** Local-only brand matcher. No favicon or network request is performed while scrolling. */
object BrandMatcher {
    data class Brand(val key: String, val label: String)

    private val cache = object : LinkedHashMap<String, Brand>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Brand>?): Boolean = size > 400
    }

    private val rules = linkedMapOf(
        "n26.com" to Brand("n26", "N26"),
        "n26" to Brand("n26", "N26"),
        "ifast global" to Brand("ifast", "iFAST"),
        "ifastcorp" to Brand("ifast", "iFAST"),
        "ifast" to Brand("ifast", "iFAST"),
        "cathaypacific" to Brand("cathay", "CX"),
        "cathay pacific" to Brand("cathay", "CX"),
        "cathay" to Brand("cathay", "CX"),
        "trae.ai" to Brand("trae", "TRAE"),
        "trae" to Brand("trae", "TRAE"),
        "osl.com" to Brand("osl", "OSL"),
        "osldigital" to Brand("osl", "OSL"),
        "osl group" to Brand("osl", "OSL"),
        "osl " to Brand("osl", "OSL"),
        "interactive brokers" to Brand("ibkr", "IBKR"),
        "interactivebrokers" to Brand("ibkr", "IBKR"),
        "ibkr" to Brand("ibkr", "IBKR"),
        "lottiefiles" to Brand("lottiefiles", "L"),
        "lottie files" to Brand("lottiefiles", "L"),
        "lottie" to Brand("lottiefiles", "L"),
        "longbridge" to Brand("longbridge", "LB"),
        "long bridge" to Brand("longbridge", "LB"),
        "长桥" to Brand("longbridge", "LB"),
        "futu securities" to Brand("futu", "FT"),
        "futunn" to Brand("futu", "FT"),
        "富途" to Brand("futu", "FT"),
        "moomoo" to Brand("futu", "FT"),
        "transferwise" to Brand("wise", "W"),
        "wise.com" to Brand("wise", "W"),
        "wise" to Brand("wise", "W"),
        "agoda" to Brand("agoda", "A"),
        "robinhood" to Brand("robinhood", "RH"),
        "binance" to Brand("binance", "BN"), "bybit" to Brand("bybit", "BY"),
        "okx" to Brand("okx", "OKX"), "bitget" to Brand("bitget", "BG"),
        "coinbase" to Brand("coinbase", "CB"), "kraken" to Brand("kraken", "KR"),
        "paypal" to Brand("paypal", "P"), "stripe" to Brand("stripe", "S"),
        "youtube" to Brand("youtube", "YT"), "netflix" to Brand("netflix", "N"),
        "discord" to Brand("discord", "D"), "telegram" to Brand("telegram", "TG"),
        "twitter" to Brand("twitter", "X"), "x.com" to Brand("x.com", "X"),
        "facebook" to Brand("facebook", "F"), "instagram" to Brand("instagram", "IG"),
        "linkedin" to Brand("linkedin", "IN"), "tiktok" to Brand("tiktok", "TT"),
        "whatsapp" to Brand("whatsapp", "WA"), "reddit" to Brand("reddit", "R"),
        "spotify" to Brand("spotify", "SP"), "github" to Brand("github", "GH"),
        "gitlab" to Brand("gitlab", "GL"), "google" to Brand("google", "G"),
        "outlook.com" to Brand("outlook.com", "O"), "hotmail.com" to Brand("hotmail.com", "O"),
        "live.com" to Brand("live.com", "O"), "microsoft" to Brand("microsoft", "MS"),
        "openai" to Brand("openai", "AI"), "chatgpt" to Brand("chatgpt", "AI"),
        "cloudflare" to Brand("cloudflare", "CF"), "amazon" to Brand("amazon", "A"),
        "aws" to Brand("aws", "AWS"), "slack" to Brand("slack", "SL"),
        "zoom" to Brand("zoom", "Z"), "dropbox" to Brand("dropbox", "DB"),
        "adobe" to Brand("adobe", "AD"), "figma" to Brand("figma", "FI"),
        "notion" to Brand("notion", "NO"), "steam" to Brand("steam", "ST"),
        "grab" to Brand("grab", "GR"), "za bank" to Brand("za bank", "ZA"),
        "bank of china" to Brand("bank of china", "BOC"), "bochk" to Brand("bochk", "BOC"),
        "gate.io" to Brand("gate.io", "GT"), "gate" to Brand("gate", "GT"),
        "neverless" to Brand("neverless", "NE"), "hsbc" to Brand("hsbc", "HSBC"),
        "hang seng" to Brand("hang seng", "HS"), "shopify" to Brand("shopify", "SH"),
        "shopee" to Brand("shopee", "SE"), "airbnb" to Brand("airbnb", "AB"),
        "uber" to Brand("uber", "U"), "samsung" to Brand("samsung", "SA"),
        "xiaomi" to Brand("xiaomi", "MI"), "huawei" to Brand("huawei", "HW"),
        "lenovo" to Brand("lenovo", "L"), "nvidia" to Brand("nvidia", "NV"),
        "amd" to Brand("amd", "AMD"), "protonmail" to Brand("protonmail", "PM"),
        "proton.me" to Brand("proton.me", "PM"), "tutanota" to Brand("tutanota", "TU"),
        "tuta.com" to Brand("tuta.com", "TU"), "zoho" to Brand("zoho", "ZO"),
        "gmx" to Brand("gmx", "GMX"), "web.de" to Brand("web.de", "WEB"),
        "mail.ru" to Brand("mail.ru", "MR"), "gmail.com" to Brand("gmail.com", "G"),
        "mail.com" to Brand("mail.com", "M"), "icloud" to Brand("icloud", "AP"),
        "apple" to Brand("apple", "AP"), "qq.com" to Brand("qq.com", "QQ"),
        "163.com" to Brand("163.com", "163"), "126.com" to Brand("126.com", "126"),
        "outlook" to Brand("outlook", "O"), "yahoo" to Brand("yahoo", "Y"),
    )

    @Synchronized
    fun match(senderName: String, senderAddress: String): Brand {
        val cacheKey = "$senderName|$senderAddress".lowercase()
        cache[cacheKey]?.let { return it }
        val haystack = "$senderName $senderAddress".lowercase()
        val entry = rules.entries.firstOrNull { haystack.contains(it.key) }
        val fallback = senderName.trim().firstOrNull()?.uppercase() ?: senderAddress.firstOrNull()?.uppercase() ?: "?"
        return (entry?.value ?: Brand("unknown", fallback)).also { cache[cacheKey] = it }
    }
}
