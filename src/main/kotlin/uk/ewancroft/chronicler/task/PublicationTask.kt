package uk.ewancroft.chronicler.task

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import uk.ewancroft.chronicler.config.PluginConfig
import uk.ewancroft.chronicler.news.ArchiveStore
import uk.ewancroft.chronicler.news.BookRenderer
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.Newspaper
import uk.ewancroft.chronicler.news.NewspaperGenerator
import uk.ewancroft.chronicler.news.WebRenderer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Calendar
import java.util.logging.Logger

class PublicationTask(
    private val plugin: JavaPlugin,
    private val config: PluginConfig,
    private val store: EventStore,
    private val generator: NewspaperGenerator,
    private val bookRenderer: BookRenderer,
    private val webRenderer: WebRenderer?,
    private val archiveStore: ArchiveStore?,
    private val logger: Logger,
) {

    private var issueNumber = 0
    private var lastPublishTime = 0L
    private var latestBook: ItemStack? = null
    private var latestNewspaper: Newspaper? = null
    private var scheduledTask: ScheduledTask? = null
    private val stateFile: Path = plugin.dataFolder.toPath().resolve("publish-state.json")

    fun start() {
        loadState()

        if (issueNumber == 0 && lastPublishTime == 0L && store.allEvents().isNotEmpty()) {
            logger.info("No prior issues found — publishing issue #0 with ${store.allEvents().size} backlogged events.")
            doPublish(isIssueZero = true)
        }

        val schedule = config.schedule
        val interval = when (schedule.uppercase()) {
            "HOURLY" -> 20L * 60L * 60L
            "DAILY" -> 20L * 60L * 60L * 24L
            "WEEKLY" -> 20L * 60L * 60L * 24L * 7L
            "BIWEEKLY" -> 20L * 60L * 60L * 24L * 14L
            "MONTHLY" -> 20L * 60L * 60L * 24L * 30L
            else -> {
                val ticks = schedule.toLongOrNull()
                if (ticks != null && ticks > 0) ticks else 20L * 60L * 60L * 24L * 7L
            }
        }

        scheduledTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            { _ -> checkAndPublish() },
            20L * 60L,
            interval,
        )

        logger.info("Publication task scheduled (${config.schedule.lowercase()})")
    }

    fun stop() {
        scheduledTask?.cancel()
        scheduledTask = null
        saveState()
    }

    fun publishNow() {
        doPublish()
    }

    fun getIssueNumber(): Int = issueNumber
    fun getLastPublishTime(): Long = lastPublishTime
    fun getLatestBook(): ItemStack? = latestBook?.clone()
    fun getLatestNewspaper(): Newspaper? = latestNewspaper

    private fun checkAndPublish() {
        if (!config.enabled) return

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val shouldPublish = when (config.schedule.uppercase()) {
            "DAILY" -> true
            "WEEKLY" -> calendar.get(Calendar.DAY_OF_WEEK) == dayOfWeekToCalendar(config.publishDay)
            "BIWEEKLY" -> calendar.get(Calendar.DAY_OF_WEEK) == dayOfWeekToCalendar(config.publishDay) &&
                (calendar.get(Calendar.WEEK_OF_YEAR) % 2 == 0)
            "MONTHLY" -> calendar.get(Calendar.DAY_OF_MONTH) == config.publishDay.coerceIn(1, 28)
            "HOURLY" -> true
            else -> true
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val correctHour = config.publishHour

        if (shouldPublish && hour == correctHour && (now - lastPublishTime) > 3600_000L) {
            doPublish()
        }
    }

    private fun doPublish(isIssueZero: Boolean = false) {
        val toTime = System.currentTimeMillis()
        val fromTime = if (isIssueZero) 0L else lastPublishTime
        val number = if (isIssueZero) 0 else issueNumber + 1

        try {
            val newspaper = generator.generate(number, fromTime, toTime)
            val book = bookRenderer.renderToBook(newspaper)

            latestNewspaper = newspaper
            latestBook = book
            archiveStore?.archive(newspaper)

            Bukkit.getOnlinePlayers().forEach { player ->
                val remaining = player.inventory.addItem(book.clone())
                if (remaining.isEmpty()) {
                    player.sendMessage(
                        net.kyori.adventure.text.Component.text("§6[Chronicler] §e${config.newspaper.title} #$number has arrived in your inventory!")
                    )
                } else {
                    player.world.dropItem(player.location, book.clone())
                    player.sendMessage(
                        net.kyori.adventure.text.Component.text("§6[Chronicler] §e${config.newspaper.title} #$number dropped at your feet (inventory full).")
                    )
                }
            }

            webRenderer?.renderAndServe(newspaper)

            lastPublishTime = toTime
            if (!isIssueZero) {
                issueNumber = number
            }
            store.clear()
            saveState()

            logger.info("Published issue #$number")
        } catch (e: Exception) {
            logger.warning("Failed to publish issue #$number: ${e.message}")
        }
    }

    private fun loadState() {
        try {
            if (Files.exists(stateFile)) {
                val text = stateFile.toFile().readText()
                val lines = text.lines()
                if (lines.size >= 2) {
                    issueNumber = lines[0].toIntOrNull() ?: 0
                    lastPublishTime = lines[1].toLongOrNull() ?: System.currentTimeMillis()
                }
            }
        } catch (_: Exception) {
            lastPublishTime = 0L
        }
    }

    private fun saveState() {
        try {
            Files.createDirectories(stateFile.parent)
            stateFile.toFile().writeText("$issueNumber\n$lastPublishTime")
        } catch (_: Exception) {
        }
    }

    private fun dayOfWeekToCalendar(day: Int): Int {
        return when (day) {
            0 -> Calendar.MONDAY
            1 -> Calendar.TUESDAY
            2 -> Calendar.WEDNESDAY
            3 -> Calendar.THURSDAY
            4 -> Calendar.FRIDAY
            5 -> Calendar.SATURDAY
            6 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
    }
}
