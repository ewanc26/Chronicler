package uk.ewancroft.chronicler.news

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import uk.ewancroft.chronicler.config.NewspaperConfig

class BookRenderer(
    private val newspaperConfig: NewspaperConfig,
) {

    private val mm = MiniMessage.miniMessage()

    fun renderToBook(newspaper: Newspaper): ItemStack {
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta

        meta.setTitle("${newspaperConfig.title} #${newspaper.issueNumber}")
        meta.setAuthor(newspaperConfig.author)
        meta.setGeneration(BookMeta.Generation.ORIGINAL)

        val pages = renderPages(newspaper)
        meta.pages(pages)

        book.itemMeta = meta
        return book
    }

    private fun renderPages(newspaper: Newspaper): List<Component> {
        val pages = mutableListOf<Component>()

        val titlePage = Component.text()
            .append(Component.text("${newspaperConfig.title}\n", TextColor.color(0xFFAA00)))
            .append(Component.text("Issue #${newspaper.issueNumber}\n", TextColor.color(0xAAAAAA)))
            .append(Component.newline())
            .append(Component.text("A chronicle of events past.", TextColor.color(0x888888)))
            .build()
        pages.add(titlePage)

        for (section in newspaper.sections) {
            val page = Component.text()
            page.append(Component.text("\n${section.title}\n", TextColor.color(0xFFAA00), TextDecoration.BOLD))
            page.append(Component.text("${"=".repeat(Math.min(section.title.length, 20))}\n\n", TextColor.color(0x666666)))

            for ((i, story) in section.stories.withIndex()) {
                page.append(Component.text("${i + 1}. ", TextColor.color(0xFFAA00)))
                page.append(Component.text("${story.headline}\n", TextColor.color(0xFFFFFF), TextDecoration.BOLD))
                page.append(Component.text("${story.body}\n", TextColor.color(0xCCCCCC)))
                if (story.players.isNotEmpty()) {
                    page.append(Component.text("  — ${story.players.joinToString(", ")}\n", TextColor.color(0x888888)))
                }
                if (i < section.stories.size - 1) {
                    page.append(Component.newline())
                }
            }
            pages.add(page.build())
        }

        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
        val footer = Component.text()
            .append(Component.newline())
            .append(Component.text("— End of Issue —\n", TextColor.color(0x888888)))
            .append(Component.text("Published: $now", TextColor.color(0x666666)))
            .build()
        pages.add(footer)

        return pages
    }
}
