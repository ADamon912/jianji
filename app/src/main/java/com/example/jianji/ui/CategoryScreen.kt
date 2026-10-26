package com.example.jianji.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.jianji.domain.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryScreen(model: CategoryViewModel, onBack: () -> Unit) {
    val categories by model.categories.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Category?>(null) }
    LaunchedEffect(notice) { if (notice != null) { delay(5_000); model.clearNotice() } }
    BackHandler(enabled = saving) { }
    Scaffold(topBar = { PageTopBar("消费类型管理", onBack, !saving) {
        IconButton(onClick = { model.open() }, enabled = !saving) { Icon(Icons.Default.Add, "添加类型") }
    } }, bottomBar = {
        notice?.let { message -> Snackbar(Modifier.padding(12.dp), action = { TextButton(onClick = model::clearNotice) { Text("知道了") } }) { Text(message) } }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("categoryList"), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionIntro("让每一笔，都有归属", "自定义名称与颜色，整理适合自己的消费类型。")
            }
            if (saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error != null && draft == null) item { ErrorCard(requireNotNull(error)) }
            when (val result = categories) {
                LoadState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is LoadState.Failed -> item { ErrorCard(result.message, model::retry) }
                is LoadState.Ready -> {
                    val active = result.value.filterNot { it.archived }
                    val archived = result.value.filter { it.archived }
                    item { Text("使用中 · ${active.size}", style = MaterialTheme.typography.titleMedium) }
                    if (active.isEmpty()) item { EmptyMessage("还没有可用类型", "添加一个类型后即可继续记账。") }
                    items(active, key = { it.id }) { category -> CategoryManagementRow(category, saving, { model.open(category) }, { deleting = category }, { model.restore(category) }) }
                    if (archived.isNotEmpty()) {
                        item { SectionIntro("已停用 · ${archived.size}", "这些类型仍关联历史账单，可恢复使用。") }
                        items(archived, key = { it.id }) { category -> CategoryManagementRow(category, saving, { model.open(category) }, { deleting = category }, { model.restore(category) }) }
                    }
                }
            }
        }
    }
    draft?.let { value ->
        Dialog(onDismissRequest = model::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
          Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.92f).heightIn(max = 600.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (value.original == null) "添加消费类型" else "编辑消费类型", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value.name, { if (it.codePointCount(0, it.length) <= 12) model.edit(name = it) }, enabled = !saving, label = { Text("类型名称") }, supportingText = { Text("1～12 个字符，名称不可重复") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("categoryNameInput"))
                Text("标识颜色", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryRules.colors.forEachIndexed { index, color ->
                        val selected = value.color == color
                        Box(Modifier.size(48.dp).selectable(selected, enabled = !saving, role = Role.RadioButton, onClick = { model.edit(color = color) })
                            .semantics { contentDescription = CategoryRules.colorNames[index] }.padding(5.dp)
                            .border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape).padding(5.dp).background(Color(color), CircleShape))
                    }
                }
                if (value.original != null) Text("修改名称或颜色会同步更新历史记录中的类型显示。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = model::close, enabled = !saving) { Text("取消") }
                    TextButton(onClick = model::save, enabled = !saving) { Text(if (saving) "保存中…" else "保存类型") }
                }
            }
          }
        }
    }
    deleting?.let { category -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除「${category.name}」？") }, text = {
        Text("未关联消费记录的类型会被删除；已有消费记录的类型将停用，历史账单和统计保留，可在已停用列表恢复。")
    }, confirmButton = { TextButton(onClick = { deleting = null; model.delete(category) }) { Text("确认删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable
private fun CategoryManagementRow(category: Category, saving: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryBadge(category)
                Column(Modifier.weight(1f)) {
                    Text(category.name, fontWeight = FontWeight.SemiBold)
                    Text(if (category.archived) "已停用 · 历史保留" else "可用于新增消费", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit, enabled = !saving, modifier = Modifier.semantics { contentDescription = "编辑${category.name}" }) { Text("编辑") }
                if (category.archived) TextButton(onClick = onRestore, enabled = !saving, modifier = Modifier.semantics { contentDescription = "恢复${category.name}" }) { Text("恢复") }
                TextButton(onClick = onDelete, enabled = !saving, modifier = Modifier.semantics { contentDescription = "删除${category.name}" }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
