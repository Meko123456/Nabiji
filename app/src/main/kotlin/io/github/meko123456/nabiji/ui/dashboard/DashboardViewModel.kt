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
import kotlinx.coroutines.Job
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

    private var refreshJob: Job? = null
    private var goalWriteJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // One load at a time. Overlapping refreshes do not merely waste a year-long Health Connect
        // query each; they race, and the one that finishes last wins rather than the one that
        // started last — so a late arrival could drop a stale dashboard over a fresh one, or put
        // the screen back into Loading after it had already rendered.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
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

    /**
     * Change the daily goal.
     *
     * Deliberately does not reload. The goal changes what the activity *means* — the streak, the
     * days met — not the activity itself, and every number that depends on it is derived by the
     * pure domain from days already in hand. Reloading re-read a year of Health Connect to arrive
     * at data identical to what was on screen.
     *
     * That mattered because the caller is a slider. Dragging it end to end fired ~29 of these, each
     * launching its own 365-day query and its own store write, all in flight together and none
     * cancelling the last. Whichever query happened to finish last decided the goal on screen, so
     * the dashboard could settle on a value the user had already dragged past.
     *
     * The state moves first and the write follows, so the screen answers the drag immediately. If
     * the write were ever to fail the next [refresh] reads the stored value back and corrects it.
     */
    fun setGoal(steps: Int) {
        val goal = StepGoal.clamped(steps)
        _state.update { it.withGoal(goal) }
        // Supersede rather than queue: only the value the user settled on needs to reach the store.
        goalWriteJob?.cancel()
        goalWriteJob = viewModelScope.launch { goals.setGoal(goal) }
    }

    /** Re-derive the goal-dependent numbers from the days already loaded. */
    private fun DashboardState.withGoal(goal: StepGoal): DashboardState = when (this) {
        is DashboardState.Ready -> buildReady(days, goal, today())
        is DashboardState.NoData -> DashboardState.NoData(goal)
        else -> this
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
