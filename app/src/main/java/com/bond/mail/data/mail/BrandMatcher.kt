package com.bond.mail.data.mail

import java.util.LinkedHashMap

/** Local-only brand matcher. No favicon or network request is performed while scrolling. */
object BrandMatcher {
    data class Brand(val key: String, val label: String)

    private val cache = object : LinkedHashMap<String, Brand>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Brand>?): Boolean = size > 400
    }

    private val rules = linkedMapOf(
        "binance" to "BN", "bybit" to "BY", "okx" to "OKX", "bitget" to "BG",
        "coinbase" to "CB", "kraken" to "KR", "paypal" to "P", "stripe" to "S",
        "youtube" to "YT", "netflix" to "N", "discord" to "D", "telegram" to "TG",
        "twitter" to "X", "x.com" to "X", "facebook" to "F", "instagram" to "IG",
        "linkedin" to "IN", "tiktok" to "TT", "whatsapp" to "WA", "reddit" to "R",
        "spotify" to "SP", "github" to "GH", "gitlab" to "GL", "google" to "G",
        "microsoft" to "MS", "openai" to "AI", "chatgpt" to "AI", "cloudflare" to "CF",
        "amazon" to "A", "aws" to "AWS", "slack" to "SL", "zoom" to "Z",
        "dropbox" to "DB", "adobe" to "AD", "figma" to "FI", "notion" to "NO",
        "steam" to "ST", "grab" to "GR", "za bank" to "ZA", "bank of china" to "BOC", "bochk" to "BOC",
        "gate.io" to "GT", "gate" to "GT", "neverless" to "NE",
        "hsbc" to "HSBC", "hang seng" to "HS", "shopify" to "SH", "shopee" to "SE",
        "airbnb" to "AB", "uber" to "U", "samsung" to "SA", "xiaomi" to "MI",
        "huawei" to "HW", "lenovo" to "L", "nvidia" to "NV", "amd" to "AMD",
        "protonmail" to "PM", "proton.me" to "PM", "tutanota" to "TU", "tuta.com" to "TU",
        "zoho" to "ZO", "gmx" to "GMX", "web.de" to "WEB", "mail.ru" to "MR",
        "gmail.com" to "G", "mail.com" to "M", "icloud" to "AP", "apple" to "AP",
        "qq.com" to "QQ", "163.com" to "163", "126.com" to "126",
        "outlook" to "O", "yahoo" to "Y",
    )

    @Synchronized
    fun match(senderName: String, senderAddress: String): Brand {
        val cacheKey = "$senderName|$senderAddress".lowercase()
        cache[cacheKey]?.let { return it }
        val haystack = "$senderName $senderAddress".lowercase()
        val entry = rules.entries.firstOrNull { haystack.contains(it.key) }
        val fallback = senderName.trim().firstOrNull()?.uppercase() ?: senderAddress.firstOrNull()?.uppercase() ?: "?"
        return Brand(entry?.key ?: "unknown", entry?.value ?: fallback).also { cache[cacheKey] = it }
    }
}
