package uk.ewancroft.chronicler.tracker

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.block.Biome
import uk.ewancroft.chronicler.config.TrackingConfig
import uk.ewancroft.chronicler.news.ChronicleEvent
import uk.ewancroft.chronicler.news.EventStore
import uk.ewancroft.chronicler.news.EventType
import java.util.UUID

class BuildTracker(
    private val store: EventStore,
    private val tracking: TrackingConfig,
) : Listener {

    private val knownBiomes = mutableMapOf<UUID, MutableSet<String>>()
    private val naturalBlocks = setOf(
        Material.STONE, Material.DIRT, Material.GRASS_BLOCK, Material.COBBLESTONE,
        Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.DEEPSLATE,
        Material.TUFF, Material.NETHERRACK, Material.END_STONE, Material.SAND,
        Material.GRAVEL, Material.SANDSTONE, Material.RED_SANDSTONE,
        Material.OAK_LOG, Material.DARK_OAK_LOG, Material.BIRCH_LOG,
        Material.SPRUCE_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG,
        Material.CHERRY_LOG, Material.MANGROVE_LOG, Material.WARPED_STEM,
        Material.CRIMSON_STEM, Material.OAK_LEAVES, Material.DARK_OAK_LEAVES,
        Material.BIRCH_LEAVES, Material.SPRUCE_LEAVES, Material.JUNGLE_LEAVES,
        Material.ACACIA_LEAVES, Material.CHERRY_LEAVES, Material.MANGROVE_LEAVES,
        Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES,
        Material.WATER, Material.LAVA, Material.SNOW_BLOCK, Material.ICE,
        Material.PACKED_ICE, Material.BLUE_ICE, Material.CLAY,
        Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
        Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
        Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA,
        Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
        Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA, Material.BLUE_TERRACOTTA,
        Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA,
        Material.BLACK_TERRACOTTA, Material.CALCITE, Material.DRIPSTONE_BLOCK,
        Material.POINTED_DRIPSTONE, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
        Material.BASALT, Material.BLACKSTONE, Material.SOUL_SAND, Material.SOUL_SOIL,
        Material.GLOWSTONE, Material.SHROOMLIGHT, Material.AMETHYST_BLOCK,
        Material.BUDDING_AMETHYST, Material.COPPER_ORE, Material.IRON_ORE,
        Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE,
        Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.COAL_ORE,
    )

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!tracking.blocks) return
        val player = event.player
        store.record(
            ChronicleEvent(
                type = EventType.BLOCK_PLACE,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
                details = mapOf(
                    "block" to event.block.type.name.lowercase(),
                    "x" to event.block.x.toString(),
                    "z" to event.block.z.toString(),
                ),
            )
        )
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!tracking.blocks) return
        val block = event.block
        if (block.type in naturalBlocks) return
        val player = event.player
        store.record(
            ChronicleEvent(
                type = EventType.BLOCK_BREAK,
                timestamp = System.currentTimeMillis(),
                playerName = player.name,
                playerUuid = player.uniqueId.toString(),
                world = player.world.name,
                details = mapOf(
                    "block" to block.type.name.lowercase(),
                    "x" to block.x.toString(),
                    "z" to block.z.toString(),
                ),
            )
        )
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!tracking.exploration) return
        val player = event.player
        val to = event.to ?: return
        val biomes = knownBiomes.getOrPut(player.uniqueId) { mutableSetOf() }
        val biome = player.world.getBiome(to.blockX, to.blockY, to.blockZ)
        val biomeName = biome.key.value

        if (biomeName !in biomes) {
            biomes.add(biomeName)
            if (biomes.size > 1) {
                store.record(
                    ChronicleEvent(
                        type = EventType.BIOME_DISCOVERY,
                        timestamp = System.currentTimeMillis(),
                        playerName = player.name,
                        playerUuid = player.uniqueId.toString(),
                        world = player.world.name,
                        details = mapOf("biome" to biomeName),
                    )
                )
            }
        }
    }
}
