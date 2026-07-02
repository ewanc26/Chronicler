package uk.ewancroft.chronicler.tracker

import org.bukkit.entity.Player
import org.bukkit.entity.Firework
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.entity.FireworkExplodeEvent
import uk.ewancroft.chronicler.config.TrackingConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType

class CombatTracker(
    private val store: EventStore,
    private val tracking: TrackingConfig,
) : Listener {

    @EventHandler
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        if (!tracking.projectiles) return
        val projectile = event.entity
        val shooter = projectile.shooter as? Player ?: return
        store.record(
            ChronicleEvent(
                type = EventType.PROJECTILE_LAUNCH,
                timestamp = System.currentTimeMillis(),
                playerName = shooter.name,
                playerUuid = shooter.uniqueId.toString(),
                world = shooter.world.name,
                details = mapOf("projectile" to projectile.type.name.lowercase()),
            )
        )
    }

    @EventHandler
    fun onPotionSplash(event: PotionSplashEvent) {
        if (!tracking.projectiles) return
        val potion = event.potion
        val shooter = potion.shooter as? Player
        store.record(
            ChronicleEvent(
                type = EventType.POTION_THROW,
                timestamp = System.currentTimeMillis(),
                playerName = shooter?.name ?: "unknown",
                playerUuid = shooter?.uniqueId?.toString() ?: "",
                world = potion.world.name,
                details = mapOf(
                    "affected" to event.affectedEntities.size.toString(),
                ),
            )
        )
    }

    @EventHandler
    fun onFireworkExplode(event: FireworkExplodeEvent) {
        if (!tracking.misc) return
        val firework = event.entity as? Firework ?: return
        val shooter = firework.shooter as? Player
        store.record(
            ChronicleEvent(
                type = EventType.FIREWORK,
                timestamp = System.currentTimeMillis(),
                playerName = shooter?.name ?: "world",
                playerUuid = shooter?.uniqueId?.toString() ?: "",
                world = firework.world.name,
                details = mapOf<String, String>("effect" to (firework.fireworkMeta.effects?.firstOrNull()?.type?.name?.lowercase()?.take(50) ?: "unknown")),
            )
        )
    }
}
