package com.example.jianji.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object MoneyParser {
    const val MAX_MINOR = 99_999_999L
    fun parse(text: String): Long? {
        if (text.length > 32) return null
        val normalized = text.trim().map { when (it) {
            in '０'..'９' -> '0' + (it - '０')
            '．' -> '.'
            else -> it
        } }.joinToString("")
        if (!Regex("^[0-9]+(\\.[0-9]{1,2})?$").matches(normalized)) return null
        return try { BigDecimal(normalized).movePointRight(2).longValueExact().takeIf { it in 1..MAX_MINOR } }
        catch (_: ArithmeticException) { null }
    }
}
fun money(minor: Long): String = BigDecimal.valueOf(minor, 2).setScale(2).toPlainString()
fun validNote(text: String): Boolean = text.codePointCount(0, text.length) <= 200 && text.none { it == '\u0000' } &&
    text.codePoints().allMatch { it !in 0xD800..0xDFFF }

class ValidateExpenseUseCase {
    operator fun invoke(amount: String, categoryId: String?, date: LocalDate, note: String, today: LocalDate): Pair<ExpenseInput?, FieldErrors> {
        val minor = MoneyParser.parse(amount)
        val errors = FieldErrors(
            amount = if (minor == null) "请输入 0.01～999,999.99 元，最多两位小数" else null,
            category = if (categoryId.isNullOrBlank()) "请选择消费类型" else null,
            date = if (date.isAfter(today) || date.toEpochDay() < 0) "消费日期不能早于 1970 年或晚于今天" else null,
            note = if (!validNote(note.trim())) "备注最多 200 个字符，且不能包含非法字符" else null,
        )
        return if (errors.hasErrors) null to errors else ExpenseInput(requireNotNull(minor), requireNotNull(categoryId), date.toEpochDay(), note.trim()) to errors
    }
}

/** A versioned, length-prefixed encoding avoids delimiter collisions in notes. */
fun ExpenseCommand.digest(): String {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
        fun utf8(value: String) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            out.writeInt(encoded.size)
            out.write(encoded)
        }
        out.writeInt(1)
        listOf(operation.name, context.dataEpoch, expenseId).forEach(::utf8)
        out.writeLong(expectedRevision ?: -1)
        out.writeBoolean(input != null)
        input?.let {
            out.writeLong(it.amountMinor)
            utf8("CNY")
            utf8(it.categoryId)
            out.writeLong(it.spentOn)
            utf8(it.note)
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class ChartSlice(val key: String, val label: String, val totalMinor: Long, val ratio: Double, val categoryIds: Set<String>, val color: Long)
data class StatisticsSnapshot(val range: DateRange, val totalMinor: Long, val count: Long, val averageYuan: String, val categories: List<CategoryTotal>, val slices: List<ChartSlice>)
object StatisticsCalculator {
    fun calculate(range: DateRange, values: List<CategoryTotal>): StatisticsSnapshot {
        require(values.all { it.totalMinor > 0 && it.count > 0 })
        require(values.map { it.category.id }.distinct().size == values.size)
        val sorted = values.sortedWith(compareByDescending<CategoryTotal> { it.totalMinor }.thenBy { it.category.sortOrder }.thenBy { it.category.id })
        val total = sorted.fold(0L) { acc, v -> Math.addExact(acc, v.totalMinor) }
        val count = sorted.fold(0L) { acc, v -> Math.addExact(acc, v.count) }
        val chartValues = if (sorted.size <= 8) sorted.map { Triple(it.category, it.totalMinor, setOf(it.category.id)) } else {
            sorted.take(7).map { Triple(it.category, it.totalMinor, setOf(it.category.id)) } + Triple(
                Category("aggregate:remaining", "其余类型", "other", 0xFF78909C, Int.MAX_VALUE),
                sorted.drop(7).fold(0L) { acc, v -> Math.addExact(acc, v.totalMinor) },
                sorted.drop(7).map { it.category.id }.toSet(),
            )
        }
        return StatisticsSnapshot(range, total, count,
            BigDecimal.valueOf(total, 2).divide(BigDecimal.valueOf(range.days), 2, RoundingMode.HALF_UP).toPlainString(),
            sorted, chartValues.map { (category, amount, ids) -> ChartSlice(category.id, category.name, amount,
                BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(total), 16, RoundingMode.HALF_UP).toDouble(), ids, category.colorKey) },
        )
    }
    fun percentage(amount: Long, total: Long): String = if (total == 0L) "0.0" else
        BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP).toPlainString()
}
