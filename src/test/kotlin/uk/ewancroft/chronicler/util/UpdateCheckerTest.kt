package uk.ewancroft.chronicler.util

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {

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
}
