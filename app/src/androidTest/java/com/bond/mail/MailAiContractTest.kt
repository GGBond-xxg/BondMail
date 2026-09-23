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
