package com.example.jianji.domain

import java.time.LocalDate

data class Category(val id: String, val name: String, val iconKey: String, val colorKey: Long, val sortOrder: Int, val archived: Boolean = false)
data class Expense(val id: String, val amountMinor: Long, val categoryId: String, val spentOn: Long, val note: String, val createdAt: Long, val updatedAt: Long, val revision: Long)
data class ExpenseInput(val amountMinor: Long, val categoryId: String, val spentOn: Long, val note: String)
data class DateRange(val start: Long, val endExclusive: Long) {
    init { require(start >= 0 && endExclusive > start) }
    val days: Long get() = endExclusive - start
    companion object {
        fun month(today: LocalDate) = DateRange(today.withDayOfMonth(1).toEpochDay(), today.plusDays(1).toEpochDay())
        fun previousMonth(today: LocalDate): DateRange {
            val start = today.minusMonths(1).withDayOfMonth(1)
            return DateRange(start.toEpochDay(), start.plusMonths(1).toEpochDay())
        }
        fun year(today: LocalDate) = DateRange(today.withDayOfYear(1).toEpochDay(), today.plusDays(1).toEpochDay())
        fun all(today: LocalDate) = DateRange(0, today.plusDays(1).toEpochDay())
    }
}
data class ExpenseFilter(val range: DateRange, val categoryIds: Set<String> = emptySet(), val keyword: String = "")
data class ExpenseRow(val expense: Expense, val category: Category)
data class DailyTotal(val day: Long, val totalMinor: Long, val count: Long)
data class ExpenseSummary(val totalMinor: Long, val count: Long, val dailyTotals: Map<Long, Long>)
data class CategoryTotal(val category: Category, val totalMinor: Long, val count: Long)
data class WriteContext(val operationId: String, val dataEpoch: String)
enum class Operation { CREATE, UPDATE, DELETE }
data class ExpenseCommand(val context: WriteContext, val operation: Operation, val expenseId: String, val expectedRevision: Long? = null, val input: ExpenseInput? = null)
data class ExpenseCommit(val operationId: String, val expenseId: String, val revision: Long?, val replayed: Boolean = false)
enum class ErrorCode {
    VALIDATION, NOT_FOUND, CONFLICT, CATEGORY_UNAVAILABLE, IDEMPOTENCY_CONFLICT,
    DATASET_CHANGED, STORAGE_FULL, DATABASE_BUSY, STORAGE_FAILURE, DATA_INTEGRITY,
}
sealed interface CommandResult {
    data class Success(val commit: ExpenseCommit) : CommandResult
    data class Failure(val code: ErrorCode) : CommandResult
}
data class FieldErrors(val amount: String? = null, val category: String? = null, val date: String? = null, val note: String? = null) {
    val hasErrors get() = amount != null || category != null || date != null || note != null
}
enum class ThemeMode { SYSTEM, LIGHT, DARK }
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}
