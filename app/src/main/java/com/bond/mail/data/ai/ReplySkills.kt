package com.bond.mail.data.ai

import com.bond.mail.data.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal const val REPLY_SKILL_LIMIT = 8000
private const val SKILL_BYTES = 32768
private const val SKILLS_KEY = "service:ai:reply-skills:v1"
internal data class ReplySkill(val id: String, val name: String, val content: String)
internal data class ReplySkills(val items: List<ReplySkill> = emptyList(), val activeId: String? = null) {
    val active: ReplySkill? get() = items.firstOrNull { it.id == activeId }
}

internal fun parseReplySkill(bytes: ByteArray): String {
    if (bytes.size > SKILL_BYTES) throw AiFailure("ai_skill_too_long")
    val raw = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString() }
        catch (_: Exception) { throw AiFailure("ai_skill_invalid") }
    var text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").trim()
    if (text.startsWith("---\n")) {
        val end = text.indexOf("\n---", 4)
        if (end < 0) throw AiFailure("ai_skill_invalid")
        text = text.substring(end + 4).trim()
    }
    if (text.length > REPLY_SKILL_LIMIT) throw AiFailure("ai_skill_too_long")
    if (text.isBlank() || text.any { it.isISOControl() && it !in "\n\r\t" } ||
        Regex("(?is)<(?:!doctype\\s+html|html|head|body)(?:\\s|>)").containsMatchIn(text)) throw AiFailure("ai_skill_invalid")
    return text
}

internal fun readReplySkill(input: InputStream): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    val deadline = System.nanoTime() + 30_000_000_000L
    while (true) {
        if (System.nanoTime() > deadline) throw AiFailure("ai_skill_import_failed")
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() + count > SKILL_BYTES) throw AiFailure("ai_skill_too_long")
        output.write(buffer, 0, count)
    }
    return parseReplySkill(output.toByteArray())
}

internal fun replySkillUrl(value: String): String {
    val uri = try { URI(value.trim()) } catch (_: Exception) { throw AiFailure("ai_skill_url_invalid") }
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.port !in -1..65535 || uri.port == 0)
        throw AiFailure("ai_skill_url_invalid")
    if (uri.host.equals("github.com", true)) {
        val parts = uri.rawPath.split('/').filter { it.isNotBlank() }
        if (parts.size < 5 || parts[2] != "blob") throw AiFailure("ai_skill_url_invalid")
        return "https://raw.githubusercontent.com/" + (parts.take(2) + parts.drop(3)).joinToString("/")
    }
    return uri.toASCIIString()
}

internal suspend fun fetchReplySkill(value: String): String = withContext(Dispatchers.IO) {
    val connection = URI(replySkillUrl(value)).toURL().openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.useCaches = false
        connection.setRequestProperty("Accept", "text/plain, text/markdown")
        if (connection.responseCode !in 200..299) throw AiFailure("ai_skill_import_failed")
        if (connection.contentLengthLong > SKILL_BYTES) throw AiFailure("ai_skill_too_long")
        connection.inputStream.use(::readReplySkill)
    } finally { connection.disconnect() }
}

internal fun decodeReplySkills(raw: String?): ReplySkills {
    if (raw == null) return ReplySkills()
    val root = JSONObject(raw)
    val array = root.getJSONArray("items")
    require(array.length() <= 20)
    val items = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        ReplySkill(item.getString("id"), item.getString("name"), parseReplySkill(item.getString("content").toByteArray(Charsets.UTF_8)))
    }
    require(items.all { it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 80 })
    require(items.map { it.id }.distinct().size == items.size)
    return ReplySkills(items, root.optString("active").takeIf { id -> items.any { it.id == id } })
}
internal fun encodeReplySkills(value: ReplySkills): String {
    val raw = JSONObject().put("active", value.activeId ?: "").put("items", JSONArray().also { array ->
        value.items.forEach { item -> array.put(JSONObject().put("id", item.id).put("name", item.name).put("content", item.content)) }
    }).toString()
    decodeReplySkills(raw)
    return raw
}
internal fun CredentialStore.replySkills() = decodeReplySkills(read(SKILLS_KEY))
internal fun CredentialStore.saveReplySkills(value: ReplySkills) = save(SKILLS_KEY, encodeReplySkills(value))
