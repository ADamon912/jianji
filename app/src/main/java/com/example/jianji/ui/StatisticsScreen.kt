package com.example.jianji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jianji.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(model: StatisticsViewModel, onDetails: (DateRange, Set<String>) -> Unit) {
    val range by model.range.collectAsStateWithLifecycle()
    val result by model.state.collectAsStateWithLifecycle()
    val trend by model.trend.collectAsStateWithLifecycle()
    val chartType by model.chartType.collectAsStateWithLifecycle()
    val chartError by model.chartError.collectAsStateWithLifecycle()
    var rangeDialog by rememberSaveable { mutableStateOf(false) }
    var showTrendData by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { PageTopBar("消费统计") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("statisticsList"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item("range") {
                TextButton(onClick = { rangeDialog = true }, contentPadding = PaddingValues(0.dp)) { Text(rangeText(range)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("month" to "本月", "previous" to "上月", "year" to "全年").forEach { (key, label) -> OutlinedButton(onClick = { model.setRange(presetRange(key), key) }) { Text(label) } }
                    OutlinedButton(onClick = { rangeDialog = true }) { Text("自定义") }
                }
            }
            when (val value = result) {
                LoadState.Loading -> item("loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is LoadState.Failed -> item("error") { ErrorCard(value.message, model::retry) }
                is LoadState.Ready -> {
                    val snapshot = value.value
                    if (snapshot.range != range) item("updating") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    else {
                        item("totals") { SpendSummaryCard("总支出 · CNY", "¥ ${money(snapshot.totalMinor)}", "${snapshot.count} 笔消费", "日均 ¥${snapshot.averageYuan}", "按选定范围 ${snapshot.range.days} 个自然日计算") }
                        item("chart_selector") {
                            SectionIntro("看清支出，发现习惯", "选择图表，切换支出分布与时间趋势。")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChartType.entries.forEach { type -> FilterChip(selected = chartType == type, onClick = { model.setChartType(type) }, label = { Text(type.label) }) }
                            }
                        }
                        chartError?.let { message -> item("preference_error") { ErrorCard(message, model::retry) } }
                        if (snapshot.totalMinor == 0L) item("empty") { EmptyMessage("这段时间还没有消费", "记录消费后，这里会显示各类型的支出分布。") }
                        else {
                            item("chart") {
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(if (chartType == ChartType.LINE) "支出趋势" else "消费类型分布", style = MaterialTheme.typography.titleLarge)
                                        when (chartType) {
                                            ChartType.DONUT, ChartType.PIE -> CategoryCircularChart(snapshot, chartType == ChartType.DONUT)
                                            ChartType.BAR -> CategoryBarChart(snapshot) { onDetails(snapshot.range, it) }
                                            ChartType.LINE -> when (val data = trend) {
                                                LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                                is LoadState.Failed -> ErrorCard(data.message, model::retry)
                                                is LoadState.Ready -> if (data.value.range == range) {
                                                    TrendLineChart(data.value)
                                                    TextButton(onClick = { showTrendData = !showTrendData }) { Text(if (showTrendData) "收起趋势数据" else "查看趋势数据") }
                                                } else LinearProgressIndicator(Modifier.fillMaxWidth())
                                            }
                                        }
                                        Text(if (chartType == ChartType.LINE) "汇总全部类型的支出，未记账的时间段按 0 计。" else "点击类型查看明细；超过 8 类时，图中合并较小的类型。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (chartType == ChartType.LINE && showTrendData) {
                                val data = (trend as? LoadState.Ready)?.value?.takeIf { it.range == range }
                                items(data?.points.orEmpty(), key = { "trend:${it.start}" }) { point ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(point.label); Text("¥${money(point.totalMinor)}", fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            item("legend_title") { Text("分类占比", style = MaterialTheme.typography.titleMedium) }
                            items(snapshot.slices, key = { "slice:${it.key}" }) { slice ->
                                Card(onClick = { onDetails(snapshot.range, slice.categoryIds) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(Modifier.size(10.dp).background(Color(slice.color), CircleShape))
                                            Text(slice.label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                            Text("${StatisticsCalculator.percentage(slice.totalMinor, snapshot.totalMinor)}%")
                                        }
                                        Text("¥${money(slice.totalMinor)}", style = MaterialTheme.typography.titleMedium)
                                        LinearProgressIndicator(progress = { slice.ratio.toFloat() }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Color(slice.color), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                                    }
                                }
                            }
                            item("table_title") { SectionIntro("全部类型明细", "百分比四舍五入后，合计可能不等于 100%。") }
                            items(snapshot.categories, key = { "category:${it.category.id}" }) { category ->
                                ListItem(headlineContent = { Text(category.category.name + if (category.category.archived) "（已停用）" else "") },
                                    supportingContent = { Text("${category.count} 笔 · ${StatisticsCalculator.percentage(category.totalMinor, snapshot.totalMinor)}%") },
                                    trailingContent = { TextButton(onClick = { onDetails(snapshot.range, setOf(category.category.id)) }) { Text("¥${money(category.totalMinor)}") } },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (rangeDialog) RangeDialog(range, { rangeDialog = false }) { model.setRange(it); rangeDialog = false }
}
