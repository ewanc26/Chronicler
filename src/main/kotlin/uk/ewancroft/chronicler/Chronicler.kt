package uk.ewancroft.chronicler

import org.bstats.bukkit.Metrics
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import uk.ewancroft.chronicler.command.ChroniclerCommand
import uk.ewancroft.chronicler.command.ChroniclerExpansion
import uk.ewancroft.chronicler.config.PluginConfig
import uk.ewancroft.chronicler.llm.AnthropicProvider
import uk.ewancroft.chronicler.llm.LlmProvider
import uk.ewancroft.chronicler.llm.OllamaProvider
import uk.ewancroft.chronicler.llm.OpenAiProvider
import uk.ewancroft.chronicler.news.ArchiveStore
import uk.ewancroft.chronicler.news.BookRenderer
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.Newspaper
import uk.ewancroft.chronicler.news.NewspaperGenerator
import uk.ewancroft.chronicler.news.WebRenderer
import uk.ewancroft.chronicler.task.HeadlineTicker
import uk.ewancroft.chronicler.task.PublicationTask
import uk.ewancroft.chronicler.tracker.BuildTracker
import uk.ewancroft.chronicler.tracker.DeathTracker
import uk.ewancroft.chronicler.tracker.MilestoneTracker
import uk.ewancroft.chronicler.tracker.SessionStore
import uk.ewancroft.chronicler.tracker.SessionTracker
import uk.ewancroft.chronicler.tracker.SocialTracker

class Chronicler : JavaPlugin() {

    private lateinit var eventStore: EventStore
    private var llmProvider: LlmProvider? = null
    private lateinit var generator: NewspaperGenerator
    private lateinit var bookRenderer: BookRenderer
    private var webRenderer: WebRenderer? = null
    private lateinit var publicationTask: PublicationTask
    private lateinit var command: ChroniclerCommand
    private var pluginConfig: PluginConfig? = null
    private var llmAvailable = false
    private var sessionStore: SessionStore? = null
    private var archiveStore: ArchiveStore? = null
    private var headlineTicker: HeadlineTicker? = null
    private var papiExpansion: ChroniclerExpansion? = null

    override fun onEnable() {
        saveDefaultConfig()

        Metrics(this, 0)

        pluginConfig = PluginConfig(config)
        val cfg = pluginConfig ?: return

        val dataPath = dataFolder.toPath()
        val storeFile = dataPath.resolve("events.json")
        val sessionFile = dataPath.resolve("sessions.json")
        val archiveDir = dataPath.resolve("archive")

        eventStore = EventStore(storeFile)
        eventStore.setMaxEvents(cfg.eventLimit)
        eventStore.load()

        sessionStore = SessionStore(sessionFile)
        sessionStore?.load()

        archiveStore = ArchiveStore(archiveDir)
        archiveStore?.loadAll()

        llmProvider = if (cfg.llm.enabled) {
            val provider = createProvider(cfg.llm)
            llmAvailable = provider.isAvailable()
            if (!llmAvailable) {
                logger.warning("${provider.name()} is not reachable. Falling back to template mode.")
            }
            provider
        } else {
            null
        }

        generator = NewspaperGenerator(
            store = eventStore,
            newspaperConfig = cfg.newspaper,
            llmProvider = llmProvider?.takeIf { llmAvailable },
            llmEnabled = cfg.llm.enabled && llmAvailable,
            logger = logger,
        )

        bookRenderer = BookRenderer(cfg.newspaper)

        if (cfg.web.enabled) {
            webRenderer = WebRenderer(cfg.web, cfg.newspaper, dataPath.resolve("web"))
        } else {
            logger.info("Web view disabled.")
        }

        val trackers = mutableListOf(
            DeathTracker(eventStore, cfg.tracking),
            MilestoneTracker(eventStore, cfg.tracking),
            BuildTracker(eventStore, cfg.tracking),
            SocialTracker(eventStore, cfg.tracking),
        )
        if (sessionStore != null) {
            trackers.add(SessionTracker(eventStore, sessionStore!!, cfg.tracking))
        }
        trackers.forEach { server.pluginManager.registerEvents(it, this) }

        publicationTask = PublicationTask(
            plugin = this,
            config = cfg,
            store = eventStore,
            generator = generator,
            bookRenderer = bookRenderer,
            webRenderer = webRenderer,
            archiveStore = archiveStore,
            logger = logger,
        )
        publicationTask.start()

        headlineTicker = HeadlineTicker(
            plugin = this,
            logger = logger,
            intervalTicks = cfg.tickerInterval,
            getLatestNewspaper = { publicationTask.getLatestNewspaper() },
        )
        headlineTicker?.start()

        papiExpansion = if (cfg.papiEnabled) {
            try {
                val exp = ChroniclerExpansion(this, sessionStore)
                exp.register()
                logger.info("PlaceholderAPI expansion registered.")
                exp
            } catch (_: NoClassDefFoundError) {
                logger.info("PlaceholderAPI not found, expansion skipped.")
                null
            }
        } else null

        command = ChroniclerCommand(this)
        val cmd = getCommand("chronicler") ?: return
        cmd.setExecutor(command)
        cmd.setTabCompleter(command)

        logger.info("Chronicler enabled. LLM: ${if (llmAvailable) "${cfg.llm.provider} (${cfg.llm.model})" else "template mode"}. Web: ${if (cfg.web.enabled) "port ${cfg.web.port}" else "disabled"}.")
    }

