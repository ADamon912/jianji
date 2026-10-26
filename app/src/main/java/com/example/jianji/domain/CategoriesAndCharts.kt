package com.example.jianji.domain

import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

object CategoryRules {
    val colors = listOf(0xFF3B8F77, 0xFFE08B36, 0xFF4285B4, 0xFFAE6BA9, 0xFFDA6D76, 0xFF478D92, 0xFF7973B2, 0xFF788489)
    val colorNames = listOf("松绿", "橙黄", "湖蓝", "丁香", "珊瑚", "青碧", "鸢紫", "岩灰")
    fun name(raw: String) = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
    fun key(raw: String) = name(raw).lowercase(Locale.ROOT)
    fun valid(raw: String): Boolean {
        val value = name(raw)
        return value.codePointCount(0, value.length) in 1..12 && validNote(value) &&
            value.codePoints().noneMatch { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }
    }
}

sealed interface CategoryResult {
    data class Success(val message: String) : CategoryResult
    data class Failure(val message: String) : CategoryResult
}

enum class ChartType(val label: String) { DONUT("环形图"), PIE("饼图"), BAR("柱状图"), LINE("折线图") }
data class TrendPoint(val start: Long, val endExclusive: Long, val label: String, val totalMinor: Long)
data class TrendSnapshot(val range: DateRange, val unit: String, val points: List<TrendPoint>)

object TrendCalculator {
    fun calculate(range: DateRange, dailyTotals: Map<Long, Long>): TrendSnapshot {
        val first = LocalDate.ofEpochDay(range.start)
        val last = LocalDate.ofEpochDay(range.endExclusive - 1)
        val unit = when {
            range.days <= 62 -> "日"
            ChronoUnit.MONTHS.between(first.withDayOfMonth(1), last.withDayOfMonth(1)) < 24 -> "月"
            else -> "年"
        }
        fun bucket(day: Long): LocalDate = LocalDate.ofEpochDay(day).let {
            when (unit) { "月" -> it.withDayOfMonth(1); "年" -> it.withDayOfYear(1); else -> it }
        }
        val sums = mutableMapOf<LocalDate, Long>()
        dailyTotals.forEach { (day, amount) ->
            require(amount >= 0)
            if (day >= range.start && day < range.endExclusive) {
                val key = bucket(day)
                sums[key] = Math.addExact(sums[key] ?: 0, amount)
            }
        }
        val points = mutableListOf<TrendPoint>()
        var date = bucket(range.start)
        while (date.toEpochDay() < range.endExclusive) {
            val next = when (unit) { "月" -> date.plusMonths(1); "年" -> date.plusYears(1); else -> date.plusDays(1) }
            val label = when (unit) { "月" -> date.toString().take(7); "年" -> date.year.toString(); else -> date.toString() }
            points += TrendPoint(maxOf(date.toEpochDay(), range.start), minOf(next.toEpochDay(), range.endExclusive), label, sums[date] ?: 0)
            date = next
        }
        return TrendSnapshot(range, unit, points)
    }
}
