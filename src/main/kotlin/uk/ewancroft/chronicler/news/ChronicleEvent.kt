package uk.ewancroft.chronicler.news

import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
    DEATH,
    KILL,
    PVP_KILL,
    ADVANCEMENT,
    BLOCK_BREAK,
    BLOCK_PLACE,
    BIOME_DISCOVERY,
    FIRST_JOIN,
    FIRST_DEATH,
    MILESTONE,
    PLAYER_JOIN,
    PLAYER_LEAVE,
    TRADE,
}

@Serializable
data class ChronicleEvent(
    val type: EventType,
    val timestamp: Long,
    val playerName: String,
    val playerUuid: String,
    val world: String,
    val details: Map<String, String> = emptyMap(),
)

data class Newspaper(
    val issueNumber: Int,
    val fromTime: Long,
    val toTime: Long,
    val sections: List<NewspaperSection>,
)

data class NewspaperSection(
    val title: String,
    val stories: List<Story>,
)

data class Story(
    val headline: String,
    val body: String,
    val players: List<String>,
    val eventType: EventType?,
)
