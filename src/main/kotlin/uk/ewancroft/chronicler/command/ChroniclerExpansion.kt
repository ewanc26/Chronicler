package uk.ewancroft.chronicler.command

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import uk.ewancroft.chronicler.Chronicler
import uk.ewancroft.chronicler.tracker.SessionStore

class ChroniclerExpansion(
    private val plugin: Chronicler,
    private val sessionStore: SessionStore?,
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "chronicler"

    override fun getAuthor(): String = "ewanc26"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String {
        val status = plugin.getStatus()
        val args = params.split("_")

        return when (args[0].lowercase()) {
            "issue" -> status.issueNumber.toString()
            "events" -> status.eventCount.toString()
            "schedule" -> status.schedule
            "llm" -> if (status.llmAvailable) "online" else "offline"
            "web" -> if (status.webEnabled) "enabled" else "disabled"
            "last_publish" -> {
                val time = plugin.getLastPublishTime()
                if (time > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(time))
                else "never"
            }
            "players" -> {
                val newspaper = plugin.getLatestNewspaper()
                if (newspaper != null) {
                    newspaper.sections.flatMap { s -> s.stories.flatMap { st -> st.players } }.distinct().size.toString()
                } else "0"
            }
            "kills" -> {
                val newspaper = plugin.getLatestNewspaper()
                if (newspaper != null) {
                    val kills = newspaper.sections.flatMap { s ->
                        s.stories.filter { it.eventType?.name == "KILL" || it.eventType?.name == "PVP_KILL" }
                    }
                    kills.size.toString()
                } else "0"
            }
            "deaths" -> {
                val newspaper = plugin.getLatestNewspaper()
                if (newspaper != null) {
                    val deaths = newspaper.sections.flatMap { s ->
                        s.stories.filter { it.eventType?.name == "DEATH" }
                    }
                    deaths.size.toString()
                } else "0"
            }
            "advancements" -> {
                val newspaper = plugin.getLatestNewspaper()
                if (newspaper != null) {
                    val adv = newspaper.sections.flatMap { s ->
                        s.stories.filter { it.eventType?.name == "ADVANCEMENT" }
                    }
                    adv.size.toString()
                } else "0"
            }
            "playtime" -> {
                if (player != null && sessionStore != null) {
                    val data = sessionStore.get(player.uniqueId.toString())
                    if (data != null) {
                        val minutes = data.totalPlaytimeTicks / (20 * 60)
                        "${minutes / 60}h ${minutes % 60}m"
                    } else "0h 0m"
                } else "0h 0m"
            }
            "streak" -> {
                if (player != null && sessionStore != null) {
                    val data = sessionStore.get(player.uniqueId.toString())
                    data?.currentStreak?.toString() ?: "0"
                } else "0"
            }
            "sessions" -> {
                if (player != null && sessionStore != null) {
                    val data = sessionStore.get(player.uniqueId.toString())
                    data?.sessionCount?.toString() ?: "0"
                } else "0"
            }
            else -> "unknown"
        }
    }
}
