package uk.ewancroft.chronicler.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import uk.ewancroft.chronicler.Chronicler
import uk.ewancroft.chronicler.news.BookRenderer

class ChroniclerCommand(private val plugin: Chronicler) : CommandExecutor, TabCompleter {

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
            val cmds = listOf("read", "web", "latest", "reload", "status", "publish", "stats", "archive", "help")
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
            sender.sendMessage(Component.text("Only players can read the newspaper.", NamedTextColor.RED))
            return true
        }
        plugin.giveNewspaper(sender)
        return true
    }

    private fun readLatest(sender: CommandSender): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can read the newspaper.", NamedTextColor.RED))
            return true
        }
        plugin.giveNewspaper(sender)
        return true
    }

    private fun webUrl(sender: CommandSender): Boolean {
        val port = plugin.getWebPort()
        if (port <= 0) {
            sender.sendMessage(Component.text("Web server is not enabled.", NamedTextColor.YELLOW))
            return true
        }
        sender.sendMessage(Component.text("Newspaper web view: http://localhost:$port", NamedTextColor.AQUA))
        sender.sendMessage(Component.text("(Replace localhost with your server address.)", NamedTextColor.GRAY))
        return true
    }

    private fun reloadConfig(sender: CommandSender): Boolean {
        if (!sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED))
            return true
        }
        plugin.reloadPlugin()
        sender.sendMessage(Component.text("Chronicler reloaded.", NamedTextColor.GREEN))
        return true
    }

    private fun showStatus(sender: CommandSender): Boolean {
        if (!sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED))
            return true
        }
        val status = plugin.getStatus()
        sender.sendMessage(Component.text("§6Chronicler §7v${plugin.pluginMeta.version}"))
        sender.sendMessage(Component.text(" §7Enabled: §f${status.enabled}"))
        sender.sendMessage(Component.text(" §7Schedule: §f${status.schedule}"))
        sender.sendMessage(Component.text(" §7Issue: §f#${status.issueNumber}"))
        sender.sendMessage(Component.text(" §7Events stored: §f${status.eventCount}"))
        sender.sendMessage(Component.text(" §7LLM: §f${if (status.llmAvailable) "§aonline" else "§coffline/disabled"}"))
        sender.sendMessage(Component.text(" §7Web: §f${if (status.webEnabled) "§a:${status.webPort}" else "§cdisabled"}"))
        return true
    }

    private fun publishNow(sender: CommandSender): Boolean {
        if (!sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED))
            return true
        }
        plugin.publishNow()
        sender.sendMessage(Component.text("Publishing newspaper...", NamedTextColor.GREEN))
        return true
    }

    private fun showStats(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /chronicler stats <player>", NamedTextColor.YELLOW))
            return true
        }
        val targetName = args[1]
        val target = Bukkit.getPlayerExact(targetName)
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
            return true
        }
        val uuid = target.uniqueId.toString()
        val store = plugin.getEventStore()
        val allEvents = store.allEvents()
        val playerEvents = allEvents.filter { it.playerUuid == uuid }

        val deaths = playerEvents.count { it.type.name == "DEATH" || it.type.name == "PVP_KILL" }
        val kills = playerEvents.count { it.type.name == "KILL" }
        val blocksPlaced = playerEvents.count { it.type.name == "BLOCK_PLACE" }
        val blocksBroken = playerEvents.count { it.type.name == "BLOCK_BREAK" }
        val biomes = playerEvents.filter { it.type.name == "BIOME_DISCOVERY" }.mapNotNull { it.details["biome"] }.distinct()
        val advancements = playerEvents.filter { it.type.name == "ADVANCEMENT" }.mapNotNull { it.details["displayName"] ?: it.details["advancement"] }.distinct()
        val logins = playerEvents.count { it.type.name == "PLAYER_JOIN" || it.type.name == "SESSION_START" }

        val sessionStore = plugin.getSessionStore()
        val sessionData = if (sessionStore != null) sessionStore.get(uuid) else null

        sender.sendMessage(Component.text("§6Chronicler Stats — §e${target.name}"))
        sender.sendMessage(Component.text(" §7Total events: §f${playerEvents.size}"))
        sender.sendMessage(Component.text(" §7Deaths: §f$deaths"))
        sender.sendMessage(Component.text(" §7Mob kills: §f$kills"))
        sender.sendMessage(Component.text(" §7Blocks placed: §f$blocksPlaced"))
        sender.sendMessage(Component.text(" §7Blocks broken: §f$blocksBroken"))
        sender.sendMessage(Component.text(" §7Biomes discovered: §f${biomes.size}"))
        sender.sendMessage(Component.text(" §7Advancements: §f${advancements.size}"))
        sender.sendMessage(Component.text(" §7Logins: §f$logins"))
        if (sessionData != null) {
            val minutes = sessionData.totalPlaytimeTicks / (20 * 60)
            sender.sendMessage(Component.text(" §7Playtime: §f${minutes / 60}h ${minutes % 60}m"))
            sender.sendMessage(Component.text(" §7Sessions: §f${sessionData.sessionCount}"))
            sender.sendMessage(Component.text(" §7Login streak: §f${sessionData.currentStreak} days"))
            sender.sendMessage(Component.text(" §7Longest streak: §f${sessionData.longestStreak} days"))
        }
        return true
    }

    private fun showArchive(sender: CommandSender, args: Array<out String>): Boolean {
        val archive = plugin.getArchiveStore() ?: run {
            sender.sendMessage(Component.text("Archive not available.", NamedTextColor.RED))
            return true
        }

        if (args.size >= 2 && args[1].lowercase() == "list") {
            val issues = archive.latest(20)
            if (issues.isEmpty()) {
                sender.sendMessage(Component.text("No archived issues yet.", NamedTextColor.YELLOW))
                return true
            }
            sender.sendMessage(Component.text("§6Chronicler Archive — Recent Issues:"))
            for (issue in issues) {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(issue.toTime))
                val stories = issue.sections.sumOf { it.stories.size }
                sender.sendMessage(Component.text(" §7#${issue.issueNumber} §f($date) §7— §f$stories stories"))
            }
            sender.sendMessage(Component.text(" §7Use §a/chronicler archive read <#> §7to view an issue."))
            return true
        }

        if (args.size >= 3 && args[1].lowercase() == "read") {
            val issueNum = args[2].toIntOrNull()
            if (issueNum == null) {
                sender.sendMessage(Component.text("Usage: /chronicler archive read <issue#>", NamedTextColor.YELLOW))
                return true
            }
            if (sender !is Player) {
                sender.sendMessage(Component.text("Only players can read archived issues.", NamedTextColor.RED))
                return true
            }
            val newspaper = archive.getIssue(issueNum)
            if (newspaper == null) {
                sender.sendMessage(Component.text("Issue #$issueNum not found.", NamedTextColor.RED))
                return true
            }
            val cfg = plugin.getNewspaperConfig()
            if (cfg != null) {
                val renderer = BookRenderer(cfg)
                val book = renderer.renderToBook(newspaper)
                if (sender.inventory.firstEmpty() == -1) {
                    sender.world.dropItem(sender.location, book)
                    sender.sendMessage(Component.text("Issue #$issueNum dropped at your feet (inventory full).", NamedTextColor.YELLOW))
                } else {
                    sender.inventory.addItem(book)
                    sender.sendMessage(Component.text("Here's issue #$issueNum!", NamedTextColor.GREEN))
                }
            }
            return true
        }

        sender.sendMessage(Component.text("Usage: /chronicler archive list or /chronicler archive read <#>", NamedTextColor.YELLOW))
        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("§6Chronicler Commands:"))
        sender.sendMessage(Component.text(" §a/chronicler read §7— Get the latest newspaper"))
        sender.sendMessage(Component.text(" §a/chronicler web §7— Show web URL"))
        sender.sendMessage(Component.text(" §a/chronicler status §7— Show plugin status"))
        sender.sendMessage(Component.text(" §a/chronicler stats <player> §7— View player stats"))
        sender.sendMessage(Component.text(" §a/chronicler archive list §7— List past issues"))
        if (sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(Component.text(" §a/chronicler archive read <#> §7— Read an old issue"))
            sender.sendMessage(Component.text(" §a/chronicler reload §7— Reload config"))
            sender.sendMessage(Component.text(" §a/chronicler publish §7— Publish now"))
        }
    }
}
