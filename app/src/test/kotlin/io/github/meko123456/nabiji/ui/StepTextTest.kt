package io.github.meko123456.nabiji.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StepTextTest {

    @Test
    fun `one step is a step`() {
        // A screen reader heard "Today: 1 steps" first thing in the morning, and "1 steps to go"
        // one step short of the goal.
        assertEquals("1 step", stepCount(1))
        assertEquals("0 steps", stepCount(0))
        assertEquals("2 steps", stepCount(2))
        assertEquals("10000 steps", stepCount(10_000))
    }
}
