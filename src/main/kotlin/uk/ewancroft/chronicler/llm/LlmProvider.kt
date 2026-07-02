package uk.ewancroft.chronicler.llm

data class ArticleResult(val headline: String, val body: String)

interface LlmProvider {
    fun generate(systemPrompt: String, sectionTitle: String, eventSummary: String): ArticleResult?
    fun isAvailable(): Boolean
    fun name(): String
}

fun buildPrompt(sectionTitle: String, eventSummary: String): String {
    return buildString {
        appendLine("Write a newspaper article for the \"$sectionTitle\" section based on these events:")
        appendLine()
        append(eventSummary)
        appendLine()
        appendLine()
        append("Respond with ---HEADLINE followed by the headline, then ---BODY followed by the article body.")
    }
}

fun parseArticle(text: String): ArticleResult? {
    val headlineMatch = Regex("---HEADLINE\\s*\\n(.+)", RegexOption.DOT_MATCHES_ALL).find(text)
    val bodyMatch = Regex("---BODY\\s*\\n(.+)", RegexOption.DOT_MATCHES_ALL).find(text)

    val headline = headlineMatch?.groupValues?.getOrNull(1)?.trim()
    val body = bodyMatch?.groupValues?.getOrNull(1)?.trim()

    if (headline != null && body != null) {
        return ArticleResult(headline, body)
    }

    val lines = text.lines().filter { it.isNotBlank() }
    if (lines.size >= 2) {
        return ArticleResult(
            headline = lines.first().trim().removePrefix("**").removeSuffix("**"),
            body = lines.drop(1).joinToString(" ").trim(),
        )
    }

    return null
}
