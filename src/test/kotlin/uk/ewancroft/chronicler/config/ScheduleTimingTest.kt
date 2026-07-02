package uk.ewancroft.chronicler.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleTimingTest {

    @Test
    fun `real-time weekly uses wall clock ticks`() {
        assertEquals(12_096_000L, publicationIntervalTicks("WEEKLY", ScheduleBase.REAL_TIME))
    }

    @Test
    fun `in-game weekly uses minecraft ticks`() {
        assertEquals(168_000L, publicationIntervalTicks("WEEKLY", ScheduleBase.IN_GAME))
    }

    @Test
    fun `in-game hourly uses minecraft hour ticks`() {
        assertEquals(1_000L, publicationIntervalTicks("HOURLY", ScheduleBase.IN_GAME))
    }

    @Test
    fun `custom tick schedules are preserved`() {
        assertEquals(42L, publicationIntervalTicks("42", ScheduleBase.REAL_TIME))
        assertEquals(42L, publicationIntervalTicks("42", ScheduleBase.IN_GAME))
    }
}
