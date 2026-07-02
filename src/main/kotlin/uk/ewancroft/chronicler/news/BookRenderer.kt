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

    private companion object {
        val ACCENT = TextColor.color(0x6B3E00)
        val PRIMARY_TEXT = TextColor.color(0x1F1A14)
        val SECONDARY_TEXT = TextColor.color(0x3D342A)
        val MUTED_TEXT = TextColor.color(0x5C4F40)
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
        val pages = mutableListOf<Component>()

        val titlePage = Component.text()
            .append(Component.text("${newspaperConfig.title}\n", ACCENT))
            .append(Component.text("Issue #${newspaper.issueNumber}\n", SECONDARY_TEXT))
            .append(Component.newline())
            .append(Component.text("A chronicle of events past.", MUTED_TEXT))
            .build()
        pages.add(titlePage)

        for (section in newspaper.sections) {
            for (story in section.stories) {
                val page = Component.text()
                page.append(Component.text("${section.title.uppercase()}\n", ACCENT, TextDecoration.BOLD))
                page.append(Component.text("${"=".repeat(Math.min(section.title.length, 20))}\n\n", MUTED_TEXT))
                page.append(Component.text("${story.headline}\n", PRIMARY_TEXT, TextDecoration.BOLD))
                page.append(Component.text("By ${story.byline}\n", MUTED_TEXT, TextDecoration.ITALIC))
                page.append(Component.newline())
                page.append(Component.text("${story.body}\n", SECONDARY_TEXT))
                if (story.players.isNotEmpty()) {
                    page.append(Component.text("Filed under: ${story.players.joinToString(", ")}\n", MUTED_TEXT))
                }
                pages.add(page.build())
            }
        }

        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
        val footer = Component.text()
            .append(Component.newline())
            .append(Component.text("— End of Issue —\n", MUTED_TEXT))
            .append(Component.text("Published: $now", MUTED_TEXT))
            .build()
        pages.add(footer)

        return pages
    }
}
