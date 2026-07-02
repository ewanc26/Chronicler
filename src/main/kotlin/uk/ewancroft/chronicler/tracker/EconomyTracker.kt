package uk.ewancroft.chronicler.tracker

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import uk.ewancroft.chronicler.config.TrackingConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType

class EconomyTracker(
    private val store: EventStore,
    private val tracking: TrackingConfig,
) : Listener {

    private var vaultHooked = false

    fun tryHook(): Boolean {
        if (!tracking.economy) return false
        return try {
            val rsp = Bukkit.getServicesManager().getRegistration(
                Class.forName("net.milkbowl.vault.economy.Economy")
            )
            vaultHooked = rsp != null
            vaultHooked
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: NoClassDefFoundError) {
            false
        }
    }

    fun isHooked(): Boolean = vaultHooked

    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) {
        try {
            val econClass = Class.forName("net.milkbowl.vault.economy.Economy")
            if (econClass.isInstance(event.provider.provider)) {
                vaultHooked = true
            }
        } catch (_: Exception) {
        }
    }

    @EventHandler
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        try {
            val econClass = Class.forName("net.milkbowl.vault.economy.Economy")
            if (econClass.isInstance(event.provider.provider)) {
                vaultHooked = false
            }
        } catch (_: Exception) {
        }
    }

    fun recordTransaction(playerName: String, playerUuid: String, worldName: String, amount: Double, cause: String) {
        if (!tracking.economy || !vaultHooked) return
        store.record(
            ChronicleEvent(
                type = EventType.TRADE,
                timestamp = System.currentTimeMillis(),
                playerName = playerName,
                playerUuid = playerUuid,
                world = worldName,
                details = mapOf(
                    "amount" to String.format("%.2f", amount),
                    "cause" to cause,
                ),
            )
        )
    }
}
