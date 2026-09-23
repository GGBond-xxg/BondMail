package com.bond.mail

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bond.mail.data.ai.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Offline contract checks: no API keys, network, mailbox access or billable requests. */
@RunWith(AndroidJUnit4::class)
class MailAiContractTest {
    @Test fun modelListsAreParsedFilteredAndDeduplicated() {
        val compatible = parseAiModels(AiProvider.COMPATIBLE, """{"data":[{"id":"model-b"},{"id":"model-a"},{"id":"model-a"},{"id":"bad model"}]}""")
        assertEquals(listOf("model-a", "model-b"), compatible.models)
        val gemini = parseAiModels(AiProvider.GEMINI, """{"models":[{"name":"models/chat-model","supportedGenerationMethods":["generateContent"]},{"name":"models/embed","supportedGenerationMethods":["embedContent"]}],"nextPageToken":"next-page"}""")
        assertEquals(listOf("chat-model"), gemini.models)
        assertEquals("next-page", gemini.nextToken)
    }
    @Test fun multipleProfilesRoundTripWithoutReplacingOtherKeys() {
        val first = AiProfile("one", "Work", "custom", "note", AiConfig(AiProvider.COMPATIBLE, "https://example.com/v1", "model-a", "fake-key-a"), listOf("model-a", "model-b"))
        val second = AiProfile("two", "Personal", "custom", "", AiConfig(AiProvider.COMPATIBLE, "https://example.org/custom", "model-c", "fake-key-b", AiAuth.X_API_KEY, true))
        val restored = decodeAiProfiles(encodeAiProfiles(AiProfiles(listOf(first, second), "two")))
        assertEquals(2, restored.entries.size)
        assertEquals("fake-key-a", restored.entries[0].config.key)
        assertEquals("Personal", restored.active!!.name)
        assertEquals(AiAuth.X_API_KEY, restored.active!!.config.auth)
        assertTrue(restored.active!!.config.fullEndpoint)
        assertEquals(listOf("model-a", "model-b"), restored.entries[0].models)
        assertNull(decodeAiProfiles(encodeAiProfiles(AiProfiles(listOf(first), null))).active)
    }
    @Test fun migratesLegacyEncryptedKeysAndDoesNotResurrectDeletedProfiles() {
        val app = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefsName = "ai-migration-test-" + java.util.UUID.randomUUID()
        val isolated = object : android.content.ContextWrapper(app) {
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = app.getSharedPreferences(prefsName, mode)
        }
        val store = com.bond.mail.data.security.CredentialStore(isolated)
        try {
            store.saveAi(AiConfig(AiProvider.COMPATIBLE, "https://example.com/v1", "model", "fake-legacy-key"))
            val migrated = store.aiProfiles()
            assertEquals("fake-legacy-key", migrated.active!!.config.key)
            store.saveAiProfiles(migrated)
            assertNull(store.aiConfig(AiProvider.COMPATIBLE))
            assertEquals("fake-legacy-key", store.activeAiConfig()!!.key)
            store.saveAiProfiles(AiProfiles(emptyList(), null))
            assertTrue(store.aiProfiles().entries.isEmpty())
            assertNull(store.activeAiConfig())
        } finally { app.getSharedPreferences(prefsName, 0).edit().clear().commit() }
    }
    @Test fun compatibleRequestContainsTextOnlyAndNeverKey() {
        val config = AiConfig(AiProvider.COMPATIBLE, "https://example.com/v1", "test-model", "test-secret")
        val raw = aiRequestBody(config, aiMessages("Subject", "<sample>quoted mail", "zh-CN", emptyList(), "Summary"))
        val json = JSONObject(raw)
        assertEquals("test-model", json.getString("model"))
        assertFalse(json.getBoolean("stream"))
        assertEquals("system", json.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertFalse(json.has("tools"))
        assertFalse(raw.contains("test-secret"))
        assertEquals("<sample>quoted mail", json.getJSONArray("messages").getJSONObject(1).getString("content").substringAfter("\n\n").substringBefore("\nEND"))
    }
    @Test fun geminiMergesAdjacentUserContentAndMapsHistoryRoles() {
        val config = AiConfig(AiProvider.GEMINI, "", "test-model", "test-secret")
        val history = listOf(AiTurn("user", "Summary"), AiTurn("assistant", "Mail summary"))
        val json = JSONObject(aiRequestBody(config, aiMessages("Subject", "Original body", "en", history, "Follow up")))
        assertTrue(json.has("systemInstruction"))
        val contents = json.getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertTrue(contents.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text").endsWith("Summary"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("user", contents.getJSONObject(2).getString("role"))
        assertFalse(json.has("tools"))
    }
    @Test fun parsesCompatibleTextAndRejectsIncompleteOrMissingOutput() {
        assertEquals("Answer", parseAiResponse(AiProvider.COMPATIBLE, """{"choices":[{"message":{"content":" Answer "},"finish_reason":"stop"}]}"""))
        listOf("{}", "not json", """{"choices":[{"message":{"content":null}}]}""",
            """{"choices":[{"message":{"content":"partial"},"finish_reason":"length"}]}""").forEach { raw ->
            assertThrows(AiFailure::class.java) { parseAiResponse(AiProvider.COMPATIBLE, raw) }
        }
    }
    @Test fun geminiExcludesThinkingAndRejectsBlockedAndTruncatedOutput() {
        assertEquals("Answer", parseAiResponse(AiProvider.GEMINI,
            """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"private reasoning","thought":true},{"text":"Answer"}]}}]}"""))
        listOf("SAFETY", "MAX_TOKENS").forEach { reason ->
            assertThrows(AiFailure::class.java) { parseAiResponse(AiProvider.GEMINI,
                """{"candidates":[{"finishReason":"$reason","content":{"parts":[{"text":"partial"}]}}]}""") }
        }
    }
}
