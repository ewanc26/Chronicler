package uk.ewancroft.chronicler.task

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import uk.ewancroft.chronicler.config.Messages
import uk.ewancroft.chronicler.config.PluginConfig
import uk.ewancroft.chronicler.news.ArchiveStore
import uk.ewancroft.chronicler.news.BookRenderer
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.Newspaper
import uk.ewancroft.chronicler.news.NewspaperGenerator
import uk.ewancroft.chronicler.news.WebRenderer
import uk.ewancroft.chronicler.tracker.SubscribeStore
import java.nio.file.Files
import java.nio.file.Path
import java.util.Calendar
import java.util.logging.Level
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.logging.Logger

class PublicationTask(
    private val plugin: JavaPlugin,
    private val config: PluginConfig,
    private val store: EventStore,
    private val subscribeStore: SubscribeStore,
    private val messages: Messages,
    private val generator: NewspaperGenerator,
    private val bookRenderer: BookRenderer,
    private val webRenderer: WebRenderer?,
    private val archiveStore: ArchiveStore?,
    private val logger: Logger,
    private val activationTime: Long,
) {

    private var issueNumber = 0
    private var lastPublishTime = 0L
    private var latestBook: ItemStack? = null
    private var latestNewspaper: Newspaper? = null
    private var draftNewspaper: Newspaper? = null
    private var scheduledTask: ScheduledTask? = null
    private val stateFile: Path = plugin.dataFolder.toPath().resolve("publish-state.json")
    private val draftFile: Path = plugin.dataFolder.toPath().resolve("draft-issue.json")
    private val deliveredPlayers = mutableSetOf<String>()

    fun start() {
        loadState()
        loadDraft()

        val backfilledEvents = store.allEvents().count { it.timestamp <= activationTime }
        if (issueNumber == 0 && lastPublishTime == 0L && backfilledEvents > 0) {
            logger.info("No prior issues found — publishing issue #0 with $backfilledEvents events through plugin activation.")
            doPublish(isIssueZero = true, cutoffTime = activationTime)
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
        logger.info("Stopping publication task at issue #$issueNumber.")
        scheduledTask?.cancel()
        scheduledTask = null
        saveState()
    }

    fun publishNow() {
        if (config.reviewRequired) createDraft() else doPublish()
    }

    fun createDraft(): Newspaper {
        val number = issueNumber + 1
        return generator.generate(number, lastPublishTime, System.currentTimeMillis()).also {
            draftNewspaper = it
            saveDraft()
            logger.info("Created draft issue #$number with ${it.sections.sumOf { section -> section.stories.size }} stories; awaiting editor approval.")
        }
    }

    fun getDraft(): Newspaper? = draftNewspaper

    fun removeDraftStory(sectionIndex: Int, storyIndex: Int): Boolean {
        val draft = draftNewspaper ?: return false
        val section = draft.sections.getOrNull(sectionIndex) ?: return false
        if (storyIndex !in section.stories.indices) return false
        val sections = draft.sections.toMutableList()
        sections[sectionIndex] = section.copy(stories = section.stories.filterIndexed { index, _ -> index != storyIndex })
        draftNewspaper = draft.copy(sections = sections.filter { it.stories.isNotEmpty() })
        saveDraft()
        return true
    }

    fun editDraftStory(sectionIndex: Int, storyIndex: Int, headline: String? = null, body: String? = null): Boolean {
        val draft = draftNewspaper ?: return false
        val section = draft.sections.getOrNull(sectionIndex) ?: return false
        val story = section.stories.getOrNull(storyIndex) ?: return false
        val stories = section.stories.toMutableList()
        stories[storyIndex] = story.copy(
            headline = headline?.trim()?.take(60)?.ifBlank { story.headline } ?: story.headline,
            body = body?.trim()?.ifBlank { story.body } ?: story.body,
        )
        val sections = draft.sections.toMutableList()
        sections[sectionIndex] = section.copy(stories = stories)
        draftNewspaper = draft.copy(sections = sections)
        saveDraft()
        return true
    }

    fun publishDraft(): Boolean {
        val draft = draftNewspaper ?: return false
        publishNewspaper(draft, draft.toTime, false)
        draftNewspaper = null
        Files.deleteIfExists(draftFile)
        return true
    }

    fun getIssueNumber(): Int = issueNumber
    fun getLastPublishTime(): Long = lastPublishTime
    fun getLatestBook(): ItemStack? = latestBook?.clone()
    fun getLatestNewspaper(): Newspaper? = latestNewspaper

    fun deliverToPlayer(player: org.bukkit.entity.Player) {
        val uuid = player.uniqueId.toString()
        if (uuid in deliveredPlayers) return
        if (latestBook == null) return
        if (!subscribeStore.isSubscribed(uuid)) return
        giveBook(player, issueNumber)
    }

    private fun giveBook(player: org.bukkit.entity.Player, number: Int) {
        val book = latestBook?.clone() ?: return
        val uuid = player.uniqueId.toString()
        val remaining = player.inventory.addItem(book)
        if (remaining.isEmpty()) {
            player.sendMessage(messages.deliveryInventory(config.newspaper.title, number))
        } else {
            player.world.dropItem(player.location, book)
            player.sendMessage(messages.deliveryDrop(config.newspaper.title, number))
        }
        deliveredPlayers.add(uuid)
    }

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

    private fun doPublish(isIssueZero: Boolean = false, cutoffTime: Long = System.currentTimeMillis()) {
        val toTime = cutoffTime
        val fromTime = if (isIssueZero) 0L else lastPublishTime
        val number = if (isIssueZero) 0 else issueNumber + 1
        val eventCount = store.eventsSince(fromTime).count { it.timestamp <= toTime }
        val startedAt = System.currentTimeMillis()

        try {
            logger.info("Starting publication of issue #$number with $eventCount events ($fromTime to $toTime).")
            val newspaper = generator.generate(number, fromTime, toTime)
            publishNewspaper(newspaper, toTime, isIssueZero, startedAt, eventCount)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to publish issue #$number after ${System.currentTimeMillis() - startedAt}ms.", e)
        }
    }

    private fun publishNewspaper(
        newspaper: Newspaper,
        toTime: Long,
        isIssueZero: Boolean,
        startedAt: Long = System.currentTimeMillis(),
        eventCount: Int = store.eventsSince(lastPublishTime).count { it.timestamp <= toTime },
    ) {
        val number = newspaper.issueNumber
        try {
            val book = bookRenderer.renderToBook(newspaper)
            val storyCount = newspaper.sections.sumOf { it.stories.size }
            logger.info("Rendered issue #$number: ${newspaper.sections.size} sections, $storyCount stories, ${book.itemMeta.let { it as org.bukkit.inventory.meta.BookMeta }.pageCount} book pages.")

            latestNewspaper = newspaper
            latestBook = book
            archiveStore?.archive(newspaper)
            if (archiveStore != null) logger.info("Archived issue #$number.")
            deliveredPlayers.clear()

            var delivered = 0
            Bukkit.getOnlinePlayers().forEach { player ->
                if (subscribeStore.isSubscribed(player.uniqueId.toString())) {
                    giveBook(player, number)
                    delivered++
                }
            }
            logger.info("Delivered issue #$number to $delivered online subscriber(s).")

            webRenderer?.renderAndServe(newspaper)
            if (webRenderer != null) logger.info("Rendered web edition for issue #$number.")

            lastPublishTime = toTime
            if (!isIssueZero) {
                issueNumber = number
            }
            store.removeThrough(toTime)
            saveState()

            logger.info("Published issue #$number in ${System.currentTimeMillis() - startedAt}ms; consumed $eventCount events.")
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to publish issue #$number after ${System.currentTimeMillis() - startedAt}ms.", e)
            throw e
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
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to load publication state from $stateFile; starting from issue zero.", e)
            lastPublishTime = 0L
        }
    }

    private fun saveState() {
        try {
            Files.createDirectories(stateFile.parent)
            Files.writeString(stateFile, "$issueNumber\n$lastPublishTime")
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to save publication state to $stateFile.", e)
        }
    }

    private fun loadDraft() {
        if (!Files.exists(draftFile)) return
        try {
            draftNewspaper = Json { ignoreUnknownKeys = true }.decodeFromString<Newspaper>(Files.readString(draftFile))
            logger.info("Restored draft issue #${draftNewspaper?.issueNumber} from disk.")
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to restore draft from $draftFile; preserving the unreadable file for recovery.", e)
        }
    }

    private fun saveDraft() {
        val draft = draftNewspaper ?: return
        try {
            Files.createDirectories(draftFile.parent)
            val temporary = draftFile.resolveSibling("${draftFile.fileName}.tmp")
            Files.writeString(temporary, Json { prettyPrint = true }.encodeToString(draft))
            Files.move(temporary, draftFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to save draft issue #${draft.issueNumber}.", e)
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
