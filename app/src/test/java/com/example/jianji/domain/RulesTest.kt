package com.example.jianji.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    @Test fun moneyIsExactAndNormalizesFullWidthDigits() {
        mapOf("12" to 1200L, "12.3" to 1230L, "12.30" to 1230L, "０１２．３０" to 1230L, "0.01" to 1L, "999999.99" to 99_999_999L).forEach { (text, expected) -> assertEquals(text, expected, MoneyParser.parse(text)) }
        assertEquals("12.30", money(1230))
    }
    @Test fun invalidAmountsCannotBeSilentlyRounded() {
        listOf("", "0", "-1", "+1", ".5", "12.", "1e2", "1,000", "十二", "0.001", "1000000", "9".repeat(33)).forEach { assertNull(it, MoneyParser.parse(it)) }
    }
    @Test fun datesUseInclusiveCalendarDaysAndHandleLeapYear() {
        val today = LocalDate.of(2024, 3, 3)
        assertEquals(3L, DateRange.month(today).days)
        val previous = DateRange.previousMonth(today)
        assertEquals(LocalDate.of(2024, 2, 1).toEpochDay(), previous.start)
        assertEquals(29L, previous.days)
        assertEquals(LocalDate.of(2024, 3, 1).toEpochDay(), previous.endExclusive)
        assertEquals(63L, DateRange.year(today).days)
    }
    @Test fun validatorRequiresCategoryAndRejectsFutureDates() {
        val today = LocalDate.of(2026, 10, 23)
        val (input, errors) = ValidateExpenseUseCase()("12.30", null, today.plusDays(1), "", today)
        assertNull(input); assertNotNull(errors.category); assertNotNull(errors.date)
        val valid = ValidateExpenseUseCase()("12.30", "category", today, "  午餐  ", today)
        assertEquals("午餐", valid.first?.note)
    }
    @Test fun notesCountCodePointsAndRejectInvalidSurrogates() {
        assertTrue(validNote("😀".repeat(200)))
        assertFalse(validNote("😀".repeat(201)))
        assertFalse(validNote("\uD800"))
        assertFalse(validNote("a\u0000b"))
    }
    @Test fun statisticsHaveCorrectAmountsRatiosAndNaturalDayAverage() {
        val result = StatisticsCalculator.calculate(DateRange(1, 4), listOf(total("food", 1000), total("bus", 3000)))
        assertEquals(4000, result.totalMinor)
        assertEquals("13.33", result.averageYuan)
        assertEquals(0.75, result.slices.first().ratio, 0.000001)
        assertEquals("25.0", StatisticsCalculator.percentage(1000, 4000))
    }
    @Test fun emptyAndSingleCategoryAreDefined() {
        val empty = StatisticsCalculator.calculate(DateRange(1, 2), emptyList())
        assertEquals(0, empty.totalMinor); assertEquals("0.00", empty.averageYuan); assertTrue(empty.slices.isEmpty())
        assertEquals(1.0, StatisticsCalculator.calculate(DateRange(1, 2), listOf(total("food", 1))).slices.single().ratio, 0.0)
    }
    @Test fun topSevenAndRemainingDoNotLoseMoneyOrDrillDownIds() {
        val result = StatisticsCalculator.calculate(DateRange(1, 2), (1..9).map { total(it.toString(), 100) })
        assertEquals(9, result.categories.size); assertEquals(8, result.slices.size)
        assertEquals(900, result.slices.sumOf { it.totalMinor })
        assertEquals(200, result.slices.last().totalMinor)
        assertEquals(2, result.slices.last().categoryIds.size)
        assertEquals(9, result.slices.flatMap { it.categoryIds }.toSet().size)
    }
    @Test(expected = ArithmeticException::class) fun totalsRejectOverflow() {
        StatisticsCalculator.calculate(DateRange(1, 2), listOf(total("a", Long.MAX_VALUE), total("b", 1)))
    }
    @Test fun requestDigestDistinguishesFieldBoundariesAndIgnoresOperationId() {
        val a = ExpenseCommand(WriteContext("first", "epoch"), Operation.CREATE, "id", input = ExpenseInput(100, "category", 1, "note"))
        assertEquals(a.digest(), a.copy(context = a.context.copy(operationId = "second")).digest())
        assertNotEquals(a.digest(), a.copy(input = a.input!!.copy(note = "different")).digest())
        assertNotEquals(a.digest(), a.copy(operation = Operation.UPDATE, expectedRevision = 1).digest())
    }
    private fun total(id: String, amount: Long) = CategoryTotal(Category(id, id, "other", 0, 0), amount, 1)
}
