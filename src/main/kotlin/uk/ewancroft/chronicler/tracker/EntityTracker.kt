package uk.ewancroft.chronicler.tracker

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityBreedEvent
import org.bukkit.event.entity.EntityTameEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.entity.SlimeSplitEvent
import org.bukkit.event.entity.CreeperPowerEvent
import org.bukkit.event.entity.SheepDyeWoolEvent
import uk.ewancroft.chronicler.config.TrackingConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType

class EntityTracker(
    private val store: EventStore,
    private val tracking: TrackingConfig,
) : Listener {

    @EventHandler
    fun onTame(event: EntityTameEvent) {
        if (!tracking.entities) return
        val player = event.owner as? org.bukkit.entity.Player ?: return
        store.record(
            ChronicleEvent(
                type = EventType.TAME,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
                details = mapOf("entity" to event.entity.type.name.lowercase()),
            )
        )
    }

    @EventHandler
    fun onBreed(event: EntityBreedEvent) {
        if (!tracking.entities) return
        val breeder = event.breeder as? org.bukkit.entity.Player ?: return
        store.record(
            ChronicleEvent(
                type = EventType.BREED,
                timestamp = System.currentTimeMillis(),
                playerName = breeder.name,
                playerUuid = breeder.uniqueId.toString(),
                world = breeder.world.name,
                details = mapOf(
                    "entity" to event.entity.type.name.lowercase(),
                    "mother" to event.mother.type.name.lowercase(),
                    "father" to event.father.type.name.lowercase(),
                ),
            )
        )
    }

    @EventHandler
    fun onTransform(event: EntityTransformEvent) {
        if (!tracking.entities) return
        val entity = event.entity
        val transformed = event.transformedEntity
        store.record(
            ChronicleEvent(
                type = EventType.ENTITY_TRANSFORM,
                timestamp = System.currentTimeMillis(),
                playerName = "world",
                playerUuid = "",
                world = entity.world.name,
                details = mapOf(
                    "from" to entity.type.name.lowercase(),
                    "to" to transformed.type.name.lowercase(),
                    "reason" to event.transformReason.name.lowercase(),
                ),
            )
        )
    }

    @EventHandler
    fun onSlimeSplit(event: SlimeSplitEvent) {
        if (!tracking.misc) return
        store.record(
            ChronicleEvent(
                type = EventType.SLIME_SPLIT,
                timestamp = System.currentTimeMillis(),
                playerName = "world",
                playerUuid = "",
                world = event.entity.world.name,
                details = mapOf("count" to event.count.toString()),
            )
        )
    }

    @EventHandler
    fun onCreeperPower(event: CreeperPowerEvent) {
        if (!tracking.misc) return
        store.record(
            ChronicleEvent(
                type = EventType.CREEPER_POWER,
                timestamp = System.currentTimeMillis(),
                playerName = "world",
                playerUuid = "",
                world = event.entity.world.name,
                details = mapOf(
                    "cause" to event.cause.name.lowercase(),
                ),
            )
        )
    }

    @EventHandler
    fun onSheepDye(event: SheepDyeWoolEvent) {
        if (!tracking.misc) return
        val player = event.player
        store.record(
            ChronicleEvent(
                type = EventType.SHEEP_DYE,
                timestamp = System.currentTimeMillis(),
                playerName = player?.name ?: "unknown",
                playerUuid = player?.uniqueId?.toString() ?: "",
                world = event.entity.world.name,
                details = mapOf("colour" to event.color.name.lowercase()),
            )
        )
    }
}
