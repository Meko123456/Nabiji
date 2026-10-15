package io.github.meko123456.nabiji.ui

/** "1 step", "8412 steps". The today card and the widget are read out, and said "1 steps to go". */
internal fun stepCount(steps: Long): String = if (steps == 1L) "1 step" else "$steps steps"
