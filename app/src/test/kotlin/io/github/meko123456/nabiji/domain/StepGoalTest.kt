package io.github.meko123456.nabiji.domain

import kotlin.math.nextDown
import kotlin.math.nextUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StepGoalTest {

    private val goal = StepGoal(10_000)

    @Test
    fun `progress is a clamped fraction`() {
        assertEquals(0f, goal.progress(0), 0.001f)
        assertEquals(0.5f, goal.progress(5_000), 0.001f)
        assertEquals(1f, goal.progress(10_000), 0.001f)
        // a 150% day still fills the ring exactly once
        assertEquals(1f, goal.progress(15_000), 0.001f)
    }

    @Test
    fun `percent is uncapped because 150 percent is worth saying`() {
        assertEquals(150, goal.percent(15_000))
        assertEquals(0, goal.percent(0))
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(4_000L, goal.remaining(6_000))
        assertEquals(0L, goal.remaining(10_000))
        assertEquals(0L, goal.remaining(12_000))
    }

    @Test
    fun `the goal is met at exactly the target`() {
        assertFalse(goal.isMet(9_999))
        assertTrue(goal.isMet(10_000))
        assertTrue(goal.isMet(10_001))
    }

    @Test
    fun `a non-positive goal is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { StepGoal(0) }
        assertThrows(IllegalArgumentException::class.java) { StepGoal(-500) }
    }

    @Test
    fun `a user-entered goal is clamped rather than rejected`() {
        assertEquals(StepGoal.MIN, StepGoal.clamped(10).steps)
        assertEquals(StepGoal.MAX, StepGoal.clamped(999_999).steps)
        assertEquals(7_500, StepGoal.clamped(7_500).steps)
        assertEquals(10_000, StepGoal.DEFAULT.steps)
    }

    @Test
    fun `a slider position a float's width off a notch still lands on it`() {
        // The slider snaps in float arithmetic, so the 10 000 notch can arrive as the float just
        // below it. Truncated, that was a saved goal of 9 999.
        assertEquals(10_000, StepGoal.fromSlider(10_000f.nextDown()).steps)
        for (notch in StepGoal.MIN..StepGoal.MAX step 1_000) {
            assertEquals(notch, StepGoal.fromSlider(notch.toFloat().nextDown()).steps)
            assertEquals(notch, StepGoal.fromSlider(notch.toFloat().nextUp()).steps)
        }
    }
}
