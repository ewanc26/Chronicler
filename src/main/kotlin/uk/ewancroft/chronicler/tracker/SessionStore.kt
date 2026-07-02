package uk.ewancroft.chronicler.tracker

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class SessionData(
    var playerName: String,
    val playerUuid: String,
    var totalPlaytimeTicks: Long = 0,
    var sessionCount: Int = 0,
    var currentStreak: Int = 0,
    var longestStreak: Int = 0,
    var lastLoginDate: String = "",
    var lastSeen: Long = 0,
)

class SessionStore(private val dataPath: Path) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val sessions = mutableMapOf<String, SessionData>()

    fun getOrCreate(uuid: String, name: String): SessionData {
        val data = sessions.getOrPut(uuid) {
            loadPlayer(uuid) ?: SessionData(playerName = name, playerUuid = uuid)
        }
        data.playerName = name
        return data
    }

    fun get(uuid: String): SessionData? = sessions[uuid]

    fun getAll(): List<SessionData> = sessions.values.toList()

    fun savePlayer(data: SessionData) {
        sessions[data.playerUuid] = data
    }

    fun save() {
        synchronized(sessions) {
            Files.createDirectories(dataPath.parent)
            val map = sessions.mapValues { (_, v) -> v }
            dataPath.toFile().writeText(json.encodeToString(map))
        }
    }

    fun load() {
        if (Files.exists(dataPath)) {
            try {
                val text = dataPath.toFile().readText()
                val loaded: Map<String, SessionData> = json.decodeFromString(text)
                synchronized(sessions) {
                    sessions.clear()
                    sessions.putAll(loaded)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadPlayer(uuid: String): SessionData? {
        return try {
            if (Files.exists(dataPath)) {
                val text = dataPath.toFile().readText()
                val loaded: Map<String, SessionData> = json.decodeFromString(text)
                loaded[uuid]
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
