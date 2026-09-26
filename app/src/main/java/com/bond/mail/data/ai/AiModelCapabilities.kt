package com.bond.mail.data.ai

internal enum class AiModelCapability { CHAT, UNVERIFIED, UNSUPPORTED }

// Model catalogs do not share a capability schema. Match bounded, known families;
// unfamiliar IDs stay visible rather than pretending their capabilities are known.
internal fun aiModelCapability(id: String): AiModelCapability {
    val name = id.substringAfterLast('/').lowercase(java.util.Locale.ROOT)
    val nonChat = Regex("(^|[-_.])(embedding[s]?|embed|rerank(er)?|tts|asr|whisper|imagen|flux|stable-diffusion|dall-e|sora|bge|gte|text2vec|sensevoice|cosyvoice|fish-speech|speech|transcribe|transcription)([-_.]|[0-9]|$)")
    if (name.startsWith("sensevoice") || name.startsWith("cosyvoice") || nonChat.containsMatchIn(name) || Regex("(^|[-_.])(image|audio|video)([-_.]|$)").containsMatchIn(name)) {
        return AiModelCapability.UNSUPPORTED
    }
    val chat = Regex("^(gpt-[0-9]|chatgpt-|o[134]([-_.]|$)|deepseek-(chat|reasoner|v[0-9]|r[0-9]|flash)|kimi-k[0-9]|moonshot-v[0-9]|mimo-v[0-9]|gemini-[0-9].*(flash|pro)|claude-)")
    val instruct = Regex("(^|[-_.])(instruct|chat)([-_.]|$)")
    return if (chat.containsMatchIn(name) || instruct.containsMatchIn(name)) AiModelCapability.CHAT
        else AiModelCapability.UNVERIFIED
}

internal fun selectableAiModels(ids: List<String>): List<String> = ids.distinct()
    .filter { aiModelCapability(it) != AiModelCapability.UNSUPPORTED }
    .sortedWith(compareBy<String> { aiModelCapability(it) != AiModelCapability.CHAT }.thenBy { it })
