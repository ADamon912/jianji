package com.example.jianji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.jianji.domain.Category
import com.example.jianji.domain.DateRange
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTopBar(title: String, onBack: (() -> Unit)? = null, enabled: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = {
        if (onBack != null) IconButton(onClick = onBack, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
    }, actions = actions)
}

@Composable
fun SectionIntro(title: String, subtitle: String) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SpendSummaryCard(label: String, amount: String, firstMetric: String, secondMetric: String, footnote: String? = null) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF134F3E), Color(0xFF28745A)))).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = Color(0xFFD6EFE0), style = MaterialTheme.typography.labelLarge)
        Text(amount, color = Color.White, style = MaterialTheme.typography.headlineLarge)
        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(firstMetric, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(secondMetric, Modifier.weight(1f), color = Color(0xFFD6EFE0), style = MaterialTheme.typography.bodyMedium)
        }
        footnote?.let { Text(it, color = Color(0xFFD6EFE0), style = MaterialTheme.typography.labelSmall) }
    }
}
fun rangeText(range: DateRange) = "${LocalDate.ofEpochDay(range.start)} 至 ${LocalDate.ofEpochDay(range.endExclusive - 1)}"

@Composable
fun ErrorCard(message: String, onRetry: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}
@Composable
fun EmptyMessage(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun CategoryBadge(category: Category) {
    Box(Modifier.size(46.dp).background(Color(category.colorKey).copy(alpha = 0.18f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
        Text(category.name.take(category.name.offsetByCodePoints(0, 1)), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectDateDialog(initial: LocalDate, onDismiss: () -> Unit, onSelected: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initial.coerceIn(LocalDate.ofEpochDay(0), today).toEpochDay() * 86_400_000,
        yearRange = 1970..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / 86_400_000 in 0..today.toEpochDay()
            override fun isSelectableYear(year: Int) = year in 1970..today.year
        },
    )
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = { picker.selectedDateMillis?.let { onSelected(LocalDate.ofEpochDay(it / 86_400_000)) } }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = picker, title = { Text("选择消费日期", Modifier.padding(24.dp)) }) }
}

@Composable
fun RangeDialog(initial: DateRange, onDismiss: () -> Unit, onApply: (DateRange) -> Unit) {
    var start by remember { mutableStateOf(LocalDate.ofEpochDay(initial.start)) }
    var end by remember { mutableStateOf(LocalDate.ofEpochDay(initial.endExclusive - 1)) }
    var selecting by remember { mutableIntStateOf(0) }
    val valid = !end.isBefore(start) && !end.isAfter(LocalDate.now())
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择日期范围") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("统计包含起止日期当天的消费。")
            OutlinedButton(onClick = { selecting = 1 }, modifier = Modifier.fillMaxWidth()) { Text("开始日期  $start") }
            OutlinedButton(onClick = { selecting = 2 }, modifier = Modifier.fillMaxWidth()) { Text("结束日期  $end") }
            if (!valid) Text("结束日期不能早于开始日期或晚于今天", color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = { onApply(DateRange(start.toEpochDay(), end.plusDays(1).toEpochDay())) }) { Text("应用") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    if (selecting != 0) SelectDateDialog(if (selecting == 1) start else end, { selecting = 0 }) { if (selecting == 1) start = it else end = it; selecting = 0 }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryFilterDialog(categories: List<Category>, initial: Set<String>, onDismiss: () -> Unit, onApply: (Set<String>) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("筛选消费类型") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("可选择多个类型，不选表示全部。")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category -> FilterChip(selected = category.id in selected, onClick = { selected = if (category.id in selected) selected - category.id else selected + category.id }, label = { Text(category.name + if (category.archived) "（已停用）" else "") }) }
            }
            TextButton(onClick = { selected = emptySet() }) { Text("全部类型") }
        }
    }, confirmButton = { TextButton(onClick = { onApply(selected) }) { Text("应用") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
