package uk.ewancroft.chronicler.tracker

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import uk.ewancroft.chronicler.config.TrackingConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType

class PrivateMessageTracker(
    private val store: EventStore,
    private val tracking: TrackingConfig,
) : Listener {

    @EventHandler
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        if (!tracking.social) return
        val cmd = event.message.lowercase()
        if (!cmd.startsWith("/msg ") && !cmd.startsWith("/tell ") && !cmd.startsWith("/w ")) return

        val cmdName: String = (cmd.split(" ").firstOrNull()?.removePrefix("/") ?: "msg")
        val player = event.player
        store.record(
            ChronicleEvent(
                type = EventType.MESSAGE_SENT,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
                details = mapOf<String, String>("command" to cmdName),
            )
        )
    }
}
