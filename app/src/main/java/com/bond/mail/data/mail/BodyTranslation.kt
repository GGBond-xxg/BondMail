package com.bond.mail.data.mail

import com.bond.mail.data.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

internal enum class TranslationProvider(val labelKey: String, val needsId: Boolean) {
    ALIYUN("translation_aliyun", true), YOUDAO("translation_youdao", true),
    GOOGLE("translation_google", false), MICROSOFT("translation_microsoft", false),
}

internal const val TRANSLATION_ACTIVE_KEY = "service:translation:active"
internal fun TranslationProvider.credentialKey() = "service:translation:$name:v1"
internal fun CredentialStore.translationProvider(): TranslationProvider =
    runCatching { TranslationProvider.valueOf(read(TRANSLATION_ACTIVE_KEY).orEmpty()) }
        .getOrDefault(TranslationProvider.ALIYUN)

internal class TranslationCredentials(val id: String, val secret: String, val region: String = "")

internal fun CredentialStore.translationCredentials(provider: TranslationProvider): TranslationCredentials? = runCatching {
    val json = JSONObject(read(provider.credentialKey()) ?: return null)
    TranslationCredentials(json.optString("id"), json.getString("secret"), json.optString("region"))
        .takeIf { (!provider.needsId || it.id.isNotBlank()) && it.secret.isNotBlank() }
}.getOrNull()

internal fun translationBodyText(html: String?, plain: String): String {
    if (html.isNullOrBlank()) return plain.trim()
    val doc = Jsoup.parse(html)
    doc.select("script,style,noscript,head,[hidden],[aria-hidden=true]").remove()
    doc.select("br").append("\n")
    doc.select("p,div,li,tr,h1,h2,h3,blockquote").prepend("\n").append("\n")
    return doc.body().wholeText().replace(Regex("[\\t ]+"), " ")
        .replace(Regex("\\n[ \\t]*\\n(?:[ \\t]*\\n)+"), "\n\n").trim()
        .ifBlank { plain.trim() }
}

internal fun translationChunks(text: String, limit: Int = 1500): List<String> {
    require(limit > 1)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = (start + limit).coerceAtMost(text.length)
        if (end < text.length) {
            val boundary = text.lastIndexOfAny(charArrayOf('\n', ' ', '.', '。'), end - 1)
            if (boundary > start + limit / 2) end = boundary + 1
            if (Character.isHighSurrogate(text[end - 1])) end--
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}

internal fun aliyunEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    .replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

internal fun aliyunSignedForm(parameters: Map<String, String>, secret: String): String {
    val canonical = parameters.toSortedMap().entries.joinToString("&") {
        "${aliyunEncode(it.key)}=${aliyunEncode(it.value)}"
    }
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec("$secret&".toByteArray(Charsets.UTF_8), "HmacSHA1"))
    val signature = Base64.getEncoder().encodeToString(
        mac.doFinal("POST&%2F&${aliyunEncode(canonical)}".toByteArray(Charsets.UTF_8)))
    return "$canonical&Signature=${aliyunEncode(signature)}"
}

internal class TranslationFailure(val reason: String) : Exception(reason)

/** Mainland China endpoint; no Google service or model download is required. */
internal suspend fun translateBody(text: String, target: String, credentials: TranslationCredentials,
    provider: TranslationProvider = TranslationProvider.ALIYUN): String =
    withContext(Dispatchers.IO) {
        if (text.isBlank()) throw TranslationFailure("translation_empty")
        translationChunks(text).map { chunk ->
            coroutineContext.ensureActive()
            if (provider != TranslationProvider.ALIYUN) {
                return@map translateOtherProvider(chunk, target, credentials, provider)
            }
            val form = aliyunSignedForm(mapOf(
                "Action" to "TranslateGeneral", "Version" to "2018-10-12", "Format" to "JSON",
                "AccessKeyId" to credentials.id, "SignatureMethod" to "HMAC-SHA1",
                "SignatureVersion" to "1.0", "SignatureNonce" to UUID.randomUUID().toString(),
                "Timestamp" to Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                "FormatType" to "text", "Scene" to "general", "SourceLanguage" to "auto",
                "TargetLanguage" to (if (target == "zh-TW") "zh-tw" else target), "SourceText" to chunk,
            ), credentials.secret)
            val connection = URI("https://mt.cn-hangzhou.aliyuncs.com/").toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                coroutineContext.ensureActive()
                val parsed = runCatching { JSONObject(response) }.getOrNull()
                val body = parsed?.optJSONObject("TranslateGeneralResponse") ?: parsed
                val code = body?.optString("Code").orEmpty()
                if (status !in 200..299 || code != "200") {
                    val reason = when {
                        status == 401 || status == 403 || code.contains("AccessKey", true) ||
                            code.contains("Signature", true) || code == "10009" -> "translation_auth_failed"
                        code in setOf("10010", "10011", "10012", "10013") -> "translation_service_failed"
                        status == 429 || code.contains("Throttl", true) -> "translation_rate_limited"
                        code in setOf("10005", "10006") -> "translation_unsupported"
                        else -> "translation_failed"
                    }
                    // Do not expose service responses: they may contain submitted text or credentials.
                    throw TranslationFailure(reason)
                }
                body?.optJSONObject("Data")?.optString("Translated")?.takeIf { it.isNotBlank() }
                    ?: throw TranslationFailure("translation_failed")
            } finally { connection.disconnect() }
        }.joinToString("\n")
    }

