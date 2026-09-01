package io.github.meko123456.nabiji.domain

/**
 * The daily step target and the arithmetic around it. A goal is always positive; the widely
 * quoted 10 000 is the default, but it is only a default — the user sets their own.
 */
@JvmInline
value class StepGoal(val steps: Int) {
    init {
        require(steps > 0) { "a step goal must be positive, was $steps" }
    }

    /** Progress as a 0..1 fraction, clamped so a 150 % day still fills the ring exactly once. */
    fun progress(actual: Long): Float = (actual.toFloat() / steps).coerceIn(0f, 1f)

    /** True once the day's steps reach the goal. */
    fun isMet(actual: Long): Boolean = actual >= steps

    /** Steps still to walk today, never negative. */
    fun remaining(actual: Long): Long = (steps - actual).coerceAtLeast(0L)

    /** Whole percent, uncapped — the number to *say*, where 150 % is meaningful. */
    fun percent(actual: Long): Int = ((actual.toDouble() / steps) * 100).toInt()

    companion object {
        val DEFAULT: StepGoal = StepGoal(10_000)
        const val MIN: Int = 1_000
        const val MAX: Int = 30_000

        /** Clamps a user-entered goal into a sane range instead of rejecting it. */
        fun clamped(steps: Int): StepGoal = StepGoal(steps.coerceIn(MIN, MAX))
    }
}
