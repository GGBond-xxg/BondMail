package com.bond.mail.data.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import kotlin.coroutines.coroutineContext

internal data class TranslatedHtmlMail(val subject: String, val html: String)

/** Translate text nodes, never attributes or markup. The normal renderer still sanitizes the result. */
internal suspend fun translateHtmlMail(
    subject: String, html: String?, plain: String,
    onProgress: (Int, Int) -> Unit = { _, _ -> },
    prepareDocument: (org.jsoup.nodes.Document) -> Unit = {},
    translate: suspend (String) -> String,
): TranslatedHtmlMail = withContext(Dispatchers.Default) {
    val document = if (html.isNullOrBlank()) Jsoup.parse("<html><body></body></html>").apply {
        body().appendElement("div").attr("style", "white-space:pre-wrap").text(plain)
    } else Jsoup.parse(html)
    prepareDocument(document)
    val excluded = setOf("script", "style", "noscript", "template", "svg", "math", "code", "pre")
    val hiddenStyle = Regex("(?i)(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden|opacity\\s*:\\s*0(?:\\.0*)?\\s*(?:;|$))")
    val nodes = document.body().getAllElements().filter { element ->
        (listOf(element) + element.parents()).none {
            it.normalName() in excluded || it.hasAttr("hidden") ||
                it.attr("aria-hidden").equals("true", true) || it.attr("translate").equals("no", true) ||
                hiddenStyle.containsMatchIn(it.attr("style"))
        }
    }.flatMap { it.textNodes() }.filter { node ->
        val text = node.wholeText.trim()
        text.any(Char::isLetter) && !text.matches(Regex("(?i)(?:https?://|mailto:|www\\.)\\S+|[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
    }
    val texts = buildList {
        if (subject.isNotBlank()) add(subject.trim())
        nodes.forEach { add(it.wholeText.trim()) }
    }.distinct()
    if (texts.isEmpty()) throw TranslationFailure("translation_empty")
    val translated = mutableMapOf<String, String>()
    onProgress(0, texts.size)
    // Sequential requests respect provider rate limits. Repeated navigation/footer text is sent once.
    texts.forEachIndexed { index, text ->
        coroutineContext.ensureActive()
        translated[text] = translate(text)
        onProgress(index + 1, texts.size)
    }
    nodes.forEach { node ->
        val original = node.wholeText
        node.text(original.takeWhile(Char::isWhitespace) + translated.getValue(original.trim()) + original.takeLastWhile(Char::isWhitespace))
    }
    TranslatedHtmlMail(translated[subject.trim()] ?: subject, document.outerHtml())
}
