package uk.ewancroft.chronicler.config

enum class ScheduleBase {
    REAL_TIME,
    IN_GAME,
    ;

    companion object {
        fun from(value: String?): ScheduleBase {
            return when (value?.trim()?.uppercase()) {
                "IN_GAME", "INGAME", "TICKS", "GAME" -> IN_GAME
                else -> REAL_TIME
            }
        }
    }
}
