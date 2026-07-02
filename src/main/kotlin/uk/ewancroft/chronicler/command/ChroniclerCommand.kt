package uk.ewancroft.chronicler.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import uk.ewancroft.chronicler.Chronicler

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
            val cmds = listOf("read", "web", "latest", "reload", "status", "publish", "help")
            return cmds.filter { it.startsWith(args[0], true) }
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
        sender.sendMessage(Component.text(" §7Web: §f${if (status.webEnabled) "§a:$webPort" else "§cdisabled"}"))
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

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(Component.text("§6Chronicler Commands:"))
        sender.sendMessage(Component.text(" §a/chronicler read §7— Get the latest newspaper"))
        sender.sendMessage(Component.text(" §a/chronicler web §7— Show web URL"))
        sender.sendMessage(Component.text(" §a/chronicler status §7— Show plugin status"))
        if (sender.hasPermission("chronicler.admin")) {
            sender.sendMessage(Component.text(" §a/chronicler reload §7— Reload config"))
            sender.sendMessage(Component.text(" §a/chronicler publish §7— Publish now"))
        }
    }
}
