package io.github.meko123456.nabiji.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.meko123456.nabiji.data.GoalRepository
import io.github.meko123456.nabiji.domain.ActivitySource
import io.github.meko123456.nabiji.domain.ActivitySummary
import io.github.meko123456.nabiji.domain.DayActivity
import io.github.meko123456.nabiji.domain.HealthAvailability
import io.github.meko123456.nabiji.domain.StepGoal
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the dashboard can be showing. The states are distinct so each can explain itself. */
sealed interface DashboardState {
    data object Loading : DashboardState

    /** Health Connect missing or too old — nothing to read from. */
    data class Unavailable(val availability: HealthAvailability) : DashboardState

    /** Health Connect is there, but the user hasn't granted the reads yet. */
    data object NeedsPermission : DashboardState

    /** Granted, but Health Connect holds nothing for this range yet. */
    data class NoData(val goal: StepGoal) : DashboardState

    data class Ready(
        val goal: StepGoal,
        val today: DayActivity,
        val days: List<DayActivity>,
        val streak: Int,
        val weekTotal: Long,
        val weekAverage: Long,
        val bestDay: DayActivity?,
        val daysMeetingGoal: Int,
    ) : DashboardState

    data class Failed(val message: String) : DashboardState
}

/**
 * Loads the dashboard. Every number it exposes comes from the pure domain, so the interesting
 * arithmetic is tested without a device.
 */
class DashboardViewModel(
    private val source: ActivitySource,
    private val goals: GoalRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val _state = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { DashboardState.Loading }
            val availability = source.availability()
            if (availability != HealthAvailability.AVAILABLE) {
                _state.update { DashboardState.Unavailable(availability) }
                return@launch
            }
            if (!source.hasPermissions()) {
                _state.update { DashboardState.NeedsPermission }
                return@launch
            }
            val goal = goals.goal.first()
            val end = today()
            val start = end.minusDays(HISTORY_DAYS - 1)
            source.dailyActivity(start, end)
                .onSuccess { days -> _state.update { buildReady(days, goal, end) } }
                .onFailure { error ->
                    _state.update { DashboardState.Failed(error.message ?: "Couldn't read your activity.") }
                }
        }
    }

    fun setGoal(steps: Int) {
        viewModelScope.launch {
            goals.setGoal(StepGoal.clamped(steps))
            refresh()
        }
    }

    private fun buildReady(days: List<DayActivity>, goal: StepGoal, end: LocalDate): DashboardState {
        if (days.none { it.steps > 0 }) return DashboardState.NoData(goal)
        val week = ActivitySummary.fillMissingDays(days, end, WEEK_DAYS)
        val todayActivity = days.firstOrNull { it.date == end } ?: DayActivity(end, 0L)
        return DashboardState.Ready(
            goal = goal,
            today = todayActivity,
            days = days,
            streak = ActivitySummary.goalStreak(days, goal, end),
            weekTotal = ActivitySummary.totalSteps(week),
            weekAverage = ActivitySummary.averageSteps(week),
            bestDay = ActivitySummary.bestDay(week),
            daysMeetingGoal = ActivitySummary.daysMeetingGoal(week, goal),
        )
    }

    private companion object {
        /** A year, so the heatmap has something to draw. */
        const val HISTORY_DAYS = 365L
        const val WEEK_DAYS = 7
    }
}
