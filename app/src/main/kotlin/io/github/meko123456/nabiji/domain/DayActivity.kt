package io.github.meko123456.nabiji.domain

import java.time.LocalDate

/**
 * One day's activity totals, as read from Health Connect. Kept free of Health Connect types so
 * every calculation below is plain Kotlin and unit-testable.
 *
 * @property date the local calendar day these totals belong to
 * @property steps step count for that day
 * @property distanceMeters distance covered, or null when no source reported it
 * @property activeKilocalories active energy burned, or null when unavailable
 */
data class DayActivity(
    val date: LocalDate,
    val steps: Long,
    val distanceMeters: Double? = null,
    val activeKilocalories: Double? = null,
)
