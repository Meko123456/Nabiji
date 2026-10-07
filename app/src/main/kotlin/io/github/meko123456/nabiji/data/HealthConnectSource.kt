package io.github.meko123456.nabiji.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.meko123456.nabiji.domain.ActivitySource
import io.github.meko123456.nabiji.domain.DayActivity
import io.github.meko123456.nabiji.domain.HealthAvailability
import io.github.meko123456.nabiji.domain.HistoryAccess
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

    override suspend fun history(): HistoryAccess = withContext(io) {
        val client = client ?: return@withContext HistoryAccess.UNSUPPORTED
        runCatching {
            when {
                client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) !=
                    HealthConnectFeatures.FEATURE_STATUS_AVAILABLE -> HistoryAccess.UNSUPPORTED
                HISTORY_PERMISSION in client.permissionController.getGrantedPermissions() -> HistoryAccess.GRANTED
                else -> HistoryAccess.GRANTABLE
            }
        }.getOrDefault(HistoryAccess.UNSUPPORTED)
    }

    /**
     * Whether Nabiji could ask to read steps while it is closed, and has not been allowed yet.
     * Only the widget needs it: the dashboard reads in the foreground, where Health Connect always
     * answers, but the widget's own updates run in the background, where it refuses without it.
     */
    suspend fun canAskForBackground(): Boolean = withContext(io) {
        val client = client ?: return@withContext false
        runCatching {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE &&
                BACKGROUND_PERMISSION !in client.permissionController.getGrantedPermissions()
        }.getOrDefault(false)
    }

    override suspend fun dailyActivity(from: LocalDate, to: LocalDate): Result<List<DayActivity>> =
        withContext(io) {
            val client = client ?: return@withContext Result.failure(IllegalStateException("Health Connect unavailable"))
            runCatching {
                val request = AggregateGroupByPeriodRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        // Active, not total: total calories include the energy a body burns at
                        // rest, so by lunchtime they read 1 500 kcal after 1 700 steps.
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
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
        activeKilocalories = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
    )

    companion object {
        /** Everything the dashboard needs; requested together in Health Connect. */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )

        /**
         * Days older than the 30 before the first grant. Not in [PERMISSIONS]: the dashboard works
         * without it, only with a shorter heatmap, so it is offered from there instead.
         */
        const val HISTORY_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

        /** Lets the widget read today's steps while the app is closed; offered once a widget is placed. */
        const val BACKGROUND_PERMISSION = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }
}
