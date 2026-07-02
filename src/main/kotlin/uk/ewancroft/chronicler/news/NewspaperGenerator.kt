package uk.ewancroft.chronicler.news

import uk.ewancroft.chronicler.config.NewspaperConfig
import uk.ewancroft.chronicler.llm.OllamaClient
import java.util.logging.Logger

class NewspaperGenerator(
    private val store: EventStore,
    private val newspaperConfig: NewspaperConfig,
    private val llmClient: OllamaClient?,
    private val llmEnabled: Boolean,
    private val logger: Logger,
) {

    fun generate(issueNumber: Int, fromTime: Long, toTime: Long): Newspaper {
        val events = store.eventsSince(fromTime).filter { it.timestamp <= toTime }
        val sections = mutableListOf<NewspaperSection>()
        val maxStories = newspaperConfig.storiesPerSection

        sections.addAll(generateHeadlines(events))
        sections.addAll(generateObituaries(events))
        sections.addAll(generateAchievements(events))
        sections.addAll(generateHuntingGrounds(events))
        sections.addAll(generateExplorationAndBuilding(events))
        if (newspaperConfig.showStatistics) {
            sections.add(generateStatistics(events))
        }

        return Newspaper(issueNumber, fromTime, toTime, sections)
    }

    private fun generateHeadlines(events: List<ChronicleEvent>): List<NewspaperSection> {
        val deathEvents = events.filter { it.type == EventType.DEATH }
        val pvpEvents = events.filter { it.type == EventType.PVP_KILL }
        val advEvents = events.filter { it.type == EventType.ADVANCEMENT }

        val summary = buildString {
            if (deathEvents.isNotEmpty()) {
                appendLine("Deaths (${deathEvents.size} total):")
                deathEvents.groupBy { it.details["message"] ?: "died" }.entries.take(5).forEach { (msg, evs) ->
                    appendLine("- ${evs.size}x: ${msg.take(100)} (${evs.map { it.playerName }.distinct().joinToString(", ")})")
                }
                appendLine()
            }
            if (pvpEvents.isNotEmpty()) {
                appendLine("PvP kills (${pvpEvents.size} total):")
                pvpEvents.groupBy { it.details["killer"] ?: "unknown" }.entries.take(5).forEach { (killer, evs) ->
                    appendLine("- $killer killed ${evs.size} player(s)")
                }
                appendLine()
            }
            if (advEvents.isNotEmpty()) {
                appendLine("Notable advancements (${advEvents.size} total):")
                advEvents.groupBy { it.details["displayName"] ?: "unknown" }.entries.take(3).forEach { (adv, evs) ->
                    appendLine("- \"$adv\" earned by ${evs.size} player(s)")
                }
            }
        }

        val stories = generateArticles("Headlines", summary, events, maxStories = 3) ?: run {
            val s = mutableListOf<Story>()
            if (deathEvents.isEmpty() && pvpEvents.isEmpty() && advEvents.isEmpty()) {
                s.add(Story("Quiet Days", "No major events to report.", emptyList(), null))
            }
            if (deathEvents.isNotEmpty()) {
                s.add(Story("Casualties", "${deathEvents.size} deaths recorded this cycle.", emptyList(), EventType.DEATH))
            }
            if (pvpEvents.isNotEmpty()) {
                val top = pvpEvents.groupBy { it.details["killer"] ?: "unknown" }.maxByOrNull { it.value.size }
                if (top != null) s.add(Story("PvP Report", "${top.key} leads with ${top.value.size} kills.", listOf(top.key), EventType.PVP_KILL))
            }
            s.take(maxStories)
        }

        return listOf(NewspaperSection("Headlines", stories))
    }

    private fun generateObituaries(events: List<ChronicleEvent>): List<NewspaperSection> {
        val deathEvents = events.filter { it.type == EventType.DEATH }
        if (deathEvents.isEmpty()) return emptyList()

        val summary = buildString {
            appendLine("Obituary report — ${deathEvents.size} deaths:")
            deathEvents.groupBy { it.playerName }.entries.sortedByDescending { it.value.size }.take(5).forEach { (player, deaths) ->
                val causes = deaths.map { it.details["message"] ?: "died" }.distinct()
                appendLine("- $player: ${deaths.size} deaths (causes: ${causes.take(3).joinToString(", ")}${if (causes.size > 3) "..." else ""})")
            }
        }

        val stories = generateArticles("Obituaries", summary, deathEvents, maxStories = 5) ?: run {
            deathEvents.groupBy { it.playerName }.entries
                .sortedByDescending { it.value.size }
                .take(maxStories)
                .map { (player, deaths) ->
                    val cause = deaths.last().details["message"] ?: "died"
                    Story("$player — ${deaths.size} deaths", "Last seen: $cause", listOf(player), EventType.DEATH)
                }
        }

        return listOf(NewspaperSection("Obituaries", stories))
    }

    private fun generateAchievements(events: List<ChronicleEvent>): List<NewspaperSection> {
        val advEvents = events.filter { it.type == EventType.ADVANCEMENT }
        if (advEvents.isEmpty()) return emptyList()

        val summary = buildString {
            appendLine("Achievements unlocked (${advEvents.size} total):")
            advEvents.groupBy { it.details["displayName"] ?: it.details["advancement"] ?: "unknown" }.entries.take(5).forEach { (adv, evs) ->
                appendLine("- \"$adv\" — ${evs.map { it.playerName }.distinct().joinToString(", ")}")
            }
        }

        val stories = generateArticles("Achievements", summary, advEvents, maxStories = 5) ?: run {
            advEvents.distinctBy { it.details["advancement"] }.take(maxStories).map { event ->
                val displayName = event.details["displayName"] ?: event.details["advancement"] ?: "unknown"
                val count = advEvents.count { it.details["advancement"] == event.details["advancement"] }
                Story(
                    if (displayName.length > 40) displayName.take(37) + "..." else displayName,
                    "Achieved by $count player(s). First: ${event.playerName}",
                    listOf(event.playerName),
                    EventType.ADVANCEMENT,
                )
            }
        }

        return listOf(NewspaperSection("Achievements", stories))
    }

    private fun generateHuntingGrounds(events: List<ChronicleEvent>): List<NewspaperSection> {
        val pvpEvents = events.filter { it.type == EventType.PVP_KILL }
        val killEvents = events.filter { it.type == EventType.KILL }
        if (pvpEvents.isEmpty() && killEvents.isEmpty()) return emptyList()

        val summary = buildString {
            if (killEvents.isNotEmpty()) {
                appendLine("Mob kills (${killEvents.size} total):")
                killEvents.groupBy { it.playerName }.entries.sortedByDescending { it.value.size }.take(3).forEach { (player, kills) ->
                    val fav = kills.groupBy { it.details["entity"] ?: "unknown" }.maxByOrNull { it.value.size }
                    appendLine("- $player: ${kills.size} kills (favorite prey: ${fav?.key ?: "various"})")
                }
                appendLine()
            }
            if (pvpEvents.isNotEmpty()) {
                appendLine("Player kills (${pvpEvents.size} total):")
                pvpEvents.groupBy { it.details["killer"] ?: "unknown" }.entries.sortedByDescending { it.value.size }.take(3).forEach { (killer, kills) ->
                    appendLine("- $killer: ${kills.size} player kills")
                }
            }
        }

        val stories = generateArticles("Hunting Grounds", summary, killEvents + pvpEvents, maxStories = 5) ?: run {
            val s = mutableListOf<Story>()
            val byKiller = killEvents.groupBy { it.playerName }
            val topHunter = byKiller.maxByOrNull { it.value.size }
            if (topHunter != null) {
                val fav = topHunter.value.groupBy { it.details["entity"] ?: "unknown" }.maxByOrNull { it.value.size }
                s.add(Story("Top Hunter: ${topHunter.key}", "${topHunter.value.size} kills this cycle. Favorite: ${fav?.key ?: "various"}", listOf(topHunter.key), EventType.KILL))
            }
            if (pvpEvents.isNotEmpty()) {
                val top = pvpEvents.groupBy { it.details["killer"] ?: "unknown" }.maxByOrNull { it.value.size }
                if (top != null) s.add(Story("PvP Leader: ${top.key}", "${top.value.size} player kills", listOf(top.key), EventType.PVP_KILL))
            }
            s.take(maxStories)
        }

        return listOf(NewspaperSection("Hunting Grounds", stories))
    }

    private fun generateExplorationAndBuilding(events: List<ChronicleEvent>): List<NewspaperSection> {
        val biomeEvents = events.filter { it.type == EventType.BIOME_DISCOVERY }
        val placeEvents = events.filter { it.type == EventType.BLOCK_PLACE }
        val breakEvents = events.filter { it.type == EventType.BLOCK_BREAK }
        if (biomeEvents.isEmpty() && placeEvents.isEmpty()) return emptyList()

        val summary = buildString {
            if (biomeEvents.isNotEmpty()) {
                appendLine("New biome discoveries (${biomeEvents.size} total):")
                biomeEvents.groupBy { it.playerName }.entries.sortedByDescending { it.value.size }.take(3).forEach { (player, disc) ->
                    val biomes = disc.mapNotNull { it.details["biome"] }.distinct()
                    appendLine("- $player discovered ${biomes.size} new biome(s): ${biomes.take(3).joinToString(", ")}${if (biomes.size > 3) "..." else ""}")
                }
                appendLine()
            }
            if (placeEvents.isNotEmpty()) {
                appendLine("Building activity (${placeEvents.size} blocks placed, ${breakEvents.size} broken):")
                placeEvents.groupBy { it.playerName }.entries.sortedByDescending { it.value.size }.take(3).forEach { (player, places) ->
                    val fav = places.groupBy { it.details["block"] ?: "unknown" }.maxByOrNull { it.value.size }
                    appendLine("- $player placed ${places.size} blocks (favorite: ${fav?.key ?: "various"})")
                }
            }
        }

        val stories = generateArticles("Exploration & Building", summary, biomeEvents + placeEvents, maxStories = 5) ?: run {
            val s = mutableListOf<Story>()
            if (biomeEvents.isNotEmpty()) {
                val top = biomeEvents.groupBy { it.playerName }.maxByOrNull { it.value.size }
                if (top != null) s.add(Story("Explorer: ${top.key}", "Discovered ${top.value.size} new biome(s)", listOf(top.key), EventType.BIOME_DISCOVERY))
            }
            if (placeEvents.isNotEmpty()) {
                val top = placeEvents.groupBy { it.playerName }.maxByOrNull { it.value.size }
                if (top != null) s.add(Story("Builder: ${top.key}", "Placed ${top.value.size} blocks", listOf(top.key), EventType.BLOCK_PLACE))
            }
            s.take(maxStories)
        }

        return listOf(NewspaperSection("Exploration & Building", stories))
    }

    private fun generateStatistics(events: List<ChronicleEvent>): NewspaperSection {
        val stats = mutableListOf<Story>()
        val allPlayers = events.map { it.playerName }.distinct()
        stats.add(Story("Active Players", "${allPlayers.size} unique player(s) this cycle", allPlayers, null))
        stats.add(Story("Total Events", "${events.size} events recorded", emptyList(), null))
        val joinCount = events.count { it.type == EventType.PLAYER_JOIN }
        if (joinCount > 0) stats.add(Story("Logins", "$joinCount total logins", emptyList(), null))
        EventType.entries.forEach { type ->
            val count = events.count { it.type == type }
            if (count > 0) {
                stats.add(Story(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }), "$count events", emptyList(), type)
            }
        }
        return NewspaperSection("Statistics", stats)
    }

    private fun generateArticles(
        sectionTitle: String,
        summary: String,
        events: List<ChronicleEvent>,
        maxStories: Int,
    ): List<Story>? {
        if (!llmEnabled || llmClient == null || summary.isBlank()) return null

        return try {
            val result = llmClient.generateArticle(sectionTitle, summary)
            if (result != null) {
                val players = events.map { it.playerName }.distinct()
                val types = events.map { it.type }.distinct()
                listOf(Story(result.headline, result.body, players, types.firstOrNull()))
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
