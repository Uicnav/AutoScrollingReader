package com.vantechinformatics.autoscrollingreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeReviewStateStore : ReviewStateStore {
    var storedSessionCount: Int = 0
    var days: List<Long> = emptyList()
    var storedLastPromptMs: Long = 0L

    override fun getSessionCount(): Int = storedSessionCount
    override fun setSessionCount(value: Int) { storedSessionCount = value }
    override fun getDistinctDays(): List<Long> = days
    override fun setDistinctDays(days: List<Long>) { this.days = days }
    override fun getLastPromptMs(): Long = storedLastPromptMs
    override fun setLastPromptMs(value: Long) { storedLastPromptMs = value }
}

class ReviewEligibilityTrackerTest {

    private val msPerDay = ReviewEligibilityTracker.MS_PER_DAY

    @Test
    fun shortSessionIsIgnored() {
        val store = FakeReviewStateStore()
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 10L * msPerDay
        tracker.recordSession(start, start + 20_000L) // 20 seconds
        assertEquals(0, store.storedSessionCount)
        assertEquals(emptyList(), store.days)
    }

    @Test
    fun qualifyingSessionIncrementsCountAndRecordsDay() {
        val store = FakeReviewStateStore()
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 10L * msPerDay
        tracker.recordSession(start, start + 30_000L)
        assertEquals(1, store.storedSessionCount)
        assertEquals(listOf(10L), store.days)
    }

    @Test
    fun sameDaySessionDoesNotAddNewDay() {
        val store = FakeReviewStateStore().apply {
            storedSessionCount = 1
            days = listOf(10L)
        }
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 10L * msPerDay + 3_600_000L // 1 hour later same day
        tracker.recordSession(start, start + 40_000L)
        assertEquals(2, store.storedSessionCount)
        assertEquals(listOf(10L), store.days)
    }

    @Test
    fun distinctDaysCapsAtTwo() {
        val store = FakeReviewStateStore().apply {
            storedSessionCount = 2
            days = listOf(10L, 11L)
        }
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 12L * msPerDay
        tracker.recordSession(start, start + 40_000L)
        assertEquals(3, store.storedSessionCount)
        assertEquals(listOf(10L, 11L), store.days) // not appended
    }

    @Test
    fun notEligibleWhenSessionCountUnderThree() {
        val store = FakeReviewStateStore().apply {
            storedSessionCount = 2
            days = listOf(10L, 11L)
        }
        val tracker = ReviewEligibilityTracker(store) { 100L * msPerDay }
        assertFalse(tracker.isEligible())
    }

    @Test
    fun notEligibleWhenDistinctDaysUnderTwo() {
        val store = FakeReviewStateStore().apply {
            storedSessionCount = 5
            days = listOf(10L)
        }
        val tracker = ReviewEligibilityTracker(store) { 100L * msPerDay }
        assertFalse(tracker.isEligible())
    }

    @Test
    fun notEligibleWhenWithinCooldown() {
        val now = 100L * msPerDay
        val store = FakeReviewStateStore().apply {
            storedSessionCount = 5
            days = listOf(10L, 11L)
            storedLastPromptMs = now - 30L * msPerDay // 30 days ago
        }
        val tracker = ReviewEligibilityTracker(store) { now }
        assertFalse(tracker.isEligible())
    }

    @Test
    fun eligibleWhenAllConditionsMetAndNeverPrompted() {
        val store = FakeReviewStateStore().apply {
            storedSessionCount = 3
            days = listOf(10L, 11L)
            storedLastPromptMs = 0L
        }
        val tracker = ReviewEligibilityTracker(store) { 100L * msPerDay }
        assertTrue(tracker.isEligible())
    }

    @Test
    fun eligibleWhenCooldownExpired() {
        val now = 200L * msPerDay
        val store = FakeReviewStateStore().apply {
            storedSessionCount = 3
            days = listOf(10L, 11L)
            storedLastPromptMs = now - 91L * msPerDay
        }
        val tracker = ReviewEligibilityTracker(store) { now }
        assertTrue(tracker.isEligible())
    }

    @Test
    fun markPromptRequestedStoresClockValue() {
        val store = FakeReviewStateStore()
        val now = 500L * msPerDay
        val tracker = ReviewEligibilityTracker(store) { now }
        tracker.markPromptRequested()
        assertEquals(now, store.storedLastPromptMs)
    }
}
