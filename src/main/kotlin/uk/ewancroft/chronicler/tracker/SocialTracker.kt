package uk.ewancroft.chronicler.tracker

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import uk.ewancroft.chronicler.config.TrackingConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType

class SocialTracker(
    private val store: EventStore,
    private val tracking: TrackingConfig,
) : Listener {

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!tracking.social) return
        val player = event.player
        store.record(
            ChronicleEvent(
                type = EventType.PLAYER_LEAVE,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
            )
        )
    }
}
