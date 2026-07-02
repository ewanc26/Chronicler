package uk.ewancroft.chronicler.tracker

import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import uk.ewancroft.chronicler.config.PluginConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType

class BreakingNewsTracker(
    private val store: EventStore,
    private val config: PluginConfig,
) : Listener {

    private val playersInEnd = mutableSetOf<String>()

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        if (event.player.world.environment == World.Environment.THE_END && event.from.environment != World.Environment.THE_END) {
            val uuid = event.player.uniqueId.toString()
            if (uuid !in playersInEnd) {
                playersInEnd.add(uuid)
                store.record(
                    ChronicleEvent(
                        type = EventType.END_ENTER,
                        timestamp = System.currentTimeMillis(),
                        playerName = event.player.name,
                        playerUuid = uuid,
                        world = event.player.world.name,
                        details = mapOf<String, String>(
                            "fromWorld" to event.from.name,
                            (if (playersInEnd.size == 1) "firstTime" else "count") to playersInEnd.size.toString(),
                        ),
                    )
                )
            }
        }
    }
}
