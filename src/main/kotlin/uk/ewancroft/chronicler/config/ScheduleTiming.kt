package uk.ewancroft.chronicler.config

private const val REAL_SECONDS_PER_MINUTE = 60L
private const val REAL_MINUTES_PER_HOUR = 60L
private const val REAL_HOURS_PER_DAY = 24L
private const val REAL_TICKS_PER_SECOND = 20L
private const val GAME_TICKS_PER_HOUR = 1000L
private const val GAME_TICKS_PER_DAY = 24000L

fun publicationIntervalTicks(schedule: String, base: ScheduleBase): Long {
    return when (schedule.trim().uppercase()) {
        "HOURLY" -> if (base == ScheduleBase.IN_GAME) GAME_TICKS_PER_HOUR else realHours(1)
        "DAILY" -> if (base == ScheduleBase.IN_GAME) GAME_TICKS_PER_DAY else realDays(1)
        "WEEKLY" -> if (base == ScheduleBase.IN_GAME) GAME_TICKS_PER_DAY * 7L else realDays(7)
        "BIWEEKLY" -> if (base == ScheduleBase.IN_GAME) GAME_TICKS_PER_DAY * 14L else realDays(14)
        "MONTHLY" -> if (base == ScheduleBase.IN_GAME) GAME_TICKS_PER_DAY * 30L else realDays(30)
        else -> schedule.toLongOrNull()?.coerceAtLeast(1L) ?: if (base == ScheduleBase.IN_GAME) {
            GAME_TICKS_PER_DAY * 7L
        } else {
            realDays(7)
        }
    }
}

private fun realHours(hours: Long): Long = hours * REAL_MINUTES_PER_HOUR * REAL_SECONDS_PER_MINUTE * REAL_TICKS_PER_SECOND

private fun realDays(days: Long): Long = realHours(days * REAL_HOURS_PER_DAY)
