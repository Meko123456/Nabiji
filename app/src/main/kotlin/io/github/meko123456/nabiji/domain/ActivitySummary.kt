package io.github.meko123456.nabiji.domain

import java.time.LocalDate

/**
 * Derived views over a run of days. All of it is pure so the dashboard's numbers can be tested
 * without Health Connect, a device, or a clock.
 */
object ActivitySummary {

    /** Totals for the given days (missing days simply contribute nothing). */
    fun totalSteps(days: List<DayActivity>): Long = days.sumOf { it.steps }

    /**
     * Mean steps per day across [days], rounded to the nearest whole step. Empty input averages
     * to zero rather than dividing by zero.
     */
    fun averageSteps(days: List<DayActivity>): Long =
        if (days.isEmpty()) 0L else Math.round(days.sumOf { it.steps }.toDouble() / days.size)

    /** The best day in the range, or null when there is nothing to compare. */
    fun bestDay(days: List<DayActivity>): DayActivity? = days.maxByOrNull { it.steps }

    /**
     * Consecutive days meeting [goal], counting back from [today].
     *
     * Today is allowed to be incomplete: if the goal is not met *yet* today, the streak is
     * measured up to yesterday rather than being reset to zero — otherwise a perfectly good
     * streak would appear to vanish every morning.
     */
    fun goalStreak(days: List<DayActivity>, goal: StepGoal, today: LocalDate): Int {
        val byDate = days.associateBy { it.date }
        var cursor = today
        // A goal not yet met today doesn't break the streak — start counting from yesterday.
        if (!goal.isMet(byDate[today]?.steps ?: 0L)) cursor = today.minusDays(1)
        var streak = 0
        while (goal.isMet(byDate[cursor]?.steps ?: 0L)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** How many of [days] met the goal. */
    fun daysMeetingGoal(days: List<DayActivity>, goal: StepGoal): Int = days.count { goal.isMet(it.steps) }

    /**
     * Steps per day keyed by epoch day, shaped for the `heatmap` library. Days with no steps are
     * omitted so the heatmap draws them as empty rather than as a zero-intensity square.
     */
    fun heatmapCounts(days: List<DayActivity>): Map<Long, Int> =
        days.filter { it.steps > 0 }.associate { it.date.toEpochDay() to it.steps.toInt() }

    /**
     * A run of [count] days ending at [endDate], filling gaps with zero-step days so a chart or
     * average never silently skips a day Health Connect had no data for.
     */
    fun fillMissingDays(days: List<DayActivity>, endDate: LocalDate, count: Int): List<DayActivity> {
        require(count > 0) { "count must be positive" }
        val byDate = days.associateBy { it.date }
        return (count - 1 downTo 0).map { back ->
            val date = endDate.minusDays(back.toLong())
            byDate[date] ?: DayActivity(date, steps = 0L)
        }
    }
}
