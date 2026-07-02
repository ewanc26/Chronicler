package uk.ewancroft.chronicler.llm

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParseArticleTest {

    @Test
    fun `parseArticle extracts headline and body from delimited format`() {
        val text = """---HEADLINE
Server sees record player count
---BODY
The server reached a new milestone today with over twenty players online simultaneously. Building projects are flourishing across all continents.
"""
        val result = parseArticle(text)
        assertNotNull(result)
        assertEquals("Server sees record player count", result.headline)
        assertEquals("The server reached a new milestone today with over twenty players online simultaneously. Building projects are flourishing across all continents.", result.body)
    }

    @Test
    fun `parseArticle handles extra whitespace`() {
        val text = """---HEADLINE   
   Headline here   
---BODY   
   Body text here
"""
        val result = parseArticle(text)
        assertNotNull(result)
        assertEquals("Headline here", result.headline)
        assertEquals("Body text here", result.body)
    }

    @Test
    fun `parseArticle falls back to plain text format`() {
        val text = """Major Battle at Spawn
Dozens of players engaged in an epic battle near spawn point. The carnage lasted for hours with multiple casualties on both sides."""
        val result = parseArticle(text)
        assertNotNull(result)
        assertEquals("Major Battle at Spawn", result.headline)
        assertEquals("Dozens of players engaged in an epic battle near spawn point. The carnage lasted for hours with multiple casualties on both sides.", result.body)
    }

    @Test
    fun `parseArticle returns null for empty input`() {
        assertNull(parseArticle(""))
        assertNull(parseArticle("   "))
    }

    @Test
    fun `parseArticle returns null for single line input`() {
        assertNull(parseArticle("Just a single line"))
    }

    @Test
    fun `parseArticle strips markdown bold markers from headline`() {
        val text = """**Breaking News**
Something important happened on the server today."""
        val result = parseArticle(text)
        assertNotNull(result)
        assertEquals("Breaking News", result.headline)
    }

    @Test
    fun `parseArticle handles multiline body`() {
        val text = """---HEADLINE
Epic Build Completed
---BODY
After weeks of work, the massive cathedral has finally been completed.
It features over ten thousand blocks and a working redstone organ.
Players from across the server came to witness the inauguration.
"""
        val result = parseArticle(text)
        assertNotNull(result)
        assertEquals("Epic Build Completed", result.headline)
        assertNotNull(result.body)
    }
}

class BuildPromptTest {

    @Test
    fun `buildPrompt includes section title and summary`() {
        val prompt = buildPrompt("Obituaries", "3 deaths this cycle")
        assertNotNull(prompt)
        assert(prompt.contains("Obituaries"))
        assert(prompt.contains("3 deaths this cycle"))
        assert(prompt.contains("---HEADLINE"))
        assert(prompt.contains("---BODY"))
    }
}
