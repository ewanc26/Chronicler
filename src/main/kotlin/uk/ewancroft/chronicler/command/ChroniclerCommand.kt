package uk.ewancroft.chronicler.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import uk.ewancroft.chronicler.Chronicler
import uk.ewancroft.chronicler.config.Messages
import uk.ewancroft.chronicler.news.BookRenderer
import uk.ewancroft.chronicler.tracker.SubscribeStore

class ChroniclerCommand(
    private val plugin: Chronicler,
    private val messages: Messages,
    private val subscribeStore: SubscribeStore,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "read" -> readIssue(sender)
            "web" -> webUrl(sender)
            "latest" -> readLatest(sender)
            "reload" -> reloadConfig(sender)
            "status" -> showStatus(sender)
            "publish" -> publishNow(sender)
            "stats" -> showStats(sender, args)
            "subscribe" -> toggleSubscribe(sender)
            "archive" -> showArchive(sender, args)
            "help" -> { sendHelp(sender); true }
            else -> { sendHelp(sender); true }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (args.size == 1) {
            val cmds = listOf("read", "web", "latest", "reload", "status", "publish", "stats", "subscribe", "archive", "help")
            return cmds.filter { it.startsWith(args[0], true) }
        }
        if (args[0].lowercase() == "stats" && args.size == 2) {
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], true) }
        }
        if (args[0].lowercase() == "archive" && args.size == 2) {
            return listOf("list", "read").filter { it.startsWith(args[1], true) }
        }
        return emptyList()
    }

    private fun readIssue(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messages.playerOnly())
            return true
        }
        plugin.giveNewspaper(sender)
        return true
    }

    private fun readLatest(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messages.playerOnly())
            return true
        }
        plugin.giveNewspaper(sender)
        return true
    }

    private fun webUrl(sender: CommandSender): Boolean {
        val port = plugin.getWebPort()
        if (port <= 0) {
            sender.sendMessage(messages.webDisabled())
            return true
        }
        sender.sendMessage(messages.webUrl(port))
        sender.sendMessage(messages.webUrlHint())
        return true
    }

    private fun reloadConfig(sender: CommandSender): Boolean {
        if (!sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(messages.noPermission())
            return true
        }
        plugin.reloadPlugin()
        sender.sendMessage(messages.reloadDone())
        return true
    }

    private fun showStatus(sender: CommandSender): Boolean {
        if (!sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(messages.noPermission())
            return true
        }
        val status = plugin.getStatus()
        sender.sendMessage(net.kyori.adventure.text.Component.text("§6Chronicler §7v${plugin.pluginMeta.version}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Enabled: §f${status.enabled}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Schedule: §f${status.schedule}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Issue: §f#${status.issueNumber}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Events stored: §f${status.eventCount}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7LLM: §f${if (status.llmAvailable) "§aonline" else "§coffline/disabled"}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Web: §f${if (status.webEnabled) "§a:${status.webPort}" else "§cdisabled"}"))
        return true
    }

    private fun publishNow(sender: CommandSender): Boolean {
        if (!sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(messages.noPermission())
            return true
        }
        plugin.publishNow()
        sender.sendMessage(messages.publishDone())
        return true
    }

    private fun toggleSubscribe(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage(messages.playerOnly())
            return true
        }
        val subscribed = subscribeStore.toggle(sender.uniqueId.toString())
        sender.sendMessage(if (subscribed) messages.subscribeEnabled() else messages.subscribeDisabled())
        return true
    }

    private fun showStats(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(messages.statsUsage())
            return true
        }
        val targetName = args[1]
        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            sender.sendMessage(messages.playerNotFound())
            return true
        }
        val uuid = target.uniqueId.toString()
        val store = plugin.getEventStore() ?: run {
            sender.sendMessage(messages.pluginNotReady())
            return true
        }
        val allEvents = store.allEvents()
        val playerEvents = allEvents.filter { it.playerUuid == uuid }

        val deaths = playerEvents.count { it.type.name == "DEATH" || it.type.name == "PVP_KILL" }
        val kills = playerEvents.count { it.type.name == "KILL" }
        val blocksPlaced = playerEvents.count { it.type.name == "BLOCK_PLACE" }
        val blocksBroken = playerEvents.count { it.type.name == "BLOCK_BREAK" }
        val biomes = playerEvents.filter { it.type.name == "BIOME_DISCOVERY" }.mapNotNull { it.details["biome"] }.distinct()
        val advancements = playerEvents.filter { it.type.name == "ADVANCEMENT" }.mapNotNull { it.details["displayName"] ?: it.details["advancement"] }.distinct()
        val logins = playerEvents.count { it.type.name == "PLAYER_JOIN" || it.type.name == "SESSION_START" }
        val ores = playerEvents.filter { it.type.name == "ORE_DISCOVERY" }.mapNotNull { it.details["ore"] }.distinct()
        val trades = playerEvents.count { it.type.name == "TRADE" }
        val messagesSent = playerEvents.count { it.type.name == "MESSAGE_SENT" }

        val sessionStore = plugin.getSessionStore()
        val sessionData = if (sessionStore != null) sessionStore.get(uuid) else null

        sender.sendMessage(net.kyori.adventure.text.Component.text("§6Chronicler Stats — §e${target.name}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Total events: §f${playerEvents.size}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Deaths: §f$deaths"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Mob kills: §f$kills"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Blocks placed: §f$blocksPlaced"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Blocks broken: §f$blocksBroken"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Biomes discovered: §f${biomes.size}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Advancements: §f${advancements.size}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Ore types discovered: §f${ores.size}"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Trades: §f$trades"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Messages sent: §f$messagesSent"))
        sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Logins: §f$logins"))
        if (sessionData != null) {
            val minutes = sessionData.totalPlaytimeTicks / (20 * 60)
            sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Playtime: §f${minutes / 60}h ${minutes % 60}m"))
            sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Sessions: §f${sessionData.sessionCount}"))
            sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Login streak: §f${sessionData.currentStreak} days"))
            sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Longest streak: §f${sessionData.longestStreak} days"))
        }
        return true
    }

    private fun showArchive(sender: CommandSender, args: Array<out String>): Boolean {
        val archive = plugin.getArchiveStore() ?: run {
            sender.sendMessage(messages.pluginNotReady())
            return true
        }

        if (args.size >= 2 && args[1].lowercase() == "list") {
            val issues = archive.latest(20)
            if (issues.isEmpty()) {
                sender.sendMessage(messages.archiveEmpty())
                return true
            }
            sender.sendMessage(net.kyori.adventure.text.Component.text("§6Chronicler Archive — Recent Issues:"))
            for (issue in issues) {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(issue.toTime))
                val stories = issue.sections.sumOf { it.stories.size }
                sender.sendMessage(net.kyori.adventure.text.Component.text(" §7#${issue.issueNumber} §f($date) §7— §f$stories stories"))
            }
            sender.sendMessage(net.kyori.adventure.text.Component.text(" §7Use §a/chronicler archive read <#> §7to view an issue."))
            return true
        }

        if (args.size >= 3 && args[1].lowercase() == "read") {
            val issueNum = args[2].toIntOrNull()
            if (issueNum == null) {
                sender.sendMessage(messages.archiveUsage())
                return true
            }
            if (sender !is Player) {
                sender.sendMessage(messages.playerOnly())
                return true
            }
            val newspaper = archive.getIssue(issueNum)
            if (newspaper == null) {
                sender.sendMessage(messages.archiveNotFound(issueNum))
                return true
            }
            val cfg = plugin.getNewspaperConfig()
            if (cfg != null) {
                val renderer = BookRenderer(cfg)
                val book = renderer.renderToBook(newspaper)
                if (sender.inventory.firstEmpty() == -1) {
                    sender.world.dropItem(sender.location, book)
                    sender.sendMessage(messages.archiveDropped(issueNum))
                } else {
                    sender.inventory.addItem(book)
                    sender.sendMessage(messages.archiveHere(issueNum))
                }
            }
            return true
        }

        sender.sendMessage(messages.archiveUsage())
        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(messages.helpHeader())
        sender.sendMessage(messages.helpRead())
        sender.sendMessage(messages.helpWeb())
        sender.sendMessage(messages.helpStatus())
        sender.sendMessage(messages.helpStats())
        sender.sendMessage(messages.helpSubscribe())
        sender.sendMessage(messages.helpArchiveList())
        if (sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(messages.helpArchiveRead())
            sender.sendMessage(messages.helpReload())
            sender.sendMessage(messages.helpPublish())
        }
    }
}
