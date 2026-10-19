package io.github.meko123456.nabiji.ui

import java.text.NumberFormat
import java.util.Locale

/**
 * A count as the phone's language writes it: "8,412" in English, "8 412" in Georgian. Steps run
 * into the thousands every day, and "of 10000 steps" had the reader counting zeros.
 */
internal fun Long.grouped(locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(this)

internal fun Int.grouped(locale: Locale): String = toLong().grouped(locale)

/** "1 step", "8,412 steps". The today card and the widget are read out, and said "1 steps to go". */
internal fun stepCount(steps: Long, locale: Locale): String =
    if (steps == 1L) "1 step" else "${steps.grouped(locale)} steps"
