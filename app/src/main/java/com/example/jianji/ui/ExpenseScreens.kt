package com.example.jianji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.jianji.domain.*
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseListScreen(model: ExpenseListViewModel, onAdd: () -> Unit, onDetail: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val pages = model.pages.collectAsLazyPagingItems()
    val summary by model.summary.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    val range by model.range.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val keyword by model.keyword.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    var showRange by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var lastFilter by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(filter) { val key = filter.toString(); if (lastFilter != null && lastFilter != key) listState.scrollToItem(0); lastFilter = key }
    Scaffold(topBar = { PageTopBar(if (onBack == null) "简记 · 消费明细" else "分类消费明细", onBack) }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, contentDescription = "记一笔") }, text = { Text("记一笔") })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item("summary") {
                val current = (summary as? LoadState.Ready)?.value
                SpendSummaryCard("当前范围总支出 · CNY", current?.let { "¥ ${money(it.totalMinor)}" } ?: "—",
                    current?.let { "共 ${it.count} 笔消费" } ?: if (summary is LoadState.Failed) "汇总暂不可用" else "正在读取汇总", "每笔小记，心中有数")
            }
            item("dates") {
                TextButton(onClick = { showRange = true }, contentPadding = PaddingValues(0.dp)) { Text(rangeText(range)) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("month" to "本月", "previous" to "上月", "all" to "全部历史").forEach { (mode, label) -> OutlinedButton(onClick = { model.preset(mode) }) { Text(label) } }
                    OutlinedButton(onClick = { showCategories = true }, enabled = categories is LoadState.Ready) { Text(if (selected.isEmpty()) "全部类型" else "已选 ${selected.size} 类") }
                }
            }
            item("search") {
                OutlinedTextField(value = keyword, onValueChange = model::search, label = { Text("搜索备注") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                    if (keyword.isNotEmpty()) TextButton(onClick = { model.search("") }) { Text("清除") }
                })
            }
            if (summary is LoadState.Failed) item("summary_error") { ErrorCard((summary as LoadState.Failed).message, model::retry) }
            if (categories is LoadState.Failed) item("category_error") { ErrorCard((categories as LoadState.Failed).message, model::retry) }
            when (pages.loadState.refresh) {
                is androidx.paging.LoadState.Loading -> item("refresh") { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在更新记录", style = MaterialTheme.typography.labelSmall) }
                is androidx.paging.LoadState.Error -> item("error") { ErrorCard("明细加载失败，已有内容会保留", pages::retry) }
                else -> if (pages.itemCount == 0) item("empty") {
                    EmptyMessage("当前范围没有消费记录", "点击「记一笔」开始记录，或调整日期和类型。")
                    TextButton(onClick = model::clear, modifier = Modifier.fillMaxWidth()) { Text("清除筛选，返回本月") }
                }
            }
            items(pages.itemCount, key = pages.itemKey { when (it) { is LedgerListItem.Header -> "day:${it.day}"; is LedgerListItem.Row -> it.value.expense.id } }) { index ->
                when (val item = pages[index]) {
                    is LedgerListItem.Header -> {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(LocalDate.ofEpochDay(item.day).toString(), style = MaterialTheme.typography.labelLarge)
                            Text((summary as? LoadState.Ready)?.value?.dailyTotals?.get(item.day)?.let { "小计 ¥${money(it)}" } ?: "小计更新中", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    is LedgerListItem.Row -> ExpenseListRow(item.value) { onDetail(item.value.expense.id) }
                    null -> Unit
                }
            }
            when (pages.loadState.append) {
                is androidx.paging.LoadState.Loading -> item("append") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is androidx.paging.LoadState.Error -> item("append_error") { ErrorCard("更多记录加载失败", pages::retry) }
                else -> Unit
            }
        }
    }
    if (showRange) RangeDialog(range, { showRange = false }) { model.setRange(it); showRange = false }
    if (showCategories && categories is LoadState.Ready) CategoryFilterDialog((categories as LoadState.Ready<List<Category>>).value, selected, { showCategories = false }) { model.setCategories(it); showCategories = false }
}

