package io.github.meko123456.nabiji.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.meko123456.nabiji.domain.ActivitySource
import io.github.meko123456.nabiji.domain.DayActivity
import io.github.meko123456.nabiji.domain.HealthAvailability
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads daily activity from Health Connect.
 *
 * Aggregation is grouped by a one-day [Period] against *local* date-times, so day boundaries
 * follow the user's own midnight rather than UTC — the difference shows up as steps landing on
 * the wrong day for anyone not on UTC.
 *
 * A metric with no reporting source stays null rather than becoming 0: "nothing recorded
 * distance today" and "you covered zero metres" are different claims, and the UI says so.
 */
class HealthConnectSource(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ActivitySource {

    private val client: HealthConnectClient? by lazy {
        runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
    }

    override fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
            else -> HealthAvailability.UNAVAILABLE
        }

    override suspend fun hasPermissions(): Boolean = withContext(io) {
        val client = client ?: return@withContext false
        runCatching { client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS) }
            .getOrDefault(false)
    }

    override suspend fun dailyActivity(from: LocalDate, to: LocalDate): Result<List<DayActivity>> =
        withContext(io) {
            val client = client ?: return@withContext Result.failure(IllegalStateException("Health Connect unavailable"))
            runCatching {
                val request = AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                    ),
                    // Local date-times: the user's midnight, not UTC's.
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay(),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
                client.aggregateGroupByPeriod(request).map { it.toDayActivity() }
            }
        }

    private fun AggregationResultGroupedByPeriod.toDayActivity(): DayActivity = DayActivity(
        date = startTime.toLocalDate(),
        steps = result[StepsRecord.COUNT_TOTAL] ?: 0L,
        distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
        activeKilocalories = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
    )

    companion object {
        /** Everything the dashboard needs; requested together in Health Connect. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        )
    }
}
