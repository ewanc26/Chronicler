package uk.ewancroft.chronicler.news

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class ArchiveStore(private val archiveDir: Path) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val archives = mutableListOf<Newspaper>()

    fun archive(newspaper: Newspaper) {
        synchronized(archives) {
            archives.add(newspaper)
            try {
                Files.createDirectories(archiveDir)
                val file = archiveDir.resolve("issue-${newspaper.issueNumber}.json")
                file.toFile().writeText(json.encodeToString(newspaper))
            } catch (_: Exception) {
            }
        }
    }

    fun getAll(): List<Newspaper> {
        synchronized(archives) {
            return archives.toList().sortedByDescending { it.issueNumber }
        }
    }

    fun getIssue(number: Int): Newspaper? {
        synchronized(archives) {
            return archives.find { it.issueNumber == number }
                ?: loadIssue(number)
        }
    }

    fun latest(n: Int = 10): List<Newspaper> {
        return getAll().take(n)
    }

    fun loadAll() {
        if (Files.exists(archiveDir)) {
            try {
                val files = archiveDir.toFile().listFiles { f -> f.name.endsWith(".json") }
                    ?.sortedBy { it.name }
                if (files != null) {
                    synchronized(archives) {
                        archives.clear()
                        for (file in files) {
                            try {
                                val text = file.readText()
                                val newspaper: Newspaper = json.decodeFromString(text)
                                archives.add(newspaper)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun loadIssue(number: Int): Newspaper? {
        return try {
            val file = archiveDir.resolve("issue-$number.json")
            if (Files.exists(file)) {
                val text = file.toFile().readText()
                json.decodeFromString<Newspaper>(text)
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
