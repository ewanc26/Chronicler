package uk.ewancroft.chronicler.tracker

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.event.weather.ThunderChangeEvent
import org.bukkit.event.raid.RaidTriggerEvent
import org.bukkit.event.world.StructureGrowEvent
import uk.ewancroft.chronicler.config.TrackingConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType

class WorldTracker(
    private val store: EventStore,
    private val tracking: TrackingConfig,
) : Listener {

    @EventHandler
    fun onPortal(event: PlayerPortalEvent) {
        if (!tracking.portals) return
        val player = event.player
        val fromEnv = event.from.world?.environment?.name?.lowercase() ?: "unknown"
        val toEnv = event.to.world?.environment?.name?.lowercase() ?: "unknown"
        store.record(
            ChronicleEvent(
                type = EventType.PORTAL,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
                details = mapOf(
                    "from" to fromEnv,
                    "to" to toEnv,
                    "cause" to event.cause.name.lowercase(),
                ),
            )
        )
    }

    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        if (!tracking.teleport) return
        val player = event.player
        val cause = event.cause.name.lowercase()
        // Skip internal teleports from plugins
        if (cause == "unknown" || cause == "plugin") return
        store.record(
            ChronicleEvent(
                type = EventType.TELEPORT,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
                details = mapOf(
                    "cause" to cause,
                    "from" to "${event.from.blockX},${event.from.blockY},${event.from.blockZ}",
                    "to" to "${event.to.blockX},${event.to.blockY},${event.to.blockZ}",
                ),
            )
        )
    }

    @EventHandler
    fun onExplosion(event: EntityExplodeEvent) {
        if (!tracking.explosions) return
        val entity = event.entity
        val source = entity.type.name.lowercase()
        store.record(
            ChronicleEvent(
                type = EventType.EXPLOSION,
                timestamp = System.currentTimeMillis(),
                playerName = "world",
                playerUuid = "",
                world = event.location.world?.name ?: "unknown",
                details = mapOf(
                    "source" to source,
                    "blocks" to event.blockList().size.toString(),
                ),
            )
        )
    }

    @EventHandler
    fun onLightning(event: LightningStrikeEvent) {
        if (!tracking.weather) return
        val bolt = event.lightning
        store.record(
            ChronicleEvent(
                type = EventType.LIGHTNING,
                timestamp = System.currentTimeMillis(),
                playerName = "world",
                playerUuid = "",
                world = bolt.world.name,
                details = mapOf(
                    "x" to bolt.location.blockX.toString(),
                    "z" to bolt.location.blockZ.toString(),
                ),
            )
        )
    }

    @EventHandler
    fun onWeatherChange(event: WeatherChangeEvent) {
        if (!tracking.weather) return
        store.record(
            ChronicleEvent(
                type = EventType.WEATHER,
                timestamp = System.currentTimeMillis(),
                playerName = "world",
                playerUuid = "",
                world = event.world.name,
                details = mapOf("raining" to event.toWeatherState().toString()),
            )
        )
    }

    @EventHandler
    fun onThunderChange(event: ThunderChangeEvent) {
        if (!tracking.weather) return
        store.record(
            ChronicleEvent(
                type = EventType.THUNDER,
                timestamp = System.currentTimeMillis(),
                playerName = "world",
                playerUuid = "",
                world = event.world.name,
                details = mapOf("thundering" to event.toThunderState().toString()),
            )
        )
    }

    @EventHandler
    fun onRaid(event: RaidTriggerEvent) {
        if (!tracking.raids) return
        val player = event.player
        store.record(
            ChronicleEvent(
                type = EventType.RAID,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
                details = mapOf(
                    "location" to "${event.raid.location.blockX},${event.raid.location.blockZ}",
                ),
            )
        )
    }

    @EventHandler
    fun onStructureGrow(event: StructureGrowEvent) {
        if (!tracking.misc) return
        store.record(
            ChronicleEvent(
                type = EventType.STRUCTURE_GROW,
                timestamp = System.currentTimeMillis(),
                playerName = event.player?.name ?: "nature",
                playerUuid = event.player?.uniqueId?.toString() ?: "",
                world = event.world.name,
                details = mapOf(
                    "species" to event.species.name.lowercase(),
                    "blocks" to event.blocks.size.toString(),
                ),
            )
        )
    }
}
