package uk.ewancroft.chronicler.tracker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SessionStoreTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `getOrCreate creates new session data`() {
        val store = SessionStore(tempDir.resolve("sessions.json"))
        val data = store.getOrCreate("uuid-1", "ewanc26")
        assertEquals("ewanc26", data.playerName)
        assertEquals("uuid-1", data.playerUuid)
        assertEquals(0, data.sessionCount)
        assertEquals(0L, data.totalPlaytimeTicks)
    }

    @Test
    fun `getOrCreate returns existing data for same uuid`() {
        val store = SessionStore(tempDir.resolve("sessions.json"))
        val data1 = store.getOrCreate("uuid-1", "ewanc26")
        data1.sessionCount = 5
        store.savePlayer(data1)

        val data2 = store.getOrCreate("uuid-1", "ewanc26")
        assertEquals(5, data2.sessionCount)
    }

    @Test
    fun `get returns null for unknown uuid`() {
        val store = SessionStore(tempDir.resolve("sessions.json"))
        assertNull(store.get("nonexistent"))
    }

    @Test
    fun `get returns data for known uuid`() {
        val store = SessionStore(tempDir.resolve("sessions.json"))
        store.getOrCreate("uuid-1", "ewanc26")
        assertNotNull(store.get("uuid-1"))
    }

    @Test
    fun `getAll returns all stored sessions`() {
        val store = SessionStore(tempDir.resolve("sessions.json"))
        store.getOrCreate("uuid-1", "a")
        store.getOrCreate("uuid-2", "b")
        store.getOrCreate("uuid-3", "c")

        assertEquals(3, store.getAll().size)
    }

    @Test
    fun `save and load persists data`() {
        val path = tempDir.resolve("sessions.json")
        val store1 = SessionStore(path)
        val data = store1.getOrCreate("uuid-1", "ewanc26")
        data.sessionCount = 10
        data.totalPlaytimeTicks = 5000
        data.currentStreak = 3
        data.longestStreak = 7
        store1.save()

        val store2 = SessionStore(path)
        store2.load()
        val loaded = store2.get("uuid-1")
        assertNotNull(loaded)
        assertEquals("ewanc26", loaded.playerName)
        assertEquals(10, loaded.sessionCount)
        assertEquals(5000L, loaded.totalPlaytimeTicks)
        assertEquals(3, loaded.currentStreak)
        assertEquals(7, loaded.longestStreak)
    }

    @Test
    fun `load with missing file does nothing`() {
        val store = SessionStore(tempDir.resolve("nonexistent.json"))
        store.load()
        assert(store.getAll().isEmpty())
    }

    @Test
    fun `load with corrupt file does nothing`() {
        val path = tempDir.resolve("corrupt.json")
        path.toFile().writeText("not valid json")
        val store = SessionStore(path)
        store.load()
        assert(store.getAll().isEmpty())
    }

    @Test
    fun `savePlayer updates existing data`() {
        val store = SessionStore(tempDir.resolve("sessions.json"))
        val data = store.getOrCreate("uuid-1", "ewanc26")
        data.sessionCount = 3
        store.savePlayer(data)

        val updated = store.getOrCreate("uuid-1", "ewanc26_new_name")
        assertEquals(3, updated.sessionCount)
        assertEquals("ewanc26_new_name", updated.playerName)
    }
}
