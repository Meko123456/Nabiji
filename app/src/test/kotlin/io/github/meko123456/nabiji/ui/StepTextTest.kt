package io.github.meko123456.nabiji.ui

import java.text.DecimalFormatSymbols
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StepTextTest {

    @Test
    fun `one step is a step`() {
        // A screen reader heard "Today: 1 steps" first thing in the morning, and "1 steps to go"
        // one step short of the goal.
        assertEquals("1 step", stepCount(1, Locale.US))
        assertEquals("0 steps", stepCount(0, Locale.US))
        assertEquals("2 steps", stepCount(2, Locale.US))
    }

    @Test
    fun `thousands are grouped as the language groups them`() {
        assertEquals("10,000 steps", stepCount(10_000, Locale.US))
        assertEquals("12,345", 12_345.grouped(Locale.US))
        assertEquals("999", 999L.grouped(Locale.US))
        // Georgian separates thousands with a space that doesn't break, not a comma.
        val space = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("ka")).groupingSeparator
        assertEquals("8${space}412 steps", stepCount(8_412, Locale.forLanguageTag("ka")))
    }
}
