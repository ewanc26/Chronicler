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
        return synchronized(subscriptions) { subscriptions[uuid]?.subscribed ?: true }
    }

    fun toggle(uuid: String): Boolean {
        val new = synchronized(subscriptions) {
            val current = subscriptions[uuid] ?: SubscriptionData(true)
            current.copy(subscribed = !current.subscribed).also { subscriptions[uuid] = it }
        }
        save()
        return new.subscribed
    }

    fun load() {
        try {
            if (Files.exists(dataPath)) {
                val text = dataPath.toFile().readText()
                val loaded = json.decodeFromString<Map<String, SubscriptionData>>(text)
                synchronized(subscriptions) {
                    subscriptions.clear()
                    subscriptions.putAll(loaded)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun save() {
        try {
            Files.createDirectories(dataPath.parent)
            val snapshot = synchronized(subscriptions) { subscriptions.toMap() }
            dataPath.toFile().writeText(json.encodeToString(snapshot))
        } catch (_: Exception) {
        }
    }
}
