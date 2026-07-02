package uk.ewancroft.chronicler.config

import org.bukkit.configuration.file.FileConfiguration

data class TrackingConfig(
    val deaths: Boolean,
    val kills: Boolean,
    val pvp: Boolean,
    val advancements: Boolean,
    val blocks: Boolean,
    val exploration: Boolean,
    val social: Boolean,
    val economy: Boolean,
)

data class LlmConfig(
    val enabled: Boolean,
    val provider: String,
    val model: String,
    val apiKey: String,
    val baseUrl: String,
    val ollamaUrl: String,
    val timeoutSeconds: Int,
    val systemPrompt: String,
)

data class NewspaperConfig(
    val title: String,
    val author: String,
    val storiesPerSection: Int,
    val showStatistics: Boolean,
)

data class WebConfig(
    val enabled: Boolean,
    val port: Int,
    val writeFiles: Boolean,
)

class PluginConfig(private val config: FileConfiguration) {

    var enabled: Boolean
    val schedule: String
    val publishDay: Int
    val publishHour: Int
    val tracking: TrackingConfig
    val eventLimit: Int
    val llm: LlmConfig
    val newspaper: NewspaperConfig
    val web: WebConfig
    val configVersion: Int

    init {
        enabled = config.getBoolean("enabled", true)
        schedule = config.getString("schedule", "WEEKLY") ?: "WEEKLY"
        publishDay = config.getInt("publish-day", 0)
        publishHour = config.getInt("publish-hour", 8)
        eventLimit = config.getInt("event-limit", 500).coerceAtLeast(100)
        llm = LlmConfig(
            enabled = config.getBoolean("llm.enabled", true),
            provider = (config.getString("llm.provider", "ollama") ?: "ollama").lowercase(),
            model = config.getString("llm.model", "llama3.2") ?: "llama3.2",
            apiKey = config.getString("llm.api-key", "") ?: "",
            baseUrl = (config.getString("llm.base-url", "https://openrouter.ai/api/v1") ?: "https://openrouter.ai/api/v1").trimEnd('/'),
            ollamaUrl = (config.getString("llm.ollama-url", "http://localhost:11434") ?: "http://localhost:11434").trimEnd('/'),
            timeoutSeconds = config.getInt("llm.timeout-seconds", 30).coerceIn(5, 120),
            systemPrompt = config.getString("llm.system-prompt", "") ?: "",
        )
        tracking = TrackingConfig(
            deaths = config.getBoolean("tracking.deaths", true),
            kills = config.getBoolean("tracking.kills", true),
            pvp = config.getBoolean("tracking.pvp", true),
            advancements = config.getBoolean("tracking.advancements", true),
            blocks = config.getBoolean("tracking.blocks", true),
            exploration = config.getBoolean("tracking.exploration", true),
            social = config.getBoolean("tracking.social", true),
            economy = config.getBoolean("tracking.economy", true),
        )
        newspaper = NewspaperConfig(
            title = config.getString("newspaper.title", "The Weekly Chronicle") ?: "The Weekly Chronicle",
            author = config.getString("newspaper.author", "Chronicler") ?: "Chronicler",
            storiesPerSection = config.getInt("newspaper.stories-per-section", 5).coerceIn(1, 20),
            showStatistics = config.getBoolean("newspaper.show-statistics", true),
        )
        web = WebConfig(
            enabled = config.getBoolean("web.enabled", true),
            port = config.getInt("web.port", 8080).coerceIn(1024, 65535),
            writeFiles = config.getBoolean("web.write-files", true),
        )
        configVersion = config.getInt("config-version", 1)
        migrateConfig()
    }

    private fun migrateConfig() {
        val version = config.getInt("config-version", 0)
        if (version >= 1) return
        config.set("config-version", 1)
    }
}
