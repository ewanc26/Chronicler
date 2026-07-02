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
    SESSION_START,
    SESSION_END,
    MILESTONE_LOGIN_STREAK,
    MILESTONE_PLAYTIME,
    MESSAGE_SENT,
    ORE_DISCOVERY,
    DISTANCE_MILESTONE,
    END_ENTER,
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

@Serializable
data class Newspaper(
    val issueNumber: Int,
    val fromTime: Long,
    val toTime: Long,
    val sections: List<NewspaperSection>,
)

@Serializable
data class NewspaperSection(
    val title: String,
    val stories: List<Story>,
)

@Serializable
data class Story(
    val headline: String,
    val body: String,
    val players: List<String>,
    val eventType: EventType?,
)
