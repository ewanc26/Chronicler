package uk.ewancroft.chronicler.task

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import uk.ewancroft.chronicler.config.PluginConfig
import uk.ewancroft.chronicler.news.BookRenderer
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.Newspaper
import uk.ewancroft.chronicler.news.NewspaperGenerator
import uk.ewancroft.chronicler.news.WebRenderer
import java.util.Calendar
import java.util.logging.Logger

class PublicationTask(
    private val plugin: JavaPlugin,
    private val config: PluginConfig,
    private val store: EventStore,
    private val generator: NewspaperGenerator,
    private val bookRenderer: BookRenderer,
    private val webRenderer: WebRenderer,
    private val logger: Logger,
) {

    private var issueNumber = 0
    private var lastPublishTime = System.currentTimeMillis()
    private var latestBook: ItemStack? = null
    private var latestNewspaper: Newspaper? = null
    private var taskId: Long? = null

    fun start() {
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

        taskId = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            { _ -> checkAndPublish() },
            20L * 60L,
            interval,
        ).taskId

        logger.info("Publication task scheduled (${config.schedule.lowercase()})")
    }

    fun stop() {
        taskId?.let { Bukkit.getGlobalRegionScheduler().cancel(it) }
        taskId = null
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

    private fun doPublish() {
        issueNumber++
        val toTime = System.currentTimeMillis()
        val fromTime = lastPublishTime

        try {
            val newspaper = generator.generate(issueNumber, fromTime, toTime)
            val book = bookRenderer.renderToBook(newspaper)

            latestNewspaper = newspaper
            latestBook = book

            Bukkit.getOnlinePlayers().forEach { player ->
                player.sendMessage(
                    net.kyori.adventure.text.Component.text("§6[Chronicler] §e${config.newspaper.title} #$issueNumber has been published! Read it with §a/chronicler read")
                )
            }

            webRenderer.renderAndServe(newspaper)

            lastPublishTime = toTime
            store.clear()

            logger.info("Published issue #$issueNumber")
        } catch (e: Exception) {
            logger.warning("Failed to publish issue #$issueNumber: ${e.message}")
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
