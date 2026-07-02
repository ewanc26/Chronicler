package uk.ewancroft.chronicler.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UpdateCheckerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `recognises newer semantic versions`() {
        assertTrue(UpdateChecker.isNewerVersion("1.3.0", "1.2.5"))
        assertTrue(UpdateChecker.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewerVersion("1.3.1", "1.3"))
    }

    @Test
    fun `does not treat equal or older versions as updates`() {
        assertFalse(UpdateChecker.isNewerVersion("1.3.0", "1.3.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.2.9", "1.3.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.3", "1.3.0"))
    }

    @Test
    fun `extracts checksum for the selected release asset`() {
        val expected = "a".repeat(64)
        val checksums = "$expected  Chronicler-1.4.0-all.jar\n${"b".repeat(64)}  sources.jar"
        assertEquals(expected, UpdateChecker.extractChecksum(checksums, "Chronicler-1.4.0-all.jar"))
    }

    @Test
    fun `calculates sha256 for downloaded file`() {
        val file = tempDir.resolve("plugin.jar")
        Files.writeString(file, "chronicler")
        assertEquals("1e0b5b255e5024cd0d8b81af01ef22616bda7273e97e86d53b3dc4d62f953b2e", UpdateChecker.sha256(file))
    }
}
