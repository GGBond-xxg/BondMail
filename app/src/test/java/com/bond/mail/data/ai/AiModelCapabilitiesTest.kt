package com.bond.mail.data.ai

import org.junit.Assert.*
import org.junit.Test

class AiModelCapabilitiesTest {
    @Test fun hidesKnownNonChatFamilies() {
        listOf("BAAI/bge-m3", "BAAI/bge-reranker-v2-m3", "Qwen/Qwen3-Embedding-8B",
            "Qwen/Qwen3-Reranker-8B", "black-forest-labs/FLUX.1-schnell", "stabilityai/stable-diffusion-xl-base-1.0",
            "FunAudioLLM/SenseVoiceSmall", "FunAudioLLM/CosyVoice2-0.5B", "gpt-4o-mini-tts",
            "whisper-1", "gemini-2.5-flash-image", "gemini-2.5-flash-preview-native-audio",
            "dall-e-3", "sora-2").forEach {
            assertEquals(it, AiModelCapability.UNSUPPORTED, aiModelCapability(it))
        }
    }
    @Test fun preservesChatAndMultimodalTextModels() {
        listOf("deepseek-ai/DeepSeek-V3.2", "Qwen/Qwen2.5-VL-72B-Instruct", "gpt-4o",
            "kimi-k2.6", "gemini-2.5-pro", "mimo-v2.5").forEach {
            assertEquals(it, AiModelCapability.CHAT, aiModelCapability(it))
        }
    }
    @Test fun unknownIdsRemainUnverifiedWithoutSubstringFalsePositives() {
        listOf("company/model-b", "my-whispering-assistant", "chatty", "my-embeddinghelper").forEach {
            assertEquals(it, AiModelCapability.UNVERIFIED, aiModelCapability(it))
        }
    }
    @Test fun filtersCachedAndFetchedIdsAndSortsChatFirst() {
        assertEquals(listOf("gpt-4o", "aaa-unknown"), selectableAiModels(
            listOf("aaa-unknown", "BAAI/bge-m3", "gpt-4o", "aaa-unknown")))
    }
}
