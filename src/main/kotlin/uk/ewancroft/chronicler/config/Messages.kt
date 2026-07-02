package uk.ewancroft.chronicler.config

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class Messages(private val file: File) {

    private val miniMessage = MiniMessage.miniMessage()
    private var config = YamlConfiguration()

    fun load() {
        if (file.exists()) {
            try {
                config = YamlConfiguration.loadConfiguration(file)
            } catch (_: Exception) {
                config = YamlConfiguration()
            }
        }
    }

    fun prefix(): Component = miniMessage.deserialize(g("prefix", "<gold>[Chronicler]</gold>"))

    fun noPermission(): Component = miniMessage.deserialize(g("general.no-permission", "<red>You do not have permission.</red>"))
    fun playerOnly(): Component = miniMessage.deserialize(g("general.player-only", "<red>Only players can use this command.</red>"))
    fun pluginNotReady(): Component = miniMessage.deserialize(g("general.plugin-not-ready", "<red>Plugin not ready.</red>"))
    fun playerNotFound(): Component = miniMessage.deserialize(g("general.player-not-found", "<red>Player not found.</red>"))
    fun inventoryFull(): Component = miniMessage.deserialize(g("general.inventory-full", "<red>Your inventory is full.</red>"))
    fun helpHeader(): Component = miniMessage.deserialize(g("command.help-header", "<gold>Chronicler Commands:</gold>"))
    fun helpRead(): Component = miniMessage.deserialize(g("command.help-read", " <green>/chronicler read</green> <gray>— Get the latest newspaper</gray>"))
    fun helpWeb(): Component = miniMessage.deserialize(g("command.help-web", " <green>/chronicler web</green> <gray>— Show web URL</gray>"))
    fun helpStatus(): Component = miniMessage.deserialize(g("command.help-status", " <green>/chronicler status</green> <gray>— Show plugin status</gray>"))
    fun helpStats(): Component = miniMessage.deserialize(g("command.help-stats", " <green>/chronicler stats <player></green> <gray>— View player stats</gray>"))
    fun helpSubscribe(): Component = miniMessage.deserialize(g("command.help-subscribe", " <green>/chronicler subscribe</green> <gray>— Toggle auto-delivery</gray>"))
    fun helpArchiveList(): Component = miniMessage.deserialize(g("command.help-archive-list", " <green>/chronicler archive list</green> <gray>— List past issues</gray>"))
    fun helpArchiveRead(): Component = miniMessage.deserialize(g("command.help-archive-read", " <green>/chronicler archive read <#></green> <gray>— Read an old issue</gray>"))
    fun helpReload(): Component = miniMessage.deserialize(g("command.help-reload", " <green>/chronicler reload</green> <gray>— Reload config</gray>"))
    fun helpPublish(): Component = miniMessage.deserialize(g("command.help-publish", " <green>/chronicler publish</green> <gray>— Publish now</gray>"))
    fun statsUsage(): Component = miniMessage.deserialize(g("command.help-usage", "<yellow>Usage: /chronicler stats <player></yellow>"))
    fun archiveUsage(): Component = miniMessage.deserialize(g("command.archive-usage", "<yellow>Usage: /chronicler archive list or /chronicler archive read <#></yellow>"))
    fun archiveEmpty(): Component = miniMessage.deserialize(g("command.archive-empty", "<yellow>No archived issues yet.</yellow>"))
    fun archiveNotFound(num: Int): Component = miniMessage.deserialize(fmt1(g("command.archive-not-found", "<red>Issue #%s not found.</red>"), num))
    fun archiveDropped(num: Int): Component = miniMessage.deserialize(fmt1(g("command.archive-dropped", "<yellow>Issue #%s dropped at your feet (inventory full).</yellow>"), num))
    fun archiveHere(num: Int): Component = miniMessage.deserialize(fmt1(g("command.archive-here", "<green>Here's issue #%s!</green>"), num))
    fun noIssue(): Component = miniMessage.deserialize(g("read.no-issue", "<yellow>No newspaper has been published yet.</yellow>"))
    fun delivering(): Component = miniMessage.deserialize(g("read.delivering", "<green>Here's the latest issue!</green>"))
    fun webDisabled(): Component = miniMessage.deserialize(g("web.disabled", "<yellow>Web server is not enabled.</yellow>"))
    fun webUrl(port: Int): Component = miniMessage.deserialize(fmt1(g("web.url", "<aqua>Newspaper web view: http://localhost:%d</aqua>"), port))
    fun webUrlHint(): Component = miniMessage.deserialize(g("web.url-hint", "<gray>(Replace localhost with your server address.)</gray>"))
    fun reloadDone(): Component = miniMessage.deserialize(g("reload.done", "<green>Chronicler reloaded.</green>"))
    fun publishDone(): Component = miniMessage.deserialize(g("publish.done", "<green>Publishing newspaper...</green>"))
    fun deliveryInventory(title: String, num: Int): Component = miniMessage.deserialize(fmt2(g("delivery.inventory", "<gold>[Chronicler]</gold> <yellow>%s #%s has arrived in your inventory!</yellow>"), title, num.toString()))
    fun deliveryDrop(title: String, num: Int): Component = miniMessage.deserialize(fmt2(g("delivery.drop", "<gold>[Chronicler]</gold> <yellow>%s #%s dropped at your feet (inventory full).</yellow>"), title, num.toString()))
    fun subscribeEnabled(): Component = miniMessage.deserialize(g("subscribe.enabled", "<green>You will now receive newspapers automatically.</green>"))
    fun subscribeDisabled(): Component = miniMessage.deserialize(g("subscribe.disabled", "<red>You will no longer receive newspapers automatically.</red>"))

    private fun g(key: String, default: String): String {
        return config.getString(key, default) ?: default
    }

    private fun fmt1(template: String, arg: Any): String {
        return template.replaceFirst("%s", arg.toString()).replaceFirst("%d", arg.toString())
    }

    private fun fmt2(template: String, arg1: String, arg2: String): String {
        val first = template.indexOf('%')
        if (first < 0) return template
        val second = template.indexOf('%', first + 1)
        if (second < 0) return template.replaceFirst("%s", arg1).replaceFirst("%d", arg1)
        val before = template.substring(0, second)
        val after = template.substring(second)
        val firstReplaced = before.replaceFirst("%s", arg1).replaceFirst("%d", arg1)
        return firstReplaced + after.replaceFirst("%s", arg2).replaceFirst("%d", arg2)
    }
}
