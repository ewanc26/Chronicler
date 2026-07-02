package uk.ewancroft.chronicler.news

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChronicleEventSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Test
    fun `serialize and deserialize ChronicleEvent`() {
        val event = ChronicleEvent(
            type = EventType.DEATH,
            timestamp = 1000L,
            playerName = "ewanc26",
            playerUuid = "550e8400-e29b-41d4-a716-446655440000",
            world = "world",
            details = mapOf("message" to "ewanc26 fell from a high place", "killer" to "environment"),
        )

        val encoded = json.encodeToString(event)
        assertContains(encoded, "DEATH")
        assertContains(encoded, "ewanc26")

        val decoded: ChronicleEvent = json.decodeFromString(encoded)
        assertEquals(event.type, decoded.type)
        assertEquals(event.playerName, decoded.playerName)
        assertEquals(event.details["message"], decoded.details["message"])
    }

    @Test
    fun `serialize and deserialize Newspaper`() {
        val newspaper = Newspaper(
            issueNumber = 1,
            fromTime = 0L,
            toTime = 1000L,
            sections = listOf(
                NewspaperSection(
                    title = "Headlines",
                    stories = listOf(
                        Story("Test Headline", "Test body text.", listOf("ewanc26"), EventType.DEATH),
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(newspaper)
        assertContains(encoded, "Test Headline")

        val decoded: Newspaper = json.decodeFromString(encoded)
        assertEquals(1, decoded.issueNumber)
        assertEquals(1, decoded.sections.size)
        assertEquals("Test Headline", decoded.sections[0].stories[0].headline)
    }

    @Test
    fun `deserialize legacy event with missing fields`() {
        val legacyJson = """{"type":"DEATH","timestamp":1000,"playerName":"ewanc26","playerUuid":"uuid","world":"world"}"""
        val event: ChronicleEvent = json.decodeFromString(legacyJson)
        assertEquals(EventType.DEATH, event.type)
        assertNotNull(event.details)
        assertEquals(emptyMap(), event.details)
    }

    @Test
    fun `all EventType values are serializable`() {
        for (type in EventType.entries) {
            val event = ChronicleEvent(type, 0L, "p", "u", "w")
            val encoded = json.encodeToString(event)
            val decoded: ChronicleEvent = json.decodeFromString(encoded)
            assertEquals(type, decoded.type)
        }
    }
}
