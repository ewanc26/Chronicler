package uk.ewancroft.chronicler.util

import org.bukkit.plugin.java.JavaPlugin
import java.net.URI

class UpdateChecker(
    private val plugin: JavaPlugin,
    private val repoOwner: String,
    private val repoName: String,
) {

    fun checkAsync() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val url = URI("https://api.github.com/repos/$repoOwner/$repoName/releases/latest").toURL()
                val conn = url.openConnection()
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.setRequestProperty("User-Agent", repoOwner)
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val body = conn.getInputStream().bufferedReader().readText()

                val tagName = extractTagName(body)
                if (tagName != null) {
                    val latest = tagName.removePrefix("v")
                    val current = plugin.pluginMeta.version
                    if (current != latest) {
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            plugin.logger.info("A new version of $repoName is available: $latest (current: $current). Download at $url")
                        })
                    }
                }
            } catch (_: Exception) {
            }
        })
    }

    private fun extractTagName(json: String): String? {
        val key = "\"tag_name\":\""
        val start = json.indexOf(key)
        if (start == -1) return null
        val valueStart = start + key.length
        val end = json.indexOf('"', valueStart)
        if (end == -1) return null
        return json.substring(valueStart, end)
    }
}
