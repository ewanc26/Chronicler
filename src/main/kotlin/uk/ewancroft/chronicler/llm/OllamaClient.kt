package uk.ewancroft.chronicler.llm

import uk.ewancroft.chronicler.config.LlmConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OllamaClient(private val config: LlmConfig) {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val defaultSystemPrompt = """
You are the editor of a Minecraft server newspaper. Write concise, vivid articles about in-game events. Use a dry, slightly humorous tone.

Each response must be exactly:
---HEADLINE
(headline, max 60 chars)
---BODY
(2-4 sentence article body)
""".trimIndent()

    data class ArticleResult(val headline: String, val body: String)

    fun generateArticle(sectionTitle: String, eventSummary: String): ArticleResult? {
        val prompt = buildString {
            appendLine("Write a newspaper article for the \"$sectionTitle\" section based on these events:")
            appendLine()
            append(eventSummary)
            appendLine()
            appendLine()
            append("Respond with ---HEADLINE followed by the headline, then ---BODY followed by the article body.")
        }

        val systemPrompt = config.systemPrompt.ifBlank { defaultSystemPrompt }
        val resolvedSystemPrompt = systemPrompt
            .replace("{series_title}", "The Weekly Chronicle")
            .replace("{server_name}", "this server")

        val requestBody = mapOf(
            "model" to config.model,
            "system" to resolvedSystemPrompt,
            "prompt" to prompt,
            "stream" to false,
            "options" to mapOf(
                "temperature" to 0.7,
                "max_tokens" to 300,
            ),
        )

        return try {
            val bodyJson = json.encodeToString(requestBody)
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return null

            val responseBody = json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(response.body())
            val text = (responseBody["response"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return null

            parseArticle(text.trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseArticle(text: String): ArticleResult? {
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

    fun isAvailable(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("${config.url}/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (_: Exception) {
            false
        }
    }
}
