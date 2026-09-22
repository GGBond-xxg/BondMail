package com.bond.mail.data.mail

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class BodyTranslationTest {
    @Test fun translatesSubjectAndBodyAsSeparateFields(): Unit = runBlocking {
        val sent = mutableListOf<String>()
        val result = translateMailText("Interest update", "Subject: unchanged delimiter\nBody") {
            sent += it
            "Translated: $it"
        }
        assertEquals(listOf("Interest update", "Subject: unchanged delimiter\nBody"), sent)
        assertEquals("Translated: Interest update", result.subject)
        assertEquals("Translated: Subject: unchanged delimiter\nBody", result.body)
    }

    @Test fun supportsMissingSubjectAndSubjectOnlyWithoutEmptyRequests(): Unit = runBlocking {
        val sent = mutableListOf<String>()
        val translate: suspend (String) -> String = { sent += it; "译文" }
        assertEquals(TranslatedMailText("", "译文"), translateMailText("", "Body", translate))
        assertEquals(TranslatedMailText("译文", ""), translateMailText("Subject", "", translate))
        assertEquals(listOf("Body", "Subject"), sent)
    }

    @Test fun doesNotReturnPartialMailWhenBodyTranslationFails(): Unit = runBlocking {
        var result: TranslatedMailText? = null
        val failure = runCatching {
            result = translateMailText("Subject", "Body") {
                if (it == "Body") throw TranslationFailure("translation_network")
                "标题"
            }
        }.exceptionOrNull()
        assertNull(result)
        assertEquals("translation_network", failure?.message)
    }

    @Test fun extractsBodyWithoutScriptsOrHiddenContent() {
        val body = translationBodyText("<html><head><title>Subject</title></head><body><p>Hello</p>" +
            "<p>World &amp; friends</p><script>secret()</script><div hidden>Hidden</div></body></html>", "short")
        assertTrue(body.contains("Hello"))
        assertTrue(body.contains("World & friends"))
        assertTrue(body.contains("\n"))
        assertFalse(body.contains("Subject"))
        assertFalse(body.contains("secret"))
        assertFalse(body.contains("Hidden"))
    }

    @Test fun chunksPreserveEveryCharacterAndSurrogatePairs() {
        val text = "a".repeat(1499) + "😀" + "你好 paragraph.\n".repeat(500)
        val chunks = translationChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 1500 && !Character.isHighSurrogate(it.last()) })
        assertTrue(translationChunks("").isEmpty())
    }

    @Test fun requestEncodingPreservesUnicodeAndReservedCharacters() {
        assertEquals("a%20b%2B%2A~%26%E4%BD%A0", aliyunEncode("a b+*~&你"))
        val form = aliyunSignedForm(mapOf("B" to "a b", "A" to "+"), "test-secret")
        assertEquals("A=%2B&B=a%20b&Signature=Qm7Gmz%2BukerL0YmtAneRx7oXU4k%3D", form)
        assertFalse(form.contains("test-secret"))
    }

    @Test fun youdaoSignatureInputUsesDocumentedTruncation() {
        assertEquals("hello", youdaoInput("hello"))
        assertEquals("0123456789233456789012", youdaoInput("01234567890123456789012"))
    }
}