internal fun youdaoInput(text: String): String =
    if (text.length <= 20) text else text.take(10) + text.length + text.takeLast(10)

private fun formBody(values: Map<String, String>) = values.entries.joinToString("&") {
    "${aliyunEncode(it.key)}=${aliyunEncode(it.value)}"
}

private fun translateOtherProvider(text: String, target: String, credentials: TranslationCredentials,
    provider: TranslationProvider): String {
    val headers = mutableMapOf<String, String>()
    var contentType = "application/json; charset=UTF-8"
    val url: String
    val payload: String
    when (provider) {
        TranslationProvider.YOUDAO -> {
            url = "https://openapi.youdao.com/api"
            val salt = UUID.randomUUID().toString()
            val time = Instant.now().epochSecond.toString()
            val input = credentials.id + youdaoInput(text) + salt + time + credentials.secret
            val sign = java.security.MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val language = when (target) { "zh" -> "zh-CHS"; "zh-TW" -> "zh-CHT"; else -> target }
            payload = formBody(mapOf("q" to text, "from" to "auto", "to" to language, "strict" to "true",
                "appKey" to credentials.id, "salt" to salt, "curtime" to time, "signType" to "v3", "sign" to sign))
            contentType = "application/x-www-form-urlencoded; charset=UTF-8"
        }
        TranslationProvider.GOOGLE -> {
            url = "https://translation.googleapis.com/language/translate/v2?key=${aliyunEncode(credentials.secret)}"
            payload = JSONObject().put("q", text).put("target", target).put("format", "text").toString()
        }
        TranslationProvider.MICROSOFT -> {
            val language = when (target) { "zh" -> "zh-Hans"; "zh-TW" -> "zh-Hant"; else -> target }
            url = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=${aliyunEncode(language)}"
            headers["Ocp-Apim-Subscription-Key"] = credentials.secret
            if (credentials.region.isNotBlank()) headers["Ocp-Apim-Subscription-Region"] = credentials.region
            payload = org.json.JSONArray().put(JSONObject().put("Text", text)).toString()
        }
        else -> error("Unexpected translation provider")
    }
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", contentType)
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        if (status !in 200..299) throw TranslationFailure(when (status) {
            401, 403 -> "translation_auth_failed"
            429 -> "translation_rate_limited"
            else -> "translation_failed"
        })
        return parseTranslationResponse(provider, connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
}

internal fun parseTranslationResponse(provider: TranslationProvider, response: String): String {
    val translated = when (provider) {
        TranslationProvider.YOUDAO -> {
            val json = JSONObject(response)
            val code = json.optString("errorCode")
            if (code != "0") throw TranslationFailure(when (code) {
                "108", "110", "111", "202", "203", "205", "206", "207" -> "translation_auth_failed"
                "411", "412" -> "translation_rate_limited"
                "310", "401" -> "translation_service_failed"
                "102", "103" -> "translation_unsupported"
                else -> "translation_failed"
            })
            json.optJSONArray("translation")?.optString(0)
        }
        TranslationProvider.GOOGLE -> JSONObject(response).optJSONObject("data")
            ?.optJSONArray("translations")?.optJSONObject(0)?.optString("translatedText")
            ?.let { org.jsoup.parser.Parser.unescapeEntities(it, false) }
        TranslationProvider.MICROSOFT -> org.json.JSONArray(response).optJSONObject(0)
            ?.optJSONArray("translations")?.optJSONObject(0)?.optString("text")
        TranslationProvider.ALIYUN -> {
            val json = JSONObject(response)
            val body = json.optJSONObject("TranslateGeneralResponse") ?: json
            if (body.optString("Code") != "200") throw TranslationFailure("translation_failed")
            body.optJSONObject("Data")?.optString("Translated")
        }
    }
    return translated?.takeIf { it.isNotBlank() } ?: throw TranslationFailure("translation_failed")
}

internal data class TranslatedMailText(val subject: String, val body: String)

/** Keep subject/body separate; no delimiter can collide with actual mail content. */
internal suspend fun translateMailText(subject: String, body: String,
    translate: suspend (String) -> String): TranslatedMailText {
    if (subject.isBlank() && body.isBlank()) throw TranslationFailure("translation_empty")
    val translatedSubject = if (subject.isBlank()) subject else translate(subject)
    val translatedBody = if (body.isBlank()) body else translate(body)
    return TranslatedMailText(translatedSubject, translatedBody)
}
