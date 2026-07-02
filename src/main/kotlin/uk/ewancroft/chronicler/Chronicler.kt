package uk.ewancroft.chronicler

import org.bstats.bukkit.Metrics
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import uk.ewancroft.chronicler.command.ChroniclerCommand
import uk.ewancroft.chronicler.config.PluginConfig
import uk.ewancroft.chronicler.llm.OllamaClient
import uk.ewancroft.chronicler.news.BookRenderer
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.NewspaperGenerator
import uk.ewancroft.chronicler.news.WebRenderer
import uk.ewancroft.chronicler.task.PublicationTask
import uk.ewancroft.chronicler.tracker.BuildTracker
import uk.ewancroft.chronicler.tracker.DeathTracker
import uk.ewancroft.chronicler.tracker.MilestoneTracker
import uk.ewancroft.chronicler.tracker.SocialTracker

class Chronicler : JavaPlugin() {

    private lateinit var eventStore: EventStore
    private lateinit var llmClient: OllamaClient?
    private lateinit var generator: NewspaperGenerator
    private lateinit var bookRenderer: BookRenderer
    private var webRenderer: WebRenderer? = null
    private lateinit var publicationTask: PublicationTask
    private lateinit var command: ChroniclerCommand
    private var pluginConfig: PluginConfig? = null
    private var llmAvailable = false

    override fun onEnable() {
        saveDefaultConfig()

        Metrics(this, 0)

        pluginConfig = PluginConfig(config)
        val cfg = pluginConfig ?: return

        val dataPath = dataFolder.toPath()
        val storeFile = dataPath.resolve("events.json")

        eventStore = EventStore(storeFile)
        eventStore.setMaxEvents(cfg.eventLimit)
        eventStore.load()

        llmClient = if (cfg.llm.enabled) {
            val client = OllamaClient(cfg.llm)
            llmAvailable = client.isAvailable()
            if (!llmAvailable) {
                logger.warning("Ollama is not reachable at ${cfg.llm.url}. Falling back to template mode.")
            }
            client
        } else {
            null
        }

        generator = NewspaperGenerator(
            store = eventStore,
            newspaperConfig = cfg.newspaper,
            llmClient = llmClient?.takeIf { llmAvailable },
            llmEnabled = cfg.llm.enabled && llmAvailable,
            logger = logger,
        )

        bookRenderer = BookRenderer(cfg.newspaper)

        if (cfg.web.enabled) {
            webRenderer = WebRenderer(cfg.web, cfg.newspaper, dataPath.resolve("web"))
        } else {
            logger.info("Web view disabled.")
        }

        val trackers = listOf(
            DeathTracker(eventStore, cfg.tracking),
            MilestoneTracker(eventStore, cfg.tracking),
            BuildTracker(eventStore, cfg.tracking),
            SocialTracker(eventStore, cfg.tracking),
        )
        trackers.forEach { server.pluginManager.registerEvents(it, this) }

        publicationTask = PublicationTask(
            plugin = this,
            config = cfg,
            store = eventStore,
            generator = generator,
            bookRenderer = bookRenderer,
            webRenderer = webRenderer,
            logger = logger,
        )
        publicationTask.start()

        command = ChroniclerCommand(this)
        val cmd = getCommand("chronicler") ?: return
        cmd.setExecutor(command)
        cmd.setTabCompleter(command)

        logger.info("Chronicler enabled. LLM: ${if (llmAvailable) "online (${cfg.llm.model})" else "template mode"}. Web: ${if (cfg.web.enabled) "port ${cfg.web.port}" else "disabled"}.")
    }

    override fun onDisable() {
        publicationTask.stop()
        webRenderer?.stop()
        eventStore.save()
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
        publicationTask = PublicationTask(
            plugin = this,
            config = pluginConfig!!,
            store = eventStore,
            generator = generator,
            bookRenderer = bookRenderer,
            webRenderer = webRenderer,
            logger = logger,
        )
        publicationTask.start()
    }

    fun getWebPort(): Int = pluginConfig?.web?.port ?: 0

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
