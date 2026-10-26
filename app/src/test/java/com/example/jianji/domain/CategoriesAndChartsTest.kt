package com.example.jianji.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CategoriesAndChartsTest {
    private fun day(date: String) = LocalDate.parse(date).toEpochDay()
    @Test fun namesUseCodePointsAndRejectControlCharacters() {
        assertTrue(CategoryRules.valid("😀".repeat(12)))
        assertFalse(CategoryRules.valid("😀".repeat(13)))
        assertFalse(CategoryRules.valid("餐\n饮"))
        assertFalse(CategoryRules.valid("\uD800"))
        assertFalse(CategoryRules.valid("\u200B"))
        assertEquals(CategoryRules.key(" ＡＢＣ "), CategoryRules.key("abc"))
    }
    @Test fun dailyTrendIncludesZeroSpendingAndLeapDays() {
        val range = DateRange(day("2024-02-28"), day("2024-03-02"))
        val result = TrendCalculator.calculate(range, mapOf(day("2024-02-29") to 1230L))
        assertEquals("日", result.unit)
        assertEquals(listOf(0L, 1230L, 0L), result.points.map { it.totalMinor })
        assertEquals("2024-02-29", result.points[1].label)
    }
    @Test fun monthlyTrendClipsBoundariesAndConservesTotals() {
        val range = DateRange(day("2023-12-31"), day("2024-03-11"))
        val result = TrendCalculator.calculate(range, mapOf(day("2023-12-30") to 999L, day("2023-12-31") to 10L, day("2024-02-29") to 200L, day("2024-03-11") to 999L))
        assertEquals("月", result.unit)
        assertEquals(4, result.points.size)
        assertEquals(210L, result.points.sumOf { it.totalMinor })
        assertEquals(range.start, result.points.first().start)
        assertEquals(range.endExclusive, result.points.last().endExclusive)
    }
    @Test fun longHistoryUsesBoundedYearlySeries() {
        val range = DateRange(0, day("2026-10-27"))
        val result = TrendCalculator.calculate(range, mapOf(day("2026-10-26") to 2500L))
        assertEquals("年", result.unit)
        assertEquals(57, result.points.size)
        assertEquals(2500L, result.points.last().totalMinor)
        assertEquals(2500L, result.points.sumOf { it.totalMinor })
    }
    @Test fun singleDayHasOnePointWithoutInventedNeighbors() {
        val result = TrendCalculator.calculate(DateRange(1, 2), emptyMap())
        assertEquals(1, result.points.size)
        assertEquals(0L, result.points.single().totalMinor)
    }
    @Test(expected = ArithmeticException::class) fun monthlySumRejectsOverflow() {
        TrendCalculator.calculate(DateRange(0, 100), mapOf(0L to Long.MAX_VALUE, 1L to 1L))
    }
}
