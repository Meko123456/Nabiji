package io.github.meko123456.nabiji.domain

import java.time.LocalDate

/** Whether the platform can give us health data at all. */
enum class HealthAvailability {
    /** Ready to use (permissions may still be missing). */
    AVAILABLE,

    /** Health Connect exists but needs updating before it can be used. */
    UPDATE_REQUIRED,

    /** No Health Connect on this device — below Android 14 without the app installed. */
    UNAVAILABLE,
}

/**
 * Port over the health data store, so the dashboard and its tests never touch Health Connect
 * directly. The implementation lives in `data/`.
 */
interface ActivitySource {
    fun availability(): HealthAvailability

    /** True when every permission the dashboard needs has been granted. */
    suspend fun hasPermissions(): Boolean

    /** Daily totals for the inclusive range, oldest first. Days with no data are simply absent. */
    suspend fun dailyActivity(from: LocalDate, to: LocalDate): Result<List<DayActivity>>
}
