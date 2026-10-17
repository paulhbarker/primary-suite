package com.primarysuite.app.games.jeopardy

/** Where the server's running countdown stands, in local time. */
data class Countdown(
    val active: Boolean,
    val remainingMs: Long,
    /** 1 → 0 as time runs out. */
    val fraction: Float,
    /** read (buzzers open automatically), buzz, or answer. */
    val kind: String,
) {
    companion object {
        val IDLE = Countdown(active = false, remainingMs = 0, fraction = 0f, kind = "")

        /**
         * Same maths as the web host's useCountdown. [clockOffsetMs] is
         * `serverTime - localTime` measured when the state arrived, so a phone
         * with a wrong clock still counts down correctly.
         */
        fun of(state: HostState?, clockOffsetMs: Long, nowMs: Long): Countdown {
            if (state == null || state.deadline == 0L || state.deadlineTotalMs == 0L) return IDLE
            val remaining = (state.deadline - (nowMs + clockOffsetMs)).coerceAtLeast(0)
            return Countdown(
                active = remaining > 0,
                remainingMs = remaining,
                fraction = (remaining.toFloat() / state.deadlineTotalMs).coerceAtMost(1f),
                kind = state.deadlineKind,
            )
        }
    }
}
