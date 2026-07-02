package uk.ewancroft.chronicler.task

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import uk.ewancroft.chronicler.news.Newspaper
import java.util.logging.Logger

class HeadlineTicker(
    private val plugin: JavaPlugin,
    private val logger: Logger,
    private val intervalTicks: Long,
    private val getLatestNewspaper: () -> Newspaper?,
) {

    private var task: ScheduledTask? = null

    fun start() {
        if (intervalTicks <= 0) return
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            { _ -> tick() },
            20L * 30L,
            intervalTicks,
        )
        logger.info("Headline ticker started (every ${intervalTicks / 20}s)")
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    private fun tick() {
        val newspaper = getLatestNewspaper() ?: return
        val allStories = newspaper.sections.flatMap { it.stories }
        if (allStories.isEmpty()) return

        val story = allStories.random()
        val headline = story.headline
        val sectionTitle = newspaper.sections.firstOrNull { it.stories.contains(story) }?.title ?: "Chronicle"

        val msg = Component.text()
            .append(Component.text("[$sectionTitle] ", TextColor.color(0xFFAA00)))
            .append(Component.text(headline, NamedTextColor.WHITE))
            .build()

        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendActionBar(msg)
        }
    }
}
