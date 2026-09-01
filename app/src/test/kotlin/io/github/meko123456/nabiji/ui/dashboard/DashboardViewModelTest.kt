package io.github.meko123456.nabiji.ui.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.github.meko123456.nabiji.data.GoalRepository
import io.github.meko123456.nabiji.domain.ActivitySource
import io.github.meko123456.nabiji.domain.DayActivity
import io.github.meko123456.nabiji.domain.HealthAvailability
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 9, 1)

    /** In-memory DataStore so the goal repository can be used without Android. */
    private class FakeStore : DataStore<Preferences> {
        private val flow = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = flow
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(flow.value)
            flow.value = updated
            return updated
        }
    }

    private class FakeSource(
        private val availability: HealthAvailability = HealthAvailability.AVAILABLE,
        private val granted: Boolean = true,
        private val days: List<DayActivity> = emptyList(),
        private val failure: Throwable? = null,
    ) : ActivitySource {
        override fun availability() = availability
        override suspend fun hasPermissions() = granted
        override suspend fun dailyActivity(from: LocalDate, to: LocalDate): Result<List<DayActivity>> =
            failure?.let { Result.failure(it) } ?: Result.success(days)
    }

    private fun vm(source: ActivitySource) =
        DashboardViewModel(source, GoalRepository(FakeStore())) { today }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `missing Health Connect surfaces as unavailable, never as an empty dashboard`() = runTest(dispatcher) {
        val vm = vm(FakeSource(availability = HealthAvailability.UNAVAILABLE))
        advanceUntilIdle()
        assertEquals(DashboardState.Unavailable(HealthAvailability.UNAVAILABLE), vm.state.value)
    }

    @Test
    fun `an outdated Health Connect is distinguished from a missing one`() = runTest(dispatcher) {
        val vm = vm(FakeSource(availability = HealthAvailability.UPDATE_REQUIRED))
        advanceUntilIdle()
        assertEquals(DashboardState.Unavailable(HealthAvailability.UPDATE_REQUIRED), vm.state.value)
    }

    @Test
    fun `ungranted permissions ask for permission rather than showing zeros`() = runTest(dispatcher) {
        val vm = vm(FakeSource(granted = false))
        advanceUntilIdle()
        assertEquals(DashboardState.NeedsPermission, vm.state.value)
    }

    @Test
    fun `granted but empty is its own state, not a broken-looking dashboard`() = runTest(dispatcher) {
        val vm = vm(FakeSource(days = listOf(DayActivity(today, 0L))))
        advanceUntilIdle()
        assertTrue(vm.state.value is DashboardState.NoData)
    }

    @Test
    fun `a read failure surfaces the message`() = runTest(dispatcher) {
        val vm = vm(FakeSource(failure = IllegalStateException("boom")))
        advanceUntilIdle()
        assertEquals(DashboardState.Failed("boom"), vm.state.value)
    }

    @Test
    fun `a ready dashboard computes today, streak and week stats`() = runTest(dispatcher) {
        val days = listOf(
            DayActivity(today.minusDays(2), 11_000),
            DayActivity(today.minusDays(1), 12_000),
            DayActivity(today, 4_000),
        )
        val vm = vm(FakeSource(days = days))
        advanceUntilIdle()
        val ready = vm.state.value as DashboardState.Ready
        assertEquals(4_000L, ready.today.steps)
        // today's goal isn't met yet, so the streak counts back from yesterday
        assertEquals(2, ready.streak)
        assertEquals(27_000L, ready.weekTotal)
        // 27 000 spread over a 7-day window (missing days count as zero)
        assertEquals(3_857L, ready.weekAverage)
        assertEquals(12_000L, ready.bestDay?.steps)
        assertEquals(2, ready.daysMeetingGoal)
        assertEquals(10_000, ready.goal.steps)
    }

    @Test
    fun `changing the goal re-reads and clamps out-of-range input`() = runTest(dispatcher) {
        val vm = vm(FakeSource(days = listOf(DayActivity(today, 12_000))))
        advanceUntilIdle()
        vm.setGoal(999_999)
        advanceUntilIdle()
        val ready = vm.state.value as DashboardState.Ready
        assertEquals(io.github.meko123456.nabiji.domain.StepGoal.MAX, ready.goal.steps)
    }
}
