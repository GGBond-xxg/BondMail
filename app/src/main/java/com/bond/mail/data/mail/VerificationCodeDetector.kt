package com.bond.mail.data.mail

import org.jsoup.Jsoup
import java.text.Normalizer

/** Local, conservative extraction. A number alone (including one in a subject) is not an OTP. */
object VerificationCodeDetector {
    private val hint = Regex(
        "验证码|驗證碼|校验码|校驗碼|动态码|動態碼|一次性密码|一次性密碼|" +
            "認証コード|確認コード|ワンタイムパスワード|인증\\s*코드|확인\\s*코드|" +
            "رمز التحقق|رمز التأكيد|รหัสยืนยัน|सत्यापन कोड|" +
            "(?i:code de v[ée]rification|code de s[ée]curit[ée]|Bestätigungscode|Sicherheitscode|Einmalpasswort|" +
            "c[oó]digo de verificaci[oó]n|c[oó]digo de seguridad|c[oó]digo de verifica[çc][ãa]o|" +
            "codice di verifica|kode verifikasi|код подтверждения)|" +
            "(?i:\\b(?:verification|security|authentication|confirmation|login|sign[ -]?in|one[ -]?time)\\s+(?:code|password|passcode)\\b|\\bOTP\\b|\\bpasscode\\b|\\byour code\\b)",
    )
    private val candidate = Regex("(?<![A-Za-z0-9_./@+-])(?:[0-9]{3}[ -][0-9]{3}|[A-Za-z0-9]{4,8})(?![A-Za-z0-9_./@+-])")
    private val afterBridge = Regex("(?i)[\\s:：,，。.!！\\-–—=()（）]*((?:is your|is|est|ist|es|é|è|adalah|هو|は|คือ|는|은|है|为|為|是|为您生成|如下|为您提供)[\\s:：,，。.!！\\-–—=()（）]*)?")
    private val beforeBridge = Regex("(?i)[\\s:：,，。.!！\\-–—=()（）]*(?:(?:is|is your|是您的|是你的|是您本次的)\\s*)?")

    fun detect(subject: String, bodyText: String, bodyHtml: String? = null, rules: List<VerificationRule> = emptyList()): String? {
        val htmlText = bodyHtml?.take(512_000)?.let {
            Jsoup.parse(it).apply {
                select("script,style,head,blockquote,.gmail_quote,.yahoo_quoted").remove()
            }.body().text()
        }.orEmpty()
        val matches = listOf(subject.take(4_096), bodyText.take(128_000), htmlText)
            .map(::normalize).flatMap { extract(it) + extractCustom(it, rules) }.distinct()
        // Conflicting codes are deliberately left for the reader to choose manually.
        return matches.singleOrNull()
    }

    private fun normalize(source: String): String = Normalizer.normalize(source, Normalizer.Form.NFKC)
        .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        .map { if (it.isDigit()) it.digitToIntOrNull()?.digitToChar() ?: it else it }.joinToString("")
        .let { normalized ->
            val quoted = Regex("(?im)^\\s*(?:On .+ wrote:|在.+写道[:：]|在.+寫道[:：]|-{2,}\\s*(?:Original Message|原始邮件|原始郵件))").find(normalized)
            if (quoted == null) normalized else normalized.substring(0, quoted.range.first)
        }

    fun testRule(sample: String, rule: VerificationRule): String? =
        extractCustom(normalize(sample.take(128_000)), listOf(rule.copy(enabled = true))).distinct().singleOrNull()

    private fun extractCustom(text: String, rules: List<VerificationRule>): List<String> =
        rules.take(20).filter { it.enabled && it.isValid() }.flatMap { rule ->
            val parts = normalize(rule.template).split("{code}")
            fun literal(value: String) = value.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
            val pattern = literal(parts[0]) + "\\s*(?<![A-Za-z0-9_./@+-])([${rule.alphabet.characters}]{${rule.length}})(?![A-Za-z0-9_./@+-])\\s*" + literal(parts[1])
            Regex(pattern, RegexOption.IGNORE_CASE).findAll(text).map { it.groupValues[1] }.toList()
        }

    private fun extract(text: String): List<String> {
        val hints = hint.findAll(text).toList()
        if (hints.isEmpty()) return emptyList()
        return candidate.findAll(text).mapNotNull { token ->
            val code = token.value.replace(" ", "").replace("-", "")
            if (code.none(Char::isDigit)) return@mapNotNull null
            val contextual = hints.any { label ->
                when {
                    token.range.first > label.range.last -> {
                        val gap = token.range.first - label.range.last - 1
                        gap <= 36 && afterBridge.matches(text.substring(label.range.last + 1, token.range.first))
                    }
                    label.range.first > token.range.last -> {
                        val gap = label.range.first - token.range.last - 1
                        gap <= 24 && beforeBridge.matches(text.substring(token.range.last + 1, label.range.first))
                    }
                    else -> false
                }
            }
            code.takeIf { contextual }
        }.toList()
    }
}
