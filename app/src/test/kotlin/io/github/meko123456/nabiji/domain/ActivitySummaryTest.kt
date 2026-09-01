package io.github.meko123456.nabiji.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySummaryTest {

    private val goal = StepGoal(10_000)
    private val today = LocalDate.of(2026, 9, 1)

    private fun day(daysBack: Long, steps: Long) = DayActivity(today.minusDays(daysBack), steps)

    @Test
    fun `totals and averages`() {
        val days = listOf(day(2, 4_000), day(1, 6_000), day(0, 11_000))
        assertEquals(21_000L, ActivitySummary.totalSteps(days))
        assertEquals(7_000L, ActivitySummary.averageSteps(days))
    }

    @Test
    fun `an empty range averages to zero rather than dividing by zero`() {
        assertEquals(0L, ActivitySummary.averageSteps(emptyList()))
        assertEquals(0L, ActivitySummary.totalSteps(emptyList()))
        assertNull(ActivitySummary.bestDay(emptyList()))
    }

    @Test
    fun `best day is the highest step count`() {
        val days = listOf(day(2, 4_000), day(1, 12_000), day(0, 9_000))
        assertEquals(12_000L, ActivitySummary.bestDay(days)?.steps)
    }

    @Test
    fun `streak counts consecutive days meeting the goal`() {
        val days = listOf(day(3, 11_000), day(2, 12_000), day(1, 10_500), day(0, 10_100))
        assertEquals(4, ActivitySummary.goalStreak(days, goal, today))
    }

    @Test
    fun `a goal not yet met today does not wipe out the streak`() {
        // The classic morning case: yesterday and before were fine, today has barely started.
        val days = listOf(day(2, 11_000), day(1, 12_000), day(0, 800))
        assertEquals(2, ActivitySummary.goalStreak(days, goal, today))
    }

    @Test
    fun `a missed day breaks the streak`() {
        val days = listOf(day(3, 11_000), day(2, 2_000), day(1, 12_000), day(0, 11_000))
        assertEquals(2, ActivitySummary.goalStreak(days, goal, today))
    }

    @Test
    fun `no qualifying days is a zero streak`() {
        assertEquals(0, ActivitySummary.goalStreak(listOf(day(1, 500), day(0, 400)), goal, today))
        assertEquals(0, ActivitySummary.goalStreak(emptyList(), goal, today))
    }

    @Test
    fun `days meeting the goal are counted`() {
        val days = listOf(day(2, 10_000), day(1, 9_999), day(0, 20_000))
        assertEquals(2, ActivitySummary.daysMeetingGoal(days, goal))
    }

    @Test
    fun `heatmap counts skip zero-step days so they draw as empty`() {
        val days = listOf(day(2, 0), day(1, 6_000), day(0, 12_000))
        val counts = ActivitySummary.heatmapCounts(days)
        assertEquals(2, counts.size)
        assertEquals(6_000, counts[today.minusDays(1).toEpochDay()])
        assertTrue(today.minusDays(2).toEpochDay() !in counts)
    }

    @Test
    fun `gaps are filled with zero-step days in chronological order`() {
        val filled = ActivitySummary.fillMissingDays(listOf(day(2, 5_000)), endDate = today, count = 3)
        assertEquals(3, filled.size)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), filled.map { it.date })
        assertEquals(listOf(5_000L, 0L, 0L), filled.map { it.steps })
    }
}
