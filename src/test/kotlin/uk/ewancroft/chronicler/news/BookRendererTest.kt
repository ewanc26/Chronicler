package uk.ewancroft.chronicler.news

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.meta.BookMeta
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import uk.ewancroft.chronicler.config.NewspaperConfig
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BookRendererTest {

    private val config = NewspaperConfig(
        title = "Test Chronicle",
        author = "Tester",
        storiesPerSection = 5,
        showStatistics = true,
    )

    @BeforeEach
    fun setUp() {
        MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `renderToBook creates a WRITTEN_BOOK`() {
        val newspaper = Newspaper(1, 0L, 1000L, emptyList())
        val renderer = BookRenderer(config)
        val book = renderer.renderToBook(newspaper)
        assertEquals(Material.WRITTEN_BOOK, book.type)
    }

    @Test
    fun `renderToBook sets title and author via components`() {
        val newspaper = Newspaper(1, 0L, 1000L, emptyList())
        val renderer = BookRenderer(config)
        val book = renderer.renderToBook(newspaper)
        val meta = book.itemMeta as BookMeta

        val title = meta.title()
        assertNotNull(title)
        assertTrue(title.toString().contains("Test Chronicle"))

        val author = meta.author()
        assertNotNull(author)
        assertTrue(author.toString().contains("Tester"))
    }

    @Test
    fun `renderToBook includes title page at minimum`() {
        val newspaper = Newspaper(1, 0L, 1000L, emptyList())
        val renderer = BookRenderer(config)
        val book = renderer.renderToBook(newspaper)
        val meta = book.itemMeta as BookMeta
        assertTrue(meta.pageCount >= 1, "Should have at least a title page")
    }

    @Test
    fun `renderToBook adds pages for sections`() {
        val newspaper = Newspaper(
            issueNumber = 1,
            fromTime = 0L,
            toTime = 1000L,
            sections = listOf(
                NewspaperSection(
                    title = "Headlines",
                    stories = listOf(Story("Big News", "Something happened.", listOf("ewanc26"), EventType.DEATH)),
                ),
            ),
        )
        val renderer = BookRenderer(config)
        val book = renderer.renderToBook(newspaper)
        val meta = book.itemMeta as BookMeta
        assertTrue(meta.pageCount >= 2, "Should have title + section pages")
    }

    @Test
    fun `renderToBook adds footer page`() {
        val newspaper = Newspaper(
            issueNumber = 1,
            fromTime = 0L,
            toTime = 1000L,
            sections = listOf(
                NewspaperSection("Test", listOf(Story("H", "B", emptyList(), null))),
            ),
        )
        val renderer = BookRenderer(config)
        val book = renderer.renderToBook(newspaper)
        val meta = book.itemMeta as BookMeta
        assertTrue(meta.pageCount >= 3, "Should have title + section + footer")
    }

    @Test
    fun `renderToBook page count matches sections plus title and footer`() {
        val newspaper = Newspaper(
            issueNumber = 1,
            fromTime = 0L,
            toTime = 1000L,
            sections = (1..5).map { i ->
                NewspaperSection("Section $i", listOf(Story("H$i", "B$i", emptyList(), null)))
            },
        )
        val renderer = BookRenderer(config)
        val book = renderer.renderToBook(newspaper)
        val meta = book.itemMeta as BookMeta
        assertEquals(7, meta.pageCount, "Should be 1 title + 5 sections + 1 footer")
    }

    @Test
    fun `renderToBook gives each article a complete page`() {
        val newspaper = Newspaper(
            issueNumber = 1,
            fromTime = 0L,
            toTime = 1000L,
            sections = listOf(
                NewspaperSection(
                    "Local News",
                    listOf(
                        Story("First", "First article.", emptyList(), null),
                        Story("Second", "Second article.", emptyList(), null),
                    ),
                ),
            ),
        )
        val meta = BookRenderer(config).renderToBook(newspaper).itemMeta as BookMeta

        assertEquals(4, meta.pageCount, "Should be title + one page per article + footer")
    }

    @Test
    fun `long articles split without losing words`() {
        val body = (1..80).joinToString(" ") { "word$it" }
        val pages = BookRenderer.splitArticle(body, 80)

        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.length <= 80 })
        assertEquals(body, pages.joinToString(" "))
    }
}
