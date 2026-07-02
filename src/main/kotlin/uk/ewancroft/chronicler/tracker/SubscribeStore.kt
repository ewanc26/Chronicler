package uk.ewancroft.chronicler.tracker

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class SubscriptionData(
    val subscribed: Boolean = true,
)

class SubscribeStore(private val dataPath: Path) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val subscriptions = mutableMapOf<String, SubscriptionData>()

    fun isSubscribed(uuid: String): Boolean {
        return subscriptions.getOrPut(uuid) { SubscriptionData(true) }.subscribed
    }

    fun toggle(uuid: String): Boolean {
        val current = subscriptions.getOrPut(uuid) { SubscriptionData(true) }
        val new = current.copy(subscribed = !current.subscribed)
        subscriptions[uuid] = new
        save()
        return new.subscribed
    }

    fun load() {
        try {
            if (Files.exists(dataPath)) {
                val text = dataPath.toFile().readText()
                val loaded = json.decodeFromString<Map<String, SubscriptionData>>(text)
                subscriptions.putAll(loaded)
            }
        } catch (_: Exception) {
        }
    }

    fun save() {
        try {
            Files.createDirectories(dataPath.parent)
            dataPath.toFile().writeText(json.encodeToString(subscriptions))
        } catch (_: Exception) {
        }
    }
}
