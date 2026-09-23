package com.bond.mail.data.ai

import com.bond.mail.data.security.CredentialStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal data class AiPreset(val id: String, val label: String, val endpoint: String,
    val models: List<String>, val protocol: AiProvider = AiProvider.COMPATIBLE, val auth: AiAuth = AiAuth.BEARER)

// Examples verified against official docs; refresh models for the current account's catalog.
internal val aiPresets = listOf(
    AiPreset("deepseek", "DeepSeek", "https://api.deepseek.com", listOf("deepseek-flash", "deepseek-v4-pro")),
    AiPreset("kimi", "Kimi", "https://api.moonshot.cn/v1", listOf("kimi-k2.6", "kimi-k3", "kimi-k2.5")),
    AiPreset("mimo", "Xiaomi MiMo", "https://api.xiaomimimo.com/v1", listOf("mimo-v2.6-flash", "mimo-v2.6-pro", "mimo-v2.5"), auth = AiAuth.API_KEY),
    AiPreset("openai", "OpenAI", "https://api.openai.com/v1", listOf("gpt-5.4-mini", "gpt-5.4", "gpt-5.5")),
    AiPreset("gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta", emptyList(), AiProvider.GEMINI),
    AiPreset("custom", "", "", emptyList()),
)

internal class AiProfile(val id: String, val name: String, val presetId: String, val note: String,
    val config: AiConfig, val models: List<String> = emptyList())
internal data class AiProfiles(val entries: List<AiProfile>, val activeId: String?) {
    val active: AiProfile? get() = entries.firstOrNull { it.id == activeId }
}
private const val PROFILES_KEY = "service:ai:profiles:v2"

internal fun encodeAiProfiles(profiles: AiProfiles): String = JSONObject().put("active", profiles.activeId.orEmpty())
    .put("profiles", JSONArray().also { array -> profiles.entries.forEach { profile ->
        val config = profile.config
        array.put(JSONObject().put("id", profile.id).put("name", profile.name).put("preset", profile.presetId)
            .put("note", profile.note).put("protocol", config.provider.name).put("endpoint", config.endpoint)
            .put("model", config.model).put("key", config.key).put("auth", config.auth.name)
            .put("fullEndpoint", config.fullEndpoint).put("models", JSONArray(profile.models)))
    } }).toString()

internal fun decodeAiProfiles(raw: String): AiProfiles {
    val json = JSONObject(raw)
    val array = json.getJSONArray("profiles")
    val profiles = (0 until array.length()).map { index ->
        val p = array.getJSONObject(index)
        val models = p.optJSONArray("models") ?: JSONArray()
        val name = p.getString("name")
        AiProfile(p.getString("id"), name, p.optString("preset", "custom"), p.optString("note"),
            AiConfig(AiProvider.valueOf(p.getString("protocol")), p.getString("endpoint"), p.getString("model"), p.getString("key"),
                AiAuth.valueOf(p.optString("auth", "BEARER")), p.optBoolean("fullEndpoint"), name),
            (0 until models.length()).map { models.getString(it) })
    }
    return AiProfiles(profiles, json.optString("active").takeIf { id -> profiles.any { it.id == id } })
}

internal fun CredentialStore.aiProfiles(): AiProfiles {
    read(PROFILES_KEY)?.let { return decodeAiProfiles(it) }
    // Read legacy profiles without deleting them; the first v2 edit commits the migration atomically.
    return legacyAiProfiles(AiProvider.entries.mapNotNull { protocol -> aiConfig(protocol)?.let { protocol to it } }.toMap(), aiProvider())
}

internal fun legacyAiProfiles(configs: Map<AiProvider, AiConfig>, selected: AiProvider): AiProfiles {
    val legacy = configs.map { (protocol, config) ->
        val name = if (protocol == AiProvider.GEMINI) "Gemini" else "OpenAI compatible"
        AiProfile("legacy-${protocol.name}", name, if (protocol == AiProvider.GEMINI) "gemini" else "custom", "",
            AiConfig(config.provider, config.endpoint, config.model, config.key, displayName = name), listOf(config.model))
    }
    val oldId = "legacy-${selected.name}"
    return AiProfiles(legacy, legacy.firstOrNull { it.id == oldId }?.id)
}
internal fun CredentialStore.saveAiProfiles(profiles: AiProfiles) {
    require(profiles.entries.map { it.id }.distinct().size == profiles.entries.size)
    require(profiles.activeId == null || profiles.entries.any { it.id == profiles.activeId })
    save(PROFILES_KEY, encodeAiProfiles(profiles))
    AiProvider.entries.forEach { delete(it.storageKey()) }
    delete("service:ai:active")
}
internal fun CredentialStore.activeAiConfig() = runCatching { aiProfiles().active?.config }.getOrNull()

internal fun aiModelsUrl(config: AiConfig): String {
    // Model selection must work before a model is entered, while still validating the key and endpoint.
    val candidate = AiConfig(config.provider, config.endpoint, "model", config.key, config.auth, config.fullEndpoint)
    val chat = aiRequestUrl(candidate)
    if (config.provider == AiProvider.GEMINI) return "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
    if (!chat.endsWith("/chat/completions")) throw AiFailure("ai_models_manual")
    return chat.removeSuffix("/chat/completions") + "/models"
}

internal data class AiModelPage(val models: List<String>, val nextToken: String?)
internal fun parseAiModels(protocol: AiProvider, raw: String): AiModelPage {
    val json = try { JSONObject(raw) } catch (_: Exception) { throw AiFailure("ai_bad_response") }
    val array = json.optJSONArray(if (protocol == AiProvider.GEMINI) "models" else "data") ?: throw AiFailure("ai_bad_response")
    val ids = (0 until array.length()).mapNotNull { index ->
        val model = array.optJSONObject(index) ?: return@mapNotNull null
        if (protocol == AiProvider.GEMINI) {
            val methods = model.optJSONArray("supportedGenerationMethods") ?: return@mapNotNull null
            if ((0 until methods.length()).none { methods.optString(it) == "generateContent" }) return@mapNotNull null
            model.optString("name").removePrefix("models/")
        } else model.optString("id")
    }.filter { Regex("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}").matches(it) }.distinct().sorted()
    return AiModelPage(ids, json.optString("nextPageToken").takeIf { it.isNotBlank() })
}

internal suspend fun fetchAiModels(config: AiConfig): List<String> {
    val base = aiModelsUrl(config)
    val found = linkedSetOf<String>()
    var token: String? = null
    repeat(10) {
        val url = base + (token?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        val page = parseAiModels(config.provider, aiHttp(config, url))
        found.addAll(page.models)
        token = if (config.provider == AiProvider.GEMINI) page.nextToken else null
        if (token == null) return found.sorted().also { if (it.isEmpty()) throw AiFailure("ai_models_empty") }
    }
    throw AiFailure("ai_models_too_many")
}