    override fun onDisable() {
        publicationTask.stop()
        webRenderer?.stop()
        headlineTicker?.stop()
        eventStore.save()
        sessionStore?.save()
        pluginConfig = null
        logger.info("Chronicler disabled.")
    }

    fun giveNewspaper(player: Player) {
        val book = publicationTask.getLatestBook()
        if (book == null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("No newspaper has been published yet.", net.kyori.adventure.text.format.NamedTextColor.YELLOW))
            return
        }
        if (player.inventory.firstEmpty() == -1) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Your inventory is full.", net.kyori.adventure.text.format.NamedTextColor.RED))
            return
        }
        player.inventory.addItem(book)
        player.sendMessage(net.kyori.adventure.text.Component.text("Here's the latest issue!", net.kyori.adventure.text.format.NamedTextColor.GREEN))
    }

    fun publishNow() {
        publicationTask.publishNow()
    }

    fun reloadPlugin() {
        reloadConfig()
        pluginConfig = PluginConfig(config)
        eventStore.setMaxEvents(pluginConfig!!.eventLimit)
        publicationTask.stop()
        headlineTicker?.stop()
        publicationTask = PublicationTask(
            plugin = this,
            config = pluginConfig!!,
            store = eventStore,
            generator = generator,
            bookRenderer = bookRenderer,
            webRenderer = webRenderer,
            archiveStore = archiveStore,
            logger = logger,
        )
        publicationTask.start()
        headlineTicker = HeadlineTicker(
            plugin = this,
            logger = logger,
            intervalTicks = pluginConfig!!.tickerInterval,
            getLatestNewspaper = { publicationTask.getLatestNewspaper() },
        )
        headlineTicker?.start()
    }

    fun getWebPort(): Int = pluginConfig?.web?.port ?: 0

    fun getLastPublishTime(): Long = publicationTask.getLastPublishTime()

    fun getLatestNewspaper(): Newspaper? = publicationTask.getLatestNewspaper()

    fun getArchiveStore(): ArchiveStore? = archiveStore

    fun getSessionStore(): SessionStore? = sessionStore

    fun getEventStore(): EventStore = eventStore

    fun getNewspaperConfig() = pluginConfig?.newspaper

    fun getBookRenderer(): BookRenderer = bookRenderer

    fun getStatus(): PluginStatus {
        val cfg = pluginConfig
        return PluginStatus(
            enabled = cfg?.enabled ?: false,
            schedule = cfg?.schedule ?: "unknown",
            issueNumber = publicationTask.getIssueNumber(),
            eventCount = eventStore.allEvents().size,
            llmAvailable = llmAvailable,
            webEnabled = cfg?.web?.enabled ?: false,
            webPort = cfg?.web?.port ?: 0,
        )
    }

    private fun createProvider(cfg: uk.ewancroft.chronicler.config.LlmConfig): LlmProvider {
        return when (cfg.provider) {
            "openai", "openai-compatible" -> OpenAiProvider(cfg)
            "anthropic" -> AnthropicProvider(cfg)
            else -> OllamaProvider(cfg)
        }
    }

    data class PluginStatus(
        val enabled: Boolean,
        val schedule: String,
        val issueNumber: Int,
        val eventCount: Int,
        val llmAvailable: Boolean,
        val webEnabled: Boolean,
        val webPort: Int,
    )
}
