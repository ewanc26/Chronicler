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
    val trimmed = text.trim()
    if (trimmed.isBlank()) return null

    val hasHeadline = trimmed.contains("---HEADLINE")
    val hasBody = trimmed.contains("---BODY")

    if (hasHeadline && hasBody) {
        val headline = trimmed
            .substringAfter("---HEADLINE")
            .substringBefore("---BODY")
            .trim()
            .lineSequence()
            .firstOrNull()
            ?.removePrefix("**")
            ?.removeSuffix("**")
            ?.trim()
        val body = trimmed
            .substringAfter("---BODY")
            .trim()
        if (!headline.isNullOrBlank() && !body.isBlank()) {
            return ArticleResult(headline, body)
        }
    }

    val lines = trimmed.lines().filter { it.isNotBlank() }
    if (lines.size >= 2) {
        return ArticleResult(
            headline = lines.first().trim().removePrefix("**").removeSuffix("**"),
            body = lines.drop(1).joinToString(" ").trim(),
        )
    }

    return null
}
