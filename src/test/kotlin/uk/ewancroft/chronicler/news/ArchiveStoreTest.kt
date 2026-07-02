package uk.ewancroft.chronicler.news

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArchiveStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var archive: ArchiveStore

    private fun newspaper(number: Int): Newspaper = Newspaper(
        issueNumber = number,
        fromTime = number * 1000L,
        toTime = number * 1000L + 500L,
        sections = listOf(
            NewspaperSection(
                title = "Headlines",
                stories = listOf(Story("Issue $number", "Content of issue $number.", listOf("player"), EventType.DEATH)),
            ),
        ),
    )

    @BeforeEach
    fun setUp() {
        archive = ArchiveStore(tempDir.resolve("archive"))
    }

    @Test
    fun `archive persists newspaper to disk`() {
        archive.archive(newspaper(1))
        val loaded = ArchiveStore(tempDir.resolve("archive"))
        loaded.loadAll()
        assertEquals(1, loaded.latest(10).size)
    }

    @Test
    fun `getAll returns issues sorted by issueNumber descending`() {
        archive.archive(newspaper(1))
        archive.archive(newspaper(2))
        archive.archive(newspaper(3))

        val all = archive.getAll()
        assertEquals(3, all.size)
        assertEquals(3, all[0].issueNumber)
        assertEquals(2, all[1].issueNumber)
        assertEquals(1, all[2].issueNumber)
    }

    @Test
    fun `getIssue returns correct issue`() {
        archive.archive(newspaper(1))
        archive.archive(newspaper(5))

        val issue = archive.getIssue(5)
        assertNotNull(issue)
        assertEquals(5, issue.issueNumber)
    }

    @Test
    fun `getIssue returns null for nonexistent issue`() {
        archive.archive(newspaper(1))
        assertNull(archive.getIssue(99))
    }

    @Test
    fun `latest returns most recent n issues`() {
        repeat(20) { i -> archive.archive(newspaper(i + 1)) }

        val latest = archive.latest(5)
        assertEquals(5, latest.size)
        assertEquals(20, latest[0].issueNumber)
        assertEquals(16, latest[4].issueNumber)
    }

    @Test
    fun `archived files can be loaded after store recreation`() {
        archive.archive(newspaper(1))
        archive.archive(newspaper(2))

        val newArchive = ArchiveStore(tempDir.resolve("archive"))
        newArchive.loadAll()
        assertEquals(2, newArchive.getAll().size)
    }

    @Test
    fun `loadAll with empty directory does nothing`() {
        val emptyArchive = ArchiveStore(tempDir.resolve("empty"))
        emptyArchive.loadAll()
        assert(emptyArchive.getAll().isEmpty())
    }

    @Test
    fun `archive handles multiple issues`() {
        repeat(100) { i -> archive.archive(newspaper(i + 1)) }
        assertEquals(100, archive.getAll().size)
    }
}
