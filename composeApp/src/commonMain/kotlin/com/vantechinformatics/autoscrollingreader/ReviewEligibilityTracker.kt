package com.vantechinformatics.autoscrollingreader

class ReviewEligibilityTracker(
    private val store: ReviewStateStore,
    private val clock: () -> Long = { currentTimeMillis() },
) {

    fun recordSession(sessionStartMs: Long, sessionEndMs: Long) {
        val duration = sessionEndMs - sessionStartMs
        if (duration < MIN_SESSION_DURATION_MS) return

        val newCount = (store.getSessionCount() + 1).coerceAtMost(MAX_SESSION_COUNT)
        store.setSessionCount(newCount)

        val epochDay = sessionEndMs / MS_PER_DAY
        val days = store.getDistinctDays()
        if (epochDay !in days && days.size < MIN_DISTINCT_DAYS) {
            store.setDistinctDays(days + epochDay)
        }
    }

    fun isEligible(): Boolean {
        if (store.getSessionCount() < MIN_SESSIONS) return false
        if (store.getDistinctDays().size < MIN_DISTINCT_DAYS) return false
        val last = store.getLastPromptMs()
        if (last != 0L && clock() - last < COOLDOWN_MS) return false
        return true
    }

    fun markPromptRequested() {
        store.setLastPromptMs(clock())
    }

    companion object {
        const val MIN_SESSION_DURATION_MS: Long = 30_000L
        const val MIN_SESSIONS: Int = 3
        const val MIN_DISTINCT_DAYS: Int = 2
        const val MS_PER_DAY: Long = 24L * 60 * 60 * 1000
        const val COOLDOWN_MS: Long = 90L * MS_PER_DAY
        const val MAX_SESSION_COUNT: Int = 1000
    }
}