@Composable
private fun ExpenseListRow(row: ExpenseRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryBadge(row.category)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.category.name, fontWeight = FontWeight.Medium)
                if (row.expense.note.isNotBlank()) Text(row.expense.note, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("¥${money(row.expense.amountMinor)}", fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorScreen(model: ExpenseEditorViewModel, onBack: () -> Unit, onManageCategories: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val state by model.state.collectAsStateWithLifecycle()
    val categories by model.categories.collectAsStateWithLifecycle()
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var leaveDialog by rememberSaveable { mutableStateOf(false) }
    var reloadDialog by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.completed) { if (state.completed != null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { onBack() } }
    val back: () -> Unit = { if (!state.saving) { if (state.dirty || state.unresolved) leaveDialog = true else onBack() } }
    BackHandler { back() }
    Scaffold(topBar = { PageTopBar(if (model.editing) "编辑消费" else "记一笔", back, !state.saving) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorCard(it, if (state.loadFailed) model::load else null) }
            if (state.conflict) OutlinedButton(onClick = { reloadDialog = true }, enabled = !state.saving && !state.unresolved) { Text("重新加载记录") }
            if (state.missing) {
                OutlinedButton(onClick = onBack) { Text("返回明细") }
            } else {
                val editable = !state.saving && !state.loading && !state.loadFailed && !state.unresolved
                OutlinedTextField(state.amount, { model.change(amount = it) }, label = { Text("消费金额（元）") }, prefix = { Text("¥ ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = editable, isError = state.errors.amount != null, supportingText = { Text(state.errors.amount ?: "人民币 CNY，最多两位小数") }, modifier = Modifier.fillMaxWidth().testTag("amountInput"), textStyle = MaterialTheme.typography.headlineMedium)
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("消费类型", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); onManageCategories() }, enabled = editable) { Text("管理类型") }
                    }
                    when (val result = categories) {
                        LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        is LoadState.Failed -> ErrorCard(result.message, model::retryCategories)
                        is LoadState.Ready -> Column {
                            if (result.value.none(model::canSelect)) Text("暂无可用类型，点击管理类型添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.value.filter(model::canSelect).forEach { category -> FilterChip(selected = state.categoryId == category.id, enabled = editable, onClick = { model.change(category = category.id) }, label = { Text(category.name + if (category.archived) "（已停用）" else "") }) }
                            }
                        }
                    }
                    state.errors.category?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                OutlinedButton(onClick = { datePicker = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) { Text("消费日期  ${state.date}") }
                state.errors.date?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(state.note, { if (it.codePointCount(0, it.length) <= 200) model.change(note = it) }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth().testTag("noteInput"), enabled = editable, minLines = 3, maxLines = 5, isError = state.errors.note != null, supportingText = { Text(state.errors.note ?: "${state.note.codePointCount(0, state.note.length)} / 200") })
                Button(onClick = model::save, enabled = !state.saving && !state.loading && !state.loadFailed && categories is LoadState.Ready, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (state.saving) "正在保存…" else if (state.unresolved) "确认原提交结果" else "保存消费")
                }
            }
        }
    }
    if (datePicker) SelectDateDialog(LocalDate.parse(state.date), { datePicker = false }) { model.change(date = it.toString()); datePicker = false }
    if (leaveDialog) AlertDialog(onDismissRequest = { leaveDialog = false }, title = { Text("离开编辑页面？") }, text = { Text(if (state.unresolved) "原提交结果尚未确认，记录可能已保存。离开后请先检查明细。" else "尚未保存的修改将被放弃。") }, confirmButton = { TextButton(onClick = onBack) { Text("放弃并离开") } }, dismissButton = { TextButton(onClick = { leaveDialog = false }) { Text("继续编辑") } })
    if (reloadDialog) AlertDialog(onDismissRequest = { reloadDialog = false }, title = { Text("重新加载最新记录？") }, text = { Text("当前未保存的修改将被替换。") }, confirmButton = { TextButton(onClick = { reloadDialog = false; model.reload() }) { Text("重新加载") } }, dismissButton = { TextButton(onClick = { reloadDialog = false }) { Text("取消") } })
}

@Composable
fun ExpenseDetailScreen(model: ExpenseDetailViewModel, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val result by model.expense.collectAsStateWithLifecycle()
    val deleting by model.deleting.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val completed by model.completed.collectAsStateWithLifecycle()
    var confirmation by remember { mutableStateOf<ExpenseRow?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(completed) { if (completed) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { onBack() } }
    BackHandler(enabled = deleting) { }
    Scaffold(topBar = { PageTopBar("消费详情", onBack, !deleting) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            error?.let { ErrorCard(it) }
            when (val value = result) {
                LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is LoadState.Failed -> ErrorCard(value.message, model::retry)
                is LoadState.Ready -> if (value.value == null) EmptyMessage("记录已不存在", "返回明细查看其他消费。") else {
                    val row = requireNotNull(value.value)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            CategoryBadge(row.category)
                            Text("¥ ${money(row.expense.amountMinor)}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text("类型：${row.category.name}")
                            Text("日期：${LocalDate.ofEpochDay(row.expense.spentOn)}")
                            HorizontalDivider()
                            Text(row.expense.note.ifBlank { "未填写备注" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = { onEdit(row.expense.id) }, enabled = !deleting, modifier = Modifier.fillMaxWidth()) { Text("修改这笔消费") }
                    OutlinedButton(onClick = { confirmation = row }, enabled = !deleting, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (deleting) "正在删除…" else "删除消费") }
                }
            }
        }
    }
    confirmation?.let { row -> AlertDialog(onDismissRequest = { confirmation = null }, title = { Text("确定删除这笔消费？") }, text = { Text("${row.category.name} · ¥${money(row.expense.amountMinor)}\n${LocalDate.ofEpochDay(row.expense.spentOn)}\n删除后无法撤销。") }, confirmButton = { TextButton(onClick = { confirmation = null; model.delete(row.expense.revision) }) { Text("删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } }) }
}
