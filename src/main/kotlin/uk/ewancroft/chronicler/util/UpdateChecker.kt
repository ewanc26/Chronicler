package uk.ewancroft.chronicler.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.bukkit.plugin.java.JavaPlugin
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UpdateChecker(
    private val plugin: JavaPlugin,
    private val repoOwner: String,
    private val repoName: String,
    private val autoUpdate: Boolean = false,
) {

    fun checkAsync() {
        plugin.logger.info("Checking GitHub Releases for updates (current: ${plugin.pluginMeta.version}, auto-update: $autoUpdate).")
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                val release = fetchLatestRelease() ?: return@Runnable
                val current = plugin.pluginMeta.version
                if (!isNewerVersion(release.version, current)) {
                    plugin.logger.info("$repoName is up to date (version $current).")
                    return@Runnable
                }

                if (!autoUpdate) {
                    logOnMainThread("A new version of $repoName is available: ${release.version} (current: $current). Download at ${release.pageUrl}")
                    return@Runnable
                }

                val asset = release.assets.firstOrNull { it.name.endsWith("-all.jar", ignoreCase = true) }
                    ?: release.assets.firstOrNull { it.name.endsWith(".jar", ignoreCase = true) }
                if (asset == null) {
                    logWarningOnMainThread("Update ${release.version} is available, but its release has no JAR asset.")
                    return@Runnable
                }

                val updateDirectory = plugin.server.updateFolderFile.toPath()
                Files.createDirectories(updateDirectory)
                val destination = updateDirectory.resolve("$repoName.jar")
                plugin.logger.info("Downloading $repoName ${release.version} from asset ${asset.name}.")
                val temporary = Files.createTempFile(updateDirectory, "$repoName-", ".download")
                try {
                    download(asset.downloadUrl, temporary)
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } finally {
                    Files.deleteIfExists(temporary)
                }
                logOnMainThread("Downloaded $repoName ${release.version}. It will be installed on the next server restart.")
            } catch (e: Exception) {
                plugin.logger.warning("Unable to check for $repoName updates: ${e.message}")
            }
        })
    }

    private fun fetchLatestRelease(): Release? {
        val endpoint = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        val connection = openConnection(endpoint)
        return connection.inputStream.bufferedReader().use { reader ->
            val root = Json.parseToJsonElement(reader.readText()).jsonObject
            val tag = (root["tag_name"] as? JsonPrimitive)?.content ?: return@use null
            val pageUrl = (root["html_url"] as? JsonPrimitive)?.content ?: endpoint
            val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
                val asset = element as? JsonObject ?: return@mapNotNull null
                val name = (asset["name"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val url = (asset["browser_download_url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                ReleaseAsset(name, url)
            }
            Release(tag.removePrefix("v"), pageUrl, assets)
        }.also { connection.disconnect() }
    }

    private fun download(url: String, destination: java.nio.file.Path) {
        val connection = openConnection(url)
        try {
            connection.inputStream.use { input ->
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "$repoName/${plugin.pluginMeta.version}")
            connectTimeout = 5000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
    }

    private fun logOnMainThread(message: String) {
        plugin.server.scheduler.runTask(plugin, Runnable { plugin.logger.info(message) })
    }

    private fun logWarningOnMainThread(message: String) {
        plugin.server.scheduler.runTask(plugin, Runnable { plugin.logger.warning(message) })
    }

    private data class Release(val version: String, val pageUrl: String, val assets: List<ReleaseAsset>)
    private data class ReleaseAsset(val name: String, val downloadUrl: String)

    companion object {
        internal fun isNewerVersion(candidate: String, current: String): Boolean {
            val candidateParts = parseVersion(candidate) ?: return false
            val currentParts = parseVersion(current) ?: return candidate != current
            val length = maxOf(candidateParts.size, currentParts.size)
            for (index in 0 until length) {
                val candidatePart = candidateParts.getOrElse(index) { 0 }
                val currentPart = currentParts.getOrElse(index) { 0 }
                if (candidatePart != currentPart) return candidatePart > currentPart
            }
            return false
        }

        private fun parseVersion(version: String): List<Int>? {
            val stableVersion = version.removePrefix("v").substringBefore('-')
            val parts = stableVersion.split('.')
            if (parts.isEmpty()) return null
            return parts.map { it.toIntOrNull() ?: return null }
        }
    }
}
