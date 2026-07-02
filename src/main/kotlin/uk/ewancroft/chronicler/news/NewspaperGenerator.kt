package uk.ewancroft.chronicler.news

import uk.ewancroft.chronicler.config.NewspaperConfig
import uk.ewancroft.chronicler.llm.ArticleResult
import uk.ewancroft.chronicler.llm.LlmProvider
import java.util.logging.Logger

class NewspaperGenerator(
    private val store: EventStore,
    private val newspaperConfig: NewspaperConfig,
    private val llmProvider: LlmProvider?,
    private val llmEnabled: Boolean,
    private val logger: Logger,
) {
    private val maxStories = newspaperConfig.storiesPerSection

    fun generate(issueNumber: Int, fromTime: Long, toTime: Long): Newspaper {
        val events = store.eventsSince(fromTime).filter { it.timestamp <= toTime }
        val sections = mutableListOf<NewspaperSection>()

        sections.addAll(generateHeadlines(events))
        sections.addAll(generateObituaries(events))
        sections.addAll(generateAchievements(events))
        sections.addAll(generateHuntingGrounds(events))
        sections.addAll(generateExplorationAndBuilding(events))
        sections.addAll(generateEconomyAndTrading(events))
        sections.addAll(generateSocial(events))
        sections.addAll(generateBreakingNews(events))
        if (newspaperConfig.showStatistics) {
            sections.add(generateStatistics(events))
        }

        return Newspaper(issueNumber, fromTime, toTime, sections)
    }

    private fun generateHeadlines(events: List<ChronicleEvent>): List<NewspaperSection> {
        val deathEvents = events.filter { it.type == EventType.DEATH }
        val pvpEvents = events.filter { it.type == EventType.PVP_KILL }
        val advEvents = events.filter { it.type == EventType.ADVANCEMENT }
        val endEvents = events.filter { it.type == EventType.END_ENTER }

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
            if (endEvents.isNotEmpty()) {
                appendLine()
                appendLine("${endEvents.size} player(s) entered The End for the first time!")
            }
        }

        val stories = generateArticles("Headlines", summary, events, maxStories = 3) ?: run {
            val s = mutableListOf<Story>()
            if (deathEvents.isEmpty() && pvpEvents.isEmpty() && advEvents.isEmpty() && endEvents.isEmpty()) {
                s.add(Story("Quiet Days", "No major events to report.", emptyList(), null))
            }
            if (deathEvents.isNotEmpty()) {
                s.add(Story("Casualties", "${deathEvents.size} deaths recorded this cycle.", emptyList(), EventType.DEATH))
            }
            if (pvpEvents.isNotEmpty()) {
                val top = pvpEvents.groupBy { it.details["killer"] ?: "unknown" }.maxByOrNull { it.value.size }
                if (top != null) s.add(Story("PvP Report", "${top.key} leads with ${top.value.size} kills.", listOf(top.key), EventType.PVP_KILL))
            }
            if (endEvents.isNotEmpty()) {
                val players = endEvents.map { it.playerName }.distinct()
                s.add(Story("Into The End", "${players.size} brave explorer(s) ventured into the End.", players, EventType.END_ENTER))
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
        val oreEvents = events.filter { it.type == EventType.ORE_DISCOVERY }
        if (biomeEvents.isEmpty() && placeEvents.isEmpty() && oreEvents.isEmpty()) return emptyList()

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
            if (oreEvents.isNotEmpty()) {
                appendLine()
                appendLine("New ore discoveries (${oreEvents.size} total):")
                oreEvents.groupBy { it.playerName }.entries.take(3).forEach { (player, ores) ->
                    val types = ores.mapNotNull { it.details["ore"] }.distinct()
                    appendLine("- $player discovered: ${types.joinToString(", ")}")
                }
            }
        }

        val stories = generateArticles("Exploration & Building", summary, biomeEvents + placeEvents + oreEvents, maxStories = 5) ?: run {
            val s = mutableListOf<Story>()
            if (biomeEvents.isNotEmpty()) {
                val top = biomeEvents.groupBy { it.playerName }.maxByOrNull { it.value.size }
                if (top != null) s.add(Story("Explorer: ${top.key}", "Discovered ${top.value.size} new biome(s)", listOf(top.key), EventType.BIOME_DISCOVERY))
            }
            if (oreEvents.isNotEmpty()) {
                val top = oreEvents.groupBy { it.playerName }.maxByOrNull { it.value.size }
                if (top != null) {
                    val types = top.value.mapNotNull { it.details["ore"] }.distinct()
                    s.add(Story("Prospector: ${top.key}", "Discovered ${types.size} ore type(s): ${types.joinToString(", ")}", listOf(top.key), EventType.ORE_DISCOVERY))
                }
            }
            if (placeEvents.isNotEmpty()) {
                val top = placeEvents.groupBy { it.playerName }.maxByOrNull { it.value.size }
                if (top != null) s.add(Story("Builder: ${top.key}", "Placed ${top.value.size} blocks", listOf(top.key), EventType.BLOCK_PLACE))
            }
            s.take(maxStories)
        }

        return listOf(NewspaperSection("Exploration & Building", stories))
    }

    private fun generateEconomyAndTrading(events: List<ChronicleEvent>): List<NewspaperSection> {
        val tradeEvents = events.filter { it.type == EventType.TRADE }
        if (tradeEvents.isEmpty()) return emptyList()

        val summary = buildString {
            appendLine("Economic activity (${tradeEvents.size} transactions):")
            tradeEvents.groupBy { it.playerName }.entries.sortedByDescending { it.value.size }.take(3).forEach { (player, trades) ->
                val total = trades.mapNotNull { it.details["amount"]?.toDoubleOrNull() }.sum()
                appendLine("- $player: ${trades.size} transactions (total: ${String.format("%.2f", total)})")
            }
        }

        val stories = generateArticles("Economy & Trading", summary, tradeEvents, maxStories = 3) ?: run {
            val s = mutableListOf<Story>()
            val byPlayer = tradeEvents.groupBy { it.playerName }
            val topTrader = byPlayer.maxByOrNull { it.value.size }
            if (topTrader != null) {
                val total = topTrader.value.mapNotNull { it.details["amount"]?.toDoubleOrNull() }.sum()
                s.add(Story("Top Trader: ${topTrader.key}", "${topTrader.value.size} transactions (total: ${String.format("%.2f", total)})", listOf(topTrader.key), EventType.TRADE))
            }
            val totals = tradeEvents.mapNotNull { it.details["amount"]?.toDoubleOrNull() }
            if (totals.isNotEmpty()) {
                s.add(Story("Market Activity", "${tradeEvents.size} transactions totaling ${String.format("%.2f", totals.sum())}", emptyList(), EventType.TRADE))
            }
            s.take(maxStories)
        }

        return listOf(NewspaperSection("Economy & Trading", stories))
    }

    private fun generateSocial(events: List<ChronicleEvent>): List<NewspaperSection> {
        val msgEvents = events.filter { it.type == EventType.MESSAGE_SENT }
        if (msgEvents.isEmpty()) return emptyList()

        val totalMessages = msgEvents.size
        val byPlayer = msgEvents.groupBy { it.playerName }
        val topChatter = byPlayer.maxByOrNull { it.value.size }

        val stories = mutableListOf<Story>()
        stories.add(Story("Messages Sent", "$totalMessages private messages were sent this cycle.", emptyList(), EventType.MESSAGE_SENT))
        if (topChatter != null) {
            stories.add(Story("Most Social: ${topChatter.key}", "${topChatter.value.size} private messages sent.", listOf(topChatter.key), EventType.MESSAGE_SENT))
        }

        return listOf(NewspaperSection("Social", stories.take(maxStories)))
    }

    private fun generateBreakingNews(events: List<ChronicleEvent>): List<NewspaperSection> {
        val endEvents = events.filter { it.type == EventType.END_ENTER }
        if (endEvents.isEmpty()) return emptyList()

        val players = endEvents.map { it.playerName }.distinct()
        val stories = listOf(
            Story(
                "Breaking: Adventurers Reach The End",
                "In a monumental achievement, ${players.size} player(s) ventured into The End dimension for the first time. The expedition was led by ${players.firstOrNull() ?: "unknown"}.",
                players,
                EventType.END_ENTER,
            )
        )

        return listOf(NewspaperSection("Breaking News", stories))
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
                stats.add(Story(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, "$count events", emptyList(), type))
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
        if (!llmEnabled || llmProvider == null || summary.isBlank()) return null

        val systemPrompt = llmProvider.let {
            "You are the editor of a Minecraft server newspaper. Write concise, vivid articles about in-game events. Use a dry, slightly humorous tone.\n\nEach response must be exactly:\n---HEADLINE\n(headline, max 60 chars)\n---BODY\n(2-4 sentence article body)"
        }

        return try {
            val result = llmProvider.generate(systemPrompt, sectionTitle, summary)
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
