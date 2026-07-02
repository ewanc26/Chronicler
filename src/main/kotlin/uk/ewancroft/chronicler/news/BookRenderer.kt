package uk.ewancroft.chronicler.news

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import uk.ewancroft.chronicler.config.NewspaperConfig

class BookRenderer(
    private val newspaperConfig: NewspaperConfig,
) {

    companion object {
        private const val MAX_BODY_CHARACTERS_PER_PAGE = 650

        internal fun splitArticle(body: String, limit: Int = MAX_BODY_CHARACTERS_PER_PAGE): List<String> {
            if (body.length <= limit) return listOf(body)
            val sentences = body.trim().split(Regex("(?<=[.!?])\\s+"))
            val pages = mutableListOf<String>()
            var current = StringBuilder()
            for (sentence in sentences) {
                val units = if (sentence.length > limit) sentence.split(Regex("\\s+")) else listOf(sentence)
                for (unit in units) {
                    if (current.isNotEmpty() && current.length + unit.length + 1 > limit) {
                        pages.add(current.toString())
                        current = StringBuilder()
                    }
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(unit)
                }
            }
            if (current.isNotEmpty()) pages.add(current.toString())
            return pages.ifEmpty { listOf(body) }
        }
    }

    fun renderToBook(newspaper: Newspaper): ItemStack {
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta

        meta.setTitle("${newspaperConfig.title} #${newspaper.issueNumber}")
        meta.setAuthor(newspaperConfig.author)
        meta.setGeneration(BookMeta.Generation.ORIGINAL)

        val pages = renderPages(newspaper)
        meta.addPages(*pages.toTypedArray())

        book.itemMeta = meta
        return book
    }

    private fun renderPages(newspaper: Newspaper): List<Component> {
        val accent = TextColor.color(newspaperConfig.accentColor)
        val primaryText = TextColor.color(newspaperConfig.primaryTextColor)
        val secondaryText = TextColor.color(newspaperConfig.secondaryTextColor)
        val mutedText = TextColor.color(newspaperConfig.mutedTextColor)
        val pages = mutableListOf<Component>()

        val titlePage = Component.text()
            .append(Component.text("${newspaperConfig.title}\n", accent))
            .append(Component.text("Issue #${newspaper.issueNumber}\n", secondaryText))
            .append(Component.newline())
            .append(Component.text(newspaperConfig.titlePageText, mutedText))
            .build()
        pages.add(titlePage)

        for (section in newspaper.sections) {
            for (story in section.stories) {
                val parts = splitArticle(story.body)
                parts.forEachIndexed { index, part ->
                    val page = Component.text()
                    page.append(Component.text("${section.title.uppercase()}\n", accent, TextDecoration.BOLD))
                    page.append(Component.text("${"=".repeat(Math.min(section.title.length, 20))}\n\n", mutedText))
                    val headline = if (index == 0) story.headline else "${story.headline} (cont.)"
                    page.append(Component.text("$headline\n", primaryText, TextDecoration.BOLD))
                    if (index == 0) {
                        page.append(Component.text("By ${story.byline}\n", mutedText, TextDecoration.ITALIC))
                        page.append(Component.newline())
                    }
                    page.append(Component.text("$part\n", secondaryText))
                    if (index == parts.lastIndex && story.players.isNotEmpty()) {
                        page.append(Component.text("Filed under: ${story.players.joinToString(", ")}\n", mutedText))
                    }
                    if (index < parts.lastIndex) page.append(Component.text("\nContinued on next page…", mutedText, TextDecoration.ITALIC))
                    pages.add(page.build())
                }
            }
        }

        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
        val footer = Component.text()
            .append(Component.newline())
            .append(Component.text("— End of Issue —\n", mutedText))
            .append(Component.text("Published: $now", mutedText))
            .build()
        pages.add(footer)

        return pages
    }

}
